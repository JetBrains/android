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

import com.android.screenshottest.util.FqNames
import com.android.screenshottest.util.ImageData
import com.android.screenshottest.util.PreviewDetails
import com.android.screenshottest.util.copyReferenceImages
import com.android.screenshottest.util.findComposableCall
import com.android.screenshottest.util.findPreviewAnnotations
import com.android.screenshottest.util.getIdentifier
import com.android.screenshottest.util.getProviderClassName
import com.intellij.openapi.application.ApplicationManager
import com.android.tools.analytics.UsageTracker
import com.android.tools.analytics.withProjectId
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ScreenshotTestComposePreviewEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * A dialog for selecting and viewing screenshot test previews. It features a two-pane layout with a
 * tree of previews on the left and a live-updating image viewer on the right.
 */
class UpdateReferenceImagesDialog(
  private val project: Project?,
  private val previewFunctions: List<KtNamedFunction>,
  private val module: Module,
  private val triggerElement: PsiElement?,
  private val logger: Logger = Logger.getInstance(UpdateReferenceImagesDialog::class.java)
) : DialogWrapper(project) {

  private val successfulLoads = AtomicInteger(0)
  private lateinit var tree: CheckboxTree
  private val previewsContainer = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
  private val placeholderLabel = JBLabel("Select a node from the left to see its previews.", JBLabel.CENTER)
  private val imagePanelMap = mutableMapOf<String, PreviewItemPanel>()
  private val multipreviewNodeMap = mutableMapOf<String, CheckedTreeNode>()

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

  private fun onImageLoadedSuccessfully() {
    if (successfulLoads.incrementAndGet() == 1) {
      logger.info("First image loaded successfully, enabling 'Add' button.")
      okAction.isEnabled = true
    }
  }

  /**
   * testResults map stores the mapping of preview configuration with their full image paths.
   * For eg: MyTest.GreetingPreview_{heightDp=20, locale=fr, showBackground=true}=.../app/build/outputs/screenshotTest-results/preview/debug/rendered/MyTest/GreetingPreview_465a9f91_0.png
   *
   * We need the mapping to map each of the preview annotations to their respective images. Once
   * the gradle test task has the callback mechanism implemented we won't need this.
   *
   * TODO (b/449792632): To implement the callback mechanism
   */
  fun onBuildFinished(testResults: Map<String, String>) {
    ApplicationManager.getApplication().invokeLater {
      val handledTestIds = mutableSetOf<String>()

      multipreviewNodeMap.forEach { (testIdPattern, parentNode) ->
        val functionNamePrefix = testIdPattern.substringBefore("_[{")
        val matchingResults = testResults.filterKeys { it.startsWith(functionNamePrefix) }

        parentNode.removeAllChildren()

        if (matchingResults.isNotEmpty()) {
          val function = previewFunctions.find { it.name == parentNode.userObject.toString() }
          if (function != null) {
            populateMultipreviewNode(function, matchingResults, handledTestIds)
          } else {
            logger.warn("Could not find KtNamedFunction for multipreview node: ${parentNode.userObject}")
          }
        } else {
          parentNode.add(CheckedTreeNode("No previews found for this test.").apply { isEnabled = false })
        }
        (tree.model as DefaultTreeModel).reload(parentNode)
        tree.expandPath(TreePath(parentNode.path))
      }

      imagePanelMap.forEach { (testId, panel) ->
        if (testId !in handledTestIds) {
          val imagePath = testResults[testId]
          if (imagePath != null) {
            panel.loadImage(imagePath, testId)
          } else {
            panel.showError("Test did not run or produce an image.")
          }
        }
      }
      updateRightPane(tree)
    }
  }

  private fun populateMultipreviewNode(
    function: KtNamedFunction,
    results: Map<String, String>,
    handledTestIds: MutableSet<String>
  ) {
    val parentNode = multipreviewNodeMap[getIdentifier(PreviewDetails(function, null, emptyList(), null))] ?: return
    val composableFunction = findComposableCall(function)
    val allAnnotations = findPreviewAnnotations(function)
    val mainAnnotation = allAnnotations.firstOrNull()
    val shouldChildrenBeChecked = parentNode.isChecked

    val sortedResults = results.entries.sortedBy { it.key }
    sortedResults.forEachIndexed { index, (testId, imagePath) ->
      handledTestIds.add(testId)

      val baseDisplayName = testId.substringAfter('[', "").substringBefore(']', "parameter")
      val finalDisplayName = "$baseDisplayName [${index}]"
      val details = PreviewDetails(function, mainAnnotation, allAnnotations, composableFunction, finalDisplayName, testId)

      val panel = PreviewItemPanel(details, { onImageLoadedSuccessfully() })
      val childNode = CheckedTreeNode(details).apply { isChecked = shouldChildrenBeChecked }

      imagePanelMap[testId] = panel
      parentNode.add(childNode)
      panel.loadImage(imagePath, testId)
    }
  }

  override fun createCenterPanel(): JComponent {
    val splitter = JBSplitter(false, 0.3f)
    tree = createPreviewTree()
    val treeScrollPane = JBScrollPane(tree)
    val rightScrollPane = JBScrollPane(previewsContainer)
    rightScrollPane.border = null
    splitter.firstComponent = treeScrollPane
    splitter.secondComponent = rightScrollPane
    val rootPanel = JPanel(BorderLayout())
    rootPanel.add(splitter, BorderLayout.CENTER)
    rootPanel.preferredSize = Dimension(800, 600)
    rootPanel.minimumSize = Dimension(550, 400)
    return rootPanel
  }

  private fun createPreviewTree(): CheckboxTree {
    val rootNode = CheckedTreeNode("Changes")
    val functionsByFile = previewFunctions.groupBy { it.containingKtFile.name }

    functionsByFile.forEach { (fileName, functions) ->
      val fileNode = CheckedTreeNode(fileName)
      functions.forEach { function ->
        val isMultipreview = function.valueParameters.any { param ->
          param.annotationEntries.any { it.shortName == FqNames.previewParameter.shortName() }
        }

        val shouldBeChecked = isFunctionInTriggerScope(function)
        val functionNode = CheckedTreeNode(function.name).apply { isChecked = shouldBeChecked }

        if (isMultipreview) {
          setupMultipreviewNode(function, functionNode)
        } else {
          populateStandardPreviewNodes(function, functionNode, shouldBeChecked)
        }

        if (functionNode.childCount > 0) {
          fileNode.add(functionNode)
        }
      }
      if (fileNode.childCount > 0) {
        rootNode.add(fileNode)
      }
    }

    val renderer = object : CheckboxTree.CheckboxTreeCellRenderer() {
      override fun customizeRenderer(
        tree: JTree, value: Any, selected: Boolean, expanded: Boolean,
        leaf: Boolean, row: Int, hasFocus: Boolean
      ) {
        val userObject = (value as? CheckedTreeNode)?.userObject
        textRenderer.append(userObject?.toString() ?: "")
      }
    }

    return CheckboxTree(renderer, rootNode).apply {
      isRootVisible = true
      addTreeSelectionListener { updateRightPane(this) }
      TreeUtil.expandAll(this)
      if (rootNode.childCount > 0) {
        selectionPath = TreePath(rootNode.path)
      }
    }
  }

  /**
   * Determines if a function should be checked by default based on the action's trigger point.
   */
  private fun isFunctionInTriggerScope(function: KtNamedFunction): Boolean {
    if (triggerElement == null) {
      // If the action is triggered from a non-specific context (e.g., a menu),
      // it's better to check all previews by default.
      return true
    }

    // Find the containing function and class for the trigger element.
    val triggerFunction = PsiTreeUtil.getNonStrictParentOfType(triggerElement, KtNamedFunction::class.java)
    if (triggerFunction != null) {
      // Trigger was on a function (or its identifier), check only that function.
      return function == triggerFunction
    }

    val triggerClass = PsiTreeUtil.getNonStrictParentOfType(triggerElement, KtClass::class.java)
    if (triggerClass != null) {
      // Trigger was on a class (or its identifier), check all functions inside that class.
      return PsiTreeUtil.isAncestor(triggerClass, function, false)
    }

    // Fallback for unexpected trigger elements.
    return true
  }

  private fun setupMultipreviewNode(function: KtNamedFunction, functionNode: CheckedTreeNode) {
    val tempDetails = PreviewDetails(function, null, emptyList(), null)
    val testIdPattern = getIdentifier(tempDetails) ?: return
    multipreviewNodeMap[testIdPattern] = functionNode

    val providerName = getProviderClassName(function)
    val placeholderText = if (providerName != null) {
      "Finding previews from $providerName..."
    } else {
      "Running tests to find previews..."
    }

    val placeholderDetails = PreviewDetails(
      function = function,
      annotation = null,
      allAnnotationsOnFunction = emptyList(),
      composableFunction = null,
      displayNameOverride = placeholderText,
      testId = testIdPattern
    )
    val placeholderNode = CheckedTreeNode(placeholderDetails).apply {
      isEnabled = false
      isChecked = functionNode.isChecked
    }
    functionNode.add(placeholderNode)

    val placeholderPanel = PreviewItemPanel(placeholderDetails, { /* no-op */ })
    imagePanelMap[testIdPattern] = placeholderPanel
  }

  private fun populateStandardPreviewNodes(
    function: KtNamedFunction,
    functionNode: CheckedTreeNode,
    shouldBeChecked: Boolean
  ) {
    val previewAnnotations = findPreviewAnnotations(function)
    val composableFunction = findComposableCall(function)

    if (previewAnnotations.isEmpty()) {
      val details = PreviewDetails(function, null, emptyList(), composableFunction)
      val testId = getIdentifier(details)
      val finalDetails = details.copy(testId = testId)
      val previewNode = CheckedTreeNode(finalDetails).apply { isChecked = shouldBeChecked }
      functionNode.add(previewNode)
      createPanelForPreview(finalDetails)
    } else {
      // Use a map to de-duplicate previews that would generate the same test.
      val uniqueDetails = mutableMapOf<String, PreviewDetails>()
      previewAnnotations.forEach { annotation ->
        val details = PreviewDetails(function, annotation, previewAnnotations, composableFunction)
        getIdentifier(details)?.let { testId ->
          // Only add the first occurrence of a preview with this testId.
          uniqueDetails.putIfAbsent(testId, details.copy(testId = testId))
        }
      }

      // Now, create nodes only for the unique configurations.
      uniqueDetails.values.forEach { uniqueDetail ->
        val previewNode = CheckedTreeNode(uniqueDetail).apply { isChecked = shouldBeChecked }
        functionNode.add(previewNode)
        createPanelForPreview(uniqueDetail)
      }
    }
  }

  private fun createPanelForPreview(nodeData: PreviewDetails) {
    nodeData.testId?.let { testId ->
      val panel = PreviewItemPanel(nodeData, { onImageLoadedSuccessfully() })
      if (nodeData.function.valueParameters.any { param -> param.annotationEntries.any { it.shortName == FqNames.previewParameter.shortName() } }) {
        panel.setMultipreview()
      }
      imagePanelMap[testId] = panel
    } ?: logger.warn("Could not create panel because PreviewDetails has no testId: $nodeData")
  }

  private fun collectPreviews(startNode: CheckedTreeNode): List<PreviewDetails> {
    val previews = mutableListOf<PreviewDetails>()
    val nodesToVisit = ArrayDeque<CheckedTreeNode>().apply { add(startNode) }
    while (nodesToVisit.isNotEmpty()) {
      val currentNode = nodesToVisit.removeFirst()
      (currentNode.userObject as? PreviewDetails)?.let { previews.add(it) }
      for (child in currentNode.children()) {
        if (child is CheckedTreeNode) nodesToVisit.add(child)
      }
    }
    return previews
  }

  private fun updateRightPane(tree: CheckboxTree) {
    previewsContainer.removeAll()
    val selectedNode = tree.selectionPath?.lastPathComponent as? CheckedTreeNode
    if (selectedNode == null) {
      previewsContainer.add(placeholderLabel)
    } else {
      val previewsToShow = collectPreviews(selectedNode)
      if (previewsToShow.isEmpty()) {
        previewsContainer.add(placeholderLabel)
      } else {
        val previewsByFunction = previewsToShow.groupBy { it.function }
        previewsByFunction.forEach { (function, previews) ->
          val functionNameLabel =
            JBLabel(function.name ?: "Unnamed Function").apply {
              font = font.deriveFont(Font.BOLD, font.size + 2f)
              border = BorderFactory.createEmptyBorder(15, 5, 5, 5)
              alignmentX = JComponent.LEFT_ALIGNMENT
            }
          previewsContainer.add(functionNameLabel)
          // Use FlowLayout to ensure components are left-aligned and not stretched.
          val horizontalPreviewsPanel =
            JPanel(FlowLayout(FlowLayout.LEFT, 10, 0)).apply {
              border = BorderFactory.createEmptyBorder(10, 20, 10, 20)
            }
          previews.forEach { previewData ->
            val testId = previewData.testId ?: getIdentifier(previewData)
            imagePanelMap[testId]?.let { panel ->
              horizontalPreviewsPanel.add(panel)
            }
          }
          val horizontalScrollPane =
            JBScrollPane(horizontalPreviewsPanel).apply {
              horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
              verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_NEVER
              border = null
              alignmentX = JComponent.LEFT_ALIGNMENT
            }
          previewsContainer.add(horizontalScrollPane)
        }
      }
    }
    previewsContainer.revalidate()
    previewsContainer.repaint()
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
      val failedNames = failedPreviews.joinToString(separator = "\n") { "- ${it.previewData.displayName}" }
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

    ApplicationManager.getApplication().executeOnPooledThread {
      val imagesToCopy = panelsToCopy.map {
        ImageData(it.previewData, it.loadedImagePaths)
      }
      val failures = copyReferenceImages(module, imagesToCopy)

      ApplicationManager.getApplication().invokeLater {
        if (failures.isEmpty()) {
          // Log the UPDATE_CLICKED event for analytics on successful copy of reference images.
          UsageTracker.log(
            AndroidStudioEvent.newBuilder().apply {
              kind = AndroidStudioEvent.EventKind.SCREENSHOT_TEST_COMPOSE_PREVIEW
              screenshotTestComposePreviewEvent = ScreenshotTestComposePreviewEvent.newBuilder().apply {
                type = ScreenshotTestComposePreviewEvent.Type.UPDATE_CLICKED
              }.build()
            }.withProjectId(project)
          )
          close(OK_EXIT_CODE)
          Messages.showInfoMessage(project, "Reference images were updated successfully.", "Update Successful")
        } else {
          val failedNames = failures.joinToString(separator = "\n") { "- ${it.previewData.displayName}" }
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