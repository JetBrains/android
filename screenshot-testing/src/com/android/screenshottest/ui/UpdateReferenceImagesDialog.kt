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
package com.android.screenshottest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.unit.dp
import com.android.screenshottest.util.ImageData
import com.android.screenshottest.util.copyReferenceImages
import com.android.tools.analytics.UsageTracker
import com.android.tools.analytics.withProjectId
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.ScreenshotViewType
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ScreenshotTestComposePreviewEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckboxTreeListener
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import org.jetbrains.jewel.ui.component.SegmentedControl
import org.jetbrains.jewel.ui.component.SegmentedControlButtonData
import org.jetbrains.jewel.ui.component.Text

/**
 * A dialog for selecting and viewing screenshot test previews. It features a two-pane layout with a
 * tree of previews on the left and a live-updating image viewer on the right.
 */
class UpdateReferenceImagesDialog(
  private val project: Project?,
  private val logger: Logger = Logger.getInstance(UpdateReferenceImagesDialog::class.java)
) : DialogWrapper(project) {

  private val centerPanelCardLayout = CardLayout()
  private val centerPanel = JPanel(centerPanelCardLayout)
  private var isFirstTestDiscovered = false
  private var isTestSuiteFinished = false
  private val successfulLoads = AtomicInteger(0)
  private lateinit var tree: CheckboxTree
  private val placeholderLabel = JBLabel("Select a node from the left to see its previews.", JBLabel.CENTER)
  private val imagePanelMap = mutableMapOf<String, PreviewItemPanel>()
  private val classNodeMap = mutableMapOf<String, CheckedTreeNode>()
  private val methodNodeMap = mutableMapOf<String, MutableMap<String, CheckedTreeNode>>()
  private lateinit var previewToolbar: ComposePanel
  private var selectedViewType by mutableStateOf(ScreenshotViewType.NEW)
  private lateinit var previewDetailsPanel: PreviewDetailsPanel
  private lateinit var rightPaneContent: JPanel
  private lateinit var rightPaneCardLayout: CardLayout
  private var isLeafSelected by mutableStateOf(false)
  private lateinit var rightPaneWrapper: JPanel


  init {
    isModal = false
    title = "Add/Update Reference Images"
    setOKButtonText("Add")
    okAction.isEnabled = false // The "Add" button is disabled until an image loads.
    setCancelButtonText("Cancel")
    isResizable = true
    init()
  }

  override fun getDimensionServiceKey(): String {
    return "com.android.screenshottest.ui.UpdateReferenceImagesDialog"
  }

  fun updateDialogWithTestResult(previewDetails: PreviewDetails, isChecked: Boolean) {
    ApplicationManager.getApplication().invokeLater {
      if (!isFirstTestDiscovered) {
        isFirstTestDiscovered = true
        populateCenterPanel()
      }

      val (testId, className, methodName, previewName, testResult, destImagePath, srcImagePath, diffImagePath, diffPercent) = previewDetails

      if(methodName.isNotBlank() && previewName.isNotBlank()) {

        val root = tree.model.root as CheckedTreeNode
        val model = tree.model as DefaultTreeModel

        val classNode = classNodeMap.getOrPut(className) {
          val newNode = CheckedTreeNode(className.substringAfterLast('.'))
          newNode.isEnabled = true
          model.insertNodeInto(newNode, root, root.childCount)
          tree.expandPath(TreePath(root.path))
          newNode
        }

        val methodMap = methodNodeMap.getOrPut(className) { mutableMapOf() }
        val methodNode = methodMap.getOrPut(methodName) {
          val newNode = CheckedTreeNode(methodName)
          newNode.isEnabled = true
          model.insertNodeInto(newNode, classNode, classNode.childCount)
          tree.expandPath(TreePath(classNode.path))
          newNode
        }

        val leafNode = CheckedTreeNode(previewDetails)
        leafNode.isChecked = isChecked
        model.insertNodeInto(leafNode, methodNode, methodNode.childCount)
        tree.expandPath(TreePath(methodNode.path))

        val panel = PreviewItemPanel(previewData = previewDetails)
        imagePanelMap[testId] = panel

        if (srcImagePath != null) {
          panel.loadImage(srcImagePath, testId)
        }
        else {
          logger.warn("Source image path missing. Test did not produce an image for testId: $testId")
          panel.showError("Test did not produce an image")
        }
        updateRightPane(tree)
      } else {
        logger.warn("Missing methodName or previewName for tests in class $className")
      }
    }
  }

  fun onTestSuiteFinished() {
    // If no tests were ever discovered by the time the suite finishes, it indicates a build
    // failure or that no tests were found to run. Close the dialog and show an error.
    ApplicationManager.getApplication().invokeLater {
      if (!isFirstTestDiscovered) {
        logger.error("No tests were discovered in the test suite")
        close(CANCEL_EXIT_CODE)
        Messages.showErrorDialog(project, "Error while generating screenshots", "Failed to generate screenshots")
      } else {
        isTestSuiteFinished = true
        logger.debug("TestSuite finished. Enabling the 'Add' button.")
        updateOkButtonState()
      }
    }
  }

  /**
   * Handles cases where the build or execution fails before tests start.
   * Closes the dialog and opens the Run tool window to show errors.
   */
  fun onBuildFailed() {
    ApplicationManager.getApplication().invokeLater {
      // Only act if we haven't discovered any tests yet (meaning the failure happened during build or startup)
      if (!isFirstTestDiscovered) {
        logger.warn("Build or execution failed. Closing dialog.")
        close(CANCEL_EXIT_CODE)

        // Open the Run window so the user can see the build error
        project?.let {
          ToolWindowManager.getInstance(it).getToolWindow(ToolWindowId.RUN)?.activate(null)
        }
      }
    }
  }

  private fun populateCenterPanel() {
    val splitter = OnePixelSplitter(false, 0.3f)

    rightPaneCardLayout = CardLayout()
    rightPaneContent = JPanel(rightPaneCardLayout)
    previewDetailsPanel = PreviewDetailsPanel()

    rightPaneContent.add(placeholderLabel, "placeholder")
    rightPaneContent.add(previewDetailsPanel, "details")
    rightPaneCardLayout.show(rightPaneContent, "placeholder")

    rightPaneWrapper = JPanel(BorderLayout())
    previewToolbar = createPreviewToolbar()
    previewToolbar.isVisible = false
    rightPaneWrapper.add(rightPaneContent, BorderLayout.CENTER)
    rightPaneWrapper.add(previewToolbar, BorderLayout.SOUTH)

    tree = createPreviewTree()
    val treeScrollPane = JBScrollPane(tree)

    splitter.firstComponent = treeScrollPane
    splitter.secondComponent = rightPaneWrapper
    centerPanel.add(splitter, "content")
    centerPanelCardLayout.show(centerPanel, "content")
  }

  override fun createCenterPanel(): JComponent {
    val loadingIcon = JBLabel("Generating screenshots", AnimatedIcon.Default(), JBLabel.CENTER)
    centerPanel.add(loadingIcon, "Loading")
    centerPanelCardLayout.show(centerPanel, "Loading")
    centerPanel.preferredSize = Dimension(800, 600)
    centerPanel.minimumSize = Dimension(550, 400)
    return centerPanel
  }

  private fun createPreviewTree(): CheckboxTree {
    val rootNode = CheckedTreeNode("Select previews to update")

    val renderer = object : CheckboxTree.CheckboxTreeCellRenderer() {
      override fun customizeRenderer(
        tree: JTree, value: Any, selected: Boolean, expanded: Boolean,
        leaf: Boolean, row: Int, hasFocus: Boolean
      ) {
        val userObject = (value as? CheckedTreeNode)?.userObject
        val displayText = when(userObject) {
          is PreviewDetails -> userObject.previewName
          else -> userObject?.toString() ?: ""
        }
        textRenderer.append(displayText)
      }
    }

    return CheckboxTree(renderer, rootNode).apply {
      isRootVisible = true
      addCheckboxTreeListener(object : CheckboxTreeListener {
        override fun nodeStateChanged(node: CheckedTreeNode) {
          updateOkButtonState()
        }
      })
      addTreeSelectionListener { updateRightPane(this) }
      // Select the root node by default when the dialog opens.
      // The tree will be expanded dynamically as nodes are added.
      selectionPath = TreePath(rootNode.path)
    }
  }

  private fun createPreviewToolbar(): ComposePanel {
    return ComposePanel().apply {
      setContent {
        SwingBridgeTheme {
          val availableViews = if (isLeafSelected) {
            ScreenshotViewType.values().toList()
          } else {
            ScreenshotViewType.values().filter { it != ScreenshotViewType.ALL }
          }
          val buttonData = remember(selectedViewType, isLeafSelected) {
            availableViews.map { viewId ->
              SegmentedControlButtonData(
                selected = viewId == selectedViewType,
                content = { _ -> Text(viewId.displayText) },
                onSelect = {
                  selectedViewType = viewId
                  updateRightPane(tree)
                },
              )
            }
          }
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
          ) {
            SegmentedControl(buttons = buttonData, enabled = true)
          }
        }
      }
    }
  }

  private fun collectPreviews(startNode: CheckedTreeNode): List<PreviewDetails> {
    val previews = mutableListOf<PreviewDetails>()
    val nodesToVisit = ArrayDeque<CheckedTreeNode>().apply { add(startNode) }
    while (nodesToVisit.isNotEmpty()) {
      val currentNode = nodesToVisit.removeFirst()
      (currentNode.userObject as? PreviewDetails)?.let {
        previews.add(it)
      }
      for (child in currentNode.children()) {
        if (child is CheckedTreeNode) {
          nodesToVisit.add(child)
        }
      }
    }
    return previews
  }

  private fun updateRightPane(tree: CheckboxTree) {
    val selectedNode = tree.selectionPath?.lastPathComponent as? CheckedTreeNode
    if (selectedNode == null) {
      rightPaneCardLayout.show(rightPaneContent, "placeholder")
      previewToolbar.isVisible = false
      return
    }

    val previewsToShow = collectPreviews(selectedNode)
    isLeafSelected = selectedNode.isLeaf && selectedNode.userObject is PreviewDetails
    previewToolbar.isVisible = previewsToShow.isNotEmpty()

    if (previewsToShow.isEmpty()) {
      rightPaneCardLayout.show(rightPaneContent, "placeholder")
    } else {
      if(isLeafSelected) {
        rightPaneWrapper.remove(previewToolbar)
        previewToolbar.border = null
        previewDetailsPanel.displayPreviews(previewsToShow, imagePanelMap, selectedViewType, previewToolbar)
      } else {
        rightPaneWrapper.add(previewToolbar, BorderLayout.SOUTH)
        previewToolbar.border = BorderFactory.createMatteBorder(1, 0, 1, 0, JBColor.border())
        if (selectedViewType == ScreenshotViewType.ALL) {
          selectedViewType = ScreenshotViewType.NEW
        }
        previewDetailsPanel.displayPreviews(previewsToShow, imagePanelMap, selectedViewType, null)
      }

      rightPaneCardLayout.show(rightPaneContent, "details")
    }
    rightPaneWrapper.revalidate()
    rightPaneWrapper.repaint()
  }

  private fun collectCheckedPreviews(): List<PreviewDetails> {
    val root = tree.model.root as CheckedTreeNode
    return TreeUtil.treeNodeTraverser(root)
      .mapNotNull { node ->
        val checkedNode = node as? CheckedTreeNode
        if (checkedNode != null && checkedNode.isLeaf && checkedNode.isChecked) {
          checkedNode.userObject as? PreviewDetails
        } else {
          null
        }
      }
      .toList()
  }

  private fun updateOkButtonState() {
    val hasCheckedPreviews = collectCheckedPreviews().isNotEmpty()
    okAction.isEnabled = hasCheckedPreviews && isTestSuiteFinished
  }

  override fun doOKAction() {
    val checkedPreviews = collectCheckedPreviews()
    if (checkedPreviews.isEmpty()) {
      close(OK_EXIT_CODE)
      return
    }

    val panelsToCopy = checkedPreviews.mapNotNull { previewDetails ->
      previewDetails.testId?.let { imagePanelMap[it] }
    }

    val failedPreviews = panelsToCopy.filter { !it.isLoadedSuccessfully }
    if (failedPreviews.isNotEmpty()) {
      val failedNames = failedPreviews.joinToString(separator = "\n") { "- ${it.previewData.previewName}" }
      logger.error("The following selected previews have not rendered successfully: $failedNames")
      Messages.showErrorDialog(
        project,
        "The following selected previews have not rendered successfully. Please uncheck them to proceed:\n\n$failedNames",
        "Cannot Add Reference Images"
      )
      return
    }

    val okButton = getButton(okAction)
    val cancelButton = getButton(cancelAction)
    val originalText = okButton?.text
    val progressIcon = AnimatedIcon.Default()

    okButton?.text = "Updating..."
    okButton?.icon = progressIcon
    okButton?.isEnabled = false
    cancelButton?.isEnabled = false

    AppExecutorUtil.getAppExecutorService().submit {
      val imagesToCopy = panelsToCopy.map {
        ImageData(it.previewData, it.sourceImageToCopy)
      }
      val failures = copyReferenceImages(imagesToCopy)

      ApplicationManager.getApplication().invokeLater {
        if (failures.isEmpty()) {
          //Log the UPDATE_CLICKED event for analytics on successful copy of reference images.
          UsageTracker.log(
            AndroidStudioEvent.newBuilder().apply {
              kind = AndroidStudioEvent.EventKind.SCREENSHOT_TEST_COMPOSE_PREVIEW
              screenshotTestComposePreviewEvent = ScreenshotTestComposePreviewEvent.newBuilder().apply {
                type = ScreenshotTestComposePreviewEvent.Type.UPDATE_CLICKED
              }.build()
            }.withProjectId(project)
          )
          close(OK_EXIT_CODE)
          logger.info("Reference images were updated successfully")
          Messages.showInfoMessage(project, "Reference images were updated successfully.", "Update Successful")
        } else {
          val failedNames = failures.joinToString(separator = "\n") { "- ${it.previewData.previewName}" }
          logger.error("Failed to copy the following previews: $failedNames")
          Messages.showErrorDialog(project, "Failed to copy the following previews:\n\n$failedNames", "Copy Failed")
          okButton?.text = originalText
          okButton?.icon = null
          okButton?.isEnabled = true
          cancelButton?.isEnabled = true
        }
      }
    }
  }
}

data class PreviewDetails(
  val testId: String,
  val className: String,
  val methodName: String,
  val previewName: String,
  val testResult: AndroidTestCaseResult? = null,
  val destImagePath: String? = null,
  val srcImagePath: String? = null,
  val diffImagePath: String? = null,
  val diffPercent: String? = null
)