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
import com.android.tools.idea.gradle.project.AndroidStudioGradleInstallationManager
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkVersionUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.FakePsiElement
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usages.impl.rules.UsageType
import com.intellij.util.lang.JavaVersion
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.plugins.gradle.service.GradleInstallationManager

class ProjectJdkRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {

  constructor(project: Project, current: AgpVersion, new: AgpVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  override val necessityInfo = AlwaysNeeded

  data class CurrentJdkInfo(val path: String, val javaVersion: JavaVersion)
  data class NewJdkInfo(val sdk: Sdk, val path: String?, val javaVersion: JavaVersion)

  private val currentJdkInfo: CurrentJdkInfo?
  var newJdkInfo: NewJdkInfo? = null

  init {
    val installationManager = GradleInstallationManager.getInstance()
    currentJdkInfo = project.basePath?.let { basePath -> installationManager.getGradleJvmPath(project, basePath) }
      ?.let { gradleJvmPath ->
        SdkVersionUtil.getJdkVersionInfo(gradleJvmPath)
          ?.let { CurrentJdkInfo(gradleJvmPath, it.version) }
      }

    val jdks = ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance())
    val newCompatibleJdk = CompatibleJdkVersion.getCompatibleJdkVersion(new)
    newJdkInfo = jdks
      .firstOrNull { JavaSdk.getInstance().getVersion(it)?.maxLanguageLevel == newCompatibleJdk.languageLevel }
      ?.let { NewJdkInfo(it, it.homePath, newCompatibleJdk.languageLevel.toJavaVersion()) }
  }

  class RequiredJdkNotSelected(languageLevel: LanguageLevel, new: AgpVersion): BlockReason(
    shortDescription = "Required JDK not selected",
    description = "Android Gradle Plugin version $new requires running with JDK ${languageLevel.toJavaVersion().feature}." +
                  "Select a suitable JDK from the combo box.",
  )

  private val isCurrentJdkNewEnough: Boolean
    get() {
      val newCompatibleJdk = CompatibleJdkVersion.getCompatibleJdkVersion(new)
      return currentJdkInfo?.let { it.javaVersion >= newCompatibleJdk.languageLevel.toJavaVersion() } ?: true
    }

  override fun blockProcessorReasons(): List<BlockReason> {
    val currentCompatibleJdk = CompatibleJdkVersion.getCompatibleJdkVersion(current)
    val newCompatibleJdk = CompatibleJdkVersion.getCompatibleJdkVersion(new)

    // If we don't need to do anything, don't block.
    if (currentCompatibleJdk == newCompatibleJdk) return listOf()

    // If we have found a JDK of the right version already configured for Studio, don't block.
    if (newJdkInfo?.javaVersion?.feature == newCompatibleJdk.languageLevel.toJavaVersion().feature) return listOf()

    // If the project is already using a new enough version of the JDK (or we can't tell), don't block.
    if (isCurrentJdkNewEnough) return listOf()

    return listOf(RequiredJdkNotSelected(newCompatibleJdk.languageLevel, new))
  }

  override fun findComponentUsages(): Array<out UsageInfo> {
    val usages = mutableListOf<UsageInfo>()
    val currentCompatibleJdk = CompatibleJdkVersion.getCompatibleJdkVersion(current)
    val newCompatibleJdk = CompatibleJdkVersion.getCompatibleJdkVersion(new)

    if (currentCompatibleJdk == newCompatibleJdk) return usages.toTypedArray() // nothing to do by definition.

    if (isCurrentJdkNewEnough) return usages.toTypedArray() // The user was already using a newer JDK; leave it alone.

    newJdkInfo?.let { info ->
      val newJdkPath = info.path ?: return usages.toTypedArray()
      val gradleJvmPath = currentJdkInfo?.path ?: return usages.toTypedArray()
      // we need a PsiElement, but what?
      //
      // Maybe the most consistent would be a PsiDirectory, either pointing to the current JDK path, or to the
      // project's IDE configuration?  However, when I tried them, both of them caused a mysterious error in
      // GroovyImportOptimizerRefactoringHelper, where the performOperation routine (which is documented to be called
      // on the EDT) tested a PsiElement for validity, and the platform complained that the thread (which was not the EDT)
      // did not have read permission.
      //
      // Rather than fight with that some more, work around the problem by defining this very null-like FakePsiElement,
      // which does not trigger the behaviour.  (Why?  I don't know.)
      val fakePsiElement = object : FakePsiElement() {
        override fun getParent() = null
        override fun isValid() = true
        override fun isWritable() = true
        override fun getContainingFile() = null
        override fun getProject() = this@ProjectJdkRefactoringProcessor.project
        // TODO(b/257040253): override something so that the preview sees something more meaningful than "Read-only Wrapped Psi Element"
      }
      val wrappedPsiElement = WrappedPsiElement(fakePsiElement, this, UPDATE_PROJECT_JDK)
      usages.add(UpdateJdkUsageInfo(wrappedPsiElement, gradleJvmPath, newJdkPath))
    }
    return usages.toTypedArray()
  }

  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder =
    builder.setKind(UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.PROJECT_JDK)

  override fun getCommandName(): String = AndroidBundle.message("project.upgrade.projectJdkRefactoringProcessor.commandName")

  override fun getShortDescription() = CompatibleJdkVersion.getCompatibleJdkVersion(new).languageLevel.toJavaVersion().feature.let { v ->
    """
      The new version of the Android Gradle Plugin requires a newer version
      of the JDK to run (JDK version $v) than is currently configured for
      this project.
    """.trimIndent()
  }

  override val readMoreUrlRedirect = ReadMoreUrlRedirect("project-jdk-needs-upgrade")

  override fun getRefactoringId() = "com.android.tools.agp.upgrade.projectJdk"

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() = AndroidBundle.message("project.upgrade.projectJdkRefactoringProcessor.usageView.header")
    }
  }

  companion object {
    val UPDATE_PROJECT_JDK = UsageType(AndroidBundle.messagePointer("project.upgrade.projectJdkRefactoringProcessor.enable.usageType"))
  }
}

class UpdateJdkUsageInfo(
  element: WrappedPsiElement,
  private val currentJdkPath: String,
  private val newJdkPath: String
) : GradleBuildModelUsageInfo(element) {
  override fun getTooltipText(): String = AndroidBundle.message("project.upgrade.projectJdkUsageInfo.tooltipText")

  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    fun setJdkAsProjectJdk(path: String) {
      // we are within a write action both during refactoring and during Undo.
      AndroidStudioGradleInstallationManager.setJdkAsProjectJdk(processor.project, path)
    }
    setJdkAsProjectJdk(newJdkPath)
    UndoHook(
      undo = { setJdkAsProjectJdk(currentJdkPath) },
      redo = { setJdkAsProjectJdk(newJdkPath) }
    ).let { processor.undoHooks.add(it) }
  }

  override fun isValid(): Boolean = true // to make sure this shows up in the refactoring preview.
}

enum class CompatibleJdkVersion(val languageLevel: LanguageLevel) {
  JDK8(LanguageLevel.JDK_1_8),
  JDK11(LanguageLevel.JDK_11),
  JDK17(LanguageLevel.JDK_17),

  ;
  companion object {
    fun getCompatibleJdkVersion(agpVersion: AgpVersion): CompatibleJdkVersion = when {
      agpVersion < "7.0.0-alpha01" -> JDK8
      agpVersion < "8.0.0-beta01" -> JDK11
      else -> JDK17
    }
  }
}