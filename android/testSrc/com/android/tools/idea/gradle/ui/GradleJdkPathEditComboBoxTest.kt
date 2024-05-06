/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.ui

import com.android.testutils.MockitoKt.argumentCaptor
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.mockStatic
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths
import com.intellij.icons.AllIcons
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.ui.JBColor
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.util.Consumer
import org.jetbrains.android.util.AndroidBundle
import org.mockito.ArgumentCaptor
import kotlin.io.path.Path

class GradleJdkPathEditComboBoxTest : LightPlatformTestCase() {

  fun `test Given no selection and empty suggested JDKs When create component Then no JDK path is selected and dropdown items are empty`() {
    val jdkComboBox = GradleJdkPathEditComboBox(emptyList(), null, "")
    assertEmpty(jdkComboBox.selectedJdkPath)
    assertEquals(0, jdkComboBox.itemCount)
  }

  fun `test Given no selection and suggested JDKs When create component Then no JDK path is selected and dropdown items are not empty`() {
    val itemValidJdk = LabelAndFileForLocation("valid", EmbeddedDistributionPaths.getInstance().embeddedJdkPath)
    val itemInvalidJdk = LabelAndFileForLocation("invalid", Path("/invalid/jdk/path"))
    val jdkComboBox = GradleJdkPathEditComboBox(listOf(itemValidJdk, itemInvalidJdk), null, "")
    assertEmpty(jdkComboBox.selectedJdkPath)
    assertEquals(2, jdkComboBox.itemCount)
  }

  fun `test Given initial selection When create component Then specified JDK path is selected`() {
    val currentJdkPath = "/jdk/path"
    val jdkComboBox = GradleJdkPathEditComboBox(emptyList(), currentJdkPath, "")
    assertEquals(currentJdkPath, jdkComboBox.selectedJdkPath)
    assertFalse(jdkComboBox.isModified)
  }

  fun `test Given comboBox When select JDK path and reset selection Then selected JDK path is consistent`() {
    val currentJdkPath = "/jdk/path"
    val jdkComboBox = GradleJdkPathEditComboBox(emptyList(), currentJdkPath, "")

    val differentSelectionJdkPath = "/another/jdk/path"
    jdkComboBox.selectedItem = differentSelectionJdkPath
    assertEquals(differentSelectionJdkPath, jdkComboBox.selectedJdkPath)
    assertTrue(jdkComboBox.isModified)

    jdkComboBox.resetSelection()
    assertEquals(currentJdkPath, jdkComboBox.selectedJdkPath)
    assertFalse(jdkComboBox.isModified)
  }

  fun `test Given comboBox When select valid or invalid JDK path Then foreground change accordingly`() {
    val itemValidJdk = LabelAndFileForLocation("valid", EmbeddedDistributionPaths.getInstance().embeddedJdkPath)
    val itemInvalidJdk = LabelAndFileForLocation("invalid", Path("/invalid/jdk/path"))
    val jdkComboBox = GradleJdkPathEditComboBox(listOf(itemValidJdk, itemInvalidJdk), null, "")
    val jdkEditor = jdkComboBox.editor.editorComponent
    jdkComboBox.selectedItem = null
    assertEquals(JBColor.red, jdkEditor.foreground)
    jdkComboBox.selectedItem = itemValidJdk
    assertEquals(JBColor.black, jdkEditor.foreground)
    jdkComboBox.selectedItem = itemInvalidJdk
    assertEquals(JBColor.red, jdkEditor.foreground)
  }

  fun `test Given list of suggested JDKs When create component Then dropdown contains all items`() {
    val items = listOf(
      LabelAndFileForLocation("label1", Path("path1")),
      LabelAndFileForLocation("label2", Path("path2")),
      LabelAndFileForLocation("label3", Path("path3")),
      LabelAndFileForLocation("label4", Path("path4"))
    )
    val jdkComboBox = GradleJdkPathEditComboBox(items, null, "")
    assertEquals(items.size, jdkComboBox.itemCount)
    items.forEachIndexed { index, labelAndFileForLocation ->
      assertEquals(labelAndFileForLocation, jdkComboBox.getItemAt(index))
    }
  }

  fun `test Given comboBox Then was configured with expected settings`() {
    val itemValidJdk = LabelAndFileForLocation("valid", EmbeddedDistributionPaths.getInstance().embeddedJdkPath)
    val jdkComboBox = GradleJdkPathEditComboBox(listOf(itemValidJdk), null, "")
    val jdkExtendableText = jdkComboBox.editor.editorComponent as ExtendableTextField
    assertEquals(1, jdkExtendableText.extensions.size)

    jdkExtendableText.extensions.first().run {
      assertEquals(AllIcons.General.OpenDisk, getIcon(false))
      assertEquals(AllIcons.General.OpenDiskHover, getIcon(true))
      assertEquals(AndroidBundle.message("gradle.settings.jdk.browse.button.tooltip.text"), tooltip)
    }
  }

  fun `test Given comboBox When select a browsed JDK Then browsed JDK path is selected`() {
    val jdkComboBox = GradleJdkPathEditComboBox(emptyList(), null, "")
    val jdkExtendableText = jdkComboBox.editor.editorComponent as ExtendableTextField
    jdkExtendableText.extensions.first().run {
      val captor: ArgumentCaptor<Consumer<String>> = argumentCaptor()
      mockStatic<SdkConfigurationUtil>().use {
        getActionOnClick(mock()).run()

        it.verify { SdkConfigurationUtil.selectSdkHome(eq(JavaSdk.getInstance()), captor.capture()) }
        captor.value.consume("/selected/jdk/path")
        assertEquals("/selected/jdk/path", jdkComboBox.selectedJdkPath)
      }
    }
  }
}