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
import com.android.tools.idea.gradle.project.upgrade.CompatibleGradleVersion.Companion.getCompatibleGradleVersion
import com.android.tools.idea.gradle.util.BuildFileProcessor
import com.android.tools.idea.gradle.util.GradleWrapper
import com.android.utils.FileUtils
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.lang.properties.psi.Property
import com.intellij.lang.properties.psi.PropertyKeyValueFormat
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
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
  VERSION_7_0_2(GradleVersion.parse("7.0.2")),
  VERSION_7_2(GradleVersion.parse("7.2")),
  VERSION_7_3_3(GradleVersion.parse("7.3.3")),
  VERSION_7_4(GradleVersion.parse("7.4")),
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
        GradleVersion.parse("7.0") >= agpVersionMajorMinor -> VERSION_7_0_2
        GradleVersion.parse("7.1") >= agpVersionMajorMinor -> VERSION_7_2
        GradleVersion.parse("7.2") >= agpVersionMajorMinor -> VERSION_7_3_3
        GradleVersion.parse("7.3") >= agpVersionMajorMinor -> VERSION_7_4
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
    ((element as? WrappedPsiElement)?.realElement as? Property)?.let { property ->
      property.setValue(updatedUrl.escapeColons(), PropertyKeyValueFormat.FILE)
      otherAffectedFiles.add(property.containingFile)
    }
  }

  private fun String.escapeColons() = this.replace(":", "\\:")
}
