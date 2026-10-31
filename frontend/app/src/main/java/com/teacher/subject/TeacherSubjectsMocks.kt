package com.example.superznalci

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

interface ClassroomContentSource {
    fun loadAssignments(classroomId: String, lessonIds: List<String>, callback: ResultCallback<List<ClassroomAssignmentDto>>)
    fun putAssignment(
        classroomId: String,
        lessonId: String,
        availableFrom: String?,
        callback: ResultCallback<ClassroomAssignmentDto>
    )
    fun loadLessonStats(classroomId: String, studentCount: Int, callback: ResultCallback<List<LessonStatDto>>)
}

class RetrofitClassroomContentSource(private val api: ApiService) : ClassroomContentSource {

    override fun loadAssignments(
        classroomId: String, lessonIds: List<String>, callback: ResultCallback<List<ClassroomAssignmentDto>>
    ) {
        api.assignments(classroomId).go({ callback(ApiResult.Failure(it)) }) {
            callback(ApiResult.Success(it.assignments.orEmpty()))
        }
    }

    override fun putAssignment(
        classroomId: String, lessonId: String, availableFrom: String?, callback: ResultCallback<ClassroomAssignmentDto>
    ) {
        api.putAssignment(classroomId, lessonId, AssignmentRequest(availableFrom = availableFrom))
            .go({ callback(ApiResult.Failure(it)) }) { env ->
                val a = env.assignment
                if (a == null) callback(ApiResult.Failure(null)) else callback(ApiResult.Success(a))
            }
    }

    override fun loadLessonStats(
        classroomId: String, studentCount: Int, callback: ResultCallback<List<LessonStatDto>>
    ) {
        api.lessonStats(classroomId).go({ callback(ApiResult.Failure(it)) }) {
            callback(ApiResult.Success(it.lessons.orEmpty()))
        }
    }
}

class MockClassroomContentSource : ClassroomContentSource {

    private val assignments = mutableListOf<ClassroomAssignmentDto>()
    private val seeded = mutableSetOf<String>()
    private var nextId = 1

    override fun loadAssignments(
        classroomId: String, lessonIds: List<String>, callback: ResultCallback<List<ClassroomAssignmentDto>>
    ) {
        if (seeded.add(classroomId)) {
            lessonIds.forEachIndexed { i, id ->
                when (i) {
                    0, 1 -> store(classroomId, id, null)
                    2 -> store(classroomId, id, iso(System.currentTimeMillis() + 14 * DAY))
                }
            }
        }
        callback(ApiResult.Success(assignments.filter { it.classroomId == classroomId }))
    }

    override fun putAssignment(
        classroomId: String, lessonId: String, availableFrom: String?, callback: ResultCallback<ClassroomAssignmentDto>
    ) {
        callback(ApiResult.Success(store(classroomId, lessonId, availableFrom)))
    }

    override fun loadLessonStats(
        classroomId: String, studentCount: Int, callback: ResultCallback<List<LessonStatDto>>
    ) {
        val now = System.currentTimeMillis()
        val stats = assignments
            .filter { it.classroomId == classroomId }
            .filter { a -> IsoDate.parse(a.availableFrom)?.let { it <= now } ?: true }
            .map { a ->
                val h = abs(a.lessonId.hashCode())
                val percent = 60 + h % 40
                LessonStatDto(
                    lessonId = a.lessonId,
                    completedCount = studentCount * percent / 100,
                    completionPercent = percent,
                    averageAccuracy = 55 + (h / 7) % 40
                )
            }
        callback(ApiResult.Success(stats))
    }

    private fun store(classroomId: String, lessonId: String, availableFrom: String?): ClassroomAssignmentDto {
        val old = assignments.firstOrNull { it.classroomId == classroomId && it.lessonId == lessonId }
        val doc = ClassroomAssignmentDto(
            id = old?.id ?: "mock-${nextId++}",
            classroomId = classroomId,
            lessonId = lessonId,
            availableFrom = availableFrom,
            dueDate = old?.dueDate
        )
        assignments.removeAll { it.id == doc.id }
        assignments += doc
        return doc
    }

    private fun iso(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(millis))

    private companion object {
        const val DAY = 86_400_000L
    }
}



interface TopicSettingsSource {
    fun load(classroomId: String, callback: ResultCallback<List<TopicSettingDto>>)
    fun setVisible(classroomId: String, topicId: String, visible: Boolean, callback: ResultCallback<TopicSettingDto>)
}

class MockTopicSettingsSource : TopicSettingsSource {

    private val collection = mutableListOf<TopicSettingDto>() 

    override fun load(classroomId: String, callback: ResultCallback<List<TopicSettingDto>>) {
        callback(ApiResult.Success(collection.filter { it.classroomId == classroomId }))
    }

    override fun setVisible(
        classroomId: String, topicId: String, visible: Boolean, callback: ResultCallback<TopicSettingDto>
    ) {
        collection.removeAll { it.classroomId == classroomId && it.topicId == topicId }
        val doc = TopicSettingDto(classroomId, topicId, visible)
        collection += doc
        callback(ApiResult.Success(doc))
    }
}

class RetrofitTopicSettingsSource(private val api: ApiService) : TopicSettingsSource {

    override fun load(classroomId: String, callback: ResultCallback<List<TopicSettingDto>>) {
        api.topicSettings(classroomId).go({ callback(ApiResult.Failure(it)) }) {
            callback(ApiResult.Success(it.settings.orEmpty()))
        }
    }

    override fun setVisible(
        classroomId: String, topicId: String, visible: Boolean, callback: ResultCallback<TopicSettingDto>
    ) {
        api.putTopicSetting(classroomId, topicId, TopicSettingRequest(visible))
            .go({ callback(ApiResult.Failure(it)) }) { env ->
                callback(ApiResult.Success(env.setting ?: TopicSettingDto(classroomId, topicId, visible)))
            }
    }
}