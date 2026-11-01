package com.example.superznalci

import android.app.AlertDialog
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

class LoginFragment : Fragment(R.layout.fragment_login) {

    companion object {
        const val RESULT_KEY = "login_prefill"
        const val KEY_ROLE = "role"
        const val KEY_EMAIL = "email"

        // Aliases so RegistrationFragment can keep using LoginFragment.ROLE_TEACHER..
        const val ROLE_STUDENT = Roles.KEY_STUDENT
        const val ROLE_TEACHER = Roles.KEY_TEACHER

        private const val MIN_PASSWORD = 8
        private const val STATE_ROLE = "state_role"
        private const val STATE_REGISTER_MODE = "state_register_mode"
    }

    private enum class Role { STUDENT, TEACHER }

    private var role = Role.STUDENT
    private var registerMode = false

    private val auth: AuthRepository get() = ServiceLocator.auth

    private lateinit var tabLogin: TextView
    private lateinit var tabRegister: TextView
    private lateinit var roleStudent: View
    private lateinit var roleTeacher: View
    private lateinit var ivStudentRadio: ImageView
    private lateinit var ivTeacherRadio: ImageView
    private lateinit var infoBox: View
    private lateinit var nameGroup: View
    private lateinit var classCodeGroup: View
    private lateinit var tvEmailLabel: TextView
    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etClassCode: EditText
    private lateinit var btnTogglePassword: ImageButton
    private lateinit var tvForgot: TextView
    private lateinit var btnSubmit: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            role = if (it.getString(STATE_ROLE) == Role.TEACHER.name) Role.TEACHER else Role.STUDENT
            registerMode = it.getBoolean(STATE_REGISTER_MODE)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_ROLE, role.name)
        outState.putBoolean(STATE_REGISTER_MODE, registerMode)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.main).applySystemBarsPadding()

        tabLogin = view.findViewById(R.id.tabLogin)
        tabRegister = view.findViewById(R.id.tabRegister)
        roleStudent = view.findViewById(R.id.roleStudent)
        roleTeacher = view.findViewById(R.id.roleTeacher)
        ivStudentRadio = view.findViewById(R.id.ivStudentRadio)
        ivTeacherRadio = view.findViewById(R.id.ivTeacherRadio)
        infoBox = view.findViewById(R.id.infoBox)
        nameGroup = view.findViewById(R.id.nameGroup)
        classCodeGroup = view.findViewById(R.id.classCodeGroup)
        tvEmailLabel = view.findViewById(R.id.tvEmailLabel)
        etName = view.findViewById(R.id.etName)
        etEmail = view.findViewById(R.id.etEmail)
        etPassword = view.findViewById(R.id.etPassword)
        etClassCode = view.findViewById(R.id.etClassCode)
        btnTogglePassword = view.findViewById(R.id.btnTogglePassword)
        tvForgot = view.findViewById(R.id.tvForgot)
        btnSubmit = view.findViewById(R.id.btnSubmit)

        etPassword.attachPasswordToggle(btnTogglePassword)
        etPassword.isSaveEnabled = false     // the password is never kept in saved state

        // Login / Registration tabs
        // Whenever the user switches to another form, everything typed so far is wiped
        tabLogin.setOnClickListener {
            if (!registerMode) return@setOnClickListener
            clearForm()
            registerMode = false
            render()
        }
        tabRegister.setOnClickListener {
            if (role == Role.TEACHER) {
                openTeacherRegistration()   // teachers get their own registration screen
            } else if (!registerMode) {
                clearForm()
                registerMode = true         // students register on this same screen
                render()
            }
        }

        // Role cards
        roleStudent.setOnClickListener {
            if (role == Role.STUDENT) return@setOnClickListener
            clearForm()
            role = Role.STUDENT
            render()
        }
        roleTeacher.setOnClickListener {
            if (role == Role.TEACHER) return@setOnClickListener
            clearForm()
            role = Role.TEACHER
            if (registerMode) {
                registerMode = false
                openTeacherRegistration()
            } else {
                render()
            }
        }

        btnSubmit.setOnClickListener { submit() }
        tvForgot.setOnClickListener { openForgotPassword() }
        view.findViewById<View>(R.id.tvDemo).visibility = View.GONE

        // Result sent back from RegistrationFragment (role + e-mail to pre-fill)
        parentFragmentManager.setFragmentResultListener(RESULT_KEY, viewLifecycleOwner) { _, bundle ->
            role = if (bundle.getString(KEY_ROLE) == ROLE_TEACHER) Role.TEACHER else Role.STUDENT
            registerMode = false
            clearForm()
            bundle.getString(KEY_EMAIL)?.takeIf { it.isNotBlank() }?.let { etEmail.setText(it) }
            render()
        }

        render()
    }

    private fun openTeacherRegistration() {
        clearForm()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, RegistrationFragment())
            .addToBackStack(null)
            .commit()
    }

    // After a successful student login: show the profile and drop login/registration from the back stack
    private fun openProfile(fragment: Fragment = ProfileFragment()) {
        parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    // Empties every field, removes errors and hides the password again
    private fun clearForm() {
        listOf(etName, etEmail, etPassword, etClassCode).forEach {
            it.text.clear()
            it.error = null
        }
        etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
        btnTogglePassword.contentDescription = getString(R.string.show_password)
    }

    // Updates every part of the screen that depends on the selected tab / role
    private fun render() {
        val student = role == Role.STUDENT
        val studentRegistering = student && registerMode

        styleTab(tabLogin, selected = !registerMode)
        styleTab(tabRegister, selected = registerMode)

        roleStudent.setBackgroundResource(
            if (student) R.drawable.bg_role_selected else R.drawable.bg_role_unselected
        )
        roleTeacher.setBackgroundResource(
            if (student) R.drawable.bg_role_unselected else R.drawable.bg_role_selected
        )
        ivStudentRadio.setImageResource(if (student) R.drawable.ic_check_circle else R.drawable.ic_radio_off)
        ivTeacherRadio.setImageResource(if (student) R.drawable.ic_radio_off else R.drawable.ic_check_circle)
        // Name + class code are only needed while a student is registering.
        // The e-mail field is always visible: the backend identifies every user by e-mail.
        infoBox.visibility = if (studentRegistering) View.VISIBLE else View.GONE
        nameGroup.visibility = if (studentRegistering) View.VISIBLE else View.GONE
        classCodeGroup.visibility = if (studentRegistering) View.VISIBLE else View.GONE

        tvEmailLabel.setText(if (student) R.string.label_email else R.string.label_email_teacher)
        etEmail.setHint(if (student) R.string.hint_email else R.string.hint_email_teacher)
        tvForgot.visibility = if (registerMode) View.GONE else View.VISIBLE

        btnSubmit.setText(
            when {
                registerMode -> R.string.btn_register_student
                student -> R.string.btn_submit
                else -> R.string.btn_login_teacher
            }
        )

        // Don't carry validation errors over to another mode
        etName.error = null
        etEmail.error = null
        etPassword.error = null
        etClassCode.error = null
    }

       private fun openForgotPassword() {
       val roleKey = if (role == Role.STUDENT) Roles.KEY_STUDENT else Roles.KEY_TEACHER
       parentFragmentManager.beginTransaction()
           .replace(
               R.id.fragment_container,
               ForgotPasswordFragment.newInstance(roleKey, etEmail.text.toString().trim())
           )
           .addToBackStack(null)
           .commit()
   }

    private fun styleTab(tab: TextView, selected: Boolean) {
        if (selected) {
            tab.setBackgroundResource(R.drawable.bg_tab_selected)
            tab.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary_dark))
        } else {
            tab.background = null
            tab.setTextColor(ContextCompat.getColor(requireContext(), R.color.on_surface))
        }
    }

    // submit

    private fun submit() {
        val student = role == Role.STUDENT
        val name = etName.text.toString().cleanName()
        val email = etEmail.text.toString().trim().lowercase()   // backend stores e-mails lowercase
        val password = etPassword.text.toString()                // never trim passwords
        val code = ClassCode.normalize(etClassCode.text.toString())

        val problems = mutableListOf<Pair<EditText, Int>>()

        // First + last name are only needed when a student is registering
        if (student && registerMode && name.split(" ").size < 2) {
            problems += etName to R.string.reg_err_name
        }
        if (email.isEmpty()) {
            problems += etEmail to R.string.err_required
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            problems += etEmail to R.string.err_email
        }
        if (password.isEmpty()) {
            problems += etPassword to R.string.err_required
        } else if (registerMode && password.length < MIN_PASSWORD) {
            problems += etPassword to R.string.err_password_min
        }
        // Class code is only validated when a student is registering
        if (student && registerMode && !ClassCode.isWellFormed(code)) {
            problems += etClassCode to R.string.err_class_code
        }

        problems.forEach { (field, message) -> field.error = getString(message) }
        problems.firstOrNull()?.first?.requestFocus()
        if (problems.isNotEmpty()) return

        setLoading(true)
        if (registerMode) {
            // Only students get here (teachers use RegistrationFragment)
            auth.registerStudent(StudentRegistrationRequest(name, email, password, joinCode = code)) { result ->
                handleResult(result) { onStudentRegistered(email) }
            }
        } else {
            val request = LoginRequest(email, password, role = if (student) Roles.STUDENT else Roles.TEACHER)
            auth.login(request) { result ->
                handleResult(result) { onLoggedIn(it) }
            }
        }
    }

    // Common handling for every response. Ignores answers that arrive after the screen is gone
    private fun <T> handleResult(result: ApiResult<T>, onSuccess: (T) -> Unit) {
        if (view == null) return
        setLoading(false)
        when (result) {
            is ApiResult.Success -> onSuccess(result.data)
            is ApiResult.Failure -> onAuthError(result.httpCode)
        }
    }

    private fun onLoggedIn(response: AuthResponse) {
        SessionStore.save(response.token, response.user)

        // Use the role from the response, not the selected card. Students go to their profile
        if (response.user.role.equals(Roles.STUDENT, ignoreCase = true)) {
            openProfile()
            return
        }

        // Teachers go to their own profile screen
        openProfile(TeacherProfileFragment())
    }

    private fun onStudentRegistered(email: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.student_registered_title)
            .setMessage(getString(R.string.student_registered_msg, email))
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (view == null) return@setPositiveButton

                registerMode = false
                clearForm()
                etEmail.setText(email)
                render()
            }
            .show()
    }

    private fun onAuthError(httpCode: Int?) {
        when (httpCode) {
            401 -> etPassword.apply { error = getString(R.string.err_credentials); requestFocus() }
            404 -> etClassCode.apply { error = getString(R.string.err_class_code_unknown); requestFocus() }
            409 -> etEmail.apply { error = getString(R.string.err_email_taken); requestFocus() }
            null -> toast(R.string.err_network)
            else -> toast(R.string.err_server)
        }
    }

    // Disables the button while a request runs (prevents double submits)
    private fun setLoading(loading: Boolean) {
        btnSubmit.isEnabled = !loading
        btnSubmit.alpha = if (loading) 0.6f else 1f
    }
}