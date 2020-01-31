/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.google.common.base.Splitter
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
  fun `ndk - nothing`() = assertParser("""
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
  fun `ndk - no build errors`() = assertParser("""
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
  fun `ndk - simple case`() = assertParser("""
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
  fun `ndk - no module name`() = assertParser("""
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
  fun `ndk - different abis`() = assertParser("""
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
  fun `ndk - multiple interleaved diagnostic messages`() = assertParser("""
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
  fun `ndk - multiple gradle tasks`() = assertParser("""
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
    Assume.assumeTrue(SdkConstants.currentPlatform() == SdkConstants.PLATFORM_LINUX)
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
        "[:app Debug arm64-v8a]" to "/usr/local/google/home/jeff/HelloWorld/src/HelloWorld.cpp:33: error: undefined reference to 'foo()'"
      )
    }
  }

  @Test
  fun `linker - missing library`() {
    Assume.assumeTrue(SdkConstants.currentPlatform() == SdkConstants.PLATFORM_LINUX)
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
        "[:app Debug x86_64]" to "/usr/local/google/home/jeff/Android/Sdk/ndk-bundle/toolchains/llvm/prebuilt/windows-x86_64/x86_64-linux-android/bin/ld: error: cannot find -lbdisasm"
      )
    }
  }

  @Test
  fun `windows - linker error has augmented details`() {
    Assume.assumeTrue(SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS)
    assertParser("""
      > Task :app:externalNativeBuildDebug
    * ninja: Entering directory `/usr/local/google/home/jeff/hello-world/app/.cxx/cmake/debug/x86_64'
    * [1/1] Linking CXX shared library /usr/local/google/home/jeff/HelloWorld/app/build/intermediates/cmake/debug/obj/x86_64/libmain.so
    * FAILED: /usr/local/google/home/jeff/HelloWorld/app/build/intermediates/cmake/debug/obj/x86_64/libmain.so
    * C:\sdk\ndk-bundle\toolchains\llvm\prebuilt\windows-x86_64\lib\gcc\arm-linux-androideabi\4.9.x\bin\ld: fatal error: C:\build\intermediates\cmake\debug\obj\armeabi-v7a\libcore.so: open: Invalid argument
    * clang++.exe: error: linker command failed with exit code 1 (use -v to see invocation)
    * ninja: build stopped: subcommand failed.
    * :app:externalNativeBuildDebug FAILED
      FAILURE: Build failed with an exception.
    """) {
      assertDiagnosticMessages(
        "[:app Debug x86_64]" to """
           C:\sdk\ndk-bundle\toolchains\llvm\prebuilt\windows-x86_64\lib\gcc\arm-linux-androideabi\4.9.x\bin\ld: fatal error: C:\build\intermediates\cmake\debug\obj\armeabi-v7a\libcore.so: open: Invalid argument
    
           File C:\build\intermediates\cmake\debug\obj\armeabi-v7a\libcore.so is not writable. This may be caused by insufficient permissions or files being locked by other processes. For example, LLDB locks .so files in a while debugging.
        """.trimIndent()
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
    """) {
    Truth.assertThat(this).hasSize(1)
    with(this[0]) {
      Truth.assertThat(parentId).isEqualTo("Dummy Id")
      Truth.assertThat(kind).isEqualTo(MessageEvent.Kind.ERROR)
      Truth.assertThat(group).isEqualTo("Clang Compiler [:app Debug arm64-v8a]")
      Truth.assertThat(message).isEqualTo("'unresolved.h' file not found")
      Truth.assertThat(result.details).isEqualTo("""
          In file included from /usr/local/google/home/jeff/hello-world/native/source.cpp:8:
          /usr/local/google/home/jeff/hello-world/native/source.h:12:10: fatal error: 'unresolved.h' file not found
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
   * Asserts that, given the input, the parser
   *
   * - only consumes lines that start with `* `
   * - output messages matching those tested by the given block
   */
  private fun assertParser(input: String, block: List<MessageEventImpl>.() -> Unit) {
    val expectedUnconsumedLineIndices = mutableListOf<Int>()
    val lines = Splitter.on('\n').omitEmptyStrings().split(input.trimIndent()).toList().mapIndexed { index, line ->
      if (line.startsWith("* ")) {
        line.substring(2)
      }
      else {
        expectedUnconsumedLineIndices.add(index)
        line.trimStart()
      }
    }
    assertParserRaw(lines) { messages, actualUnconsumedLineIndices ->
      Truth.assertThat(actualUnconsumedLineIndices).named("unconsumed line indices").isEqualTo(expectedUnconsumedLineIndices)
      messages.block()
    }
  }

  private fun assertParserRaw(lines: List<String>, block: (messages: List<MessageEventImpl>, unconsumedLines: List<Int>) -> Unit) {
    val parser = ClangOutputParser()
    val consumer = TestMessageEventConsumer()
    val reader = TestBuildOutputInstantReader(lines)
    val unconsumedLineIndices = mutableListOf<Int>()
    while (true) {
      val line = reader.readLine() ?: break
      if (!parser.parse(line, reader, consumer)) {
        // Record the current line and check if it's not supposed to be consumed by parsing it to the block.
        unconsumedLineIndices.add(reader.currentIndex)
        // Assert the reader is consistent with respect to the "current" line so there is no surprises for parsers after this parser.
        Truth.assertThat(line).named("current line in reader").isEqualTo(reader.currentLine)
      }
    }
    block(consumer.messageEvents.map { it as MessageEventImpl }, unconsumedLineIndices)
  }

  /** normalize path separator so it works on windows.*/
  private fun String.normalizeSeparator() = replace('/', File.separatorChar)
}

