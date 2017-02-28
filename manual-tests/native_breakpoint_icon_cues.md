## Native Breakpoint Icon Cues Test

Status/Icon reference https://goo.gl/MLwqIU
More info regarding breakpoint cues for native code: https://goto.google.com/ocnfz

## Manually testing by using an existing project.
## The short/quick way to test
0. Download the native android app from https://goto.google.com/orfof. Extract and open it.
Note: Update the local.properties to reflect the correct ndk and sdk directories.
1. Verify that the target link libraries do not have 'baz' linked in CMakeLists.txt.
2. Add breakpoints in Foo::getString in Foo.cpp and Baz::getString in Baz.cpp
Verify that the breakpoints are both set to 'Enabled'
3. Re-sync gradle, rebuild the app and debug the app
4. Breakpoint in Foo::getString should be hit
Verify:
- the icon is set to 'Valid' in Foo::getString()
- the icon is set to 'Enabled' in Baz::getString()
5. Resume the program
Verify:
- output in the app is "Hello from C++ I am in Foo"
- the icon is set to 'Valid' in Foo::getString()
- the icon is set to 'Enabled' in Baz::getString()
6. Stop the app.

7. Update the target_link_libraries CMakeLists.txt
target_link_libraries( # Specifies the target library.
                       native-lib foo baz

                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib} )

8. Verify that existing breakpoints are both set to 'Enabled'
9. Re-sync gradle, rebuild the app and debug the app
10. Breakpoint in Foo::getString should be hit
Verify:
- the icon is set to 'Valid' in Foo::getString()
- the icon is set to 'Valid' in Baz::getString()
11. Resume the program
Verify:
- output in the app is "Hello from C++ I am in Foo"
- the icon is set to 'Valid' in Foo::getString()
- the icon is set to 'Valid' in Baz::getString()
12. Stop the app.

## Manually testing by creating a new project from scratch.
## The long way (if the downloaded native android app does not work for some reason).
0. Create a new project with C++ support with default options.

1. Create Foo.h and Foo.cpp

1.a Update Foo.h

// start code
#include<string>

class Foo {
public:
    Foo() { }
    std::string getString() const;
};
// end code

1.b and update Foo.cpp

// start code
#include "Foo.h"

std::string Foo::getString() const {
    std::string result;
    result = "I am in Foo";
    return result;
}
// end code

2. Create Baz.h and Baz.cpp and make similar updates.

2.a Baz.h
// start code
#include<string>

class Baz {
public:
    Baz() { }
    std::string getString() const;
};
// end code

2.b Baz.cpp

// start code
#include "Baz.h"

std::string Baz::getString() const {
    std::string result;
    result = "I am in Baz";
    return result;
}
// end code

3. Now update app/CMakeLists.txt

# add foo and baz libraries
add_library( # Sets the name of the library.
             foo
             # Sets the library as a shared library.
             SHARED
             # Provides a relative path to your source file(s).
             src/main/cpp/Foo.cpp )

add_library( # Sets the name of the library.
             baz
             # Sets the library as a shared library.
             SHARED
             # Provides a relative path to your source file(s).
             src/main/cpp/Baz.cpp )

# update the target_link_libraries
# Note: baz is not part of the linked libraries.
target_link_libraries( # Specifies the target library.
                       native-lib foo

                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib} )

4. Update native-lib.cpp

# start update
extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_kravindran_as_1icon_1cue_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    const Foo f;
    hello += " " + f.getString();
    return env->NewStringUTF(hello.c_str());
}
# end update

5. Add breakpoints in Foo::getString in Foo.cpp and Baz::getString in Baz.cpp
Verify that the breakpoints are both set to 'Enabled'

6. Re-sync gradle, rebuild the app and debug the app
7. Breakpoint in Foo::getString should be hit
Verify:
- the icon is set to 'Valid' in Foo::getString()
- the icon is set to 'Invalid' in Baz::getString()
8. Resume the program
Verify:
- output in the app is "Hello from C++ I am in Foo"
- the icon is set to 'Valid' in Foo::getString()
- the icon is set to 'Invalid' in Baz::getString()

9. Stop the app.

10. Update the CMakeLists.txt
# update the target_link_libraries
# Note: baz is now part of the linked libraries.
target_link_libraries( # Specifies the target library.
                       native-lib foo baz

                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib} )

11. Verify that existing breakpoints are both set to 'Enabled'
12. Re-sync gradle, rebuild the app and debug the app
13. Breakpoint in Foo::getString should be hit
Verify:
- the icon is set to 'Valid' in Foo::getString()
- the icon is set to 'Valid' in Baz::getString()

14. Resume the program
Verify:
- output in the app is "Hello from C++ I am in Foo"
- the icon is set to 'Valid' in Foo::getString()
- the icon is set to 'Valid' in Baz::getString()

15. Stop the app.
