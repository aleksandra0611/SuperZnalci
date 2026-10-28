package com.example.superznalci

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

