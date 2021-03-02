package com.android.tools.idea.uibuilder.options

import com.android.tools.idea.IdeInfo
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.panel
import org.jetbrains.android.uipreview.AndroidEditorSettings
import org.jetbrains.annotations.Nls
import javax.swing.JComboBox
import javax.swing.JList

class NlOptionsConfigurable : SearchableConfigurable, Configurable.NoScroll {

  private class EditorModeComboBox : JComboBox<AndroidEditorSettings.EditorMode>(AndroidEditorSettings.EditorMode.values()) {
    init {
      setRenderer(EditorModeCellRenderer())
    }

    private class EditorModeCellRenderer : SimpleListCellRenderer<AndroidEditorSettings.EditorMode>() {
      override fun customize(list: JList<out AndroidEditorSettings.EditorMode>,
                             value: AndroidEditorSettings.EditorMode?,
                             index: Int,
                             selected: Boolean,
                             hasFocus: Boolean) {
        value?.let {
          text = it.displayName
          icon = it.icon
        }
      }
    }
  }

  private val showLint = JBCheckBox("Show lint icons on design surface")
  private val preferredComposableEditorMode = EditorModeComboBox()
  private val preferredDrawablesEditorMode = EditorModeComboBox()
  private val preferredEditorMode = EditorModeComboBox()
  private val preferredKotlinEditorMode = EditorModeComboBox()

  private val state = AndroidEditorSettings.getInstance().globalState

  override fun getId() = "nele.options"

  override fun createComponent() = panel {
    row { showLint() }
    titledRow("Default Editor Mode") {
      row("Drawables:") { preferredDrawablesEditorMode() }
      row("Other Resources (e.g. Layout, Menu, Navigation):") { preferredEditorMode() }
      row("Compose files:") { preferredComposableEditorMode() }
      row("Other Kotlin files:") { preferredKotlinEditorMode() }
    }
  }

  override fun isModified() =
    showLint.isSelected != state.isShowLint
    || preferredDrawablesEditorMode.selectedItem != state.preferredDrawableEditorMode
    || preferredEditorMode.selectedItem != state.preferredEditorMode
    || preferredComposableEditorMode.selectedItem != state.preferredComposableEditorMode
    || preferredKotlinEditorMode.selectedItem != state.preferredKotlinEditorMode

  @Throws(ConfigurationException::class)
  override fun apply() {
    state.isShowLint = showLint.isSelected
    state.preferredDrawableEditorMode = preferredDrawablesEditorMode.selectedItem as AndroidEditorSettings.EditorMode
    state.preferredEditorMode = preferredEditorMode.selectedItem as AndroidEditorSettings.EditorMode
    state.preferredComposableEditorMode = preferredComposableEditorMode.selectedItem as AndroidEditorSettings.EditorMode
    state.preferredKotlinEditorMode = preferredKotlinEditorMode.selectedItem as AndroidEditorSettings.EditorMode
  }

  override fun reset() {
    showLint.isSelected = state.isShowLint

    // Handle the case where preferredDrawableEditorMode and preferredEditorMode were not set for the first time yet.
    if (state.preferredDrawableEditorMode == null && state.preferredEditorMode == null) {
      if (state.isPreferXmlEditor) {
        // Preserve the user preference if they had set the old "Prefer XML editor" option.
        preferredDrawablesEditorMode.selectedItem = AndroidEditorSettings.EditorMode.CODE
        preferredEditorMode.selectedItem = AndroidEditorSettings.EditorMode.CODE
      }
      else {
        // Otherwise default drawables to SPLIT and other resource types to DESIGN
        preferredDrawablesEditorMode.selectedItem = AndroidEditorSettings.EditorMode.SPLIT
        preferredEditorMode.selectedItem = AndroidEditorSettings.EditorMode.DESIGN
      }
    }
    else {
      preferredDrawablesEditorMode.selectedItem = state.preferredDrawableEditorMode
      preferredEditorMode.selectedItem = state.preferredEditorMode
    }

    preferredComposableEditorMode.selectedItem = state.preferredComposableEditorMode ?: AndroidEditorSettings.EditorMode.SPLIT
    preferredKotlinEditorMode.selectedItem = state.preferredKotlinEditorMode ?: AndroidEditorSettings.EditorMode.CODE
  }

  @Nls
  override fun getDisplayName() = if (IdeInfo.getInstance().isAndroidStudio) "Layout Editor" else "Android Layout Editor"
}
