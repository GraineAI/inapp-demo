import SwiftUI
import XCTest
@testable import GraineDemo

/// The packs the app ships, read from the app bundle exactly as the app reads them.
final class BundledPackTests: XCTestCase {
    func testEveryShippedPackLoadsInTheAppsOrder() {
        let packs = UseCasePacks.load()
        XCTAssertEqual(packs.map(\.id), UseCasePacks.bundled, "a pack in demo/usecases did not decode, or was not bundled")
        XCTAssertEqual(packs.first?.id, "health-coach")
    }

    func testEveryShippedPackHoldsTogether() {
        for pack in UseCasePacks.load() {
            XCTAssertEqual(pack.schemaVersion, 1, pack.id)
            XCTAssertFalse(pack.users.isEmpty, pack.id)
            for user in pack.users {
                let traits = pack.traits(for: user)
                XCTAssertEqual(traits[pack.identifierField], user.id, pack.id)
                XCTAssertEqual(traits["name"], user.name, pack.id)
                XCTAssertFalse(pack.rows(for: user).isEmpty, "\(pack.id): \(user.name) has no rows")
            }
            for row in pack.items.rows {
                for name in row.actions {
                    let action = pack.action(named: name)
                    XCTAssertEqual(action?.knownEffect, .setStatus, "\(pack.id): \(row.id) offers \(name)")
                    XCTAssertNotNil(action?.status, "\(pack.id): \(name) has no status")
                }
            }
            for action in pack.actions {
                XCTAssertNotNil(action.knownEffect, "\(pack.id): \(action.name) has an effect this app does not run")
            }
            for card in pack.home.cards {
                XCTAssertNotNil(DemoScreen.parse(card.target, pack: pack), "\(pack.id): card \(card.id)")
            }
            XCTAssertTrue(pack.call.reasons.allSatisfy { $0.count <= 120 }, pack.id)
            if pack.accent != nil { XCTAssertNotNil(Color(hex: pack.accent), pack.id) }
            // Every shipped pack names both agents; the main one repeats the pack's own name.
            XCTAssertEqual(pack.profile(.main)?.name, pack.agentName, pack.id)
            XCTAssertNotNil(pack.profile(.help), pack.id)
            XCTAssertFalse(pack.reasons(.help).isEmpty, pack.id)
        }
    }

    func testTheChoiceIsRememberedOnTheDevice() {
        let packs = [TestPack.pack, TestPack.other]
        let defaults = TestClock.defaults()
        XCTAssertEqual(UseCasePacks.chosen(packs, defaults: defaults)?.id, "test-pack")
        defaults.set("other-pack", forKey: UseCasePacks.choiceKey)
        XCTAssertEqual(UseCasePacks.chosen(packs, defaults: defaults)?.id, "other-pack")
        defaults.set("gone", forKey: UseCasePacks.choiceKey)
        XCTAssertEqual(UseCasePacks.chosen(packs, defaults: defaults)?.id, "test-pack")
    }
}

final class PackModelTests: XCTestCase {
    private let pack = TestPack.pack

    func testTraitsCarryTheIdentifierUnderTheAgentsField() {
        XCTAssertEqual(pack.traits(for: pack.users[0]), ["patient_id": "P-1", "name": "Riya Sharma", "plan": "Diabetes care"])
    }

    func testRowsAreTheUsersOwnPlusTheShared() {
        XCTAssertEqual(pack.rows(for: pack.users[0]).map(\.id), ["metformin", "atorvastatin", "vitamin-d3"])
        XCTAssertEqual(pack.rows(for: pack.users[1]).map(\.id), ["aspirin", "vitamin-d3"])
    }

    func testAnUnknownEffectStillDecodes() {
        XCTAssertNil(pack.action(named: "teleport")?.knownEffect)
        XCTAssertEqual(pack.action(named: "mark_done")?.knownEffect, .setStatus)
    }

    func testTheTwoAgentsComeFromThePackOrFallBack() {
        // A pack without `agents`: the main agent is the pack's own; there is no help desk.
        XCTAssertEqual(pack.profile(.main), AgentProfile(name: "Asha", role: "care coach", starters: ["What is due today?"]))
        XCTAssertNil(pack.profile(.help))

        let json = TestPack.json.replacingOccurrences(
            of: "\"starters\": [\"What is due today?\"]",
            with: "\"starters\": [\"What is due today?\"], \"agents\": { \"main\": { \"name\": \"Asha\", \"role\": \"Care coach\" }, \"help\": { \"name\": \"Billing & appointments help\", \"role\": \"Bills, payments and appointments\", \"starters\": [\"Where is my bill?\"], \"callName\": \"Test Care · Billing\", \"placeReason\": \"billing_question\", \"bookLabel\": \"Book a call with billing\", \"reasons\": [\"Billing question\", \"Change an appointment\"] } }"
        )
        let two = try! UseCasePack.parse(Data(json.utf8))
        XCTAssertEqual(two.profile(.main)?.role, "Care coach")
        XCTAssertEqual(two.profile(.main)?.starters, ["What is due today?"])
        XCTAssertEqual(two.profile(.help), AgentProfile(name: "Billing & appointments help", role: "Bills, payments and appointments", starters: ["Where is my bill?"]))
        XCTAssertEqual(DemoScreen.help.title(two), "Billing & appointments help")
        // The help desk books and calls with its own words; the main agent with the pack's.
        XCTAssertEqual(two.bookLabel(.help), "Book a call with billing")
        XCTAssertEqual(two.reasons(.help), ["Billing question", "Change an appointment"])
        XCTAssertEqual(two.placeReason(.help), "billing_question")
        XCTAssertEqual(two.bookLabel(.main), "Schedule a consultation call")
        XCTAssertEqual(two.reasons(.main), ["Weekly check-in", "Medicine review"])
        XCTAssertEqual(two.placeReason(.main), "patient_question")
        // Without the block, the help desk falls back to the pack's.
        XCTAssertEqual(pack.bookLabel(.help), "Schedule a consultation call")
    }

    func testAccentColour() {
        XCTAssertNotNil(Color(hex: "#0F766E"))
        XCTAssertNil(Color(hex: "0F766E"))
        XCTAssertNil(Color(hex: nil))
    }
}

final class ItemsStateTests: XCTestCase {
    private let pack = TestPack.pack
    private var riya: ItemsState { ItemsState(rows: pack.rows(for: pack.users[0])) }

    private func found(_ q: String?) -> String? {
        if case .found(let row) = riya.find(q) { return row.id }
        return nil
    }

    func testFindsByIdThenTitleThenTheOneRowContainingIt() {
        XCTAssertEqual(found("metformin"), "metformin")
        XCTAssertEqual(found("  ATORVASTATIN 10 MG "), "atorvastatin")
        XCTAssertEqual(found("vitamin"), "vitamin-d3")
        // A field's value counts too.
        XCTAssertEqual(found("9 days"), "atorvastatin")
    }

    func testTwoMatchesAreAskedAboutAndNoneIsNone() {
        guard case .ambiguous(let rows) = riya.find("mg") else { return XCTFail("expected ambiguous") }
        XCTAssertEqual(rows.map(\.id), ["metformin", "atorvastatin"])
        XCTAssertEqual(riya.find("insulin"), .noMatch)
        XCTAssertEqual(riya.find(""), .noMatch)
        XCTAssertEqual(riya.find(nil), .noMatch)
        // Only the signed-in user's rows.
        XCTAssertEqual(riya.find("aspirin"), .noMatch)
    }

    func testStatusAndValue() {
        var state = riya
        let metformin = state.rows[0]
        let atorvastatin = state.rows[1]
        XCTAssertEqual(state.status(of: metformin), "Due 8:00 pm")
        XCTAssertEqual(state.value(of: metformin), "Due 8:00 pm")
        XCTAssertNil(state.status(of: atorvastatin))
        XCTAssertEqual(state.value(of: atorvastatin), "At bedtime")
        state.setStatus("Taken today", of: metformin)
        XCTAssertEqual(state.value(of: metformin), "Taken today")
    }

    func testButtonsAreTheRowsOwnActionsHiddenOnceApplied() {
        var state = riya
        let metformin = state.rows[0]
        XCTAssertEqual(state.buttons(for: metformin, pack: pack).map(\.name), ["mark_done"])
        state.setStatus("Taken today", of: metformin)
        XCTAssertTrue(state.buttons(for: metformin, pack: pack).isEmpty)
        XCTAssertTrue(state.buttons(for: state.rows[2], pack: pack).isEmpty)
    }
}

final class DemoScreenTests: XCTestCase {
    private let pack = TestPack.pack

    func testOpenScreenTakesTheGenericKeysAndThePacksName() {
        XCTAssertEqual(DemoScreen.parse("home", pack: pack), .home)
        XCTAssertEqual(DemoScreen.parse("CHAT", pack: pack), .chat)
        XCTAssertEqual(DemoScreen.parse("items", pack: pack), .items)
        XCTAssertEqual(DemoScreen.parse("Medicines", pack: pack), .items)
        XCTAssertEqual(DemoScreen.parse("/medicines", pack: pack), .items)
        XCTAssertEqual(DemoScreen.parse("calls", pack: pack), .calls)
        XCTAssertEqual(DemoScreen.parse("memory", pack: pack), .memory)
        XCTAssertEqual(DemoScreen.parse(" profile ", pack: pack), .profile)
    }

    func testEverythingElseIsNotAScreen() {
        XCTAssertNil(DemoScreen.parse("sign_in", pack: pack))
        XCTAssertNil(DemoScreen.parse("call_log", pack: pack))
        XCTAssertNil(DemoScreen.parse("settings", pack: pack))
        XCTAssertNil(DemoScreen.parse(nil, pack: pack))
        XCTAssertNil(DemoScreen.parse(7, pack: pack))
    }

    func testTitlesAndNamesComeFromThePack() {
        XCTAssertEqual(DemoScreen.home.title(pack), "Today")
        XCTAssertEqual(DemoScreen.chat.title(pack), "Asha")
        XCTAssertEqual(DemoScreen.items.title(pack), "Medicines")
        XCTAssertEqual(DemoScreen.calls.title(pack), "Check-in calls")
        XCTAssertEqual(DemoScreen.items.contextName(pack), "medicines")
        XCTAssertEqual(DemoScreen.calls.contextName(pack), "calls")
    }

    func testConfigReadsTheBuildSettingsAndDefaultsTheURL() {
        let empty = AppConfig.from(info: [:])
        XCTAssertEqual(empty.baseURL, "https://www.graine.ai")
        XCTAssertFalse(empty.hasKey)
        let unexpanded = AppConfig.from(info: ["GraineBaseURL": "$(GRAINE_BASE_URL)", "GrainePublishableKey": "$(GRAINE_PUBLISHABLE_KEY)"])
        XCTAssertEqual(unexpanded, empty)
        let set = AppConfig.from(info: ["GraineBaseURL": "http://localhost:3000", "GrainePublishableKey": " pk_live_abc "])
        XCTAssertEqual(set.baseURL, "http://localhost:3000")
        XCTAssertEqual(set.publishableKey, "pk_live_abc")
        XCTAssertTrue(set.hasKey)
    }

    func testTheOfflineDemoIsALaunchArgumentWithStandInKeys() {
        let info = ["GraineBaseURL": "https://www.graine.ai", "GrainePublishableKey": "pk_live_abc"]
        // No argument: the build settings, as before.
        XCTAssertEqual(AppConfig.from(info: info, launch: [:]), AppConfig.from(info: info))
        // -GraineOffline <url>: that server, and the stand-in key of each agent.
        let offline = AppConfig.from(info: info, launch: ["GraineOffline": " http://127.0.0.1:8790 "])
        XCTAssertEqual(offline.baseURL, "http://127.0.0.1:8790")
        XCTAssertEqual(offline.publishableKey, OfflineDemo.mainKey)
        XCTAssertEqual(offline.helpPublishableKey, OfflineDemo.helpKey)
        XCTAssertTrue(offline.hasKey && offline.hasHelpKey)
        // Anything but a URL leaves the app as it was built.
        XCTAssertEqual(AppConfig.from(info: info, launch: ["GraineOffline": "YES"]), AppConfig.from(info: info))
    }

    func testTheOfflineDemoScriptReadsItsLaunchArguments() {
        let script = OfflineDemo.Script.from([
            "GraineDemoUser": "MRN-000981", "GraineDemoOpen": "chat", "GraineDemoSay": " Book a check-in call ",
            "GraineDemoSayAfter": "3", "GraineDemoRing": "2.5",
        ])
        XCTAssertEqual(script, OfflineDemo.Script(userId: "MRN-000981", open: "chat", say: "Book a check-in call", sayAfter: 3, ringAfter: 2.5))
        XCTAssertEqual(OfflineDemo.Script.from(["GraineDemoSayAfter": "soon"]), OfflineDemo.Script())
    }
}
