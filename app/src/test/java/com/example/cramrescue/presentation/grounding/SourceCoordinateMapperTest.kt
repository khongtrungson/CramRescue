package com.example.cramrescue.presentation.grounding

import com.example.cramrescue.domain.model.BoundingBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceCoordinateMapperTest {

    private val eps = 0.001f

    private fun assertRect(expected: DisplayRect, actual: DisplayRect, tolerance: Float = eps) {
        assertEquals("left", expected.left, actual.left, tolerance)
        assertEquals("top", expected.top, actual.top, tolerance)
        assertEquals("right", expected.right, actual.right, tolerance)
        assertEquals("bottom", expected.bottom, actual.bottom, tolerance)
    }

    private fun mapOne(sw: Int, sh: Int, cw: Float, ch: Float, box: BoundingBox): DisplayRect {
        val t = SourceCoordinateMapper.fit(sw, sh, cw, ch)!!
        return SourceCoordinateMapper.mapAll(listOf(box), sw, sh, t).single()
    }

    @Test
    fun identityMapping() {
        assertRect(DisplayRect(100f, 200f, 300f, 400f), mapOne(1000, 1000, 1000f, 1000f, BoundingBox(100, 200, 300, 400)))
    }

    @Test
    fun uniformHalfScaleMatchesTheRealPdfExample() {
        // Real persisted data: 1414×2000 page, block (202,1116)-(1233,1194), displayed at 707×1000.
        assertRect(DisplayRect(101f, 558f, 616.5f, 597f), mapOne(1414, 2000, 707f, 1000f, BoundingBox(202, 1116, 1233, 1194)))
    }

    @Test
    fun uniformDoubleScale() {
        assertRect(DisplayRect(20f, 40f, 60f, 80f), mapOne(100, 100, 200f, 200f, BoundingBox(10, 20, 30, 40)))
    }

    @Test
    fun portraitIntoPortraitAndLandscapeIntoLandscape() {
        assertRect(DisplayRect(50f, 100f, 150f, 200f), mapOne(1000, 2000, 500f, 1000f, BoundingBox(100, 200, 300, 400)))
        assertRect(DisplayRect(100f, 50f, 200f, 150f), mapOne(2000, 1000, 1000f, 500f, BoundingBox(200, 100, 400, 300)))
    }

    @Test
    fun portraitInSquareContainerIsHorizontallyLetterboxed() {
        // 1000×2000 in 400×400 → rendered 200×400, offsetX 100.
        val t = SourceCoordinateMapper.fit(1000, 2000, 400f, 400f)!!
        assertEquals(0.2f, t.scale, eps)
        assertEquals(100f, t.offsetX, eps)
        assertEquals(0f, t.offsetY, eps)
        assertRect(DisplayRect(120f, 40f, 160f, 80f), mapOne(1000, 2000, 400f, 400f, BoundingBox(100, 200, 300, 400)))
    }

    @Test
    fun landscapeInSquareContainerIsVerticallyLetterboxed() {
        // 2000×1000 in 400×400 → rendered 400×200, offsetY 100.
        val t = SourceCoordinateMapper.fit(2000, 1000, 400f, 400f)!!
        assertEquals(0f, t.offsetX, eps)
        assertEquals(100f, t.offsetY, eps)
        assertRect(DisplayRect(40f, 120f, 80f, 160f), mapOne(2000, 1000, 400f, 400f, BoundingBox(200, 100, 400, 300)))
    }

    @Test
    fun nonIntegerScale() {
        // 1414×2000 in a 1080×1920 phone container: scale = min(0.76379, 0.96) = 0.763790…
        val t = SourceCoordinateMapper.fit(1414, 2000, 1080f, 1920f)!!
        val scale = 1080f / 1414f
        assertEquals(scale, t.scale, 1e-6f)
        val expectedOffsetY = (1920f - 2000f * scale) / 2f
        assertRect(
            DisplayRect(202 * scale, expectedOffsetY + 1116 * scale, 1233 * scale, expectedOffsetY + 1194 * scale),
            SourceCoordinateMapper.map(BoundingBox(202, 1116, 1233, 1194), t)
        )
    }

    @Test
    fun cornersAndFullPage() {
        val t = SourceCoordinateMapper.fit(1000, 2000, 400f, 400f)!!
        assertRect(DisplayRect(100f, 0f, 102f, 2f), SourceCoordinateMapper.map(BoundingBox(0, 0, 10, 10), t))
        assertRect(DisplayRect(298f, 398f, 300f, 400f), SourceCoordinateMapper.map(BoundingBox(990, 1990, 1000, 2000), t))
        val full = SourceCoordinateMapper.map(BoundingBox(0, 0, 1000, 2000), t)
        assertRect(SourceCoordinateMapper.contentRect(1000, 2000, t), full)
    }

    @Test
    fun multipleBoxesAreMappedIndependentlyAndUnionIsSeparate() {
        val t = SourceCoordinateMapper.fit(1000, 1000, 500f, 500f)!!
        val rects = SourceCoordinateMapper.mapAll(
            listOf(BoundingBox(0, 0, 100, 100), BoundingBox(800, 900, 1000, 1000)), 1000, 1000, t
        )
        assertEquals(2, rects.size)
        assertRect(DisplayRect(0f, 0f, 50f, 50f), rects[0])
        assertRect(DisplayRect(400f, 450f, 500f, 500f), rects[1])
        assertRect(DisplayRect(0f, 0f, 500f, 500f), SourceCoordinateMapper.union(rects)!!)
        assertNull(SourceCoordinateMapper.union(emptyList()))
    }

    @Test
    fun outOfBoundsBoxesAreClampedAndInvalidOnesDropped() {
        assertEquals(BoundingBox(0, 0, 50, 60), SourceCoordinateMapper.sanitize(BoundingBox(-20, -5, 50, 60), 100, 100))
        assertEquals(BoundingBox(90, 90, 100, 100), SourceCoordinateMapper.sanitize(BoundingBox(90, 90, 150, 300), 100, 100))
        assertNull(SourceCoordinateMapper.sanitize(BoundingBox(50, 50, 40, 60), 100, 100)) // inverted
        assertNull(SourceCoordinateMapper.sanitize(BoundingBox(10, 10, 10, 20), 100, 100)) // zero width
        assertNull(SourceCoordinateMapper.sanitize(BoundingBox(200, 200, 300, 300), 100, 100)) // entirely outside
        val t = SourceCoordinateMapper.fit(100, 100, 100f, 100f)!!
        assertEquals(1, SourceCoordinateMapper.mapAll(listOf(BoundingBox(50, 50, 40, 60), BoundingBox(1, 1, 2, 2)), 100, 100, t).size)
    }

    @Test
    fun zeroOrNegativeSizesAreRejected() {
        assertNull(SourceCoordinateMapper.fit(0, 2000, 400f, 400f))
        assertNull(SourceCoordinateMapper.fit(1000, 0, 400f, 400f))
        assertNull(SourceCoordinateMapper.fit(1000, 2000, 0f, 400f))
        assertNull(SourceCoordinateMapper.fit(1000, 2000, 400f, 0f))
        assertNull(SourceCoordinateMapper.sanitize(BoundingBox(0, 0, 1, 1), 0, 10))
    }

    @Test
    fun verySmallBoxStaysSmallAndInPlace() {
        val r = mapOne(1414, 2000, 707f, 1000f, BoundingBox(700, 1000, 701, 1001))
        assertRect(DisplayRect(350f, 500f, 350.5f, 500.5f), r)
    }

    @Test
    fun floatingPointDoesNotMateriallyShiftGeometry() {
        // A box mapped then mapped back lands within a hundredth of a source pixel.
        listOf(1414 to 2000, 999 to 1333, 3024 to 4032, 1 to 7).forEach { (sw, sh) ->
            listOf(333f to 777f, 1080f to 2400f, 2400f to 1080f).forEach { (cw, ch) ->
                val t = SourceCoordinateMapper.fit(sw, sh, cw, ch)!!
                val box = BoundingBox(0, 0, sw, sh)
                val r = SourceCoordinateMapper.map(box, t)
                assertEquals(0f, (r.left - t.offsetX) / t.scale, 0.01f)
                assertEquals(sh.toFloat(), (r.bottom - t.offsetY) / t.scale, 0.01f)
                assertTrue(r.left >= -eps && r.right <= cw + 0.01f && r.top >= -eps && r.bottom <= ch + 0.01f)
            }
        }
    }

    @Test
    fun containerResizeRecomputesFromCanonicalCoordinates() {
        val box = BoundingBox(202, 1116, 1233, 1194)
        val portrait = mapOne(1414, 2000, 1080f, 2000f, box)
        val landscape = mapOne(1414, 2000, 2000f, 1080f, box)
        // Same canonical box, different containers → different, individually correct rects.
        val lt = SourceCoordinateMapper.fit(1414, 2000, 2000f, 1080f)!!
        assertEquals(1080f / 2000f, lt.scale, 1e-6f)
        assertRect(DisplayRect(lt.offsetX + 202 * lt.scale, 1116 * lt.scale, lt.offsetX + 1233 * lt.scale, 1194 * lt.scale), landscape)
        assertTrue(portrait != landscape)
    }

    @Test
    fun autoFocusZoomsOntoTheFocusAndStaysWithinThePage() {
        val t = SourceCoordinateMapper.fit(1000, 2000, 400f, 800f)!! // exact fit, scale 0.4
        val content = SourceCoordinateMapper.contentRect(1000, 2000, t)
        val focus = SourceCoordinateMapper.map(BoundingBox(400, 900, 600, 1100), t) // 80×80 in the middle
        val view = SourceCoordinateMapper.focusTransform(focus, content, 400f, 800f)

        assertEquals(4f, view.zoom, eps) // capped at maxZoom
        // The focus centre ends up at the container centre.
        val (cx, cy) = SourceCoordinateMapper.applyView(focus.centerX, focus.centerY, view)
        assertEquals(200f, cx, eps)
        assertEquals(400f, cy, eps)

        // A focus at the top-left corner is clamped so no area outside the page shows.
        val corner = SourceCoordinateMapper.map(BoundingBox(0, 0, 100, 100), t)
        val cornerView = SourceCoordinateMapper.focusTransform(corner, content, 400f, 800f)
        assertEquals(0f, cornerView.translationX, eps)
        assertEquals(0f, cornerView.translationY, eps)
    }

    @Test
    fun paddingIsSeparateAndClippedToThePage() {
        val bounds = DisplayRect(0f, 0f, 100f, 100f)
        assertRect(DisplayRect(0f, 5f, 30f, 45f), SourceCoordinateMapper.pad(DisplayRect(5f, 15f, 20f, 35f), 10f, bounds))
    }
}
