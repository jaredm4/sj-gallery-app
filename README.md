# SJ Gallery

A small, simple Android slideshow app. Build galleries from your own photos,
tap one, and it plays full screen in a freshly shuffled order.

Built for personal use and installed by sideloading; it is not published on
any app store. I could not find a good slideshow gallery app on the app store that didn't have many compromises (ads, photo count limits, lack of privacy, etc), so built this one.

Bare bones, but works well.

## Features

- **Unlimited galleries, unlimited photos** per gallery.
- **Collage covers**: each gallery's thumbnail is a 2x2 collage of four random
  photos, regenerated whenever photos are added or removed.
- **Shuffled slideshow**: the order is reshuffled every time a slideshow is
  opened, and kept stable while it loops.
- **Full screen, screen stays on** while a slideshow is playing.
- **Configurable timing** (1-120 seconds per photo), fit-or-fill scaling, and
  an optional crossfade.
- **Animated GIF / WebP** play and loop; the slide timer always wins, even
  mid-loop.
- **Private copies**: picked photos are copied into app-private storage, so
  the originals can be moved or deleted afterwards.
- **Encrypted at rest**: every photo, cover and the gallery catalogue is
  encrypted with AES-256-GCM (details below).
- **Crop to your screen**: reposition a photo behind a frame shaped like the
  device's screen, so "fill" mode shows the part you care about.
- **Storage-friendly**: photos larger than the screen are downscaled on import
  (optional). Animated images are stored untouched.
- **No permissions, no network.** The app cannot reach the internet.

### Using it

| Where | Action |
| --- | --- |
| Home grid | Tap a gallery to play it. Tap its pencil button to manage it. |
| Manage screen | **Add photos** opens the system file picker (multi-select). Tap photos to select them, then the bin icon removes them. With exactly one (non-animated) photo selected, the pencil icon opens the crop editor. The top bar has play, rename and delete-gallery. |
| Crop editor | Drag and pinch the photo behind the fixed frame; the button underneath flips the frame between portrait and landscape. The tick saves. |
| Slideshow | Tap the right half to skip forward, the left half to go back. Use the system back gesture to exit. |
| Settings (gear icon) | Time per photo, fit/fill, transition, shrink-on-import. |

## Build and install

You need a Mac/PC with Android Studio and an Android 11+ phone.

### 1. Install Android Studio

```sh
brew install --cask android-studio
```

Launch it once and accept the **Standard** setup; this downloads the Android
SDK. Nothing else (Java, Gradle) needs installing separately, as Android
Studio bundles both.

### 2. Open the project

*File > Open*, choose this folder, and wait for the first "Gradle sync" to
finish (it downloads Gradle and the libraries, which takes a few minutes).
If Android Studio offers to upgrade the Android Gradle Plugin, you can decline.

### 3. Enable USB debugging on the phone

1. *Settings > About phone*: tap **Build number** seven times.
2. *Settings > System > Developer options*: turn on **USB debugging**.
3. Connect the phone by USB and accept the "Allow USB debugging?" prompt.

### 4. Run

Pick your phone in the device dropdown in Android Studio's toolbar and press
**Run** (the green triangle). The app is built, installed and launched. It
stays installed after you unplug; repeat this step to install an update.

### Alternative: build an APK file

*Build > Generate App Bundles or APKs > Generate APKs* produces
`app/build/outputs/apk/debug/app-debug.apk`, which can be copied to the phone
and opened to install (Android will ask you to allow installs from that source).

### Alternative: command line

```sh
brew install gradle                 # once, only to generate the wrapper
./scripts/bootstrap-wrapper.sh      # creates ./gradlew
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew installDebug              # build and install on the connected phone
./gradlew test                      # run unit tests
```

### Things to know

- **Uninstalling the app deletes its galleries.** They exist only inside the
  app, encrypted with a key that is destroyed on uninstall. Installing an
  update over the top keeps everything.
- Updates must be built on the same computer (or with the same
  `~/.android/debug.keystore`), because Android only accepts an update signed
  with the same key as the installed app.
- Backups and device-to-device transfer are deliberately disabled, since the
  encryption key cannot leave the device.

## How it is built

Kotlin, Jetpack Compose (Material 3), single activity, MVVM.

```
app/src/main/kotlin/app/sjgallery/
├── SjGalleryApp.kt        Application; configures the Coil image loader
├── AppContainer.kt        Manual dependency wiring
├── data/
│   ├── GalleryRepository  Source of truth: galleries, imports, covers
│   ├── crypto/            AES-GCM envelope encryption (Android Keystore)
│   ├── media/             Import + downscale, cropping, cover collage, sizing maths
│   ├── model/             Serializable Library / Gallery / Photo
│   ├── settings/          Preferences (DataStore)
│   └── storage/           Encrypted JSON catalogue, atomic file writes
├── image/                 Coil fetcher that decrypts images in memory
└── ui/                    Compose screens + view models, navigation, theme
```

Notable decisions:

- **Encrypted JSON catalogue instead of a database.** The metadata is tiny
  (about 100 bytes per photo), and a single file lets it share the photos'
  encryption instead of needing an encrypted-database dependency.
- **Envelope encryption.** A random 256-bit data key encrypts files
  in-process (fast, even for large GIFs). That key is stored wrapped by a
  second key that is generated in, and never leaves, the Android Keystore.
- **Decrypt to memory only.** A custom Coil `Fetcher` decrypts into a buffer
  and hands it to the standard decoders; plaintext is never written to disk.
- **Transitions are pluggable.** `SlideTransition` (settings) maps to a
  Compose `ContentTransform` in one function in `SlideshowScreen.kt`; adding
  an animation means adding an enum entry and one `when` branch.
- **Shuffle lives in the view model**, which is created per slideshow launch:
  a new order each time you open one, stable across loops and rotation.

### What the encryption does and does not protect

App-private storage is already inaccessible to other apps on a non-rooted
device. The extra encryption layer means that the stored files are useless
without the Keystore-held key: copying them off the device (root access,
forensic imaging, a leaked backup) yields only ciphertext.

It does not protect against someone using the unlocked phone, since there is
no in-app lock, or against malware with root access *while the app is able to
use its key*. File names and sizes are not hidden.

### How "shrink on import" sizes photos

A photo whose longest edge exceeds the display's longest edge is scaled down,
keeping its aspect ratio, until it fits; nothing is cropped or enlarged. The
result is stored as JPEG (quality 90), or PNG if it has transparency. Photos
already small enough, and all animated images, are stored byte-for-byte.

### Cropping

Cropping permanently replaces the gallery's private copy of a photo (the file
you originally imported from is never touched). The chosen area is stored as
fractions of the image, so the low-resolution editor preview and the
full-resolution crop agree. Animated images cannot be cropped, since that
would flatten them to a single frame.

## Possible next steps

- Video support.
- More transitions (slide, zoom / Ken Burns).
- Per-gallery slideshow settings.
- App lock (biometric) before galleries are shown.
