/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.compose

import com.android.tools.configurations.Configuration
import com.android.tools.configurations.DEVICE_CLASS_DESKTOP_ID
import com.android.tools.configurations.DEVICE_CLASS_FOLDABLE_ID
import com.android.tools.configurations.DEVICE_CLASS_PHONE_ID
import com.android.tools.configurations.DEVICE_CLASS_TABLET_ID
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.compose.preview.PreviewGroup
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorBlindMode
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorConverter
import com.android.tools.preview.ComposePreviewElementInstance
import com.android.tools.preview.ParametrizedComposePreviewElementInstance
import com.android.tools.preview.PreviewConfiguration
import com.android.tools.preview.PreviewDisplaySettings
import com.android.tools.preview.SingleComposePreviewElementInstance
import com.android.tools.preview.config.referenceDeviceIds
import org.jetbrains.annotations.VisibleForTesting

private const val DEVICE_CLASS_LANDSCAPE_PHONE_ID = "$DEVICE_CLASS_PHONE_ID-landscape"
private val idToName =
  mapOf(
    DEVICE_CLASS_PHONE_ID to "Medium Phone",
    DEVICE_CLASS_FOLDABLE_ID to "Unfolded Foldable",
    DEVICE_CLASS_TABLET_ID to "Medium Tablet",
    DEVICE_CLASS_DESKTOP_ID to "Desktop",
    DEVICE_CLASS_LANDSCAPE_PHONE_ID to "Medium Phone-Landscape"
  )
private val fontScales =
  mapOf(
    0.85f to "85%",
    1.0f to "100%",
    1.15f to "115%",
    1.3f to "130%",
    1.8f to "180%",
    2.0f to "200%",
  )
private val lightDarkModes =
  mapOf(Configuration.UI_MODE_NIGHT_NO to "Light", Configuration.UI_MODE_NIGHT_YES to "Dark")

/**
 * A filter that is applied in "UI Check Mode". When enabled, it will get the `selected` instance
 * and generate multiple previews, one per reference device for the user to check.
 */
sealed class UiCheckModeFilter {
  var modelsWithErrors: Set<NlModel>? = null
  abstract val basePreviewInstance: ComposePreviewElementInstance?

  abstract fun filterPreviewInstances(
    previewInstances: Collection<ComposePreviewElementInstance>
  ): Collection<ComposePreviewElementInstance>

  abstract fun filterGroups(groups: Set<PreviewGroup.Named>): Set<PreviewGroup.Named>

  object Disabled : UiCheckModeFilter() {
    override val basePreviewInstance = null

    override fun filterPreviewInstances(
      previewInstances: Collection<ComposePreviewElementInstance>
    ): Collection<ComposePreviewElementInstance> = previewInstances

    override fun filterGroups(groups: Set<PreviewGroup.Named>): Set<PreviewGroup.Named> = groups
  }

  class Enabled(selected: ComposePreviewElementInstance) : UiCheckModeFilter() {
    override val basePreviewInstance = selected

    private val uiCheckPreviews: Collection<ComposePreviewElementInstance> =
      calculatePreviews(selected)

    /** Calculate the groups. This will be all the groups available in [uiCheckPreviews] if any. */
    private val uiCheckPreviewGroups =
      uiCheckPreviews
        .mapNotNull { it.displaySettings.group?.let { group -> PreviewGroup.namedGroup(group) } }
        .toSet()

    override fun filterPreviewInstances(
      previewInstances: Collection<ComposePreviewElementInstance>
    ): Collection<ComposePreviewElementInstance> =
      if (basePreviewInstance in previewInstances) uiCheckPreviews else emptyList()

    override fun filterGroups(groups: Set<PreviewGroup.Named>): Set<PreviewGroup.Named> =
      uiCheckPreviewGroups

    @VisibleForTesting
    companion object {
      fun calculatePreviews(
        base: ComposePreviewElementInstance
      ): Collection<ComposePreviewElementInstance> {

        val composePreviewInstances = mutableListOf<ComposePreviewElementInstance>()
        composePreviewInstances.addAll(deviceSizePreviews(base))
        composePreviewInstances.addAll(fontSizePreviews(base))
        composePreviewInstances.addAll(lightDarkPreviews(base))

        val isColorBlindModeUICheckEnabled = StudioFlags.NELE_COMPOSE_UI_CHECK_COLORBLIND_MODE.get()
        if (isColorBlindModeUICheckEnabled) {
          composePreviewInstances.addAll(colorBlindPreviews(base))
        }
        return composePreviewInstances
      }
    }
  }
}

private fun deviceSizePreviews(
  baseInstance: ComposePreviewElementInstance
): List<ComposePreviewElementInstance> {
  val baseConfig = baseInstance.configuration
  val baseDisplaySettings = baseInstance.displaySettings
  val effectiveDeviceIds =
    referenceDeviceIds +
      mapOf(
        "spec:parent=$DEVICE_CLASS_PHONE_ID,orientation=landscape" to
          DEVICE_CLASS_LANDSCAPE_PHONE_ID,
      )
  return effectiveDeviceIds.keys.map { device ->
    val config = baseConfig.copy(deviceSpec = device)
    val displaySettings =
      baseDisplaySettings.copy(
        name = "${idToName[effectiveDeviceIds[device]]} - ${baseDisplaySettings.name}",
        group = message("ui.check.mode.screen.size.group"),
        showDecoration = true,
      )
    baseInstance.createDerivedInstance(displaySettings, config)
  }
}

private fun fontSizePreviews(
  baseInstance: ComposePreviewElementInstance
): List<ComposePreviewElementInstance> {
  val baseConfig = baseInstance.configuration
  val baseDisplaySettings = baseInstance.displaySettings
  return fontScales.map { (value, name) ->
    val config = baseConfig.copy(fontScale = value)
    val displaySettings =
      baseDisplaySettings.copy(
        name = "$name - ${baseDisplaySettings.name}",
        group = message("ui.check.mode.font.scale.group"),
      )
    baseInstance.createDerivedInstance(displaySettings, config)
  }
}

private fun lightDarkPreviews(
  baseInstance: ComposePreviewElementInstance
): List<ComposePreviewElementInstance> {
  val baseConfig = baseInstance.configuration
  val baseDisplaySettings = baseInstance.displaySettings
  return lightDarkModes.map { (value, name) ->
    val config =
      baseConfig.copy(uiMode = (baseConfig.uiMode and Configuration.UI_MODE_TYPE_MASK) or value)
    val displaySettings =
      baseDisplaySettings.copy(
        name = "$name - ${baseDisplaySettings.name}",
        group = message("ui.check.mode.light.dark.group"),
      )
    baseInstance.createDerivedInstance(displaySettings, config)
  }
}

private fun colorBlindPreviews(
  baseInstance: ComposePreviewElementInstance
): List<ComposePreviewElementInstance> {
  val baseConfig = baseInstance.configuration
  val baseDisplaySettings = baseInstance.displaySettings
  return ColorBlindMode.values().map { colorBlindMode ->
    val colorFilterBaseConfig =
      baseConfig.copy(
        imageTransformation = { image -> ColorConverter(colorBlindMode).convert(image, image) },
      )
    val displaySettings =
      baseDisplaySettings.copy(
        name = colorBlindMode.displayName,
        group = message("ui.check.mode.screen.accessibility.group"),
        showDecoration = false,
      )
    baseInstance.createDerivedInstance(displaySettings, colorFilterBaseConfig)
  }
}

/**
 * Derives a new [ComposePreviewElementInstance] from an existing one, replacing the
 * [PreviewDisplaySettings] and the [PreviewConfiguration].
 */
private fun ComposePreviewElementInstance.createDerivedInstance(
  displaySettings: PreviewDisplaySettings,
  config: PreviewConfiguration
): ComposePreviewElementInstance {
  val singleInstance =
    SingleComposePreviewElementInstance(
      methodFqn,
      displaySettings,
      previewElementDefinitionPsi,
      previewBodyPsi,
      config,
    )
  return if (this is ParametrizedComposePreviewElementInstance) {
    ParametrizedComposePreviewElementInstance(
      singleInstance,
      "",
      providerClassFqn,
      index,
      maxIndex,
    )
  } else {
    singleInstance
  }
}
