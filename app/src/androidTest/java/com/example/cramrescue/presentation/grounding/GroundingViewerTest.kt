package com.example.cramrescue.presentation.grounding

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cramrescue.domain.model.BoundingBox
import com.example.cramrescue.ui.theme.GroundingHighlight
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks the drawn pixels: highlights must land where SourceCoordinateMapper says, on top of
 * the page, including letterbox offsets, several boxes, and a container resize.
 */
@RunWith(AndroidJUnit4::class)
class GroundingViewerTest {

    @get:Rule
    val compose = createComposeRule()

    private val page: Bitmap = Bitmap.createBitmap(1000, 2000, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }

    private fun Bitmap.at(x: Int, y: Int) = Color(getPixel(x, y))

    private fun Color.isHighlightStroke() =
        abs(red - GroundingHighlight.red) < 0.1f && abs(green - GroundingHighlight.green) < 0.1f && abs(blue - GroundingHighlight.blue) < 0.1f

    /** White page tinted by the translucent highlight fill. */
    private fun Color.isTintedFill() = red > 0.95f && blue < 0.9f && blue > 0.5f

    private fun Color.isWhitePage() = red > 0.97f && green > 0.97f && blue > 0.97f

    @Test
    fun boxesAreDrawnAtMappedPositionsIncludingLetterboxAndResize() {
        var containerPx by mutableStateOf(400)
        val boxes = listOf(BoundingBox(100, 200, 300, 400), BoundingBox(600, 1500, 900, 1800))

        compose.setContent {
            val side = with(LocalDensity.current) { containerPx.toDp() }
            Box(Modifier.size(side).background(Color.Black)) {
                GroundingViewer(page, 1000, 2000, boxes, Modifier.size(side), autoFocus = false)
            }
        }

        // 1000×2000 in 400×400: scale 0.2, offsetX 100 (horizontal letterbox).
        var shot = compose.onNodeWithTag("grounding-viewer").captureToImage().asAndroidBitmap()
        assertEquals(400, shot.width)
        assertTrue("letterbox left of page", shot.at(50, 200).red < 0.1f)
        assertTrue("page is drawn", shot.at(200, 20).isWhitePage())
        // Box 1 → (120,40)-(160,80): stroke on its left edge, tinted fill inside, page outside.
        // The 3 px stroke is centred on the edge x=120 (covers 118.5–121.5): pixels 119 and 120 are fully inside it.
        listOf(119, 120).forEach { x -> assertTrue("box 1 stroke at $x: ${shot.at(x, 60)}", shot.at(x, 60).isHighlightStroke()) }
        assertTrue("box 1 fill", shot.at(140, 60).isTintedFill())
        assertTrue("left of box 1", shot.at(114, 60).isWhitePage())
        // Box 2 → (220,300)-(280,360), drawn independently (no giant union rectangle between them).
        assertTrue("box 2 fill", shot.at(250, 330).isTintedFill())
        assertTrue("between the boxes", shot.at(190, 200).isWhitePage())

        // Resize to 800×800: recomputed from canonical coordinates → scale 0.4, offsetX 200.
        containerPx = 800
        compose.waitForIdle()
        shot = compose.onNodeWithTag("grounding-viewer").captureToImage().asAndroidBitmap()
        // Box 1 → (240,80)-(320,160).
        assertTrue("box 1 fill after resize", shot.at(280, 120).isTintedFill())
        assertTrue("old position no longer highlighted", shot.at(140, 60).let { it.isWhitePage() || it.red < 0.1f })
    }

    @Test
    fun autoFocusBringsTheCitedRegionToTheCentre() {
        compose.setContent {
            val side = with(LocalDensity.current) { 400.toDp() }
            GroundingViewer(page, 1000, 2000, listOf(BoundingBox(700, 1700, 800, 1800)), Modifier.size(side))
        }
        val shot = compose.onNodeWithTag("grounding-viewer").captureToImage().asAndroidBitmap()
        // Without focus the box would be a tiny 20×20 square near the bottom right; with focus its fill covers the centre.
        assertTrue(shot.at(200, 200).isTintedFill())
    }
}
