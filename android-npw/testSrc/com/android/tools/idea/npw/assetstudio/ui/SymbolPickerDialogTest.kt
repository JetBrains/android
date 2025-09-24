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
package com.android.tools.idea.npw.assetstudio.ui

import com.android.tools.idea.material.icons.common.MaterialIconsMetadataUrlProvider
import com.android.tools.idea.material.icons.common.MaterialSymbolsUrlProvider
import com.android.tools.idea.material.icons.common.Symbols
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.table.JBTable
import com.intellij.util.WaitFor
import com.intellij.util.io.createDirectories
import com.intellij.util.ui.UIUtil
import java.io.File
import java.net.URL
import java.nio.file.Path
import java.util.Objects
import javax.swing.JComboBox
import javax.swing.JTable
import junit.framework.TestCase
import kotlin.io.path.createFile
import kotlin.io.path.createTempDirectory
import kotlin.io.path.pathString
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class SymbolPickerDialogTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testCategoriesBoxPopulated() =
    runBlocking(Dispatchers.Main) {
      val testDirectory = createTempDirectory()
      val symbolsPicker =
        getInitializedIconPickerDialog(
          SymbolPickerDialog(
            projectRule.fixture.module.androidFacet!!,
            projectRule.fixture.testRootDisposable,
            TestSymbolsUrlProvider(testDirectory),
            TestSymbolsMetadataUrlProvider,
          )
        )

      UIUtil.findComponentsOfType(symbolsPicker.createCenterPanel(), JComboBox::class.java)
        .forEach { box ->
          if (Objects.requireNonNull(box.selectedItem).toString() == "All") {
            box.selectedIndex = 1
            assertEquals("Category1", box.selectedItem?.toString())
            box.selectedIndex = 2
            assertEquals("Category2", box.selectedItem?.toString())
            box.selectedIndex = 3
            assertEquals("Category3", box.selectedItem?.toString())
          }
        }
    }

  @Test
  fun testStylesBoxPopulated() =
    runBlocking(Dispatchers.Main) {
      val testDirectory = createTempDirectory()
      val symbolsPicker =
        getInitializedIconPickerDialog(
          SymbolPickerDialog(
            projectRule.fixture.module.androidFacet!!,
            projectRule.fixture.testRootDisposable,
            TestSymbolsUrlProvider(testDirectory),
            TestSymbolsMetadataUrlProvider,
          )
        )

      UIUtil.findComponentsOfType(symbolsPicker.createCenterPanel(), JComboBox::class.java)
        .forEach { box ->
          if (Objects.requireNonNull(box.selectedItem).toString() == "Material Symbols Outlined") {
            box.selectedIndex = 1
            assertEquals("Material Symbols Rounded", box.selectedItem?.toString())
            box.selectedIndex = 2
            assertEquals("Material Symbols Sharp", box.selectedItem?.toString())
          }
        }
    }

  @Test
  fun testTablePopulated() =
    runBlocking(Dispatchers.Main) {
      val testDirectory = createTempDirectory()
      val symbolsPicker =
        getInitializedIconPickerDialog(
          SymbolPickerDialog(
            projectRule.fixture.module.androidFacet!!,
            projectRule.fixture.testRootDisposable,
            TestSymbolsUrlProvider(testDirectory),
            TestSymbolsMetadataUrlProvider,
          )
        )

      UIUtil.findComponentOfType(symbolsPicker.createCenterPanel(), JBTable::class.java).let { table
        ->
        assertNotNull(table)
        assertNotNull(table.getValueAt(0, 0))
        assertNotNull(table.getValueAt(0, 1))
        assertNull(table.getValueAt(0, 2))
      }
    }

  @Test
  fun testFiltering() =
    runBlocking(Dispatchers.Main) {
      val testDirectory = createTempDirectory()
      val symbolsPicker =
        getInitializedIconPickerDialog(
          SymbolPickerDialog(
            projectRule.fixture.module.androidFacet!!,
            projectRule.fixture.testRootDisposable,
            TestSymbolsUrlProvider(testDirectory),
            TestSymbolsMetadataUrlProvider,
          )
        )

      UIUtil.findComponentsOfType(symbolsPicker.createCenterPanel(), JComboBox::class.java)
        .forEach { box ->
          if (Objects.requireNonNull(box.selectedItem).toString() == "All") {
            box.selectedIndex = 2
            assertEquals("Category2", box.selectedItem?.toString())
          }
        }

      UIUtil.findComponentOfType(symbolsPicker.createCenterPanel(), JBTable::class.java).let { table
        ->
        assertNotNull(table)
        assertNotNull(table.getValueAt(0, 0))
        assertNull(table.getValueAt(0, 1))
      }
    }

  private fun getInitializedIconPickerDialog(dialog: SymbolPickerDialog): SymbolPickerDialog {
    val pickerPanel = dialog.createCenterPanel()
    pickerPanel.isVisible = true

    // The icons table is initialized asynchronously, so before doing any tests, lets wait for the
    // table to get populated.
    val wait: WaitFor =
      object : WaitFor(3000) {
        override fun condition(): Boolean {
          // Dispatch pending EDT tasks, do not block the thread while waiting.
          PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
          val table = UIUtil.findComponentOfType(pickerPanel, JTable::class.java)
          val box = UIUtil.findComponentOfType(pickerPanel, JComboBox::class.java)
          val populatedTable = table != null && table.getValueAt(0, 0) != null
          val populatedComboBox = box != null && box.isEnabled
          return populatedComboBox && populatedTable && !dialog.isBusy()
        }
      }
    TestCase.assertTrue(wait.isConditionRealized)
    return dialog
  }
}

private class TestSymbolsUrlProvider(private val testDirectory: Path) : MaterialSymbolsUrlProvider {

  private val testLocalFontName = "variablefont/"

  init {
    val testFontDirectory = testDirectory.resolve(testLocalFontName).apply { createDirectories() }

    for (symbolStyle in Symbols.entries) {
      testFontDirectory
        .resolve(symbolStyle.localName)
        .createDirectories()
        .resolve(symbolStyle.localName + ".ttf")
        .createFile()
        .writeText("test")
    }
  }

  override fun getRemoteFontUrl(type: Symbols): URL {
    val urlString = "file:/" + testDirectory.pathString + "remote/" + type.localName
    return URL(urlString)
  }

  override fun getLocalFontDirectoryFile(type: Symbols): File? {
    val directoryName = type.localName
    val fontDirectoryPath = testDirectory.resolve(testLocalFontName + directoryName) ?: return null
    return fontDirectoryPath.toFile()
  }

  override fun getLocalFontFile(type: Symbols): File? {
    val fileName = type.localName + ".ttf"
    val fontFilePath = getLocalFontDirectoryFile(type)?.resolve(fileName) ?: return null
    return fontFilePath
  }

  override fun getLocalSymbolsPath(): File? {
    return testDirectory.toFile()
  }

  override fun hasFontPathInSdk(type: Symbols): Boolean = true
}

private object TestSymbolsMetadataUrlProvider : MaterialIconsMetadataUrlProvider {

  override fun getMetadataUrl(): URL? {
    return IconPickerDialogTest::class
      .java
      .getClassLoader()
      .getResource("images/material/icons/icons_metadata_test.txt")
  }
}
