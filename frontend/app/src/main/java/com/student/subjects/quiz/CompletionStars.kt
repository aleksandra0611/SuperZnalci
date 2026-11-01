package com.example.superznalci

import android.content.Context

object CompletionStars {
    private fun prefs(c: Context) = c.getSharedPreferences("completion_stars", Context.MODE_PRIVATE)
    fun mark(c: Context, userId: String, lessonId: String) =
        prefs(c).edit().putBoolean("$userId:$lessonId", true).apply()
    fun has(c: Context, userId: String, lessonId: String) =
        prefs(c).getBoolean("$userId:$lessonId", false)
}