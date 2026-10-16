const mongoose = require('mongoose');

const badgeSchema = new mongoose.Schema({
    name: { type: String, required: true, unique: true, trim: true },
    condition: { type: String, required: true }, //npr "7-day streak"
});

module.exports = mongoose.model('Badge', badgeSchema);