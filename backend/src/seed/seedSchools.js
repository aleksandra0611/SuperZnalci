require("dotenv").config();

const mongoose = require("mongoose");

const connectDB = require("../config/db");
const School = require("../models/schoolModel");

const schools = [
  "OŠ Primoža Trubarja, Ljubljana",
  "OŠ Ivana Cankarja, Maribor",
  "OŠ Franceta Prešerna, Kranj",
];

async function seedSchools() {
  try {
    await connectDB();

    for (const name of schools) {
      await School.updateOne(
        { name },
        {
          $setOnInsert: {
            name,
          },
        },
        {
          upsert: true,
        }
      );
    }

    console.log(
      "Šole so uspešno dodane v MongoDB."
    );
  } catch (error) {
    console.error(
      "Napaka pri ustvarjanju šol:",
      error
    );

    process.exitCode = 1;
  } finally {
    await mongoose.disconnect();
  }
}

seedSchools();