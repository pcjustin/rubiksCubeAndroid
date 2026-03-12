# Rubik's Cube Android Solver

An Android app for entering a 3x3 Rubik's Cube state, solving it with the `min2phase` algorithm, and previewing the solution step by step in a 3D scene.

## Overview

This project combines:

- A 3D interactive cube built with `SceneView`
- Manual sticker coloring through a touch UI
- Cube-state validation before solving
- A two-phase solver based on the bundled `cs.min2phase` implementation
- Step-by-step move playback for the generated solution

The app is currently implemented with the classic Android View system and Kotlin.

## Features

- Rotate the 3D camera by dragging on the cube view
- Select a sticker color from the bottom palette
- Tap stickers to paint the cube state
- Use gray as the unset color while entering the puzzle
- Validate the cube before solving
- Generate a solution in standard cube notation such as `R`, `U'`, and `F2`
- Replay the solution one move at a time with previous/next controls

## Tech Stack

- Kotlin
- Android SDK 35
- Min SDK 24
- Android Gradle Plugin 8.13.2
- Java 17
- `io.github.sceneview:sceneview:2.2.1`

## Project Structure

```text
app/src/main/java/com/rubikscube/app/
  MainActivity.kt        UI wiring and user interactions
  RubiksCubeScene.kt     3D scene setup, sticker nodes, move animation
  RubiksCubeState.kt     Logical cube model and move application
  RubiksSolver.kt        Solver wrapper and state validation
  StickerColor.kt        Sticker color definitions

app/src/main/java/cs/min2phase/
  ...                    Embedded two-phase solver implementation
```

## Requirements

Before building the project, make sure you have:

- Android Studio with a recent Android SDK installation
- JDK 17
- An Android device or emulator running Android 7.0 or later

## Build and Run

### Android Studio

1. Open the project in Android Studio.
2. Let Gradle sync the project.
3. Run the `app` configuration on a device or emulator.

### Command Line

```bash
./gradlew assembleDebug
```

To install the debug build on a connected device:

```bash
./gradlew installDebug
```

## How to Use

1. Launch the app.
2. Drag the 3D scene to inspect the cube.
3. Select a color from the bottom palette.
4. Tap stickers to enter the full cube state.
5. Tap `Solve`.
6. Review the generated move sequence.
7. Use the step buttons to animate the solution forward or backward.

## Solver Rules

The solver expects a complete and physically valid 3x3 cube state.

Validation includes:

- All 54 stickers must be filled
- Each color must appear exactly 9 times
- The six center stickers must all be different
- Edge and corner permutation/orientation must be valid
- Overall parity must be valid

If the state is invalid, the app shows an error instead of returning a solution.

## Notes

- Solver tables are initialized in the background when the app starts.
- Native SceneView libraries are configured for `arm64-v8a` and `x86_64`.
- The current UI text in the app is mainly Chinese, even though this README is in English.
- This repository vendors the `cs.min2phase` solver source from `cs0x7f/min2phase`.

## Future Improvements

- Import cube state from camera scanning
- Save and restore cube sessions
- Add scramble generation
- Improve localization and string resources
- Add unit tests for cube-state conversion and move logic

## License

The app code in this repository is licensed under the MIT License. See [LICENSE](LICENSE).

It also includes third-party code from `cs0x7f/min2phase`. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for attribution and license text copied from the upstream project:

- Upstream repository: https://github.com/cs0x7f/min2phase
- Upstream README includes both GPLv3 and MIT license text
