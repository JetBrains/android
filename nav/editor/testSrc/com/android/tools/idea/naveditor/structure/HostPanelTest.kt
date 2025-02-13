/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.naveditor.structure

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.laf.HeadlessListUI
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.naveditor.NavEditorRule
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.ui.FileOpenCaptureRule
import com.android.tools.idea.testing.waitForResourceRepositoryUpdates
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.codeVision.ui.popup.layouter.bottom
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import java.awt.Dimension
import javax.swing.DefaultListModel
import kotlin.test.fail
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class HostPanelTest {
  private val projectRule = AndroidProjectRule.withSdk()
  private val navRule = NavEditorRule(projectRule)
  private val fileOpenRule = FileOpenCaptureRule(projectRule)

  @get:Rule val chain = RuleChain(projectRule, navRule, fileOpenRule, EdtRule())

  @Test
  fun testDumbMode() {
    // This has a navHostFragment referencing our nav file
    projectRule.fixture.addFileToProject(
      "res/layout/file1.xml",
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
        "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
        "    xmlns:app=\"http://schemas.android.com/apk/res-auto\">\n" +
        "\n" +
        "    <fragment\n" +
        "        android:id=\"@+id/fragment3\"\n" +
        "        android:name=\"androidx.navigation.fragment.NavHostFragment\"\n" +
        "        app:defaultNavHost=\"true\"\n" +
        "        app:navGraph=\"@navigation/nav\" />\n" +
        "\n" +
        "</LinearLayout>",
    )
    val model = modelBuilder("nav.xml") { navigation() }.build(false)
    waitForResourceRepositoryUpdates(projectRule.module)
    val panel = HostPanel(model.surface as NavDesignSurface)
    val listModel = panel.list.model as DefaultListModel
    waitFor("list was never populated") { !listModel.isEmpty }
    panel.list.model = listModel

    DumbModeTestUtils.runInDumbModeSynchronously(projectRule.project) {
      model.activate(this)
      waitFor("list expected to be empty") { listModel.isEmpty }
    }

    // This forces the listModel to be loaded on the next model activation. Without this call, the
    // panel will
    // detect that no changes have happened and ignore the changes.
    panel.resetCachedVersionCount()
    model.deactivate(this)
    model.activate(this)
    waitFor("list should be re-populated") { !listModel.isEmpty }
  }

  @Test
  fun testDoubleClick() {
    // This has a navHostFragment referencing our nav file
    projectRule.fixture.addFileToProject(
      "res/layout/file1.xml",
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
        "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
        "    xmlns:app=\"http://schemas.android.com/apk/res-auto\">\n" +
        "\n" +
        "    <fragment\n" +
        "        android:id=\"@+id/fragment3\"\n" +
        "        android:name=\"androidx.navigation.fragment.NavHostFragment\"\n" +
        "        app:defaultNavHost=\"true\"\n" +
        "        app:navGraph=\"@navigation/nav\" />\n" +
        "\n" +
        "</LinearLayout>",
    )
    val model = modelBuilder("nav.xml") { navigation() }.build(false)
    waitForResourceRepositoryUpdates(projectRule.module)
    val panel = HostPanel(model.surface as NavDesignSurface)
    panel.list.ui = HeadlessListUI()
    val listModel = panel.list.model as DefaultListModel
    waitFor("list was never populated") { !listModel.isEmpty }
    panel.list.model = listModel

    panel.size = Dimension(2000, 5000)
    val ui = FakeUi(panel)
    val bounds = panel.list.getCellBounds(0, 0)

    // A double click below the items in the list, does not go anywhere:
    ui.mouse.doubleClick(bounds.centerX.toInt(), bounds.bottom + 10)
    fileOpenRule.checkNoNavigation()

    // A double click on the item does:
    ui.mouse.doubleClick(bounds.centerX.toInt(), bounds.centerY.toInt())
    fileOpenRule.checkFileOpened("file1.xml", true)
  }

  @Test
  fun testFindReferences() {
    // This has a navHostFragment referencing our nav file
    projectRule.fixture.addFileToProject(
      "res/layout/file1.xml",
      """
      .<?xml version="1.0" encoding="utf-8"?>
      .<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
      .    xmlns:app="http://schemas.android.com/apk/res-auto">
      .
      .    <fragment
      .       android:id="@+id/fragment3"
      .       android:name="androidx.navigation.fragment.NavHostFragment"
      .       app:defaultNavHost="true"
      .       app:navGraph="@navigation/nav"/>
      .
      .</LinearLayout>
      """
        .trimMargin("."),
    )

    // This has a navHostFragment referencing a different nav file
    projectRule.fixture.addFileToProject(
      "res/layout/file2.xml",
      """
      .<?xml version="1.0" encoding="utf-8"?>
      .<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
      .   xmlns:app="http://schemas.android.com/apk/res-auto">
      .
      .   <fragment
      .     android:id="@+id/fragment3"
      .     android:name="androidx.navigation.fragment.NavHostFragment"
      .     app:defaultNavHost="true"
      .     app:navGraph="@navigation/navigation"/>
      .
      .</LinearLayout>
      """
        .trimMargin("."),
    )

    // This has a fragment referencing this file, but it's not a navHostFragment
    projectRule.fixture.addFileToProject(
      "res/layout/file3.xml",
      """
      .<?xml version="1.0" encoding="utf-8"?>
      .<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
      .   xmlns:app="http://schemas.android.com/apk/res-auto">
      .
      .   <fragment
      .       android:id="@+id/fragment3"
      .       android:name="com.example.MyFragment"
      .       app:defaultNavHost="true"
      .       app:navGraph="@navigation/navigation"/>
      .
      .</LinearLayout>
      """
        .trimMargin("."),
    )

    val model = model("nav.xml") { navigation() }
    waitForResourceRepositoryUpdates(projectRule.module)

    val references = findReferences(model.file, model.module)
    assertThat(references.size).isEqualTo(1)
    assertThat(references[0].containingFile.name).isEqualTo("file1.xml")
    assertThat(references[0].textOffset).isEqualTo(174)
  }

  @Test
  fun testFindDerivedClassReference() {
    // This has a subclass of NavHostFragment referencing this file
    projectRule.fixture.addFileToProject(
      "res/layout/file1.xml",
      """
      .<?xml version="1.0" encoding="utf-8"?>
      .<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
      .    xmlns:app="http://schemas.android.com/apk/res-auto">
      .
      .    <fragment
      .        android:id="@+id/fragment3"
      .        android:name="mytest.navtest.NavHostFragmentChild"
      .        app:defaultNavHost="true"
      .        app:navGraph="@navigation/nav" />
      .
      .</LinearLayout>
      """
        .trimMargin("."),
    )

    projectRule.fixture.addFileToProject(
      "src/mytest/navtest/NavHostFragmentChild.java",
      """
      .package mytest.navtest;
      .import androidx.navigation.fragment.NavHostFragment;
      .
      .public class NavHostFragmentChild extends NavHostFragment {
      .}
      """
        .trimMargin("."),
    )

    val model = model("nav.xml") { navigation() }
    waitForResourceRepositoryUpdates(projectRule.module)

    val references = findReferences(model.file, model.module)
    assertThat(references.size).isEqualTo(1)
    assertThat(references[0].containingFile.name).isEqualTo("file1.xml")
    assertThat(references[0].textOffset).isEqualTo(174)
  }

  private fun model(name: String, f: () -> ComponentDescriptor): SyncNlModel {
    return modelBuilder(name, f).build()
  }

  private fun modelBuilder(name: String, f: () -> ComponentDescriptor): ModelBuilder {
    return NavModelBuilderUtil.model(
      name,
      projectRule.module.androidFacet!!,
      projectRule.fixture,
      f,
    )
  }

  private fun waitFor(error: String, condition: () -> Boolean) {
    repeat(1000) {
      if (condition()) {
        return
      }
      Thread.sleep(10)
    }
    fail(error)
  }
}
