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
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.util.toVirtualFile
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlFile
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usages.impl.rules.UsageType
import org.jetbrains.android.util.AndroidBundle
import java.io.File

class AndroidManifestExtractNativeLibsToUseLegacyPackagingRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion) : super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor) : super(processor)

  override val necessityInfo = RegionNecessity(AgpVersion.parse("8.0.0-beta01"), AgpVersion.parse("9.0.0-beta01"))

  private fun File.computeExtractNativeLibsWith(function: (XmlAttribute) -> Unit): Boolean? {
    val psiManager = PsiManager.getInstance(project)
    var extractNativeLibs: Boolean? = null
    this.toVirtualFile()?.findChild("src")?.children?.forEach child@{ child ->
      if (!child.isDirectory) return@child
      if (child.name != "main")  return@child
      val manifestFile = child.findChild("AndroidManifest.xml") ?: return@child
      val xmlFile = manifestFile.takeIf { it.isValid }?.let { psiManager.findFile(it) } as? XmlFile ?: return@child
      val rootTag = xmlFile.rootTag ?: return@child
      val applicationTag = rootTag.findSubTags("application").takeIf { it.size == 1 }?.get(0) ?: return@child
      val extractNativeLibsAttribute = applicationTag.getAttribute("extractNativeLibs", "http://schemas.android.com/apk/res/android")
                                       ?: return@child
      function(extractNativeLibsAttribute)
      if (extractNativeLibs == null && child.name == "main") {
        extractNativeLibsAttribute.value?.toBoolean()?.let { extractNativeLibs = it }
      }
    }
    return extractNativeLibs
  }

  override fun getCommandName(): String = AndroidBundle.message("project.upgrade.androidManifestExtractNativeLibsToUseLegacyPackagingRefactoringProcessor.commandName")

  override val readMoreUrlRedirect = ReadMoreUrlRedirect("extract-native-libs-deprecated")

  override fun getShortDescription(): String = """
    The extractNativeLibs property in AndroidManifest.xml is deprecated in
    favour of the useLegacyPackaging jniLibs option in build files.
  """.trimIndent()

  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder =
    builder.setKind(UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.ANDROID_MANIFEST_EXTRACT_NATIVE_LIBS)

  override fun findComponentUsages(): Array<UsageInfo> {
    val usages = ArrayList<UsageInfo>()
    projectBuildModel.allIncludedBuildModels.forEach model@{ model ->
      val modelPsiElement = model.psiElement ?: return@model
      val moduleDirectory = model.moduleRootDirectory
      val manifestValue = moduleDirectory.computeExtractNativeLibsWith { attribute ->
        val wrappedPsiElement = WrappedPsiElement(attribute, this, REMOVE_MANIFEST_EXTRACT_NATIVE_LIBS)
        val usageInfo = AndroidManifestExtractNativeLibsInfo(wrappedPsiElement)
        usages.add(usageInfo)
      }
      manifestValue?.let {
        if (model.android().packagingOptions().jniLibs().useLegacyPackaging().valueType != GradlePropertyModel.ValueType.NONE)  return@let
        val psiElement = model.android().packagingOptions().jniLibs().psiElement
                         ?: model.android().packagingOptions().psiElement
                         ?: model.android().psiElement ?: modelPsiElement
        val wrappedPsiElement = WrappedPsiElement(psiElement, this, ADD_DSL_USE_LEGACY_PACKAGING)
        val usageInfo = AddUseLegacyPackagingInfo(wrappedPsiElement, model, manifestValue)
        usages.add(usageInfo)
      }
    }
    return usages.toTypedArray()
  }

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() = AndroidBundle.message("project.upgrade.androidManifestExtractNativeLibsToUseLegacyPackagingRefactoringProcessor.usageView.header")
    }
  }

  companion object {
    val REMOVE_MANIFEST_EXTRACT_NATIVE_LIBS = UsageType(AndroidBundle.messagePointer("project.upgrade.androidManifestExtractNativeLibsToUseLegacyPackagingRefactoringProcessor.removeExtractNativeLibs.usageType"))
    val ADD_DSL_USE_LEGACY_PACKAGING = UsageType(AndroidBundle.messagePointer("project.upgrade.androidManifestExtractNativeLibsToUseLegacyPackagingRefactoringProcessor.addUseLegacyPackaging.usageType"))
  }
}

class AndroidManifestExtractNativeLibsInfo(element: WrappedPsiElement): GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    element?.let {
      val containingFile = it.containingFile
      it.delete()
      otherAffectedFiles.add(containingFile)
    }
  }

  override fun getTooltipText(): String = AndroidBundle.message("project.upgrade.androidManifestExtractNativeLibsToUseLegacyPackagingRefactoringProcessor.removeExtractNativeLibs.tooltipText")
}

class AddUseLegacyPackagingInfo(
  element: WrappedPsiElement,
  val model: GradleBuildModel,
  val value: Boolean
): GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    model.android().packagingOptions().jniLibs().useLegacyPackaging().setValue(value)
  }

  override fun getTooltipText(): String = AndroidBundle.message("project.upgrade.androidManifestExtractNativeLibsToUseLegacyPackagingRefactoringProcessor.addUseLegacyPackaging.tooltipText")
}