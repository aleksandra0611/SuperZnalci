package com.example.superznalci

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.superznalci.R

object BottomNavHelper {

    fun setSelected(root: View, selectedId: Int) {

        val green = ContextCompat.getColor(root.context, R.color.primary_dark)
        val grey = ContextCompat.getColor(root.context, R.color.on_surface_muted)

        updateItem(root, R.id.navLearn, R.id.ivNavLearn, R.id.tvNavLearn, selectedId, green, grey)
        updateItem(root, R.id.navSubjects, R.id.ivNavSubjects, R.id.tvNavSubjects, selectedId, green, grey)
        updateItem(root, R.id.navLeaderboard, R.id.ivNavLeaderboard, R.id.tvNavLeaderboard, selectedId, green, grey)
        updateItem(root, R.id.navProfile, R.id.ivNavProfile, R.id.tvNavProfile, selectedId, green, grey)
    }

    private fun updateItem(
        root: View,
        itemId: Int,
        iconId: Int,
        textId: Int,
        selectedId: Int,
        green: Int,
        grey: Int
    ) {
        val isSelected = itemId == selectedId

        root.findViewById<View>(itemId)?.isSelected = isSelected

        root.findViewById<ImageView>(iconId)?.setColorFilter(
            if (isSelected) green else grey
        )

        root.findViewById<TextView>(textId)?.setTextColor(
            if (isSelected) green else grey
        )
    }
}