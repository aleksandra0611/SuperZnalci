require('dotenv').config();
const fs = require('fs');
const path = require('path');
const mongoose = require('mongoose');
const Subject = require('../models/subjectModel.js');
const Lesson = require('../models/lessonModel.js')

// the files use MongoDB extended JSON ({"$oid": "..."}), so EJSON is needed instead of JSON.parse
const { EJSON } = mongoose.mongo.BSON;
const read = (file) =>
    EJSON.parse(fs.readFileSync(path.join(__dirname, '../../data', file), 'utf8'));

// replace the document if it already exists (same _id), otherwise insert it
const upsertAll = (Model, docs) =>
    Model.bulkWrite(docs.map((d) => ({
        replaceOne: { filter: { _id: d._id }, replacement: d, upsert: true },
    })));

(async () => {
    await mongoose.connect(process.env.MONGODB_URI);

    await upsertAll(Subject, read('subjects.json'));
    await upsertAll(Lesson, read('lessons.json')); // every lesson has its own scope (PRACTICE / SHARED)

    console.log('Seed done');
    await mongoose.disconnect();
})().catch((e) => {
    console.error(e);
    process.exit(1);
});
