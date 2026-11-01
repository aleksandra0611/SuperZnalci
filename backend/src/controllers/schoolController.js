const School = require("../models/schoolModel");

/**
 * GET /api/schools
 *
 * Vrne seznam šol za registracijo učitelja.
 */
async function listSchools(req, res, next) {
  try {
    const schools = await School.find({})
      .select("_id name")
      .sort({ name: 1 })
      .lean();

    const result = schools.map((school) => ({
      id: String(school._id),
      name: school.name,
    }));

    return res.status(200).json({
      schools: result,
    });
  } catch (error) {
    next(error);
  }
}

module.exports = {
  listSchools,
};