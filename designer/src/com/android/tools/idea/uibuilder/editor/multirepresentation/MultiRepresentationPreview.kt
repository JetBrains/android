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
package com.android.tools.idea.uibuilder.editor.multirepresentation

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.adtui.common.AdtPrimaryPanel
import com.android.tools.editor.ActionToolbarUtil
import com.android.tools.idea.common.editor.DesignFileEditor
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import kotlin.streams.toList

/**
 * A generic preview [com.intellij.openapi.fileEditor.FileEditor] that allows you to switch between different [PreviewRepresentation]s.
 */
open class MultiRepresentationPreview(private val psiFile: PsiFile,
                                      private val providers: List<PreviewRepresentationProvider>,
                                      persistenceProvider: (Project) -> PropertiesComponent) :
  PreviewRepresentationManager, DesignFileEditor(psiFile.virtualFile!!) {

  constructor(file: PsiFile, epName: String) :
    this(
      file,
      ExtensionPointName.create<PreviewRepresentationProvider>(epName).extensions().toList(),
      { p -> PropertiesComponent.getInstance(p) })

  private val project = psiFile.project
  private val virtualFile = psiFile.virtualFile!!
  private var shortcutsApplicableComponent: JComponent? = null

  private val instanceId = "$MULTI_REPRESENTATION_PREVIEW${virtualFile.path}"

  private val persistenceManager = persistenceProvider(project)

  private var representationNeverShown = true

  private val representations: MutableMap<RepresentationName, PreviewRepresentation> = mutableMapOf()

  override val currentRepresentation: PreviewRepresentation?
    get() {
      return representations[currentRepresentationName]
    }

  override val representationNames: List<RepresentationName>
    get() {
      return representations.keys.sorted()
    }

  // It is a client's responsibility to set a correct (valid) value of the currentRepresentationName
  override var currentRepresentationName: RepresentationName =
    persistenceManager.getValue("${instanceId}_selected", "")
    set(value) {
      if (field != value) {
        field = value

        persistenceManager.setValue("${instanceId}_selected", field)

        onRepresentationChanged()
      }
    }

  private fun onRepresentationChanged() = UIUtil.invokeLaterIfNeeded {
    component.removeAll()

    component.add(representationSelectionToolbar, BorderLayout.NORTH)

    currentRepresentation?.let {
      component.add(it.component, BorderLayout.CENTER)
    }

    representationNeverShown = false
  }

  private fun validateCurrentRepresentationName() {
    if (representations.isEmpty()) {
      currentRepresentationName = ""
    }
    else if (!representations.containsKey(currentRepresentationName)) {
      currentRepresentationName = representationNames.first()
    }
    if (representationNeverShown) {
      onRepresentationChanged()
    }
  }

  protected fun updateRepresentations() = UIUtil.invokeLaterIfNeeded {
    if (Disposer.isDisposed(this)) {
      return@invokeLaterIfNeeded
    }

    val providers = providers.filter { it.accept(project, virtualFile) }.toList()
    val providerNames = providers.map { it.displayName }.toSet()

    // Remove unaccepted
    (representations.keys - providerNames).forEach { name ->
      representations.remove(name)?.let {
        Disposer.dispose(it)
      }
    }
    // Add new
    for (provider in providers.filter { it.displayName !in representations.keys }) {
      val representation = provider.createRepresentation(psiFile)
      Disposer.register(this, representation)
      shortcutsApplicableComponent?.let {
        representation.registerShortcuts(it)
      }
      representations[provider.displayName] = representation
    }

    // update current if it was deleted
    validateCurrentRepresentationName()

    representationSelectionToolbar.isVisible = representations.size > 1

    onRepresentationsUpdated?.invoke()
  }

  var onRepresentationsUpdated: (() -> Unit)? = null

  fun updateNotifications() {
    representations.values.forEach {
      it.updateNotifications(this)
    }
  }

  fun registerShortcuts(appliedTo: JComponent) {
    shortcutsApplicableComponent = appliedTo
    representations.values.forEach { it.registerShortcuts(appliedTo) }
  }

  /*
   * The code below is responsible for creating a toolbar for selecting current [PreviewRepresentation]. The toolbar appears only if there
   * are more than 1 [PreviewRepresentation] is available (accepted) for the current file.
   */
  private fun findAllPreviews() = FileEditorManager.getInstance(project).getAllEditors(file).asIterable()
    .filterIsInstance<TextEditorWithMultiRepresentationPreview<out MultiRepresentationPreview>>()
    .map { it.preview }
    .distinct()

  private fun createActionToolbar(group: ActionGroup): ActionToolbarImpl {
    val toolbar = ActionManager.getInstance().createActionToolbar("top", group, true)
    toolbar.layoutPolicy = ActionToolbar.WRAP_LAYOUT_POLICY
    if (group === ActionGroup.EMPTY_GROUP) {
      toolbar.component.isVisible = false
    }
    ActionToolbarUtil.makeToolbarNavigable(toolbar)
    return toolbar as ActionToolbarImpl
  }

  private class RepresentationOption(val representationName: String, val parent: MultiRepresentationPreview) :
    AnAction(representationName) {
    override fun actionPerformed(e: AnActionEvent) {
      // Here we iterate over all editors as change in selection (write) should trigger updates in all of them
      parent.findAllPreviews().forEach { it.currentRepresentationName = representationName }
    }
  }

  private class RepresentationsSelector(val parent: MultiRepresentationPreview) :
    DropDownAction(null, "Representations", StudioIcons.LayoutEditor.Palette.CUSTOM_VIEW) {
    override fun update(e: AnActionEvent) {
      super.update(e)
      removeAll()

      // We need just a single previewEditor here (any) to retrieve (read) the states and currently selected state
      parent.representations.keys.forEach {
        add(RepresentationOption(it, parent))
      }
      e.presentation.setText(parent.currentRepresentationName, false)
    }

    override fun displayTextInToolbar() = true
  }

  private fun createActionGroup(): ActionGroup {
    val actionGroup = DefaultActionGroup()
    val representationsSelector = RepresentationsSelector(this)
    actionGroup.add(representationsSelector)
    return actionGroup
  }

  private val representationSelectionToolbar: JComponent by lazy {
    AdtPrimaryPanel(BorderLayout()).apply {
      border = BorderFactory.createMatteBorder(0, 0, 1, 0, com.android.tools.adtui.common.border)

      add(createActionToolbar(createActionGroup()))
    }
  }
}

private const val MULTI_REPRESENTATION_PREVIEW = "multi-representation-preview"
