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
package com.android.tools.idea.updater.configure

import com.android.repository.api.UpdatablePackage
import org.junit.Test
import org.mockito.Mockito.mock
import kotlin.test.assertEquals

class ToolComponentsPanelTest {
  // basic test of tree construction with no packages installed
  @Test
  fun testNodes() {
    val panel = ToolComponentsPanel()
    panel.setConfigurable(mock(SdkUpdaterConfigurable::class.java))
    val foo = createLocalPackage("foo", 1)
    val bar = createLocalPackage("bar", 2)
    val buildTools12 = createLocalPackage("build-tools;1.2", 1, 2)
    val buildTools13a1 = createLocalPackage("build-tools;1.3.0 rc1", 1, 3, 0, 1)
    val buildTools1 = createLocalPackage("build-tools;1", 1)
    val cmdlineTools1 = createLocalPackage("cmdline-tools;1", 1)
    val cmdlineTools12 = createLocalPackage("cmdline-tools;1.2", 1, 2)
    val cmdlineToolsLatest = createLocalPackage("cmdline-tools;latest", 1, 2)
    val packages = setOf(foo, bar, buildTools12, buildTools13a1, buildTools1, cmdlineTools1, cmdlineTools12, cmdlineToolsLatest)
    panel.setPackages(packages.map { UpdatablePackage(it) }.toSet())

    assertEquals("""
      Root
       build-tools;
       cmdline-tools;
       bar
       foo
    """.trimIndent(), panel.myToolsSummaryRootNode.asString())
    assertEquals("""
      Root
       build-tools;
        1.3.0 rc1
        1.2
        1
       cmdline-tools;
        latest
        1.2
        1
       bar
       foo
    """.trimIndent(), panel.myToolsDetailsRootNode.asString())
  }
}