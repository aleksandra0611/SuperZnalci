package com.example.superznalci

import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

object TeacherNav {

    fun bind(root: View, activeId: Int, fragment: Fragment) {
        val items = listOf(
            R.id.navLearn, R.id.navSubjects, R.id.navClassroom, R.id.navLeaderboard, R.id.navProfile
        )
        items.forEach { id ->
            val item = root.findViewById<View>(id)
            val active = id == activeId
            val color = ContextCompat.getColor(
                root.context, if (active) R.color.t_green else R.color.ts_muted
            )
            (item as? android.view.ViewGroup)?.let { group ->
                for (i in 0 until group.childCount) {
                    when (val child = group.getChildAt(i)) {
                        is TextView -> child.setTextColor(color)
                        is android.widget.ImageView ->
                            child.imageTintList = android.content.res.ColorStateList.valueOf(color)
                    }
                }
            }
            item.setOnClickListener { if (!active) open(id, fragment) }
        }
    }

    private fun open(id: Int, fragment: Fragment) {
        val target: Fragment? = when (id) {
            R.id.navSubjects -> TeacherSubjectsFragment()
            R.id.navProfile -> TeacherProfileFragment()
            R.id.navClassroom -> TeacherClassroomFragment()
            R.id.navLeaderboard -> TeacherLeaderboardFragment()
            R.id.navLearn -> TeacherLearnFragment()
            else -> null
        }
        if (target == null) {
            fragment.toast(R.string.teacher_coming_soon)
            return
        }
        fragment.parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, target)
            .commit()
    }
}
