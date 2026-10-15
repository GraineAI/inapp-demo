/**
 * Scheduling is the SDK's own hook, passed through — booking included (bookingAvailable, openBooking: the
 * SDK's booking sheet); the chat is laid out by the SDK's own rules (earlier messages, day lines, an earlier
 * call as one row, the starters, New conversation), passed through.
 */
const mockUseGraineScheduling = jest.fn();
const mockUseGraineAgent = jest.fn();
const mockChatPieces = jest.fn();
const mockChatTurnRows = jest.fn();
const mockChatClock = jest.fn();
const mockUseGraineTheme = jest.fn();

jest.mock("@graineai/inapp-react-native", () => ({
  GraineInAppClient: class {},
  requestMicrophonePermission: jest.fn(),
  useGraineScheduling: mockUseGraineScheduling,
  useGraineAgent: mockUseGraineAgent,
  chatPieces: mockChatPieces,
  chatTurnRows: mockChatTurnRows,
  chatClock: mockChatClock,
  useGraineTheme: mockUseGraineTheme,
}));
jest.mock("@graineai/inapp-react-native-calls", () => ({ GraineCalls: { isAvailable: false }, useGraineCall: jest.fn() }));
jest.mock("react-native", () => ({ Platform: { OS: "ios" }, PermissionsAndroid: {} }));
jest.mock("@react-native-async-storage/async-storage", () => ({ __esModule: true, default: {} }));
jest.mock("@react-navigation/native", () => ({ useFocusEffect: jest.fn() }));

it("uses the SDK's scheduling hook and its chat layout rules as they are", () => {
  const graine = require("../graine") as typeof import("../graine");
  expect(graine.useScheduling).toBe(mockUseGraineScheduling);
  // The Help screen books its own agent with the SDK's agent hook, under that agent's provider.
  expect(graine.useAgentConversation).toBe(mockUseGraineAgent);
  expect(graine.chatPieces).toBe(mockChatPieces);
  expect(graine.chatTurnRows).toBe(mockChatTurnRows);
  expect(graine.chatClock).toBe(mockChatClock);
  // The brand's chat settings (Earlier messages, New conversation) come from the agent's design.
  expect(graine.useAgentDesign).toBe(mockUseGraineTheme);
});
