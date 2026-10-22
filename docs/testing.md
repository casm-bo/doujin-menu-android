# Android device tests

Device tests use the `uitest` build type (`com.doujinmenu.android.uitest`).
They must never target the developer's installed `.debug` or release application.
Gradle can uninstall instrumentation targets during setup or cleanup, so a separate
application ID is required even when leaving APKs installed after a test run.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testUitestUnitTest :app:lintDebug :app:assembleDebug
.\gradlew.bat :app:connectedUitestAndroidTest
```

The tests create isolated lock preference files and synthetic image fixtures.
They do not change device biometrics or the installed debug application's credentials.
Use `:app:assembleDebug` and `adb install --user 0 -r` to update the real debug app.
Never use `pm clear` or uninstall it for routine verification.

For an already installed isolated target, run instrumentation without Gradle's
installation lifecycle (replace `SERIAL` with the intended device):

```powershell
adb -s SERIAL install --user 0 -r app/build/outputs/apk/uitest/app-uitest.apk
adb -s SERIAL install --user 0 -r -t app/build/outputs/apk/androidTest/uitest/app-uitest-androidTest.apk
adb -s SERIAL shell am instrument --user 0 -w com.doujinmenu.android.uitest.test/androidx.test.runner.AndroidJUnitRunner
```

`LibraryScrollBenchmarkTest` uses 600 synthetic books and one local cover to
separate UI/state costs from network loading. It reports frame duration and scroll
state writes; this debug-build workload is diagnostic, not a release benchmark.
