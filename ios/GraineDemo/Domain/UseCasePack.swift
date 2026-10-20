import Foundation

/**
 * A USE-CASE PACK: every domain word the demo shows — the assistant's name,
 * the fictional users, the list on the Items screen, the actions the agent
 * can run, the calls it can book. One JSON file per use case in
 * `demo/usecases/` (schema: `usecase.schema.json`, rules: its README), shared
 * by every platform and bundled into the app unchanged. The app's code is the
 * same for every pack.
 */
struct UseCasePack: Codable, Identifiable, Equatable {
    struct User: Codable, Identifiable, Equatable, Hashable {
        /// The identifier's value: sent as `traits[identifierField]`.
        let id: String
        let name: String
        /// Extra prompt variables (strings).
        let traits: [String: String]

        var firstName: String { name.split(separator: " ").first.map(String.init) ?? name }
    }

    struct Home: Codable, Equatable {
        let title: String
        let cards: [HomeCard]
    }

    struct HomeCard: Codable, Identifiable, Equatable {
        let id: String
        let title: String
        let body: String
        /// The generic screen a tap opens: items, calls, memory, chat or profile.
        let target: String
    }

    struct Field: Codable, Equatable, Hashable {
        let name: String
        let label: String
        let value: String
    }

    struct Row: Codable, Identifiable, Equatable, Hashable {
        let id: String
        let title: String
        let subtitle: String
        /// The starting status chip; a `set_status` action replaces it.
        let status: String?
        let fields: [Field]
        /// The `set_status` actions this row offers as buttons and accepts from the agent.
        let actions: [String]
        /// Shown only to these users. Absent: to everyone.
        let userIds: [String]?
    }

    struct Items: Codable, Equatable {
        /// "Medicines", "Cards", "Orders": the screen's title and tab label.
        let title: String
        /// What the app reports as the screen: "medicines".
        let screenName: String
        /// "medicine", "card", "order".
        let itemNoun: String
        let emptyText: String
        let rows: [Row]
    }

    struct Arg: Codable, Equatable {
        let name: String
        let type: String
        let required: Bool?
        let description: String?
        let `enum`: [String]?
    }

    enum Effect: String, Equatable {
        case openScreen = "open_screen"
        case openItem = "open_item"
        case setStatus = "set_status"
    }

    struct Action: Codable, Identifiable, Equatable {
        let name: String
        /// open_screen, open_item or set_status.
        let effect: String
        /// Button text.
        let label: String
        /// For the model: when to use it.
        let description: String
        /// set_status only: the status the row gets.
        let status: String?
        let args: [Arg]

        var id: String { name }
        /// Nil for an effect this build does not know (a newer pack): the agent is told so.
        var knownEffect: Effect? { Effect(rawValue: effect) }
    }

    struct Call: Codable, Equatable {
        /// Name on the incoming call (matches the dashboard).
        let callName: String
        /// The scheduled-calls screen's title: "Check-in calls".
        let title: String
        /// The button that opens the SDK's booking sheet: "Schedule a consultation call".
        let bookLabel: String
        /// What a booking is about: the first starts the sheet's "What's it about?" line, which the user may change.
        let reasons: [String]
        /// The reason a call the user places from Home carries.
        let placeReason: String
    }

    /// One of the pack's agents (`agents.main`, `agents.help`): read leniently, so a pack written before the block existed still reads.
    struct AgentInfo: Codable, Equatable {
        let name: String
        let role: String?
        let starters: [String]?
        /// The help desk's own call settings: the name its calls ring with, the reason on a call placed from Help,
        /// its booking button and reasons.
        let callName: String?
        let placeReason: String?
        let bookLabel: String?
        let reasons: [String]?

        private enum Keys: String, CodingKey {
            case name, agentName, role, agentRole, roleLine, starters, callName, placeReason, bookLabel, reasons
        }

        init(name: String, role: String? = nil, starters: [String]? = nil, callName: String? = nil,
             placeReason: String? = nil, bookLabel: String? = nil, reasons: [String]? = nil) {
            self.name = name
            self.role = role
            self.starters = starters
            self.callName = callName
            self.placeReason = placeReason
            self.bookLabel = bookLabel
            self.reasons = reasons
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: Keys.self)
            name = try c.decodeIfPresent(String.self, forKey: .name) ?? c.decode(String.self, forKey: .agentName)
            role = try c.decodeIfPresent(String.self, forKey: .role)
                ?? c.decodeIfPresent(String.self, forKey: .agentRole)
                ?? c.decodeIfPresent(String.self, forKey: .roleLine)
            starters = try c.decodeIfPresent([String].self, forKey: .starters)
            callName = try c.decodeIfPresent(String.self, forKey: .callName)
            placeReason = try c.decodeIfPresent(String.self, forKey: .placeReason)
            bookLabel = try c.decodeIfPresent(String.self, forKey: .bookLabel)
            reasons = try c.decodeIfPresent([String].self, forKey: .reasons)
        }

        func encode(to encoder: Encoder) throws {
            var c = encoder.container(keyedBy: Keys.self)
            try c.encode(name, forKey: .name)
            try c.encodeIfPresent(role, forKey: .role)
            try c.encodeIfPresent(starters, forKey: .starters)
            try c.encodeIfPresent(callName, forKey: .callName)
            try c.encodeIfPresent(placeReason, forKey: .placeReason)
            try c.encodeIfPresent(bookLabel, forKey: .bookLabel)
            try c.encodeIfPresent(reasons, forKey: .reasons)
        }
    }

    struct Agents: Codable, Equatable {
        let main: AgentInfo?
        let help: AgentInfo?
    }

    struct MemoryExample: Codable, Equatable, Hashable {
        let tell: String
        let ask: String
        let expect: String
    }

    let schemaVersion: Int
    let id: String
    let appTitle: String
    let agentName: String
    let agentRole: String
    let tagline: String
    let notice: String?
    let accent: String?
    let identifierField: String
    let identifierLabel: String
    let userNoun: String
    let users: [User]
    let home: Home
    let items: Items
    let actions: [Action]
    let call: Call
    let memoryExamples: [MemoryExample]
    let starters: [String]
    /// The main agent and the help desk; absent in a pack written before there were two.
    let agents: Agents?
    /// The agent's prompt variables and the user field each takes — `{"callee_name": "name", "age": "age"}`:
    /// a key is a variable the agent's prompt or welcome uses, a value is "name" or one of the person's traits
    /// (never the identifier). Set through the SDK at sign-in, as an API call sends `variables`. Absent: `defaultVariables`.
    let variables: [String: String]?

    /// A pack that names no variables: the person's name as `callee_name`, what greetings and Call History read.
    static let defaultVariables = ["callee_name": "name"]

    /// `identify`'s traits: the identifier under the agent's identity field, the name, and the extra traits.
    func traits(for user: User) -> [String: String] {
        var out = user.traits
        out["name"] = user.name
        out[identifierField] = user.id
        return out
    }

    /// The variables the pack sets and the user field each takes: its own `variables`, else `defaultVariables`.
    var variableFields: [String: String] { variables ?? Self.defaultVariables }

    /// The agent's prompt variables for `user`, from `variableFields` — set through the SDK exactly as an API
    /// call sends them. A field the person does not have is left out.
    func variables(for user: User) -> [String: String] {
        var out: [String: String] = [:]
        for (variable, field) in variableFields {
            let value = field == "name" ? user.name : user.traits[field]
            if let value = value, !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { out[variable] = value }
        }
        return out
    }

    /// The rows `user` sees.
    func rows(for user: User) -> [Row] {
        items.rows.filter { $0.userIds?.contains(user.id) ?? true }
    }

    func action(named name: String) -> Action? { actions.first { $0.name == name } }

    static func parse(_ data: Data) throws -> UseCasePack {
        try JSONDecoder().decode(UseCasePack.self, from: data)
    }
}

/// The packs this app ships: `demo/usecases/<id>.json`, bundled. A new pack is added here too.
enum UseCasePacks {
    static let bundled = ["health-coach", "banking-support", "retail-shopping"]
    static let defaultId = "health-coach"
    static let choiceKey = "demo.pack"

    static func load(bundle: Bundle = .main) -> [UseCasePack] {
        bundled.compactMap { id in
            guard let url = bundle.url(forResource: id, withExtension: "json", subdirectory: "usecases"),
                  let data = try? Data(contentsOf: url) else { return nil }
            return try? UseCasePack.parse(data)
        }
    }

    /// The pack the device chose last, else the default, else the first.
    static func chosen(_ packs: [UseCasePack], defaults: UserDefaults) -> UseCasePack? {
        let id = defaults.string(forKey: choiceKey)
        return packs.first { $0.id == id } ?? packs.first { $0.id == defaultId } ?? packs.first
    }
}
