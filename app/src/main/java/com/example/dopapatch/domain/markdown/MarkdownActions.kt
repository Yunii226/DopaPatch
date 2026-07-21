package com.example.dopapatch.domain.markdown

/**
 * Toolbar Markdown editing — pure text math, no Compose/Android types, so it unit-tests.
 * Ceiling (see PLAN Phase 8): this inserts Markdown *syntax*, it is not a WYSIWYG model.
 */
data class MdEdit(val text: String, val selStart: Int, val selEnd: Int)

enum class MdAction(internal val wrap: String? = null, internal val prefix: String? = null) {
    Bold(wrap = "**"),
    Italic(wrap = "_"),          // `_` so toggling italic inside **bold** doesn't eat one `*`
    H1(prefix = "# "),
    H2(prefix = "## "),
    Bullet(prefix = "- "),
    Number(prefix = "1. "),
    Checkbox(prefix = "- [ ] "),
}

/** Apply [action] to [text] over the selection [start]..[end]. Toggles off if already applied. */
fun applyMd(action: MdAction, text: String, start: Int, end: Int): MdEdit {
    val s = start.coerceIn(0, text.length)
    val e = end.coerceIn(s, text.length)
    return action.wrap?.let { wrapSelection(it, text, s, e) } ?: prefixLines(action.prefix!!, text, s, e)
}

/** Insert [snippet] at [pos] (used for images); cursor lands after it. */
fun insertMd(text: String, pos: Int, snippet: String): MdEdit {
    val p = pos.coerceIn(0, text.length)
    return MdEdit(text.substring(0, p) + snippet + text.substring(p), p + snippet.length, p + snippet.length)
}

private fun wrapSelection(m: String, text: String, s: Int, e: Int): MdEdit {
    val wrapped = s >= m.length && e + m.length <= text.length &&
        text.regionMatches(s - m.length, m, 0, m.length) && text.regionMatches(e, m, 0, m.length)
    if (wrapped) {
        val out = text.removeRange(e, e + m.length).removeRange(s - m.length, s)
        return MdEdit(out, s - m.length, e - m.length)
    }
    val out = text.substring(0, s) + m + text.substring(s, e) + m + text.substring(e)
    return MdEdit(out, s + m.length, e + m.length)
}

private fun prefixLines(p: String, text: String, s: Int, e: Int): MdEdit {
    val lineStart = text.lastIndexOf('\n', s - 1) + 1
    val lineEnd = text.indexOf('\n', e).let { if (it < 0) text.length else it }
    val block = text.substring(lineStart, lineEnd)
    val lines = block.split("\n")
    val on = lines.all { it.startsWith(p) }          // all prefixed => toggle off
    val newBlock = lines.joinToString("\n") { if (on) it.removePrefix(p) else p + it }
    val out = text.substring(0, lineStart) + newBlock + text.substring(lineEnd)
    val shift = if (on) -p.length else p.length
    return MdEdit(
        out,
        (s + shift).coerceIn(lineStart, out.length),
        (e + newBlock.length - block.length).coerceIn(lineStart, out.length),
    )
}
