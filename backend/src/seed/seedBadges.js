require("dotenv").config();

const mongoose = require("mongoose");
const connectDB = require("../config/db");
const Badge = require("../models/badgesModel");

const badges = [
  {
    code: "FIRST_QUIZ",
    name: "Prvi korak",
    condition: "Zaključi svoj prvi kviz.",
  },
  {
    code: "XP_100",
    name: "Zbiratelj točk",
    condition: "Zberi vsaj 100 XP.",
  },
  {
    code: "XP_500",
    name: "Mojster točk",
    condition: "Zberi vsaj 500 XP.",
  },
  {
    code: "STREAK_3",
    name: "Vztrajen učenec",
    condition: "Dosezi 3-dnevni streak.",
  },
  {
    code: "STREAK_7",
    name: "Tedenski junak",
    condition: "Dosezi 7-dnevni streak.",
  },
  {
    code: "PERFECT_QUIZ",
    name: "Popoln rezultat",
    condition: "Zaključi kviz s 100 % pravilnimi odgovori.",
  },
];

async function seedBadges() {
  try {
    if (!process.env.MONGODB_URI) {
      throw new Error(
        "MONGODB_URI ni nastavljen v .env datoteki."
      );
    }

    await connectDB();

    for (const badge of badges) {
      await Badge.updateOne(
        { code: badge.code },
        {
          $set: {
            name: badge.name,
            condition: badge.condition,
          },
          $setOnInsert: {
            code: badge.code,
          },
        },
        { upsert: true }
      );
    }

    console.log("Katalog značk je uspešno ustvarjen.");
  } catch (error) {
    console.error(
      "Napaka pri ustvarjanju značk:",
      error
    );
    process.exitCode = 1;
  } finally {
    await mongoose.disconnect();
  }
}

seedBadges();