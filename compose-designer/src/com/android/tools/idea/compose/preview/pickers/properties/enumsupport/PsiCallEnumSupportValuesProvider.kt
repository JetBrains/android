/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.pickers.properties.enumsupport

import com.android.SdkConstants
import com.android.sdklib.AndroidVersion
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.DeviceManager
import com.android.tools.idea.compose.preview.AnnotationFilePreviewElementFinder
import com.android.tools.idea.compose.preview.PARAMETER_API_LEVEL
import com.android.tools.idea.compose.preview.PARAMETER_GROUP
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_DEVICE
import com.android.tools.idea.compose.preview.PARAMETER_LOCALE
import com.android.tools.idea.compose.preview.PARAMETER_UI_MODE
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.pickers.properties.enumsupport.devices.DeviceEnumValueBuilder
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.configurations.DeviceGroup
import com.android.tools.idea.configurations.groupDevices
import com.android.tools.idea.model.AndroidModuleInfo
import com.android.tools.idea.rendering.Locale
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.property.panel.api.EnumValue
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.impl.LibraryScopeCache
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.util.text.nullize
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.sdk.AndroidSdkData
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly

/**
 * [EnumSupportValuesProvider] that uses a backing map to return the provider functions.
 */
class PsiCallEnumSupportValuesProvider private constructor(
  private val providersMap: Map<String, () -> List<EnumValue>>
) : EnumSupportValuesProvider {

  override fun getValuesProvider(key: String): (EnumValuesProvider)? = providersMap[key]

  companion object {
    @JvmStatic
    fun createPreviewValuesProvider(
      module: Module,
      containingFile: VirtualFile?
    ): EnumSupportValuesProvider {
      val providersMap = mutableMapOf<String, EnumValuesProvider>()

      providersMap[PARAMETER_UI_MODE] = createUiModeEnumProvider(module)

      providersMap[PARAMETER_API_LEVEL] = createApiLevelEnumProvider(module)

      containingFile?.let {
        providersMap[PARAMETER_GROUP] = createGroupEnumProvider(module, containingFile)
      }

      providersMap[PARAMETER_LOCALE] = createLocaleEnumProvider(module)

      providersMap[PARAMETER_HARDWARE_DEVICE] = createDeviceEnumProvider(module)

      return PsiCallEnumSupportValuesProvider(providersMap)
    }
  }
}

private fun createDeviceEnumProvider(module: Module): EnumValuesProvider =
  {
    val existingDevices = getGroupedDevices(module)
    val devicesEnumValueBuilder = DeviceEnumValueBuilder.withDefaultDevices()

    existingDevices[DeviceGroup.NEXUS_XL]?.forEach(devicesEnumValueBuilder::addPhone)
    existingDevices[DeviceGroup.NEXUS_TABLET]?.forEach(devicesEnumValueBuilder::addTablet)
    existingDevices[DeviceGroup.GENERIC]?.forEach(devicesEnumValueBuilder::addGeneric)

    devicesEnumValueBuilder.build()
  }

private fun DeviceEnumValueBuilder.addPhone(device: Device) {
  val screen = device.defaultState.hardware.screen
  addPhone(
    displayName = device.displayName,
    widthPx = screen.xDimension,
    heightPx = screen.yDimension,
    diagonalIn = screen.diagonalLength
  )
}

private fun DeviceEnumValueBuilder.addTablet(device: Device) {
  val screen = device.defaultState.hardware.screen
  addTablet(
    displayName = device.displayName,
    widthPx = screen.xDimension,
    heightPx = screen.yDimension,
    diagonalIn = screen.diagonalLength
  )
}

private fun DeviceEnumValueBuilder.addGeneric(device: Device) {
  val screen = device.defaultState.hardware.screen
  addGeneric(
    displayName = device.displayName,
    widthPx = screen.xDimension,
    heightPx = screen.yDimension,
    diagonalIn = screen.diagonalLength
  )
}

/**
 * Returns grouped devices from the DeviceManager.
 */
private fun getGroupedDevices(module: Module): Map<DeviceGroup, List<Device>> {
  val studioDevices = AndroidFacet.getInstance(module)?.let { facet ->
    AndroidSdkData.getSdkData(facet)?.deviceManager?.getDevices(DeviceManager.ALL_DEVICES)?.toList()
  } ?: emptyList()
  return groupDevices(studioDevices)
}

private fun createUiModeEnumProvider(module: Module): EnumValuesProvider =
  uiModeProvider@{
    val configurationClass = findClass(module, SdkConstants.CLASS_CONFIGURATION) ?: return@uiModeProvider emptyList()
    val uiModeValueParams = configurationClass.fields.filter {
      it.name.startsWith("UI_MODE_TYPE_") && !it.name.endsWith("MASK")
    }.mapNotNull { uiMode ->
      (uiMode.computeConstantValue() as? Int)?.let {
        val displayName = if (UiMode.VR.resolvedValue == it.toString()) {
          UiMode.VR.display
        }
        else {
          uiMode.name.substringAfter("UI_MODE_TYPE_").replace('_', ' ').toLowerCaseAsciiOnly().capitalizeAsciiOnly()
        }
        ClassEnumValueParams(uiMode.name, displayName, it.toString())
      }
    }.filter { params ->
      UiMode.NORMAL.resolvedValue != params.resolvedValue
    }.sortedBy { params ->
      params.resolvedValue
    }

    val uiModeNoNightValues = uiModeValueParams.map { uiModeParams ->
      UiModeWithNightMaskEnumValue.createNotNightUiModeEnumValue(
        uiModeParams.value,
        uiModeParams.displayName,
        uiModeParams.resolvedValue
      )
    }

    val uiModeNightValues = uiModeValueParams.map { uiModeParams ->
      UiModeWithNightMaskEnumValue.createNightUiModeEnumValue(
        uiModeParams.value,
        uiModeParams.displayName,
        uiModeParams.resolvedValue
      )
    }
    return@uiModeProvider listOf(
      EnumValue.header(message("picker.preview.uimode.header.notnight")),
      UiModeWithNightMaskEnumValue.NormalNotNightEnumValue,
      *uiModeNoNightValues.toTypedArray(),
      EnumValue.header(message("picker.preview.uimode.header.night")),
      UiModeWithNightMaskEnumValue.NormalNightEnumValue,
      *uiModeNightValues.toTypedArray(),
    )
  }

/**
 * Provides a list of targets within the appropriate range (by minimum sdk) and that are valid for rendering.
 */
private fun createApiLevelEnumProvider(module: Module): EnumValuesProvider =
  {
    val configurationManager = ConfigurationManager.findExistingInstance(module)
    val minTargetSdk = AndroidModuleInfo.getInstance(module)?.minSdkVersion?.apiLevel ?: AndroidVersion.VersionCodes.BASE
    configurationManager?.targets?.filter {
      ConfigurationManager.isLayoutLibTarget(it) && it.version.apiLevel >= minTargetSdk
    }?.map { target ->
      EnumValue.item(target.version.apiLevel.toString(), "${target.version.apiLevel} (Android ${target.versionName})")
    } ?: emptyList()
  }

private fun createGroupEnumProvider(module: Module, containingFile: VirtualFile): EnumValuesProvider =
  {
    AnnotationFilePreviewElementFinder.findPreviewMethods(module.project, containingFile).mapNotNull { previewElement ->
      previewElement.displaySettings.group
    }.distinct().map { group ->
      EnumValue.Companion.item(group)
    }
  }

private fun createLocaleEnumProvider(module: Module): EnumValuesProvider =
  {
    ResourceRepositoryManager.getInstance(module)?.localesInProject?.sortedWith(Locale.LANGUAGE_CODE_COMPARATOR)?.mapNotNull { locale ->
      locale.qualifier.full.nullize()?.let { EnumValue.Companion.item(it, locale.toLocaleId()) }
    } ?: emptyList()
  }

private data class ClassEnumValueParams(
  val value: String,
  val displayName: String,
  val resolvedValue: String
)

private fun findClass(module: Module, fqClassName: String): PsiClass? {
  val libraryScope = LibraryScopeCache.getInstance(module.project).librariesOnlyScope
  val psiFacade = JavaPsiFacade.getInstance(module.project)
  return runReadAction { psiFacade.findClass(fqClassName, libraryScope) }
}