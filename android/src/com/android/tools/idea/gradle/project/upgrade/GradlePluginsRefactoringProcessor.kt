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

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.dsl.api.PluginModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.parser.dependencies.FakeArtifactElement
import com.android.tools.idea.gradle.project.upgrade.CompatibleGradleVersion.*
import com.android.tools.idea.gradle.project.upgrade.CompatibleGradleVersion.Companion.getCompatibleGradleVersion
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usages.impl.rules.UsageType
import org.jetbrains.android.util.AndroidBundle

class GradlePluginsRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {

  constructor(project: Project, current: GradleVersion, new: GradleVersion): super(project, current, new) {
    this.compatibleGradleVersion = getCompatibleGradleVersion(new)
  }
  constructor(processor: AgpUpgradeRefactoringProcessor) : super(processor) {
    compatibleGradleVersion = getCompatibleGradleVersion(processor.new)
  }

  val compatibleGradleVersion: CompatibleGradleVersion

  override fun necessity() = AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT

  override fun findComponentUsages(): Array<out UsageInfo> {
    val usages = mutableListOf<UsageInfo>()
    // Check plugins for compatibility with our minimum Gradle version even if we're not upgrading (because the project has a higher
    // version, for example) because some compatibility issues are related to the (AGP,Gradle) version pair rather than just directly
    // the Gradle version.  (Also, this makes it substantially easier to test the action of this processor on a file at a time.)
    projectBuildModel.allIncludedBuildModels.forEach model@{ model ->
      model.buildscript().dependencies().artifacts(CommonConfigurationNames.CLASSPATH).forEach dep@{ dep ->
        GradleVersion.tryParse(dep.version().toString())?.let { currentVersion ->
          // GradleVersion.tryParse() looks like it should only parse plausibly-valid version strings.  Unfortunately, things like
          // `Versions.kotlin` are apparently plausibly-valid, returning a GradleVersion object essentially equivalent to `0.0` but with
          // odd text present in the major/minor VersionSegments.
          if (GradleVersion(0, 0) >= currentVersion) return@dep
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
      }
      model.plugins().forEach plugin@{ plugin ->
        if (plugin.version().valueType == GradlePropertyModel.ValueType.STRING) {
          val version = GradleVersion.tryParse(plugin.version().toString()) ?: return@plugin
          if (GradleVersion(0, 0) >= version) return@plugin
          WELL_KNOWN_GRADLE_PLUGIN_TABLE[plugin.name().toString()]?.let { info ->
            val minVersion = info(compatibleGradleVersion)
            if (minVersion <= version) return@plugin
            val resultModel = plugin.version().resultModel
            val element = resultModel.rawElement
            val psiElement = element?.psiElement ?: return@plugin
            val wrappedPsiElement = WrappedPsiElement(psiElement, this, WELL_KNOWN_GRADLE_PLUGIN_USAGE_TYPE)
            usages.add(WellKnownGradlePluginDslUsageInfo(wrappedPsiElement, plugin, resultModel, minVersion.toString()))
          }
        }
      }
    }
    return usages.toTypedArray()
  }

  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder =
    builder.setKind(UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.GRADLE_VERSION)

  override fun getCommandName(): String =
    AndroidBundle.message("project.upgrade.gradlePluginsRefactoringProcessor.commandName", compatibleGradleVersion.version)

  override fun getShortDescription(): String =
    """
      Some Gradle plugins in the project use interfaces which are no longer supported
      in version ${compatibleGradleVersion.version} (or later) of Gradle and version $new (or later)
      of Android Gradle Plugin.
    """.trimIndent()

  override fun getRefactoringId(): String = "com.android.tools.agp.upgrade.gradlePlugins"

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() =
        AndroidBundle.message("project.upgrade.gradlePluginsRefactoringProcessor.usageView.header", compatibleGradleVersion.version)
    }
  }

  @Suppress("FunctionName")
  companion object {
    val WELL_KNOWN_GRADLE_PLUGIN_USAGE_TYPE =
      UsageType(AndroidBundle.messagePointer("project.upgrade.gradlePluginsRefactoringProcessor.wellKnownGradlePluginUsageType"))

    fun `kotlin-gradle-plugin-compatibility-info`(compatibleGradleVersion: CompatibleGradleVersion): GradleVersion =
      when (compatibleGradleVersion) {
        VERSION_4_4 -> GradleVersion.parse("1.1.3")
        VERSION_4_6 -> GradleVersion.parse("1.2.51")
        VERSION_MIN -> GradleVersion.parse("1.2.51")
        VERSION_4_10_1 -> GradleVersion.parse("1.3.0")
        VERSION_5_1_1 -> GradleVersion.parse("1.3.10")
        VERSION_5_4_1 -> GradleVersion.parse("1.3.10")
        VERSION_5_6_4 -> GradleVersion.parse("1.3.10")
        VERSION_6_1_1 -> GradleVersion.parse("1.3.20")
        VERSION_6_5 -> GradleVersion.parse("1.3.20")
        VERSION_6_7_1 -> GradleVersion.parse("1.3.20")
        VERSION_FOR_DEV -> GradleVersion.parse("1.3.40")
      }

    fun `androidx-navigation-safeargs-gradle-plugin-compatibility-info`(compatibleGradleVersion: CompatibleGradleVersion): GradleVersion =
      when (compatibleGradleVersion) {
        VERSION_4_4, VERSION_4_6, VERSION_MIN, VERSION_4_10_1, VERSION_5_1_1, VERSION_5_4_1, VERSION_5_6_4, VERSION_6_1_1,
        VERSION_6_5, VERSION_6_7_1 ->
          GradleVersion.parse("2.0.0")
        // TODO(xof): For Studio 4.3 / AGP 7.0, this might not be correct: a feature deprecated in
        //  AGP 4 might be removed in AGP 7.0 (see b/159542337) at which point we would need to upgrade the version to whatever the
        //  version is that doesn't use that deprecated interface (2.3.2?  2.4.0?  3.0.0?  Who knows?)
        VERSION_FOR_DEV -> GradleVersion.parse("2.0.0")
      }

    // compatibility information from b/174686925 and https://github.com/mannodermaus/android-junit5/releases
    fun `de-mannodermaus-android-junit5-plugin-compatibility-info`(compatibleGradleVersion: CompatibleGradleVersion): GradleVersion =
      when (compatibleGradleVersion) {
        VERSION_4_4, VERSION_4_6, VERSION_MIN, VERSION_4_10_1, VERSION_5_1_1 -> GradleVersion.parse("1.3.1.0")
        VERSION_5_4_1, VERSION_5_6_4, VERSION_6_1_1 -> GradleVersion.parse("1.4.2.1")
        VERSION_6_5, VERSION_6_7_1, VERSION_FOR_DEV -> GradleVersion.parse("1.6.1.0")
      }

    fun `com-google-firebase-crashlytics-plugin-compatibility-info`(compatibleGradleVersion: CompatibleGradleVersion): GradleVersion =
      when (compatibleGradleVersion) {
        VERSION_4_4, VERSION_4_6, VERSION_MIN, VERSION_4_10_1, VERSION_5_1_1, VERSION_5_4_1, VERSION_5_6_4, VERSION_6_1_1,
        VERSION_6_5, VERSION_6_7_1 -> GradleVersion.parse("2.0.0")
        VERSION_FOR_DEV -> GradleVersion.parse("2.5.2")
      }

    fun `com-google-firebase-appdistribution-plugin-compatibility-info`(compatibleGradleVersion: CompatibleGradleVersion): GradleVersion =
      when (compatibleGradleVersion) {
        VERSION_4_4, VERSION_4_6, VERSION_MIN -> GradleVersion.parse("1.0.0")
        VERSION_4_10_1, VERSION_5_1_1, VERSION_5_4_1, VERSION_5_6_4 -> GradleVersion.parse("1.1.0")
        VERSION_6_1_1, VERSION_6_5, VERSION_6_7_1 -> GradleVersion.parse("1.4.0")
        VERSION_FOR_DEV -> GradleVersion.parse("2.1.1")
      }

    fun `com-google-android-gms-oss-licenses-plugin-compatibility-info`(compatibleGradleVersion: CompatibleGradleVersion): GradleVersion =
      when (compatibleGradleVersion) {
        VERSION_4_4, VERSION_4_6, VERSION_MIN, VERSION_4_10_1 -> GradleVersion.parse("0.9.3")
        VERSION_5_1_1, VERSION_5_4_1, VERSION_5_6_4, VERSION_6_1_1, VERSION_6_5, VERSION_6_7_1 -> GradleVersion.parse("0.10.1")
        VERSION_FOR_DEV -> GradleVersion.parse("0.10.4")
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

      "com.google.firebase:firebase-crashlytics-gradle" to ::`com-google-firebase-crashlytics-plugin-compatibility-info`,
      "com.google.firebase.crashlytics" to ::`com-google-firebase-crashlytics-plugin-compatibility-info`,

      "com.google.firebase:firebase-appdistribution-gradle" to ::`com-google-firebase-appdistribution-plugin-compatibility-info`,
      "com.google.firebase.appdistribution" to ::`com-google-firebase-appdistribution-plugin-compatibility-info`,

      "com.google.android.gms:oss-licenses-plugin" to ::`com-google-android-gms-oss-licenses-plugin-compatibility-info`,
      "com.google.android.gms.oss-licenses-plugin" to ::`com-google-android-gms-oss-licenses-plugin-compatibility-info`,
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