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
package com.android.tools.idea.preview.uicheck

import com.android.tools.configurations.Configuration
import com.android.tools.configurations.DEVICE_CLASS_DESKTOP_ID
import com.android.tools.configurations.DEVICE_CLASS_FOLDABLE_ID
import com.android.tools.configurations.DEVICE_CLASS_PHONE_ID
import com.android.tools.configurations.DEVICE_CLASS_TABLET_ID
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.concurrency.FlowableCollection
import com.android.tools.idea.concurrency.asCollection
import com.android.tools.idea.preview.PreviewBundle.message
import com.android.tools.idea.preview.groups.PreviewGroup
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorBlindMode
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorConverter
import com.android.tools.preview.PreviewElementInstance
import com.android.tools.preview.config.referenceDeviceIds
import org.jetbrains.annotations.VisibleForTesting

private const val DEVICE_CLASS_LANDSCAPE_PHONE_ID = "$DEVICE_CLASS_PHONE_ID-landscape"
private val idToName =
  mapOf(
    DEVICE_CLASS_PHONE_ID to "Medium Phone",
    DEVICE_CLASS_FOLDABLE_ID to "Unfolded Foldable",
    DEVICE_CLASS_TABLET_ID to "Medium Tablet",
    DEVICE_CLASS_DESKTOP_ID to "Desktop",
    DEVICE_CLASS_LANDSCAPE_PHONE_ID to "Medium Phone-Landscape",
  )
private val wearSpecToName =
  mapOf(
    "id:wearos_large_round" to "Wear OS Large Round",
    "id:wearos_small_round" to "Wear OS Small Round",
    "id:wearos_square" to "Wear OS Square",
    "id:wearos_rect" to "Wear OS Rectangular",
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
sealed class UiCheckModeFilter<T : PreviewElementInstance<*>> {
  var modelsWithErrors: Set<NlModel>? = null
  abstract val basePreviewInstance: T?

  abstract fun filterPreviewInstances(
    previewInstances: FlowableCollection<T>
  ): FlowableCollection<T>

  abstract fun filterGroups(groups: Set<PreviewGroup.Named>): Set<PreviewGroup.Named>

  class Disabled<T : PreviewElementInstance<*>> : UiCheckModeFilter<T>() {
    override val basePreviewInstance = null

    override fun filterPreviewInstances(
      previewInstances: FlowableCollection<T>
    ): FlowableCollection<T> = previewInstances

    override fun filterGroups(groups: Set<PreviewGroup.Named>): Set<PreviewGroup.Named> = groups
  }

  class Enabled<T : PreviewElementInstance<*>>(selected: T, isWearPreview: Boolean) :
    UiCheckModeFilter<T>() {
    override val basePreviewInstance = selected

    private val uiCheckPreviews: Collection<T> = calculatePreviews(selected, isWearPreview)

    /** Calculate the groups. This will be all the groups available in [uiCheckPreviews] if any. */
    private val uiCheckPreviewGroups =
      uiCheckPreviews
        .mapNotNull { it.displaySettings.group?.let { group -> PreviewGroup.namedGroup(group) } }
        .toSet()

    override fun filterPreviewInstances(
      previewInstances: FlowableCollection<T>
    ): FlowableCollection<T> =
      when (previewInstances) {
        is FlowableCollection.Uninitialized -> FlowableCollection.Uninitialized
        is FlowableCollection.Present ->
          if (basePreviewInstance in previewInstances.asCollection())
            FlowableCollection.Present(uiCheckPreviews)
          else FlowableCollection.Present(emptyList())
      }

    override fun filterGroups(groups: Set<PreviewGroup.Named>): Set<PreviewGroup.Named> =
      uiCheckPreviewGroups

    @VisibleForTesting
    companion object {
      fun <T : PreviewElementInstance<*>> calculatePreviews(
        base: T?,
        isWearPreview: Boolean,
      ): Collection<T> {

        if (base == null) {
          return emptyList()
        }
        val previewInstances = mutableListOf<T>()
        if (isWearPreview) {
          previewInstances.addAll(wearPreviews(base))
        } else {
          previewInstances.addAll(deviceSizePreviews(base))
          previewInstances.addAll(fontSizePreviews(base))
          previewInstances.addAll(lightDarkPreviews(base))
          previewInstances.addAll(colorBlindPreviews(base))
        }
        return previewInstances
      }
    }
  }
}

private fun <T : PreviewElementInstance<*>> wearPreviews(baseInstance: T): List<T> {
  val baseConfig = baseInstance.configuration
  val baseDisplaySettings = baseInstance.displaySettings
  return wearSpecToName
    .map { (deviceSpec, name) ->
      val config = baseConfig.copy(deviceSpec = deviceSpec)
      val displaySettings =
        baseDisplaySettings.copy(
          name = "$name - ${baseDisplaySettings.name}",
          group = message("ui.check.mode.wear.group"),
          showDecoration = true,
        )
      baseInstance.createDerivedInstance(displaySettings, config)
    }
    .filterIsInstance(baseInstance::class.java)
}

private fun <T : PreviewElementInstance<*>> deviceSizePreviews(baseInstance: T): List<T> {
  val baseConfig = baseInstance.configuration
  val baseDisplaySettings = baseInstance.displaySettings
  val effectiveDeviceIds =
    referenceDeviceIds +
      mapOf(
        "spec:parent=$DEVICE_CLASS_PHONE_ID,orientation=landscape" to
          DEVICE_CLASS_LANDSCAPE_PHONE_ID
      )
  return effectiveDeviceIds.keys
    .map { device ->
      val config = baseConfig.copy(deviceSpec = device)
      val displaySettings =
        baseDisplaySettings.copy(
          name = "${idToName[effectiveDeviceIds[device]]} - ${baseDisplaySettings.name}",
          group = message("ui.check.mode.screen.size.group"),
          showDecoration = true,
        )
      baseInstance.createDerivedInstance(displaySettings, config)
    }
    .filterIsInstance(baseInstance::class.java)
}

private fun <T : PreviewElementInstance<*>> fontSizePreviews(baseInstance: T): List<T> {
  val baseConfig = baseInstance.configuration
  val baseDisplaySettings = baseInstance.displaySettings
  return fontScales
    .map { (value, name) ->
      val config = baseConfig.copy(fontScale = value)
      val displaySettings =
        baseDisplaySettings.copy(
          name = "$name - ${baseDisplaySettings.name}",
          group = message("ui.check.mode.font.scale.group"),
        )
      baseInstance.createDerivedInstance(displaySettings, config)
    }
    .filterIsInstance(baseInstance::class.java)
}

private fun <T : PreviewElementInstance<*>> lightDarkPreviews(baseInstance: T): List<T> {
  val baseConfig = baseInstance.configuration
  val baseDisplaySettings = baseInstance.displaySettings
  return lightDarkModes
    .map { (value, name) ->
      val config =
        baseConfig.copy(uiMode = (baseConfig.uiMode and Configuration.UI_MODE_TYPE_MASK) or value)
      val displaySettings =
        baseDisplaySettings.copy(
          name = "$name - ${baseDisplaySettings.name}",
          group = message("ui.check.mode.light.dark.group"),
        )
      baseInstance.createDerivedInstance(displaySettings, config)
    }
    .filterIsInstance(baseInstance::class.java)
}

private fun <T : PreviewElementInstance<*>> colorBlindPreviews(baseInstance: T): List<T> {
  val baseConfig = baseInstance.configuration
  val baseDisplaySettings = baseInstance.displaySettings
  return ColorBlindMode.values()
    .map { colorBlindMode ->
      val colorFilterBaseConfig =
        baseConfig.copy(
          imageTransformation = { image -> ColorConverter(colorBlindMode).convert(image, image) }
        )
      val displaySettings =
        baseDisplaySettings.copy(
          name = "${colorBlindMode.displayName} - ${baseDisplaySettings.name}",
          group = message("ui.check.mode.screen.accessibility.group"),
          showDecoration = false,
        )
      baseInstance.createDerivedInstance(displaySettings, colorFilterBaseConfig)
    }
    .filterIsInstance(baseInstance::class.java)
}
