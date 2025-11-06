/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.diagnostics

import kotlin.test.assertEquals
import org.junit.Test

class SanitizedBuilderTest {
  private lateinit var builder: SanitizedBuilder

  @Test
  fun testSanitizeEOL() {
    builder = SanitizedBuilder()
    val linuxLine = "Event: 0.003 Loaded shared library /usr/local/google/home/taorantr/.local/share/JetBrains/Toolbox/apps/android-studio/jbr/lib/libjava.so"
    val expectedLinuxLine = "Event: 0.003 Loaded shared library <elided>\n"
    builder.sanitizeUntilEOL(linuxLine)
    assertEquals(expectedLinuxLine, builder.toString())

    builder = SanitizedBuilder()
    val windowsLine = "Event: 0.003 Loaded shared library C:\\Users\\taorantr\\AppData\\Local\\JetBrains\\Toolbox\\apps\\android-studio\\jbr\\lib\\libjava.so"
    val expectedWindowsLine = "Event: 0.003 Loaded shared library <elided>\n"
    builder.sanitizeUntilEOL(windowsLine)
    assertEquals(expectedWindowsLine, builder.toString())
  }

  @Test
  fun testSanitizeEOLWithSpace() {
    builder = SanitizedBuilder()
    val linuxLineWithSpace = "Event: 0.003 Loaded shared library /usr/local/goo gle/home/taor antr/.local/share/JetBrains/Toolbox/apps/android-studio/jbr/lib/libjava.so"
    val expectedLinuxLineWithSpace = "Event: 0.003 Loaded shared library <elided>\n"
    builder.sanitizeUntilEOL(linuxLineWithSpace)
    assertEquals(expectedLinuxLineWithSpace, builder.toString())

    builder = SanitizedBuilder()
    val windowsLineWithSpace = "Event: 0.003 Loaded shared library C:\\Program File\\User\\taoran tr\\AppData\\Local\\JetBrains\\Toolbox\\apps\\android-studio\\jbr\\lib\\libjava.so"
    val expectedWindowsLineWithSpace = "Event: 0.003 Loaded shared library <elided>\n"
    builder.sanitizeUntilEOL(windowsLineWithSpace)
    assertEquals(expectedWindowsLineWithSpace, builder.toString())
  }

  @Test
  fun testSanitizeEOLWrongSlash() {
    builder = SanitizedBuilder()
    val linuxLineWithWrongSlash = "Event: 0.003 Loaded shared library /usr/local\\google/home/taor antr/.local/share/JetBrains\\Toolbox/apps/android-studio/jbr/lib/libjava.so"
    val expectedLinuxLineWithWrongSlash = "Event: 0.003 Loaded shared library <elided>\n"
    builder.sanitizeUntilEOL(linuxLineWithWrongSlash)
    assertEquals(expectedLinuxLineWithWrongSlash, builder.toString())

    builder = SanitizedBuilder()
    val windowsLineWithWrongSlash = "Event: 0.003 Loaded shared library C:/Program File\\User/aorantr\\AppData\\Local\\JetBrains\\Toolbox\\apps\\android-studio\\jbr\\lib\\libjava.so"
    val expectedWindowsLineWithWrongSlash = "Event: 0.003 Loaded shared library <elided>\n"
    builder.sanitizeUntilEOL(windowsLineWithWrongSlash)
    assertEquals(expectedWindowsLineWithWrongSlash, builder.toString())
  }

  @Test
  fun testSanitizeEOLWithMultiplePaths() {
    builder = SanitizedBuilder()
    val linuxLine = "abort vfprintf -XX:ErrorFile=/usr/local/google/home/taorantr/java_error_in_studio_%p.log " +
                    "-XX:HeapDumpPath=/usr/local/google/home/taorantr/java_error_in_studio.hprof -Xms256m -Xmx2048m " +
                    "-Dide.managed.by.toolbox=/usr/local/google/home/taorantr/Documents/jetbrains-toolbox-2.8.1.52155/bin/jetbrains-toolbox"
    val expectedLinuxLine = "abort vfprintf -XX:ErrorFile=<elided>\n"
    builder.sanitizeUntilEOL(linuxLine)
    assertEquals(expectedLinuxLine, builder.toString())

    builder = SanitizedBuilder()
    val windowsLine = "exit -XX:ErrorFile=C:\\Users\\taorantr\\\\java_error_in_idea64_%p.log -" +
                      "XX:HeapDumpPath=C:\\Users\\taorantr\\\\java_error_in_idea64.hprof -Xms128m -Xmx2048m " +
                      "-Djb.vmOptionsFile=C:\\Users\\taorantr\\AppData\\Roaming\\\\JetBrains\\\\IntelliJIdea2023.2\\idea64.exe.vmoptions"
    val expectedWindowsLine = "exit -XX:ErrorFile=<elided>\n"
    builder.sanitizeUntilEOL(windowsLine)
    assertEquals(expectedWindowsLine, builder.toString())
  }

  @Test
  fun testSanitizeEOLWithMixPaths() {
    builder = SanitizedBuilder()
    val mixLine1 = "abort vfprintf -XX:ErrorFile=/usr/local/google/home/taorantr/java_error_in_studio_%p.log " +
                   "-XX:HeapDumpPath=C:\\Users\\taorantr\\\\java_error_in_idea64.hprof -Xms128m -Xmx2048m"
    val expectedMixLine1 = "abort vfprintf -XX:ErrorFile=<elided>\n"
    builder.sanitizeUntilEOL(mixLine1)
    assertEquals(expectedMixLine1, builder.toString())

    builder = SanitizedBuilder()
    val mixLine2 = "exit -XX:ErrorFile=C:\\Users\\taorantr\\\\java_error_in_idea64_%p.log " +
                   "-XX:HeapDumpPath=/usr/local/google/home/taorantr/java_error_in_studio.hprof -Xms256m -Xmx2048m"
    val expectedMixLine2 = "exit -XX:ErrorFile=<elided>\n"
    builder.sanitizeUntilEOL(mixLine2)
    assertEquals(expectedMixLine2, builder.toString())
  }

  @Test
  fun testSanitizeMultiplePaths() {
    builder = SanitizedBuilder()
    val linuxLine = "abort vfprintf -XX:ErrorFile=/usr/local/google/home/taorantr/java_error_in_studio_%p.log " +
                    "-XX:HeapDumpPath=/usr/local/google/home/taorantr/java_error_in_studio.hprof -Xms256m -Xmx2048m " +
                    "-Dide.managed.by.toolbox=/usr/local/google/home/taorantr/Documents/jetbrains-toolbox-2.8.1.52155/bin/jetbrains-toolbox"
    val expectedLinuxLine = "abort vfprintf -XX:ErrorFile=<elided> -XX:HeapDumpPath=<elided> -Xms256m -Xmx2048m " +
                            "-Dide.managed.by.toolbox=<elided>\n"
    builder.sanitizeMultiplePaths(linuxLine)
    assertEquals(expectedLinuxLine, builder.toString())

    builder = SanitizedBuilder()
    val windowsLine = "exit -XX:ErrorFile=C:\\Users\\taorantr\\\\java_error_in_idea64_%p.log -" +
                      "XX:HeapDumpPath=C:\\Users\\taorantr\\\\java_error_in_idea64.hprof -Xms128m -Xmx2048m " +
                      "-Djb.vmOptionsFile=C:\\Users\\taorantr\\AppData\\Roaming\\\\JetBrains\\\\IntelliJIdea2023.2\\idea64.exe.vmoptions"
    val expectedWindowsLine = "exit -XX:ErrorFile=<elided> -XX:HeapDumpPath=<elided> -Xms128m -Xmx2048m " +
                              "-Djb.vmOptionsFile=<elided>\n"
    builder.sanitizeMultiplePaths(windowsLine)
    assertEquals(expectedWindowsLine, builder.toString())
  }

  @Test
  fun testSanitizeMultiplePathsWithMix() {
    builder = SanitizedBuilder()
    val mixLine1 = "exit -XX:ErrorFile=C:\\Users\\taorantr\\\\java_error_in_idea64_%p.log -" +
                   "XX:HeapDumpPath=C:\\Users\\taorantr\\\\java_error_in_idea64.hprof -Xms128m -Xmx2048m " +
                   "-Dide.managed.by.toolbox=/usr/local/google/home/taorantr/Documents/jetbrains-toolbox-2.8.1.52155/bin/jetbrains-toolbox"
    val expectedMixLine1 = "exit -XX:ErrorFile=<elided> -XX:HeapDumpPath=<elided> -Xms128m -Xmx2048m " +
                           "-Dide.managed.by.toolbox=<elided>\n"
    builder.sanitizeMultiplePaths(mixLine1)
    assertEquals(expectedMixLine1, builder.toString())

    builder = SanitizedBuilder()
    val mixLine2 = "abort vfprintf -XX:ErrorFile=/usr/local/google/home/taorantr/java_error_in_studio_%p.log " +
                   "-XX:HeapDumpPath=/usr/local/google/home/taorantr/java_error_in_studio.hprof -Xms256m -Xmx2048m " +
                   "-Djb.vmOptionsFile=C:\\Users\\taorantr\\AppData\\Roaming\\\\JetBrains\\\\IntelliJIdea2023.2\\idea64.exe.vmoptions " +
                   "-Dide.managed.by.toolbox=/usr/local/google/home/taorantr/Documents/jetbrains-toolbox-2.8.1.52155/bin/jetbrains-toolbox " +
                   "-Dpty4j.preferred.native.folder=C:\\Program Files\\JetBrains\\IntelliJ IDEA 2023.2.1/lib/pty4j " +
                   "-Dide.native.launcher=true -Djcef.sandbox.ptr=0000015FBB000CF0"
    val expectedMixLine2 = "abort vfprintf -XX:ErrorFile=<elided> -XX:HeapDumpPath=<elided> -Xms256m -Xmx2048m " +
                           "-Djb.vmOptionsFile=<elided> " +
                           "-Dide.managed.by.toolbox=<elided> -Dpty4j.preferred.native.folder=<elided> " +
                           "-Dide.native.launcher=true -Djcef.sandbox.ptr=0000015FBB000CF0\n"
    builder.sanitizeMultiplePaths(mixLine2)
    assertEquals(expectedMixLine2, builder.toString())
  }

  @Test
  fun testSanitizeMultiplePathsWrongSlash() {
    builder = SanitizedBuilder()
    val linuxLineWithWrongSlash = "abort vfprintf -XX:ErrorFile=/usr\\local/google/home/taorantr/java_error_in_studio_%p.log " +
                                  "-XX:HeapDumpPath=/usr/local/google/home/taorantr\\java_error_in_studio.hprof -Xms256m -Xmx2048m " +
                                  "-Dide.managed.by.toolbox=/usr/local/google/home\\taorantr/Documents\\jetbrains-toolbox-2.8.1.52155/bin/jetbrains-toolbox " +
                                  "-Djcef.sandbox.ptr=0000015FBB000CF0"
    val expectedLinuxLineWithWrongSlash = "abort vfprintf -XX:ErrorFile=<elided> -XX:HeapDumpPath=<elided> -Xms256m -Xmx2048m " +
                                          "-Dide.managed.by.toolbox=<elided> -Djcef.sandbox.ptr=0000015FBB000CF0\n"
    builder.sanitizeMultiplePaths(linuxLineWithWrongSlash)
    assertEquals(expectedLinuxLineWithWrongSlash, builder.toString())

    builder = SanitizedBuilder()
    val windowsLineWithWrongSlash = "exit -XX:ErrorFile=C:\\Users/taorantr\\\\java_error_in_idea64_%p.log -" +
                                    "XX:HeapDumpPath=C:\\Users\\taorantr//java_error_in_idea64.hprof -Xms128m -Xmx2048m " +
                                    "-XX:CICompilerCount=2 -XX:+HeapDumpOnOutOfMemoryError -XX:-OmitStackTraceInFastThrow " +
                                    "-Djb.vmOptionsFile=C:/Users\\taorantr\\AppData\\Roaming\\\\JetBrains\\\\IntelliJIdea2023.2\\idea64.exe.vmoptions"
    val expectedWindowsLineWithWrongSlash = "exit -XX:ErrorFile=<elided> -XX:HeapDumpPath=<elided> -Xms128m -Xmx2048m " +
                                            "-XX:CICompilerCount=2 -XX:+HeapDumpOnOutOfMemoryError -XX:-OmitStackTraceInFastThrow " +
                                            "-Djb.vmOptionsFile=C:<elided>\n"
    builder.sanitizeMultiplePaths(windowsLineWithWrongSlash)
    assertEquals(expectedWindowsLineWithWrongSlash, builder.toString())
  }

  @Test
  fun testSanitizeNoAbsolutePath() {
    builder = SanitizedBuilder()
    val noPathLine = "Host: 11th Gen Intel(R) Core(TM) i9-11900K @ 3.50GHz, 16 cores, 63G,  Windows 11 , 64 bit Build 22621 (10.0.22621.2215)"
    builder.sanitizeUntilEOL(noPathLine)
    assertEquals(noPathLine + "\n", builder.toString())

    builder = SanitizedBuilder()
    builder.sanitizeMultiplePaths(noPathLine)
    assertEquals(noPathLine + "\n", builder.toString())

    builder = SanitizedBuilder()
    val classPathLine = "Event: 8.306 loading class javax/swing/ComboBoxModel"
    builder.sanitizeUntilEOL(classPathLine)
    assertEquals(classPathLine + "\n", builder.toString())

    builder = SanitizedBuilder()
    builder.sanitizeMultiplePaths(classPathLine)
    assertEquals(classPathLine + "\n", builder.toString())
  }

  @Test
  fun testSanitizeEmptyLine() {
    builder = SanitizedBuilder()
    val emptyLine = ""
    builder.sanitizeUntilEOL(emptyLine)
    assertEquals("\n", builder.toString())

    builder = SanitizedBuilder()
    builder.sanitizeMultiplePaths(emptyLine)
    assertEquals("\n", builder.toString())
  }

  @Test
  fun testSanitizePathAtStartingOfLine() {
    builder = SanitizedBuilder()
    val linuxLine = "/usr/local/google/home/taorantr/java_error_in_studio_%p.log "
    val expectedLinuxLine = "<elided>\n"
    builder.sanitizeUntilEOL(linuxLine)
    assertEquals(expectedLinuxLine, builder.toString())

    builder = SanitizedBuilder()
    val windowsLine = "C:\\Users\\taorantr\\java_error_in_studio_%p.log "
    val expectedWindowsLine = "<elided>\n"
    builder.sanitizeUntilEOL(windowsLine)
    assertEquals(expectedWindowsLine, builder.toString())
  }
}
