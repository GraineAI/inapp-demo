import Foundation
@testable import GraineDemo

/**
 * A small pack in the shared schema, written for the tests so they do not
 * break when the shipped packs' wording changes. The shipped packs are checked
 * separately (`BundledPackTests`).
 */
enum TestPack {
    static let json = """
    {
      "schemaVersion": 1,
      "id": "test-pack",
      "appTitle": "Test Care",
      "agentName": "Asha",
      "agentRole": "care coach",
      "tagline": "A test pack.",
      "notice": "Not medical advice.",
      "accent": "#0F766E",
      "identifierField": "patient_id",
      "identifierLabel": "Patient ID",
      "userNoun": "patient",
      "users": [
        { "id": "P-1", "name": "Riya Sharma", "traits": { "plan": "Diabetes care" } },
        { "id": "P-2", "name": "Arjun Mehta", "traits": { "plan": "Heart health" } }
      ],
      "home": { "title": "Today", "cards": [ { "id": "plan", "title": "Medicines", "body": "Due today.", "target": "items" } ] },
      "items": {
        "title": "Medicines",
        "screenName": "medicines",
        "itemNoun": "medicine",
        "emptyText": "No medicines.",
        "rows": [
          { "id": "metformin", "title": "Metformin 500 mg", "subtitle": "Twice a day", "status": "Due 8:00 pm",
            "fields": [ { "name": "next_dose", "label": "Next dose", "value": "8:00 pm" } ], "actions": ["mark_done"], "userIds": ["P-1"] },
          { "id": "atorvastatin", "title": "Atorvastatin 10 mg", "subtitle": "At bedtime",
            "fields": [ { "name": "supply", "label": "Supply", "value": "9 days left" } ], "actions": ["mark_done"], "userIds": ["P-1"] },
          { "id": "aspirin", "title": "Aspirin 75 mg", "subtitle": "After lunch", "status": "Due 2:00 pm",
            "fields": [], "actions": ["mark_done"], "userIds": ["P-2"] },
          { "id": "vitamin-d3", "title": "Vitamin D3", "subtitle": "Weekly", "fields": [], "actions": [] }
        ]
      },
      "actions": [
        { "name": "open_screen", "effect": "open_screen", "label": "Open", "description": "Open a screen.",
          "args": [ { "name": "screen", "type": "string", "required": true, "description": "The screen." } ] },
        { "name": "open_item", "effect": "open_item", "label": "Details", "description": "Open a medicine.",
          "args": [ { "name": "item", "type": "string", "required": true, "description": "The medicine." } ] },
        { "name": "mark_done", "effect": "set_status", "label": "Mark as taken", "status": "Taken today", "description": "Record a dose.",
          "args": [ { "name": "item", "type": "string", "required": true, "description": "The medicine." } ] },
        { "name": "pause_reminders", "effect": "set_status", "label": "Pause reminders", "status": "Paused", "description": "Pause.",
          "args": [ { "name": "item", "type": "string", "required": true, "description": "The medicine." } ] },
        { "name": "teleport", "effect": "beam", "label": "Teleport", "description": "An effect from a newer pack.", "args": [] }
      ],
      "call": { "callName": "Asha · Test Care", "title": "Check-in calls", "bookLabel": "Schedule a consultation call",
        "reasons": ["Weekly check-in", "Medicine review"],
        "placeReason": "patient_question" },
      "memoryExamples": [ { "tell": "I take metformin at 8 pm.", "ask": "What do I take in the evening?", "expect": "Metformin at 8 pm." } ],
      "starters": ["What is due today?"]
    }
    """

    static var pack: UseCasePack {
        try! UseCasePack.parse(Data(json.utf8))
    }

    /// A second pack, to switch to.
    static var other: UseCasePack {
        let text = json
            .replacingOccurrences(of: "\"id\": \"test-pack\"", with: "\"id\": \"other-pack\"")
            .replacingOccurrences(of: "Asha · Test Care", with: "Kabir · Test Bank")
            .replacingOccurrences(of: "\"agentName\": \"Asha\"", with: "\"agentName\": \"Kabir\"")
        return try! UseCasePack.parse(Data(text.utf8))
    }
}
