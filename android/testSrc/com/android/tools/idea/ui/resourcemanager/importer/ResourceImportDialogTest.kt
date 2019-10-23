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
package com.android.tools.idea.ui.resourcemanager.importer

import com.android.ide.common.resources.configuration.DensityQualifier
import com.android.ide.common.resources.configuration.NightModeQualifier
import com.android.resources.Density
import com.android.resources.NightMode
import com.android.tools.idea.ui.resourcemanager.getTestDataDirectory
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.StaticStringMapper
import com.android.tools.idea.ui.resourcemanager.model.getDesignAssets
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.intellij.ide.ui.laf.darcula.DarculaLaf
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.UIUtil.findComponentsOfType
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description
import java.io.File
import javax.swing.JComboBox
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.UIManager
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ResourceImportDialogTest {

  @get:Rule
  val rule = AndroidProjectRule.onDisk()

  private lateinit var resourceImportDialog: ResourceImportDialog
  private lateinit var dialogViewModel: ResourceImportDialogViewModel

  @Before
  fun setUp() {
    rule.fixture.testDataPath = getTestDataDirectory() + "/designAssets"
    dialogViewModel = ResourceImportDialogViewModel(rule.module.androidFacet!!,
                                                    getAssets(
                                                      rule.fixture.testDataPath).asSequence())
    resourceImportDialog = runInEdtAndGet {
      ResourceImportDialog(dialogViewModel)
    }
  }

  @Test
  fun checkDialogCorrectlyPopulated() {
    val content = resourceImportDialog.root.viewport.view as JPanel
    val fileRows = findComponentsOfType(content, FileImportRow::class.java)
    assertEquals(3, fileRows.size)

    val row0 = fileRows[0]
    val combos = findComponentsOfType(row0, JComboBox::class.java)
    assertEquals(2, combos.size)
    assertEquals(DensityQualifier::class, (combos[0].selectedItem::class))
    assertEquals(Density.XHIGH, combos[1].selectedItem)

    val row1 = fileRows[1]
    val combos1 = findComponentsOfType(row1, JComboBox::class.java)
    assertEquals(4, combos1.size)
    assertEquals(NightModeQualifier::class, (combos1[0].selectedItem::class))
    assertEquals(NightMode.NIGHT, combos1[1].selectedItem)
    assertEquals(DensityQualifier::class, (combos1[2].selectedItem::class))
    assertEquals(Density.MEDIUM, combos1[3].selectedItem)
  }

  @Test
  fun removeAssets() {
    val content = resourceImportDialog.root.viewport.view as JPanel
    val fileRows = findComponentsOfType(content, FileImportRow::class.java)
    val firstAssetSet = dialogViewModel.assetSets.first()
    val firstAsset = firstAssetSet.designAssets.first()

    val row0 = fileRows[0]
    val parent = row0.parent as JPanel

    // Click the remove button on the fist asset to be imported.
    val removeLabel = findComponentsOfType(row0, LinkLabel::class.java).first { it.text.equals("Do not import", true) }
    removeLabel.doClick()

    // Check that the view has been removed and that the asset has been removed from the model.
    assertFalse(parent.components.contains(row0))
    assertFalse(dialogViewModel.assetSets.first().designAssets.contains(firstAsset))

    // Check that a label with the assetSet name is still present.
    assertEquals(1, findComponentsOfType(content, JBTextField::class.java).filter { it.text.equals(firstAssetSet.name, true) }.size)

    // Click all remove button for the reset of the label.
    var removeLabel1: LinkLabel<*>?
    do {
      removeLabel1 = findComponentsOfType(parent, LinkLabel::class.java).firstOrNull { it.text.equals("Do not import", true) }
      removeLabel1?.doClick()
    }
    while (removeLabel1 != null)

    // Check that the DesignAssetSet has been removed from the model.
    assertFalse(dialogViewModel.assetSets.contains(firstAssetSet))

    // Check that the DesignAssetSet name is not present anymore.
    assertNull(findComponentsOfType(content, JLabel::class.java).firstOrNull { it.text.equals(firstAssetSet.name, true) })
  }

  @Test
  fun addAssets() {
    val content = resourceImportDialog.root.viewport.view as JPanel
    val fileRows = findComponentsOfType(content, FileImportRow::class.java)
    val firstAssetSet = dialogViewModel.assetSets.first()
    val firstAsset = firstAssetSet.designAssets.first()

    val row0 = fileRows[0]
    val parent = row0.parent as JPanel
    // Click the remove button on the fist asset to be imported.
    val removeLabel = findComponentsOfType(row0, LinkLabel::class.java).first { it.text.equals("Do not import", true) }
    removeLabel.doClick()

    // Check that the view has been removed and that the asset has been removed from the model.
    assertFalse(parent.components.contains(row0))
    assertFalse(dialogViewModel.assetSets.first().designAssets.contains(firstAsset))

    // Check that a label with the assetSet name is still present.
    assertEquals(1, findComponentsOfType(content, JBTextField::class.java).filter { it.text.equals(firstAssetSet.name, true) }.size)

    // Click all remove button for the reset of the label.
    var removeLabel1: LinkLabel<*>?
    do {
      removeLabel1 = findComponentsOfType(parent, LinkLabel::class.java).firstOrNull { it.text.equals("Do not import", true) }
      removeLabel1?.doClick()
    }
    while (removeLabel1 != null)

    // Check that the DesignAssetSet has been removed from the model.
    assertFalse(dialogViewModel.assetSets.contains(firstAssetSet))

    // Check that the DesignAssetSet name is not present anymore.
    assertNull(findComponentsOfType(content, JLabel::class.java).firstOrNull { it.text.equals(firstAssetSet.name, true) })
  }


  @After
  fun tearDown() {
    runInEdtAndWait {
      Disposer.dispose(resourceImportDialog.disposable)
    }
  }
}


fun getAssets(path: String): List<DesignAsset> {
  val mappers = setOf(
    StaticStringMapper(mapOf(
      "@2x" to DensityQualifier(Density.XHIGH),
      "@3x" to DensityQualifier(Density.XXHIGH),
      "" to DensityQualifier(Density.MEDIUM))),
    StaticStringMapper(mapOf(
      "_dark" to NightModeQualifier(NightMode.NIGHT)
    )))
  val qualifierMatcher = QualifierMatcher(mappers)
  val directory = VfsUtil.findFileByIoFile(File(path), true)!!
  return getDesignAssets(directory, setOf("png"), directory, qualifierMatcher)
}

private val staticRule = AndroidProjectRule.onDisk()

fun main(vararg args: String) {
  staticRule.before(Description.createSuiteDescription(
    ResourceImportDialogTest::class.java))
  staticRule.fixture.testDataPath = getTestDataDirectory() + "/designAssets"
  runInEdt {
    UIManager.setLookAndFeel(DarculaLaf())
    JFrame().apply {
      contentPane = ResourceImportDialog(
        ResourceImportDialogViewModel(staticRule.module.androidFacet!!, getAssets(
          staticRule.fixture.testDataPath).asSequence())).root
      pack()
      isVisible = true
    }
  }
}