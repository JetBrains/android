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
package com.android.gmdcodecompletion

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.intellij.psi.PsiElement
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import com.intellij.openapi.project.Project

internal fun String.removeDoubleQuote() = this.replace("\"", "")
internal fun PsiElement.superParent(level: Int = 2): PsiElement? {
  return if (level > 0) {
    this.parent?.superParent(level - 1) ?: null
  }
  else this
}

internal fun PsiElement.superParentAsGrMethodCall(level: Int = 2): GrMethodCallExpression? = this.superParent(
  level) as? GrMethodCallExpression

// FTL device catalog should be updated every 7 days
const val FTL_DEVICE_CATALOG_UPDATE_FREQUENCY: Int = 7

// Managed virtual device catalog should be updated every 7 days
const val MANAGED_VIRTUAL_DEVICE_CATALOG_UPDATE_FREQUENCY: Int = 7

// Stores device related information to better sort code completion suggestion list
data class AndroidDeviceInfo(
  val deviceName: String = "",
  val supportedApis: List<Int> = emptyList(),
  val brand: String = "",
  val formFactor: String = "",
  val deviceForm: String = "",
)

/**
 * Describes number levels in Psi element we need to search for a given suggestion type (device property name or value)
 * in order to get its siblings
 */
enum class CurrentPsiElement(val psiElementLevel: Int) {
  DEVICE_PROPERTY_NAME(1),
  DEVICE_PROPERTY_VALUE(2),
}

data class MinAndTargetApiLevel(val minSdk: Int, val targetSdk: Int)

/**
 * Describes variables in ManagedVirtualDevice and ManagedDevice interface
 * @property propertyName is the variable name in the interface
 * @property needCustomComparable set to true if this variable needs custom ordering in suggestion list. Else set to false
 */
enum class DevicePropertyName(val propertyName: String, val needCustomComparable: Boolean = false) {
  DEVICE_ID("device", true),
  API_LEVEL("apiLevel", true),
  SYS_IMAGE_SOURCE("systemImageSource", true),
  ORIENTATION("orientation"),
  LOCALE("locale"),
  API_PREVIEW("apiPreview", true),
  REQUIRE64BIT("require64Bit");

  companion object {
    // Returns corresponding DevicePropertyField if type is one of propertyName. Else return null
    fun fromOrNull(type: String): DevicePropertyName? = values().find { it.propertyName == type }

    // All the available properties in managed virtual devices
    val MANAGED_VIRTUAL_DEVICE_PROPERTY = persistentListOf(DEVICE_ID, API_LEVEL, SYS_IMAGE_SOURCE, REQUIRE64BIT, API_PREVIEW)

    // All the available properties in FTL devices
    val FTL_DEVICE_PROPERTY = persistentListOf(DEVICE_ID, API_LEVEL, ORIENTATION, LOCALE)
  }
}

fun isFtlPluginEnabled(project: Project): Boolean = ProjectBuildModel.get(project)?.projectBuildModel?.plugins()?.any { pluginModel ->
  pluginModel.psiElement?.text?.contains("com.google.firebase.testlab") == true
} == true