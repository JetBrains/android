/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.upgrade.ui

import com.android.SdkConstants
import com.android.ide.common.repository.AgpVersion
import com.android.tools.adtui.HtmlLabel
import com.android.tools.adtui.common.primaryContentBackground
import com.android.tools.adtui.model.stdui.CommonComboBoxModel
import com.android.tools.adtui.model.stdui.DefaultCommonComboBoxModel
import com.android.tools.adtui.model.stdui.EditingErrorCategory
import com.android.tools.adtui.model.stdui.EditingSupport
import com.android.tools.adtui.model.stdui.EditingValidation
import com.android.tools.adtui.model.stdui.EditorCompletion
import com.android.tools.adtui.stdui.CommonComboBox
import com.android.tools.adtui.stdui.CommonTextField
import com.android.tools.adtui.stdui.KeyStrokes
import com.android.tools.adtui.stdui.registerActionKey
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity
import com.android.tools.idea.gradle.project.upgrade.AndroidGradlePluginCompatibility
import com.android.tools.idea.gradle.project.upgrade.computeAndroidGradlePluginCompatibility
import com.android.tools.idea.observable.ListenerManager
import com.intellij.build.BuildContentManager
import com.intellij.history.integration.LocalHistoryImpl
import com.intellij.history.integration.ui.views.DirectoryHistoryDialog
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.CheckboxTree
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.BrowserLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeModelAdapter
import com.intellij.util.ui.tree.TreeUtil
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.TreeModelEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeSelectionModel

class UpgradeAssistantView(val model: UpgradeAssistantWindowModel, contentManager: com.intellij.ui.content.ContentManager) {
  private val myListeners = ListenerManager()

  val treePanel = JBPanel<JBPanel<*>>(BorderLayout())

  val detailsPanel = JBPanel<JBPanel<*>>().apply {
    layout = VerticalLayout(0, SwingConstants.LEFT)
    border = JBUI.Borders.empty(20)
  }

  val tree: CheckboxTree = CheckboxTree(UpgradeAssistantTreeCellRenderer(), null)

  init {
    treePanel.apply {
      add(ScrollPaneFactory.createScrollPane(tree, SideBorder.NONE), BorderLayout.WEST)
      add(JSeparator(SwingConstants.VERTICAL), BorderLayout.CENTER)
    }

    tree.apply {
      model = this@UpgradeAssistantView.model.treeModel
      isRootVisible = false
      selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
      addCheckboxTreeListener(this@UpgradeAssistantView.model.checkboxTreeStateUpdater)
      addTreeSelectionListener { e -> refreshDetailsPanel() }
      background = primaryContentBackground
      isOpaque = true
      fun update(uiState: UpgradeAssistantWindowModel.UIState) {
        isEnabled = !uiState.showLoadingState
        treePanel.isVisible = uiState.showTree
        if (!uiState.showTree) {
          selectionModel.clearSelection()
        }
        refreshDetailsPanel()
      }
      myListeners.listenAndFire(this@UpgradeAssistantView.model.uiState, ::update)
    }
  }

  val upgradeLabel = JBLabel(model.current.upgradeLabelText()).also { it.border = JBUI.Borders.empty(0, 6) }

  val versionTextField = CommonComboBox<AgpVersion, CommonComboBoxModel<AgpVersion>>(
    // TODO this model needs to be enhanced to know when to commit value, instead of doing it in document listener below.
    object : DefaultCommonComboBoxModel<AgpVersion>(
      model.selectedVersion?.toString() ?: "",
      model.suggestedVersions.valueOrNull ?: emptyList()
    ) {
      init {
        selectedItem = model.selectedVersion
        myListeners.listen(model.suggestedVersions) { suggestedVersions ->
          val selectedVersion = model.selectedVersion
          for (i in size - 1 downTo 0) {
            if (getElementAt(i) != selectedVersion) removeElementAt(i)
          }
          suggestedVersions.orElse(emptyList()).forEachIndexed { i, it ->
            when {
              selectedVersion == null -> addElement(it)
              it > selectedVersion -> insertElementAt(it, i)
              it == selectedVersion -> Unit
              else -> addElement(it)
            }
          }
          selectedItem = selectedVersion
        }
        placeHolderValue = "Select new version"
      }

      // Given the ComponentValidator installation below, one might expect this not to be necessary,
      // but the outline highlighting does not work without it.
      // This is happening because not specifying validation here does not remove validation but just using default 'accept all' one.
      // This validation is triggered after the ComponentValidator and overrides the outline set by ComponentValidator.
      // The solution would be either add support of the tooltip to this component validation logic or use a different component.
      override val editingSupport = object : EditingSupport {
        override val validation: EditingValidation = model::editingValidation
        override val completion: EditorCompletion = { model.suggestedVersions.getValueOr(emptyList()).map { it.toString() } }
      }
    }
  ).apply {
    myListeners.listenAndFire(this@UpgradeAssistantView.model.uiState) { uiState ->
      isEnabled = uiState.comboEnabled
    }

    // Need to register additional key listeners to the textfield that would hide main combo-box popup.
    // Otherwise textfield consumes these events without hiding popup making it impossible to do with a keyboard.
    val textField = editor.editorComponent as CommonTextField<*>
    textField.registerActionKey({ hidePopup(); textField.enterInLookup() }, KeyStrokes.ENTER, "enter")
    textField.registerActionKey({ hidePopup(); textField.escapeInLookup() }, KeyStrokes.ESCAPE, "escape")
    ComponentValidator(this@UpgradeAssistantView.model).withValidator { ->
      val text = editor.item.toString()
      val validation = this@UpgradeAssistantView.model.editingValidation(text)
      when (validation.first) {
        EditingErrorCategory.ERROR -> ValidationInfo(validation.second, this)
        EditingErrorCategory.WARNING -> ValidationInfo(validation.second, this).asWarning()
        else -> null
      }
    }.installOn(this)
    textField.document?.addDocumentListener(
      object: DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          ComponentValidator.getInstance(this@apply).ifPresent { v -> v.revalidate() }
          this@UpgradeAssistantView.model.newVersionSet(editor.item.toString())
        }
      }
    )
  }

  val refreshButton = JButton("Refresh").apply {
    myListeners.listenAndFire(this@UpgradeAssistantView.model.uiState) { uiState ->
      isEnabled = !uiState.showLoadingState
    }
    addActionListener {
      this@UpgradeAssistantView.model.run {
        refresh(true)
      }
    }
  }
  val okButton = JButton("Run selected steps").apply {
    addActionListener { this@UpgradeAssistantView.model.runUpgrade(false) }
    myListeners.listenAndFire(this@UpgradeAssistantView.model.uiState) { uiState ->
      toolTipText = uiState.runTooltip
      isEnabled = uiState.runEnabled
    }
    putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, true)
  }
  val previewButton = JButton("Show Usages").apply {
    addActionListener { this@UpgradeAssistantView.model.runUpgrade(true) }
    myListeners.listenAndFire(this@UpgradeAssistantView.model.uiState) { uiState ->
      toolTipText = uiState.runTooltip
      isEnabled = uiState.showPreviewEnabled
    }
  }
  val messageLabel = JBLabel().apply {
    myListeners.listenAndFire(this@UpgradeAssistantView.model.uiState) { uiState ->
      icon = uiState.statusMessage?.severity?.icon
      text = uiState.statusMessage?.text
    }
  }
  val hyperlinkLabel = object : ActionLink("Read more") {
    var url: String? = null
  }
    .apply {
      addActionListener { url?.let { BrowserUtil.browse(it) } }
      setExternalLinkIcon()
      myListeners.listenAndFire(this@UpgradeAssistantView.model.uiState) { uiState ->
        url = uiState.statusMessage?.url
        isVisible = url != null
      }
    }

  val content = JBLoadingPanel(BorderLayout(), contentManager).apply {
    val controlsPanel = makeTopComponent()
    val topPanel = JBPanel<JBPanel<*>>().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      add(controlsPanel)
      add(JSeparator(SwingConstants.HORIZONTAL))
    }
    add(topPanel, BorderLayout.NORTH)
    add(treePanel, BorderLayout.WEST)
    add(detailsPanel, BorderLayout.CENTER)

    fun updateLoadingState(uiState: UpgradeAssistantWindowModel.UIState) {
      setLoadingText(uiState.loadingText)
      if (uiState.showLoadingState) {
        startLoading()
      }
      else {
        stopLoading()
        upgradeLabel.text = model.current.upgradeLabelText()
        contentManager.getContent(this)?.displayName = model.current.contentDisplayName()
      }
    }

    myListeners.listenAndFire(model.uiState) { uiState ->
      setLoadingText(uiState.loadingText)
      if (uiState.showLoadingState) {
        startLoading()
      }
      else {
        stopLoading()
        upgradeLabel.text = model.current.upgradeLabelText()
        contentManager.getContent(this)?.displayName = model.current.contentDisplayName()
      }
    }
  }

  init {
    myListeners.listen(model.uiRefreshNotificationTimestamp) {
      tree.repaint()
      refreshDetailsPanel()
    }
    model.treeModel.addTreeModelListener(object : TreeModelAdapter() {
      override fun treeStructureChanged(event: TreeModelEvent?) {
        // Tree expansion should not run in 'treeStructureChanged' as another listener clears the nodes expanded state
        // in the same event listener that is normally called after this one. Probably this state is cached somewhere else
        // making this diversion not immediately visible but on page hide and restore it uses all-folded state form the model.
        invokeLater(ModalityState.NON_MODAL) {
          tree.setHoldSize(false)
          TreeUtil.expandAll(tree) {
            tree.setHoldSize(true)
            content.revalidate()
          }
        }
      }
    })
    TreeUtil.expandAll(tree)
    tree.setHoldSize(true)
  }

  private fun makeTopComponent() = JBPanel<JBPanel<*>>().apply {
    // This layout, rather than com.intellij.ide.plugins.newui.HorizontalLayout (used elsewhere in ContentManager), is needed to make
    // the baseline of the hyperlinkLabel be aligned with the baselines of unstyled text in other elements.  It does not align the text
    // in the versionTextField combo box with this baseline, however; instead the borders of the combo are aligned with the borders of
    // the button.  Using GroupLayout (with BASELINE alignment) aligns all the text baselines, at the cost of misaligning the combo
    // borders; altering the combo's dimensions or insets somehow might allow complete unity.
    layout = HorizontalLayout(5)
    add(upgradeLabel, HorizontalLayout.LEFT)
    add(versionTextField, HorizontalLayout.LEFT)
    add(okButton, HorizontalLayout.LEFT)
    add(previewButton, HorizontalLayout.LEFT)
    add(refreshButton, HorizontalLayout.LEFT)
    add(messageLabel, HorizontalLayout.LEFT)
    add(hyperlinkLabel, HorizontalLayout.LEFT)
  }

  private fun refreshDetailsPanel() {
    detailsPanel.removeAll()
    val selectedStep = (tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode)?.userObject
    val uiState = this@UpgradeAssistantView.model.uiState.get()
    val label = HtmlLabel().apply { name = "content" }
    HtmlLabel.setUpAsHtmlLabel(label)
    when {
      uiState is UpgradeAssistantWindowModel.UIState.CaughtException -> {
        val sb = StringBuilder()
        sb.append("<div><b>Caught exception</b></div>")
        sb.append("<p>Something went wrong (an internal exception occurred).  ")
        sb.append("You should revert to a known-good state before doing anything else.</p>")
        sb.append("<p>The status message is:<br/>")
        sb.append(uiState.statusMessage.text)
        sb.append("</p>")
        label.text = sb.toString()
        detailsPanel.add(label)
        detailsPanel.addRevertInfo(showRevertButton = false, markRevertAsDefault = false)
      }
      uiState is UpgradeAssistantWindowModel.UIState.UpgradeSyncFailed -> {
        val sb = StringBuilder()
        sb.append("<div><b>Sync Failed</b></div>")
        sb.append("<p>The project failed to sync with the IDE.  You should revert<br/>")
        sb.append("to a known-good state before making further changes.</p>")
        label.text = sb.toString()
        val layoutPanel = JPanel()
        layoutPanel.layout = BorderLayout()
        detailsPanel.add(layoutPanel)
        val realDetailsPanel = JBPanel<JBPanel<*>>().apply {
          layout = VerticalLayout(0, SwingConstants.LEFT)
          border = JBUI.Borders.empty(0, 0, 0, 20)
        }
        layoutPanel.add(realDetailsPanel, BorderLayout.WEST)
        val errorPanel = JBPanel<JBPanel<*>>().apply {
          layout = VerticalLayout(0, SwingConstants.LEFT)
          border = JBUI.Borders.empty(0, 0, 0, 0)
        }
        layoutPanel.add(errorPanel, BorderLayout.CENTER)
        realDetailsPanel.add(label)
        errorPanel.add(JBLabel("The error message from sync is:"))
        uiState.errorMessage.trimEnd().let { errorMessage ->
          val rows = minOf(errorMessage.lines().size, 10)
          JBTextArea(errorMessage, rows, 80).let { textArea ->
            textArea.isEditable = false
            JBScrollPane(textArea).run {
              errorPanel.add(this)
            }
          }
        }
        realDetailsPanel.addRevertInfo(showRevertButton = true, markRevertAsDefault = true)
        realDetailsPanel.addBuildWindowInfo()
      }
      uiState is UpgradeAssistantWindowModel.UIState.UpgradeSyncSucceeded -> {
        val sb = StringBuilder()
        sb.append("<div><b>Sync succeeded</b></div>")
        sb.append("<p>The upgraded project successfully synced with the IDE.  ")
        sb.append("You should test that the upgraded project builds and passes its tests successfully before making further changes.</p>")
        model.processor?.let { processor ->
          sb.append("<p>The upgrade consisted of the following steps:</p>")
          sb.append("<ul>")
          processor.componentRefactoringProcessors.filter { it.isEnabled }.forEach {
            sb.append("<li>${it.commandName}</li>")
          }
          sb.append("</ul>")
        }
        label.text = sb.toString()
        detailsPanel.add(label)
        detailsPanel.addRevertInfo(showRevertButton = true, markRevertAsDefault = false)
      }
      uiState is UpgradeAssistantWindowModel.UIState.AllDone -> {
        val sb = StringBuilder()
        sb.append("<div><b>Up-to-date for Android Gradle Plugin version ${this@UpgradeAssistantView.model.current}</b></div>")
        if (this@UpgradeAssistantView.model.current?.let { it < this@UpgradeAssistantView.model.latestKnownVersion } == true) {
          sb.append("<p>Upgrades to newer versions of Android Gradle Plugin (up to ${this@UpgradeAssistantView.model.latestKnownVersion}) can be")
          sb.append("<br>performed by selecting those versions from the dropdown.</p>")
        }
        label.text = sb.toString()
        detailsPanel.add(label)
      }
      selectedStep is AgpUpgradeComponentNecessity -> {
        label.text = "<div><b>${selectedStep.treeText()}</b></div><p>${selectedStep.description().replace("\n", "<br>")}</p>"
        detailsPanel.add(label)
      }
      selectedStep is UpgradeAssistantWindowModel.StepUiPresentation -> {
        val text = StringBuilder("<div><b>${selectedStep.pageHeader}</b></div>")
        val paragraph = selectedStep.helpLinkUrl != null || selectedStep.shortDescription != null
        if (paragraph) text.append("<p>")
        selectedStep.shortDescription?.let { description ->
          text.append(description.replace("\n", "<br>"))
          selectedStep.helpLinkUrl?.let { text.append("  ") }
        }
        selectedStep.helpLinkUrl?.let { url ->
          // TODO(xof): what if we end near the end of the line, and this sticks out in an ugly fashion?
          text.append("<a href='$url'>Read more</a><icon src='AllIcons.Ide.External_link_arrow'>.")
        }
        selectedStep.additionalInfo?.let { text.append(it) }
        if (selectedStep.isBlocked) {
          text.append("<br><br><div><b>This step is blocked</b></div>")
          text.append("<ul>")
          selectedStep.blockReasons.forEach { reason ->
            reason.shortDescription.let { text.append("<li>$it") }
            reason.description?.let { text.append("<br>${it.replace("\n", "<br>")}") }
            reason.readMoreUrl?.let { text.append("  <a href='${it.url}'>Read more</a><icon src='AllIcons.Ide.External_link_arrow'>.") }
            text.append("</li>")
          }
          text.append("</ul>")
        }
        label.text = text.toString()
        detailsPanel.add(label)
        if (selectedStep is UpgradeAssistantWindowModel.StepUiWithComboSelectorPresentation) {
          ComboBox(selectedStep.elements.toTypedArray()).apply {
            name = "selection"
            item = selectedStep.selectedValue
            addActionListener {
              selectedStep.selectedValue = this.item
              tree.repaint()
              refreshDetailsPanel()
            }
            val comboPanel = JBPanel<JBPanel<*>>()
            comboPanel.layout = com.intellij.ide.plugins.newui.HorizontalLayout(0)
            comboPanel.add(JBLabel(selectedStep.label).also { it.border = JBUI.Borders.empty(0, 4); it.name = "label" })
            comboPanel.add(this)
            detailsPanel.add(comboPanel)
          }
        }
        if (selectedStep is UpgradeAssistantWindowModel.StepUiWithUserSelection) {
          detailsPanel.add(selectedStep.createUiSelector())
        }
      }
      selectedStep == null && uiState.showTree -> {
        when (model.current?.let { computeAndroidGradlePluginCompatibility(it, model.latestKnownVersion) }) {
          AndroidGradlePluginCompatibility.DEPRECATED -> {
            val sb = StringBuilder()
            sb.append("<div><b>Update from deprecated Android Gradle Plugin version</b></div>")
            sb.append("<p>This project currently uses Android Gradle Plugin version ${model.current}, which<br/>" +
                      "will not be supported in future versions of Android Studio.  Update your project<br/>" +
                      "to version ${SdkConstants.GRADLE_PLUGIN_NEXT_MINIMUM_VERSION} or later")
            if (model.recommended?.let { it < AgpVersion.parse(SdkConstants.GRADLE_PLUGIN_NEXT_MINIMUM_VERSION) } == true) {
              sb.append(" (you may wish to do this in multiple steps)")
            }
            sb.append(".")
            label.text = sb.toString()
            detailsPanel.add(label)
            detailsPanel.addReleaseNotesInfo(model)
          }
          AndroidGradlePluginCompatibility.COMPATIBLE -> {
            if (model.current == model.recommended) {
              // not AllDone: there must be optional steps left (current must be non-null here)
              val sb = StringBuilder()
              sb.append("<div><b>Recommended project updates</b></div>")
              sb.append("<p>To prepare your project for future changes in the Android Gradle Plugin, we recommend<br/>" +
                        "executing additional update steps.")
              label.text = sb.toString()
              detailsPanel.add(label)
            }
            else {
              val sb = StringBuilder()
              sb.append("<div><b>Updates available</b></div>")
              sb.append("<p>To take advantage of the latest features, improvements and fixes, we<br/>" +
                        "recommend that you upgrade this project's Android Gradle Plugin from ${model.current}<br/>" +
                        "to ${model.recommended}.</p>")
              label.text = sb.toString()
              detailsPanel.add(label)
              detailsPanel.addReleaseNotesInfo(model)
            }
          }
          // Other (non-compatible) cases not handled by AGP Upgrade Assistant Tool Window
          AndroidGradlePluginCompatibility.DIFFERENT_PREVIEW, AndroidGradlePluginCompatibility.BEFORE_MINIMUM, AndroidGradlePluginCompatibility.AFTER_MAXIMUM -> Unit
          null -> Unit
        }
      }
    }
    detailsPanel.revalidate()
    detailsPanel.repaint()
  }

  private fun JBPanel<JBPanel<*>>.addBuildWindowInfo() {
    JPanel().apply {
      name = "build window info panel"
      layout = VerticalLayout(0)
      border = JBUI.Borders.empty(10, 0, 0, 0)
      add(JBLabel("There may be more information about the sync failure in the"))
      ActionLink("'Build' window") {
        val project = this@UpgradeAssistantView.model.project
        invokeLater {
          if (!project.isDisposed) {
            val buildContentManager = BuildContentManager.getInstance(project)
            val buildToolWindow = buildContentManager.getOrCreateToolWindow()
            if (!buildToolWindow.isAvailable) return@invokeLater
            buildToolWindow.show()
            val contentManager = buildToolWindow.contentManager
            contentManager.findContent("Sync")?.let { content -> contentManager.setSelectedContent(content) }
          }
        }
      }
        .apply { name = "open build window link" }
        .also { actionLink -> add(actionLink) }
    }
      .also { panel -> add(panel) }
  }

  private fun JBPanel<JBPanel<*>>.addRevertInfo(showRevertButton: Boolean, markRevertAsDefault: Boolean) {
    if (showRevertButton) {
      JButton("Revert Project Files")
        .apply {
          name = "revert project button"
          toolTipText = "Revert all project files to a state recorded just before running last upgrade."
          putClientProperty(DarculaButtonUI.DEFAULT_STYLE_KEY, markRevertAsDefault)
          addActionListener { this@UpgradeAssistantView.model.runRevert() }
        }
        .also { revertButton -> add(revertButton) }
    }

    JPanel().apply {
      name = "revert information panel"
      layout = FlowLayout(FlowLayout.LEADING, 0, 0)
      border = JBUI.Borders.empty(20, 0, 0, 0)
      add(JBLabel("You can review the applied changes in the "))
      ActionLink("'Local History' dialog") {
        val ideaGateway = LocalHistoryImpl.getInstanceImpl().getGateway()
        // TODO (mlazeba/xof): baseDir is deprecated, how can we avoid it here? might be better to show RecentChangeDialog instead
        val dialog = DirectoryHistoryDialog(this@UpgradeAssistantView.model.project, ideaGateway, this@UpgradeAssistantView.model.project.baseDir)
        dialog.show()
      }
        .apply { name = "open local history link" }
        .also { actionLink -> add(actionLink) }
    }
      .also { panel -> add(panel) }
  }

  private fun JBPanel<JBPanel<*>>.addReleaseNotesInfo(toolWindowModel: UpgradeAssistantWindowModel) {
    val agpVersion = toolWindowModel.selectedVersion ?: toolWindowModel.recommended
    JPanel().apply {
      name = "release notes info panel"
      layout = FlowLayout(FlowLayout.LEADING, 0, 0)
      border = JBUI.Borders.empty(10, 0, 0, 0)
      add(JBLabel("View the Android Gradle plugin "))
      val (url, text) = when {
        agpVersion == null -> "https://developer.android.com/studio/releases/gradle-plugin" to "release notes"
        agpVersion.isPreview -> "https://developer.android.com/studio/preview/features" to "preview release notes"
        else -> "https://developer.android.com/studio/releases/gradle-plugin" to "release notes"
      }
      BrowserLink(AllIcons.Ide.External_link_arrow, text, null, url)
        .apply { name = "browse release notes link" }
        .also { browserLink -> add(browserLink) }
    }
      .also { panel -> add(panel) }
  }
}