import ComposeApp
import SwiftUI
import UIKit

/// The shared Compose UI (composeApp's MainViewController) as a SwiftUI view.
struct ContentView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
