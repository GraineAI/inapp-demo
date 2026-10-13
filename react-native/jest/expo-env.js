// In tests, babel-preset-expo points process.env.EXPO_PUBLIC_* at expo/virtual/env (an ES module that
// jest does not transform). This is the same object, as CommonJS.
module.exports = { env: process.env };
