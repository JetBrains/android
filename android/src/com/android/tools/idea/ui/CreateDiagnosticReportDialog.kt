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

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.actions.ShowDiagnosticReportAction
import com.android.tools.idea.diagnostics.report.FileInfo
import com.android.tools.idea.util.ZipData
import com.android.tools.idea.util.zipFiles
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.CreateDiagnosticReportAction
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.RevealFileAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.components.BrowserLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeSelectionModel
import kotlin.io.path.name

private const val PRIVACY_TEXT_1 =
  "Some account and system information may be sent to Google. We will use the information you give us to help address technical issues " +
  "and to improve our services,"

private const val PRIVACY_TEXT_2 =
  "subject to our privacy policy and terms of service."

private const val PRIVACY_HYPERLINK = "http://www.google.com/policies/privacy/"
private const val TOS_HYPERLINK = "http://www.google.com/policies/terms/"
private const val TITLE = "Collect Logs and Diagnostics Data"

/**
 * CreateDiagnosticReportDialog displays a tree view of files to be included in a diagnostic zip file, as well as a preview pane
 * to show the contents of each individual file
 *
 * @param project the currently active project
 * @param files a list of source and destination pairs for the final zip file
 */
class CreateDiagnosticReportDialog(private val project: Project?, files: List<FileInfo>) : DialogWrapper(project) {
  private val fileTree: Tree
  private val grid = JPanel(GridBagLayout())
  private val contents: JBTextArea
  private val checkBox: JBCheckBox

  // Privacy requires that the 'Create' button has nothing that gives it precedence over the cancel button.
  // Make a custom OK action as the default OK button will be colored blue instead of grey.
  private val createAction = object : DialogWrapperAction("Create") {
    override fun doAction(e: ActionEvent) {
      doOKAction()
    }
  }.apply {
    isEnabled = false
  }

  override fun createActions(): Array<Action> {
    return arrayOf(createAction, myCancelAction)
  }


  init {
    title = TITLE
    isResizable = false
    isModal = true

    grid.apply {
      val filesLabel = JLabel().apply {
        text = "Files to include:"
      }

      val constraints = GridBagConstraints().apply {
        gridx = 0
        gridy = 0
        anchor = GridBagConstraints.NORTHWEST
        insets = JBUI.insets(10, 20, 0, 0)
      }

      add(filesLabel, constraints)

      fileTree = buildTree(files)

      val treeScrollPane = JScrollPane(fileTree).apply {
        preferredSize = JBDimension(300, 300)
        verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
      }

      constraints.apply {
        gridx = 0
        gridy = 1
      }

      add(treeScrollPane, constraints)

      contents = JBTextArea().apply {
        isEditable = false
        border = fileTree.border
        addFocusListener(object : FocusAdapter() {
          override fun focusGained(e: FocusEvent?) {
            caret.isVisible = true
          }
        })
      }

      val contentsScrollPane = JScrollPane(contents).apply {
        preferredSize = JBDimension(800, 300)
      }

      constraints.apply {
        gridx = 1
        gridy = 1
      }

      add(contentsScrollPane, constraints)

      val privacy1 = JLabel().apply {
        text = PRIVACY_TEXT_1
      }

      constraints.apply {
        gridx = 0
        gridy = 2
        gridwidth = 2
      }

      add(privacy1, constraints)

      val privacy2 = JLabel().apply {
        text = PRIVACY_TEXT_2
      }

      val oldInsets = constraints.insets
      constraints.apply {
        gridy = 3
        insets = JBUI.insets(insets.top - 10, insets.left, insets.bottom, insets.right)
      }

      add(privacy2, constraints)

      val privacyLink = BrowserLink("Privacy Policy", PRIVACY_HYPERLINK)

      constraints.apply {
        gridy = 4
        insets = oldInsets
      }

      add(privacyLink, constraints)

      val termsOfServiceLink = BrowserLink("Terms of Service", TOS_HYPERLINK)

      constraints.apply {
        gridy = 5
      }

      add(termsOfServiceLink, constraints)

      checkBox = JBCheckBox().apply {
        text = "I agree to the terms above."
        addItemListener { _ ->
          createAction.isEnabled = isSelected
        }
      }

      constraints.apply {
        gridx = 0
        gridy = 6
        gridwidth = 1
      }

      add(checkBox, constraints)
    }

    updateContents(null)

    init()
  }

  override fun createCenterPanel(): JComponent = grid

  override fun doOKAction() {
    if (!visitAllNodes(fileTree.model.root as FileTreeNode).any { it.isChecked }) {
      Messages.showErrorDialog(project, "No files are currently selected.", TITLE)
      return
    }

    val saveFile = getSaveFile(project) ?: return
    createZipFile(saveFile)
    showNotification(saveFile)
    log(CreateDiagnosticReportAction.ActionType.CREATED)

    super.doOKAction()
  }

  override fun doCancelAction() {
    log(CreateDiagnosticReportAction.ActionType.CANCELLED)
    super.doCancelAction()
  }

  private fun buildTree(list: List<FileInfo>): Tree {
    val root = FileTreeNode("Attached Files")

    for (file in list.sortedBy { it.destination.toString() }) {
      addFilesToTree(root, file)
    }

    return CheckboxTree(FileTreeRenderer(), root).apply {
      setSelectionRow(0)
      selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
      addTreeSelectionListener { selectionEvent ->
        val node = selectionEvent.newLeadSelectionPath?.lastPathComponent as? FileTreeNode
        updateContents(node)
      }
    }
  }

  private fun getSaveFile(project: Project?): Path? {
    val descriptor = FileSaverDescriptor("Save Diagnostic Report As", "Choose a location for saving the diagnostic report", "zip")
    val saveFileDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)

    val dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
    val file = "DiagnosticsReport${dateTime}.zip"

    return saveFileDialog.save(VfsUtil.getUserHomeDir(), file)?.file?.toPath()
  }

  private fun createZipFile(path: Path) {
    val list = buildList()
    val zipInfo = list.map { ZipData(it.source.toString(), it.destination.toString()) }.toTypedArray()
    zipFiles(zipInfo, path.toString())
  }

  private fun updateContents(node: FileTreeNode?) {
    contents.text = node?.fileInfo?.let {
      try {
        Files.readString(it.source)
      }
      catch (e: IOException) {
        "Unable to load file contents"
      }
    } ?: "Select a file to preview its contents"

    contents.select(0, 0)
    contents.caretPosition = 0
  }

  private fun addFilesToTree(root: DefaultMutableTreeNode, file: FileInfo) {
    val tokens = file.destination.toList().map { it.name }
    var current = root
    for (i in 0..tokens.size - 2) {
      val token = tokens[i]
      if (current.childCount > 0) {
        val lastChild = current.lastChild as DefaultMutableTreeNode
        if (lastChild.userObject as String == token) {
          current = lastChild
          continue
        }
      }
      val newNode = FileTreeNode(token)
      current.add(newNode)
      current = newNode
    }

    current.add(FileTreeNode(tokens.last(), file))
  }

  private fun buildList(): List<FileInfo> {
    val list = mutableListOf<FileInfo>()

    for (node in visitAllNodes(fileTree.model.root as FileTreeNode)) {
      if (node.isChecked) {
        node.fileInfo?.let { list.add(it) }
      }
    }

    return list
  }

  private fun visitAllNodes(current: FileTreeNode): Sequence<FileTreeNode> = sequence {
    yield(current)
    for (child in current.children()) {
      for (node in visitAllNodes(child as FileTreeNode)) {
        yield(node)
      }
    }
  }

  private fun showNotification(path: Path) {
    if (!RevealFileAction.isSupported()) {
      return
    }

    val notificationGroup =
      NotificationGroupManager.getInstance().getNotificationGroup("Create Diagnostic Report") ?: return

    val notification =
      notificationGroup.createNotification(
        TITLE,
        "The diagnostic report has been created.",
        NotificationType.INFORMATION
      )

    notification.addAction(ShowDiagnosticReportAction(path.toFile()))

    ApplicationManager.getApplication().invokeLater { Notifications.Bus.notify(notification) }
  }

  private fun log(type: CreateDiagnosticReportAction.ActionType) {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder().apply {
        kind = AndroidStudioEvent.EventKind.CREATE_DIAGNOSTIC_REPORT_ACTION
        createDiagnosticReportActionEvent = CreateDiagnosticReportAction.newBuilder().apply {
          actionType = type
        }.build()
      })
  }

  private class FileTreeRenderer : CheckboxTree.CheckboxTreeCellRenderer() {
    override fun customizeRenderer(tree: JTree?,
                                   value: Any?,
                                   selected: Boolean,
                                   expanded: Boolean,
                                   leaf: Boolean,
                                   row: Int,
                                   hasFocus: Boolean) {
      val node = value as? FileTreeNode
      node?.let {
        textRenderer.append(it.userObject as String)
        textRenderer.icon = when {
          (it.fileInfo == null) -> AllIcons.Nodes.Folder
          else -> AllIcons.FileTypes.Text
        }
      }

      super.customizeRenderer(tree, value, selected, expanded, leaf, row, hasFocus)
    }
  }

  class FileTreeNode(userObject: String, val fileInfo: FileInfo? = null) : CheckedTreeNode(userObject)
}
