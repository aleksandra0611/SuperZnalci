package com.example.superznalci

/**
 * Everything that is fake lives in this file (plus the Mock*Repository classes).
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
    const val DEMO_STUDENT_COUNT = 24

    // Badge catalog (backend: Badge collection)
    val BADGES = listOf(
        BadgeDto("badge-1", "Mojster ulomkov", "Opravljene vse vaje brez napake"),
        BadgeDto("badge-2", "Ogenj znanja", "5-dnevna serija učenja"),
        BadgeDto("badge-3", "Hitri račun", "Lekcija v manj kot 2 min"),
        BadgeDto("badge-4", "Kahoot Prvak", "1. mesto na tedenski lestvici"),
        BadgeDto("badge-5", "Zvezda tedna", "Zbranih 5000 XP")
    )

    // Subject names must equal the subject_* strings of the registration chips,
    // because the teacher registration sends names and the backend resolves them to ids.
    // (Slovenščina and Angleščina are new: if the chips do not have them, nothing breaks.)
    private class SubjectSpec(
        val key: String,
        val name: String,
        val icon: String,
        val topics: List<String>
    )

    private val SPECS = listOf(
        SubjectSpec("math", "Matematika", "calculate", listOf("Ulomki", "Enačbe", "Geometrija")),
        SubjectSpec("slovene", "Slovenščina", "menu_book", listOf("Slovnica", "Besedila", "Pravopis")),
        SubjectSpec("english", "Angleščina", "public", listOf("Vocabulary", "Grammar")),
        SubjectSpec("physics", "Fizika", "science", listOf("Gibanje", "Sile", "Energija")),
        SubjectSpec("chemistry", "Kemija", "science", listOf("Snovi", "Atomi", "Reakcije")),
        SubjectSpec("biology", "Biologija", "eco", listOf("Celica", "Rastline", "Živali"))
    )

    private val GRADES = 6..9
    private const val LESSONS_PER_TOPIC = 4

    // Every mock lesson has 8 questions worth 10 points (the demo attempts use the same numbers)
    private const val QUESTIONS_PER_LESSON = 8
    private const val POINTS_PER_QUESTION = 10

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

    // Lesson collection (without questions): public lessons, 4 per topic, subject and grade
    private val PUBLIC_LESSONS: List<LessonSummaryDto> = SPECS.flatMap { spec ->
        GRADES.flatMap { grade ->
            spec.topics.indices.flatMap { topicIndex ->
                (1..LESSONS_PER_TOPIC).map { number ->
                    LessonSummaryDto(
                        id = lessonId(spec.key, grade, topicIndex + 1, number),
                        topicId = topicId(spec.key, grade, topicIndex + 1),
                        name = "${spec.topics[topicIndex]} $number",
                        order = number,
                        isPublic = true,
                        questionCount = QUESTIONS_PER_LESSON,
                        totalPoints = QUESTIONS_PER_LESSON * POINTS_PER_QUESTION,
                        description = "Vaje iz teme ${spec.topics[topicIndex]}, lekcija $number."
                    )
                }
            }
        }
    }

    // Teacher-made lessons (isPublic = false): visible only when assigned to the classroom
    private val ASSIGNED_HOMEWORK_ID = lessonId("math", 8, 1, 5)
    private val UNASSIGNED_LESSON_ID = lessonId("math", 8, 1, 6)

    private val TEACHER_LESSONS = listOf(
        LessonSummaryDto(
            id = ASSIGNED_HOMEWORK_ID,
            topicId = topicId("math", 8, 1),
            name = "Domača naloga: ulomki",
            order = 5,
            isPublic = false,
            questionCount = QUESTIONS_PER_LESSON,
            totalPoints = QUESTIONS_PER_LESSON * POINTS_PER_QUESTION,
            description = "Naloga učitelja za utrjevanje ulomkov."
        ),
        // never assigned in the mock -> must NOT appear in the app
        LessonSummaryDto(
            id = UNASSIGNED_LESSON_ID,
            topicId = topicId("math", 8, 1),
            name = "Skrita lekcija",
            order = 6,
            isPublic = false,
            questionCount = QUESTIONS_PER_LESSON,
            totalPoints = QUESTIONS_PER_LESSON * POINTS_PER_QUESTION
        )
    )

    val LESSONS: List<LessonSummaryDto> = PUBLIC_LESSONS + TEACHER_LESSONS

    // Assignment collection of the demo classroom: (lessonId, due date in N days or null)
    val DEMO_ASSIGNMENTS: List<Pair<String, Int?>> = listOf(
        ASSIGNED_HOMEWORK_ID to 3,
        lessonId("physics", 8, 1, 3) to 5
    )
}
