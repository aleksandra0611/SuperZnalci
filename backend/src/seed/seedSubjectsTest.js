require("dotenv").config();
require("node:dns").setServers(["8.8.8.8", "1.1.1.1"]);
const mongoose = require("mongoose");
const Subject = require("../models/subjectModel");

const SPECS = [
    { name: "Matematika", icon: "calculate", topics: ["Ulomki", "Enačbe", "Geometrija"] },
    { name: "Fizika", icon: "science", topics: ["Gibanje", "Sile", "Energija"] },
    { name: "Kemija", icon: "science", topics: ["Snovi", "Atomi", "Reakcije"] },
    { name: "Biologija", icon: "eco", topics: ["Celica", "Rastline", "Živali"] },
];
const GRADES = [6, 7, 8, 9];

async function seed() {
    await mongoose.connect(process.env.MONGODB_URI);
    for (const spec of SPECS) {
        if (await Subject.exists({ name: spec.name })) {
            console.log(`${spec.name} že obstaja`);
            continue;
        }
        await Subject.create({
            name: spec.name,
            icon: spec.icon,
            grades: GRADES.map((gradeNumber) => ({
                gradeNumber,
                topics: spec.topics.map((name) => ({ name })),
            })),
        });
        console.log(`${spec.name} dodan`);
    }
    await mongoose.disconnect();
}

seed().catch((err) => {
    console.error(err);
    process.exit(1);
});