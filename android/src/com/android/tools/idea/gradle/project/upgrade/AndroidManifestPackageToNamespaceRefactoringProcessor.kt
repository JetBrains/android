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
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.Companion.standardRegionNecessity
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.ANDROID_MANIFEST_PACKAGE
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usages.impl.rules.UsageType
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.kotlin.idea.core.util.toVirtualFile

class AndroidManifestPackageToNamespaceRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {
  constructor(project: Project, current: GradleVersion, new: GradleVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  override fun necessity() =
    standardRegionNecessity(current, new, GradleVersion.parse("4.2.0-beta03"), GradleVersion.parse("8.0.0-alpha01"))

  override fun findComponentUsages(): Array<UsageInfo> {
    val usages = ArrayList<UsageInfo>()
    val psiManager = PsiManager.getInstance(project)
    projectBuildModel.allIncludedBuildModels.forEach model@{ model ->
      val modelPsiElement = model.psiElement ?: return@model
      val moduleDirectory = model.moduleRootDirectory
      // This is unsound, in that the sourceSets are in fact configurable, rather than fixed, and the location of each sourceSet's
      // manifest is user-controllable.  However, we expect most cases to stick with the default locations, or if not preserve the
      // structure involving subdirectories of "src".
      var namespace: String? = null
      moduleDirectory.toVirtualFile()?.findChild("src")?.children?.forEach child@{ child ->
        if (!child.isDirectory) return@child
        val childName = child.name
        val manifestFile = child.findChild("AndroidManifest.xml") ?: return@child
        val xmlFile = manifestFile.takeIf { it.isValid }?.let { psiManager.findFile(it) } as? XmlFile ?: return@child
        val rootTag = xmlFile.rootTag ?: return@child
        val packageAttribute = rootTag.getAttribute("package") ?: return@child
        // Again, this is somewhat unsound as the main sourceSet need not have the location src/main.  However, well-formed projects
        // should have the same value for package in all sourceSets, so to some extent this concern is theoretical.
        namespace = when  {
          (childName == "main") -> packageAttribute.value
          else -> namespace ?: packageAttribute.value
        }
        val wrappedPsiElement = WrappedPsiElement(packageAttribute, this, REMOVE_MANIFEST_PACKAGE)
        val usageInfo = AndroidManifestPackageUsageInfo(wrappedPsiElement)
        usages.add(usageInfo)
      }
      namespace?.let { namespace ->
        if (model.android().namespace().valueType != GradlePropertyModel.ValueType.NONE) return@let
        val psiElement = model.android().namespace().psiElement ?: model.android().psiElement ?: modelPsiElement
        val wrappedPsiElement = WrappedPsiElement(psiElement, this, ADD_NAMESPACE_BUILDFILE)
        val usageInfo = AndroidNamespaceUsageInfo(wrappedPsiElement, model, namespace)
        usages.add(usageInfo)
      }
    }
    return usages.toTypedArray()
  }

  override fun getCommandName(): String = AndroidBundle.message("project.upgrade.androidManifestPackageToNamespaceRefactoringProcessor.commandName")

  override fun getShortDescription(): String? = """
    Declaration of a project's namespace using the package attribute of the
    Android manifest is deprecated in favour of a namespace declaration in build
    files.
  """.trimIndent()

  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder =
    builder.setKind(ANDROID_MANIFEST_PACKAGE)

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() = AndroidBundle.message("project.upgrade.androidManifestPackageToNamespaceRefactoringProcessor.usageView.header")
    }
  }

  companion object {
    val REMOVE_MANIFEST_PACKAGE = UsageType(AndroidBundle.messagePointer("project.upgrade.androidManifestPackageToNamespaceRefactoringProcessor.removePackage.usageType"))
    val ADD_NAMESPACE_BUILDFILE = UsageType(AndroidBundle.messagePointer("project.upgrade.androidManifestPackageToNamespaceRefactoringProcessor.addNamespace.usageType"))
  }
}

class AndroidManifestPackageUsageInfo(element: WrappedPsiElement) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    element?.let {
      val containingFile = it.containingFile
      it.delete()
      otherAffectedFiles.add(containingFile)
    }
  }

  override fun getTooltipText(): String = AndroidBundle.message("project.upgrade.androidManifestPackageToNamespaceRefactoringProcessor.removePackage.tooltipText")
}

class AndroidNamespaceUsageInfo(
  element: WrappedPsiElement,
  val model: GradleBuildModel,
  val value: String
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    model.android().namespace().setValue(value)
  }

  override fun getTooltipText(): String = AndroidBundle.message("project.upgrade.androidManifestPackageToNamespaceRefactoringProcessor.addNamespace.tooltipText")
}