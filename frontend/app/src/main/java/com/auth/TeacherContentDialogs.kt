package com.example.superznalci

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/** Minimal real database editor. Only teacher-owned CLASSROOM lessons can be edited. */
object TeacherContentDialogs {
    private fun form(context: Context, title: String, fields: List<Pair<String,String>>,
                     commit: (List<String>) -> Unit) {
        val layout=LinearLayout(context).apply {orientation=LinearLayout.VERTICAL;setPadding(36,16,36,8)}
        val editors=fields.map { (label,default) ->
            EditText(context).apply {hint=label;setText(default);setSingleLine(label != "Vprašanje" && !label.startsWith("Možnosti"));layout.addView(this)}
        }
        AlertDialog.Builder(context).setTitle(title).setView(layout)
            .setNegativeButton("Prekliči",null)
            .setPositiveButton("Shrani") { _,_ -> commit(editors.map { it.text.toString().trim() }) }.show()
    }
    private fun toast(c:Context,s:String)=Toast.makeText(c,s,Toast.LENGTH_LONG).show()
    private fun run(c:Context,call:Call<JsonObject>,done:()->Unit) {
        call.enqueue(object:Callback<JsonObject>{
            override fun onResponse(call:Call<JsonObject>,response:Response<JsonObject>){
                if(response.isSuccessful){toast(c,"Uspešno shranjeno");done()}
                else toast(c,"Napaka pri shranjevanju: HTTP ${response.code()}")
            }
            override fun onFailure(call:Call<JsonObject>,t:Throwable){toast(c,"Ni povezave: ${t.localizedMessage}")}
        })
    }
    private fun remove(c:Context,call:Call<Void>,done:()->Unit) {
        AlertDialog.Builder(c).setMessage("Ali res želiš izbrisati? Brisanje ni mogoče, če obstajajo poskusi ali naloge.")
            .setNegativeButton("Prekliči",null).setPositiveButton("Izbriši"){_,_->
                call.enqueue(object:Callback<Void>{
                    override fun onResponse(call:Call<Void>,response:Response<Void>){
                        if(response.isSuccessful){toast(c,"Izbrisano");done()}
                        else toast(c,"Brisanje ni dovoljeno: HTTP ${response.code()}")
                    }
                    override fun onFailure(call:Call<Void>,t:Throwable){toast(c,"Ni povezave: ${t.localizedMessage}")}
                })
            }.show()
    }
    private fun obj(vararg pairs:Pair<String,String>):JsonObject = JsonObject().also { j -> pairs.forEach { j.addProperty(it.first,it.second) } }
    fun newClass(c:Context,done:()->Unit){
        form(c,"Nova učilnica", listOf("Ime učilnice" to "8.A", "Razred (1–9)" to "8", "Oddelek" to "A")){ v ->
            val grade=v[1].toIntOrNull();if(grade==null||grade !in 1..9||v[0].isBlank()){toast(c,"Preveri podatke");return@form}
            val body=obj("name" to v[0],"department" to v[2]);body.addProperty("gradeNumber",grade)
            body.add("subjectIds",JsonArray());run(c,ApiClient.service.newTeacherClass(body),done)
        }
    }
    fun subjectActions(c: Context, classId: String, done: () -> Unit) {
        AlertDialog.Builder(c).setTitle("Predmeti")
            .setItems(arrayOf("Dodaj obstoječi ali novi predmet")) { _, _ ->
                newSubject(c, classId, done)
            }.show()
    }
    fun renameLesson(c: Context, lessonId: String, oldName: String, done: () -> Unit) {
        form(c, "Preimenuj lekcijo", listOf("Novo ime" to oldName)) { v ->
            if (v[0].isBlank()) { toast(c,"Vnesi ime lekcije");return@form }
            run(c, ApiClient.service.renameTeacherLesson(lessonId, obj("name" to v[0])), done)
        }
    }
    fun editQuestion(c: Context, lessonId: String, questionId: String, done: () -> Unit) {
        ApiClient.service.ownedLessonQuestions(lessonId)
            .enqueue(object : Callback<JsonObject> {
                override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                    if (!response.isSuccessful) {
                        toast(c, "Vprašanja ni mogoče naložiti: HTTP ${response.code()}")
                        return
                    }
                    val q = response.body()?.getAsJsonArray("questions")
                        ?.map { it.asJsonObject }
                        ?.firstOrNull { it.get("id")?.asString == questionId }
                    if (q == null) { toast(c, "Vprašanje ne obstaja"); return }
                    question(c, lessonId, questionId, q.get("text")?.asString.orEmpty(), done, q)
                }
                override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                    toast(c, "Ni povezave: ${t.localizedMessage}")
                }
            })
    }
    fun deleteQuestion(c: Context, lessonId: String, questionId: String, done: () -> Unit) {
        remove(c, ApiClient.service.removeTeacherQuestion(lessonId, questionId), done)
    }
    fun questionList(c: Context, lessonId: String, lessonName: String, done: () -> Unit) {
        ApiClient.service.ownedLessonQuestions(lessonId)
            .enqueue(object : Callback<JsonObject> {
                override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                    if (!response.isSuccessful) {
                        toast(c,"Vprašanj ni mogoče naložiti: HTTP ${response.code()}");return
                    }
                    val questions = response.body()?.getAsJsonArray("questions")?.map { it.asJsonObject }.orEmpty()
                    val labels = listOf("+ Dodaj novo vprašanje") + questions.mapIndexed { i, q ->
                        "${i+1}. ${q.get("text")?.asString.orEmpty()}"
                    }
                    AlertDialog.Builder(c).setTitle("Vprašanja – $lessonName")
                        .setItems(labels.toTypedArray()) { _, selected ->
                            if (selected == 0) question(c, lessonId, done = done)
                            else {
                                val q = questions[selected - 1]
                                val id = q.get("id")?.asString.orEmpty()
                                val text = q.get("text")?.asString.orEmpty()
                                AlertDialog.Builder(c).setTitle(text)
                                    .setItems(arrayOf("Uredi vprašanje", "Izbriši vprašanje")) { _, action ->
                                        if (action == 0) question(c, lessonId, id, text, done, q)
                                        else remove(c, ApiClient.service.removeTeacherQuestion(lessonId, id), done)
                                    }.show()
                            }
                        }.setNegativeButton("Zapri", null).show()
                }
                override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                    toast(c,"Ni povezave: ${t.localizedMessage}")
                }
            })
    }
    fun newSubject(c:Context,classId:String,done:()->Unit){
        form(c,"Dodaj predmet v učilnico",listOf("Ime predmeta" to "")){ v->
            if(v[0].isBlank()){toast(c,"Vnesi ime");return@form}
            run(c,ApiClient.service.newTeacherSubject(classId,obj("name" to v[0])),done)
        }
    }
    fun newTopic(c:Context,classId:String,subjectId:String,done:()->Unit){
        form(c,"Nova tema",listOf("Ime teme" to "")){ v->
            if(v[0].isBlank()){toast(c,"Vnesi ime");return@form}
            run(c,ApiClient.service.newTeacherTopic(classId,obj("subjectId" to subjectId,"name" to v[0])),done)
        }
    }
    fun newLesson(c:Context,classId:String,topicId:String,done:()->Unit){
        form(c,"Nova lekcija",listOf("Ime lekcije" to "")){ v->
            if(v[0].isBlank()){toast(c,"Vnesi ime");return@form}
            run(c,ApiClient.service.newTeacherLesson(classId,obj("topicId" to topicId,"name" to v[0])),done)
        }
    }
    fun lessonOptions(c:Context,lessonId:String,name:String,done:()->Unit){
        AlertDialog.Builder(c).setTitle(name)
            .setItems(arrayOf("Preimenuj lekcijo", "Uredi vprašanja", "Izbriši lekcijo")) { _, selected ->
                when (selected) {
                    0 -> renameLesson(c, lessonId, name, done)
                    1 -> questionList(c, lessonId, name, done)
                    2 -> remove(c, ApiClient.service.removeTeacherLesson(lessonId), done)
                }
            }.show()
    }
    fun question(c:Context,lessonId:String,existingQuestionId:String?=null,
                 oldText:String="",done:()->Unit, existing:JsonObject?=null) {
        val oldType = existing?.get("type")?.asString ?: "MULTIPLE_CHOICE"
        val oldPoints = existing?.get("points")?.asString ?: "10"
        val oldCorrect = existing?.get("correctAnswers")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.joinToString(";") { it.asString } ?: ""
        val oldOptions = existing?.get("answers")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.joinToString(";") { it.asString } ?: ""
        val oldExplanation = existing?.get("explanation")?.takeUnless { it.isJsonNull }?.asString ?: ""
        form(c,if(existingQuestionId==null)"Novo vprašanje" else "Uredi vprašanje",
            listOf("Vprašanje" to oldText,
                "Vrsta: MULTIPLE_CHOICE / TRUE_FALSE / FILL_IN_BLANK" to oldType,
                "Možnosti, ločene z ; (za MULTIPLE_CHOICE)" to oldOptions,
                "Pravilen odgovor" to oldCorrect,
                "Točke" to oldPoints,"Razlaga" to oldExplanation)) { v ->
            val points = v[4].toIntOrNull()
            val type = v[1].uppercase(java.util.Locale.ROOT).trim()
            if (v[0].isBlank() || points == null || points !in 1..100 || v[3].isBlank() ||
                type !in setOf("MULTIPLE_CHOICE","TRUE_FALSE","FILL_IN_BLANK")) {
                toast(c,"Preveri vprašanje, vrsto, pravilni odgovor in točke 1–100");return@form
            }
            val answers = v[2].split(';').map { it.trim() }.filter { it.isNotBlank() }
            if (type == "MULTIPLE_CHOICE" &&
                (answers.size !in 2..8 || v[3] !in answers)) {
                toast(c,"Vnesi 2–8 možnosti in odgovor, ki je ena od njih");return@form
            }
            if (type == "TRUE_FALSE" && v[3].lowercase() !in setOf("true","false")) {
                toast(c,"Za TRUE_FALSE vnesi true ali false");return@form
            }
            val body = obj("text" to v[0],"type" to type,"explanation" to v[5])
            body.addProperty("points",points)
            if (type == "MULTIPLE_CHOICE")
                body.add("answers",JsonArray().also { a -> answers.forEach { a.add(it) } })
            else if (type == "TRUE_FALSE")
                body.add("answers",JsonArray().also { a -> a.add("true");a.add("false") })
            else body.add("answers",com.google.gson.JsonNull.INSTANCE)
            body.add("correctAnswers", JsonArray().also { it.add(v[3]) })
            val call = if(existingQuestionId==null) ApiClient.service.newTeacherQuestion(lessonId,body)
                       else ApiClient.service.editTeacherQuestion(lessonId,existingQuestionId,body)
            run(c,call,done)
        }
    }
    fun questionOptions(c:Context,lessonId:String,questionId:String,text:String,done:()->Unit){
        AlertDialog.Builder(c).setTitle("Vprašanje").setItems(arrayOf("Uredi","Izbriši")){_,which->
            if(which==0)question(c,lessonId,questionId,text,done)
            else remove(c,ApiClient.service.removeTeacherQuestion(lessonId,questionId),done)
        }.show()
    }
}

data class TeacherMessageDto(val id:String, val type:String, val message:String, val createdAt:String?=null)
class MessagesEnvelope(val messages:List<TeacherMessageDto>?)
