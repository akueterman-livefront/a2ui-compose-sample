# A2UI Compose Sample

A small, interactive [A2UI](https://a2ui.org/) demo on Android, using the official AndroidX
renderer (`androidx.a2ui` / `material3-a2ui` `1.0.0-alpha01`, protocol v0.9.1).

No network, no LLM: `FakeAgent` plays the model. It reads each user action and answers with the
same A2UI JSON a real agent would send, so you can watch user input turn into agent output turn
into components.

## Flow

```
FakeAgent ──JSON──▶ A2uiMessageParser ─▶ A2uiMessageProcessor ─▶ activeSurfaces ─▶ A2uiSurface (Compose)
    ▲                                            │
    └──────────── outboundEvents (button tap) ◀──┘
```

A multi-turn order. Each step ends with a button, because in A2UI **changing an input only updates
the local data model; the agent hears about it when an action (a Button) fires**. The button's
event context carries the current selections, and the agent picks the next UI from them:

| User does | Event (context) | Agent responds with |
|---|---|---|
| Opens the app | – | Coffee type radios (Hot / Cold) |
| Picks a type, taps Next | `chooseType` (`type`) | Add-in chips for that type: Hot → Half & half, Milk. Cold → Sweetener, Ice |
| Picks add-ins, taps Next | `chooseAddIns` (`type`, `addIns`) | Sweetness slider if Sweetener was picked, otherwise checkout |
| Sets sweetness, taps Next | `chooseSweetness` (`+ sweetness`) | Checkout: To go checkbox + Place order |
| Taps Place order | `placeOrder` (`+ toGo`) | Order summary |

The agent is stateless, like an LLM: every turn it re-sends the whole component tree for the
steps reached so far (components are replaced by ID). Changing an earlier answer and tapping
that step's Next again rebuilds everything after it. Each reply is a short stream (a
"Thinking…" prompt, a delay, then the new UI), and a newer tap cancels a reply still in progress.

The stock Material 3 catalog renders a single-choice `ChoicePicker` as a dropdown.
`RadioChoicePicker` replaces it with radio buttons and hands every other variant to the stock
renderer. The agent's JSON doesn't change; the client decides how a component looks.

| File | Role |
|---|---|
| `FakeAgent.kt` | The "model": decides the next step from each event and builds the A2UI messages |
| `AgenticUiViewModel.kt` | Parser, Material 3 catalog (with the override), processor, both directions of traffic |
| `RadioChoicePicker.kt` | Custom `ChoicePicker` renderer (radio buttons) plugged into the catalog |
| `AgenticUiScreen.kt` | Renders each active surface with `A2uiSurface` |
| `MainActivity.kt` | Hosts `AgenticUiScreen` in a `MaterialTheme` |

## Run

Requires Android SDK Platform **37.1** (`android sdk install platforms/android-37.1`) and JDK 17+.

```sh
./gradlew :app:installDebug
adb logcat -s A2UI   # see every message in both directions
```

In the log, each turn is a `client -> agent` event followed by the agent's messages. For example,
tapping Next after picking Sweetener logs:

```
client -> agent: A2uiClientEventMessage(type=chooseAddIns, ..., context={type=[cold], addIns=[sweetener]})
agent -> client: updateDataModel  /prompt = "Thinking…"
agent -> client: updateDataModel  /prompt = "How sweet should it be?"
agent -> client: updateComponents root.children += sweetness_slider, sweetness_next
```

(The `agent -> client` lines are shortened here. The log prints the full JSON.)
