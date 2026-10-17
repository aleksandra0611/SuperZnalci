package com.example.superznalci

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment

/**
 * Password reset, shared by the student and the teacher login.
 * Step 1: e-mail -> the backend sends a 6-digit code.
 * Step 2: code + new password -> back to the login screen with the e-mail filled in.
 */
class ForgotPasswordFragment : Fragment(R.layout.fragment_forgot_password) {

    companion object {
        private const val ARG_ROLE = "role"
        private const val ARG_EMAIL = "email"
        private const val STATE_STEP = "state_step"
        private const val STATE_SENT_TO = "state_sent_to"
        private const val MIN_PASSWORD = 8
        private const val CODE_LENGTH = 6

        // roleKey: Roles.KEY_STUDENT / Roles.KEY_TEACHER, so the login screen opens on the same card
        fun newInstance(roleKey: String, email: String) = ForgotPasswordFragment().apply {
            arguments = bundleOf(ARG_ROLE to roleKey, ARG_EMAIL to email)
        }
    }

    private var step = 1
    private var sentTo = ""   //the e-mail a code was requested for

    private val roleKey: String get() = arguments?.getString(ARG_ROLE) ?: Roles.KEY_STUDENT
    private val repo: PasswordResetRepository get() = ServiceLocator.passwordReset

    private lateinit var scroll: ScrollView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStepLabel: TextView
    private lateinit var tvStepName: TextView
    private lateinit var stepEmail: View
    private lateinit var stepCode: View
    private lateinit var tvCodeInfo: TextView
    private lateinit var etEmail: EditText
    private lateinit var etCode: EditText
    private lateinit var etPassword: EditText
    private lateinit var etPassword2: EditText
    private lateinit var tvPwRequirement: TextView
    private lateinit var btnSendCode: View
    private lateinit var btnReset: View
    private lateinit var tvResend: View

    private lateinit var backCallback: OnBackPressedCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            step = it.getInt(STATE_STEP, 1)
            sentTo = it.getString(STATE_SENT_TO).orEmpty()
            if (sentTo.isBlank()) step = 1
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_STEP, step)
        outState.putString(STATE_SENT_TO, sentTo)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scroll = view.findViewById(R.id.main)
        scroll.applySystemBarsPadding()

        progressBar = view.findViewById(R.id.progressSteps)
        tvStepLabel = view.findViewById(R.id.tvStepLabel)
        tvStepName = view.findViewById(R.id.tvStepName)
        stepEmail = view.findViewById(R.id.stepEmail)
        stepCode = view.findViewById(R.id.stepCode)
        tvCodeInfo = view.findViewById(R.id.tvCodeInfo)
        etEmail = view.findViewById(R.id.etFpEmail)
        etCode = view.findViewById(R.id.etFpCode)
        etPassword = view.findViewById(R.id.etFpPassword)
        etPassword2 = view.findViewById(R.id.etFpPassword2)
        tvPwRequirement = view.findViewById(R.id.tvFpPwRequirement)
        btnSendCode = view.findViewById(R.id.btnSendCode)
        btnReset = view.findViewById(R.id.btnReset)
        tvResend = view.findViewById(R.id.tvResend)

        etPassword.isSaveEnabled = false
        etPassword2.isSaveEnabled = false
        etPassword.attachPasswordToggle(view.findViewById<ImageButton>(R.id.btnToggleFp1))
        etPassword2.attachPasswordToggle(view.findViewById<ImageButton>(R.id.btnToggleFp2))

        // The e-mail typed on the login screen is carried over
        if (savedInstanceState == null) {
            etEmail.setText(arguments?.getString(ARG_EMAIL).orEmpty())
        }

        // System Back goes from step 2 to step 1 before leaving the screen
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = showStep(1)
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            if (step == 2) showStep(1) else parentFragmentManager.popBackStack()
        }
        view.findViewById<View>(R.id.tvGoLogin).setOnClickListener {
            backToLogin(etEmail.text.toString().trim().lowercase())
        }

        btnSendCode.setOnClickListener { submitEmail() }
        tvResend.setOnClickListener { requestCode(sentTo, resend = true) }
        btnReset.setOnClickListener { submitReset() }

        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = updatePasswordRequirement()
        })
        updatePasswordRequirement()

        showStep(step)
    }

    // steps

    private fun showStep(newStep: Int) {
        step = newStep
        val first = step == 1

        stepEmail.visibility = if (first) View.VISIBLE else View.GONE
        stepCode.visibility = if (first) View.GONE else View.VISIBLE
        progressBar.progress = step
        tvStepLabel.setText(if (first) R.string.fp_step_1_of_2 else R.string.fp_step_2_of_2)
        tvStepName.setText(if (first) R.string.fp_step_name_1 else R.string.fp_step_name_2)
        tvCodeInfo.text = getString(R.string.fp_info_code, sentTo)
        backCallback.isEnabled = !first

        scroll.post { scroll.smoothScrollTo(0, 0) }
    }

    // step 1: e-mail

    private fun submitEmail() {
        val email = etEmail.text.toString().trim().lowercase()   // backend stores e-mails lowercase

        if (email.isEmpty()) {
            etEmail.error = getString(R.string.err_required)
            etEmail.requestFocus()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = getString(R.string.err_email)
            etEmail.requestFocus()
            return
        }
        requestCode(email, resend = false)
    }

    private fun requestCode(email: String, resend: Boolean) {
        setLoading(true)
        repo.requestReset(ForgotPasswordRequest(email)) { result ->
            if (view == null) return@requestReset   // screen was left while waiting
            setLoading(false)
            when (result) {
                is ApiResult.Success -> {
                    sentTo = email
                    if (resend) toast(R.string.fp_resent) else showStep(2)
                }
                is ApiResult.Failure -> onError(result.httpCode)
            }
        }
    }

    // step 2: code + new password

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

    private fun submitReset() {
        val code = etCode.text.toString().trim()
        val password = etPassword.text.toString()
        val password2 = etPassword2.text.toString()

        val problems = mutableListOf<Pair<EditText, Int>>()
        if (code.length != CODE_LENGTH) problems += etCode to R.string.fp_err_code
        if (password.length < MIN_PASSWORD) problems += etPassword to R.string.err_password_min
        if (password2.isEmpty() || password2 != password) problems += etPassword2 to R.string.reg_err_password2

        problems.forEach { (field, message) -> field.error = getString(message) }
        problems.firstOrNull()?.first?.requestFocus()
        if (problems.isNotEmpty()) return

        setLoading(true)
        repo.resetPassword(ResetPasswordRequest(sentTo, code, password)) { result ->
            if (view == null) return@resetPassword
            setLoading(false)
            when (result) {
                is ApiResult.Success -> {
                    toast(R.string.fp_success)
                    backToLogin(sentTo)
                }
                is ApiResult.Failure -> when (result.httpCode) {
                    400 -> etCode.apply { error = getString(R.string.fp_err_code_invalid); requestFocus() }
                    else -> onError(result.httpCode)
                }
            }
        }
    }

    // helpers

    private fun onError(httpCode: Int?) {
        if (httpCode == null) toast(R.string.err_network) else toast(R.string.err_server)
    }

    // Back to the login screen on the same role card, with the e-mail filled in
    private fun backToLogin(email: String) {
        parentFragmentManager.setFragmentResult(
            LoginFragment.RESULT_KEY,
            bundleOf(LoginFragment.KEY_ROLE to roleKey, LoginFragment.KEY_EMAIL to email)
        )
        parentFragmentManager.popBackStack()
    }

    // Disables the buttons while a request runs (prevents double submits)
    private fun setLoading(loading: Boolean) {
        listOf(btnSendCode, btnReset, tvResend).forEach {
            it.isEnabled = !loading
            it.alpha = if (loading) 0.6f else 1f
        }
    }
}
