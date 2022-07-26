package com.android.tools.idea.uibuilder.options

import com.android.tools.idea.IdeInfo
import com.intellij.ide.ui.search.SearchableOptionContributor
import com.intellij.ide.ui.search.SearchableOptionProcessor
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.labelTable
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.android.uipreview.AndroidEditorSettings
import org.jetbrains.annotations.VisibleForTesting
import java.awt.GraphicsEnvironment
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JSlider

private const val CONFIGURABLE_ID = "nele.options"
private val DISPLAY_NAME = if (IdeInfo.getInstance().isAndroidStudio) "Design Tools" else "Android Design Tools"

@VisibleForTesting const val LABEL_TRACK_PAD = "Track Pad"
@VisibleForTesting const val LABEL_MAGNIFY_ZOOMING_SENSITIVITY = "Magnify zooming (pinch) sensitivity"

private val MAGNIFY_SUPPORTED = SystemInfo.isMac && Registry.`is`("actionSystem.mouseGesturesEnabled", true)

class NlOptionsConfigurable : BoundConfigurable(DISPLAY_NAME), SearchableConfigurable {

  private class EditorModeCellRenderer : SimpleListCellRenderer<AndroidEditorSettings.EditorMode>() {
    override fun customize(
      list: JList<out AndroidEditorSettings.EditorMode>,
      value: AndroidEditorSettings.EditorMode?,
      index: Int,
      selected: Boolean,
      hasFocus: Boolean,
    ) {
      value?.let {
        text = it.displayName
        icon = it.icon
      }
    }
  }

  private lateinit var preferredDrawablesEditorMode: ComboBox<AndroidEditorSettings.EditorMode>
  private lateinit var preferredEditorMode: ComboBox<AndroidEditorSettings.EditorMode>

  private var magnifySensitivity: JSlider? = null

  private val state = AndroidEditorSettings.getInstance().globalState

  override fun getId() = CONFIGURABLE_ID

  private fun Row.editorModeComboBox(): Cell<ComboBox<AndroidEditorSettings.EditorMode>> {
    return comboBox(AndroidEditorSettings.EditorMode.values().asList(), EditorModeCellRenderer())
  }

  override fun createPanel(): DialogPanel {
    // The bazel test //tools/adt/idea/searchable-options:searchable_options_test compares the created option list with a static xml file,
    // which doesn't include the options added at runtime.
    // We disable magnify support in headless environment to make this bazel test passes on all platform. In thee meanwhile, we use the unit
    // tests in NlOptionConfigurableSearchableOptionContributorTest to cover the magnify options created at runtime.
    val showMagnify = MAGNIFY_SUPPORTED && !GraphicsEnvironment.isHeadless()

    return panel {
      row {
        checkBox("Show lint icons on design surface")
          .bindSelected(state::isShowLint, state::setShowLint)
      }
      group("Default Editor Mode") {
        row("Drawables:") {
          preferredDrawablesEditorMode = editorModeComboBox().component
        }
        row("Other Resources (e.g. Layout, Menu, Navigation):") {
          preferredEditorMode = editorModeComboBox().component
        }
        row("Compose files:") {
          editorModeComboBox()
            .bindItem({ state.preferredComposableEditorMode ?: AndroidEditorSettings.EditorMode.SPLIT },
                      state::setPreferredComposableEditorMode)
        }
        row("Other Kotlin files:") {
          editorModeComboBox()
            .bindItem({ state.preferredKotlinEditorMode ?: AndroidEditorSettings.EditorMode.CODE },
                      state::setPreferredKotlinEditorMode)
        }
      }
      if (showMagnify) {
        val percentageValue = doubleToPercentageValue(state.magnifySensitivity)
        group(LABEL_TRACK_PAD) {
          row(LABEL_MAGNIFY_ZOOMING_SENSITIVITY) {
            val minSensitivityPercentage = doubleToPercentageValue(AndroidEditorSettings.MIN_MAGNIFY_SENSITIVITY)
            val defaultSensitivityPercentage = doubleToPercentageValue(AndroidEditorSettings.DEFAULT_MAGNIFY_SENSITIVITY)
            val maxSensitivityPercentage = doubleToPercentageValue(AndroidEditorSettings.MAX_MAGNIFY_SENSITIVITY)
            magnifySensitivity = slider(minSensitivityPercentage, maxSensitivityPercentage, 0, (maxSensitivityPercentage - minSensitivityPercentage) / 4)
              .labelTable(mapOf(
                minSensitivityPercentage to JLabel("Slow"),
                defaultSensitivityPercentage to JLabel("Default"),
                maxSensitivityPercentage to JLabel("Fast")
              )).applyToComponent {
                value = percentageValue
              }.component
          }
        }
      }
    }
  }

  override fun isModified(): Boolean {
    val magnifySensitivityValue = magnifySensitivity?.value
    return super<BoundConfigurable>.isModified()
      || preferredDrawablesEditorMode.selectedItem != state.preferredDrawableEditorMode
      || preferredEditorMode.selectedItem != state.preferredEditorMode
      || (magnifySensitivityValue != null && magnifySensitivityValue != doubleToPercentageValue(state.magnifySensitivity))
  }

  @Throws(ConfigurationException::class)
  override fun apply() {
    super.apply()
    state.preferredDrawableEditorMode = preferredDrawablesEditorMode.selectedItem as AndroidEditorSettings.EditorMode
    state.preferredEditorMode = preferredEditorMode.selectedItem as AndroidEditorSettings.EditorMode
    magnifySensitivity?.let {
      state.magnifySensitivity = percentageValueToDouble(it.value)
    }
  }

  override fun reset() {
    super<BoundConfigurable>.reset()

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

    magnifySensitivity?.value = doubleToPercentageValue(state.magnifySensitivity)
  }
}

/**
 * Helper function to convert percentage value to double. For example, when [percentage] is 22, the return value is 0.22
 */
private fun percentageValueToDouble(percentage: Int): Double = percentage * 0.01
/**
 * Helper function to convert a double value to percentage value. For example, when [double] is 0.44, the return value is 44.
 */
private fun doubleToPercentageValue(double: Double): Int = (double * 100).toInt()

/**
 * The magnify configurations is added conditionally, we cannot use the static xml files to define the options.
 * Thus, we add the corresponding options at runtime here.
 */
class NlOptionConfigurableSearchableOptionContributor : SearchableOptionContributor() {

  override fun processOptions(processor: SearchableOptionProcessor) {
    if (MAGNIFY_SUPPORTED) {
      processor.addOptions("track pad", null, LABEL_TRACK_PAD, CONFIGURABLE_ID, DISPLAY_NAME, false)
      processor.addOptions("magnify pinch zooming sensitivity", null,
                           LABEL_MAGNIFY_ZOOMING_SENSITIVITY, CONFIGURABLE_ID, DISPLAY_NAME, false)
    }
  }
}
