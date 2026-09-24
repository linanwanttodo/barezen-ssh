This is a Kotlin Multiplatform project targeting Desktop (JVM).

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
    - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
    - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
      For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
      the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
      Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
      folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and
options:

- Desktop app:
    - Hot reload: `./gradlew :desktopApp:hotRun --auto`
    - Standard run: `./gradlew :desktopApp:run`

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Desktop tests: `./gradlew :shared:jvmTest`

### Troubleshooting

- **Fonts: JetBrains Mono not applied / boxes instead of glyphs** — the app bundles JetBrains Mono and a
  Material Symbols woff2 under `docs/ui/fonts/`, but the JVM still resolves fonts through fontconfig on
  Linux. If the UI or terminal falls back to a wrong font, check that fontconfig sees a usable CJK fallback
  (`fc-list | grep -i "Noto Sans CJK\|WenQuanYi"`) and refresh its cache after installing fonts
  (`fc-cache -fv`). Running from a minimal container/chroot without fontconfig installed will always
  fall back to the JVM default logical fonts.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…