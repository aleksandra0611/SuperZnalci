
package com.example.superznalci

import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class LoginFragment : Fragment(R.layout.fragment_login) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(view.findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )

            insets
        }

        val etPassword = view.findViewById<EditText>(R.id.etPassword)
        val btnTogglePassword =
            view.findViewById<ImageButton>(R.id.btnTogglePassword)

        btnTogglePassword.setOnClickListener {
            val isPasswordHidden =
                etPassword.transformationMethod is PasswordTransformationMethod

            etPassword.transformationMethod =
                if (isPasswordHidden) {
                    HideReturnsTransformationMethod.getInstance()
                } else {
                    PasswordTransformationMethod.getInstance()
                }

            btnTogglePassword.contentDescription =
                if (isPasswordHidden) "Skrij geslo" else "Pokaži geslo"

            etPassword.setSelection(etPassword.text.length)
        }
    }
}
