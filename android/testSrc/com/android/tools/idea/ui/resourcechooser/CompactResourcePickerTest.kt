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
package com.android.tools.idea.ui.resourcechooser

import com.android.resources.ResourceType
import com.android.testutils.AssumeUtil
import com.android.tools.adtui.swing.laf.HeadlessListUI
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.ui.resourcechooser.common.ResourcePickerSources
import com.android.tools.idea.ui.resourcemanager.simulateMouseClick
import com.android.tools.idea.ui.resourcemanager.waitAndAssert
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.SearchTextField
import com.intellij.util.ui.UIUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.JPanel
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Language("XML")
private const val COLORS_RESOURCE_FILE_CONTENTS =
  """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="colorPrimary">#008577</color>
    <color name="colorPrimaryDark">#64B5F6</color>
    <color name="colorAccent">#D2E3F8</color>
    <color name="colorForeground">#9067BCFF</color>
</resources>"""

@RunsInEdt
class CompactResourcePickerTest {

  @get:Rule
  val edtRule = EdtRule()

  companion object {
    @ClassRule
    @JvmField
    val rule = AndroidProjectRule.withSdk()

    @BeforeClass
    @JvmStatic
    fun setup() {
      // TODO(b/153469993): Remove assumption when fixed.
      AssumeUtil.assumeNotWindows()
      rule.fixture.addFileToProject("res/values/colors.xml", COLORS_RESOURCE_FILE_CONTENTS)
    }
  }

  private lateinit var disposable: Disposable

  @Before
  fun setupTest() {
    disposable = Disposer.newDisposable(javaClass.simpleName)
  }

  @After
  fun teardown() {
    Disposer.dispose(disposable)
  }

  @Test
  fun selectedResource() {
    var selectedResource = ""
    val resourcePickerPanel = createAndWaitForResourcePickerPanel { selectedResource = it }
    val resourcesList = UIUtil.findComponentOfType(resourcePickerPanel, JList::class.java)!!

    simulateMouseClick(resourcesList, resourcesList.indexToLocation(0), 1)
    assertEquals("@color/colorAccent", selectedResource)
    simulateMouseClick(resourcesList, resourcesList.indexToLocation(1), 1)
    assertEquals("@color/colorForeground", selectedResource)
  }

  @Test
  fun searchResource() {
    var selectedResource = ""
    val resourcePickerPanel = createAndWaitForResourcePickerPanel { selectedResource = it }
    val searchField = UIUtil.findComponentOfType(resourcePickerPanel, SearchTextField::class.java)!!

    runInEdtAndWait {
      searchField.text = "Foreground"
    }
    waitAndAssert<JList<in Any>>(resourcePickerPanel) {
      // Wait till the filter is applied
      it != null && it.model.size == 1
    }
    val resourcesList = UIUtil.findComponentOfType(resourcePickerPanel, JList::class.java)!!
    // Select and assert the first resource in the list
    simulateMouseClick(resourcesList, resourcesList.indexToLocation(0), 1)
    assertEquals("@color/colorForeground", selectedResource)
  }

  @Test
  fun changeToFrameworkResourceSource() {
    var selectedResource = ""
    val resourcePickerPanel = createAndWaitForResourcePickerPanel { selectedResource = it }
    val resourceSourceComboBox = UIUtil.findComponentOfType(resourcePickerPanel, JComboBox::class.java)!!

    // Set the combo box to "Android"
    resourceSourceComboBox.model.selectedItem = resourceSourceComboBox.model.getElementAt(2)
    assertEquals("Android", resourceSourceComboBox.model.selectedItem!!.toString())

    waitAndAssert<JList<in Any>>(resourcePickerPanel) {
      // The local color resources only has 4 different resources, the framework repository should have more.
      it != null && it.model.size > 4
    }

    val resourcesList = UIUtil.findComponentOfType(resourcePickerPanel, JList::class.java)!!
    simulateMouseClick(resourcesList, resourcesList.indexToLocation(0), 1)
    // Assert that the first selected resource is an android color, we don't assert the full resource name since it might be a different
    // resource under a different Sdk.
    assertTrue(selectedResource.startsWith("@android:color/"))
  }

  @Test
  fun onlyFrameworkSources() {
    val resourcePickerPanel = createAndWaitForResourcePickerPanel(listOf(ResourcePickerSources.ANDROID)) {}
    val resourceSourceComboBox = UIUtil.findComponentOfType(resourcePickerPanel, JComboBox::class.java)!!

    // We set the the picker to only be able to show Android framework resources (no other options available)
    assertEquals("Android", resourceSourceComboBox.model.selectedItem!!.toString())
    assertEquals(1, resourceSourceComboBox.model.size)

    waitAndAssert<JList<in Any>>(resourcePickerPanel) {
      // The local color resources only has 4 different resources, the framework repository should have more.
      it != null && it.model.size > 4
    }
  }

  private fun createAndWaitForResourcePickerPanel(resourcePickerSources: List<ResourcePickerSources> = ResourcePickerSources.allSources(),
                                                  onSelectedResource: (String) -> Unit): JPanel {
    val facet = AndroidFacet.getInstance(rule.module)!!
    val configuration = ConfigurationManager.getOrCreateInstance(facet.module).getConfiguration(
      LocalFileSystem.getInstance().findFileByPath(rule.project.basePath!!)!!)
    val panel = CompactResourcePicker(
      AndroidFacet.getInstance(rule.module)!!,
      configuration.file,
      configuration.resourceResolver,
      ResourceType.COLOR,
      resourcePickerSources,
      onSelectedResource,
      {},
      disposable
    )

    runInEdtAndWait {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      UIUtil.findComponentOfType(panel, JList::class.java)!!.setUI(HeadlessListUI())
    }

    // Wait for the panel to be populated
    waitAndAssert<JList<in Any>>(panel) {
      it != null && it.model.size > 0
    }
    return panel
  }
}