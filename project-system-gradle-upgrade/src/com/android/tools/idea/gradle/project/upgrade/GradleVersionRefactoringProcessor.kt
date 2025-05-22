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
import com.android.tools.idea.gradle.util.BuildFileProcessor
import com.android.tools.idea.gradle.util.CompatibleGradleVersion
import com.android.tools.idea.gradle.util.CompatibleGradleVersion.Companion.getCompatibleGradleVersion
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
import org.jetbrains.android.util.AndroidBundle
import java.io.File

class GradleVersionRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {

  constructor(project: Project, current: AgpVersion, new: AgpVersion): super(project, current, new) {
    this.compatibleGradleVersion = getCompatibleGradleVersion(new)
  }
  constructor(processor: AgpUpgradeRefactoringProcessor) : super(processor) {
    compatibleGradleVersion = getCompatibleGradleVersion(processor.new)
  }

  val compatibleGradleVersion: CompatibleGradleVersion

  override val necessityInfo = AlwaysNeeded

  override fun findComponentUsages(): Array<out UsageInfo> {
    val usages = mutableListOf<UsageInfo>()
    // check the project's wrapper(s) for references to no-longer-supported Gradle versions
    project.basePath?.let {
      val projectRootFolders = listOf(File(FileUtils.toSystemDependentPath(it))) + BuildFileProcessor.getCompositeBuildFolderPaths(project)
      projectRootFolders.filterNotNull().forEach { ioRoot ->
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
        val property = propertiesFile.properties.firstOrNull { SdkConstants.GRADLE_DISTRIBUTION_URL_PROPERTY == it.key } ?: return@forEach
        val currentUrl = property.value?.removeEscapingBackslashes() ?: return@forEach
        val currentGradleVersion = GradleWrapper.getGradleVersion(currentUrl) ?: return@forEach
        val parsedCurrentGradleVersion = runCatching { GradleVersion.version(currentGradleVersion) }.getOrNull() ?: return@forEach
        if (compatibleGradleVersion.version > parsedCurrentGradleVersion) {
          val updatedUrl = GradleWrapper.getUpdatedDistributionUrl(currentUrl, compatibleGradleVersion.version, true)
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
    AndroidBundle.message("project.upgrade.gradleVersionRefactoringProcessor.commandName", compatibleGradleVersion.version.version)

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
        AndroidBundle.message("project.upgrade.gradleVersionRefactoringProcessor.usageView.header", compatibleGradleVersion.version.version)
    }
  }

  @Suppress("FunctionName")
  companion object {
    val GRADLE_URL_USAGE_TYPE =
      UsageType(AndroidBundle.messagePointer("project.upgrade.gradleVersionRefactoringProcessor.gradleUrlUsageType"))
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
  override fun getTooltipText(): String = AndroidBundle.message("project.upgrade.gradleVersionUsageInfo.tooltipText", gradleVersion.version)

  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    ((element as? WrappedPsiElement)?.realElement as? Property)?.let { property ->
      property.setValue(updatedUrl.escapeColons(), PropertyKeyValueFormat.FILE)
      otherAffectedFiles.add(property.containingFile)
    }
  }

  fun String.escapeColons() = this.replace(":", "\\:")
}
