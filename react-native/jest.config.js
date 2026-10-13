/** Unit tests: the adapter (with the SDKs mocked) and the pure logic. No device, no network. */
module.exports = {
  testEnvironment: "node",
  roots: ["<rootDir>/src"],
  transform: { "^.+\\.[jt]sx?$": "babel-jest" },
  moduleFileExtensions: ["ts", "tsx", "js", "json"],
  moduleNameMapper: { "^expo/virtual/env$": "<rootDir>/jest/expo-env.js" },
};
