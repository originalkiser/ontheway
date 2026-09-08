package com.reststop.countdown.domain.util

/** Directions API `html_instructions` are simple HTML (e.g. "Turn <b>left</b> onto Main St"). */
object HtmlText {
    private val tagRegex = Regex("<[^>]*>")
    private val whitespaceRegex = Regex("\\s+")

    fun strip(html: String): String =
        tagRegex.replace(html, "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .let { whitespaceRegex.replace(it, " ") }
            .trim()
}
