import { Alert } from "react-native";

/** The in-app confirmation before erasing memory: it cannot be undone. Resolves true on Erase. */
export function confirmErase(agentName: string): Promise<boolean> {
  return new Promise((resolve) => {
    Alert.alert(
      `Erase everything ${agentName} remembers?`,
      `${agentName} will no longer recall anything from your earlier chats and calls. This cannot be undone.`,
      [
        { text: "Cancel", style: "cancel", onPress: () => resolve(false) },
        { text: "Erase", style: "destructive", onPress: () => resolve(true) },
      ],
      { cancelable: true, onDismiss: () => resolve(false) },
    );
  });
}
