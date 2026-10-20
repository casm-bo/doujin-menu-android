# Android device tests

Device tests use the `uitest` build type (`com.doujinmenu.android.uitest`).
They must never target the developer's installed `.debug` or release application.
Gradle can uninstall instrumentation targets during setup or cleanup, so a separate
application ID is required even when leaving APKs installed after a test run.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:connectedUitestAndroidTest
```

The tests create isolated lock preference files and synthetic image fixtures.
They do not change device biometrics or the installed debug application's credentials.
Use `:app:assembleDebug` and `adb install --user 0 -r` to update the real debug app.
Never use `pm clear` or uninstall it for routine verification.
