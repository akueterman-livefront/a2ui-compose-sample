# A2UI Compose Sample

A small, interactive [A2UI](https://a2ui.org/) demo on Android, using the official AndroidX renderer (`androidx.a2ui` / `material3-a2ui` `1.0.0-alpha01`, protocol v0.9.1).

Two agents implement the same `Agent` interface and show two different uses of A2UI:

- **`FakeAgent`** (the default): no network and no setup. The coffee flow is hard-coded in Kotlin, and it answers with the same A2UI JSON a real agent would send.
- **`ClaudeAgent`** (opt-in): a brew troubleshooter. You describe what's wrong with your coffee, and a real LLM designs each screen to fit the problem. See [Use a real LLM (Claude)](#use-a-real-llm-claude).

Either way, you can watch user input turn into agent output turn into components. The coffee order is a fixed flow, so `FakeAgent` can script it. The troubleshooter isn't: what to ask and what to show depend on what the user says, which is where an LLM generating the UI earns its keep.

## Demo

|`FakeAgent`|`ClaudeAgent`|
|:---:|:---:|
|https://github.com/user-attachments/assets/c237d74f-df62-4f54-873e-1206fafb72f4|https://github.com/user-attachments/assets/54b8f9a9-b07a-42c5-95b4-687ded96de7e|








## Flow (`FakeAgent`)

```
   Agent ──JSON──▶ A2uiMessageParser ─▶ A2uiMessageProcessor ─▶ activeSurfaces ─▶ A2uiSurface (Compose)
    ▲                                            │
    └──────────── outboundEvents (button tap) ◀──┘
```

A multi-turn order. Each step ends with a button, because in A2UI **changing an input only updates the local data model; the agent hears about it when an action (a Button) fires**. The button's event context carries the current selections, and the agent picks the next UI from them:

| User does | Event (context) | Agent responds with |
|---|---|---|
| Opens the app | – | Coffee type radios (Hot / Cold) |
| Picks a type, taps Next | `chooseType` (`type`) | Add-in chips for that type: Hot → Half & half, Milk. Cold → Sweetener, Ice |
| Picks add-ins, taps Next | `chooseAddIns` (`type`, `addIns`) | Sweetness slider if Sweetener was picked, otherwise checkout |
| Sets sweetness, taps Next | `chooseSweetness` (`+ sweetness`) | Checkout: To go checkbox + Place order |
| Taps Place order | `placeOrder` (`+ toGo`) | Order summary + Start a new order (replaces the selection steps) |
| Taps Start a new order | `startOver` (none) | Resets the data model and shows the coffee type radios again |

The agent is stateless, like an LLM: every turn it re-sends the whole component tree for the steps reached so far (components are replaced by ID). Changing an earlier answer and tapping that step's Next again rebuilds everything after it. Each reply is a short stream (a "Thinking…" prompt, a delay, then the new UI), and a newer tap cancels a reply still in progress.

The stock Material 3 catalog renders a single-choice `ChoicePicker` as a dropdown. `RadioChoicePicker` replaces it with radio buttons and hands every other variant to the stock renderer. The agent's JSON doesn't change; the client decides how a component looks.

Source is under `app/src/main/java/com/example/a2uisample/`, in three packages: `agent` (the agent side of the conversation), `ui` (the client: renderer wiring and screen) and `ui.catalog` (client-owned component renderers).

| File | Role |
|---|---|
| `agent/Agent.kt` | The `Agent` interface both agents implement, and `createAgent()`, which picks one from `local.properties` |
| `agent/FakeAgent.kt` | The offline "model": decides the next step from each event in Kotlin and builds the A2UI messages |
| `agent/ClaudeAgent.kt` | The troubleshooter: sends each event and the session so far to Claude, validates the A2UI JSON it returns, and retries once if the JSON is invalid |
| `agent/TroubleshooterPrompt.kt` | Claude's system prompt: role, output rules, a short UI description, the renderer's catalog as JSON Schema, and the example replies |
| `resources/troubleshooter/*.json` | Example replies the prompt includes. `start.json` is also the opening screen. Checked by `TroubleshooterExamplesTest` |
| `agent/A2uiMessages.kt` | Builders for the envelope, `createSurface` and `updateDataModel` messages both agents use |
| `ui/AgenticUiViewModel.kt` | Parser, Material 3 catalog (with the override), processor, both directions of traffic |
| `ui/catalog/RadioChoicePicker.kt` | Custom `ChoicePicker` renderer (radio buttons) plugged into the catalog |
| `ui/AgenticUiScreen.kt` | Renders each active surface with `A2uiSurface` |
| `MainActivity.kt` | Hosts `AgenticUiScreen` in a `MaterialTheme` |

## Run

Requires Android SDK Platform **37.1** (`android sdk install platforms/android-37.1`) and JDK 17+.

```sh
./gradlew :app:installDebug
adb logcat -s A2UI   # see every message in both directions
```

In the log, each turn is a `client -> agent` event followed by the agent's messages. For example, tapping Next after picking Sweetener logs:

```
client -> agent: A2uiClientEventMessage(type=chooseAddIns, ..., context={type=[cold], addIns=[sweetener]})
agent -> client: updateDataModel  /prompt = "Thinking…"
agent -> client: updateDataModel  /prompt = "How sweet should it be?"
agent -> client: updateComponents root.children += sweetness_slider, sweetness_next
```

(The `agent -> client` lines are shortened here. The log prints the full JSON.)

## Use a real LLM (Claude)

With `ClaudeAgent`, the app becomes a brew troubleshooter. Describe the problem in your own words, for example "my espresso is sour and runs in 18 seconds". Claude replies with a diagnosis card, a recipe card with concrete changes, and only the inputs it needs to hear how the next brew went (a shot-time slider for espresso, a brew-time one for pour-over, taste chips). If your description is too vague, it asks a couple of questions with pickers first. Report back and it adjusts the recipe, building on what you already tried.

There is no scripted flow. The prompt follows the pattern the [A2UI agent SDK](https://github.com/google/A2UI) uses: a one-line role, output rules, a short UI description ("if you can't diagnose yet, ask like CLARIFY_EXAMPLE"), the catalog schema, and complete example replies. The examples do most of the work: Claude copies their structure and picks its own components, ids and event names. `TroubleshooterExamplesTest` runs the examples through the A2UI parser, so a broken example can't teach Claude a broken pattern. Run it with `./gradlew :app:connectedDebugAndroidTest`, since the parser needs a device.

1. Create an API key at [console.anthropic.com](https://console.anthropic.com/).
2. Add it to `local.properties` in the project root. The file is gitignored, so the key stays on your machine.

   ```properties
   ANTHROPIC_API_KEY=sk-ant-...
   ```
3. Rebuild and run: `./gradlew :app:installDebug`.

To use a different model, also set `ANTHROPIC_MODEL` in `local.properties`, for example `ANTHROPIC_MODEL=claude-sonnet-5-5`. The default is `claude-haiku-4-5`, the cheapest and fastest. To switch back to `FakeAgent`, remove the key and rebuild.

**Cost.** The opening screen comes from `start.json`, so it costs nothing. Each tap after that is one request: the system prompt (about 11K tokens, cached after the first request) plus the session so far, capped at the last 6 turns, and the new event. On Haiku 4.5 that's about 1K output tokens per turn, roughly a cent. Start over clears the session. Logcat prints the token counts for every turn.

**How a turn works.** The app shows "Thinking…" right away, then sends the event (`{"name":"reportResult","context":{...}}`) to Claude, after the earlier turns of the session. Before anything reaches the renderer, it checks the reply with the same `A2uiMessageParser` the renderer uses. If the reply isn't valid A2UI, the app sends the error back to Claude and asks once more. API errors (a bad key, no network) appear in the prompt text instead of crashing.

**Things to know:**

- The key is compiled into the debug APK. That's fine on your own device, but don't share the APK. A production app would call the LLM through its own server instead.
- A turn takes a few seconds while Claude writes the full component tree.
- Wording and layout can vary a little between runs. That comes with letting the LLM write the UI.
