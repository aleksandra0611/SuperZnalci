const { requestPasswordReset, resetPassword } = require("../services/passwordResetService");

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function validEmail(value) {
  return (
    typeof value === "string" &&
    value.length <= 254 &&
    EMAIL_REGEX.test(value.trim())
  );
}

/**
 * POST /api/auth/forgot-password
 *
 * Body:
 * {
 *   "email": "student@example.com"
 * }
 */
async function forgotPassword(req, res, next) {
  try {
    const { email } = req.body || {};

    if (!validEmail(email)) {
      return res.status(400).json({
        message: "Neveljavna e-pošta.",
      });
    }

    await requestPasswordReset(email);

    return res.status(204).send();

  } catch (error) {
    next(error);
  }
}

/**
 * POST /api/auth/reset-password
 *
 * Body:
 * {
 *   "email": "student@example.com",
 *   "code": "123456",
 *   "newPassword": "NovoGeslo123!"
 * }
 */
async function resetPasswordHandler(req, res, next) {
  try {
    const {
      email,
      code,
      newPassword,
    } = req.body || {};

    if (!validEmail(email)) {
      return res.status(400).json({
        message: "Neveljavna e-pošta.",
      });
    }

    if (
      typeof code !== "string" ||
      !/^\d{6}$/.test(code)
    ) {
      return res.status(400).json({
        message: "Neveljavna ali potekla koda.",
      });
    }

    if (
      typeof newPassword !== "string" ||
      newPassword.length < 8 ||
      newPassword.length > 72
    ) {
      return res.status(400).json({
        message:
          "Geslo mora imeti od 8 do 72 znakov.",
      });
    }

    const success = await resetPassword({
      email,
      code,
      newPassword,
    });

    if (!success) {
      return res.status(400).json({
        message: "Neveljavna ali potekla koda.",
      });
    }

    return res.status(204).send();

  } catch (error) {
    next(error);
  }
}

module.exports = {
  forgotPassword,
  resetPasswordHandler,
};