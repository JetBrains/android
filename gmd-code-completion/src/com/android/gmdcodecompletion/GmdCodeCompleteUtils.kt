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

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression

internal fun String.removeDoubleQuote() = this.replace("\"", "")
internal fun PsiElement.superParent(level: Int = 2): PsiElement? {
  return if (level > 0) {
    this.parent?.superParent(level - 1) ?: null
  }
  else this
}

internal fun GrMethodCallExpression.getQualifiedNameList(): List<String>? = (this.invokedExpression as? GrReferenceExpression)
  ?.qualifiedReferenceName?.split('.')?.reversed()

internal fun PsiElement.superParentAsGrMethodCall(level: Int = 2): GrMethodCallExpression? = this.superParent(
  level) as? GrMethodCallExpression

// FTL device catalog should be updated every 7 days
const val FTL_DEVICE_CATALOG_UPDATE_FREQUENCY: Int = 7

// Managed virtual device catalog should be updated every 7 days
const val MANAGED_VIRTUAL_DEVICE_CATALOG_UPDATE_FREQUENCY: Int = 7

// set android.experimental.testOptions.managedDevices.allowOldApiLevelDevices to true to allow API levels 26 and below
const val MIN_SUPPORTED_GMD_API_LEVEL = 27

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
enum class PsiElementLevel(val psiElementLevel: Int) {
  DEVICE_PROPERTY_NAME(1),
  COMPLETION_PROPERTY_VALUE(2),
}

data class MinAndTargetApiLevel(var minSdk: Int, val targetSdk: Int)

/**
 * Describes variables in ManagedVirtualDevice and ManagedDevice interface
 * @property propertyName is the variable name in the interface
 * @property needCustomComparable set to true if this variable needs custom ordering in suggestion list. Else set to false
 */
enum class ConfigurationParameterName(val propertyName: String, val needCustomComparable: Boolean = false) {
  DEVICE_ID("device", true),
  API_LEVEL("apiLevel", true),
  SYS_IMAGE_SOURCE("systemImageSource", true),
  ORIENTATION("orientation"),
  LOCALE("locale"),
  API_PREVIEW("apiPreview", true),
  REQUIRE64BIT("require64Bit"),
  GRANTED_PERMISSIONS("grantedPermissions"),
  EXTRA_DEVICE_FILES("extraDeviceFiles"),
  NETWORK_PROFILE("networkProfile"),
  TIMEOUT_MINUTES("timeoutMinutes"),
  MAX_TEST_RERUNS("maxTestReruns"),
  FAIL_FAST("failFast"),
  NUM_UNIFORM_SHARDS("numUniformShards"),
  TARGETED_SHARD_DURATION_MINUTES("targetedShardDurationMinutes"),
  CLOUD_STORAGE_BUCKET("cloudStorageBucket"),
  RESULTS_HISTORY_NAME("resultsHistoryName"),
  DIRECTORIES_TO_PULL("directoriesToPull"),
  RECORD_VIDEO("recordVideo"),
  PERFORMANCE_METRICS("performanceMetrics");

  companion object {
    // Returns corresponding DevicePropertyField if type is one of propertyName. Else return null
    fun fromOrNull(type: String): ConfigurationParameterName? = values().find { it.propertyName == type }
  }
}

/**
 * Describes interface of leaf DSL blocks within GMD configuration block
 */
enum class GmdConfigurationInterfaceInfo(val interfaceName: String,
                                         val availableConfigurations: PersistentList<ConfigurationParameterName>,
                                         val leafDslBlock: String = "") {
  FTL_DEVICE("com.google.firebase.testlab.gradle.ManagedDevice", persistentListOf(ConfigurationParameterName.DEVICE_ID,
                                                                                  ConfigurationParameterName.API_LEVEL,
                                                                                  ConfigurationParameterName.ORIENTATION,
                                                                                  ConfigurationParameterName.LOCALE)),

  FTL_FIXTURE("com.google.firebase.testlab.gradle.Fixture", persistentListOf(ConfigurationParameterName.GRANTED_PERMISSIONS,
                                                                             ConfigurationParameterName.EXTRA_DEVICE_FILES,
                                                                             ConfigurationParameterName.NETWORK_PROFILE), "fixture"),

  FTL_EXECUTION("com.google.firebase.testlab.gradle.Execution", persistentListOf(ConfigurationParameterName.TIMEOUT_MINUTES,
                                                                                 ConfigurationParameterName.MAX_TEST_RERUNS,
                                                                                 ConfigurationParameterName.FAIL_FAST,
                                                                                 ConfigurationParameterName.TARGETED_SHARD_DURATION_MINUTES),
                "execution"),

  FTL_RESULTS("com.google.firebase.testlab.gradle.Results", persistentListOf(ConfigurationParameterName.CLOUD_STORAGE_BUCKET,
                                                                             ConfigurationParameterName.RESULTS_HISTORY_NAME,
                                                                             ConfigurationParameterName.DIRECTORIES_TO_PULL,
                                                                             ConfigurationParameterName.RECORD_VIDEO,
                                                                             ConfigurationParameterName.PERFORMANCE_METRICS), "results"),

  MANAGED_VIRTUAL_DEVICE("com.android.build.api.dsl.ManagedVirtualDevice", persistentListOf(ConfigurationParameterName.DEVICE_ID,
                                                                                            ConfigurationParameterName.API_LEVEL,
                                                                                            ConfigurationParameterName.SYS_IMAGE_SOURCE,
                                                                                            ConfigurationParameterName.REQUIRE64BIT,
                                                                                            ConfigurationParameterName.API_PREVIEW));

  fun getDslSequence(leafBlockName: String, isSimplified: Boolean): PersistentList<String> {
    return if (!isSimplified) persistentListOf(leafBlockName, "devices", "managedDevices", "testOptions", "android")
    else {
      when (this) {
        FTL_DEVICE -> persistentListOf(leafBlockName, "managedDevices", "firebaseTestLab")
        MANAGED_VIRTUAL_DEVICE -> persistentListOf(leafBlockName, "localDevices", "managedDevices", "testOptions", "android")
        FTL_FIXTURE, FTL_EXECUTION, FTL_RESULTS -> persistentListOf(this.leafDslBlock, "testOptions", "firebaseTestLab")
      }
    }
  }
}

fun isFtlPluginEnabled(project: Project, selectedModules: Array<Module>): Boolean {
  fun GradleBuildModel.getPluginNames(): List<String> = this.plugins().orEmpty().mapNotNull { it.psiElement?.text }
  if (!ApplicationManager.getApplication().isUnitTestMode && project.getProjectSystem() !is GradleProjectSystem) return false
  val projectBuildModel = ProjectBuildModel.get(project) ?: return false
  val selectedPlugins: HashSet<String> = hashSetOf()
  selectedModules.forEach {
    ApplicationManager.getApplication().runReadAction {
      selectedPlugins.addAll(projectBuildModel.getModuleBuildModel(it)?.getPluginNames().orEmpty())
    }
  }
  return selectedPlugins.any { it.contains("com.google.firebase.testlab") } &&
         getGradlePropertyValue(projectBuildModel, "android.experimental.testOptions.managedDevices.customDevice")
}

fun getGradlePropertyValue(projectBuildModel: ProjectBuildModel, propertyName: String): Boolean {
  return projectBuildModel.projectBuildModel?.propertiesModel?.declaredProperties?.filter { it.name == propertyName }?.let {
    if (it.isNotEmpty()) it[0].valueAsString().toBoolean() else false
  } ?: false
}