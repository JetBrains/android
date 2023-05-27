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
package com.android.tools.idea.ui

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.HeadlessDialogRule
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.idea.diagnostics.report.FileInfo
import com.android.utils.FileUtils
import com.google.common.truth.Truth
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.FileSaverDialog
import com.intellij.openapi.fileChooser.impl.FileChooserFactoryImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.components.JBCheckBox
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipFile
import javax.swing.JButton
import javax.swing.JScrollPane
import javax.swing.tree.TreeNode

private const val ZIP_FILE_NAME = "DiagnosticReport.zip"
private const val CREATE_BUTTON_TEXT = "Create"

private val path1 = Paths.get("A").resolve(Paths.get("file1.txt"))
private val path2 = Paths.get("A").resolve(Paths.get("B")).resolve(Paths.get("file2.txt"))
private val path3 = Paths.get("C").resolve(Paths.get("file3.txt"))

@RunsInEdt
class CreateDiagnosticReportDialogTest {
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()
  private lateinit var testDirectoryPath: Path
  private lateinit var files: List<FileInfo>
  private lateinit var zipFile: File
  private val dialog by lazy { CreateDiagnosticReportDialog(projectRule.project, files) }

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule(), HeadlessDialogRule(), disposableRule)

  @Before
  fun setUp() {
    testDirectoryPath = FileUtil.createTempDirectory("CreateDiagnosticReportDialogTest", null).toPath()

    val range = (1..3).toList()

    val filePaths = range.map {
      testDirectoryPath.resolve("file$it.txt")
    }

    for ((i, filePath) in filePaths.withIndex()) {
      filePath.toFile().writeText("File contents $i")
    }

    val directoryPaths = arrayOf(path1, path2, path3)
    files = range.map { FileInfo(filePaths[it - 1], directoryPaths[it - 1]) }

    zipFile = testDirectoryPath.resolve(Paths.get(ZIP_FILE_NAME)).toFile()

    // Override the file choose factory application service. The default file saver
    // dialog does not support being run in headless mode and throws an exception.
    val factory: FileChooserFactoryImpl = object : FileChooserFactoryImpl() {
      override fun createSaveFileDialog(descriptor: FileSaverDescriptor, project: Project?): FileSaverDialog {
        return object : FileSaverDialog {
          override fun save(baseDir: VirtualFile?, filename: String?) = VirtualFileWrapper(zipFile)
          override fun save(baseDir: Path?, filename: String?) = VirtualFileWrapper(zipFile)
        }
      }
    }

    ApplicationManager.getApplication()
      .replaceService(FileChooserFactory::class.java, factory, disposableRule.disposable)
  }

  @After
  fun tearDown() {
    FileUtils.deleteRecursivelyIfExists(testDirectoryPath.toFile())
  }

  @Test
  fun `Test the contents of the file content tree`() {
    createModalDialogAndInteractWithIt(dialog::show) {
      val root = getTreeRoot(it)

      val directoryA = root.getChildAt(0)
      assertNode(directoryA, "A")

      val directoryB = directoryA.getChildAt(0)
      assertNode(directoryB, "B")

      val directoryC = root.getChildAt(1)
      assertNode(directoryC, "C")

      val file1 = directoryA.getChildAt(1)
      assertNode(file1, "file1.txt")

      val file2 = directoryB.getChildAt(0)
      assertNode(file2, "file2.txt")

      val file3 = directoryC.getChildAt(0)
      assertNode(file3, "file3.txt")
    }
  }

  @Test
  fun `Test the contents of the saved zip file`() {
    createModalDialogAndInteractWithIt(dialog::show) {
      val root = getTreeRoot(it)
      Truth.assertThat(root.childCount).isEqualTo(2)

      val directoryA = root.getChildAt(1)
      val file3 = directoryA.getChildAt(0)
      (file3 as CheckedTreeNode).isChecked = false

      selectCheckBox()
      clickCreateButton()

      ZipFile(zipFile).use { zipFile ->
        val entryList = zipFile.entries().toList()
        Truth.assertThat(entryList.size).isEqualTo(2)
        Truth.assertThat(entryList[0].name).isEqualTo(path2.toString())
        Truth.assertThat(entryList[1].name).isEqualTo(path1.toString())
      }
    }
  }

  private fun getTreeRoot(dialog: DialogWrapper): CheckedTreeNode {
    val scrollPanes = TreeWalker(dialog.rootPane).descendants().filterIsInstance<JScrollPane>()
    val tree = scrollPanes.map { it.viewport.view }.filterIsInstance<CheckboxTree>().firstOrNull()
    Truth.assertThat(tree).isNotNull()

    val root = tree!!.model.root as? CheckedTreeNode
    Truth.assertThat(root).isNotNull()

    return root!!
  }

  private fun selectCheckBox() {
    val checkBox = TreeWalker(dialog.rootPane).descendants().filterIsInstance<JBCheckBox>().firstOrNull()
    Truth.assertThat(checkBox).isNotNull()
    checkBox!!.isSelected = true
  }

  private fun clickCreateButton() {
    val buttons = TreeWalker(dialog.rootPane).descendants().filterIsInstance<JButton>()
    val okButton = buttons.firstOrNull { button -> button.text == CREATE_BUTTON_TEXT }
    Truth.assertThat(okButton).isNotNull()
    okButton!!.doClick()
  }

  private fun assertNode(node: TreeNode, userObject: String) {
    Truth.assertThat(node is CheckedTreeNode).isTrue()
    val checkedTreeNode = node as CheckedTreeNode

    Truth.assertThat(checkedTreeNode.userObject).isEqualTo(userObject)
    Truth.assertThat(checkedTreeNode.isChecked).isTrue()
  }
}