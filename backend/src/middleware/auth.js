const jwt = require("jsonwebtoken");
const User = require("../models/userModel");

async function authenticate(req, res, next) {
  try {
    const header = req.headers.authorization || "";
    const [scheme, token] = header.split(" ");

    if (!scheme || scheme.toLowerCase() !== "bearer" || !token) {
      return res.status(401).json({ message: "Manjka žeton. Prijavite se." });
    }

    let payload;
    try {
      payload = jwt.verify(token, process.env.JWT_SECRET, { algorithms: ["HS256"] });
    } catch (err) {
      const message =
        err.name === "TokenExpiredError"
          ? "Žeton je potekel. Prijavite se znova."
          : "Neveljaven žeton.";
      return res.status(401).json({ message });
    }

    const user = await User.findById(payload.id);
    if (!user) {
      return res.status(401).json({ message: "Uporabnik ne obstaja." });
    }

    req.user = user; 
    next();
  } catch (err) {
    next(err);
  }
}

module.exports = { authenticate };