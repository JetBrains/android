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
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.usageView.UsageInfo
import com.intellij.usages.impl.rules.UsageType
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeBasicVariant
import com.intellij.openapi.project.guessProjectDir

/**
 * Starting with AGP 9.0, the default value of android.onlyEnableUnitTestForTheTestedBuildType is now
 * true. This refactoring adds the property if it was not defined and sets it to false when
 * upgrading from a version lower than 9.0.0-alpha01 with release type unit tests present.
 */
class BuildTypesUnitTestDefaultRefactoringProcessor : AbstractBooleanPropertyDefaultRefactoringProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion) : super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor) : super(processor)

  override val propertyKey = "android.onlyEnableUnitTestForTheTestedBuildType"
  override val oldDefault = false
  override val upgradeEventKind = UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.ONLY_ENABLE_UNIT_TEST_BY_DEFAULT_FOR_THE_TESTED_BUILD_TYPE
  override val insertPropertyText = AgpUpgradeBundle.message("project.upgrade.buildTypesUnitTest.enable.usageType")!!
  override val tooltip = AgpUpgradeBundle.message("project.upgrade.buildTypesUnitTest.tooltipText")!!
  override val usageViewHeader = AgpUpgradeBundle.message("project.upgrade.buildTypesUnitTest.usageView.header")!!
  override val necessityInfo = PointNecessity(AgpVersion.parse("9.0.0-alpha01"))
  override fun getRefactoringId() = "com.android.tools.agp.upgrade.onlyEnableUnitTestForTheTestedBuildType"
  override fun getCommandName() = AgpUpgradeBundle.message("project.upgrade.buildTypesUnitTest.commandName")!!
  override fun getShortDescription() = AgpUpgradeBundle.message("project.upgrade.buildTypesUnitTest.shortDescription")!!
  override fun findComponentUsages(): Array<out UsageInfo> {
    val usages = ArrayList<UsageInfo>()

    val hasReleaseUnitTest = project.modules.any { module ->
      val androidModel = GradleAndroidModel.get(module) ?: return@any false

      androidModel.variants.any { variant ->
        val basicVariant = androidModel.findBasicVariantByName(variant.name) ?: return@any false
        val buildTypeContainer = androidModel.getBuildType(basicVariant) ?: return@any false
        val isDebuggable = buildTypeContainer.buildType.isDebuggable
        if (isDebuggable) {
          false
        }
        else {
          variant.hostTestArtifacts.any { it.name == IdeArtifactName.UNIT_TEST }
        }
      }
    }

    if (hasReleaseUnitTest) {
      val baseDir = project.guessProjectDir() ?: return usages.toTypedArray()
      val gradlePropertiesVirtualFile = baseDir.findChild("gradle.properties")
      if (gradlePropertiesVirtualFile != null && gradlePropertiesVirtualFile.exists()) {
        val gradlePropertiesPsiFile = PsiManager.getInstance(project).findFile(gradlePropertiesVirtualFile)
        if (gradlePropertiesPsiFile is PropertiesFile) {
          val property = gradlePropertiesPsiFile.findPropertyByKey(propertyKey)
          if (property == null) {
            val wrappedPsiElement = WrappedPsiElement(gradlePropertiesPsiFile, this, INSERT_PROPERTY)
            usages.add(GradlePropertyUsageInfo(wrappedPsiElement, propertyKey, oldDefault, tooltip))
          }
        }
      }
      else if (baseDir.exists()) {
        val baseDirPsiDirectory = PsiManager.getInstance(project).findDirectory(baseDir)
        if (baseDirPsiDirectory is PsiElement) {
          val wrappedPsiElement = WrappedPsiElement(baseDirPsiDirectory, this, INSERT_PROPERTY)
          usages.add(GradlePropertyUsageInfo(wrappedPsiElement, propertyKey, oldDefault, tooltip))
        }
      }
    }
    return usages.toTypedArray()
  }

  companion object {
    val INSERT_PROPERTY = UsageType(AgpUpgradeBundle.messagePointer("project.upgrade.buildTypesUnitTest.enable.usageType"))
  }
}