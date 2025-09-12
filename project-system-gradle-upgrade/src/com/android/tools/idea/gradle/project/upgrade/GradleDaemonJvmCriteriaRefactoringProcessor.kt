/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.extensions.getPropertyPath
import com.android.tools.idea.gradle.extensions.getRecommendedJavaVersion
import com.android.tools.idea.gradle.project.AgpCompatibleJdkVersion
import com.android.tools.idea.gradle.toolchain.GradleDaemonJvmCriteriaTemplatesManager
import com.android.tools.idea.gradle.util.CompatibleGradleVersion.Companion.getCompatibleGradleVersion
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usages.impl.rules.UsageType
import com.intellij.util.lang.JavaVersion
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.properties.GradleDaemonJvmPropertiesFile
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper
import kotlin.io.path.Path

class GradleDaemonJvmCriteriaRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {

  constructor(project: Project, current: AgpVersion, new: AgpVersion) : super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor) : super(processor)

  private val recommendedToolchainVersion by lazy {
    GradleJvmSupportMatrix.getRecommendedJavaVersion(getRecommendedGradleVersion(), true)
  }

  override val necessityInfo = AlwaysNeeded

  override fun findComponentUsages(): Array<out UsageInfo> {
    if (!GradleDaemonJvmHelper.isDaemonJvmCriteriaSupported(getRecommendedGradleVersion())) return emptyArray()

    val externalProjectPath = project.basePath ?: return emptyArray()
    val propertiesPsiFile = getDaemonJvmCriteriaPropertiesFile(externalProjectPath) ?: return emptyArray()
    val currentToolchainVersion = GradleDaemonJvmPropertiesFile.getProperties(Path(externalProjectPath))?.version?.value?.let {
      JavaVersion.tryParse(it)
    }
    val requiredToolchainVersion = AgpCompatibleJdkVersion.getCompatibleJdkVersion(new).languageLevel.toJavaVersion()
    if (currentToolchainVersion != null && currentToolchainVersion >= requiredToolchainVersion) return emptyArray()
    if (!GradleDaemonJvmCriteriaTemplatesManager.canGeneratePropertiesFile(recommendedToolchainVersion)) return emptyArray()

    val usageType = UsageType(AndroidBundle.messagePointer("project.upgrade.gradleDaemonJvmCriteria.enable.usageType", recommendedToolchainVersion.feature))
    val wrappedPsiElement = WrappedPsiElement(propertiesPsiFile.containingFile, this, usageType)
    return arrayOf(GradleDaemonJvmCriteriaUsageInfo(wrappedPsiElement, recommendedToolchainVersion, externalProjectPath))
  }

  override fun getCommandName(): String =
    AndroidBundle.message("project.upgrade.gradleDaemonJvmCriteria.commandName", recommendedToolchainVersion)

  override fun getShortDescription(): String =
    AndroidBundle.message("project.upgrade.gradleDaemonJvmCriteria.shortDescription", recommendedToolchainVersion)

  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder =
    builder.setKind(UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.DAEMON_JVM_CRITERIA)

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo?>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() =
        AndroidBundle.message("project.upgrade.gradleDaemonJvmCriteria.usageView.header", recommendedToolchainVersion)
    }
  }

  override fun getRefactoringId(): String = "com.android.tools.agp.upgrade.daemonJvmCriteria"

  private fun getDaemonJvmCriteriaPropertiesFile(externalProjectPath: @SystemIndependent String): PropertiesFile? {
    val propertiesFile = GradleDaemonJvmPropertiesFile.getPropertyPath(externalProjectPath).toFile()
    val propertiesVirtualFile = VfsUtil.findFileByIoFile(propertiesFile, false) ?: return null
    if (!propertiesVirtualFile.isValid) return null

    return PsiManager.getInstance(project).findFile(propertiesVirtualFile) as? PropertiesFile
  }

  private fun getRecommendedGradleVersion() = getCompatibleGradleVersion(new).version

  class GradleDaemonJvmCriteriaUsageInfo(
    element: WrappedPsiElement,
    private val javaVersion: JavaVersion,
    private val externalProjectPath: @SystemIndependent String,
  ) : GradleBuildModelUsageInfo(element) {
    override fun getTooltipText(): String = AndroidBundle.message(
      "project.upgrade.gradleDaemonJvmCriteria.enable.tooltipText", javaVersion.feature)

    override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
      GradleDaemonJvmCriteriaTemplatesManager.generatePropertiesFile(javaVersion, externalProjectPath)
    }
  }
}