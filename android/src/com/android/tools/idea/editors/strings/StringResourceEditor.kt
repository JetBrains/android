/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.strings

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.res.ResourceNotificationManager
import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import org.jetbrains.android.facet.AndroidFacet
import java.awt.Font
import java.beans.PropertyChangeListener
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Icon
import javax.swing.JComponent

/**
 * The editor for string resources and translations.
 *
 * This editor is mostly a wrapper around the [StringResourceViewPanel] which holds most of the functionality.
 */
class StringResourceEditor(private val file: StringsVirtualFile) : UserDataHolderBase(), FileEditor {
  // We sometimes get extra calls to `selectNotify`. This ensures that we know when
  // those calls represent a real transition.
  private val selected = AtomicBoolean()
  private val resourceChangeListener = ResourceNotificationManager.ResourceChangeListener { reason ->
    if (reason.contains(ResourceNotificationManager.Reason.RESOURCE_EDIT)) {
      panel.reloadData()
    }
  }

  private var resourceVersion = file.facet.let {
    ResourceNotificationManager.getInstance(it.module.project).getCurrentVersion(it, /* file = */ null, /* configuration = */ null)
  }

  /** The [StringResourceViewPanel] that holds most of the UI. */
  lateinit var panel: StringResourceViewPanel
    private set

  init {
    // Post-startup activities (such as when reopening last open editors) are run from a background thread
    UIUtil.invokeAndWaitIfNeeded(Runnable { panel = StringResourceViewPanel(file.facet, this) })
  }

  override fun getFile() = file

  override fun getComponent(): JComponent = panel.loadingPanel

  override fun getPreferredFocusedComponent(): JComponent = panel.preferredFocusedComponent

  override fun getName() = "String Resource Editor"

  override fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState.INSTANCE

  override fun setState(state: FileEditorState) {}

  override fun isModified() = false

  override fun isValid() = true

  override fun selectNotify() {
    // TODO(b/200817330): Figure out whether it is worth updating the editor when the files change
    //  and find a way to do it that does not cause edits in the editor itself to trigger
    //  those updates. The approach enabled below unfortunately breaks the editor because the call to
    //  reload the data is a non-trivial operation with a progress bar and everything. It also
    //  deselects the current cell and kicks the user out of the editing fields at the bottom.
    //  This is off by default.
    if (StudioFlags.TRANSLATIONS_EDITOR_SYNCHRONIZATION.get() && selected.compareAndSet(false, true)) addListener()
  }

  override fun deselectNotify() {
    if (selected.compareAndSet(true, false)) removeListener()
  }

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

  override fun getBackgroundHighlighter(): BackgroundEditorHighlighter? = null

  override fun getCurrentLocation(): FileEditorLocation? = null

  override fun getStructureViewBuilder(): StructureViewBuilder? = null

  override fun dispose() {}

  override fun toString() = "StringResourceEditor ${panel.facet} ${System.identityHashCode(this)}"

  private fun addListener() {
    val facet: AndroidFacet = file.facet
    val latest = ResourceNotificationManager.getInstance(facet.module.project)
      .addListener(resourceChangeListener, facet, /* file = */ null, /* configuration = */ null)
    if (resourceVersion != latest) panel.reloadData()
  }

  private fun removeListener() {
    val facet: AndroidFacet = file.facet
    val manager = ResourceNotificationManager.getInstance(facet.module.project)
    resourceVersion = manager.getCurrentVersion(facet, /* file = */ null, /* configuration = */ null)
    manager.removeListener(resourceChangeListener, facet, /* file = */ null, /* configuration = */ null)
  }

  companion object {
    /** An [Icon] representing the editor. */
    @JvmField
    val ICON: Icon = StudioIcons.LayoutEditor.Toolbar.LANGUAGE

    /** Creates a font to use for certain components in the editor. */
    @JvmStatic
    fun getFont(defaultFont: Font): Font = JBFont.create(Font(Font.DIALOG, Font.PLAIN, defaultFont.size), defaultFont !is JBFont)
  }
}
