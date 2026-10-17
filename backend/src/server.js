require("dotenv").config();
const app = require("./app");
const connectDB = require("./config/db");

if (!process.env.JWT_SECRET) {
  console.error("JWT_SECRET ni nastavljen v .env.");
  process.exit(1);
}

const PORT = process.env.PORT || 3000;

connectDB().then(() => {
  app.listen(PORT, () => console.log(`Strežnik teče na portu ${PORT}`));
});