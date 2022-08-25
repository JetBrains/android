/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.tools.idea.gradle.project.upgrade.R8FullModeDefaultRefactoringProcessor.NoPropertyPresentAction
import com.android.tools.idea.gradle.project.upgrade.R8FullModeDefaultRefactoringProcessor.NoPropertyPresentAction.ACCEPT_NEW_DEFAULT
import com.android.tools.idea.gradle.project.upgrade.R8FullModeDefaultRefactoringProcessor.NoPropertyPresentAction.INSERT_OLD_DEFAULT
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.R8FullModeDefaultProcessorSettings
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usages.impl.rules.UsageType
import org.jetbrains.android.util.AndroidBundle
import java.util.function.Supplier

private val LOG = Logger.getInstance(LOG_CATEGORY)

class R8FullModeDefaultRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {

  var noPropertyPresentAction = NoPropertyPresentAction.INSERT_OLD_DEFAULT
    set(value) {
      LOG.info("setting noPropertyPresentAction to ${value.name}")
      field = value
    }

  constructor(project: Project, current: AgpVersion, new: AgpVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  override val necessityInfo = PointNecessity(ACTIVATED_VERSION)

  override fun findComponentUsages(): Array<out UsageInfo> {
    val usages = mutableListOf<UsageInfo>()
    val baseDir = project.baseDir ?: return usages.toTypedArray()
    val gradlePropertiesVirtualFile = baseDir.findChild("gradle.properties")
    if (gradlePropertiesVirtualFile != null && gradlePropertiesVirtualFile.exists()) {
      val gradlePropertiesPsiFile = PsiManager.getInstance(project).findFile(gradlePropertiesVirtualFile)
      if (gradlePropertiesPsiFile is PropertiesFile) {
        val property = gradlePropertiesPsiFile.findPropertyByKey("android.enableR8.fullMode")
        val (psiElement, usageType) = when(property) {
          null -> gradlePropertiesPsiFile to when (noPropertyPresentAction) {
            ACCEPT_NEW_DEFAULT -> ACCEPT_NEW_USAGE_TYPE
            INSERT_OLD_DEFAULT -> INSERT_OLD_USAGE_TYPE
          }
          else -> property.psiElement to EXISTING_PROPERTY_USAGE_TYPE
        }
        val wrappedPsiElement = WrappedPsiElement(psiElement, this, usageType)
        usages.add(R8FullModeUsageInfo(wrappedPsiElement, usageType == EXISTING_PROPERTY_USAGE_TYPE, noPropertyPresentAction))
      }
    }
    else if (baseDir.exists()) {
      val baseDirPsiDirectory = PsiManager.getInstance(project).findDirectory(baseDir)
      if (baseDirPsiDirectory is PsiElement) {
        val usageType = when(noPropertyPresentAction) {
          ACCEPT_NEW_DEFAULT -> ACCEPT_NEW_USAGE_TYPE
          INSERT_OLD_DEFAULT -> INSERT_OLD_USAGE_TYPE
        }
        val wrappedPsiElement = WrappedPsiElement(baseDirPsiDirectory, this, usageType)
        usages.add(R8FullModeUsageInfo(wrappedPsiElement, false, noPropertyPresentAction))
      }
    }
    return usages.toTypedArray()
  }

  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder {
    val protoNoPropertyPresentAction = when (noPropertyPresentAction) {
      INSERT_OLD_DEFAULT -> R8FullModeDefaultProcessorSettings.NoPropertyPresentAction.INSERT_OLD_DEFAULT
      ACCEPT_NEW_DEFAULT -> R8FullModeDefaultProcessorSettings.NoPropertyPresentAction.ACCEPT_NEW_DEFAULT
    }
    val r8FullModeSettings = R8FullModeDefaultProcessorSettings.newBuilder().setNoPropertyPresentAction(protoNoPropertyPresentAction)
      .build()
    return builder.setKind(UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.R8_FULL_MODE_DEFAULT)
      .setR8FullModeDefaultSettings(r8FullModeSettings)
  }

  override fun getCommandName() = AndroidBundle.message("project.upgrade.r8FullModeDefaultRefactoringProcessor.commandName")

  override val groupingName
    get() = AndroidBundle.message("project.upgrade.r8FullModeDefaultRefactoringProcessor.groupingName")

  override fun getRefactoringId() = "com.android.tools.agp.upgrade.R8FullModeDefault"

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() = AndroidBundle.message("project.upgrade.r8FullModeDefaultRefactoringProcessor.usageView.header")
    }
  }

  override fun computeIsAlwaysNoOpForProject(): Boolean =
    findComponentUsages().all {
      it is R8FullModeUsageInfo && it.existing
    }

  override val readMoreUrlRedirect = ReadMoreUrlRedirect("r8-full-mode-default")

  override fun getShortDescription(): String? =
    """
      The default for the R8 mode in AGP is now full mode, rather than the
      previous compatibility mode.  If your project requires compatibility
      mode, it needs to be set explicitly in the gradle.properties file.
    """.trimIndent()

  enum class NoPropertyPresentAction(val supplier: Supplier<String>) {
    ACCEPT_NEW_DEFAULT(AndroidBundle.messagePointer("project.upgrade.noPropertyPresentAction.acceptNewDefault")),
    INSERT_OLD_DEFAULT(AndroidBundle.messagePointer("project.upgrade.noPropertyPresentAction.insertOldDefault")),
    ;

    override fun toString() = supplier.get()
  }

  companion object {
    val ACTIVATED_VERSION = AgpVersion.parse("8.0.0-alpha01")

    val EXISTING_PROPERTY_USAGE_TYPE = UsageType(AndroidBundle.messagePointer("project.upgrade.r8FullModeDefaultRefactoringProcessor.existingDirectiveUsageType"))
    val ACCEPT_NEW_USAGE_TYPE = UsageType(AndroidBundle.messagePointer("project.upgrade.r8FullModeDefaultRefactoringProcessor.acceptNewUsageType"))
    val INSERT_OLD_USAGE_TYPE = UsageType(AndroidBundle.messagePointer("project.upgrade.r8FullModeDefaultRefactoringProcessor.insertOldUsageType"))
  }
}

class R8FullModeUsageInfo(
  private val wrappedElement: WrappedPsiElement,
  val existing: Boolean,
  private val noPropertyPresentAction: NoPropertyPresentAction
) : GradleBuildModelUsageInfo(wrappedElement) {

  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    if (!existing && noPropertyPresentAction == INSERT_OLD_DEFAULT) {
      val (propertiesFile, psiFile) = when (val realElement = wrappedElement.realElement) {
        is PropertiesFile -> realElement to (realElement as? PsiFile ?: return)
        is PsiDirectory -> realElement.createFile("gradle.properties").let {
          (it as? PropertiesFile ?: return) to (it as? PsiFile ?: return)
        }
        else -> return
      }
      otherAffectedFiles.add(psiFile)
      propertiesFile.addProperty("android.enableR8.fullMode", "false")
    }
  }

  override fun getTooltipText() = when (existing) {
    false -> when (noPropertyPresentAction) {
      ACCEPT_NEW_DEFAULT -> AndroidBundle.message("project.upgrade.r8FullModeDefaultUsageInfo.tooltipText.acceptNewDefault")
      INSERT_OLD_DEFAULT -> AndroidBundle.message("project.upgrade.r8FullModeDefaultUsageInfo.tooltipText.insertOldDefault")
    }
    true -> AndroidBundle.message("project.upgrade.r8FullModeDefaultUsageInfo.tooltipText.existing")
  }
}
