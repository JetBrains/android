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
import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.util.CompatibleGradleVersion.Companion.getCompatibleGradleVersion
import com.android.tools.idea.gradle.util.BuildFileProcessor
import com.android.tools.idea.gradle.util.CompatibleGradleVersion
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
import org.gradle.util.GradleVersion
import java.io.File
import org.gradle.wrapper.WrapperExecutor

class GradleVersionRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {

  constructor(project: Project, current: AgpVersion, new: AgpVersion): super(project, current, new) {
    this.compatibleGradleVersion = getCompatibleGradleVersion(new)
  }
  constructor(processor: AgpUpgradeRefactoringProcessor) : super(processor) {
    compatibleGradleVersion = getCompatibleGradleVersion(processor.new)
  }

  val compatibleGradleVersion: CompatibleGradleVersion

  override val necessityInfo = AlwaysNeeded

  private fun List<File?>.forEachGradleVersion(body: (GradleVersion, String, PsiElement, PsiElement?) -> Unit) {
    filterNotNull().forEach { ioRoot ->
      val ioFile = GradleWrapper.getDefaultPropertiesFilePath(ioRoot)
      // this is called from within a read action (via BaseRefactoringProcessor.doRun()); we must not trigger Vfs refreshes here.
      // (which is also why we cannot simply call GradleWrapper.getGradleVersion() as implicitly that might trigger a Vfs
      // refresh).
      val virtualFile = VfsUtil.findFileByIoFile(ioFile, false) ?: return@forEach
      if (!virtualFile.isValid) return@forEach
      val propertiesFile = PsiManager.getInstance(project).findFile(virtualFile) as? PropertiesFile ?: return@forEach
      // TODO(b/262527341): This line looks like it should use propertiesFile.findPropertyByKey().  However, that is implemented by
      //  looking in indexes, which for some reason under some circumstances doesn't actually contain the keys in
      //  gradle-wrapper.properties.  Filtering all the properties of the file ourselves is a workaround for this unexplained
      //  behavior.
      val urlProperty = propertiesFile.properties.firstOrNull { SdkConstants.GRADLE_DISTRIBUTION_URL_PROPERTY == it.key } ?: return@forEach
      val currentUrl = urlProperty.value?.removeEscapingBackslashes() ?: return@forEach
      val currentGradleVersion = GradleWrapper.getGradleVersion(currentUrl) ?: return@forEach
      val parsedCurrentGradleVersion = runCatching { GradleVersion.version(currentGradleVersion) }.getOrNull() ?: return@forEach
      val shaProperty = propertiesFile.properties.firstOrNull { it.name == WrapperExecutor.DISTRIBUTION_SHA_256_SUM }
      body(parsedCurrentGradleVersion, currentUrl, urlProperty.psiElement, shaProperty?.psiElement)
    }
  }

  override fun findComponentUsages(): Array<out UsageInfo> {
    val usages = mutableListOf<UsageInfo>()
    // check the project's wrapper(s) for references to no-longer-supported Gradle versions
    project.basePath?.let {
      val projectRootFolders = listOf(File(FileUtils.toSystemDependentPath(it))) + BuildFileProcessor.getCompositeBuildFolderPaths(project)
      projectRootFolders.forEachGradleVersion { parsedCurrentGradleVersion, currentUrl, urlPsiElement, shaPsiElementOrNull ->
        if (compatibleGradleVersion.version > parsedCurrentGradleVersion) {
          val updatedUrl = GradleWrapper.getUpdatedDistributionUrl(currentUrl, compatibleGradleVersion.version, true)
          val wrappedUrlPsiElement = WrappedPsiElement(urlPsiElement, this, GRADLE_URL_USAGE_TYPE)
          usages.add(GradleVersionUsageInfo(wrappedUrlPsiElement, compatibleGradleVersion.version, updatedUrl))
          if (shaPsiElementOrNull != null) {
            val wrappedShaPsiElement = WrappedPsiElement(shaPsiElementOrNull, this, GRADLE_SHA_256_USAGE_TYPE)
            val isBinaryOnlyDistribution = currentUrl.endsWith("-bin.zip")
            val updatedSha256 = GradleWrapper.getDistributionSha256(compatibleGradleVersion.version, isBinaryOnlyDistribution)
            usages.add(GradleSha256UsageInfo(wrappedShaPsiElement, updatedSha256))
          }
        }
      }
    }
    return usages.toTypedArray()
  }

  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder =
    builder.setKind(UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.GRADLE_VERSION)

  override fun getCommandName(): String =
    AgpUpgradeBundle.message("gradleVersionRefactoringProcessor.commandName", compatibleGradleVersion.version.version)

  override fun getShortDescription(): String =
    """
      Version ${compatibleGradleVersion.version.version} is the minimum version of Gradle compatible
      with Android Gradle Plugin version $new.
    """.trimIndent()

  override fun getRefactoringId(): String = "com.android.tools.agp.upgrade.gradleVersion"

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() =
        AgpUpgradeBundle.message("gradleVersionRefactoringProcessor.usageView.header", compatibleGradleVersion.version.version)
    }
  }

  @Suppress("FunctionName")
  companion object {
    val GRADLE_URL_USAGE_TYPE =
      UsageType(AgpUpgradeBundle.messagePointer("gradleVersionRefactoringProcessor.gradleUrlUsageType"))

    val GRADLE_SHA_256_USAGE_TYPE =
      UsageType(AgpUpgradeBundle.messagePointer("gradleVersionRefactoringProcessor.gradleShaUsageType"))
  }

  // Handle an apparent annoyance in the Psi PropertiesFile implementation which apparently gives us escaping backslashes
  // in values read, but then escapes them (again) when writing.  This implementation is generic but in practice should only
  // be used to remove backslashes in strings of the form "file\://..." and "https\://..."
  private fun String.removeEscapingBackslashes(): String {
    var escape = false
    return this.filter { c ->
      when {
        escape -> true.also { escape = false }
        c == '\\' -> false.also { escape = true }
        else -> true
      }
    }
  }
}

class GradleVersionUsageInfo(
  element: WrappedPsiElement,
  private val gradleVersion: GradleVersion,
  private val updatedUrl: String
) : GradleBuildModelUsageInfo(element) {
  override fun getTooltipText(): String = AgpUpgradeBundle.message("gradleVersionUsageInfo.tooltipText", gradleVersion.version)

  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    ((element as? WrappedPsiElement)?.realElement as? Property)?.let { property ->
      property.setValue(updatedUrl.escapeColons(), PropertyKeyValueFormat.FILE)
      otherAffectedFiles.add(property.containingFile)
    }
  }

  fun String.escapeColons() = this.replace(":", "\\:")
}

class GradleSha256UsageInfo(
  element: WrappedPsiElement,
  private val sha256: String?
) : GradleBuildModelUsageInfo(element) {
  override fun getTooltipText(): String = AgpUpgradeBundle.message("gradleVersionRefactoringProcessor.tooltipText")

  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    ((element as? WrappedPsiElement)?.realElement as? Property)?.let { property ->
      if (sha256 != null) {
        property.setValue(sha256, PropertyKeyValueFormat.FILE)
      } else {
        property.delete()
      }
      otherAffectedFiles.add(property.containingFile)
    }
  }
}
