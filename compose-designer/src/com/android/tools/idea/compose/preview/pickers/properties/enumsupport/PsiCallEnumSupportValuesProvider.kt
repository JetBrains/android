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
import com.android.tools.compose.ComposeLibraryNamespace
import com.android.tools.idea.compose.preview.AnnotationFilePreviewElementFinder
import com.android.tools.idea.compose.preview.PARAMETER_API_LEVEL
import com.android.tools.idea.compose.preview.PARAMETER_DEVICE
import com.android.tools.idea.compose.preview.PARAMETER_GROUP
import com.android.tools.idea.compose.preview.PARAMETER_UI_MODE
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.model.AndroidModuleInfo
import com.android.tools.property.panel.api.EnumValue
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.impl.LibraryScopeCache
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
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
      composeLibraryNamespace: ComposeLibraryNamespace,
      containingFile: VirtualFile?
    ): EnumSupportValuesProvider {
      val providersMap = mutableMapOf<String, EnumValuesProvider>()

      providersMap[PARAMETER_DEVICE] = createDeviceEnumProvider(module, composeLibraryNamespace.composeDevicesClassName)

      providersMap[PARAMETER_UI_MODE] = createUiModeEnumProvider(module)

      providersMap[PARAMETER_API_LEVEL] = createApiLevelEnumProvider(module)

      containingFile?.let {
        providersMap[PARAMETER_GROUP] = createGroupEnumProvider(module, containingFile)
      }

      return PsiCallEnumSupportValuesProvider(providersMap)
    }
  }
}

private fun createDeviceEnumProvider(module: Module, devicesClassName: String): EnumValuesProvider =
  deviceProvider@{
    val devicesClass = findClass(module, devicesClassName) ?: return@deviceProvider emptyList()
    val devicesValues = devicesClass.fields.mapNotNull { device ->
      (device.computeConstantValue() as? String)?.takeIf { it.startsWith("id:") || it.startsWith("name:") }?.let { resolvedValue ->
        val fullName = device.name.replace('_', ' ')
        val deviceName = fullName.substringBefore(' ', fullName).toLowerCaseAsciiOnly().capitalizeAsciiOnly()
        val displayName = "$deviceName ${fullName.substringAfter(' ', "")}"
        ClassEnumValueParams(device.name, displayName, resolvedValue)
      }
    }.sortedWith(DevicesComparator).map { uiModeParams ->
      DeviceEnumValueImpl(uiModeParams.className, uiModeParams.displayName, uiModeParams.resolvedValue, devicesClassName)
    }
    // TODO(b/184789272): Add devices from DeviceManager
    return@deviceProvider listOf(DeviceEnumValueImpl("DEFAULT", "Default", "", devicesClassName), *devicesValues.toTypedArray())
  }

/**
 * Custom [Comparator] for a more convenient order in Devices options.
 */
private object DevicesComparator : Comparator<ClassEnumValueParams> {
  override fun compare(o1: ClassEnumValueParams, o2: ClassEnumValueParams): Int {
    val display1 = o1.displayName
    val display2 = o2.displayName
    if (display1.getOrNull(0) == display2.getOrNull(0)) {
      return display1.compareTo(display2)
    }

    if (display1.startsWith('p', true)) {
      return -1
    }
    else if (display2.startsWith('p', true)) {
      return 1
    }
    if (display1.startsWith('n', true)) {
      return -1
    }
    else if (display2.startsWith('n', true)) {
      return 1
    }
    return display1.compareTo(display2)
  }
}

private fun createUiModeEnumProvider(module: Module): EnumValuesProvider =
  uiModeProvider@{
    val configurationClass = findClass(module, SdkConstants.CLASS_CONFIGURATION) ?: return@uiModeProvider emptyList()
    val uiModeValues = configurationClass.fields.filter {
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
    }.map { uiModeParams ->
      UiModeEnumValueImpl(uiModeParams.className, uiModeParams.displayName, uiModeParams.resolvedValue)
    }
    return@uiModeProvider listOf(UiMode.NORMAL, *uiModeValues.toTypedArray())
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

private data class ClassEnumValueParams(
  val className: String,
  val displayName: String,
  val resolvedValue: String
)

private fun findClass(module: Module, fqClassName: String): PsiClass? {
  val libraryScope = LibraryScopeCache.getInstance(module.project).librariesOnlyScope
  val psiFacade = JavaPsiFacade.getInstance(module.project)
  return runReadAction { psiFacade.findClass(fqClassName, libraryScope) }
}