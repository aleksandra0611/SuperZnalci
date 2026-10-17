package com.example.superznalci

import android.graphics.Rect
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment

// Adds the system-bar insets to whatever padding the view already has
fun View.applySystemBarsPadding() {
    val initial = Rect(paddingLeft, paddingTop, paddingRight, paddingBottom)
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.setPadding(
            initial.left + bars.left,
            initial.top + bars.top,
            initial.right + bars.right,
            initial.bottom + bars.bottom
        )
        insets
    }
}

// Makes [button] show / hide the password typed in this EditText
fun EditText.attachPasswordToggle(button: ImageButton) {
    button.setOnClickListener {
        val hidden = transformationMethod is PasswordTransformationMethod
        transformationMethod = if (hidden) {
            HideReturnsTransformationMethod.getInstance()
        } else {
            PasswordTransformationMethod.getInstance()
        }
        button.contentDescription =
            context.getString(if (hidden) R.string.hide_password else R.string.show_password)
        setSelection(text.length)
    }
}

fun Fragment.toast(messageRes: Int) {
    Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
}