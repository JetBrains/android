package com.android.tools.idea.uibuilder.options

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.editors.fast.FastPreviewConfiguration
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.options.AndroidDesignerBundle.message
import com.intellij.ide.ui.search.SearchableOptionContributor
import com.intellij.ide.ui.search.SearchableOptionProcessor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.labelTable
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.util.messages.Topic
import java.awt.GraphicsEnvironment
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JSlider
import org.jetbrains.android.uipreview.AndroidEditorSettings
import org.jetbrains.annotations.VisibleForTesting

private const val CONFIGURABLE_ID = "nele.options"
private val DISPLAY_NAME =
  if (IdeInfo.getInstance().isAndroidStudio) "UI Tools"
  else message("android.uibuilder.nloptionsconfigurable.displayName")

@VisibleForTesting const val LABEL_TRACK_PAD = "Track Pad"
@VisibleForTesting
const val LABEL_MAGNIFY_ZOOMING_SENSITIVITY = "Magnify zooming (pinch) sensitivity"

private val MAGNIFY_SUPPORTED =
  SystemInfo.isMac && Registry.`is`("actionSystem.mouseGesturesEnabled", true)

class NlOptionsConfigurable : BoundConfigurable(DISPLAY_NAME), SearchableConfigurable {

  fun interface Listener {

    companion object {
      val TOPIC: Topic<Listener> = Topic(Listener::class.java, Topic.BroadcastDirection.TO_CHILDREN)
    }

    fun onOptionsChanged()
  }

  private fun fireOptionsChanged() =
    ApplicationManager.getApplication().messageBus.syncPublisher(Listener.TOPIC).onOptionsChanged()

  private class EditorModeCellRenderer :
    SimpleListCellRenderer<AndroidEditorSettings.EditorMode>() {
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
  private val fastPreviewState = FastPreviewConfiguration.getInstance()

  override fun getId() = CONFIGURABLE_ID

  private fun Row.editorModeComboBox(): Cell<ComboBox<AndroidEditorSettings.EditorMode>> {
    return comboBox(AndroidEditorSettings.EditorMode.values().asList(), EditorModeCellRenderer())
  }

  override fun createPanel(): DialogPanel {
    // The bazel test //tools/adt/idea/searchable-options:searchable_options_test compares the
    // created option list with a static xml file, which doesn't include the options added at
    // runtime. We disable magnify support in headless environment to make this bazel test passes
    // on all platforms. Meanwhile, we use the NlOptionConfigurableSearchableOptionContributorTest
    // unit tests to cover the magnify options created at runtime.
    val showMagnify = MAGNIFY_SUPPORTED && !GraphicsEnvironment.isHeadless()

    return panel {
      group("Default Editor Mode") {
        row("Drawables:") { preferredDrawablesEditorMode = editorModeComboBox().component }
        row("Other Resources (e.g. Layout, Menu, Navigation):") {
          preferredEditorMode = editorModeComboBox().component
        }
        row("Compose files with previews:") {
          editorModeComboBox()
            .bindItem(
              { state.preferredPreviewableEditorMode ?: AndroidEditorSettings.EditorMode.SPLIT },
              state::setPreferredPreviewableEditorMode,
            )
        }
        row("Compose files without previews:") {
          editorModeComboBox()
            .bindItem(
              { state.preferredComposableEditorMode ?: AndroidEditorSettings.EditorMode.CODE },
              state::setPreferredComposableEditorMode,
            )
        }
        row("Other Kotlin files:") {
          editorModeComboBox()
            .bindItem(
              { state.preferredKotlinEditorMode ?: AndroidEditorSettings.EditorMode.CODE },
              state::setPreferredKotlinEditorMode,
            )
        }
      }
      if (showMagnify) {
        val percentageValue = doubleToPercentageValue(state.magnifySensitivity)
        group(LABEL_TRACK_PAD) {
          row(LABEL_MAGNIFY_ZOOMING_SENSITIVITY) {
            val minSensitivityPercentage =
              doubleToPercentageValue(AndroidEditorSettings.MIN_MAGNIFY_SENSITIVITY)
            val defaultSensitivityPercentage =
              doubleToPercentageValue(AndroidEditorSettings.DEFAULT_MAGNIFY_SENSITIVITY)
            val maxSensitivityPercentage =
              doubleToPercentageValue(AndroidEditorSettings.MAX_MAGNIFY_SENSITIVITY)
            magnifySensitivity =
              slider(
                  minSensitivityPercentage,
                  maxSensitivityPercentage,
                  0,
                  (maxSensitivityPercentage - minSensitivityPercentage) / 4,
                )
                .labelTable(
                  mapOf(
                    minSensitivityPercentage to JLabel("Slow"),
                    defaultSensitivityPercentage to JLabel("Default"),
                    maxSensitivityPercentage to JLabel("Fast"),
                  )
                )
                .applyToComponent { value = percentageValue }
                .component
          }
        }
      }

      group("Preview Settings") {
        if (StudioFlags.PREVIEW_ESSENTIALS_MODE.get()) {
          buttonsGroup(message("android.uibuilder.nloptionsconfigurable.resource.usage")) {
            lateinit var defaultModeRadioButton: Cell<JBRadioButton>
            row {
              defaultModeRadioButton =
                radioButton(
                    message("android.uibuilder.nloptionsconfigurable.resource.usage.default")
                  )
                  .bindSelected({ !state.isPreviewEssentialsModeEnabled }) {
                    state.isPreviewEssentialsModeEnabled = !it
                  }
            }
            indent {
              row {
                checkBox("Enable live updates")
                  .bindSelected(fastPreviewState::isEnabled) { fastPreviewState.isEnabled = it }
                  .enabledIf(defaultModeRadioButton.selected)
              }
            }
            row {
              radioButton(
                  message("android.uibuilder.nloptionsconfigurable.resource.usage.essentials")
                )
                // TODO(b/327343295) add "Learn More" link when the DAC page is live
                .comment(message("essentials.mode.hint"))
                .bindSelected({ state.isPreviewEssentialsModeEnabled }) {
                  state.isPreviewEssentialsModeEnabled = it
                }
            }
          }
        } else {
          row {
            checkBox("Enable live updates").bindSelected(fastPreviewState::isEnabled) {
              fastPreviewState.isEnabled = it
            }
          }
        }
      }
    }
  }

  override fun isModified(): Boolean {
    val magnifySensitivityValue = magnifySensitivity?.value
    return super<BoundConfigurable>.isModified() ||
      preferredDrawablesEditorMode.selectedItem != state.preferredDrawableEditorMode ||
      preferredEditorMode.selectedItem != state.preferredEditorMode ||
      (magnifySensitivityValue != null &&
        magnifySensitivityValue != doubleToPercentageValue(state.magnifySensitivity))
  }

  @Throws(ConfigurationException::class)
  override fun apply() {
    super.apply()
    state.preferredDrawableEditorMode =
      preferredDrawablesEditorMode.selectedItem as AndroidEditorSettings.EditorMode
    state.preferredEditorMode = preferredEditorMode.selectedItem as AndroidEditorSettings.EditorMode
    magnifySensitivity?.let { state.magnifySensitivity = percentageValueToDouble(it.value) }
    fireOptionsChanged()
  }

  override fun reset() {
    super<BoundConfigurable>.reset()

    // Handle the case where preferredDrawableEditorMode and preferredEditorMode were not set for
    // the first time yet.
    if (state.preferredDrawableEditorMode == null && state.preferredEditorMode == null) {
      // Default drawables to SPLIT and other resource types to DESIGN
      preferredDrawablesEditorMode.selectedItem = AndroidEditorSettings.EditorMode.SPLIT
      preferredEditorMode.selectedItem = AndroidEditorSettings.EditorMode.DESIGN
    } else {
      preferredDrawablesEditorMode.selectedItem = state.preferredDrawableEditorMode
      preferredEditorMode.selectedItem = state.preferredEditorMode
    }

    magnifySensitivity?.value = doubleToPercentageValue(state.magnifySensitivity)
  }
}

/**
 * Helper function to convert percentage value to double. For example, when [percentage] is 22, the
 * return value is 0.22
 */
private fun percentageValueToDouble(percentage: Int): Double = percentage * 0.01

/**
 * Helper function to convert a double value to percentage value. For example, when [double] is
 * 0.44, the return value is 44.
 */
private fun doubleToPercentageValue(double: Double): Int = (double * 100).toInt()

/**
 * The magnify configurations is added conditionally, we cannot use the static xml files to define
 * the options. Thus, we add the corresponding options at runtime here.
 */
class NlOptionConfigurableSearchableOptionContributor : SearchableOptionContributor() {

  override fun processOptions(processor: SearchableOptionProcessor) {
    if (MAGNIFY_SUPPORTED) {
      processor.addOptions("track pad", null, LABEL_TRACK_PAD, CONFIGURABLE_ID, DISPLAY_NAME, false)
      processor.addOptions(
        "magnify pinch zooming sensitivity",
        null,
        LABEL_MAGNIFY_ZOOMING_SENSITIVITY,
        CONFIGURABLE_ID,
        DISPLAY_NAME,
        false,
      )
    }
  }
}
