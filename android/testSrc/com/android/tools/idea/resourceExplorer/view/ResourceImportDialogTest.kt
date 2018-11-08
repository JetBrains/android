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
package com.android.tools.idea.resourceExplorer.view

import com.android.ide.common.resources.configuration.DensityQualifier
import com.android.ide.common.resources.configuration.NightModeQualifier
import com.android.resources.Density
import com.android.resources.NightMode
import com.android.tools.idea.resourceExplorer.getTestDataDirectory
import com.android.tools.idea.resourceExplorer.importer.QualifierMatcher
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.android.tools.idea.resourceExplorer.model.StaticStringMapper
import com.android.tools.idea.resourceExplorer.model.getAssetSets
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.intellij.ide.ui.laf.darcula.DarculaLaf
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.UIUtil
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description
import java.io.File
import javax.swing.JComboBox
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.UIManager
import kotlin.test.assertEquals

class ResourceImportDialogTest {

  @get:Rule
  val rule = AndroidProjectRule.onDisk()

  private lateinit var resourceImportDialog: ResourceImportDialog

  @Before
  fun setUp() {
    rule.fixture.testDataPath = getTestDataDirectory() + "/designAssets"
    resourceImportDialog = runInEdtAndGet { ResourceImportDialog(rule.module.androidFacet!!, getAssets(rule.fixture.testDataPath)) }
  }

  @Test
  fun checkDialogCorrectlyPopulated() {
    val content = resourceImportDialog.root.viewport.view as JPanel
    val fileRows = UIUtil.findComponentsOfType(content, FileImportRow::class.java)
    assertEquals(3, fileRows.size)

    val row0 = fileRows[0]
    val combos = UIUtil.findComponentsOfType(row0, JComboBox::class.java)
    assertEquals(2, combos.size)
    assertEquals(DensityQualifier::class, (combos[0].selectedItem::class))
    assertEquals(Density.XHIGH, combos[1].selectedItem)

    val row1 = fileRows[1]
    val combos1 = UIUtil.findComponentsOfType(row1, JComboBox::class.java)
    assertEquals(4, combos1.size)
    assertEquals(NightModeQualifier::class, (combos1[0].selectedItem::class))
    assertEquals(NightMode.NIGHT, combos1[1].selectedItem)
    assertEquals(DensityQualifier::class, (combos1[2].selectedItem::class))
    assertEquals(Density.MEDIUM, combos1[3].selectedItem)
  }

  @After
  fun tearDown() {
    runInEdtAndWait {
      Disposer.dispose(resourceImportDialog.disposable)
    }
  }
}


fun getAssets(path: String): List<DesignAssetSet> {
  val mappers = setOf(
    StaticStringMapper(mapOf(
      "@2x" to DensityQualifier(Density.XHIGH),
      "@3x" to DensityQualifier(Density.XXHIGH),
      "" to DensityQualifier(Density.MEDIUM))),
    StaticStringMapper(mapOf(
      "_dark" to NightModeQualifier(NightMode.NIGHT)
    )))
  val qualifierMatcher = QualifierMatcher(mappers)
  return getAssetSets(VfsUtil.findFileByIoFile(File(path), true)!!, setOf("png"), qualifierMatcher)
}

private val staticRule = AndroidProjectRule.onDisk()

fun main(vararg args: String) {
  staticRule.before(Description.createSuiteDescription(ResourceImportDialogTest::class.java))
  staticRule.fixture.testDataPath = getTestDataDirectory() + "/designAssets"
  runInEdt {
    UIManager.setLookAndFeel(DarculaLaf())
    JFrame().apply {
      contentPane = ResourceImportDialog(staticRule.module.androidFacet!!, getAssets(staticRule.fixture.testDataPath)).root
      pack()
      isVisible = true
    }
  }
}