## "Install CMake" Quick-Fix Test

Make sure that your Android SDK does not have CMake installed.

Create a new project, check the box "Include C++ support" and
accept all default values in the "New Project" wizard.

A sync error should be displayed in the "Messages Gradle Sync"
view: "Failed to find CMake".

Click the "Install CMake and sync project" quick-fix hyperlink.

The "SDK Quickfix Installation" should appear and it should
automatically start the installation of CMake.

After installation is done, click "Finish" on the dialog.

The dialog should be closed automatically and the IDE should
start a new "Gradle Sync" operation.

"Gradle Sync" should finish without errors.