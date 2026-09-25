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

1. `createSurface` creates `hello_surface` with the Basic Catalog.
2. `updateComponents` sends a flat list: Column → Text, TextField, Button, Text.
3. `updateDataModel` fills `/form/name` and `/greeting`.
4. Typing writes to `/form/name` (two-way binding). Tapping the button emits a `greet` event with
   `context.name` resolved from the data model.
5. `FakeAgent` replies with an `updateDataModel` for `/greeting`.

| File | Role |
|---|---|
| `FakeAgent.kt` | The "agent": the message stream and the event handler |
| `AgenticUiViewModel.kt` | Parser, Material 3 catalog, processor, both directions of traffic |
| `MainActivity.kt` | Renders each active surface with `A2uiSurface` |

## Run

Requires Android SDK Platform **37.1** (`android sdk install platforms/android-37.1`) and JDK 17+.

```sh
./gradlew :app:installDebug
adb logcat -s A2UI   # see every message in both directions
```
