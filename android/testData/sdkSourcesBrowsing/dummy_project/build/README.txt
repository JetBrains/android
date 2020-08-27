This jar file contains classes that must be loaded by the tests in order for
code highlighting to work.

If you need to recreate the jar, you can do so using the following commands (on
a Unix system):

# Assumes $ANDROID_SDK_ROOT is set to an Android SDK on your machine
# with a compatible platform(*) installed
$ pwd
.../sdkSourcesBrowsing/dummy_project
$ mkdir -p build/classes/
$ find . -name "*.java" | xargs javac -classpath $ANDROID_SDK_ROOT/platforms/(platform)/android.jar -sourcepath . -d build/classes
$ cd build/
$ jar cf classes.jar -C classes/ .

(*) Most recent platforms should be compatible, as these test classes aren't
using anything too sophisticated from the Android SDK.
