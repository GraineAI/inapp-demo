/**
 * The adapter, with both SDK packages mocked: every call reaches the SDK method the contract names,
 * with the arguments it expects. No device, no network.
 */

// ── mocks (names start with "mock" so jest may hoist them) ────────────────
const mockClients: any[] = [];

jest.mock("@graineai/inapp-react-native", () => {
  class GraineInAppClient {
    opts: any;
    identify = jest.fn(() => undefined);
    setMemory = jest.fn(() => undefined);
    memories = jest.fn(async () => [{ id: "m1", text: "Takes metformin at 8 pm", createdAt: null, updatedAt: null }]);
    forgetMemory = jest.fn(async () => undefined);
    getInstallId = jest.fn(async () => "3f2b8c1e-8d4a-4e1b-9a55-0c2f9b7d6e11");
    session = jest.fn(async () => ({ agentId: "a1", memory: { enabled: true }, calls: { hosted: true, callName: "Asha · Graine Care", scheduling: { usersCanBook: true, maxDaysAhead: 7 } } }));
    registerAction = jest.fn((_name: string, _handler: any) => jest.fn());
    setScreen = jest.fn();
    clearScreen = jest.fn();
    constructor(opts: any) {
      this.opts = opts;
      mockClients.push(this);
    }
  }
  class GraineError extends Error {
    code: string;
    retryAfterSeconds: number | null;
    constructor(code: string, message: string, _status = 0, retryAfterSeconds: number | null = null) {
      super(message);
      this.code = code;
      this.retryAfterSeconds = retryAfterSeconds;
    }
  }
  return {
    GraineInAppClient,
    GraineError,
    requestMicrophonePermission: jest.fn(async () => true),
    GraineProvider: () => null,
    GraineVoiceLauncher: () => null,
    GraineVoiceBar: () => null,
    GraineWidget: () => null,
    useGraineAgent: jest.fn(),
    useGraineSurface: jest.fn(),
    useGraineMemories: jest.fn(),
    useGraineScheduling: jest.fn(),
  };
});

const mockCalls = {
  isAvailable: true,
  configure: jest.fn(async () => undefined),
  enableVoIPPushes: jest.fn(() => () => undefined),
  attach: jest.fn(() => jest.fn()),
  startCall: jest.fn(async () => "call-1"),
  onOutcome: jest.fn(() => jest.fn()),
  onIncomingCall: jest.fn(() => jest.fn()),
};
jest.mock("@graineai/inapp-react-native-calls", () => ({ GraineCalls: mockCalls, useGraineCall: jest.fn() }));

const mockPlatform = { OS: "ios", Version: "17.0" };
const mockPermissions = { request: jest.fn(async () => "granted"), PERMISSIONS: { POST_NOTIFICATIONS: "android.permission.POST_NOTIFICATIONS" } };
jest.mock("react-native", () => ({ Platform: mockPlatform, PermissionsAndroid: mockPermissions }));
jest.mock("@react-native-async-storage/async-storage", () => ({ __esModule: true, default: { getItem: jest.fn(), setItem: jest.fn() } }));
jest.mock("@react-navigation/native", () => ({ useFocusEffect: jest.fn() }));

// The keys are read when config loads: set them first, then load the adapter.
process.env.EXPO_PUBLIC_GRAINE_PUBLISHABLE_KEY = "pk_test_default";
process.env.EXPO_PUBLIC_GRAINE_KEY_BANKING_SUPPORT = "pk_test_banking";
process.env.EXPO_PUBLIC_GRAINE_HELP_KEY = "pk_test_help";
process.env.EXPO_PUBLIC_GRAINE_BASE_URL = "https://example.test/";

/* eslint-disable @typescript-eslint/no-var-requires */
const graine = require("../graine") as typeof import("../graine");
const { PACKS } = require("../usecases") as typeof import("../usecases");
const sdk = require("@graineai/inapp-react-native");
/* eslint-enable @typescript-eslint/no-var-requires */

const health = PACKS.find((p) => p.id === "health-coach")!;
const banking = PACKS.find((p) => p.id === "banking-support")!;

beforeEach(() => {
  jest.clearAllMocks();
  mockPlatform.OS = "ios";
  mockCalls.isAvailable = true;
});

describe("one client per agent", () => {
  it("uses each pack's own key, else the default, and keeps one client per key", () => {
    const a = graine.selectPack(health);
    expect(a.opts).toMatchObject({ publishableKey: "pk_test_default", baseUrl: "https://example.test" });
    expect(a.opts.storage).toBeDefined();
    const b = graine.selectPack(banking);
    expect(b.opts.publishableKey).toBe("pk_test_banking");
    expect(graine.selectPack(health)).toBe(a);
    expect(graine.currentClient()).toBe(a);
  });
});

describe("identity and memory", () => {
  it("signs in with the pack's identifier trait and the consent choice", () => {
    graine.signIn(health, health.users[0], false);
    const client = graine.currentClient() as any;
    expect(client.identify).toHaveBeenCalledWith(
      { patient_id: "MRN-000981", name: "Riya Sharma", plan: "Diabetes care", care_team: "Dr. Kavya Rao" },
      { memory: false },
    );
  });

  it("signs out with identify(null)", () => {
    graine.selectPack(health);
    graine.signOut();
    expect((graine.currentClient() as any).identify).toHaveBeenCalledWith(null);
  });

  it("switches memory, lists and erases it through the SDK", async () => {
    graine.selectPack(health);
    const client = graine.currentClient() as any;
    graine.setMemoryConsent(true);
    expect(client.setMemory).toHaveBeenCalledWith(true);
    await expect(graine.listMemories()).resolves.toEqual([expect.objectContaining({ id: "m1" })]);
    await expect(graine.eraseMemories()).resolves.toBeNull();
    client.forgetMemory.mockResolvedValueOnce(4);
    await expect(graine.eraseMemories()).resolves.toBe(4);
    expect(client.forgetMemory).toHaveBeenCalledTimes(2);
    await expect(graine.installId()).resolves.toMatch(/^[0-9a-f-]{36}$/);
  });
});

describe("the agent's settings", () => {
  it("reads the session once per agent and maps it", async () => {
    graine.selectPack(health);
    const client = graine.currentClient() as any;
    const s = await graine.agentSettings();
    await graine.agentSettings();
    expect(client.session).toHaveBeenCalledTimes(1);
    expect(s).toEqual({ memoryEnabled: true, callName: "Asha · Graine Care" });
    await graine.agentSettings(true);
    expect(client.session).toHaveBeenCalledTimes(2);
  });

  it("falls back to unknown settings when the session cannot be read, and tries again later", async () => {
    graine.selectPack(banking);
    const client = graine.currentClient() as any;
    const warn = jest.spyOn(console, "warn").mockImplementation(() => undefined);
    client.session.mockRejectedValueOnce(new sdk.GraineError("origin_refused", "Add app:// to Allowed domains"));
    const s = await graine.agentSettings(true);
    expect(s.memoryEnabled).toBeNull();
    await graine.agentSettings();
    expect(client.session).toHaveBeenCalledTimes(2);
    warn.mockRestore();
  });
});

describe("errors in plain words", () => {
  it("turns the SDK's error codes into sentences", () => {
    const err = new sdk.GraineError("too_soon", "server text");
    expect(graine.describeError(err, { minLeadMinutes: 10 })).toBe("Pick a time at least 10 minutes from now.");
    expect(graine.describeError(new sdk.GraineError("outside_window", "x"), { agentName: "Asha" })).toBe("That time is outside Asha's calling hours. Pick another time.");
    expect(graine.describeError(new sdk.GraineError("rate_limited", "x", 429, 30))).toBe("Too many requests. Try again in 30 seconds.");
    expect(graine.describeError(new sdk.GraineError("device_limit", "x"), { agentName: "Asha" })).toContain("not one of yours for Asha");
    expect(graine.describeError(new Error("boom"))).toBe("boom");
    expect(graine.errorCode(new sdk.GraineError("not_found", "x"))).toBe("not_found");
  });
});

describe("actions", () => {
  it("registers every action the pack declares, each answered by the runner", async () => {
    const run = jest.fn(() => ({ status: "ok" as const, message: "done" }));
    const off = graine.registerPackActions(banking, run);
    const client = graine.currentClient() as any;
    expect(client.registerAction.mock.calls.map((c: any[]) => c[0])).toEqual(banking.actions.map((a) => a.name));
    const handler = client.registerAction.mock.calls[2][1];
    await expect(Promise.resolve(handler({ item: "7730" }))).resolves.toEqual({ status: "ok", message: "done" });
    expect(run).toHaveBeenCalledWith(banking.actions[2], { item: "7730" });
    off();
    for (const r of client.registerAction.mock.results) expect(r.value).toHaveBeenCalled();
  });
});

describe("calls like a phone call", () => {
  it("configures the engine with the pack's key and call name, once, and VoIP on iOS", async () => {
    await expect(graine.setUpCalls(health, "Asha · Graine Care")).resolves.toBe(true);
    expect(mockCalls.configure).toHaveBeenCalledWith(expect.objectContaining({
      publishableKey: "pk_test_default", baseUrl: "https://example.test", displayName: "Asha · Graine Care", callScreen: "sdk",
      // The help agent beside the main one (the engine's default agent), by name.
      agents: { help: { publishableKey: "pk_test_help" } },
    }));
    expect(mockCalls.enableVoIPPushes).toHaveBeenCalledTimes(1);
    await graine.setUpCalls(health, "Asha · Graine Care");
    expect(mockCalls.configure).toHaveBeenCalledTimes(1);
    await graine.setUpCalls(banking, "Kabir · Graine Bank");
    expect(mockCalls.configure).toHaveBeenLastCalledWith(expect.objectContaining({ publishableKey: "pk_test_banking", displayName: "Kabir · Graine Bank" }));
    expect(mockCalls.enableVoIPPushes).toHaveBeenCalledTimes(1);
  });

  it("does nothing where the calls package is not linked", async () => {
    mockCalls.isAvailable = false;
    await expect(graine.setUpCalls(health, "x")).resolves.toBe(false);
    expect(graine.attachCalls(graine.selectPack(health))()).toBeUndefined();
    expect(graine.onCallEvents(jest.fn())).toEqual(expect.any(Function));
    expect(mockCalls.configure).not.toHaveBeenCalled();
    expect(mockCalls.onOutcome).not.toHaveBeenCalled();
  });

  it("attaches the agent's client and places calls with the pack's reason", async () => {
    const client = graine.selectPack(health);
    const detach = graine.attachCalls(client);
    expect(mockCalls.attach).toHaveBeenCalledTimes(1);
    expect(mockCalls.attach).toHaveBeenCalledWith(client);
    detach();
    expect(mockCalls.attach.mock.results[0].value).toHaveBeenCalled();
    await expect(graine.placeCall(health.call.placeReason)).resolves.toBe("call-1");
    expect(mockCalls.startCall).toHaveBeenCalledWith({ reason: "patient_question" });
  });

  it("logs rings and outcomes in one shape, with the agent the call is with", () => {
    const at = new Date(0);
    expect(graine.callEventFrom({ incoming: { callId: "c9", reason: null, callerName: "Asha · Graine Care", agent: "default" } }, at))
      .toEqual({ callId: "c9", kind: "incoming", detail: "Asha · Graine Care", agent: "main", at });
    expect(graine.callEventFrom({ outcome: { callId: "c9", outcome: "ended", reason: "remote", agent: "help" } }, at)).toMatchObject({ kind: "ended", detail: "remote", agent: "help" });
    expect(graine.callEventFrom({ outcome: { callId: "c9", outcome: "failed", error: "no_network" } }, at)).toMatchObject({ kind: "failed", detail: "no_network", agent: null });
    expect(graine.callEventFrom({ outcome: { callId: "c9", outcome: "declined", agent: null } }, at)).toMatchObject({ kind: "declined", detail: null, agent: null });
    expect(graine.callEventFrom({ outcome: { callId: "c9", outcome: "ringing", hosted: true, agent: "help" } }, at)).toMatchObject({ kind: "ringing", agent: "help" });
    const fn = jest.fn();
    graine.onCallEvents(fn);
    const [[incoming]] = mockCalls.onIncomingCall.mock.calls as any[];
    incoming({ callId: "c2", reason: null, callerName: null });
    expect(fn).toHaveBeenCalledWith(expect.objectContaining({ callId: "c2", kind: "incoming" }));
  });

  it("asks for notifications on Android 13+ only", async () => {
    await graine.requestPermissions();
    expect(sdk.requestMicrophonePermission).toHaveBeenCalled();
    expect(mockPermissions.request).not.toHaveBeenCalled();
    mockPlatform.OS = "android";
    mockPlatform.Version = "34";
    await graine.requestPermissions();
    expect(mockPermissions.request).toHaveBeenCalledWith("android.permission.POST_NOTIFICATIONS");
  });
});

describe("two agents in one app", () => {
  it("makes a help client with the help key, and identifies the person on both", () => {
    graine.selectPack(health);
    const main = graine.currentClient() as any;
    const help = graine.helpClient() as any;
    expect(help).not.toBeNull();
    expect(help).not.toBe(main);
    expect(help.opts.publishableKey).toBe("pk_test_help");
    graine.signIn(health, health.users[1], true);
    for (const client of [main, help]) {
      expect(client.identify).toHaveBeenLastCalledWith(expect.objectContaining({ patient_id: "MRN-000982" }), { memory: true });
    }
    graine.setMemoryConsent(false);
    expect(main.setMemory).toHaveBeenLastCalledWith(false);
    expect(help.setMemory).toHaveBeenLastCalledWith(false);
    graine.signOut();
    expect(main.identify).toHaveBeenLastCalledWith(null);
    expect(help.identify).toHaveBeenLastCalledWith(null);
  });

  it("gives the help agent the same actions", () => {
    graine.selectPack(banking);
    const help = graine.helpClient() as any;
    help.registerAction.mockClear();
    const off = graine.registerPackActions(banking, () => ({ status: "ok" as const, message: "done" }));
    expect(help.registerAction.mock.calls.map((x: any[]) => x[0])).toEqual(banking.actions.map((a) => a.name));
    off();
  });

  it("attaches the help agent's client by name, beside the main one", () => {
    graine.selectPack(health);
    const main = graine.currentClient();
    const help = graine.helpClient()!;
    const detach = graine.attachCalls(main, help);
    expect(mockCalls.attach).toHaveBeenNthCalledWith(1, main);
    expect(mockCalls.attach).toHaveBeenNthCalledWith(2, help, { agent: "help" });
    detach();
    for (const r of mockCalls.attach.mock.results) expect(r.value).toHaveBeenCalled();
  });

  it("places a call with the help agent by name", async () => {
    await graine.placeCall(health.agents.help.placeReason, "help");
    expect(mockCalls.startCall).toHaveBeenLastCalledWith({ reason: "billing_question", agent: "help" });
    await graine.placeCall(health.call.placeReason);
    expect(mockCalls.startCall).toHaveBeenLastCalledWith({ reason: "patient_question" });
  });
});

afterAll(() => {
  // health's main (the default key), banking's main, and one help agent (the same help key for both packs).
  expect(mockClients.length).toBe(3);
});
