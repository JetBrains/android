package com.android.tools.idea.uibuilder.options

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.panel
import org.jetbrains.android.uipreview.AndroidEditorSettings
import org.jetbrains.annotations.Nls

class NlOptionsConfigurable : SearchableConfigurable, Configurable.NoScroll {
  private val preferXmlEditor = JBCheckBox("Prefer XML editor")
  private val showLint = JBCheckBox("Show lint icons on design surface")
  private val hideForNonLayoutFiles = JBCheckBox("Hide preview window when editing non-layout files")

  private val state = AndroidEditorSettings.getInstance().globalState

  override fun getId() = "nele.options"

  override fun createComponent() = panel {
    row { preferXmlEditor() }
    row { showLint() }
    if (!StudioFlags.NELE_SPLIT_EDITOR.get()) {
      row { hideForNonLayoutFiles() }
    }
  }

  override fun isModified() =
    preferXmlEditor.isSelected != state.isPreferXmlEditor
    || showLint.isSelected != state.isShowLint
    || hideForNonLayoutFiles.isSelected != state.isHideForNonLayoutFiles

  @Throws(ConfigurationException::class)
  override fun apply() {
    state.isPreferXmlEditor = preferXmlEditor.isSelected
    state.isShowLint = showLint.isSelected
    state.isHideForNonLayoutFiles = hideForNonLayoutFiles.isSelected
  }

  override fun reset() {
    preferXmlEditor.isSelected = state.isPreferXmlEditor
    showLint.isSelected = state.isShowLint
    hideForNonLayoutFiles.isSelected = state.isHideForNonLayoutFiles
  }

  @Nls
  override fun getDisplayName() = if (IdeInfo.getInstance().isAndroidStudio) "Layout Editor" else "Android Layout Editor"
}
