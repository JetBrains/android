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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.gradle.Version
import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.dsl.api.PluginModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.parser.dependencies.FakeArtifactElement
import com.android.tools.idea.gradle.util.CompatibleGradleVersion
import com.android.tools.idea.gradle.util.CompatibleGradleVersion.*
import com.android.tools.idea.gradle.util.CompatibleGradleVersion.Companion.getCompatibleGradleVersion
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usages.impl.rules.UsageType
import org.jetbrains.android.util.AndroidBundle

class GradlePluginsRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {

  constructor(project: Project, current: AgpVersion, new: AgpVersion): super(project, current, new) {
    this.compatibleGradleVersion = getCompatibleGradleVersion(new)
  }
  constructor(processor: AgpUpgradeRefactoringProcessor) : super(processor) {
    compatibleGradleVersion = getCompatibleGradleVersion(processor.new)
  }

  private val compatibleGradleVersion: CompatibleGradleVersion

  override val necessityInfo = AlwaysNeeded

  override fun findComponentUsages(): Array<out UsageInfo> {
    val usages = mutableListOf<UsageInfo>()
    fun addUsagesFor(plugin: PluginModel) {
      if (plugin.version().valueType == GradlePropertyModel.ValueType.STRING) {
        val version = Version.parse(plugin.version().toString()).takeIf { it > Version.prefixInfimum("0") } ?: return
        WELL_KNOWN_GRADLE_PLUGIN_TABLE[plugin.name().toString()]?.let { info ->
          val minVersion = info(compatibleGradleVersion)
          if (minVersion <= version) return
          val resultModel = plugin.version().resultModel
          val element = resultModel.rawElement
          val psiElement = element?.psiElement ?: return
          val wrappedPsiElement = WrappedPsiElement(psiElement, this, WELL_KNOWN_GRADLE_PLUGIN_USAGE_TYPE)
          usages.add(WellKnownGradlePluginDslUsageInfo(wrappedPsiElement, plugin, resultModel, minVersion.toString()))
        }
      }
    }

    // Check plugins for compatibility with our minimum Gradle version even if we're not upgrading (because the project has a higher
    // version, for example) because some compatibility issues are related to the (AGP,Gradle) version pair rather than just directly
    // the Gradle version.  (Also, this makes it substantially easier to test the action of this processor on a file at a time.)
    projectBuildModel.allIncludedBuildModels.forEach model@{ model ->
      model.buildscript().dependencies().artifacts(CommonConfigurationNames.CLASSPATH).forEach dep@{ dep ->
        val currentVersion = Version.parse(dep.version().toString()).takeIf { it > Version.prefixInfimum("0") } ?: return@dep
        WELL_KNOWN_GRADLE_PLUGIN_TABLE["${dep.group()}:${dep.name()}"]?.let { info ->
          val minVersion = info(compatibleGradleVersion)
          if (minVersion <= currentVersion) return@dep
          val resultModel = dep.version().resultModel
          val psiElement = when (val element = resultModel.rawElement) {
            null -> return@dep
            // TODO(xof): most likely we need a range in PsiElement, if the dependency is expressed in compactNotation
            is FakeArtifactElement -> element.realExpression.psiElement
            else -> element.psiElement
          }
          psiElement?.let {
            val wrappedPsiElement = WrappedPsiElement(psiElement, this, WELL_KNOWN_GRADLE_PLUGIN_USAGE_TYPE)
            usages.add(WellKnownGradlePluginDependencyUsageInfo(wrappedPsiElement, dep, resultModel, minVersion.toString()))
          }
        }
      }
      model.plugins().forEach(::addUsagesFor)
    }
    projectBuildModel.projectSettingsModel?.pluginManagement()?.plugins()?.plugins()?.forEach(::addUsagesFor)
    return usages.toTypedArray()
  }

  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder =
    builder.setKind(UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.GRADLE_PLUGINS)

  override fun getCommandName(): String =
    AndroidBundle.message("project.upgrade.gradlePluginsRefactoringProcessor.commandName")

  override fun getShortDescription(): String =
    """
      Some Gradle plugins in the project use interfaces which are no longer supported
      in version ${compatibleGradleVersion.version.version} (or later) of Gradle and version $new (or later)
      of Android Gradle Plugin.
    """.trimIndent()

  override fun getRefactoringId(): String = "com.android.tools.agp.upgrade.gradlePlugins"

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() =
        AndroidBundle.message("project.upgrade.gradlePluginsRefactoringProcessor.usageView.header")
    }
  }

  @Suppress("FunctionName")
  companion object {
    val WELL_KNOWN_GRADLE_PLUGIN_USAGE_TYPE =
      UsageType(AndroidBundle.messagePointer("project.upgrade.gradlePluginsRefactoringProcessor.wellKnownGradlePluginUsageType"))

    fun `kotlin-gradle-plugin-compatibility-info`(compatibleGradleVersion: CompatibleGradleVersion): Version =
      when (compatibleGradleVersion) {
        VERSION_4_4 -> Version.parse("1.1.3")
        VERSION_4_6 -> Version.parse("1.2.51")
        VERSION_MIN -> Version.parse("1.2.51")
        VERSION_4_10_1 -> Version.parse("1.3.0")
        VERSION_5_1_1 -> Version.parse("1.3.10")
        VERSION_5_4_1 -> Version.parse("1.3.10")
        VERSION_5_6_4 -> Version.parse("1.3.10")
        VERSION_6_1_1 -> Version.parse("1.3.20")
        VERSION_6_5 -> Version.parse("1.3.20")
        VERSION_6_7_1 -> Version.parse("1.3.20")
        VERSION_7_0_2 -> Version.parse("1.3.40")
        VERSION_7_2 -> Version.parse("1.3.40")
        VERSION_7_3_3 -> Version.parse("1.3.40")
        VERSION_7_4 -> Version.parse("1.6.21")
        VERSION_7_5 -> Version.parse("1.6.21")
        VERSION_FOR_DEV -> Version.parse("1.6.21")
      }

    fun `androidx-navigation-safeargs-gradle-plugin-compatibility-info`(compatibleGradleVersion: CompatibleGradleVersion): Version =
      when (compatibleGradleVersion) {
        VERSION_4_4, VERSION_4_6, VERSION_MIN, VERSION_4_10_1, VERSION_5_1_1, VERSION_5_4_1, VERSION_5_6_4, VERSION_6_1_1,
        VERSION_6_5, VERSION_6_7_1, VERSION_7_0_2 ->
          Version.parse("2.0.0")
        // AGP 7.1 removed an incubating API used by safeargs.
        VERSION_7_2, VERSION_7_3_3, VERSION_7_4, VERSION_7_5, VERSION_FOR_DEV -> Version.parse("2.4.1")
      }

    // compatibility information from b/174686925 and https://github.com/mannodermaus/android-junit5/releases
    fun `de-mannodermaus-android-junit5-plugin-compatibility-info`(compatibleGradleVersion: CompatibleGradleVersion): Version =
      when (compatibleGradleVersion) {
        VERSION_4_4, VERSION_4_6, VERSION_MIN, VERSION_4_10_1, VERSION_5_1_1 -> Version.parse("1.3.1.0")
        VERSION_5_4_1, VERSION_5_6_4, VERSION_6_1_1 -> Version.parse("1.4.2.1")
        VERSION_6_5, VERSION_6_7_1, VERSION_7_0_2, VERSION_7_2, VERSION_7_3_3,
        VERSION_7_4, VERSION_7_5, VERSION_FOR_DEV -> Version.parse("1.6.1.0")
      }

    fun `com-google-firebase-crashlytics-plugin-compatibility-info`(compatibleGradleVersion: CompatibleGradleVersion): Version =
      when (compatibleGradleVersion) {
        VERSION_4_4, VERSION_4_6, VERSION_MIN, VERSION_4_10_1, VERSION_5_1_1, VERSION_5_4_1, VERSION_5_6_4, VERSION_6_1_1,
        VERSION_6_5, VERSION_6_7_1 -> Version.parse("2.0.0")
        VERSION_7_0_2, VERSION_7_2, VERSION_7_3_3, VERSION_7_4, VERSION_7_5, VERSION_FOR_DEV -> Version.parse("2.5.2")
      }

    fun `com-google-firebase-appdistribution-plugin-compatibility-info`(compatibleGradleVersion: CompatibleGradleVersion): Version =
      when (compatibleGradleVersion) {
        VERSION_4_4, VERSION_4_6, VERSION_MIN -> Version.parse("1.0.0")
        VERSION_4_10_1, VERSION_5_1_1, VERSION_5_4_1, VERSION_5_6_4 -> Version.parse("1.1.0")
        VERSION_6_1_1, VERSION_6_5, VERSION_6_7_1 -> Version.parse("1.4.0")
        VERSION_7_0_2, VERSION_7_2, VERSION_7_3_3, VERSION_7_4, VERSION_7_5, VERSION_FOR_DEV -> Version.parse("2.1.1")
      }

    fun `com-google-firebase-perf-plugin-compatibility-info`(compatibleGradleVersion: CompatibleGradleVersion): Version =
      when (compatibleGradleVersion) {
        VERSION_4_4, VERSION_4_6, VERSION_MIN, VERSION_4_10_1, VERSION_5_1_1, VERSION_5_4_1, VERSION_5_6_4, VERSION_6_1_1,
        VERSION_6_5, VERSION_6_7_1, VERSION_7_0_2 -> Version.parse("1.2.1")
        VERSION_7_2, VERSION_7_3_3, VERSION_7_4, VERSION_7_5, VERSION_FOR_DEV -> Version.parse("1.4.1")
      }

    fun `com-google-android-gms-oss-licenses-plugin-compatibility-info`(compatibleGradleVersion: CompatibleGradleVersion): Version =
      when (compatibleGradleVersion) {
        VERSION_4_4, VERSION_4_6, VERSION_MIN, VERSION_4_10_1 -> Version.parse("0.9.3")
        VERSION_5_1_1, VERSION_5_4_1, VERSION_5_6_4, VERSION_6_1_1, VERSION_6_5, VERSION_6_7_1 -> Version.parse("0.10.1")
        VERSION_7_0_2, VERSION_7_2, VERSION_7_3_3, VERSION_7_4, VERSION_7_5, VERSION_FOR_DEV -> Version.parse("0.10.4")
      }

    fun `com-google-gms-google-services-plugin-compatibility-info`(compatibleGradleVersion: CompatibleGradleVersion): Version =
      when (compatibleGradleVersion) {
        VERSION_4_4, VERSION_4_6, VERSION_MIN, VERSION_4_10_1, VERSION_5_1_1, VERSION_5_4_1, VERSION_5_6_4, VERSION_6_1_1,
        VERSION_6_5, VERSION_6_7_1, VERSION_7_0_2 -> Version.parse("4.0.1")
        VERSION_7_2, VERSION_7_3_3, VERSION_7_4, VERSION_7_5, VERSION_FOR_DEV -> Version.parse("4.3.10")
      }

    fun `com-google-dagger-hilt-android-gradle-plugin-compatibility-info`(compatibleGradleVersion: CompatibleGradleVersion): Version =
      when (compatibleGradleVersion) {
        VERSION_4_4, VERSION_4_6, VERSION_MIN, VERSION_4_10_1, VERSION_5_1_1, VERSION_5_4_1, VERSION_5_6_4, VERSION_6_1_1,
        VERSION_6_5 -> Version.parse("2.0")
        VERSION_6_7_1 -> Version.parse("2.32")
        VERSION_7_0_2, VERSION_7_2 -> Version.parse("2.38")
        VERSION_7_3_3, VERSION_7_4, VERSION_7_5, VERSION_FOR_DEV -> Version.parse("2.40.1")
      }

    fun `com-google-protobuf-protobuf-gradle-plugin-compatibility-info`(compatibleGradleVersion: CompatibleGradleVersion): Version =
      when (compatibleGradleVersion) {
        VERSION_4_4, VERSION_4_6, VERSION_MIN, VERSION_4_10_1, VERSION_5_1_1, VERSION_5_4_1 -> Version.parse("0.8.8")
        VERSION_5_6_4 -> Version.parse("0.8.11")
        VERSION_6_1_1, VERSION_6_5, -> Version.parse("0.8.12")
        VERSION_6_7_1 -> Version.parse("0.8.13")
        VERSION_7_0_2, VERSION_7_2, VERSION_7_3_3, VERSION_7_4, VERSION_7_5 -> Version.parse("0.8.16")
        VERSION_FOR_DEV -> Version.parse("0.9.0")
      }

    /**
     * This table contains both the artifact names and the plugin names of the well known plugins, as each of them can be used to
     * declare that a project uses a given plugin or set of plugins (one through a `classpath` configuration, the other through the
     * plugins Dsl).
     */
    val WELL_KNOWN_GRADLE_PLUGIN_TABLE = mapOf(
      "org.jetbrains.kotlin:kotlin-gradle-plugin" to ::`kotlin-gradle-plugin-compatibility-info`,
      "org.jetbrains.kotlin.android" to ::`kotlin-gradle-plugin-compatibility-info`,

      "androidx.navigation:navigation-safe-args-gradle-plugin" to ::`androidx-navigation-safeargs-gradle-plugin-compatibility-info`,
      "androidx.navigation.safeargs" to ::`androidx-navigation-safeargs-gradle-plugin-compatibility-info`,
      "androidx.navigation.safeargs.kotlin" to ::`androidx-navigation-safeargs-gradle-plugin-compatibility-info`,

      "de.mannodermaus.gradle.plugins:android-junit5" to ::`de-mannodermaus-android-junit5-plugin-compatibility-info`,
      "de.mannodermaus.android-junit5" to ::`de-mannodermaus-android-junit5-plugin-compatibility-info`,

      "com.google.gms:google-services" to ::`com-google-gms-google-services-plugin-compatibility-info`,
      "com.google.gms.google-services" to ::`com-google-gms-google-services-plugin-compatibility-info`,

      "com.google.firebase:perf-plugin" to ::`com-google-firebase-perf-plugin-compatibility-info`,
      "com.google.firebase.firebase-perf" to ::`com-google-firebase-perf-plugin-compatibility-info`,

      "com.google.firebase:firebase-crashlytics-gradle" to ::`com-google-firebase-crashlytics-plugin-compatibility-info`,
      "com.google.firebase.crashlytics" to ::`com-google-firebase-crashlytics-plugin-compatibility-info`,

      "com.google.firebase:firebase-appdistribution-gradle" to ::`com-google-firebase-appdistribution-plugin-compatibility-info`,
      "com.google.firebase.appdistribution" to ::`com-google-firebase-appdistribution-plugin-compatibility-info`,

      "com.google.android.gms:oss-licenses-plugin" to ::`com-google-android-gms-oss-licenses-plugin-compatibility-info`,
      "com.google.android.gms.oss-licenses-plugin" to ::`com-google-android-gms-oss-licenses-plugin-compatibility-info`,

      "com.google.dagger:hilt-android-gradle-plugin" to ::`com-google-dagger-hilt-android-gradle-plugin-compatibility-info`,
      "dagger.hilt.android.plugin" to ::`com-google-dagger-hilt-android-gradle-plugin-compatibility-info`,

      "com.google.protobuf:protobuf-gradle-plugin" to ::`com-google-protobuf-protobuf-gradle-plugin-compatibility-info`,
      "com.google.protobuf" to  ::`com-google-protobuf-protobuf-gradle-plugin-compatibility-info`,
    )
  }
}

class WellKnownGradlePluginDependencyUsageInfo(
  element: WrappedPsiElement,
  val dependency: ArtifactDependencyModel,
  val resultModel: GradlePropertyModel,
  val version: String
): GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    resultModel.setValue(version)
  }

  override fun getDiscriminatingValues() = listOf(dependency.group().toString(), dependency.name().toString(), version)

  override fun getTooltipText() = "Update version of ${dependency.group()}:${dependency.name()} to $version"
}

class WellKnownGradlePluginDslUsageInfo(
  element: WrappedPsiElement,
  val plugin: PluginModel,
  val resultModel: GradlePropertyModel,
  val version: String
): GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    resultModel.setValue(version)
  }

  override fun getDiscriminatingValues() = listOf(plugin.name().toString(), version)

  override fun getTooltipText() = "Update version of ${plugin.name()} to $version"
}