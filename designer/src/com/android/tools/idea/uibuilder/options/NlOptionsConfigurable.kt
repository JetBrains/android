package com.android.tools.idea.uibuilder.options

import com.android.tools.idea.IdeInfo
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.panel
import org.jetbrains.android.uipreview.AndroidEditorSettings
import org.jetbrains.annotations.Nls
import java.util.Hashtable
import javax.swing.DefaultBoundedRangeModel
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JSlider

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
  private val magnifySensitivity = JSlider(JSlider.HORIZONTAL).apply {
    model = DefaultBoundedRangeModel()
    val qualityLabels = Hashtable<Int, JComponent>()
    val minSensitivityPercentage = doubleToPercentageValue(AndroidEditorSettings.MIN_MAGNIFY_SENSITIVITY)
    val defaultSensitivityPercentage = doubleToPercentageValue(AndroidEditorSettings.DEFAULT_MAGNIFY_SENSITIVITY)
    val maxSensitivityPercentage = doubleToPercentageValue(AndroidEditorSettings.MAX_MAGNIFY_SENSITIVITY)

    qualityLabels[minSensitivityPercentage] = JLabel("Slow")
    qualityLabels[defaultSensitivityPercentage] = JLabel("Default")
    qualityLabels[maxSensitivityPercentage] = JLabel("Fast")
    labelTable = qualityLabels
    paintLabels = true
    paintTicks = true
    // The default value doesn't matter here because it will be overwrote by state value.
    model = DefaultBoundedRangeModel(minSensitivityPercentage, 0, minSensitivityPercentage, maxSensitivityPercentage)
    // Show the ticks on the slider.
    majorTickSpacing = (maxSensitivityPercentage - minSensitivityPercentage) / 4
  }

  private val state = AndroidEditorSettings.getInstance().globalState

  override fun getId() = "nele.options"

  override fun createComponent(): JComponent {
    val hasTrackPad = SystemInfo.isMac && Registry.`is`("actionSystem.mouseGesturesEnabled", true)

    return panel {
      row { showLint() }
      titledRow("Default Editor Mode") {
        row("Drawables:") { preferredDrawablesEditorMode() }
        row("Other Resources (e.g. Layout, Menu, Navigation):") { preferredEditorMode() }
        row("Compose files:") { preferredComposableEditorMode() }
        row("Other Kotlin files:") { preferredKotlinEditorMode() }
      }
      if (hasTrackPad) {
        val percentageValue = doubleToPercentageValue(state.magnifySensitivity)
        titledRow("Track Pad") {
          row("Magnify zooming (pinch) sensitivity") { magnifySensitivity.apply { magnifySensitivity.value = percentageValue }() }
        }
      }
    }
  }

  override fun isModified() =
    showLint.isSelected != state.isShowLint
    || preferredDrawablesEditorMode.selectedItem != state.preferredDrawableEditorMode
    || preferredEditorMode.selectedItem != state.preferredEditorMode
    || preferredComposableEditorMode.selectedItem != state.preferredComposableEditorMode
    || preferredKotlinEditorMode.selectedItem != state.preferredKotlinEditorMode
    || magnifySensitivity.value != doubleToPercentageValue(state.magnifySensitivity)

  @Throws(ConfigurationException::class)
  override fun apply() {
    state.isShowLint = showLint.isSelected
    state.preferredDrawableEditorMode = preferredDrawablesEditorMode.selectedItem as AndroidEditorSettings.EditorMode
    state.preferredEditorMode = preferredEditorMode.selectedItem as AndroidEditorSettings.EditorMode
    state.preferredComposableEditorMode = preferredComposableEditorMode.selectedItem as AndroidEditorSettings.EditorMode
    state.preferredKotlinEditorMode = preferredKotlinEditorMode.selectedItem as AndroidEditorSettings.EditorMode
    state.magnifySensitivity = percentageValueToDouble(magnifySensitivity.value)
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

    magnifySensitivity.value = doubleToPercentageValue(state.magnifySensitivity)
  }

  @Nls
  override fun getDisplayName() = if (IdeInfo.getInstance().isAndroidStudio) "Design Tools" else "Android Design Tools"
}

/**
 * Helper function to convert percentage value to double. For example, when [percentage] is 22, the return value is 0.22
 */
private fun percentageValueToDouble(percentage: Int): Double = percentage * 0.01
/**
 * Helper function to convert a double value to percentage value. For example, when [double] is 0.44, the return value is 44.
 */
private fun doubleToPercentageValue(double: Double): Int = (double * 100).toInt()
