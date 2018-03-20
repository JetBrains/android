# C++ Manual Tests

## Project System Header Files

### Simple test
1. From Android Studio choose `File/New/New Project...`
1. Click "Include C++ Support"
1. Click through the wizard `Next->Next->Basic Activity->Next->Next`
1. In Android project view open app/cpp/includes.

**Expect to see 'includes' node**


![New Project Enhanced Includes][res/cxx/enhanced-header-files/new-project.png]

NOTE: On Windows the slashes in paths displayed should be back slashes.

#### Add a new user header file
1. Right-click on `cpp` and choose `New C/C++ Header File`
1. Name the new header file `my-header-file.h`

**Expect to see 'my-header-file.h' node under `includes`**

![New Project Enhanced Includes][res/cxx/enhanced-header-files/add-new-header-file.png]

NOTE: On Windows the slashes in paths displayed should be back slashes.

### Endless Tunnel -- Viewing NDK sub components

1. From Android Studio choose `File/New/Import Sample...`
1. Type `NDK` and choose Endless Tunnel
1. Open `app/cpp/game/includes/NDK Components`

**Expect to see several sub-nodes under 'NDK Components' node**


![New Project Enhanced Includes][res/cxx/enhanced-header-files/endless-tunnel-ndk-components.png]

NOTE 1: On Windows the slashes in paths displayed should be back slashes.

NOTE 2: The exact content of the sub-nodes depends on the version of the NDK installed.


### Viewing CDep sub components

1. Clone this project from github: `git clone https://github.com/jomof/cdep-android-studio-freetype-sample.git`
1. `cd cdep-android-studio-freetype-sample`
1. On Mac/Linux `./cdep`, on Windows `cdep`
1. Open project `File/Open..` choose `/path/to/projects/cdep-android-studio-freetype-sample/build.gradle`
1. Open `app/cpp/includes/CDep Packages`

**Expect to see two sub-nodes under 'CDep Packages' node: freetype and SDL**


![New Project Enhanced Includes][res/cxx/enhanced-header-files/cdep-free-type-example.png]