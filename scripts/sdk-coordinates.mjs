/**
 * The published SDK coordinates the apps' committed manifests use. One place, read by
 * use-local-sdks.sh (to switch back) and check-manifests.mjs (to refuse a manifest left on local).
 */
export const RN_SDK = { name: "@graineai/inapp-react-native", version: "^0.42.0" };
export const RN_CALLS = { name: "@graineai/inapp-react-native-calls", version: "^0.3.0" };
export const FLUTTER_SDK = { name: "graine_inapp", constraint: "^0.8.0" };
