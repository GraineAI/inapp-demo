/// <reference types="node" />
/**
 * Expo config for the Graine Demo development build. Identifiers and the Firebase file come from
 * .env (see .env.example): push credentials are registered per app, so use identifiers you own.
 */
import fs from "fs";
import path from "path";
import type { ConfigContext, ExpoConfig } from "expo/config";


export default ({ config }: ConfigContext): ExpoConfig => {
  const googleServicesFile = process.env.GRAINE_DEMO_GOOGLE_SERVICES_FILE || "./google-services.json";
  const hasGoogleServices = fs.existsSync(path.resolve(__dirname, googleServicesFile));

  return {
    ...config,
    name: "Graine Demo",
    slug: "graine-demo",
    version: "1.0.0",
    orientation: "portrait",
    userInterfaceStyle: "automatic",
    scheme: "grainedemo",
    ios: {
      bundleIdentifier: process.env.GRAINE_DEMO_IOS_BUNDLE_ID || "ai.graine.demo.rn",
      supportsTablet: false,
      infoPlist: {
        NSMicrophoneUsageDescription: "The assistant listens when you talk to it — in the app, or on a call with it.",
        // Required by App Store processing because the WebRTC library contains camera code (ITMS-90683); calls are audio only.
        NSCameraUsageDescription: "Calls are audio only. The camera is never turned on.",
        // audio: a call keeps going with the screen off; voip: Graine's ring arrives as a VoIP push.
        UIBackgroundModes: ["audio", "voip"],
        ITSAppUsesNonExemptEncryption: false,
      },
      // Push Notifications capability, for the VoIP pushes that ring a booked call.
      entitlements: { "aps-environment": "development" },
    },
    android: {
      package: process.env.GRAINE_DEMO_ANDROID_PACKAGE || "ai.graine.demo.rn",
      permissions: [
        "android.permission.RECORD_AUDIO",
        "android.permission.MODIFY_AUDIO_SETTINGS",
        "android.permission.POST_NOTIFICATIONS",
        // The ringing call screen over the lock screen.
        "android.permission.USE_FULL_SCREEN_INTENT",
      ],
      ...(hasGoogleServices ? { googleServicesFile } : {}),
    },
    plugins: [
      "expo-dev-client",
      // The SDK's calls: microphone text, background audio/VoIP, the push entitlement, WebRTC calls, and — with
      // android.googleServicesFile set — Firebase Cloud Messaging, which the SDK registers with itself.
      ["@graineai/inapp-react-native-calls", { webrtc: true }],
    ],
  };
};
