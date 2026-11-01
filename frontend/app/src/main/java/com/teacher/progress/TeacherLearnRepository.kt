package com.example.superznalci

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

private const val LEARN_DAY = 86_400_000L

// sources

interface TeacherLearnSource {
    fun loadOverview(classroomId: String, studentCount: Int, callback: ResultCallback<ClassOverviewDto>)
    fun loadStruggling(classroomId: String, callback: ResultCallback<List<StrugglingStudentDto>>)
    fun sendReminder(classroomId: String, studentId: String, callback: ResultCallback<Unit>)
    fun assignHomework(
        classroomId: String,
        lessonId: String,
        dueDateIso: String,
        allowRetry: Boolean,
        callback: ResultCallback<Unit>
    )
}

class RetrofitTeacherLearnSource(private val api: ApiService) : TeacherLearnSource {

    override fun loadOverview(
        classroomId: String, studentCount: Int, callback: ResultCallback<ClassOverviewDto>
    ) {
        api.classOverview(classroomId).go({ callback(ApiResult.Failure(it)) }) {
            callback(ApiResult.Success(it))
        }
    }

    override fun loadStruggling(
        classroomId: String, callback: ResultCallback<List<StrugglingStudentDto>>
    ) {
        api.strugglingStudents(classroomId).go({ callback(ApiResult.Failure(it)) }) {
            callback(ApiResult.Success(it.students.orEmpty()))
        }
    }

    override fun sendReminder(classroomId: String, studentId: String, callback: ResultCallback<Unit>) {
        api.sendReminder(classroomId, ReminderRequest(studentId)).go({ callback(ApiResult.Failure(it)) }) {
            callback(ApiResult.Success(Unit))
        }
    }

    override fun assignHomework(
        classroomId: String, lessonId: String, dueDateIso: String, allowRetry: Boolean,
        callback: ResultCallback<Unit>
    ) {
        api.putAssignment(
            classroomId, lessonId,
            AssignmentRequest(availableFrom = null, dueDate = dueDateIso, allowRetry = allowRetry)
        ).go({ callback(ApiResult.Failure(it)) }) { callback(ApiResult.Success(Unit)) }
    }
}


class MockTeacherLearnSource(private val content: ClassroomContentSource) : TeacherLearnSource {

    private class Homework(val classroomId: String, val lessonId: String, val dueDate: String, val allowRetry: Boolean)
    private val homework = mutableListOf<Homework>()

    override fun loadOverview(
        classroomId: String, studentCount: Int, callback: ResultCallback<ClassOverviewDto>
    ) {
        callback(
            ApiResult.Success(
                ClassOverviewDto(
                    weekActive = true,
                    completedCount = (studentCount * 0.79).roundToInt(),
                    totalStudents = studentCount,
                    needHelpCount = minOf(3, studentCount)
                )
            )
        )
    }

    override fun loadStruggling(
        classroomId: String, callback: ResultCallback<List<StrugglingStudentDto>>
    ) {
        val now = System.currentTimeMillis()
        callback(
            ApiResult.Success(
                listOf(
                    StrugglingStudentDto(
                        studentId = "mock-student-1",
                        name = "Sara B.",
                        reason = StruggleReason.MISTAKES,
                        mistakeCount = 2,
                        lessonName = "Lekcija 2 • Seštevanje z različnimi imenovalci",
                        lastActiveAt = iso(now - LEARN_DAY)
                    ),
                    StrugglingStudentDto(
                        studentId = "mock-student-2",
                        name = "Tim K.",
                        reason = StruggleReason.NOT_STARTED,
                        lastActiveAt = iso(now - 4 * LEARN_DAY)
                    )
                )
            )
        )
    }

    override fun sendReminder(classroomId: String, studentId: String, callback: ResultCallback<Unit>) {
        callback(ApiResult.Success(Unit))
    }

    override fun assignHomework(
        classroomId: String, lessonId: String, dueDateIso: String, allowRetry: Boolean,
        callback: ResultCallback<Unit>
    ) {
        homework.removeAll { it.classroomId == classroomId && it.lessonId == lessonId }
        homework += Homework(classroomId, lessonId, dueDateIso, allowRetry)
        content.putAssignment(classroomId, lessonId, null) { result ->
            when (result) {
                is ApiResult.Success -> callback(ApiResult.Success(Unit))
                is ApiResult.Failure -> callback(ApiResult.Failure(result.httpCode))
            }
        }
    }

    private fun iso(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(millis))
}

// repository

interface TeacherLearnRepository {
    fun loadClasses(callback: ResultCallback<List<TeacherClassTab>>)
    fun loadClass(tab: TeacherClassTab, callback: ResultCallback<TeacherLearnData>)
    fun assignHomework(
        classroomId: String, lessonId: String, dueDateIso: String, allowRetry: Boolean,
        callback: ResultCallback<Unit>
    )
    fun unlockModule(classroomId: String, module: PathModuleUi, callback: ResultCallback<Unit>)
    fun sendReminder(classroomId: String, studentId: String, callback: ResultCallback<Unit>)
}

class RetrofitTeacherLearnRepository(
    private val subjects: TeacherSubjectsRepository,
    private val source: TeacherLearnSource
) : TeacherLearnRepository {

    private class Join(
        private var pending: Int,
        private val onFail: (Int?) -> Unit,
        private val onDone: () -> Unit
    ) {
        private var failed = false
        fun ok() {
            if (failed) return
            if (--pending == 0) onDone()
        }
        fun fail(code: Int?) {
            if (failed) return
            failed = true
            onFail(code)
        }
    }

    override fun loadClasses(callback: ResultCallback<List<TeacherClassTab>>) =
        subjects.loadClasses(callback)

    override fun loadClass(tab: TeacherClassTab, callback: ResultCallback<TeacherLearnData>) {
        val fail: (Int?) -> Unit = { callback(ApiResult.Failure(it)) }
        var subjectList: List<SubjectUi> = emptyList()
        var overview = ClassOverviewDto(totalStudents = tab.studentCount)
        var struggling: List<StrugglingStudentDto> = emptyList()

        val join = Join(3, fail) {
            callback(ApiResult.Success(build(tab, subjectList, overview, struggling)))
        }
        val soft: (Int?) -> Unit = { if (it == 401) join.fail(it) else join.ok() }

        subjects.loadClassSubjects(tab.classroom) { r ->
            when (r) {
                is ApiResult.Success -> { subjectList = r.data; join.ok() }
                is ApiResult.Failure -> join.fail(r.httpCode)
            }
        }
        source.loadOverview(tab.classroom.id, tab.studentCount) { r ->
            when (r) {
                is ApiResult.Success -> { overview = r.data; join.ok() }
                is ApiResult.Failure -> soft(r.httpCode)
            }
        }
        source.loadStruggling(tab.classroom.id) { r ->
            when (r) {
                is ApiResult.Success -> { struggling = r.data; join.ok() }
                is ApiResult.Failure -> soft(r.httpCode)
            }
        }
    }

    override fun assignHomework(
        classroomId: String, lessonId: String, dueDateIso: String, allowRetry: Boolean,
        callback: ResultCallback<Unit>
    ) = source.assignHomework(classroomId, lessonId, dueDateIso, allowRetry, callback)

    override fun unlockModule(classroomId: String, module: PathModuleUi, callback: ResultCallback<Unit>) {
        val ids = module.scheduledLessonIds
        if (ids.isEmpty()) {
            callback(ApiResult.Success(Unit))
            return
        }
        val join = Join(ids.size, { callback(ApiResult.Failure(it)) }) { callback(ApiResult.Success(Unit)) }
        ids.forEach { id ->
            subjects.setLessonAvailability(classroomId, id, null) { r ->
                when (r) {
                    is ApiResult.Success -> join.ok()
                    is ApiResult.Failure -> join.fail(r.httpCode)
                }
            }
        }
    }

    override fun sendReminder(classroomId: String, studentId: String, callback: ResultCallback<Unit>) =
        source.sendReminder(classroomId, studentId, callback)

    // build

    private fun build(
        tab: TeacherClassTab,
        subjects: List<SubjectUi>,
        overview: ClassOverviewDto,
        struggling: List<StrugglingStudentDto>
    ): TeacherLearnData {
        val modules = ArrayList<PathModuleUi>()
        val homework = ArrayList<HomeworkLessonUi>()

        subjects.forEach { subject ->
            subject.modules.forEach { m ->
                val published = m.lessons.filter { it.status == LessonStatus.PUBLISHED }
                val scheduled = m.lessons.filter { it.status == LessonStatus.SCHEDULED }
                val state = when {
                    m.isActive -> PathState.OPEN
                    scheduled.isNotEmpty() -> PathState.LOCKED
                    else -> PathState.DRAFT
                }
                val accuracies = published.mapNotNull { it.averageAccuracy }
                modules += PathModuleUi(
                    subjectName = subject.name,
                    topicId = m.topicId,
                    number = m.number,
                    name = m.name,
                    state = state,
                    lessonCount = m.lessons.size,
                    averageAccuracy = if (accuracies.isEmpty()) null else accuracies.average().roundToInt(),
                    unlockAtMillis = scheduled.mapNotNull { IsoDate.parse(it.availableFrom) }.minOrNull(),
                    scheduledLessonIds = scheduled.map { it.id },
                    lessonIds = m.lessons.map { it.id }
                )
                // Domačo nalogo je mogoče dodeliti tudi iz modula »Osnutek«.
                // Sicer je seznam vedno prazen, če naloga še ni bila dodeljena.
                m.lessons.forEach { l ->
                    homework += HomeworkLessonUi(
                        l.id, m.topicId, "${subject.name} / ${m.name} / ${l.name}"
                    )
                }
            }
        }

        return TeacherLearnData(
            classroom = tab.classroom,
            modules = modules,
            homeworkLessons = homework,
            overview = overview.copy(
                totalStudents = if (overview.totalStudents > 0) overview.totalStudents else tab.studentCount
            ),
            struggling = struggling
        )
    }
}