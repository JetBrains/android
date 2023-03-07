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
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Desktop
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.event.HyperlinkEvent
import javax.swing.tree.DefaultMutableTreeNode
import kotlin.io.path.name

private const val PRIVACY_TEXT =
  "Some account and system information may be sent to Google. We will use the information you give us to help address technical issues " +
  "and to improve our services, subject to our <a href='http://www.google.com/policies/privacy/'>Privacy Policy</a> and " +
  "<a href='http://www.google.com/policies/terms/'>Terms of Service</a>."

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
    title = "Create Diagnostic Report"
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
      fileTree.preferredSize = null

      val treeScrollPane = JScrollPane(fileTree).apply {
        preferredSize = Dimension(300, 300)
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
      }

      val contentsScrollPane = JScrollPane(contents).apply {
        preferredSize = Dimension(800, 300)
      }

      constraints.apply {
        gridx = 1
        gridy = 1
      }

      add(contentsScrollPane, constraints)

      val privacy = JEditorPane("text/html", PRIVACY_TEXT).apply {
        isEditable = false
        background = JBColor(UIUtil.TRANSPARENT_COLOR, UIUtil.TRANSPARENT_COLOR)
        preferredSize = Dimension(1100, 40)
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)

        addHyperlinkListener { e ->
          if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
            if (Desktop.isDesktopSupported()) {
              Desktop.getDesktop().browse(e.url.toURI());
            }
          }
        }
      }

      constraints.apply {
        gridx = 0
        gridy = 2
        gridwidth = 2
      }

      add(privacy, constraints)

      checkBox = JBCheckBox().apply {
        text = "I agree to the terms above."
        addItemListener { _ ->
          createAction.isEnabled = isSelected
        }
      }

      constraints.apply {
        gridx = 0
        gridy = 3
        gridwidth = 1
      }

      add(checkBox, constraints)
    }

    init()
  }

  override fun createCenterPanel(): JComponent = grid

  override fun doOKAction() {
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
      preferredSize = Dimension(300, 200)
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
        null
      }
    } ?: ""

    contents.select(0, 0)

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
    val root = fileTree.model.root as FileTreeNode
    val list = mutableListOf<FileInfo>()
    addFilesToList(root, list)
    return list
  }

  private fun addFilesToList(node: FileTreeNode, list: MutableList<FileInfo>) {
    if (node.isChecked) {
      node.fileInfo?.let { list.add(it) }
    }

    for (child in node.children()) {
      addFilesToList(child as FileTreeNode, list)
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
        "Diagnostic report created",
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
