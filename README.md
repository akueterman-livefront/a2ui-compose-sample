# A2UI Compose Sample

The smallest vertical slice of [A2UI](https://a2ui.org/) on Android, using the official AndroidX
renderer (`androidx.a2ui` / `material3-a2ui` `1.0.0-alpha01`, protocol v0.9.1).

No network, no LLM: `FakeAgent` replays hard-coded A2UI JSON messages, exactly what a real agent
would send.

## Flow

```
FakeAgent ──JSON──▶ A2uiMessageParser ─▶ A2uiMessageProcessor ─▶ activeSurfaces ─▶ A2uiSurface (Compose)
    ▲                                            │
    └──────────── outboundEvents (button tap) ◀──┘
```

1. `createSurface` creates `coffee_order` with the Basic Catalog.
2. `updateComponents` sends a flat list: a Column holding a size `ChoicePicker` (single choice),
   an extras `ChoicePicker` (multiple choice, chips), a `Slider`, a `CheckBox`, a `Button`, and a
   summary `Text`.
3. `updateDataModel` fills `/order/*` with defaults and sets `/summary`.
4. Each input writes to its path under `/order` (two-way binding). Tapping the button emits a
   `placeOrder` event whose context has those paths resolved to their current values.
5. `FakeAgent` replies with an `updateDataModel` for `/summary`.

The stock Material 3 catalog renders a single-choice `ChoicePicker` as a dropdown.
`RadioChoicePicker` replaces it with radio buttons and hands every other variant to the stock
renderer. The agent's JSON doesn't change; the client decides how a component looks.

| File | Role |
|---|---|
| `FakeAgent.kt` | The "agent": the message stream and the event handler |
| `AgenticUiViewModel.kt` | Parser, Material 3 catalog (with the override), processor, both directions of traffic |
| `RadioChoicePicker.kt` | Custom `ChoicePicker` renderer (radio buttons) plugged into the catalog |
| `MainActivity.kt` | Renders each active surface with `A2uiSurface` |

## Run

Requires Android SDK Platform **37.1** (`android sdk install platforms/android-37.1`) and JDK 17+.

```sh
./gradlew :app:installDebug
adb logcat -s A2UI   # see every message in both directions
```
