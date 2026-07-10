/*
 * Copyright (C) 2026 The Android Open Source Project
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
package org.jetbrains.android.dom.navigation

import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.testing.AndroidDomRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunsInEdt
@RunWith(JUnit4::class)
class NavXmlHelperTest {
  private val projectRule = AndroidProjectRule.withSdk().initAndroid(true)
  private val domRule = AndroidDomRule("res/navigation") { projectRule.fixture }

  @get:Rule val ruleChain: TestRule = RuleChain.outerRule(projectRule).around(domRule).around(EdtRule())

  private val project by lazy { projectRule.project }
  private val fixture by lazy { projectRule.fixture }
  private val module by lazy { projectRule.module }

  @Test
  fun testGetStartDestLayoutIdWithSubgraph() {
    fixture.addFileToProject(
      "res/layout/fragment_first.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android" />
      """
        .trimIndent(),
    )
    fixture.addFileToProject(
      "res/navigation/subgraph.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <navigation xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:app="http://schemas.android.com/apk/res-auto"
          xmlns:tools="http://schemas.android.com/tools"
          android:id="@+id/subgraph"
          app:startDestination="@id/first_fragment">
          <fragment
              android:id="@+id/first_fragment"
              android:name="com.example.FirstFragment"
              tools:layout="@layout/fragment_first" />
      </navigation>
      """
        .trimIndent(),
    )
    val navGraphFile =
      fixture.addFileToProject(
        "res/navigation/nav_graph.xml",
        """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            android:id="@+id/nav_graph"
            app:startDestination="@id/subgraph">
            <include app:graph="@navigation/subgraph" />
        </navigation>
        """
          .trimIndent(),
      )

    val manager = ConfigurationManager.getOrCreateInstance(module)
    val configuration = manager.getConfiguration(navGraphFile.virtualFile)
    val resourceResolver = configuration.resourceResolver!!

    val layoutId = getStartDestLayoutId("@navigation/nav_graph", project, resourceResolver)
    assertThat(layoutId).isEqualTo("@layout/fragment_first")
  }

  @Test
  fun testGetStartDestLayoutIdWithLoop() {
    fixture.addFileToProject(
      "res/navigation/loop1.xml",
      """
      <?xml version="1.0" encoding="utf-8"?>
      <navigation xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:app="http://schemas.android.com/apk/res-auto"
          android:id="@+id/loop1"
          app:startDestination="@id/loop2">
          <include app:graph="@navigation/loop2" />
      </navigation>
      """
        .trimIndent(),
    )
    val loop2File =
      fixture.addFileToProject(
        "res/navigation/loop2.xml",
        """
        <?xml version="1.0" encoding="utf-8"?>
        <navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            android:id="@+id/loop2"
            app:startDestination="@id/loop1">
            <include app:graph="@navigation/loop1" />
        </navigation>
        """
          .trimIndent(),
      )

    val manager = ConfigurationManager.getOrCreateInstance(module)
    val configuration = manager.getConfiguration(loop2File.virtualFile)
    val resourceResolver = configuration.resourceResolver!!

    val layoutId = getStartDestLayoutId("@navigation/loop1", project, resourceResolver)
    assertThat(layoutId).isNull()
  }
}
