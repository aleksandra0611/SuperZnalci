const mongoose = require("mongoose");
require("node:dns").setServers(["8.8.8.8", "1.1.1.1"]);

async function connectDB() {
  if (!process.env.MONGODB_URI) {
    console.warn("MONGODB_URI ni nastavljen, strežnik teče brez baze.");
    return;
  }
  try {
    await mongoose.connect(process.env.MONGODB_URI);
    console.log("MongoDB povezan");
  } catch (err) {
    console.error("Napaka pri povezavi z MongoDB:", err.message);
    process.exit(1);
  }
}

module.exports = connectDB;