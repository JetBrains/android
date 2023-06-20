The file `multiPreviewTestLibrary.aar` is an android library that only contains
the file called `MultiPreviewAnnotations.kt`.

If the test projects used in this module are updated, some compatibility issues
may appear between those projects and this library. In such case, the
`multiPreviewTestLibrary.aar` file should be regenerated. To do that, follow the
instructions below:

1. Open Android Studio and create a new empty Compose project using the package
name `com.example.mytestlibrary`
2. Delete the content of `AndroidManifest.xml`
3. Open `app/build.gradle` and modify the line `id 'com.android.application'`
to `id 'com.android.library'`
4. Also in `app/build.gradle`, delete the line `applicationId ...`
5. Copy `MultiPreviewAnnotations.kt` into the source code of the new project
(`app/src/main/java/com/example/mytestlibrary`)
6. Build the project
7. Use `app/build/outputs/aar/app-debug.aar` to replace `multiPreviewTestLibrary.aar`
