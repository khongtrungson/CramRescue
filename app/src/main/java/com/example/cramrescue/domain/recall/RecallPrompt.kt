package com.example.cramrescue.domain.recall

/**
 * What the learner sees before revealing a card (FR-11). A presentation of the card's immutable
 * content — never stored, never a modified copy of the card.
 */
sealed interface RecallPrompt {

    /**
     * 1–2 core keywords of bullet [bulletIndex] are hidden. [hiddenRanges] are sorted,
     * non-overlapping character ranges into that bullet's original text.
     */
    data class Cloze(val bulletIndex: Int, val hiddenRanges: List<IntRange>) : RecallPrompt

    /** No reliable keyword could be chosen locally: recall the card's main points instead. */
    data object General : RecallPrompt
}

sealed interface ClozeSegment {
    data class Visible(val text: String) : ClozeSegment
    data class Hidden(val text: String) : ClozeSegment
}

/** Splits [bullet] into visible and hidden segments for rendering. */
fun RecallPrompt.Cloze.segments(bullet: String): List<ClozeSegment> = buildList {
    var cursor = 0
    hiddenRanges.forEach { range ->
        if (range.first > cursor) add(ClozeSegment.Visible(bullet.substring(cursor, range.first)))
        add(ClozeSegment.Hidden(bullet.substring(range.first, range.last + 1)))
        cursor = range.last + 1
    }
    if (cursor < bullet.length) add(ClozeSegment.Visible(bullet.substring(cursor)))
}

/** The distinct hidden keywords — the answer. */
fun RecallPrompt.Cloze.answers(bullet: String): List<String> =
    hiddenRanges.map { bullet.substring(it.first, it.last + 1) }.distinct()
