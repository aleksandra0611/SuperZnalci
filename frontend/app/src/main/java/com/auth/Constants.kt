package com.example.superznalci

object ClassCode {
    val PATTERN = Regex("^ZN-[1-9][A-Z]{2}\\d{2}$")

    fun normalize(s: String) = s.trim().uppercase()
    fun isWellFormed(s: String) = PATTERN.matches(normalize(s))


    fun gradeFromLabel(label: String): Int =
        label.trim().takeWhile { it.isDigit() }.toIntOrNull()?.coerceIn(1, 9) ?: 8
}

/**
 * Role strings as they travel over the wire (backend enum values),
 * plus the lowercase keys used by [LoginFragment]'s fragment result
 */
object Roles {
    const val STUDENT = "STUDENT"
    const val TEACHER = "TEACHER"
    const val ADMIN   = "ADMIN"

    const val KEY_STUDENT = "student"
    const val KEY_TEACHER = "teacher"

    fun toKey(backendRole: String?): String = when (backendRole?.uppercase()) {
        TEACHER -> KEY_TEACHER
        else    -> KEY_STUDENT
    }
}