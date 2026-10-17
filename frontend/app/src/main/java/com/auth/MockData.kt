package com.example.superznalci

/**
 * Everything that is fake lives in this file (plus MockAuthRepository and MockProfileRepository).
 * The shapes are the same as the backend models, so deleting the mocks later changes nothing else.
 */
object MockData {

    val SCHOOLS = listOf(
        "OŠ Primoža Trubarja, Ljubljana",
        "OŠ Ivana Cankarja, Maribor",
        "OŠ Franceta Prešerna, Kranj"
    )

    // Demo accounts (password for both: geslo1234)
    const val DEMO_PASSWORD = "geslo1234"
    const val DEMO_TEACHER_EMAIL = "ucitelj@demo.si"
    const val DEMO_STUDENT_EMAIL = "ucenec@demo.si"
    const val DEMO_JOIN_CODE = "ZN-8AK42"

    // Badge catalog (backend: Badge collection)
    val BADGES = listOf(
        BadgeDto("badge-1", "Mojster ulomkov", "Opravljene vse vaje brez napake"),
        BadgeDto("badge-2", "Ogenj znanja", "5-dnevna serija učenja"),
        BadgeDto("badge-3", "Hitri račun", "Lekcija v manj kot 2 min"),
        BadgeDto("badge-4", "Kahoot Prvak", "1. mesto na tedenski lestvici"),
        BadgeDto("badge-5", "Zvezda tedna", "Zbranih 5000 XP")
    )

    // Subject names must equal the subject_* strings of the registration chips,
    // because the teacher registration sends names and the backend resolves them to ids
    private class SubjectSpec(
        val key: String,
        val name: String,
        val icon: String,
        val topics: List<String>
    )

    private val SPECS = listOf(
        SubjectSpec("math", "Matematika", "calculate", listOf("Ulomki", "Enačbe", "Geometrija")),
        SubjectSpec("physics", "Fizika", "science", listOf("Gibanje", "Sile", "Energija")),
        SubjectSpec("chemistry", "Kemija", "science", listOf("Snovi", "Atomi", "Reakcije")),
        SubjectSpec("biology", "Biologija", "eco", listOf("Celica", "Rastline", "Živali"))
    )

    private val GRADES = 6..9
    private const val LESSONS_PER_TOPIC = 4

    fun subjectId(key: String) = "subject-$key"
    private fun topicId(key: String, grade: Int, topic: Int) = "topic-$key-$grade-$topic"
    fun lessonId(key: String, grade: Int, topic: Int, number: Int) = "lesson-$key-$grade-$topic-$number"

    // Subject collection: every subject has topics for grades 6-9
    val SUBJECTS: List<SubjectDto> = SPECS.map { spec ->
        SubjectDto(
            id = subjectId(spec.key),
            name = spec.name,
            icon = spec.icon,
            grades = GRADES.map { grade ->
                GradeDto(
                    gradeNumber = grade,
                    topics = spec.topics.mapIndexed { index, name ->
                        TopicDto(topicId(spec.key, grade, index + 1), name)
                    }
                )
            }
        )
    }

    // Lesson collection (without questions): 3 topics x 4 lessons per subject and grade
    val LESSONS: List<LessonSummaryDto> = SPECS.flatMap { spec ->
        GRADES.flatMap { grade ->
            spec.topics.indices.flatMap { topicIndex ->
                (1..LESSONS_PER_TOPIC).map { number ->
                    LessonSummaryDto(
                        id = lessonId(spec.key, grade, topicIndex + 1, number),
                        topicId = topicId(spec.key, grade, topicIndex + 1),
                        name = "${spec.topics[topicIndex]} $number",
                        order = number
                    )
                }
            }
        }
    }
}