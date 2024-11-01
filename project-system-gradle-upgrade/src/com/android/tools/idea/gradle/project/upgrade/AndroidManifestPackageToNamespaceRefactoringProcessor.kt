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

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.projectsystem.getAndroidTestModule
import com.android.tools.idea.projectsystem.gradle.isMainModule
import com.android.tools.idea.util.toVirtualFile
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.ANDROID_MANIFEST_PACKAGE
import com.intellij.openapi.module.ModuleManager
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

class AndroidManifestPackageToNamespaceRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {
  private var blockedReasons: List<BlockReason>? = null
  private var cachingLock = Any()

  constructor(project: Project, current: AgpVersion, new: AgpVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  override val necessityInfo = RegionNecessity(AgpVersion.parse("7.0.0-alpha05"), AgpVersion.parse("8.0.0-alpha03"))

  data class Namespaces(val namespace: String?, val testNamespace: String?)

  private fun File.computeNamespacesFromManifestPackageAttributes(): Namespaces =
    this.computeNamespacesFromManifestPackageAttributesWith { }

  private fun File.computeNamespacesFromManifestPackageAttributesWith(function: (XmlAttribute) -> Unit): Namespaces {
    val psiManager = PsiManager.getInstance(project)
    // This is unsound, in that the sourceSets are in fact configurable, rather than fixed, and the location of each sourceSet's
    // manifest is user-controllable.  However, we expect most cases to stick with the default locations, or if not preserve the
    // structure involving subdirectories of "src".
    var namespace: String? = null
    var testNamespace: String? = null
    this.toVirtualFile()?.findChild("src")?.children?.forEach child@{ child ->
      if (!child.isDirectory) return@child
      val childName = child.name
      val manifestFile = child.findChild("AndroidManifest.xml") ?: return@child
      val xmlFile = manifestFile.takeIf { it.isValid }?.let { psiManager.findFile(it) } as? XmlFile ?: return@child
      val rootTag = xmlFile.rootTag ?: return@child
      val packageAttribute = rootTag.getAttribute("package") ?: return@child
      when {
        (childName == "main") -> namespace = packageAttribute.value
        (childName == "androidTest") -> testNamespace = packageAttribute.value
        // This is somewhat unsound as the main sourceSet need not have the location src/main.  However, well-formed projects
        // should have the same value for package in all production sourceSets, so to some extent this concern is theoretical.
        else -> namespace = namespace ?: packageAttribute.value
      }
      function(packageAttribute)
    }
    return Namespaces(namespace, testNamespace)
  }

  class MainAndTestPackageEqual(moduleNames: List<String>) : BlockReason(
    shortDescription = "Modules have the same package for their main and androidTest artifacts",
    description = "The package specifications in AndroidManifest.xml files define the same \n" +
                  "package for the main and androidTest artifacts, in the following modules: \n" +
                  moduleNames.descriptionText + "\n" +
                  "To proceed, change the androidTest package in the manifest for all affected modules.",
    readMoreUrl = ReadMoreUrlRedirect("main-and-test-package-equal"),
  )

  override fun blockProcessorReasons(): List<BlockReason> {
    synchronized(cachingLock) {
      if (blockedReasons == null) {
        blockedReasons = getBlockedReasons()
      }
      return blockedReasons!!
    }
  }

  private fun getBlockedReasons(): List<BlockReason> {
    val moduleNames = mutableListOf<String>()
    val modules = ModuleManager.getInstance(project).modules.filter { it.isMainModule() }
    modules.forEach module@{ module ->
      val modelNamespace = GradleAndroidModel.get(module)?.androidProject?.namespace
      val androidTestModule = module.getAndroidTestModule()
      val modelTestNamespace = androidTestModule?.let { GradleAndroidModel.get(it)?.androidProject?.testNamespace }
      val buildModel = projectBuildModel.getModuleBuildModel(module) ?: return@module
      buildModel.psiElement ?: return@module
      val moduleDirectory = buildModel.moduleRootDirectory
      val (namespace, testNamespace) = moduleDirectory.computeNamespacesFromManifestPackageAttributes()
      (testNamespace ?: modelTestNamespace)?.let { testNamespace ->
        if (buildModel.android().testNamespace().valueType != GradlePropertyModel.ValueType.NONE) return@let
        val currentNamespace = when {
          buildModel.android().namespace().valueType != GradlePropertyModel.ValueType.NONE -> buildModel.android().namespace().forceString()
          namespace != null -> namespace
          modelNamespace != null -> modelNamespace
          else -> ""
        }
        // TODO(xof): the moduleDirectory need not have the same name as the module (though I guess it's reasonably common?)
        if (testNamespace == currentNamespace) moduleNames.add(moduleDirectory.name)
      }
    }
    return moduleNames.takeIf { it.isNotEmpty() }?.let { listOf(MainAndTestPackageEqual(moduleNames)) } ?: listOf()
  }

  override fun findComponentUsages(): Array<UsageInfo> {
    val usages = ArrayList<UsageInfo>()
    projectBuildModel.allIncludedBuildModels.forEach model@{ model ->
      val modelPsiElement = model.psiElement ?: return@model
      val moduleDirectory = model.moduleRootDirectory
      val (namespace, testNamespace) = moduleDirectory.computeNamespacesFromManifestPackageAttributesWith { packageAttribute ->
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
      testNamespace?.let { testNamespace ->
        if (model.android().testNamespace().valueType != GradlePropertyModel.ValueType.NONE) return@let
        val psiElement = model.android().testNamespace().psiElement ?: model.android().psiElement ?: modelPsiElement
        val wrappedPsiElement = WrappedPsiElement(psiElement, this, ADD_TEST_NAMESPACE_BUILDFILE)
        val usageInfo = AndroidTestNamespaceUsageInfo(wrappedPsiElement, model, testNamespace)
        usages.add(usageInfo)
      }
    }
    return usages.toTypedArray()
  }

  override fun getCommandName(): String = AndroidBundle.message("project.upgrade.androidManifestPackageToNamespaceRefactoringProcessor.commandName")

  override val readMoreUrlRedirect = ReadMoreUrlRedirect("manifest-package-deprecated")

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

  override fun initializeComponentExtraCaches() {
    synchronized(cachingLock) {
      if (blockedReasons == null) {
        blockedReasons = getBlockedReasons()
      }
    }
    super.initializeComponentExtraCaches()
  }

  companion object {
    val REMOVE_MANIFEST_PACKAGE = UsageType(AndroidBundle.messagePointer("project.upgrade.androidManifestPackageToNamespaceRefactoringProcessor.removePackage.usageType"))
    val ADD_NAMESPACE_BUILDFILE = UsageType(AndroidBundle.messagePointer("project.upgrade.androidManifestPackageToNamespaceRefactoringProcessor.addNamespace.usageType"))
    val ADD_TEST_NAMESPACE_BUILDFILE = UsageType(AndroidBundle.messagePointer("project.upgrade.androidManifestPackageToNamespaceRefactoringProcessor.addTestNamespace.usageType"))
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

class AndroidTestNamespaceUsageInfo(
  element: WrappedPsiElement,
  val model: GradleBuildModel,
  val value: String
) : GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    model.android().testNamespace().setValue(value)
  }

  override fun getTooltipText(): String = AndroidBundle.message("project.upgrade.androidManifestPackageToNamespaceRefactoringProcessor.addTestNamespace.tooltipText")
}
