/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.testFramework.LightPlatformTestCase

/**
 * Kotlin tests for [SystemHealthMonitor].
 */
class AndroidStudioSystemHealthMonitorKtTest : LightPlatformTestCase() {
  fun testGetActionName() {
    val isJava8 = System.getProperty("java.specification.version") == "1.8"
    val expected1 = if (isJava8) "AndroidStudioSystemHealthMonitorKtTest.testGetActionName\$1" else
      "AnAction@AndroidStudioSystemHealthMonitorKtTest"
    val expected2 = if (isJava8) "AndroidStudioSystemHealthMonitorKtTest.testGetActionName\$2\$1" else
      "AnAction@AndroidStudioSystemHealthMonitorKtTest"
    // Anonymous class - in Java8, Kotlin classes are not recognized as anonymous classes by the JVM.
    //   Action is formatted as an inner class
    assertEquals(
      expected1,
      AndroidStudioSystemHealthMonitor.getActionName(object : AnAction() {
        override fun actionPerformed(e: AnActionEvent) {}
      }.javaClass, Presentation("foo")))
    // Double-nested anonymous class - same as above - not seen as an anonymous class.
    Any().apply {
      assertEquals(
        expected2,
        AndroidStudioSystemHealthMonitor.getActionName(object : AnAction() {
          override fun actionPerformed(e: AnActionEvent) {}
        }.javaClass, Presentation("foo")))
    }
  }
}
