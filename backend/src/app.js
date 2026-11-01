const express = require("express");
const cors = require("cors");
const authRoutes = require("./routes/authRoutes");
const classroomRoutes = require("./routes/classroomRoutes");
const userRoutes = require("./routes/userRoutes");
const subjectRoutes = require("./routes/subjectRoutes");
const questionRoutes = require("./routes/questionRoutes");
const practiceRoutes = require("./routes/practiceRoutes");
const attemptRoutes = require("./routes/attemptRoutes");
const badgeRoutes = require("./routes/badgeRoutes");
const studentStatisticsRoutes = require("./routes/studentStatisticsRoutes");
const lessonRoutes = require("./routes/lessonRoutes");
const schoolRoutes = require("./routes/schoolRoutes");
const studentAssignmentRoutes = require("./routes/studentAssignmentRoutes");
const teacherIntegrationRoutes = require("./routes/teacherIntegrationRoutes");

const teacherContentRoutes = require("./routes/teacherContentRoutes");
const studentMessageRoutes = require("./routes/studentMessageRoutes");

const app = express();
app.use(cors());
app.use(express.json());

app.get("/api/health", (req, res) =>
  res.json({ status: "ok" })
);

app.use("/api/auth", authRoutes);
app.use("/api/schools", schoolRoutes);
app.use("/api/classrooms", classroomRoutes);
app.use("/api/users", userRoutes);
app.use("/api/subjects", subjectRoutes);
app.use("/api/questions", questionRoutes);
app.use("/api/practice", practiceRoutes);
app.use("/api", attemptRoutes);
app.use("/api/badges", badgeRoutes);
app.use("/api/students", studentStatisticsRoutes);
app.use("/api/lessons", lessonRoutes);
app.use("/api/assignments", studentAssignmentRoutes);
app.use("/api/teacher-content", teacherContentRoutes);
app.use("/api/messages", studentMessageRoutes);
app.use("/api", teacherIntegrationRoutes);

const path = require("path");

app.use(
  "/images",
  express.static(
    path.join(__dirname, "..", "data", "images")
  )
);

app.use((req, res) =>
  res.status(404).json({
    message: "Pot ne obstaja.",
  })
);

app.use((err, req, res, next) => {
  if (err.type === "entity.parse.failed") {
    return res.status(400).json({
      message: "Neveljaven JSON.",
    });
  }

  console.error(err);

  res.status(500).json({
    message: "Napaka na strežniku.",
  });
});

module.exports = app;