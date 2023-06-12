/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.project.build.output

import com.android.SdkConstants
import com.android.utils.cxx.process.NativeBuildOutputClassifier
import com.google.common.truth.Truth
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.FileMessageEventImpl
import com.intellij.build.events.impl.MessageEventImpl
import org.junit.Assume
import org.junit.Test
import java.io.File

class ClangOutputParserTest {
  // Notes on test input setup: any line that should be consumed by the parser must start with "* " (after trimIndent()).

  @Test
  fun `unrelated input`() = assertParser("""
    Unrelated line1
    Unrelated line2
    """) {
    assertDiagnosticMessages() // empty
  }

  @Test
  fun `ndk-build - nothing`() = assertParser("""
    > Task :mupen64plus-core:externalNativeBuildDebug
  * Build mupen64plus-core_armeabi-v7a
  * make: Nothing to be done for `mupen64plus-core'.
  * Build mupen64plus-core_arm64-v8a
  * make: Nothing to be done for `mupen64plus-core'.
  * Build mupen64plus-core_x86
  * make: Nothing to be done for `mupen64plus-core'.
  * Build mupen64plus-core_x86_64
  * make: Nothing to be done for `mupen64plus-core'.
    > Task :mupen64plus-core:mergeDebugJniLibFolders UP-TO-DATE
    """) {
    assertDiagnosticMessages()
  }

  @Test
  fun `ndk-build - no build errors`() = assertParser("""
    > Task :app:externalNativeBuildDebug
  * Build hello_world_armeabi-v7a
  * [armeabi-v7a] Compile arm    : hello-world <= blah1.c
  * [armeabi-v7a] Compile arm    : hello-world <= blah2.c
  * [armeabi-v7a] Compile arm    : hello-world <= blah3.c
  * [armeabi-v7a] SharedLibrary  : hello-world.so
  * Build hello_world_arm64-v8a
    > Task :mupen64plus-video-gln64:mergeDebugJniLibFolders UP-TO-DATE
  """) {
    assertDiagnosticMessages()
  }

  @Test
  fun `ndk-build - simple case`() = assertParser("""
    > Task :some:gradle:task UP-TO-DATE
    > Task :foo:bar:app:externalNativeBuildDebug
  * Build app_armeabi-v7a
  * [armeabi-v7a] Compile++ arm  : app <= app.cpp
  * /usr/local/home/jeff/hello-world/src/app.cpp:1:1: warning: something is suboptimal
  *         some randome code
  *         ^~~~~~~~~
  * 1 warning generated.
    > Task :some:other:gradle:task UP-TO-DATE
    """) {
    assertDiagnosticMessages(
      "[:foo:bar:app Debug armeabi-v7a]" to "/usr/local/home/jeff/hello-world/src/app.cpp:1:1: warning: something is suboptimal")
  }

  @Test
  fun `ndk-build - simple case - AGP 7_0 or later`() = assertParser("""
    > Task :some:gradle:task UP-TO-DATE
    > Task :foo:bar:app:buildNdkBuildDebug
  * C/C++: Build app_armeabi-v7a
  * C/C++: [armeabi-v7a] Compile++ arm  : app <= app.cpp
  * C/C++: /usr/local/home/jeff/hello-world/src/app.cpp:1:1: warning: something is suboptimal
    > Task :some:other:gradle:task UP-TO-DATE
    BUILD FAILED in 3s
    """) {
    assertDiagnosticMessages(
      "[:foo:bar:app Debug armeabi-v7a]" to "/usr/local/home/jeff/hello-world/src/app.cpp:1:1: warning: something is suboptimal")
  }

  @Test
  fun `ndk-build - no module name`() = assertParser("""
    > Task :some:gradle:task UP-TO-DATE
    > Task :externalNativeBuildDebug
  * Build app_armeabi-v7a
  * [armeabi-v7a] Compile++ arm  : app <= app.cpp
  * /usr/local/home/jeff/hello-world/src/app.cpp:1:1: warning: something is suboptimal
  *         some randome code
  *         ^~~~~~~~~
  * 1 warning generated.
    > Task :some:other:gradle:task UP-TO-DATE
    """) {
    assertDiagnosticMessages(
      "[ Debug armeabi-v7a]" to "/usr/local/home/jeff/hello-world/src/app.cpp:1:1: warning: something is suboptimal")
  }

  @Test
  fun `ndk-build - different abis`() = assertParser("""
    > Task :some:gradle:task UP-TO-DATE
    > Task :app:externalNativeBuildDebug
  * Build app_armeabi-v7a
  * [armeabi-v7a] Compile++ arm  : app <= app.cpp
  * /usr/local/home/jeff/hello-world/src/app.cpp:1:1: warning: something is suboptimal
  *         some randome code
  *         ^~~~~~~~~
  * 1 warning generated.
  * [x86] Compile++ arm  : app2 <= app2.cpp
  * /usr/local/home/jeff/hello-world/src/app2.cpp:2:2: error: something is wrong
  *         some randome code 2
  *         ^~~~~~~~~
  * 1 warning generated.
    > Task :some:other:gradle:task UP-TO-DATE
    """) {
    assertDiagnosticMessages(
      "[:app Debug armeabi-v7a]" to "/usr/local/home/jeff/hello-world/src/app.cpp:1:1: warning: something is suboptimal",
      "[:app Debug x86]" to "/usr/local/home/jeff/hello-world/src/app2.cpp:2:2: error: something is wrong"
    )
  }

  @Test
  fun `ndk-build - multiple interleaved diagnostic messages`() = assertParser("""
    > Task :some:gradle:task UP-TO-DATE
    > Task :app:externalNativeBuildDebug
  * Build app_armeabi-v7a
  * [armeabi-v7a] Compile++ arm  : app <= app.cpp
  * /usr/local/home/jeff/hello-world/src/app.cpp:1:1: warning: something is suboptimal
  *         some randome code
  *         ^~~~~~~~~
  * [x86] Compile++ arm  : app2 <= app2.cpp
  * /usr/local/home/jeff/hello-world/src/app2.cpp:2:2: error: something is wrong
  *         some randome code 2
  *         ^~~~~~~~~
  * 1 warning generated.
  * 1 warning generated.
    > Task :some:other:gradle:task UP-TO-DATE
    """) {
    assertDiagnosticMessages(
      "[:app Debug armeabi-v7a]" to "/usr/local/home/jeff/hello-world/src/app.cpp:1:1: warning: something is suboptimal",
      "[:app Debug x86]" to "/usr/local/home/jeff/hello-world/src/app2.cpp:2:2: error: something is wrong"
    )
  }

  @Test
  fun `ndk-build - multiple gradle tasks`() = assertParser("""
    > Task :some:gradle:task UP-TO-DATE
    > Task :app:externalNativeBuildDebug
  * Build app_armeabi-v7a
  * [armeabi-v7a] Compile++ arm  : app <= app.cpp
  * /usr/local/home/jeff/hello-world/src/app.cpp:1:1: warning: something is suboptimal
  *         some randome code
  *         ^~~~~~~~~
  * 1 warning generated.
  * [x86] Compile++ arm  : app2 <= app2.cpp
  * /usr/local/home/jeff/hello-world/src/app2.cpp:2:2: error: something is wrong
  *         some randome code 2
  *         ^~~~~~~~~
  * 1 warning generated.
    > Task :app:externalNativeBuildRelease
  * Build app_armeabi-v7a
  * [armeabi-v7a] Compile++ arm  : app <= app.cpp
  * /usr/local/home/jeff/hello-world/src/app.cpp:3:3: warning: something is suboptimal
  *         some randome code
  *         ^~~~~~~~~
  * 1 warning generated.
  * [x86] Compile++ arm  : app2 <= app2.cpp
  * /usr/local/home/jeff/hello-world/src/app2.cpp:4:4: error: something is wrong
  *         some randome code 2
  *         ^~~~~~~~~
  * 1 warning generated.
    > Task :some:other:gradle:task UP-TO-DATE
    """) {
    assertDiagnosticMessages(
      "[:app Debug armeabi-v7a]" to "/usr/local/home/jeff/hello-world/src/app.cpp:1:1: warning: something is suboptimal",
      "[:app Debug x86]" to "/usr/local/home/jeff/hello-world/src/app2.cpp:2:2: error: something is wrong",
      "[:app Release armeabi-v7a]" to "/usr/local/home/jeff/hello-world/src/app.cpp:3:3: warning: something is suboptimal",
      "[:app Release x86]" to "/usr/local/home/jeff/hello-world/src/app2.cpp:4:4: error: something is wrong"
    )
  }

  @Test
  fun `cmake - nothing`() = assertParser("""
    > Task :app:externalNativeBuildDebug
  * Build multiple targets ...
  * ninja: Entering directory `/usr/local/google/home/tgeng/x/test-projects/dolphin/Source/Android/app/.cxx/cmake/debug/arm64-v8a'
  * ninja: no work to do.
  * Build multiple targets ...
  * ninja: Entering directory `/usr/local/google/home/tgeng/x/test-projects/dolphin/Source/Android/app/.cxx/cmake/debug/x86_64'
  * ninja: no work to do.
    > Task :app:compileDebugSources
    """) {
    assertDiagnosticMessages()
  }

  @Test
  fun `cmake - simple case`() = assertParser("""
    > Task :app:externalNativeBuildDebug
  * ninja: Entering directory `/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/arm64-v8a'
  * [1/1] Building CXX object blah.cpp.o
  * FAILED: blah.cpp.o
  * In file included from ../../../../../native/source.cpp:8:
  * ../../../../../native/source.h:12:10: fatal error: 'unresolved.h' file not found
  * #include "unresolved.h"
  *          ^~~~~~~~~~~~~~
  * 1 error generated.
    > Task :some:other:gradle:task UP-TO-DATE
    """) {
    assertDiagnosticMessages(
      "[:app Debug arm64-v8a]" to "/usr/local/google/home/jeff/hello-world/native/source.h:12:10: fatal error: 'unresolved.h' file not found"
    )
  }

  @Test
  fun `cmake - unrecognized ABI`() = assertParser("""
    > Task :app:externalNativeBuildDebug
  * ninja: Entering directory `/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/UNRECOGNIZED'
  * [1/1] Building CXX object blah.cpp.o
  * FAILED: blah.cpp.o
  * In file included from ../../../../../native/source.cpp:8:
  * ../../../../../native/source.h:12:10: fatal error: 'unresolved.h' file not found
  * #include "unresolved.h"
  *          ^~~~~~~~~~~~~~
  * 1 error generated.
    > Task :some:other:gradle:task UP-TO-DATE
    """) {
    assertDiagnosticMessages(
      "[:app Debug]" to "/usr/local/google/home/jeff/hello-world/native/source.h:12:10: fatal error: 'unresolved.h' file not found"
    )
  }

  @Test
  fun `cmake - simple case with AGP 7_0 or later`() = assertParser("""
    > Task :app:buildCMakeDebug
  * C/C++: ninja: Entering directory `/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/arm64-v8a'
  * C/C++: In file included from ../../../../../native/source.cpp:8:
  * C/C++: ../../../../../native/source.h:12:10: fatal error: 'unresolved.h' file not found
    > Task :some:other:gradle:task UP-TO-DATE
    BUILD FAILED in 19s
    """) {
    assertDiagnosticMessages(
      "[:app Debug arm64-v8a]" to "/usr/local/google/home/jeff/hello-world/native/source.h:12:10: fatal error: 'unresolved.h' file not found"
    )
  }

  @Test
  fun `cmake - different ABIs`() = assertParser("""
    > Task :app:externalNativeBuildDebug
  * ninja: Entering directory `/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/arm64-v8a'
  * [1/1] Building CXX object blah.cpp.o
  * FAILED: blah.cpp.o
  * In file included from ../../../../../native/source.cpp:8:
  * ../../../../../native/source.h:12:10: fatal error: 'unresolved.h' file not found
  * #include "unresolved.h"
  *          ^~~~~~~~~~~~~~
  * 1 error generated.
  * ninja: Entering directory `/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/x86'
  * [1/1] Building CXX object blah.cpp.o
  * FAILED: blah.cpp.o
  * In file included from ../../../../../native/source.cpp:8:
  * ../../../../../native/source.h:12:10: fatal error: 'unresolved.h' file not found
  * #include "unresolved.h"
  *          ^~~~~~~~~~~~~~
  * 1 error generated.
    > Task :some:other:gradle:task UP-TO-DATE
    """) {
    assertDiagnosticMessages(
      "[:app Debug arm64-v8a]" to "/usr/local/google/home/jeff/hello-world/native/source.h:12:10: fatal error: 'unresolved.h' file not found",
      "[:app Debug x86]" to "/usr/local/google/home/jeff/hello-world/native/source.h:12:10: fatal error: 'unresolved.h' file not found"
    )
  }

  @Test
  fun `cmake - warnings`() = assertParser("""
    > Task :app:mergeDebugShaders UP-TO-DATE
    > Task :app:compileDebugShaders NO-SOURCE
    > Task :app:generateDebugAssets UP-TO-DATE
    > Task :app:mergeDebugAssets UP-TO-DATE
    > Task :app:compressDebugAssets UP-TO-DATE
    > Task :app:desugarDebugFileDependencies
    > Task :app:processDebugJavaRes NO-SOURCE
    > Task :app:checkDebugDuplicateClasses UP-TO-DATE
    > Task :app:mergeLibDexDebug UP-TO-DATE
    > Task :app:processDebugResources
    > Task :app:configureCMakeDebug[arm64-v8a]
    > Task :app:mergeExtDexDebug
    
    > Task :app:buildCMakeDebug[arm64-v8a] FAILED
    * C/C++: ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/4m6p3s41/arm64-v8a'
    * C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:4:2: warning: "Simulated warning" [-W#warnings]
    * C/C++: #warning "Simulated warning"
    * C/C++:  ^
    * C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:6:3: error: no matching function for call to 'bar'
    * C/C++:   bar(b);
    * C/C++:   ^~~
    * C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:1:13: note: candidate function not viable: no known conversion from 'int' to 'const int *' for 1st argument; take the address of the argument with &
    * C/C++: extern void bar(const int*);
    * C/C++:             ^
    
    > Task :app:configureCMakeDebug[armeabi-v7a]
    
    > Task :app:buildCMakeDebug[armeabi-v7a] FAILED
    * C/C++: ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/4m6p3s41/armeabi-v7a'
    * C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:4:2: warning: "Simulated warning" [-W#warnings]
    * C/C++: #warning "Simulated warning"
    * C/C++:  ^
    * C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:6:3: error: no matching function for call to 'bar'
    * C/C++:   bar(b);
    * C/C++:   ^~~
    * C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:1:13: note: candidate function not viable: no known conversion from 'int' to 'const int *' for 1st argument; take the address of the argument with &
    * C/C++: extern void bar(const int*);
    * C/C++:             ^
    
    > Task :app:configureCMakeDebug[x86]
    
    > Task :app:buildCMakeDebug[x86] FAILED
    * C/C++: ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/4m6p3s41/x86'
    * C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:4:2: warning: "Simulated warning" [-W#warnings]
    * C/C++: #warning "Simulated warning"
    * C/C++:  ^
    * C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:6:3: error: no matching function for call to 'bar'
    * C/C++:   bar(b);
    * C/C++:   ^~~
    * C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:1:13: note: candidate function not viable: no known conversion from 'int' to 'const int *' for 1st argument; take the address of the argument with &
    * C/C++: extern void bar(const int*);
    * C/C++:             ^
    
    > Task :app:configureCMakeDebug[x86_64]
    
    > Task :app:buildCMakeDebug[x86_64] FAILED
    * C/C++: ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/4m6p3s41/x86_64'
    * C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:4:2: warning: "Simulated warning" [-W#warnings]
    * C/C++: #warning "Simulated warning"
    * C/C++:  ^
    * C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:6:3: error: no matching function for call to 'bar'
    * C/C++:   bar(b);
    * C/C++:   ^~~
    * C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:1:13: note: candidate function not viable: no known conversion from 'int' to 'const int *' for 1st argument; take the address of the argument with &
    * C/C++: extern void bar(const int*);
    * C/C++:             ^
    
    > Task :app:mergeDebugJniLibFolders
    BUILD FAILED in 13s
    """) {
    assertDiagnosticMessages(
      "[:app Debug arm64-v8a]" to """
          /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:4:2: warning: "Simulated warning" [-W#warnings]
          #warning "Simulated warning"
           ^
      """.trimIndent(),
      "[:app Debug armeabi-v7a]" to "",
      "[:app Debug x86]" to "",
      "[:app Debug x86_64]" to "",
      "[:app Debug arm64-v8a]" to """
          /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:6:3: error: no matching function for call to 'bar'
            bar(b);
            ^~~
          /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:1:13: note: candidate function not viable: no known conversion from 'int' to 'const int *' for 1st argument; take the address of the argument with &
          extern void bar(const int*);
                      ^
      """.trimIndent(),
      "[:app Debug armeabi-v7a]" to "",
      "[:app Debug x86]" to "",
      "[:app Debug x86_64]" to "",
    )
  }

  @Test
  fun `cmake - multiple gradle tasks`() = assertParser("""
    > Task :app:externalNativeBuildDebug
  * ninja: Entering directory `/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/arm64-v8a'
  * [1/1] Building CXX object blah.cpp.o
  * FAILED: blah.cpp.o
  * In file included from ../../../../../native/source.cpp:8:
  * ../../../../../native/source.h:12:10: fatal error: 'unresolved.h' file not found
  * #include "unresolved.h"
  *          ^~~~~~~~~~~~~~
  * 1 error generated.
  * ninja: Entering directory `/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/x86'
  * [1/1] Building CXX object blah.cpp.o
  * FAILED: blah.cpp.o
  * In file included from ../../../../../native/source.cpp:8:
  * ../../../../../native/source.h:12:10: fatal error: 'unresolved.h' file not found
  * #include "unresolved.h"
  *          ^~~~~~~~~~~~~~
  * 1 error generated.
    > Task :app:externalNativeBuildRelease
  * ninja: Entering directory `/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/arm64-v8a'
  * [1/1] Building CXX object blah.cpp.o
  * FAILED: blah.cpp.o
  * In file included from ../../../../../native/source.cpp:8:
  * ../../../../../native/source.h:12:10: fatal error: 'unresolved.h' file not found
  * #include "unresolved.h"
  *          ^~~~~~~~~~~~~~
  * 1 error generated.
  * ninja: Entering directory `/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/x86'
  * [1/1] Building CXX object blah.cpp.o
  * FAILED: blah.cpp.o
  * In file included from ../../../../../native/source.cpp:8:
  * ../../../../../native/source.h:12:10: fatal error: 'unresolved.h' file not found
  * #include "unresolved.h"
  *          ^~~~~~~~~~~~~~
  * 1 error generated.
    > Task :some:other:gradle:task UP-TO-DATE
    """) {
    assertDiagnosticMessages(
      "[:app Debug arm64-v8a]" to "/usr/local/google/home/jeff/hello-world/native/source.h:12:10: fatal error: 'unresolved.h' file not found",
      "[:app Debug x86]" to "/usr/local/google/home/jeff/hello-world/native/source.h:12:10: fatal error: 'unresolved.h' file not found",
      "[:app Release arm64-v8a]" to "/usr/local/google/home/jeff/hello-world/native/source.h:12:10: fatal error: 'unresolved.h' file not found",
      "[:app Release x86]" to "/usr/local/google/home/jeff/hello-world/native/source.h:12:10: fatal error: 'unresolved.h' file not found"
    )
  }

  @Test
  fun `general - reader closed before there is another line`() = assertParser("""
    > Task :app:externalNativeBuildDebug
  * ninja: Entering directory `/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/arm64-v8a'
  * [1/1] Building CXX object blah.cpp.o
  * FAILED: blah.cpp.o
  * In file included from ../../../../../native/source.cpp:8:
  * ../../../../../native/source.h:12:10: fatal error: 'unresolved.h' file not found
  * #include "unresolved.h"
  *          ^~~~~~~~~~~~~~
  * 1 error generated.
    """) {
    assertDiagnosticMessages(
      "[:app Debug arm64-v8a]" to "/usr/local/google/home/jeff/hello-world/native/source.h:12:10: fatal error: 'unresolved.h' file not found"
    )
  }

  @Test
  fun `linker - unresolved reference`() {
    assertParser("""
      > Task :app:externalNativeBuildDebug
    * ninja: Entering directory `/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/arm64-v8a'
    * [1/1] Linking CXX shared library /usr/local/google/home/jeff/HelloWorld/app/build/intermediates/cmake/debug/obj/x86_64/libmain.so
    * FAILED: /usr/local/google/home/jeff/HelloWorld/app/build/intermediates/cmake/debug/obj/x86_64/libmain.so
    * /usr/local/google/home/jeff/HelloWorld/src/HelloWorld.cpp:33: error: undefined reference to 'foo()'
    * clang++: error: linker command failed with exit code 1 (use -v to see invocation)
    * ninja: build stopped: subcommand failed.
    * :app:externalNativeBuildDebug FAILED
      FAILURE: Build failed with an exception.
      """) {
      assertDiagnosticMessages(
        "[:app Debug arm64-v8a]" to "/usr/local/google/home/jeff/HelloWorld/src/HelloWorld.cpp:33: error: undefined reference to 'foo()'",
        "[:app Debug arm64-v8a]" to "",
      )
    }
  }

  @Test
  fun `linker - unresolved reference with AGP 7_0 or later`() {
    assertParser("""
      > Task :app:buildCMakeDebug
    * C/C++: ninja: Entering directory `/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/arm64-v8a'
    * C/C++: FAILED: /usr/local/google/home/jeff/HelloWorld/app/build/intermediates/cmake/debug/obj/x86_64/libmain.so
    * C/C++: /usr/local/google/home/jeff/HelloWorld/src/HelloWorld.cpp:33: error: undefined reference to 'foo()'
    * C/C++: clang++: error: linker command failed with exit code 1 (use -v to see invocation)
    * :app:externalNativeBuildDebug FAILED
      FAILURE: Build failed with an exception.
      """) {
      assertDiagnosticMessages(
        "[:app Debug arm64-v8a]" to "/usr/local/google/home/jeff/HelloWorld/src/HelloWorld.cpp:33: error: undefined reference to 'foo()'",
        "[:app Debug arm64-v8a]" to "clang++: error: linker command failed with exit code 1 (use -v to see invocation)"
      )
    }
  }

  @Test
  fun `linker - missing library`() {
    assertParser("""
      > Task :app:externalNativeBuildDebug
    * ninja: Entering directory `/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/x86_64'
    * [1/1] Linking CXX shared library /usr/local/google/home/jeff/HelloWorld/app/build/intermediates/cmake/debug/obj/x86_64/libmain.so
    * FAILED: /usr/local/google/home/jeff/HelloWorld/app/build/intermediates/cmake/debug/obj/x86_64/libmain.so
    * /usr/local/google/home/jeff/Android/Sdk/ndk-bundle/toolchains/llvm/prebuilt/windows-x86_64/x86_64-linux-android/bin/ld: error: cannot find -lbdisasm
    * clang++.exe: error: linker command failed with exit code 1 (use -v to see invocation)
    * ninja: build stopped: subcommand failed.
    * :app:externalNativeBuildDebug FAILED
      FAILURE: Build failed with an exception.
    """) {
      assertDiagnosticMessages(
        "[:app Debug x86_64]" to "/usr/local/google/home/jeff/Android/Sdk/ndk-bundle/toolchains/llvm/prebuilt/windows-x86_64/x86_64-linux-android/bin/ld: error: cannot find -lbdisasm",
        "[:app Debug x86_64]" to "clang++.exe: error: linker command failed with exit code 1 (use -v to see invocation)"
      )
    }
  }

  @Test
  fun `linker error has augmented details`() {
    assertParser("""
      > Task :app:externalNativeBuildDebug
    * ninja: Entering directory `/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/x86_64'
    * [1/1] Linking CXX shared library /usr/local/google/home/jeff/HelloWorld/app/build/intermediates/cmake/debug/obj/x86_64/libmain.so
    * FAILED: /usr/local/google/home/jeff/HelloWorld/app/build/intermediates/cmake/debug/obj/x86_64/libmain.so
    * ld: fatal error: /build/intermediates/cmake/debug/obj/armeabi-v7a/libcore.so: open: Invalid argument
    * clang++.exe: error: linker command failed with exit code 1 (use -v to see invocation)
    * ninja: build stopped: subcommand failed.
    * :app:externalNativeBuildDebug FAILED
      FAILURE: Build failed with an exception.
    """) {
      assertDiagnosticMessages(
        "[:app Debug x86_64]" to """
           ld: fatal error: /build/intermediates/cmake/debug/obj/armeabi-v7a/libcore.so: open: Invalid argument
    
           File /build/intermediates/cmake/debug/obj/armeabi-v7a/libcore.so could not be written. This may be caused by insufficient permissions or files being locked by other processes. For example, LLDB may lock .so files while debugging.
        """.trimIndent(),
        "" to ""
      )
    }
  }

  @Test
  fun `windows - relative paths are resolved correctly`() {
    Assume.assumeTrue(SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS)
    assertParser("""
      > Task :app:externalNativeBuildDebug
    * Build multiple targets ...
    * ninja: Entering directory `C:\src\HelloWorld\app\.cxx\cmake\debug\arm64-v8a'
    * [1/1] Building CXX object HelloWorld.cpp.o
    * In file included from ../../../../HelloWorld.cpp:14:
    * ../../../../include\common/header.h:72:1: error: C++ requires a type specifier for all declarations
    * blah;
    * ^
    * 1 error generated.
      > Task :app:externalNativeBuildDebug FAILED
    """.trimIndent().replace("\n", "\r\n")) {
      // Note the weird backward slash in middle of forward slashes in the diagnostic message line. It's what the clang compiler generates.
      assertDiagnosticMessages(
        "[:app Debug arm64-v8a]" to """
           In file included from C:\src\HelloWorld\app\HelloWorld.cpp:14:
           C:\src\HelloWorld\app\include\common\header.h:72:1: error: C++ requires a type specifier for all declarations
         """.trimIndent())
    }
  }

  @Test
  fun `windows - path with invalid character is ignored`() {
    Assume.assumeTrue(SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS)
    assertParser("""
      > Task :app:externalNativeBuildDebug
    * Build multiple targets ...
    * ninja: Entering directory `C:\src\HelloWorld\app\.cxx\cmake\debug\arm64-v8a'
    * [1/1] Building CXX object HelloWorld.cpp.o
    * In file included from ../../../../HelloWorld.cpp:14:
    * ../../path?with?invalid?char?.h:72:1: error: C++ requires a type specifier for all declarations
    * blah;
    * ^
    * 1 error generated.
      > Task :app:externalNativeBuildDebug FAILED
    """.trimIndent().replace("\n", "\r\n")) {
      assertDiagnosticMessages()
    }
  }

  @Test
  fun `windows - absolute paths are resolved correctly`() {
    Assume.assumeTrue(SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS)
    assertParser("""
      > Task :app:externalNativeBuildDebug
    * Build multiple targets ...
    * ninja: Entering directory `C:\src\HelloWorld\app\.cxx\cmake\debug\arm64-v8a'
    * [1/1] Building CXX object HelloWorld.cpp.o
    * In file included from C:\src\HelloWorld\app\HelloWorld.cpp:14:
    * C:\src\HelloWorld\app\include\common\header.h:72:1: error: C++ requires a type specifier for all declarations
    * blah;
    * ^
    * 1 error generated.
      > Task :app:externalNativeBuildDebug FAILED
    """.trimIndent().replace("\n", "\r\n")) {
      // Note the weird backward slash in middle of forward slashes in the diagnostic message line. It's what the clang compiler generates.
      assertDiagnosticMessages(
        "[:app Debug arm64-v8a]" to """
             In file included from C:\src\HelloWorld\app\HelloWorld.cpp:14:
             C:\src\HelloWorld\app\include\common\header.h:72:1: error: C++ requires a type specifier for all declarations
           """.trimIndent())
    }
  }

  /**
   * Asserts the essential content of the [FileMessageEventImpl] for ease of testing covering aspects other than every fine details about
   * the [FileMessageEventImpl]. Additional tests under this method is used cover the full scope.
   * @param expected expected messages that should be emitted by this parser. The first of the pair is the suffix of the compiler group,
   * the second of the pair is the prefix of the message details.
   */
  private fun List<MessageEventImpl>.assertDiagnosticMessages(vararg expected: Pair<String, String>) {
    Truth.assertThat(this).named("emittedFileMessageEvents").hasSize(expected.size)
    for ((event, pair) in zip(expected)) {
      val (compilerGroup, msg) = pair
      Truth.assertThat(event.group).named("compiler group").endsWith(compilerGroup)
      // Details may contains file inclusion path in addition to the expected error itself.
      Truth.assertThat(event.result.details).named("diagnostic details").contains(msg.normalizeSeparator())
    }
  }

  @Test
  fun `general - FileMessageEventImpl has correct properties populated`() = assertParser("""
    > Task :app:externalNativeBuildDebug
  * ninja: Entering directory `/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/arm64-v8a'
  * [1/135] Building CXX object blah.cpp.o
  * FAILED: blah.cpp.o
  * In file included from ../../../../../native/source.cpp:8:
  * ../../../../../native/source.h:12:10: fatal error: 'unresolved.h' file not found
  * #include "unresolved.h"
  *          ^~~~~~~~~~~~~~
  * 1 error generated.
    > Task :some:other:gradle:task UP-TO-DATE
    BUILD FAILED in 3s
    """) {
    Truth.assertThat(this).hasSize(1)
    with(this[0]) {
      Truth.assertThat(kind).isEqualTo(MessageEvent.Kind.ERROR)
      Truth.assertThat(group).isEqualTo("Clang Compiler [:app Debug arm64-v8a]")
      Truth.assertThat(message).isEqualTo("'unresolved.h' file not found [arm64-v8a]")
      Truth.assertThat(result.details).isEqualTo("""
          In file included from /usr/local/google/home/jeff/hello-world/native/source.cpp:8:
          /usr/local/google/home/jeff/hello-world/native/source.h:12:10: fatal error: 'unresolved.h' file not found
          #include "unresolved.h"
                   ^~~~~~~~~~~~~~
          """.trimIndent().normalizeSeparator())
      with((this as FileMessageEventImpl).result.filePosition) {
        Truth.assertThat(file.path).isEqualTo("/usr/local/google/home/jeff/hello-world/native/source.h".normalizeSeparator())
        // The following two numbers are one less than that from clang since Clang error output counts from 1 while Intellij counts from 0.
        Truth.assertThat(startLine).isEqualTo(11)
        Truth.assertThat(startColumn).isEqualTo(9)
      }
    }
  }

  /**
   * The parse(...) function is only called from one thread, but the lines it receives can be out-of-order.
   * The parser itself must establish the order either by using the reader to read to the end of the current task
   * or by using parentEventId to group lines into a shared context.
   *
   * This test is a real sequence of lines obtained from running Android Studio. It is out-of-order but distinct
   * parentEventIds can are assigned to each 'thread' of messages.
   */
  @Test
  fun `check interleaved event order`() {
    checkInterleavedParse(
      """
      EXECUTE_TASK:0|Executing tasks: [:app:assembleDebug, :app:assembleDebugUnitTest, :app:assembleDebugAndroidTest] in project /Users/jomof/projects/repro/as-bad-error-context-repro
      EXECUTE_TASK:0|Starting Gradle Daemon...
      -406546952_Task :app:mergeProjectDexDebug|> Task :app:mergeProjectDexDebug UP-TO-DATE
      -406546952_Task :app:configureCMakeDebug[arm64-v8a]|> Task :app:configureCMakeDebug[arm64-v8a]
      -406546952_Task :app:buildCMakeDebug[arm64-v8a]|> Task :app:buildCMakeDebug[arm64-v8a] FAILED
      -406546952_Task :app:buildCMakeDebug[arm64-v8a]|C/C++: ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/4m6p3s41/arm64-v8a'
      -406546952_Task :app:configureCMakeDebug[armeabi-v7a]|> Task :app:configureCMakeDebug[armeabi-v7a]
      -406546952_Task :app:buildCMakeDebug[arm64-v8a]|C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:4:2: warning: Simulated warning [-W#warnings]
      -406546952_Task :app:buildCMakeDebug[arm64-v8a]|C/C++: #warning Simulated warning
      -406546952_Task :app:buildCMakeDebug[arm64-v8a]|C/C++:  ^
      -406546952_Task :app:buildCMakeDebug[arm64-v8a]|C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:6:3: error: no matching function for call to 'bar'
      -406546952_Task :app:buildCMakeDebug[arm64-v8a]|C/C++:   bar(b);
      -406546952_Task :app:buildCMakeDebug[arm64-v8a]|C/C++:   ^~~
      -406546952_Task :app:buildCMakeDebug[arm64-v8a]|C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:1:13: note: candidate function not viable: no known conversion from 'int' to 'const int *' for 1st argument; take the address of the argument with &
      -406546952_Task :app:buildCMakeDebug[arm64-v8a]|C/C++: extern void bar(const int*);
      -406546952_Task :app:buildCMakeDebug[arm64-v8a]|C/C++:             ^
      -406546952_Task :app:buildCMakeDebug[arm64-v8a]|C/C++: 1 warning and 1 error generated.
      -406546952_Task :app:buildCMakeDebug[armeabi-v7a]|> Task :app:buildCMakeDebug[armeabi-v7a] FAILED
      -406546952_Task :app:buildCMakeDebug[armeabi-v7a]|C/C++: ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/4m6p3s41/armeabi-v7a'
      -406546952_Task :app:buildCMakeDebug[armeabi-v7a]|C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:4:2: warning: Simulated warning [-W#warnings]
      -406546952_Task :app:configureCMakeDebug[x86]|> Task :app:configureCMakeDebug[x86]
      -406546952_Task :app:buildCMakeDebug[armeabi-v7a]|C/C++: #warning Simulated warning
      -406546952_Task :app:buildCMakeDebug[armeabi-v7a]|C/C++:  ^
      -406546952_Task :app:buildCMakeDebug[armeabi-v7a]|C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:6:3: error: no matching function for call to 'bar'
      -406546952_Task :app:buildCMakeDebug[armeabi-v7a]|C/C++:   bar(b);
      -406546952_Task :app:buildCMakeDebug[armeabi-v7a]|C/C++:   ^~~
      -406546952_Task :app:buildCMakeDebug[armeabi-v7a]|C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:1:13: note: candidate function not viable: no known conversion from 'int' to 'const int *' for 1st argument; take the address of the argument with &
      -406546952_Task :app:buildCMakeDebug[armeabi-v7a]|C/C++: extern void bar(const int*);
      -406546952_Task :app:buildCMakeDebug[armeabi-v7a]|C/C++:             ^
      -406546952_Task :app:buildCMakeDebug[armeabi-v7a]|C/C++: 1 warning and 1 error generated.
      -406546952_Task :app:buildCMakeDebug[x86]|> Task :app:buildCMakeDebug[x86] FAILED
      -406546952_Task :app:buildCMakeDebug[x86]|C/C++: ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/4m6p3s41/x86'
      -406546952_Task :app:buildCMakeDebug[x86]|C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:4:2: warning: Simulated warning [-W#warnings]
      -406546952_Task :app:configureCMakeDebug[x86_64]|> Task :app:configureCMakeDebug[x86_64]
      -406546952_Task :app:buildCMakeDebug[x86]|C/C++: #warning Simulated warning
      -406546952_Task :app:buildCMakeDebug[x86]|C/C++:  ^
      -406546952_Task :app:buildCMakeDebug[x86]|C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:6:3: error: no matching function for call to 'bar'
      -406546952_Task :app:buildCMakeDebug[x86]|C/C++:   bar(b);
      -406546952_Task :app:buildCMakeDebug[x86]|C/C++:   ^~~
      -406546952_Task :app:buildCMakeDebug[x86]|C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:1:13: note: candidate function not viable: no known conversion from 'int' to 'const int *' for 1st argument; take the address of the argument with &
      -406546952_Task :app:buildCMakeDebug[x86]|C/C++: extern void bar(const int*);
      -406546952_Task :app:buildCMakeDebug[x86]|C/C++:             ^
      -406546952_Task :app:buildCMakeDebug[x86]|C/C++: 1 warning and 1 error generated.
      -406546952_Task :app:buildCMakeDebug[x86_64]|> Task :app:buildCMakeDebug[x86_64] FAILED
      -406546952_Task :app:buildCMakeDebug[x86_64]|C/C++: ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/4m6p3s41/x86_64'
      -406546952_Task :app:mergeDebugJniLibFolders|> Task :app:mergeDebugJniLibFolders UP-TO-DATE
      -406546952_Task :app:assembleDebugUnitTest|> Task :app:assembleDebugUnitTest UP-TO-DATE
      -406546952_Task :app:validateSigningDebug|> Task :app:validateSigningDebug UP-TO-DATE
      -406546952_Task :app:buildCMakeDebug[x86_64]|C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:4:2: warning: Simulated warning [-W#warnings]
      -406546952_Task :app:writeDebugAppMetadata|> Task :app:writeDebugAppMetadata UP-TO-DATE
      -406546952_Task :app:bundleDebugClassesToCompileJar|> Task :app:bundleDebugClassesToCompileJar UP-TO-DATE
      -406546952_Task :app:writeDebugSigningConfigVersions|> Task :app:writeDebugSigningConfigVersions UP-TO-DATE
      -406546952_Task :app:preDebugAndroidTestBuild|> Task :app:preDebugAndroidTestBuild SKIPPED
      -406546952_Task :app:buildCMakeDebug[x86_64]|C/C++: #warning Simulated warning
      -406546952_Task :app:buildCMakeDebug[x86_64]|C/C++:  ^
      -406546952_Task :app:buildCMakeDebug[x86_64]|C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:6:3: error: no matching function for call to 'bar'
      -406546952_Task :app:buildCMakeDebug[x86_64]|C/C++:   bar(b);
      -406546952_Task :app:buildCMakeDebug[x86_64]|C/C++:   ^~~
      -406546952_Task :app:buildCMakeDebug[x86_64]|C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:1:13: note: candidate function not viable: no known conversion from 'int' to 'const int *' for 1st argument; take the address of the argument with &
      -406546952_Task :app:buildCMakeDebug[x86_64]|C/C++: extern void bar(const int*);
      -406546952_Task :app:buildCMakeDebug[x86_64]|C/C++:             ^
      -406546952_Task :app:buildCMakeDebug[x86_64]|C/C++: 1 warning and 1 error generated.
      -406546952_Task :app:dataBindingMergeDependencyArtifactsDebugAndroidTest|> Task :app:dataBindingMergeDependencyArtifactsDebugAndroidTest UP-TO-DATE
      -406546952_Task :app:generateDebugAndroidTestResValues|> Task :app:generateDebugAndroidTestResValues UP-TO-DATE
      -406546952_Task :app:generateDebugAndroidTestResources|> Task :app:generateDebugAndroidTestResources UP-TO-DATE
      -406546952_Task :app:mergeDebugAndroidTestResources|> Task :app:mergeDebugAndroidTestResources UP-TO-DATE
      -406546952_Task :app:dataBindingGenBaseClassesDebugAndroidTest|> Task :app:dataBindingGenBaseClassesDebugAndroidTest UP-TO-DATE
      -406546952_Task :app:processDebugAndroidTestManifest|> Task :app:processDebugAndroidTestManifest UP-TO-DATE
      -406546952_Task :app:generateDebugAndroidTestBuildConfig|> Task :app:generateDebugAndroidTestBuildConfig UP-TO-DATE
      -406546952_Task :app:checkDebugAndroidTestAarMetadata|> Task :app:checkDebugAndroidTestAarMetadata UP-TO-DATE
      -406546952_Task :app:mapDebugAndroidTestSourceSetPaths|> Task :app:mapDebugAndroidTestSourceSetPaths UP-TO-DATE
      -406546952_Task :app:processDebugAndroidTestResources|> Task :app:processDebugAndroidTestResources UP-TO-DATE
      -406546952_Task :app:compileDebugAndroidTestKotlin|> Task :app:compileDebugAndroidTestKotlin UP-TO-DATE
      -406546952_Task :app:javaPreCompileDebugAndroidTest|> Task :app:javaPreCompileDebugAndroidTest UP-TO-DATE
      -406546952_Task :app:mergeDebugAndroidTestShaders|> Task :app:mergeDebugAndroidTestShaders UP-TO-DATE
      -406546952_Task :app:generateDebugAndroidTestAssets|> Task :app:generateDebugAndroidTestAssets UP-TO-DATE
      -406546952_Task :app:compileDebugAndroidTestJavaWithJavac|> Task :app:compileDebugAndroidTestJavaWithJavac UP-TO-DATE
      -406546952_Task :app:compileDebugAndroidTestShaders|> Task :app:compileDebugAndroidTestShaders NO-SOURCE
      -406546952_Task :app:mergeDebugAndroidTestAssets|> Task :app:mergeDebugAndroidTestAssets UP-TO-DATE
      -406546952_Task :app:compressDebugAndroidTestAssets|> Task :app:compressDebugAndroidTestAssets UP-TO-DATE
      -406546952_Task :app:desugarDebugAndroidTestFileDependencies|> Task :app:desugarDebugAndroidTestFileDependencies UP-TO-DATE
      -406546952_Task :app:dexBuilderDebugAndroidTest|> Task :app:dexBuilderDebugAndroidTest UP-TO-DATE
      -406546952_Task :app:mergeDebugAndroidTestGlobalSynthetics|> Task :app:mergeDebugAndroidTestGlobalSynthetics UP-TO-DATE
      -406546952_Task :app:processDebugAndroidTestJavaRes|> Task :app:processDebugAndroidTestJavaRes NO-SOURCE
      -406546952_Task :app:mergeDebugAndroidTestJavaResource|> Task :app:mergeDebugAndroidTestJavaResource UP-TO-DATE
      -406546952_Task :app:mergeDebugAndroidTestJniLibFolders|> Task :app:mergeDebugAndroidTestJniLibFolders UP-TO-DATE
      -406546952_Task :app:mergeDebugAndroidTestNativeLibs|> Task :app:mergeDebugAndroidTestNativeLibs NO-SOURCE
      -406546952_Task :app:checkDebugAndroidTestDuplicateClasses|> Task :app:checkDebugAndroidTestDuplicateClasses UP-TO-DATE
      -406546952_Task :app:mergeExtDexDebugAndroidTest|> Task :app:mergeExtDexDebugAndroidTest UP-TO-DATE
      -406546952_Task :app:mergeLibDexDebugAndroidTest|> Task :app:mergeLibDexDebugAndroidTest UP-TO-DATE
      -406546952_Task :app:mergeProjectDexDebugAndroidTest|> Task :app:mergeProjectDexDebugAndroidTest UP-TO-DATE
      -406546952_Task :app:validateSigningDebugAndroidTest|> Task :app:validateSigningDebugAndroidTest UP-TO-DATE
      -406546952_Task :app:writeDebugAndroidTestSigningConfigVersions|> Task :app:writeDebugAndroidTestSigningConfigVersions UP-TO-DATE
      -406546952_Task :app:packageDebugAndroidTest|> Task :app:packageDebugAndroidTest UP-TO-DATE
      -406546952_Task :app:createDebugAndroidTestApkListingFileRedirect|> Task :app:createDebugAndroidTestApkListingFileRedirect UP-TO-DATE
      -406546952_Task :app:assembleDebugAndroidTest|> Task :app:assembleDebugAndroidTest UP-TO-DATE
      -406546952_Task :app:assembleDebugAndroidTest|FAILURE: Build completed with 4 failures.
      -406546952_Task :app:assembleDebugAndroidTest|1: Task failed with an exception.
      -406546952_Task :app:assembleDebugAndroidTest|-----------
      -406546952_Task :app:assembleDebugAndroidTest|* What went wrong:
      -406546952_Task :app:assembleDebugAndroidTest|Execution failed for task ':app:buildCMakeDebug[arm64-v8a]'.
      -406546952_Task :app:assembleDebugAndroidTest|> com.android.ide.common.process.ProcessException: ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/4m6p3s41/arm64-v8a'
      -406546952_Task :app:assembleDebugAndroidTest|  [1/2] Building CXX object CMakeFiles/app.dir/app.cpp.o
      -406546952_Task :app:assembleDebugAndroidTest|  FAILED: CMakeFiles/app.dir/app.cpp.o 
      -406546952_Task :app:assembleDebugAndroidTest|  /Users/jomof/projects/studio-main/prebuilts/studio/sdk/darwin/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++ --target=aarch64-none-linux-android21 --sysroot=/Users/jomof/projects/studio-main/prebuilts/studio/sdk/darwin/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/sysroot -Dapp_EXPORTS  -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security   -fno-limit-debug-info  -fPIC -MD -MT CMakeFiles/app.dir/app.cpp.o -MF CMakeFiles/app.dir/app.cpp.o.d -o CMakeFiles/app.dir/app.cpp.o -c /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp
      -406546952_Task :app:assembleDebugAndroidTest|  /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:4:2: warning: Simulated warning [-W#warnings]
      -406546952_Task :app:assembleDebugAndroidTest|  #warning Simulated warning
      -406546952_Task :app:assembleDebugAndroidTest|   ^
      -406546952_Task :app:assembleDebugAndroidTest|  /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:6:3: error: no matching function for call to 'bar'
      -406546952_Task :app:assembleDebugAndroidTest|    bar(b);
      -406546952_Task :app:assembleDebugAndroidTest|    ^~~
      -406546952_Task :app:assembleDebugAndroidTest|  /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:1:13: note: candidate function not viable: no known conversion from 'int' to 'const int *' for 1st argument; take the address of the argument with &
      -406546952_Task :app:assembleDebugAndroidTest|  extern void bar(const int*);
      -406546952_Task :app:assembleDebugAndroidTest|              ^
      -406546952_Task :app:assembleDebugAndroidTest|  1 warning and 1 error generated.
      -406546952_Task :app:assembleDebugAndroidTest|  ninja: build stopped: subcommand failed.
      -406546952_Task :app:assembleDebugAndroidTest|  C++ build system [build] failed while executing:
      -406546952_Task :app:assembleDebugAndroidTest|      /Users/jomof/projects/studio-main/prebuilts/studio/sdk/darwin/cmake/3.22.1/bin/ninja \
      -406546952_Task :app:assembleDebugAndroidTest|        -C \
      -406546952_Task :app:assembleDebugAndroidTest|        /Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/4m6p3s41/arm64-v8a \
      -406546952_Task :app:assembleDebugAndroidTest|        app
      -406546952_Task :app:assembleDebugAndroidTest|    from /Users/jomof/projects/repro/as-bad-error-context-repro/app
      -406546952_Task :app:assembleDebugAndroidTest|* Try:
      -406546952_Task :app:assembleDebugAndroidTest|> Run with --stacktrace option to get the stack trace.
      -406546952_Task :app:assembleDebugAndroidTest|> Run with --info or --debug option to get more log output.
      -406546952_Task :app:assembleDebugAndroidTest|> Run with --scan to get full insights.
      -406546952_Task :app:assembleDebugAndroidTest|==============================================================================
      -406546952_Task :app:assembleDebugAndroidTest|2: Task failed with an exception.
      -406546952_Task :app:assembleDebugAndroidTest|-----------
      -406546952_Task :app:assembleDebugAndroidTest|* What went wrong:
      -406546952_Task :app:assembleDebugAndroidTest|Execution failed for task ':app:buildCMakeDebug[armeabi-v7a]'.
      -406546952_Task :app:assembleDebugAndroidTest|> com.android.ide.common.process.ProcessException: ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/4m6p3s41/armeabi-v7a'
      -406546952_Task :app:assembleDebugAndroidTest|  [1/2] Building CXX object CMakeFiles/app.dir/app.cpp.o
      -406546952_Task :app:assembleDebugAndroidTest|  FAILED: CMakeFiles/app.dir/app.cpp.o 
      -406546952_Task :app:assembleDebugAndroidTest|  /Users/jomof/projects/studio-main/prebuilts/studio/sdk/darwin/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++ --target=armv7-none-linux-androideabi21 --sysroot=/Users/jomof/projects/studio-main/prebuilts/studio/sdk/darwin/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/sysroot -Dapp_EXPORTS  -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -march=armv7-a -mthumb -Wformat -Werror=format-security   -fno-limit-debug-info  -fPIC -MD -MT CMakeFiles/app.dir/app.cpp.o -MF CMakeFiles/app.dir/app.cpp.o.d -o CMakeFiles/app.dir/app.cpp.o -c /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp
      -406546952_Task :app:assembleDebugAndroidTest|  /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:4:2: warning: Simulated warning [-W#warnings]
      -406546952_Task :app:assembleDebugAndroidTest|  #warning Simulated warning
      -406546952_Task :app:assembleDebugAndroidTest|   ^
      -406546952_Task :app:assembleDebugAndroidTest|  /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:6:3: error: no matching function for call to 'bar'
      -406546952_Task :app:assembleDebugAndroidTest|    bar(b);
      -406546952_Task :app:assembleDebugAndroidTest|    ^~~
      -406546952_Task :app:assembleDebugAndroidTest|  /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:1:13: note: candidate function not viable: no known conversion from 'int' to 'const int *' for 1st argument; take the address of the argument with &
      -406546952_Task :app:assembleDebugAndroidTest|  extern void bar(const int*);
      -406546952_Task :app:assembleDebugAndroidTest|              ^
      -406546952_Task :app:assembleDebugAndroidTest|  1 warning and 1 error generated.
      -406546952_Task :app:assembleDebugAndroidTest|  ninja: build stopped: subcommand failed.
      -406546952_Task :app:assembleDebugAndroidTest|  C++ build system [build] failed while executing:
      -406546952_Task :app:assembleDebugAndroidTest|      /Users/jomof/projects/studio-main/prebuilts/studio/sdk/darwin/cmake/3.22.1/bin/ninja \
      -406546952_Task :app:assembleDebugAndroidTest|        -C \
      -406546952_Task :app:assembleDebugAndroidTest|        /Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/4m6p3s41/armeabi-v7a \
      -406546952_Task :app:assembleDebugAndroidTest|        app
      -406546952_Task :app:assembleDebugAndroidTest|    from /Users/jomof/projects/repro/as-bad-error-context-repro/app
      -406546952_Task :app:assembleDebugAndroidTest|* Try:
      -406546952_Task :app:assembleDebugAndroidTest|> Run with --stacktrace option to get the stack trace.
      -406546952_Task :app:assembleDebugAndroidTest|> Run with --info or --debug option to get more log output.
      -406546952_Task :app:assembleDebugAndroidTest|> Run with --scan to get full insights.
      -406546952_Task :app:assembleDebugAndroidTest|==============================================================================
      -406546952_Task :app:assembleDebugAndroidTest|3: Task failed with an exception.
      -406546952_Task :app:assembleDebugAndroidTest|-----------
      -406546952_Task :app:assembleDebugAndroidTest|* What went wrong:
      -406546952_Task :app:assembleDebugAndroidTest|Execution failed for task ':app:buildCMakeDebug[x86]'.
      -406546952_Task :app:assembleDebugAndroidTest|> com.android.ide.common.process.ProcessException: ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/4m6p3s41/x86'
      -406546952_Task :app:assembleDebugAndroidTest|  [1/2] Building CXX object CMakeFiles/app.dir/app.cpp.o
      -406546952_Task :app:assembleDebugAndroidTest|  FAILED: CMakeFiles/app.dir/app.cpp.o 
      -406546952_Task :app:assembleDebugAndroidTest|  /Users/jomof/projects/studio-main/prebuilts/studio/sdk/darwin/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++ --target=i686-none-linux-android21 --sysroot=/Users/jomof/projects/studio-main/prebuilts/studio/sdk/darwin/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/sysroot -Dapp_EXPORTS  -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -mstackrealign -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security   -fno-limit-debug-info  -fPIC -MD -MT CMakeFiles/app.dir/app.cpp.o -MF CMakeFiles/app.dir/app.cpp.o.d -o CMakeFiles/app.dir/app.cpp.o -c /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp
      -406546952_Task :app:assembleDebugAndroidTest|  /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:4:2: warning: Simulated warning [-W#warnings]
      -406546952_Task :app:assembleDebugAndroidTest|  #warning Simulated warning
      -406546952_Task :app:assembleDebugAndroidTest|   ^
      -406546952_Task :app:assembleDebugAndroidTest|  /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:6:3: error: no matching function for call to 'bar'
      -406546952_Task :app:assembleDebugAndroidTest|    bar(b);
      -406546952_Task :app:assembleDebugAndroidTest|    ^~~
      -406546952_Task :app:assembleDebugAndroidTest|  /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:1:13: note: candidate function not viable: no known conversion from 'int' to 'const int *' for 1st argument; take the address of the argument with &
      -406546952_Task :app:assembleDebugAndroidTest|  extern void bar(const int*);
      -406546952_Task :app:assembleDebugAndroidTest|              ^
      -406546952_Task :app:assembleDebugAndroidTest|  1 warning and 1 error generated.
      -406546952_Task :app:assembleDebugAndroidTest|  ninja: build stopped: subcommand failed.
      -406546952_Task :app:assembleDebugAndroidTest|  C++ build system [build] failed while executing:
      -406546952_Task :app:assembleDebugAndroidTest|      /Users/jomof/projects/studio-main/prebuilts/studio/sdk/darwin/cmake/3.22.1/bin/ninja \
      -406546952_Task :app:assembleDebugAndroidTest|        -C \
      -406546952_Task :app:assembleDebugAndroidTest|        /Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/4m6p3s41/x86 \
      -406546952_Task :app:assembleDebugAndroidTest|        app
      -406546952_Task :app:assembleDebugAndroidTest|    from /Users/jomof/projects/repro/as-bad-error-context-repro/app
      -406546952_Task :app:assembleDebugAndroidTest|* Try:
      -406546952_Task :app:assembleDebugAndroidTest|> Run with --stacktrace option to get the stack trace.
      -406546952_Task :app:assembleDebugAndroidTest|> Run with --info or --debug option to get more log output.
      -406546952_Task :app:assembleDebugAndroidTest|> Run with --scan to get full insights.
      -406546952_Task :app:assembleDebugAndroidTest|==============================================================================
      -406546952_Task :app:assembleDebugAndroidTest|4: Task failed with an exception.
      -406546952_Task :app:assembleDebugAndroidTest|-----------
      -406546952_Task :app:assembleDebugAndroidTest|* What went wrong:
      -406546952_Task :app:assembleDebugAndroidTest|Execution failed for task ':app:buildCMakeDebug[x86_64]'.
      -406546952_Task :app:assembleDebugAndroidTest|> com.android.ide.common.process.ProcessException: ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/4m6p3s41/x86_64'
      -406546952_Task :app:assembleDebugAndroidTest|  [1/2] Building CXX object CMakeFiles/app.dir/app.cpp.o
      -406546952_Task :app:assembleDebugAndroidTest|  FAILED: CMakeFiles/app.dir/app.cpp.o 
      -406546952_Task :app:assembleDebugAndroidTest|  /Users/jomof/projects/studio-main/prebuilts/studio/sdk/darwin/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++ --target=x86_64-none-linux-android21 --sysroot=/Users/jomof/projects/studio-main/prebuilts/studio/sdk/darwin/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/sysroot -Dapp_EXPORTS  -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security   -fno-limit-debug-info  -fPIC -MD -MT CMakeFiles/app.dir/app.cpp.o -MF CMakeFiles/app.dir/app.cpp.o.d -o CMakeFiles/app.dir/app.cpp.o -c /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp
      -406546952_Task :app:assembleDebugAndroidTest|  /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:4:2: warning: Simulated warning [-W#warnings]
      -406546952_Task :app:assembleDebugAndroidTest|  #warning Simulated warning
      -406546952_Task :app:assembleDebugAndroidTest|   ^
      -406546952_Task :app:assembleDebugAndroidTest|  /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:6:3: error: no matching function for call to 'bar'
      -406546952_Task :app:assembleDebugAndroidTest|    bar(b);
      -406546952_Task :app:assembleDebugAndroidTest|    ^~~
      -406546952_Task :app:assembleDebugAndroidTest|  /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:1:13: note: candidate function not viable: no known conversion from 'int' to 'const int *' for 1st argument; take the address of the argument with &
      -406546952_Task :app:assembleDebugAndroidTest|  extern void bar(const int*);
      -406546952_Task :app:assembleDebugAndroidTest|              ^
      -406546952_Task :app:assembleDebugAndroidTest|  1 warning and 1 error generated.
      -406546952_Task :app:assembleDebugAndroidTest|  ninja: build stopped: subcommand failed.
      -406546952_Task :app:assembleDebugAndroidTest|  C++ build system [build] failed while executing:
      -406546952_Task :app:assembleDebugAndroidTest|      /Users/jomof/projects/studio-main/prebuilts/studio/sdk/darwin/cmake/3.22.1/bin/ninja \
      -406546952_Task :app:assembleDebugAndroidTest|        -C \
      -406546952_Task :app:assembleDebugAndroidTest|        /Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/4m6p3s41/x86_64 \
      -406546952_Task :app:assembleDebugAndroidTest|        app
      -406546952_Task :app:assembleDebugAndroidTest|    from /Users/jomof/projects/repro/as-bad-error-context-repro/app
      -406546952_Task :app:assembleDebugAndroidTest|* Try:
      -406546952_Task :app:assembleDebugAndroidTest|> Run with --stacktrace option to get the stack trace.
      -406546952_Task :app:assembleDebugAndroidTest|> Run with --info or --debug option to get more log output.
      -406546952_Task :app:assembleDebugAndroidTest|> Run with --scan to get full insights.
      -406546952_Task :app:assembleDebugAndroidTest|==============================================================================
      -406546952_Task :app:assembleDebugAndroidTest|* Get more help at https://help.gradle.org
      -406546952_Task :app:assembleDebugAndroidTest|BUILD FAILED in 13s
      -406546952_Task :app:assembleDebugAndroidTest|72 actionable tasks: 9 executed, 63 up-to-date
      """.trimIndent()
    ) {
      assertDiagnosticMessages(
        "[:app Debug arm64-v8a]" to """
              /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:4:2: warning: Simulated warning [-W#warnings]
              #warning Simulated warning
               ^
        """.trimIndent(),
        "[:app Debug arm64-v8a]" to """
              /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:6:3: error: no matching function for call to 'bar'
                bar(b);
                ^~~
              /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:1:13: note: candidate function not viable: no known conversion from 'int' to 'const int *' for 1st argument; take the address of the argument with &
              extern void bar(const int*);
                          ^
        """.trimIndent(),
        "[:app Debug armeabi-v7a]" to """
              /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:4:2: warning: Simulated warning [-W#warnings]
              #warning Simulated warning
               ^
        """.trimIndent(),
        "[:app Debug armeabi-v7a]" to """
              /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:6:3: error: no matching function for call to 'bar'
                bar(b);
                ^~~
              /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:1:13: note: candidate function not viable: no known conversion from 'int' to 'const int *' for 1st argument; take the address of the argument with &
              extern void bar(const int*);
                          ^
        """.trimIndent(),
        "[:app Debug x86]" to """
              /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:4:2: warning: Simulated warning [-W#warnings]
              #warning Simulated warning
               ^
        """.trimIndent(),
        "[:app Debug x86]" to """
              /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:6:3: error: no matching function for call to 'bar'
                bar(b);
                ^~~
              /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:1:13: note: candidate function not viable: no known conversion from 'int' to 'const int *' for 1st argument; take the address of the argument with &
              extern void bar(const int*);
                          ^
        """.trimIndent(),
        "[:app Debug x86_64]" to """
              /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:4:2: warning: Simulated warning [-W#warnings]
              #warning Simulated warning
               ^
        """.trimIndent(),
        "[:app Debug x86_64]" to """
              /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:6:3: error: no matching function for call to 'bar'
                bar(b);
                ^~~
              /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:1:13: note: candidate function not viable: no known conversion from 'int' to 'const int *' for 1st argument; take the address of the argument with &
              extern void bar(const int*);
                          ^
        """.trimIndent(),
      )
    }
  }

  @Test
  fun `check interleaved event order with clang command-lines`() {
    checkInterleavedParse(
      """
      -1776703563_Task :prepareKotlinBuildScriptModel|> Task :prepareKotlinBuildScriptModel UP-TO-DATE
      RESOLVE_PROJECT:1|BUILD SUCCESSFUL in 18s
      EXECUTE_TASK:2|Executing tasks: [:app:assembleDebug] in project /Users/jomof/projects/repro/as-bad-error-context-repro
      -1998758610_Task :app:preBuild|> Task :app:preBuild UP-TO-DATE
      -1998758610_Task :app:createDebugVariantModel|> Task :app:createDebugVariantModel
      -1998758610_Task :app:preDebugBuild|> Task :app:preDebugBuild UP-TO-DATE
      -1998758610_Task :app:mergeDebugNativeDebugMetadata|> Task :app:mergeDebugNativeDebugMetadata NO-SOURCE
      -1998758610_Task :app:generateDebugResValues|> Task :app:generateDebugResValues
      -1998758610_Task :app:generateDebugResources|> Task :app:generateDebugResources
      -1998758610_Task :app:dataBindingMergeDependencyArtifactsDebug|> Task :app:dataBindingMergeDependencyArtifactsDebug
      -1998758610_Task :app:packageDebugResources|> Task :app:packageDebugResources
      -1998758610_Task :app:mapDebugSourceSetPaths|> Task :app:mapDebugSourceSetPaths
      -1998758610_Task :app:createDebugCompatibleScreenManifests|> Task :app:createDebugCompatibleScreenManifests
      -1998758610_Task :app:extractDeepLinksDebug|> Task :app:extractDeepLinksDebug
      -1998758610_Task :app:parseDebugLocalResources|> Task :app:parseDebugLocalResources
      -1998758610_Task :app:checkDebugAarMetadata|> Task :app:checkDebugAarMetadata
      -1998758610_Task :app:processDebugMainManifest|> Task :app:processDebugMainManifest
      -1998758610_Task :app:mergeDebugResources|> Task :app:mergeDebugResources
      -1998758610_Task :app:processDebugManifest|> Task :app:processDebugManifest
      -1998758610_Task :app:dataBindingGenBaseClassesDebug|> Task :app:dataBindingGenBaseClassesDebug
      -1998758610_Task :app:javaPreCompileDebug|> Task :app:javaPreCompileDebug
      -1998758610_Task :app:mergeDebugShaders|> Task :app:mergeDebugShaders
      -1998758610_Task :app:compileDebugShaders|> Task :app:compileDebugShaders NO-SOURCE
      -1998758610_Task :app:generateDebugAssets|> Task :app:generateDebugAssets UP-TO-DATE
      -1998758610_Task :app:mergeDebugAssets|> Task :app:mergeDebugAssets
      -1998758610_Task :app:compressDebugAssets|> Task :app:compressDebugAssets
      -1998758610_Task :app:desugarDebugFileDependencies|> Task :app:desugarDebugFileDependencies
      -1998758610_Task :app:processDebugJavaRes|> Task :app:processDebugJavaRes NO-SOURCE
      -1998758610_Task :app:checkDebugDuplicateClasses|> Task :app:checkDebugDuplicateClasses
      -1998758610_Task :app:configureCMakeDebug[arm64-v8a]|> Task :app:configureCMakeDebug[arm64-v8a]
      -1998758610_Task :app:mergeLibDexDebug|> Task :app:mergeLibDexDebug
      -1998758610_Task :app:buildCMakeDebug[arm64-v8a]|> Task :app:buildCMakeDebug[arm64-v8a] FAILED
      -1998758610_Task :app:buildCMakeDebug[arm64-v8a]|C/C++: ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/1r1y724v/arm64-v8a'
      -1998758610_Task :app:buildCMakeDebug[arm64-v8a]|C/C++: /Users/jomof/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++ --target=aarch64-none-linux-android21 --sysroot=/Users/jomof/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/sysroot -Dapp_EXPORTS  -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security   -fno-limit-debug-info  -fPIC -MD -MT CMakeFiles/app.dir/app.cpp.o -MF CMakeFiles/app.dir/app.cpp.o.d -o CMakeFiles/app.dir/app.cpp.o -c /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp
      -1998758610_Task :app:buildCMakeDebug[arm64-v8a]|C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:7:3: error: no matching function for call to 'bar'
      -1998758610_Task :app:buildCMakeDebug[arm64-v8a]|C/C++:   bar(b);
      -1998758610_Task :app:buildCMakeDebug[arm64-v8a]|C/C++:   ^~~
      -1998758610_Task :app:buildCMakeDebug[arm64-v8a]|C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:2:13: note: candidate function not viable: no known conversion from 'int' to 'const int *' for 1st argument; take the address of the argument with &
      -1998758610_Task :app:buildCMakeDebug[arm64-v8a]|C/C++: extern void bar(const int*);
      -1998758610_Task :app:buildCMakeDebug[arm64-v8a]|C/C++:             ^
      -1998758610_Task :app:buildCMakeDebug[arm64-v8a]|C/C++: 1 error generated.
      -1998758610_Task :app:configureCMakeDebug[armeabi-v7a]|> Task :app:configureCMakeDebug[armeabi-v7a]
      -1998758610_Task :app:buildCMakeDebug[armeabi-v7a]|> Task :app:buildCMakeDebug[armeabi-v7a] FAILED
      -1998758610_Task :app:buildCMakeDebug[armeabi-v7a]|C/C++: ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/1r1y724v/armeabi-v7a'
      -1998758610_Task :app:buildCMakeDebug[armeabi-v7a]|C/C++: /Users/jomof/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++ --target=armv7-none-linux-androideabi21 --sysroot=/Users/jomof/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/sysroot -Dapp_EXPORTS  -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -march=armv7-a -mthumb -Wformat -Werror=format-security   -fno-limit-debug-info  -fPIC -MD -MT CMakeFiles/app.dir/app.cpp.o -MF CMakeFiles/app.dir/app.cpp.o.d -o CMakeFiles/app.dir/app.cpp.o -c /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp
      -1998758610_Task :app:buildCMakeDebug[armeabi-v7a]|C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:7:3: error: no matching function for call to 'bar'
      -1998758610_Task :app:configureCMakeDebug[x86]|> Task :app:configureCMakeDebug[x86]
      -1998758610_Task :app:buildCMakeDebug[armeabi-v7a]|C/C++:   bar(b);
      -1998758610_Task :app:buildCMakeDebug[armeabi-v7a]|C/C++:   ^~~
      -1998758610_Task :app:buildCMakeDebug[armeabi-v7a]|C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:2:13: note: candidate function not viable: no known conversion from 'int' to 'const int *' for 1st argument; take the address of the argument with &
      -1998758610_Task :app:buildCMakeDebug[armeabi-v7a]|C/C++: extern void bar(const int*);
      -1998758610_Task :app:buildCMakeDebug[armeabi-v7a]|C/C++:             ^
      -1998758610_Task :app:buildCMakeDebug[armeabi-v7a]|C/C++: 1 error generated.
      -1998758610_Task :app:buildCMakeDebug[x86]|> Task :app:buildCMakeDebug[x86] FAILED
      -1998758610_Task :app:buildCMakeDebug[x86]|C/C++: ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/1r1y724v/x86'
      -1998758610_Task :app:buildCMakeDebug[x86]|C/C++: /Users/jomof/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++ --target=i686-none-linux-android21 --sysroot=/Users/jomof/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/sysroot -Dapp_EXPORTS  -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -mstackrealign -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security   -fno-limit-debug-info  -fPIC -MD -MT CMakeFiles/app.dir/app.cpp.o -MF CMakeFiles/app.dir/app.cpp.o.d -o CMakeFiles/app.dir/app.cpp.o -c /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp
      -1998758610_Task :app:buildCMakeDebug[x86]|C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:7:3: error: no matching function for call to 'bar'
      -1998758610_Task :app:buildCMakeDebug[x86]|C/C++:   bar(b);
      -1998758610_Task :app:buildCMakeDebug[x86]|C/C++:   ^~~
      -1998758610_Task :app:buildCMakeDebug[x86]|C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:2:13: note: candidate function not viable: no known conversion from 'int' to 'const int *' for 1st argument; take the address of the argument with &
      -1998758610_Task :app:buildCMakeDebug[x86]|C/C++: extern void bar(const int*);
      -1998758610_Task :app:buildCMakeDebug[x86]|C/C++:             ^
      -1998758610_Task :app:buildCMakeDebug[x86]|C/C++: 1 error generated.
      -1998758610_Task :app:mergeExtDexDebug|> Task :app:mergeExtDexDebug
      -1998758610_Task :app:configureCMakeDebug[x86_64]|> Task :app:configureCMakeDebug[x86_64]
      -1998758610_Task :app:buildCMakeDebug[x86_64]|> Task :app:buildCMakeDebug[x86_64] FAILED
      -1998758610_Task :app:buildCMakeDebug[x86_64]|C/C++: ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/1r1y724v/x86_64'
      -1998758610_Task :app:buildCMakeDebug[x86_64]|C/C++: /Users/jomof/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++ --target=x86_64-none-linux-android21 --sysroot=/Users/jomof/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/sysroot -Dapp_EXPORTS  -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security   -fno-limit-debug-info  -fPIC -MD -MT CMakeFiles/app.dir/app.cpp.o -MF CMakeFiles/app.dir/app.cpp.o.d -o CMakeFiles/app.dir/app.cpp.o -c /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp
      -1998758610_Task :app:mergeDebugJniLibFolders|> Task :app:mergeDebugJniLibFolders
      -1998758610_Task :app:validateSigningDebug|> Task :app:validateSigningDebug
      -1998758610_Task :app:writeDebugSigningConfigVersions|> Task :app:writeDebugSigningConfigVersions
      -1998758610_Task :app:buildCMakeDebug[x86_64]|C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:7:3: error: no matching function for call to 'bar'
      -1998758610_Task :app:writeDebugAppMetadata|> Task :app:writeDebugAppMetadata
      -1998758610_Task :app:processDebugManifestForPackage|> Task :app:processDebugManifestForPackage
      -1998758610_Task :app:buildCMakeDebug[x86_64]|C/C++:   bar(b);
      -1998758610_Task :app:buildCMakeDebug[x86_64]|C/C++:   ^~~
      -1998758610_Task :app:buildCMakeDebug[x86_64]|C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:2:13: note: candidate function not viable: no known conversion from 'int' to 'const int *' for 1st argument; take the address of the argument with &
      -1998758610_Task :app:buildCMakeDebug[x86_64]|C/C++: extern void bar(const int*);
      -1998758610_Task :app:buildCMakeDebug[x86_64]|C/C++:             ^
      -1998758610_Task :app:buildCMakeDebug[x86_64]|C/C++: 1 error generated.
      -1998758610_Task :app:processDebugResources|> Task :app:processDebugResources
      -1998758610_Task :app:compileDebugKotlin|> Task :app:compileDebugKotlin
      -1998758610_Task :app:compileDebugJavaWithJavac|> Task :app:compileDebugJavaWithJavac
      -1998758610_Task :app:dexBuilderDebug|> Task :app:dexBuilderDebug
      -1998758610_Task :app:mergeDebugGlobalSynthetics|> Task :app:mergeDebugGlobalSynthetics
      -1998758610_Task :app:mergeProjectDexDebug|> Task :app:mergeProjectDexDebug
      -1998758610_Task :app:mergeDebugJavaResource|> Task :app:mergeDebugJavaResource
      -1998758610_Task :app:mergeDebugJavaResource|FAILURE: Build completed with 4 failures.
      -1998758610_Task :app:mergeDebugJavaResource|1: Task failed with an exception.
      -1998758610_Task :app:mergeDebugJavaResource|-----------
      -1998758610_Task :app:mergeDebugJavaResource|* What went wrong:
      -1998758610_Task :app:mergeDebugJavaResource|Execution failed for task ':app:buildCMakeDebug[arm64-v8a]'.
      -1998758610_Task :app:mergeDebugJavaResource|> com.android.ide.common.process.ProcessException: ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/1r1y724v/arm64-v8a'
      -1998758610_Task :app:mergeDebugJavaResource|  [1/2] Building CXX object CMakeFiles/app.dir/app.cpp.o
      -1998758610_Task :app:mergeDebugJavaResource|  FAILED: CMakeFiles/app.dir/app.cpp.o 
      -1998758610_Task :app:mergeDebugJavaResource|  /Users/jomof/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++ --target=aarch64-none-linux-android21 --sysroot=/Users/jomof/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/sysroot -Dapp_EXPORTS  -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security   -fno-limit-debug-info  -fPIC -MD -MT CMakeFiles/app.dir/app.cpp.o -MF CMakeFiles/app.dir/app.cpp.o.d -o CMakeFiles/app.dir/app.cpp.o -c /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp
      -1998758610_Task :app:mergeDebugJavaResource|  /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:7:3: error: no matching function for call to 'bar'
      -1998758610_Task :app:mergeDebugJavaResource|    bar(b);
      -1998758610_Task :app:mergeDebugJavaResource|    ^~~
      -1998758610_Task :app:mergeDebugJavaResource|  /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:2:13: note: candidate function not viable: no known conversion from 'int' to 'const int *' for 1st argument; take the address of the argument with &
      -1998758610_Task :app:mergeDebugJavaResource|  extern void bar(const int*);
      -1998758610_Task :app:mergeDebugJavaResource|              ^
      -1998758610_Task :app:mergeDebugJavaResource|  1 error generated.
      -1998758610_Task :app:mergeDebugJavaResource|  ninja: build stopped: subcommand failed.
      -1998758610_Task :app:mergeDebugJavaResource|  C++ build system [build] failed while executing:
      -1998758610_Task :app:mergeDebugJavaResource|      /Users/jomof/Library/Android/sdk/cmake/3.22.1/bin/ninja \
      -1998758610_Task :app:mergeDebugJavaResource|        -C \
      -1998758610_Task :app:mergeDebugJavaResource|        /Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/1r1y724v/arm64-v8a \
      -1998758610_Task :app:mergeDebugJavaResource|        app
      -1998758610_Task :app:mergeDebugJavaResource|    from /Users/jomof/projects/repro/as-bad-error-context-repro/app
      -1998758610_Task :app:mergeDebugJavaResource|* Try:
      -1998758610_Task :app:mergeDebugJavaResource|> Run with --stacktrace option to get the stack trace.
      -1998758610_Task :app:mergeDebugJavaResource|> Run with --info or --debug option to get more log output.
      -1998758610_Task :app:mergeDebugJavaResource|> Run with --scan to get full insights.
      -1998758610_Task :app:mergeDebugJavaResource|==============================================================================
      -1998758610_Task :app:mergeDebugJavaResource|2: Task failed with an exception.
      -1998758610_Task :app:mergeDebugJavaResource|-----------
      -1998758610_Task :app:mergeDebugJavaResource|* What went wrong:
      -1998758610_Task :app:mergeDebugJavaResource|Execution failed for task ':app:buildCMakeDebug[armeabi-v7a]'.
      -1998758610_Task :app:mergeDebugJavaResource|> com.android.ide.common.process.ProcessException: ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/1r1y724v/armeabi-v7a'
      -1998758610_Task :app:mergeDebugJavaResource|  [1/3] Building CXX object CMakeFiles/app.dir/Capital.cpp.o
      -1998758610_Task :app:mergeDebugJavaResource|  [2/3] Building CXX object CMakeFiles/app.dir/app.cpp.o
      -1998758610_Task :app:mergeDebugJavaResource|  FAILED: CMakeFiles/app.dir/app.cpp.o 
      -1998758610_Task :app:mergeDebugJavaResource|  /Users/jomof/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++ --target=armv7-none-linux-androideabi21 --sysroot=/Users/jomof/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/sysroot -Dapp_EXPORTS  -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -march=armv7-a -mthumb -Wformat -Werror=format-security   -fno-limit-debug-info  -fPIC -MD -MT CMakeFiles/app.dir/app.cpp.o -MF CMakeFiles/app.dir/app.cpp.o.d -o CMakeFiles/app.dir/app.cpp.o -c /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp
      -1998758610_Task :app:mergeDebugJavaResource|  /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:7:3: error: no matching function for call to 'bar'
      -1998758610_Task :app:mergeDebugJavaResource|    bar(b);
      -1998758610_Task :app:mergeDebugJavaResource|    ^~~
      -1998758610_Task :app:mergeDebugJavaResource|  /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:2:13: note: candidate function not viable: no known conversion from 'int' to 'const int *' for 1st argument; take the address of the argument with &
      -1998758610_Task :app:mergeDebugJavaResource|  extern void bar(const int*);
      -1998758610_Task :app:mergeDebugJavaResource|              ^
      -1998758610_Task :app:mergeDebugJavaResource|  1 error generated.
      -1998758610_Task :app:mergeDebugJavaResource|  ninja: build stopped: subcommand failed.
      -1998758610_Task :app:mergeDebugJavaResource|  C++ build system [build] failed while executing:
      -1998758610_Task :app:mergeDebugJavaResource|      /Users/jomof/Library/Android/sdk/cmake/3.22.1/bin/ninja \
      -1998758610_Task :app:mergeDebugJavaResource|        -C \
      -1998758610_Task :app:mergeDebugJavaResource|        /Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/1r1y724v/armeabi-v7a \
      -1998758610_Task :app:mergeDebugJavaResource|        app
      -1998758610_Task :app:mergeDebugJavaResource|    from /Users/jomof/projects/repro/as-bad-error-context-repro/app
      -1998758610_Task :app:mergeDebugJavaResource|* Try:
      -1998758610_Task :app:mergeDebugJavaResource|> Run with --stacktrace option to get the stack trace.
      -1998758610_Task :app:mergeDebugJavaResource|> Run with --info or --debug option to get more log output.
      -1998758610_Task :app:mergeDebugJavaResource|> Run with --scan to get full insights.
      -1998758610_Task :app:mergeDebugJavaResource|==============================================================================
      -1998758610_Task :app:mergeDebugJavaResource|3: Task failed with an exception.
      -1998758610_Task :app:mergeDebugJavaResource|-----------
      -1998758610_Task :app:mergeDebugJavaResource|* What went wrong:
      -1998758610_Task :app:mergeDebugJavaResource|Execution failed for task ':app:buildCMakeDebug[x86]'.
      -1998758610_Task :app:mergeDebugJavaResource|> com.android.ide.common.process.ProcessException: ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/1r1y724v/x86'
      -1998758610_Task :app:mergeDebugJavaResource|  [1/3] Building CXX object CMakeFiles/app.dir/Capital.cpp.o
      -1998758610_Task :app:mergeDebugJavaResource|  [2/3] Building CXX object CMakeFiles/app.dir/app.cpp.o
      -1998758610_Task :app:mergeDebugJavaResource|  FAILED: CMakeFiles/app.dir/app.cpp.o 
      -1998758610_Task :app:mergeDebugJavaResource|  /Users/jomof/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++ --target=i686-none-linux-android21 --sysroot=/Users/jomof/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/sysroot -Dapp_EXPORTS  -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -mstackrealign -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security   -fno-limit-debug-info  -fPIC -MD -MT CMakeFiles/app.dir/app.cpp.o -MF CMakeFiles/app.dir/app.cpp.o.d -o CMakeFiles/app.dir/app.cpp.o -c /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp
      -1998758610_Task :app:mergeDebugJavaResource|  /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:7:3: error: no matching function for call to 'bar'
      -1998758610_Task :app:mergeDebugJavaResource|    bar(b);
      -1998758610_Task :app:mergeDebugJavaResource|    ^~~
      -1998758610_Task :app:mergeDebugJavaResource|  /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:2:13: note: candidate function not viable: no known conversion from 'int' to 'const int *' for 1st argument; take the address of the argument with &
      -1998758610_Task :app:mergeDebugJavaResource|  extern void bar(const int*);
      -1998758610_Task :app:mergeDebugJavaResource|              ^
      -1998758610_Task :app:mergeDebugJavaResource|  1 error generated.
      -1998758610_Task :app:mergeDebugJavaResource|  ninja: build stopped: subcommand failed.
      -1998758610_Task :app:mergeDebugJavaResource|  C++ build system [build] failed while executing:
      -1998758610_Task :app:mergeDebugJavaResource|      /Users/jomof/Library/Android/sdk/cmake/3.22.1/bin/ninja \
      -1998758610_Task :app:mergeDebugJavaResource|        -C \
      -1998758610_Task :app:mergeDebugJavaResource|        /Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/1r1y724v/x86 \
      -1998758610_Task :app:mergeDebugJavaResource|        app
      -1998758610_Task :app:mergeDebugJavaResource|    from /Users/jomof/projects/repro/as-bad-error-context-repro/app
      -1998758610_Task :app:mergeDebugJavaResource|* Try:
      -1998758610_Task :app:mergeDebugJavaResource|> Run with --stacktrace option to get the stack trace.
      -1998758610_Task :app:mergeDebugJavaResource|> Run with --info or --debug option to get more log output.
      -1998758610_Task :app:mergeDebugJavaResource|> Run with --scan to get full insights.
      -1998758610_Task :app:mergeDebugJavaResource|==============================================================================
      -1998758610_Task :app:mergeDebugJavaResource|4: Task failed with an exception.
      -1998758610_Task :app:mergeDebugJavaResource|-----------
      -1998758610_Task :app:mergeDebugJavaResource|* What went wrong:
      -1998758610_Task :app:mergeDebugJavaResource|Execution failed for task ':app:buildCMakeDebug[x86_64]'.
      -1998758610_Task :app:mergeDebugJavaResource|> com.android.ide.common.process.ProcessException: ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/1r1y724v/x86_64'
      -1998758610_Task :app:mergeDebugJavaResource|  [1/3] Building CXX object CMakeFiles/app.dir/app.cpp.o
      -1998758610_Task :app:mergeDebugJavaResource|  FAILED: CMakeFiles/app.dir/app.cpp.o 
      -1998758610_Task :app:mergeDebugJavaResource|  /Users/jomof/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++ --target=x86_64-none-linux-android21 --sysroot=/Users/jomof/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/sysroot -Dapp_EXPORTS  -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security   -fno-limit-debug-info  -fPIC -MD -MT CMakeFiles/app.dir/app.cpp.o -MF CMakeFiles/app.dir/app.cpp.o.d -o CMakeFiles/app.dir/app.cpp.o -c /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp
      -1998758610_Task :app:mergeDebugJavaResource|  /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:7:3: error: no matching function for call to 'bar'
      -1998758610_Task :app:mergeDebugJavaResource|    bar(b);
      -1998758610_Task :app:mergeDebugJavaResource|    ^~~
      -1998758610_Task :app:mergeDebugJavaResource|  /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:2:13: note: candidate function not viable: no known conversion from 'int' to 'const int *' for 1st argument; take the address of the argument with &
      -1998758610_Task :app:mergeDebugJavaResource|  extern void bar(const int*);
      -1998758610_Task :app:mergeDebugJavaResource|              ^
      -1998758610_Task :app:mergeDebugJavaResource|  1 error generated.
      -1998758610_Task :app:mergeDebugJavaResource|  [2/3] Building CXX object CMakeFiles/app.dir/Capital.cpp.o
      -1998758610_Task :app:mergeDebugJavaResource|  ninja: build stopped: subcommand failed.
      -1998758610_Task :app:mergeDebugJavaResource|  C++ build system [build] failed while executing:
      -1998758610_Task :app:mergeDebugJavaResource|      /Users/jomof/Library/Android/sdk/cmake/3.22.1/bin/ninja \
      -1998758610_Task :app:mergeDebugJavaResource|        -C \
      -1998758610_Task :app:mergeDebugJavaResource|        /Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/1r1y724v/x86_64 \
      -1998758610_Task :app:mergeDebugJavaResource|        app
      -1998758610_Task :app:mergeDebugJavaResource|    from /Users/jomof/projects/repro/as-bad-error-context-repro/app
      -1998758610_Task :app:mergeDebugJavaResource|* Try:
      -1998758610_Task :app:mergeDebugJavaResource|> Run with --stacktrace option to get the stack trace.
      -1998758610_Task :app:mergeDebugJavaResource|> Run with --info or --debug option to get more log output.
      -1998758610_Task :app:mergeDebugJavaResource|> Run with --scan to get full insights.
      -1998758610_Task :app:mergeDebugJavaResource|==============================================================================
      -1998758610_Task :app:mergeDebugJavaResource|* Get more help at https://help.gradle.org
      -1998758610_Task :app:mergeDebugJavaResource|BUILD FAILED in 31s
      -1998758610_Task :app:mergeDebugJavaResource|41 actionable tasks: 41 executed   
      """.trimIndent()
    ) {
      assertDiagnosticMessages(
        "[:app Debug arm64-v8a]" to """
          /Users/jomof/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++ --target=aarch64-none-linux-android21 --sysroot=/Users/jomof/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/sysroot -Dapp_EXPORTS  -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security   -fno-limit-debug-info  -fPIC -MD -MT CMakeFiles/app.dir/app.cpp.o -MF CMakeFiles/app.dir/app.cpp.o.d -o CMakeFiles/app.dir/app.cpp.o -c /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp
          
          /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:7:3: error: no matching function for call to 'bar'
            bar(b);
            ^~~
          /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:2:13: note: candidate function not viable: no known conversion from 'int' to 'const int *' for 1st argument; take the address of the argument with &
          extern void bar(const int*);
                      ^
        """.trimIndent(),
        "[:app Debug armeabi-v7a]" to "",
        "[:app Debug x86]" to "",
        "[:app Debug x86_64]" to "",
      )
    }
  }

  @Test
  fun `check interleaved event order with clang command-lines 2`() {
    checkInterleavedParse(
      """
      -1837090683_Task :app:configureCMakeDebug[arm64-v8a]|> Task :app:configureCMakeDebug[arm64-v8a]
      -1837090683_Task :app:configureCMakeDebug[armeabi-v7a]|> Task :app:configureCMakeDebug[armeabi-v7a]
      -1837090683_Task :app:configureCMakeDebug[x86]|> Task :app:configureCMakeDebug[x86]
      -1837090683_Task :app:buildCMakeDebug[x86]|> Task :app:buildCMakeDebug[x86] FAILED
      -1837090683_Task :app:buildCMakeDebug[x86]|C/C++: ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/1r1y724v/x86'
      -1837090683_Task :app:buildCMakeDebug[x86]|C/C++: /Users/jomof/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++ --target=i686-none-linux-android21 --sysroot=/Users/jomof/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/sysroot -Dapp_EXPORTS  -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -mstackrealign -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security   -fno-limit-debug-info  -fPIC -MD -MT CMakeFiles/app.dir/app.cpp.o -MF CMakeFiles/app.dir/app.cpp.o.d -o CMakeFiles/app.dir/app.cpp.o -c /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp
      -1837090683_Task :app:buildCMakeDebug[x86]|C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:7:3: error: no matching function for call to 'bar'
      -1837090683_Task :app:buildCMakeDebug[x86]|C/C++:   bar(b);
      -1837090683_Task :app:buildCMakeDebug[x86]|C/C++:   ^~~
      -1837090683_Task :app:buildCMakeDebug[x86]|C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:2:13: note: candidate function not viable: no known conversion from 'int' to 'const int *' for 1st argument; take the address of the argument with &
      -1837090683_Task :app:buildCMakeDebug[x86]|C/C++: extern void bar(const int*);
      -1837090683_Task :app:buildCMakeDebug[x86]|C/C++:             ^
      -1837090683_Task :app:buildCMakeDebug[x86]|C/C++: 1 error generated.
      -1837090683_Task :app:configureCMakeDebug[x86_64]|> Task :app:configureCMakeDebug[x86_64]
      -1837090683_Task :app:buildCMakeDebug[x86_64]|> Task :app:buildCMakeDebug[x86_64] FAILED
      -1837090683_Task :app:mergeDebugJniLibFolders|> Task :app:mergeDebugJniLibFolders UP-TO-DATE
      -1837090683_Task :app:validateSigningDebug|> Task :app:validateSigningDebug UP-TO-DATE
      -1837090683_Task :app:writeDebugAppMetadata|> Task :app:writeDebugAppMetadata UP-TO-DATE
      -1837090683_Task :app:buildCMakeDebug[x86_64]|C/C++: ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/1r1y724v/x86_64'
      -1837090683_Task :app:writeDebugSigningConfigVersions|> Task :app:writeDebugSigningConfigVersions UP-TO-DATE
      -1837090683_Task :app:writeDebugSigningConfigVersions|FAILURE: Build completed with 4 failures.
      -1837090683_Task :app:buildCMakeDebug[x86_64]|C/C++: /Users/jomof/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++ --target=x86_64-none-linux-android21 --sysroot=/Users/jomof/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/sysroot -Dapp_EXPORTS  -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security   -fno-limit-debug-info  -fPIC -MD -MT CMakeFiles/app.dir/app.cpp.o -MF CMakeFiles/app.dir/app.cpp.o.d -o CMakeFiles/app.dir/app.cpp.o -c /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp
      -1837090683_Task :app:writeDebugSigningConfigVersions|1: Task failed with an exception.
      -1837090683_Task :app:writeDebugSigningConfigVersions|-----------
      -1837090683_Task :app:writeDebugSigningConfigVersions|* What went wrong:
      -1837090683_Task :app:buildCMakeDebug[x86_64]|C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:7:3: error: no matching function for call to 'bar'
      -1837090683_Task :app:writeDebugSigningConfigVersions|Execution failed for task ':app:buildCMakeDebug[arm64-v8a]'.
      -1837090683_Task :app:buildCMakeDebug[x86_64]|C/C++:   bar(b);
      -1837090683_Task :app:writeDebugSigningConfigVersions|> com.android.ide.common.process.ProcessException: ninja: Entering directory `/Users/jomof/projects/repro/as-bad-error-context-repro/app/.cxx/Debug/1r1y724v/arm64-v8a'
      -1837090683_Task :app:buildCMakeDebug[x86_64]|C/C++:   ^~~
      -1837090683_Task :app:buildCMakeDebug[x86_64]|C/C++: /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:2:13: note: candidate function not viable: no known conversion from 'int' to 'const int *' for 1st argument; take the address of the argument with &
      -1837090683_Task :app:writeDebugSigningConfigVersions|  [1/2] Building CXX object CMakeFiles/app.dir/app.cpp.o
      -1837090683_Task :app:writeDebugSigningConfigVersions|  FAILED: CMakeFiles/app.dir/app.cpp.o 
      -1837090683_Task :app:buildCMakeDebug[x86_64]|C/C++: extern void bar(const int*);
      -1837090683_Task :app:writeDebugSigningConfigVersions|  /Users/jomof/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/bin/clang++ --target=aarch64-none-linux-android21 --sysroot=/Users/jomof/Library/Android/sdk/ndk/25.1.8937393/toolchains/llvm/prebuilt/darwin-x86_64/sysroot -Dapp_EXPORTS  -g -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security   -fno-limit-debug-info  -fPIC -MD -MT CMakeFiles/app.dir/app.cpp.o -MF CMakeFiles/app.dir/app.cpp.o.d -o CMakeFiles/app.dir/app.cpp.o -c /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp
      -1837090683_Task :app:buildCMakeDebug[x86_64]|C/C++:             ^
      -1837090683_Task :app:writeDebugSigningConfigVersions|  /Users/jomof/projects/repro/as-bad-error-context-repro/app/src/main/cpp/app.cpp:7:3: error: no matching function for call to 'bar'
      -1837090683_Task :app:buildCMakeDebug[x86_64]|C/C++: 1 error generated.
      -1837090683_Task :app:writeDebugSigningConfigVersions|    bar(b);
      -1837090683_Task :app:writeDebugSigningConfigVersions|    ^~~
      -1837090683_Task :app:writeDebugSigningConfigVersions|BUILD FAILED in 19s
      """.trimIndent()
    ) {
      assertDiagnosticMessages(
        "[:app Debug x86]" to "",
        "[:app Debug x86_64]" to "",
      )
    }
  }

  /**
   * Found by fuzzing, a "note:" with no preceding error or warning to associate it with.
   */
  @Test
  fun `fuzz 1`() {
    checkInterleavedParse("""
      Parent0|> Task :app:buildCMakeDebug[x86]
      Parent0|/f.c:1:2: note: This is a note
      Parent-256|BUILD FAILED in 1s
    """.trimIndent()) { }
  }

  /**
   * Found by fuzzing, a path that cannot be canonicalized.
   */
  @Test
  fun `fuzz 2`() {
    checkInterleavedParse("""
      Parent-256|> Task :app:buildCMakeDebug[x86]
      Parent-256|ninja: Entering directory `/some/dir'
      Parent-256|17.1.2- -B --?"a\\\\"b" c //f.c:1:2: error: b?//
    """.trimIndent()) { }
  }

  /**
   * Found by fuzzing, warning followed by another warning
   */
  @Test
  fun `fuzz 3`() {
    checkInterleavedParse("""
      PARENT2|> Task :app:buildCMakeDebug[x86]
      PARENT2|/f.c:1:2: warning: warning1
      PARENT2|/f.c:1:2: warning: warning2
    """.trimIndent()) { }
  }

  /**
   * Found by fuzzing, fatal error after error
   */
  @Test
  fun `fuzz 4`() {
    checkInterleavedParse("""
    PARENT1|> Task :app:buildCMakeDebug[x86]
    PARENT1|/f.c:1:2: error: error
    PARENT1|ld: fatal error: f.so: open: Invalid argument
    """.trimIndent()) { }
  }

  /**
   * Found by fuzzing, warning followed by command-line
   */
  @Test
  fun `fuzz 5`() {
    checkInterleavedParse("""
      PARENT1|> Task :app:configureNdkBuildRelease[x86_64]
      PARENT1||f.c:1:2: warning: warning
      PARENT1|1 warning clang++ --target=android
    """.trimIndent()) { }
  }

  /**
   * Found by fuzzing, enter ninja directory after error
   */
  @Test
  fun `fuzz 6`() {
    checkInterleavedParse("""
      PARENT1|> Task :app:configureNdkBuildRelease[x86_64]
      PARENT1|clang: error: linker command failed
      PARENT1|ninja: Entering directory `/some/dir'
    """.trimIndent()) { }
  }

  /**
   * Found by fuzzing
   */
  @Test
  fun `fuzz 7`() {
    checkInterleavedParse("""
      PARENT2|> Task :app:configureNdkBuildRelease[x86_64]
      PARENT2|/f.c:1:2: error: body
      PARENT2|warn: note: body
    """.trimIndent()) { }
  }

  private fun checkInterleavedParse(rawLines : String, block: List<MessageEventImpl>.() -> Unit)  {
    val lines = rawLines
      .split("\n")
      .mapIndexed { n, line ->
        val id = line.substringBefore("|")
        val text = line.substringAfter("|")
        InstantReaderLine(id.trim(), text)
      }
    runParser(keepOnlyRecognizedLines(lines), block)
  }

  private data class InstantReaderLine(
    val parentId : String,
    val text : String
  )

  /**
   * Asserts that, given the input, the parser
   *
   * - only consumes lines that start with `* `
   * - output messages matching those tested by the given block
   */
  private fun assertParser(input: String, block: List<MessageEventImpl>.() -> Unit) {
    var id = "DummyID"
    val lines = input
      .split("\n")
      .mapIndexed { n, line ->
        val text = line.trimStart().substringAfter("* ")
        if (text.startsWith("> Task ")) {
          id = text.substringAfter("> Task")
        }
        InstantReaderLine(id.trim(), text)
      }
      // Run first time with lines that AGP doesn't recognize removed.
      runParser(keepOnlyRecognizedLines(lines), block)
      // Run second time with lines that AGP doesn't recognize kept.
      runParser(lines, block)
  }

  /**
   * AGP will only elevate some lines. This function removes lines AGP would not elevate
   * so that the test can verify it ClangOutputParser is able to parse with only those
   * lines.
   */
  private fun keepOnlyRecognizedLines(lines : List<InstantReaderLine>) :  List<InstantReaderLine> {
    val result = mutableListOf<InstantReaderLine>()
    val classifiers = mutableMapOf<String, NativeBuildOutputClassifier>()
    lines.forEach { (parent, line) ->
      if (line.contains("> Task")) {
        result.add(InstantReaderLine(parent, line))
      } else {
        val classifier = classifiers.computeIfAbsent(parent) {
          NativeBuildOutputClassifier { message ->
            message.lines.forEach { messageLine ->
              result.add(InstantReaderLine(parent, messageLine))
            }
          }
        }
        classifier.consume(line)
      }
    }
    classifiers.values.forEach { it.flush() }

    lines.forEach { (parent, line) ->
      if (line.startsWith("BUILD FAILED in ")) {
        result.add(InstantReaderLine(parent, line))
      }
    }
    return result
  }

  private fun runParser(lines : List<InstantReaderLine>, block: List<MessageEventImpl>.() -> Unit) {
    val parser = ClangOutputParser()
    val consumer = TestMessageEventConsumer()
    val readerMap = mutableMapOf<String, TestBuildOutputInstantReader>()
    for((parentId, _) in lines) {
      val reader = readerMap.computeIfAbsent(parentId) {
        TestBuildOutputInstantReader(
          lines.filter { it.parentId == parentId }.map { it.text },
          parentId
        )
      }
      val line = reader.readLine() ?: continue
      if (!parser.parse(line, reader, consumer)) {
        // Assert the reader is consistent with respect to the "current" line so there is no surprises for parsers after this parser.
        reader.pushBack()
        Truth.assertThat(line).named("current line in reader").isEqualTo(reader.readLine())
      }
    }
    block(consumer.messageEvents.map { it as MessageEventImpl } )
  }

  /** normalize path separator so it works on windows.*/
  private fun String.normalizeSeparator() = replace('/', File.separatorChar)
}




