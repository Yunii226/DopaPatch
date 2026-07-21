package com.example.dopapatch.domain

import com.example.dopapatch.domain.markdown.MdAction
import com.example.dopapatch.domain.markdown.applyMd
import com.example.dopapatch.domain.markdown.insertMd
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownActionsTest {

    @Test fun bold_wraps_selection_and_keeps_it_selected() {
        val r = applyMd(MdAction.Bold, "hello world", 6, 11)
        assertEquals("hello **world**", r.text)
        assertEquals("world", r.text.substring(r.selStart, r.selEnd))
    }

    @Test fun bold_toggles_off_when_already_wrapped() {
        val r = applyMd(MdAction.Bold, "hello **world**", 8, 13)
        assertEquals("hello world", r.text)
    }

    @Test fun bold_with_empty_selection_leaves_cursor_between_markers() {
        val r = applyMd(MdAction.Bold, "ab", 1, 1)
        assertEquals("a****b", r.text)
        assertEquals(3, r.selStart)
    }

    @Test fun bullet_prefixes_every_line_in_selection_then_toggles_off() {
        val on = applyMd(MdAction.Bullet, "one\ntwo\nthree", 0, 7)
        assertEquals("- one\n- two\nthree", on.text)
        val off = applyMd(MdAction.Bullet, on.text, 2, 9)
        assertEquals("one\ntwo\nthree", off.text)
    }

    @Test fun heading_prefixes_only_the_cursor_line() {
        val r = applyMd(MdAction.H2, "a\nb", 2, 2)
        assertEquals("a\n## b", r.text)
    }

    @Test fun checkbox_prefix_is_the_gfm_form() {
        assertEquals("- [ ] task", applyMd(MdAction.Checkbox, "task", 0, 0).text)
    }

    @Test fun insert_puts_snippet_at_cursor() {
        val r = insertMd("ab", 1, "![](p.jpg)")
        assertEquals("a![](p.jpg)b", r.text)
        assertEquals(11, r.selStart)
    }

    @Test fun out_of_range_selection_is_clamped_not_crashing() {
        assertEquals("**ab**", applyMd(MdAction.Bold, "ab", -5, 99).text)
    }
}
