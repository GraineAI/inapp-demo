// The use-case packs live in ../usecases, shared with every platform: Metro watches that folder so
// the app bundles the same files rather than copies.
const path = require("path");
const { getDefaultConfig } = require("expo/metro-config");

const config = getDefaultConfig(__dirname);
config.watchFolders = [...(config.watchFolders ?? []), path.resolve(__dirname, "../usecases")];
module.exports = config;
