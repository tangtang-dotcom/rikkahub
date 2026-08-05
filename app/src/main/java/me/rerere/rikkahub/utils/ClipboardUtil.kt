package me.rerere.rikkahub.utils

import android.content.ClipData

fun ClipData.getText(): String =
    buildString {
        repeat(itemCount) {
            append(getItemAt(it).text ?: "")
        }
    }
