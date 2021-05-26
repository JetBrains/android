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

import com.android.SdkConstants
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.dsl.api.PluginModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.parser.dependencies.FakeArtifactElement
import com.android.tools.idea.gradle.project.upgrade.CompatibleGradleVersion.*
import com.android.tools.idea.gradle.project.upgrade.CompatibleGradleVersion.Companion.getCompatibleGradleVersion
import com.android.tools.idea.gradle.util.BuildFileProcessor
import com.android.tools.idea.gradle.util.GradleWrapper
import com.android.utils.FileUtils
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.lang.properties.psi.Property
import com.intellij.lang.properties.psi.PropertyKeyValueFormat
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usages.impl.rules.UsageType
import org.jetbrains.android.util.AndroidBundle
import java.io.File

class GradleVersionRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {

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
    // check the project's wrapper(s) for references to no-longer-supported Gradle versions
    project.basePath?.let {
      val projectRootFolders = listOf(File(FileUtils.toSystemDependentPath(it))) + BuildFileProcessor.getCompositeBuildFolderPaths(project)
      projectRootFolders.filterNotNull().forEach { ioRoot ->
        val ioFile = GradleWrapper.getDefaultPropertiesFilePath(ioRoot)
        val gradleWrapper = GradleWrapper.get(ioFile, project)
        val currentGradleVersion = gradleWrapper.gradleVersion ?: return@forEach
        val parsedCurrentGradleVersion = GradleVersion.tryParse(currentGradleVersion) ?: return@forEach
        if (compatibleGradleVersion.version > parsedCurrentGradleVersion) {
          val updatedUrl = gradleWrapper.getUpdatedDistributionUrl(compatibleGradleVersion.version.toString(), true)
          val virtualFile = VfsUtil.findFileByIoFile(ioFile, true) ?: return@forEach
          val propertiesFile = PsiManager.getInstance(project).findFile(virtualFile) as? PropertiesFile ?: return@forEach
          val property = propertiesFile.findPropertyByKey(SdkConstants.GRADLE_DISTRIBUTION_URL_PROPERTY) ?: return@forEach
          val wrappedPsiElement = WrappedPsiElement(property.psiElement, this, GRADLE_URL_USAGE_TYPE)
          usages.add(GradleVersionUsageInfo(wrappedPsiElement, compatibleGradleVersion.version, updatedUrl))
        }
      }
    }

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
    AndroidBundle.message("project.upgrade.gradleVersionRefactoringProcessor.commandName", compatibleGradleVersion.version)

  override fun getShortDescription(): String =
    """
      Version ${compatibleGradleVersion.version} is the minimum version of Gradle compatible
      with Android Gradle Plugin version $new.
    """.trimIndent()

  override fun getRefactoringId(): String = "com.android.tools.agp.upgrade.gradleVersion"

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() =
        AndroidBundle.message("project.upgrade.gradleVersionRefactoringProcessor.usageView.header", compatibleGradleVersion.version)
    }
  }

  @Suppress("FunctionName")
  companion object {
    val GRADLE_URL_USAGE_TYPE =
      UsageType(AndroidBundle.messagePointer("project.upgrade.gradleVersionRefactoringProcessor.gradleUrlUsageType"))
    val WELL_KNOWN_GRADLE_PLUGIN_USAGE_TYPE =
      UsageType(AndroidBundle.messagePointer("project.upgrade.gradleVersionRefactoringProcessor.wellKnownGradlePluginUsageType"))

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

enum class CompatibleGradleVersion(val version: GradleVersion) {
  // versions earlier than 4.4 (corresponding to AGP 3.0.0 and below) are not needed because
  // we no longer support running such early versions of Gradle given our required JDKs, so upgrading to
  // them using this functionality is a non-starter.
  VERSION_4_4(GradleVersion.parse("4.4")),
  VERSION_4_6(GradleVersion.parse("4.6")),
  VERSION_MIN(GradleVersion.parse(SdkConstants.GRADLE_MINIMUM_VERSION)),
  VERSION_4_10_1(GradleVersion.parse("4.10.1")),
  VERSION_5_1_1(GradleVersion.parse("5.1.1")),
  VERSION_5_4_1(GradleVersion.parse("5.4.1")),
  VERSION_5_6_4(GradleVersion.parse("5.6.4")),
  VERSION_6_1_1(GradleVersion.parse("6.1.1")),
  VERSION_6_5(GradleVersion.parse("6.5")),
  VERSION_6_7_1(GradleVersion.parse("6.7.1")),
  VERSION_FOR_DEV(GradleVersion.parse(SdkConstants.GRADLE_LATEST_VERSION)),

  ;

  companion object {
    fun getCompatibleGradleVersion(agpVersion: GradleVersion): CompatibleGradleVersion {
      val agpVersionMajorMinor = GradleVersion(agpVersion.major, agpVersion.minor)
      val compatibleGradleVersion = when {
        GradleVersion.parse("3.1") >= agpVersionMajorMinor -> VERSION_4_4
        GradleVersion.parse("3.2") >= agpVersionMajorMinor -> VERSION_4_6
        GradleVersion.parse("3.3") >= agpVersionMajorMinor -> VERSION_4_10_1
        GradleVersion.parse("3.4") >= agpVersionMajorMinor -> VERSION_5_1_1
        GradleVersion.parse("3.5") >= agpVersionMajorMinor -> VERSION_5_4_1
        GradleVersion.parse("3.6") >= agpVersionMajorMinor -> VERSION_5_6_4
        GradleVersion.parse("4.0") >= agpVersionMajorMinor -> VERSION_6_1_1
        GradleVersion.parse("4.1") >= agpVersionMajorMinor -> VERSION_6_5
        GradleVersion.parse("4.2") >= agpVersionMajorMinor -> VERSION_6_7_1
        else -> VERSION_FOR_DEV
      }
      return when {
        compatibleGradleVersion.version < VERSION_MIN.version -> VERSION_MIN
        else -> compatibleGradleVersion
      }
    }
  }
}

class GradleVersionUsageInfo(
  element: WrappedPsiElement,
  private val gradleVersion: GradleVersion,
  private val updatedUrl: String
) : GradleBuildModelUsageInfo(element) {
  override fun getTooltipText(): String = AndroidBundle.message("project.upgrade.gradleVersionUsageInfo.tooltipText", gradleVersion)

  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    ((element as? WrappedPsiElement)?.realElement as? Property)?.setValue(updatedUrl.escapeColons(), PropertyKeyValueFormat.FILE)
    // TODO(xof): if we brought properties files into the build model, this would not be necessary here, but the buildModel applyChanges()
    //  does all that is necessary to save files, so we do that here to mimic that.  Should we do that in
    //  performPsiSpoilingBuildModelRefactoring instead, to mimic the time applyChanges() would do that more precisely?
    val documentManager = PsiDocumentManager.getInstance(project)
    val document = documentManager.getDocument(element!!.containingFile) ?: return
    if (documentManager.isDocumentBlockedByPsi(document)) {
      documentManager.doPostponedOperationsAndUnblockDocument(document)
    }
    FileDocumentManager.getInstance().saveDocument(document)
    if (!documentManager.isCommitted(document)) {
      documentManager.commitDocument(document)
    }
  }

  fun String.escapeColons() = this.replace(":", "\\:")
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