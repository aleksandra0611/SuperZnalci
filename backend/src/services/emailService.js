const nodemailer = require("nodemailer");

async function sendPasswordResetEmail({ to, code }) {
  const mode = process.env.MAIL_MODE || "smtp";

  // Samo za lokalni razvoj
  if (mode === "console") {
    if (process.env.NODE_ENV === "production") {
      throw new Error(
        "Console način ni dovoljen v produkciji."
      );
    }

    console.log("\n--- PASSWORD RESET (LOCAL TEST) ---");
    console.log("Email:", to);
    console.log("Code:", code);
    console.log("Valid for: 15 minutes");
    console.log("-----------------------------------\n");
    return;
  }

  if (mode !== "smtp") {
    throw new Error("Neveljaven MAIL_MODE.");
  }

  const {
    SMTP_HOST,
    SMTP_PORT,
    SMTP_USER,
    SMTP_PASS,
    SMTP_FROM,
  } = process.env;

  if (
    !SMTP_HOST ||
    !SMTP_PORT ||
    !SMTP_USER ||
    !SMTP_PASS ||
    !SMTP_FROM
  ) {
    throw new Error(
      "Manjka konfiguracija SMTP v .env."
    );
  }

  const port = Number(SMTP_PORT);

  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error("Neveljaven SMTP_PORT.");
  }

  const transporter = nodemailer.createTransport({
    host: SMTP_HOST,
    port,
    secure: port === 465,
    auth: {
      user: SMTP_USER,
      pass: SMTP_PASS,
    },
  });

  await transporter.sendMail({
    from: SMTP_FROM,
    to,
    subject: "SuperZnalci – ponastavitev gesla",
    text:
      `Pozdravljeni!\n\n` +
      `Vaša koda za ponastavitev gesla je: ${code}\n\n` +
      `Koda velja 15 minut.\n\n` +
      `Če niste zahtevali ponastavitve, ` +
      `lahko to sporočilo prezrete.\n\n` +
      `SuperZnalci`,
  });
}

module.exports = {
  sendPasswordResetEmail,
};