package com.example.superznalci

import android.os.Handler
import android.os.Looper
import android.util.Log

// Password reset for BOTH roles (students and teachers use the same screen).
//
// Flow (2 steps, no deep links needed):
//   1. POST /api/auth/forgot-password   ForgotPasswordRequest -> 204
//      The backend e-mails a 6-digit code (valid ~15 min). It answers 204 for EVERY address,
//      known or not, so the screen cannot be used to find out which e-mails have an account.
//   2. POST /api/auth/reset-password    ResetPasswordRequest  -> 204
//      400 = wrong / expired code (or unknown e-mail, same answer on purpose)
//
// Backend notes: store only a hash of the code + expiry, limit attempts (e.g. 5) and requests
// per e-mail, and hash the new password exactly like on registration.

data class ForgotPasswordRequest(val email: String)

data class ResetPasswordRequest(
    val email: String,
    val code: String,
    val newPassword: String
) {
    // never log the new password
    override fun toString() = "ResetPasswordRequest(email=$email)"
}

interface PasswordResetRepository {
    fun requestReset(request: ForgotPasswordRequest, callback: ResultCallback<Unit>)
    fun resetPassword(request: ResetPasswordRequest, callback: ResultCallback<Unit>)
}


//  Mock: no e-mail is sent. Every account that exists "receives" the demo code 123456
class MockPasswordResetRepository(private val auth: MockAuthRepository) : PasswordResetRepository {

    private val handler = Handler(Looper.getMainLooper())

    // e-mails that asked for a code and have an account
    private val pending = mutableSetOf<String>()

    override fun requestReset(request: ForgotPasswordRequest, callback: ResultCallback<Unit>) {
        val email = request.email.trim().lowercase()
        if (auth.userIdByEmail(email) != null) {
            pending += email
            Log.d(TAG, "Reset code for $email: $DEMO_CODE")
        }
        respond(ApiResult.Success(Unit), callback)
    }

    override fun resetPassword(request: ResetPasswordRequest, callback: ResultCallback<Unit>) {
        val email = request.email.trim().lowercase()
        val ok = email in pending &&
            request.code == DEMO_CODE &&
            request.newPassword.length >= MIN_PASSWORD &&
            auth.updatePassword(email, request.newPassword)

        if (ok) pending -= email
        respond(if (ok) ApiResult.Success(Unit) else ApiResult.Failure(400), callback)
    }

    private fun <T> respond(result: ApiResult<T>, callback: ResultCallback<T>) {
        handler.postDelayed({ callback(result) }, DELAY_MS)
    }

    private companion object {
        const val TAG = "MockReset"
        const val DEMO_CODE = "123456"
        const val MIN_PASSWORD = 8
        const val DELAY_MS = 700L
    }
}
