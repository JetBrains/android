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
import com.android.tools.idea.material.icons.common.SymbolConfiguration
import com.android.tools.idea.material.icons.common.Symbols
import com.android.tools.idea.material.icons.metadata.MaterialMetadataIcon
import com.android.tools.idea.npw.assetstudio.assets.MaterialSymbolsVirtualFile
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.table.JBTable
import com.intellij.util.WaitFor
import com.intellij.util.io.createDirectories
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.io.File
import java.net.URL
import java.nio.file.Path
import java.util.Objects
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JSlider
import javax.swing.JTable
import junit.framework.TestCase
import kotlin.io.path.createFile
import kotlin.io.path.createTempDirectory
import kotlin.io.path.pathString
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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

      UIUtil.findComponentsOfType(symbolsPicker.createCenterPanel(), JComboBox::class.java).forEach { box ->
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

      UIUtil.findComponentsOfType(symbolsPicker.createCenterPanel(), JComboBox::class.java).forEach { box ->
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

      UIUtil.findComponentOfType(symbolsPicker.createCenterPanel(), JBTable::class.java).let { table ->
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

      UIUtil.findComponentsOfType(symbolsPicker.createCenterPanel(), JComboBox::class.java).forEach { box ->
        if (Objects.requireNonNull(box.selectedItem).toString() == "All") {
          box.selectedIndex = 2
          assertEquals("Category2", box.selectedItem?.toString())
        }
      }

      UIUtil.findComponentOfType(symbolsPicker.createCenterPanel(), JBTable::class.java).let { table ->
        assertNotNull(table)
        assertNotNull(table.getValueAt(0, 0))
        assertNull(table.getValueAt(0, 1))
      }
    }

  @Test
  fun testSearch() =
    runBlocking(Dispatchers.Main) {
      val testDirectory = createTempDirectory()
      val dialog =
        SymbolPickerDialog(
          projectRule.fixture.module.androidFacet!!,
          projectRule.fixture.testRootDisposable,
          TestSymbolsUrlProvider(testDirectory),
          TestSymbolsMetadataUrlProvider,
        )
      val symbolsPicker = getInitializedIconPickerDialog(dialog)

      UIUtil.findComponentsOfType(symbolsPicker.createCenterPanel(), SearchTextField::class.java).first().let { searchField ->
        // Trailing characters are ignored
        searchField.text = "  My    "
        assertEquals(listOf("My Icon 1", "My Icon 2"), dialog.getCurrentSymbolNames())

        // One icon found.
        searchField.text = "  1"
        assertEquals(listOf("My Icon 1"), dialog.getCurrentSymbolNames())

        // One icon found.
        searchField.text = "2    "
        assertEquals(listOf("My Icon 2"), dialog.getCurrentSymbolNames())

        // No icons found.
        searchField.text = "Day"
        assertEquals(emptyList<String>(), dialog.getCurrentSymbolNames())

        // Case is ignored.
        searchField.text = "my icon"
        assertEquals(listOf("My Icon 1", "My Icon 2"), dialog.getCurrentSymbolNames())
      }
    }

  @Test
  fun testSearchFieldConfiguredInPanelContext() =
    runBlocking(Dispatchers.Main) {
      HeadlessDataManager.fallbackToProductionDataManager(projectRule.fixture.testRootDisposable)

      val testDirectory = createTempDirectory()
      val dialog =
        getInitializedIconPickerDialog(
          SymbolPickerDialog(
            projectRule.fixture.module.androidFacet!!,
            projectRule.fixture.testRootDisposable,
            TestSymbolsUrlProvider(testDirectory),
            TestSymbolsMetadataUrlProvider,
          )
        )

      val centerPanel = dialog.createCenterPanel()
      val context = DataManager.getInstance().getDataContext(centerPanel)
      val providedField = context.getData(SearchTextField.KEY)

      assertThat(providedField).isNotNull()
    }

  @Test
  fun testRefreshButtonHasTooltip() =
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

      val refreshButton =
        UIUtil.findComponentsOfType(symbolsPicker.createCenterPanel(), JButton::class.java).find { it.icon == AllIcons.General.Refresh }
      assertNotNull(refreshButton)
      assertEquals("Refresh", refreshButton.toolTipText)
    }

  @Test
  fun testResetButtonResetsSliders() =
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

      val centerPanel = symbolsPicker.createCenterPanel()
      val sliders = UIUtil.findComponentsOfType(centerPanel, JSlider::class.java)
      assertEquals(3, sliders.size)
      val weightSlider = sliders[0]
      val gradeSlider = sliders[1]
      val opticalSizeSlider = sliders[2]

      // Initial defaults
      assertEquals(SymbolPickerDialog.DEFAULT_WEIGHT_INDEX, weightSlider.value)
      assertEquals(SymbolPickerDialog.DEFAULT_GRADE_INDEX, gradeSlider.value)
      assertEquals(SymbolPickerDialog.DEFAULT_OPTICAL_SIZE_INDEX, opticalSizeSlider.value)

      // Change values
      weightSlider.value = 0
      gradeSlider.value = 0
      opticalSizeSlider.value = 0
      assertEquals(0, weightSlider.value)
      assertEquals(0, gradeSlider.value)
      assertEquals(0, opticalSizeSlider.value)

      val resetButton = UIUtil.findComponentsOfType(centerPanel, JButton::class.java).find { it.icon == AllIcons.General.Reset }
      assertNotNull(resetButton)
      assertEquals("Reset", resetButton.toolTipText)

      resetButton.doClick()

      assertEquals(SymbolPickerDialog.DEFAULT_WEIGHT_INDEX, weightSlider.value)
      assertEquals(SymbolPickerDialog.DEFAULT_GRADE_INDEX, gradeSlider.value)
      assertEquals(SymbolPickerDialog.DEFAULT_OPTICAL_SIZE_INDEX, opticalSizeSlider.value)
    }

  @Test
  fun testTablePadding() =
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

      val table = UIUtil.findComponentOfType(symbolsPicker.createCenterPanel(), JBTable::class.java)
      assertNotNull(table)
      // ICON_HEIGHT (64) + TEXT_HEIGHT (16) + PADDING_BOTTOM (8) = 88
      assertEquals(JBUI.scale(88), table.rowHeight)
    }

  @Test
  fun testRendererPadding() =
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

      val table = UIUtil.findComponentOfType(symbolsPicker.createCenterPanel(), JBTable::class.java)
      assertNotNull(table)
      val renderer = table.getDefaultRenderer(MaterialSymbolsVirtualFile::class.java)
      val component = renderer.getTableCellRendererComponent(table, table.getValueAt(0, 0), false, false, 0, 0)
      assertThat(component).isInstanceOf(JBLabel::class.java)
      val label = component as JBLabel
      assertEquals(JBUI.scale(8), label.insets.bottom)
    }

  @Test
  fun testRefreshButtonDisabledDuringRefresh() =
    runBlocking(Dispatchers.Main) {
      var isFetching = false
      val slowMetadataUrlProvider =
        object : MaterialIconsMetadataUrlProvider {
          override fun getMetadataUrl(): URL? {
            isFetching = true
            // Delay for a bit to allow us to check the button state
            Thread.sleep(500)
            return IconPickerDialogTest::class.java.getClassLoader().getResource("images/material/icons/icons_metadata_test.txt")
          }
        }

      val testDirectory = createTempDirectory()
      val symbolsPicker =
        getInitializedIconPickerDialog(
          SymbolPickerDialog(
            projectRule.fixture.module.androidFacet!!,
            projectRule.fixture.testRootDisposable,
            TestSymbolsUrlProvider(testDirectory),
            slowMetadataUrlProvider,
          )
        )

      assertThat(symbolsPicker.isRefreshButtonEnabled()).isTrue()

      val refreshButton =
        UIUtil.findComponentsOfType(symbolsPicker.createCenterPanel(), JButton::class.java).find { it.icon == AllIcons.General.Refresh }!!

      // Click the button
      withContext(Dispatchers.EDT) { refreshButton.doClick() }

      // Now it should be fetching and button should be disabled
      val wait: WaitFor =
        object : WaitFor(3000) {
          override fun condition(): Boolean {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            return isFetching && !symbolsPicker.isRefreshButtonEnabled()
          }
        }
      assertTrue(wait.isConditionRealized, "Should be fetching and button should be disabled")

      // Now wait for it to finish, i.e. the button should be enabled again.
      val finishWait: WaitFor =
        object : WaitFor(3000) {
          override fun condition(): Boolean {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            return symbolsPicker.isRefreshButtonEnabled()
          }
        }
      assertTrue(finishWait.isConditionRealized, "Button should be enabled")
    }

  @Test
  fun testMaterialSymbolsVirtualFileContent() {
    val symbolConfiguration = SymbolConfiguration(type = Symbols.OUTLINED, weight = 400, grade = 0, opticalSize = 24, filled = false)
    val metadata =
      MaterialMetadataIcon(
        name = "home",
        version = 1,
        unsupportedFamilies = emptyArray(),
        categories = arrayOf("home"),
        tags = emptyArray(),
        unicode = 0xe88a,
      )
    val fontPath = "/path/to/font.ttf"
    val virtualFile = MaterialSymbolsVirtualFile(symbolConfiguration, metadata, fontPath)

    val content = virtualFile.content.toString()
    assertTrue(content.contains("android:layout_width=\"wrap_content\""))
    assertTrue(content.contains("android:layout_height=\"wrap_content\""))
  }

  @Test
  fun testOkButtonDisabledInitiallyAndEnabledOnSelection() =
    runBlocking(Dispatchers.Main) {
      val testDirectory = createTempDirectory()
      val dialog =
        SymbolPickerDialog(
          projectRule.fixture.module.androidFacet!!,
          projectRule.fixture.testRootDisposable,
          TestSymbolsUrlProvider(testDirectory),
          TestSymbolsMetadataUrlProvider,
          vdIconLoader = { _, _, _, _ -> org.mockito.Mockito.mock(com.android.ide.common.vectordrawable.VdIcon::class.java) },
        )

      try {
        assertThat(dialog.isOKActionEnabled).isFalse()

        // Initialize the dialog (loads icons and populates the table)
        getInitializedIconPickerDialog(dialog)

        // Select an icon in the table
        val table = UIUtil.findComponentOfType(dialog.createCenterPanel(), JBTable::class.java)!!
        table.setRowSelectionInterval(0, 0)
        table.setColumnSelectionInterval(0, 0)

        // Wait for the OK button to be enabled (async icon loading)
        val waitOk =
          object : WaitFor(3000) {
            override fun condition(): Boolean {
              PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
              return dialog.isOKActionEnabled
            }
          }
        assertTrue(waitOk.isConditionRealized)
      } finally {
        dialog.close(0)
      }
    }

  @Test
  fun testOkButtonDoesNotBlinkOnSelectionChange() =
    runBlocking(Dispatchers.Main) {
      val testDirectory = createTempDirectory()
      val dialog =
        SymbolPickerDialog(
          projectRule.fixture.module.androidFacet!!,
          projectRule.fixture.testRootDisposable,
          TestSymbolsUrlProvider(testDirectory),
          TestSymbolsMetadataUrlProvider,
          vdIconLoader = { _, _, _, _ -> org.mockito.Mockito.mock(com.android.ide.common.vectordrawable.VdIcon::class.java) },
        )

      try {
        getInitializedIconPickerDialog(dialog)
        val table = UIUtil.findComponentOfType(dialog.createCenterPanel(), JBTable::class.java)!!

        // Select first icon and wait for OK to be enabled
        table.setRowSelectionInterval(0, 0)
        table.setColumnSelectionInterval(0, 0)
        val waitOk =
          object : WaitFor(3000) {
            override fun condition(): Boolean {
              PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
              return dialog.isOKActionEnabled
            }
          }
        assertTrue(waitOk.isConditionRealized)

        // Select another icon.
        table.setColumnSelectionInterval(1, 1)

        // Verify it remains enabled immediately, i.e. no blinking to false before dispatching events.
        assertTrue(dialog.isOKActionEnabled, "OK button should not be disabled when changing selection")

        // Also check the button remains enabled after dispatching events (for completeness).
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertTrue(dialog.isOKActionEnabled)
      } finally {
        dialog.close(0)
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
    return IconPickerDialogTest::class.java.getClassLoader().getResource("images/material/icons/icons_metadata_test.txt")
  }
}
