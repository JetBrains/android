* Creating the minnativeapp-debug project

Apply the instructions at:
http://google3/experimental/users/adamdoes/g3doc/e2e-framework/run-apk-test.md

with the following exceptions:

* Copy this project to `/tmp` directory on the local machine to
  avoid having your username embedded in the `.so` files.

* After you build the APK, before you import it into Android Studio, move the
  project `/tmp/minnativeapp` to some other directory such as
  `/tmp/minnativeapp2`. This will cause the Android Studio APK importer think
  that none of the original project directories exist on the local machine (and
  thus, require path mappings).

* Add the symbol files files to the project.

* Add the path mapping for `/tmp/minnativeapp/src/main/cpp/`.

* Close the APK project.

* Copy the APK project to `minnativeapp-apk`.

* Update the symbol files under `minnativeapp-apk/minnativeapp/build`.

* Update the path mapping in the `minnativeapp-debug.iml` file so that it
  refers to the original relative path `$MODULE_DIR$/minnativeapp/src/main/cpp`.

* Update the JDK in the `minnativeapp-debug.iml` file (to avoid missing Android
  SDK error in the run configuration).
