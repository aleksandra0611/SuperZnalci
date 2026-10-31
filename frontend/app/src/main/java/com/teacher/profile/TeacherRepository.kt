package com.example.superznalci

interface TeacherRepository {
    fun loadProfile(callback: ResultCallback<TeacherProfileData>)
    fun updateSettings(settings: TeacherSettingsDto, callback: ResultCallback<TeacherSettingsDto>)
}

interface TeacherExtrasSource {
    fun load(classroomIds: List<String>, callback: ResultCallback<TeacherExtrasDto>)
    fun updateSettings(settings: TeacherSettingsDto, callback: ResultCallback<TeacherSettingsDto>)
}

class RetrofitTeacherExtrasSource(private val api: ApiService) : TeacherExtrasSource {
    override fun load(classroomIds: List<String>, callback: ResultCallback<TeacherExtrasDto>) {
        api.teacherExtras().go({ callback(ApiResult.Failure(it)) }) { callback(ApiResult.Success(it)) }
    }

    override fun updateSettings(settings: TeacherSettingsDto, callback: ResultCallback<TeacherSettingsDto>) {
        api.updateSettings(settings).go({ callback(ApiResult.Failure(it)) }) { callback(ApiResult.Success(it)) }
    }
}

class MockTeacherExtrasSource : TeacherExtrasSource {

    private var settings = TeacherSettingsDto(emailNotifications = true, weeklyReport = true)

    override fun load(classroomIds: List<String>, callback: ResultCallback<TeacherExtrasDto>) {
        callback(
            ApiResult.Success(
                TeacherExtrasDto(
                    isVerified = true,
                    quizzesCreated = 18,
                    averageAccuracy = 84.0,
                    accuracyDelta = 4.2,
                    classrooms = classroomIds.mapIndexed { i, id ->
                        ClassroomExtraDto(id, if (i % 2 == 0) Shifts.MORNING else Shifts.AFTERNOON)
                    },
                    settings = settings
                )
            )
        )
    }

    override fun updateSettings(settings: TeacherSettingsDto, callback: ResultCallback<TeacherSettingsDto>) {
        this.settings = settings
        callback(ApiResult.Success(settings))
    }
}

class RetrofitTeacherRepository(
    private val api: ApiService,
    private val extras: TeacherExtrasSource
) : TeacherRepository {

    private class Barrier(
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

    override fun loadProfile(callback: ResultCallback<TeacherProfileData>) {
        val fail: (Int?) -> Unit = { callback(ApiResult.Failure(it)) }

        api.me().go(fail) { userEnvelope ->
            val user = userEnvelope.user
            SessionStore.token?.let { SessionStore.save(it, user) }

            api.myClassrooms().go(fail) { classroomsEnvelope ->
                val classrooms = classroomsEnvelope.classrooms.orEmpty()
                val subjectIds = classrooms.flatMap { it.subjectIds.orEmpty() }.distinct()

                val counts = HashMap<String, Int>()
                var subjects: List<SubjectDto> = emptyList()
                var extrasDto = TeacherExtrasDto()

                val calls = classrooms.size + (if (subjectIds.isNotEmpty()) 1 else 0) + 1
                val barrier = Barrier(calls, fail) {
                    callback(
                        ApiResult.Success(buildProfile(user, classrooms, counts, subjects, extrasDto))
                    )
                }

                classrooms.forEach { c ->
                    api.classroomStudents(c.id).go(barrier::fail) { env ->
                        counts[c.id] = env.students.orEmpty().size
                        barrier.ok()
                    }
                }

                if (subjectIds.isNotEmpty()) {
                    api.subjects(subjectIds.joinToString(","), null).go(barrier::fail) { env ->
                        subjects = env.subjects.orEmpty()
                        barrier.ok()
                    }
                }

                extras.load(classrooms.map { it.id }) { result ->
                    when (result) {
                        is ApiResult.Success -> {
                            extrasDto = result.data
                            barrier.ok()
                        }
                        is ApiResult.Failure ->
                            if (result.httpCode == 401) barrier.fail(401) else barrier.ok()
                    }
                }
            }
        }
    }

    override fun updateSettings(settings: TeacherSettingsDto, callback: ResultCallback<TeacherSettingsDto>) {
        extras.updateSettings(settings, callback)
    }

    private fun buildProfile(
        user: UserDto,
        classrooms: List<ClassroomDto>,
        counts: Map<String, Int>,
        subjects: List<SubjectDto>,
        extras: TeacherExtrasDto
    ): TeacherProfileData {
        val subjectsById = subjects.associateBy { it.id }
        val shiftById = extras.classrooms.orEmpty().associate { it.classroomId to it.shift }

        val classes = classrooms.map { c ->
            TeacherClassUi(
                classroom = c,
                studentCount = counts[c.id] ?: 0,
                subjectNames = c.subjectIds.orEmpty().mapNotNull { subjectsById[it]?.name },
                shift = shiftById[c.id]
            )
        }

        val seen = LinkedHashSet<Pair<String, Int>>()
        val chips = ArrayList<TeacherSubjectChip>()
        classrooms.forEach { c ->
            c.subjectIds.orEmpty().forEach { sid ->
                val subject = subjectsById[sid] ?: return@forEach
                if (seen.add(sid to c.gradeNumber)) {
                    val modules = subject.grades.orEmpty()
                        .firstOrNull { it.gradeNumber == c.gradeNumber }
                        ?.topics.orEmpty().size
                    chips += TeacherSubjectChip("${subject.name} ${c.gradeNumber}", modules)
                }
            }
        }

        val school = classrooms.firstOrNull()
            ?.let { ClassroomNaming.parse(it.name).second }
            .orEmpty()

        return TeacherProfileData(
            user = user,
            schoolName = school,
            classes = classes,
            totalStudents = classes.sumOf { it.studentCount },
            subjectChips = chips,
            gradeNumber = classrooms.firstOrNull()?.gradeNumber,
            extras = extras,
            settings = extras.settings ?: TeacherSettingsDto()
        )
    }
}
