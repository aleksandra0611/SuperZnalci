package com.example.superznalci

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Patterns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment

// Request / response classes live in ApiModels.kt, backend access in AuthRepository.kt

class RegistrationFragment : Fragment(R.layout.fragment_registration) {

    private companion object {
        const val MIN_PASSWORD = 8

        val GRADES = listOf(6 to "6. razred", 7 to "7. razred", 8 to "8. razred", 9 to "9. razred")
        val DEPARTMENTS = listOf("A", "B", "C", "D")
        const val DEFAULT_GRADE_INDEX = 2 //8. rayred

        const val STATE_STEP = "state_step"
        const val STATE_SUBJECTS = "state_subjects"
        const val STATE_CODE = "state_issued_code"
        const val STATE_EMAIL = "state_issued_email"
    }

    private var step = 1

    // Subject names, sent as-is. They must equal Subject.name in the database
    private val selectedSubjects = linkedSetOf<String>()

    // Set once the backend has created the account + classroom. Kept in the saved state so a
    // rotation cannot make the teacher lose the code
    private var issuedCode: String? = null
    private var issuedEmail: String = ""
    private val chips = mutableListOf<TextView>()

    private val auth: AuthRepository get() = ServiceLocator.auth

    private lateinit var scroll: ScrollView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStepLabel: TextView
    private lateinit var tvStepName: TextView
    private lateinit var tabStepTeacher: TextView
    private lateinit var tabStepClass: TextView
    private lateinit var stepTeacher: View
    private lateinit var stepClass: View

    private lateinit var etName: EditText
    private lateinit var etRegEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etPassword2: EditText
    private lateinit var tvPwRequirement: TextView

    private lateinit var spSchool: Spinner
    private lateinit var spGrade: Spinner
    private lateinit var spDepartment: Spinner
    private lateinit var tvClassCode: TextView
    private lateinit var btnCopyCode: View
    private lateinit var btnFinish: View
    private lateinit var cbTerms: CheckBox

    private lateinit var backCallback: OnBackPressedCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            step = it.getInt(STATE_STEP, 1)
            it.getStringArrayList(STATE_SUBJECTS)?.let { saved -> selectedSubjects.addAll(saved) }
            issuedCode = it.getString(STATE_CODE)
            issuedEmail = it.getString(STATE_EMAIL).orEmpty()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_STEP, step)
        outState.putStringArrayList(STATE_SUBJECTS, ArrayList(selectedSubjects))
        outState.putString(STATE_CODE, issuedCode)
        outState.putString(STATE_EMAIL, issuedEmail)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scroll = view.findViewById(R.id.main)
        scroll.applySystemBarsPadding()

        progressBar = view.findViewById(R.id.progressSteps)
        tvStepLabel = view.findViewById(R.id.tvStepLabel)
        tvStepName = view.findViewById(R.id.tvStepName)
        tabStepTeacher = view.findViewById(R.id.tabStepTeacher)
        tabStepClass = view.findViewById(R.id.tabStepClass)
        stepTeacher = view.findViewById(R.id.stepTeacher)
        stepClass = view.findViewById(R.id.stepClass)

        etName = view.findViewById(R.id.etRegName)
        etRegEmail = view.findViewById(R.id.etRegEmail)
        etPassword = view.findViewById(R.id.etRegPassword)
        etPassword2 = view.findViewById(R.id.etRegPassword2)
        tvPwRequirement = view.findViewById(R.id.tvPwRequirement)
        etPassword.isSaveEnabled = false     // passwords are never kept in saved state
        etPassword2.isSaveEnabled = false

        spSchool = view.findViewById(R.id.spSchool)
        spGrade = view.findViewById(R.id.spGrade)
        spDepartment = view.findViewById(R.id.spDepartment)
        tvClassCode = view.findViewById(R.id.tvClassCode)
        btnCopyCode = view.findViewById(R.id.btnCopyCode)
        btnFinish = view.findViewById(R.id.btnFinish)
        cbTerms = view.findViewById(R.id.cbTerms)

        // Red asterisk after the labels of required fields
        listOf(
            R.id.tvNameLabel, R.id.tvRegEmailLabel, R.id.tvPasswordLabel, R.id.tvPassword2Label,
            R.id.tvSchoolLabel, R.id.tvGradeLabel, R.id.tvDepartmentLabel
        ).forEach { markRequired(view.findViewById(it)) }

        // System Back goes from step 2 to step 1 before leaving the screen
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (issuedCode != null) leaveAfterRegistration() else showStep(1)
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        setupHeaderAndNavigation(view)
        setupTeacherStep(view)
        setupClassStep(view)

        showStep(step)

        // Screen was recreated after a successful registration: show the code again
        issuedCode?.let { showIssuedCode(it) }
    }

    // header / navigation

    private fun setupHeaderAndNavigation(view: View) {
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            when {
                issuedCode != null -> leaveAfterRegistration()
                step == 2 -> showStep(1)
                else -> parentFragmentManager.popBackStack()
            }
        }
        view.findViewById<View>(R.id.btnHelp).setOnClickListener { toast(R.string.reg_help) }

        tabStepTeacher.setOnClickListener { showStep(1) }
        tabStepClass.setOnClickListener { if (validateStep1()) showStep(2) }

        // Back to the login screen
        view.findViewById<View>(R.id.tvGoLogin).setOnClickListener {
            backToLogin(Roles.KEY_TEACHER)
        }
        view.findViewById<View>(R.id.btnStudentLogin).setOnClickListener {
            backToLogin(Roles.KEY_STUDENT)
        }
    }

    private fun backToLogin(roleKey: String, email: String = "") {
        parentFragmentManager.setFragmentResult(
            LoginFragment.RESULT_KEY,
            bundleOf(LoginFragment.KEY_ROLE to roleKey, LoginFragment.KEY_EMAIL to email)
        )
        parentFragmentManager.popBackStack()
    }

    private fun showStep(newStep: Int) {
        step = newStep
        val first = step == 1

        stepTeacher.visibility = if (first) View.VISIBLE else View.GONE
        stepClass.visibility = if (first) View.GONE else View.VISIBLE
        progressBar.progress = step
        tvStepLabel.setText(if (first) R.string.reg_step_1_of_2 else R.string.reg_step_2_of_2)
        tvStepName.setText(if (first) R.string.reg_step_name_1 else R.string.reg_step_name_2)
        styleTab(tabStepTeacher, selected = first)
        styleTab(tabStepClass, selected = !first)
        backCallback.isEnabled = !first || issuedCode != null

        scroll.post { scroll.smoothScrollTo(0, 0) }
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

    // step: teacher

    private fun setupTeacherStep(view: View) {
        etPassword.attachPasswordToggle(view.findViewById<ImageButton>(R.id.btnTogglePw1))
        etPassword2.attachPasswordToggle(view.findViewById<ImageButton>(R.id.btnTogglePw2))

        view.findViewById<View>(R.id.tvStatusLink).setOnClickListener { toast(R.string.reg_status_info) }

        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = updatePasswordRequirement()
        })
        updatePasswordRequirement()

        view.findViewById<View>(R.id.btnNext).setOnClickListener {
            if (validateStep1()) showStep(2)
        }
    }

    private fun updatePasswordRequirement() {
        val ok = etPassword.text.length >= MIN_PASSWORD
        tvPwRequirement.setText(if (ok) R.string.reg_pw_ok else R.string.reg_pw_hint)
        tvPwRequirement.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (ok) R.color.primary_dark else R.color.on_surface_muted
            )
        )
        tvPwRequirement.setCompoundDrawablesRelativeWithIntrinsicBounds(
            if (ok) R.drawable.ic_check_small else 0, 0, 0, 0
        )
    }

    private fun validateStep1(): Boolean {
        val problems = mutableListOf<Pair<EditText, Int>>()

        if (etName.text.toString().cleanName().split(" ").size < 2) {
            problems += etName to R.string.reg_err_name
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(etRegEmail.text.toString().trim()).matches()) {
            problems += etRegEmail to R.string.reg_err_email
        }
        if (etPassword.text.length < MIN_PASSWORD) {
            problems += etPassword to R.string.reg_err_password
        }
        if (etPassword2.text.toString() != etPassword.text.toString() || etPassword2.text.isEmpty()) {
            problems += etPassword2 to R.string.reg_err_password2
        }

        problems.forEach { (field, message) -> field.error = getString(message) }
        problems.firstOrNull()?.first?.requestFocus()
        return problems.isEmpty()
    }

    // step: school & class

    private fun setupClassStep(view: View) {
        setupSpinner(spSchool, MockData.SCHOOLS)      // TODO: load the real schools from the backend
        setupSpinner(spGrade, GRADES.map { it.second }, DEFAULT_GRADE_INDEX)
        setupSpinner(spDepartment, DEPARTMENTS)

        // The class code only exists once the backend has created the classroom
        // Until then the card keeps its placeholder and the copy button stays disabled,
        // otherwise a teacher could hand out a code that does not exist
        btnCopyCode.isEnabled = false

        chips.clear()
        setupChip(view.findViewById(R.id.chipMath), getString(R.string.subject_math))
        setupChip(view.findViewById(R.id.chipPhysics), getString(R.string.subject_physics))
        setupChip(view.findViewById(R.id.chipChemistry), getString(R.string.subject_chemistry))
        setupChip(view.findViewById(R.id.chipBiology), getString(R.string.subject_biology))

        setupTermsText(view.findViewById(R.id.tvTerms))
        btnFinish.setOnClickListener { submitRegistration() }
    }

    private fun setupSpinner(spinner: Spinner, labels: List<String>, selected: Int = 0) {
        val adapter = ArrayAdapter(requireContext(), R.layout.item_spinner, labels)
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
        spinner.adapter = adapter
        spinner.setSelection(selected, false)
    }

    private fun copyClassCode(code: String) {
        val clipboard = requireContext()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("class code", code))
        toast(R.string.reg_code_copied)
    }

    private fun setupChip(chip: TextView, subject: String) {
        chips += chip
        chip.setOnClickListener {
            if (!selectedSubjects.add(subject)) selectedSubjects.remove(subject)
            renderChip(chip, subject)
        }
        renderChip(chip, subject)
    }

    private fun renderChip(chip: TextView, subject: String) {
        val on = subject in selectedSubjects
        chip.text = if (on) "$subject ✓" else subject
        chip.setBackgroundResource(if (on) R.drawable.bg_chip_on else R.drawable.bg_chip_off)
        chip.setTextColor(
            ContextCompat.getColor(requireContext(), if (on) R.color.surface else R.color.on_surface)
        )
    }

    private fun setupTermsText(tv: TextView) {
        val link1 = getString(R.string.reg_terms_link1)
        val link2 = getString(R.string.reg_terms_link2)
        val full = getString(R.string.reg_terms, link1, link2)
        val green = ContextCompat.getColor(requireContext(), R.color.primary_dark)

        val text = SpannableString(full)
        listOf(link1, link2).forEach { link ->
            val start = full.indexOf(link)
            if (start >= 0) {
                val end = start + link.length
                text.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                text.setSpan(ForegroundColorSpan(green), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        tv.text = text
        tv.setOnClickListener { cbTerms.toggle() }   // TODO: open the terms / GDPR pages
    }

    // submit

    private fun submitRegistration() {
        if (!validateStep1()) { showStep(1); return }
        if (selectedSubjects.isEmpty()) { toast(R.string.reg_err_subjects); return }
        if (!cbTerms.isChecked) { toast(R.string.reg_err_terms); return }

        val gradeIndex = spGrade.selectedItemPosition.coerceIn(0, GRADES.lastIndex)
        val gradeNumber = GRADES[gradeIndex].first
        val department = spDepartment.selectedItem.toString()
        val school = spSchool.selectedItem.toString()

        // The backend has no school / department fields, so both are kept inside Classroom.name
        val payload = TeacherRegistrationRequest(
            name = etName.text.toString().cleanName(),
            email = etRegEmail.text.toString().trim().lowercase(),
            password = etPassword.text.toString(),
            classroomName = "$gradeNumber.$department – $school",
            gradeNumber = gradeNumber,
            department = department,
            subjects = selectedSubjects.toList()
        )

        setLoading(true)
        auth.registerTeacher(payload) { result ->
            if (view == null) return@registerTeacher   // screen was left while waiting
            setLoading(false)
            when (result) {
                is ApiResult.Success -> onTeacherRegistered(result.data)
                is ApiResult.Failure -> onRegistrationError(result.httpCode)
            }
        }
    }

    private fun onTeacherRegistered(response: TeacherRegistrationResponse) {
        SessionStore.save(response.token, response.user)
        issuedCode = response.classroom.joinCode
        issuedEmail = response.user.email
        showIssuedCode(response.classroom.joinCode)
        toast(R.string.reg_success)
    }

    /**
     * Shows the real class code in the code card (no popup). The form is locked because the
     * code belongs to exactly this registration, and the finish button turns into
     * "back to login"
     */
    private fun showIssuedCode(joinCode: String) {
        tvClassCode.text = joinCode
        btnCopyCode.isEnabled = true
        btnCopyCode.setOnClickListener { copyClassCode(joinCode) }

        lockForm()

        btnFinish.isEnabled = true
        btnFinish.alpha = 1f
        (btnFinish as? TextView)?.setText(R.string.reg_btn_to_login)
        btnFinish.setOnClickListener { leaveAfterRegistration() }
        backCallback.isEnabled = true

        // Bring the code card into view
        scroll.post {
            val rect = Rect()
            tvClassCode.getDrawingRect(rect)
            scroll.offsetDescendantRectToMyCoords(tvClassCode, rect)
            val margin = (96 * resources.displayMetrics.density).toInt()
            scroll.smoothScrollTo(0, (rect.top - margin).coerceAtLeast(0))
        }
    }

    // Nothing in the form may be changed after the classroom has been created
    private fun lockForm() {
        listOf(etName, etRegEmail, etPassword, etPassword2, spSchool, spGrade, spDepartment, cbTerms)
            .forEach { it.isEnabled = false }
        chips.forEach { it.isClickable = false }
    }

private fun leaveAfterRegistration() {
    // The token is already saved; the teacher can log in again until the home screen exists.
    val email = issuedEmail
    issuedCode = null
    backToLogin(Roles.KEY_TEACHER, email)
}

    private fun onRegistrationError(httpCode: Int?) {
        when (httpCode) {
            409 -> {
                showStep(1)
                etRegEmail.error = getString(R.string.err_email_taken)
                etRegEmail.requestFocus()
            }
            null -> toast(R.string.err_network)
            else -> toast(R.string.err_server)
        }
    }

    private fun setLoading(loading: Boolean) {
        btnFinish.isEnabled = !loading
        btnFinish.alpha = if (loading) 0.6f else 1f
    }

    private fun markRequired(label: TextView) {
        val base = label.text.toString()
        val text = SpannableString("$base *")
        text.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.reg_error)),
            base.length + 1, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        label.text = text
    }
}