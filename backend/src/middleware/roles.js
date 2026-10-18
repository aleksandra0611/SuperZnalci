function requireRole(...allowedRoles) {
  const allowed = allowedRoles.map((role) => role.toUpperCase());

  return (req, res, next) => {
    if (!req.user) {
      return res.status(401).json({ message: "Niste prijavljeni." });
    }
    if (!allowed.includes(req.user.role)) {
      return res.status(403).json({ message: "Nimate pravice za to dejanje." });
    }
    next();
  };
}

module.exports = { requireRole };