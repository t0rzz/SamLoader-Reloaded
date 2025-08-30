import SwiftUI

@main
struct DuofrostApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: View {
    var body: some View {
        VStack(spacing: 12) {
            Text("Duofrost")
                .font(.title)
                .bold()
            Text("This is a placeholder iOS app built via XcodeGen.\nKMP frameworks are copied at build time.")
                .font(.footnote)
                .multilineTextAlignment(.center)
                .foregroundColor(.secondary)
        }
        .padding()
    }
}