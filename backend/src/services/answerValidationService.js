const QUESTION_TYPES = [
  "TRUE_FALSE",
  "MULTIPLE_CHOICE",
  "MULTIPLE_SELECT",
  "FILL_IN_BLANK",
  "MATCHING",
  "IMAGE_QUESTION",
];

function validationError(message) {
  const error = new Error(message);
  error.status = 400;
  return error;
}

// Normalizira besedilo za primerjavo
// Unicode NFKC, presledki, velike/male črke in različni zapisi znaka minus
function normalize(value) {
  return value
    .normalize("NFKC")
    .replace(/[−–—]/g, "-")
    .trim()
    .replace(/\s+/g, " ")
    .toLocaleLowerCase("sl");
}

function validString(value) {
  return typeof value === "string" &&
    value.trim().length > 0;
}

function sameString(a, b) {
  return normalize(a) === normalize(b);
}

function isStringArray(value) {
  return Array.isArray(value) &&
    value.every(validString);
}

function hasNoDuplicates(values) {
  const normalized = values.map(normalize);
  return new Set(normalized).size === normalized.length;
}

// Enaka množica odgovorov, ne glede na vrstni red
function sameStringSet(given, correct) {
  if (given.length !== correct.length) {
    return false;
  }

  const givenSet = new Set(given.map(normalize));
  const correctSet = new Set(correct.map(normalize));

  if (
    givenSet.size !== given.length ||
    correctSet.size !== correct.length
  ) {
    return false;
  }

  return [...givenSet].every((item) =>
    correctSet.has(item)
  );
}

function checkSingleChoice(question, givenAnswer) {
  const allowed = question.answers;
  const correct = question.correctAnswers;

  if (!validString(givenAnswer)) {
    throw validationError(
      "Odgovor mora biti neprazen niz."
    );
  }

  if (!isStringArray(allowed) ||
      !isStringArray(correct) ||
      correct.length !== 1) {
    throw validationError(
      "Vprašanje nima pravilno nastavljenih odgovorov."
    );
  }

  if (!allowed.some((a) => sameString(a, givenAnswer))) {
    throw validationError(
      "Izbrani odgovor ni med ponujenimi možnostmi."
    );
  }

  return sameString(givenAnswer, correct[0]);
}

function checkMultipleSelect(question, givenAnswer) {
  const allowed = question.answers;
  const correct = question.correctAnswers;

  if (
    !isStringArray(givenAnswer) ||
    !hasNoDuplicates(givenAnswer)
  ) {
    throw validationError(
      "Odgovor mora biti seznam različnih možnosti."
    );
  }

  if (!isStringArray(allowed) ||
      !isStringArray(correct)) {
    throw validationError(
      "Vprašanje nima pravilno nastavljenih odgovorov."
    );
  }

  const allowedSet = new Set(allowed.map(normalize));

  if (!givenAnswer.every((a) =>
    allowedSet.has(normalize(a))
  )) {
    throw validationError(
      "Ena ali več izbranih možnosti ni dovoljenih."
    );
  }

  return sameStringSet(givenAnswer, correct);
}

function checkFillInBlank(question, givenAnswer) {
  const correct = question.correctAnswers;

  if (!isStringArray(correct) ||
      correct.length === 0) {
    throw validationError(
      "Vprašanje nima pravilno nastavljenih rešitev."
    );
  }

  if (
    !Array.isArray(givenAnswer) ||
    givenAnswer.length !== correct.length ||
    !givenAnswer.every(validString)
  ) {
    throw validationError(
      `Poslati moraš ${correct.length} besedilnih odgovorov.`
    );
  }

  return givenAnswer.every((answer, index) =>
    sameString(answer, correct[index])
  );
}

function checkImageQuestion(question, givenAnswer) {
  const correct = question.correctAnswers;

  if (!validString(givenAnswer)) {
    throw validationError(
      "Odgovor mora biti neprazen niz."
    );
  }

  if (!isStringArray(correct) ||
      correct.length === 0) {
    throw validationError(
      "Vprašanje nima pravilno nastavljenih rešitev."
    );
  }

  return correct.some((answer) =>
    sameString(givenAnswer, answer)
  );
}

function isValidPair(pair) {
  return pair !== null &&
    typeof pair === "object" &&
    !Array.isArray(pair) &&
    validString(pair.left) &&
    validString(pair.right);
}

function checkMatching(question, givenAnswer) {
  const allowed = question.answers;
  const correct = question.correctAnswers;

  if (
    !allowed ||
    !isStringArray(allowed.left) ||
    !isStringArray(allowed.right) ||
    !Array.isArray(correct) ||
    !correct.every(isValidPair)
  ) {
    throw validationError(
      "Vprašanje MATCHING nima pravilnih podatkov."
    );
  }

  if (
    !Array.isArray(givenAnswer) ||
    givenAnswer.length !== allowed.left.length ||
    !givenAnswer.every(isValidPair)
  ) {
    throw validationError(
      "Poslati moraš vse povezave med levo in desno stranjo."
    );
  }

  const leftSet = new Set(allowed.left.map(normalize));
  const rightSet = new Set(allowed.right.map(normalize));

  const givenLeft = givenAnswer.map(
    (pair) => normalize(pair.left)
  );
  const givenRight = givenAnswer.map(
    (pair) => normalize(pair.right)
  );

  // Vsako levo in vsako desno možnost lahko
  // uporabimo največ enkrat.
  if (
    new Set(givenLeft).size !== givenLeft.length ||
    new Set(givenRight).size !== givenRight.length
  ) {
    throw validationError(
      "Možnosti v povezovanju se ne smejo ponavljati."
    );
  }

  if (
    !givenLeft.every((value) => leftSet.has(value)) ||
    !givenRight.every((value) => rightSet.has(value))
  ) {
    throw validationError(
      "Povezava vsebuje neobstoječo možnost."
    );
  }

  const correctMap = new Map(
    correct.map((pair) => [
      normalize(pair.left),
      normalize(pair.right),
    ])
  );

  return givenAnswer.every((pair) =>
    correctMap.get(normalize(pair.left)) ===
    normalize(pair.right)
  );
}

function validateAnswer(question, givenAnswer) {
  if (!question || !QUESTION_TYPES.includes(question.type)) {
    throw validationError(
      "Nepodprt tip vprašanja."
    );
  }

  switch (question.type) {
    case "MULTIPLE_CHOICE":
    case "TRUE_FALSE":
      return checkSingleChoice(question, givenAnswer);

    case "MULTIPLE_SELECT":
      return checkMultipleSelect(question, givenAnswer);

    case "FILL_IN_BLANK":
      return checkFillInBlank(question, givenAnswer);

    case "IMAGE_QUESTION":
      return checkImageQuestion(question, givenAnswer);

    case "MATCHING":
      return checkMatching(question, givenAnswer);

    default:
      throw validationError(
        "Nepodprt tip vprašanja."
      );
  }
}

module.exports = {
  validateAnswer,
};
