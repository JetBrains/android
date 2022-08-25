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
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usages.impl.rules.UsageType
import org.jetbrains.android.util.AndroidBundle

class MigratePackagingOptionsToJniLibsAndResourcesRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  override val necessityInfo = RegionNecessity(AgpVersion.parse("4.2.0-alpha08"), AgpVersion.parse("9.0.0-alpha01"))

  override fun findComponentUsages(): Array<UsageInfo> {
    val usages = ArrayList<UsageInfo>()
    fun splitModel(source: GradlePropertyModel, jniLibs: GradlePropertyModel, resources: GradlePropertyModel) {
      if (source.valueType == GradlePropertyModel.ValueType.LIST) {
        val listOfModels = source.getValue(GradlePropertyModel.LIST_TYPE) ?: return
        listOfModels.forEach forEach@{ m ->
          val psiElement = m.psiElement ?: return@forEach
          val wrappedPsiElement = WrappedPsiElement(psiElement, this, SPLIT_PROPERTY)
          val value: Any = when (m.valueType) {
            GradlePropertyModel.ValueType.STRING -> m.getValue(GradlePropertyModel.STRING_TYPE)!!
            GradlePropertyModel.ValueType.REFERENCE -> m.getValue(GradlePropertyModel.OBJECT_TYPE)!!
            else -> return@forEach
          }
          val resolvedValue: Any = when (m.resolve().valueType) {
            GradlePropertyModel.ValueType.STRING -> m.resolve().getValue(GradlePropertyModel.STRING_TYPE)!!
            else -> value
          }
          val usageInfo = when (GlobDestination.of(resolvedValue)) {
            GlobDestination.JNI_LIBS -> SplitPropertiesUsageInfo(wrappedPsiElement, value, listOf(jniLibs))
            GlobDestination.RESOURCES -> SplitPropertiesUsageInfo(wrappedPsiElement, value, listOf(resources))
            GlobDestination.BOTH -> SplitPropertiesUsageInfo(wrappedPsiElement, value, listOf(jniLibs, resources))
          }
          usages.add(usageInfo)
        }
      }
    }

    projectBuildModel.allIncludedBuildModels.forEach model@{ model ->
      usages.addAll(MOVE_PACKAGING_OPTIONS_PROPERTIES_INFO.findBuildModelUsages(this, model))
      model.android().packagingOptions().run {
        splitModel(excludes(), jniLibs().excludes(), resources().excludes())
        splitModel(pickFirsts(), jniLibs().pickFirsts(), resources().pickFirsts())
      }
      usages.addAll(REMOVE_PACKAGING_OPTIONS_PROPERTIES_INFO.findBuildModelUsages(this, model))
    }
    return usages.toArray(UsageInfo.EMPTY_ARRAY)
  }

  override fun getCommandName(): String = AndroidBundle.message("project.upgrade.migratePackagingOptionsRefactoringProcessor.commandName")

  override fun getShortDescription(): String? =
    """
      Directives to affect packaging have been split into those affecting
      libraries (.so files) and those affecting all other resources.
    """.trimIndent()

  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder
    // TODO(xof)
    = builder.setKind(UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.MIGRATE_PACKAGING_OPTIONS)

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() = AndroidBundle.message("project.upgrade.migratePackagingOptionsRefactoringProcessor.usageView.header")
    }
  }

  companion object {
    val MOVE_PROPERTY = UsageType(AndroidBundle.messagePointer("project.upgrade.migratePackagingOptionsRefactoringProcessor.move.usageType"))
    val SPLIT_PROPERTY = UsageType(AndroidBundle.messagePointer("project.upgrade.migratePackagingOptionsRefactoringProcessor.split.usageType"))
    val REMOVE_PROPERTY = UsageType(AndroidBundle.messagePointer("project.upgrade.migratePackagingOptionsRefactoringProcessor.remove.usageType"))

    val MOVE_PACKAGING_OPTIONS_PROPERTIES_INFO = MovePropertiesInfo(
      listOf(
        Pair({ android().packagingOptions().doNotStrip() }, { android().packagingOptions().jniLibs().keepDebugSymbols() }),
        Pair({ android().packagingOptions().merges() }, { android().packagingOptions().resources().merges() }),
      ),
      tooltipTextSupplier = AndroidBundle.messagePointer("project.upgrade.migratePackagingOptionsRefactoringProcessor.move.tooltipText"),
      usageType = MOVE_PROPERTY
    )
    val REMOVE_PACKAGING_OPTIONS_PROPERTIES_INFO = RemovePropertiesInfo(
      { listOf(android().packagingOptions().excludes(), android().packagingOptions().pickFirsts()) },
      tooltipTextSupplier = AndroidBundle.messagePointer("project.upgrade.migratePackagingOptionsRefactoringProcessor.remove.tooltipText"),
      usageType = REMOVE_PROPERTY
    )
  }

  enum class GlobDestination {
    JNI_LIBS,
    RESOURCES,
    BOTH
    ;

    companion object {
      fun of(value: Any): GlobDestination = when {
        value is String && value.endsWith(".so") -> JNI_LIBS
        value is String && !value.contains("[?}*\\]].{0,2}$".toRegex()) -> RESOURCES
        else -> BOTH
      }
    }
  }
}

class SplitPropertiesUsageInfo(
  element: WrappedPsiElement, val value: Any, val destinations: List<GradlePropertyModel>
): GradleBuildModelUsageInfo(element) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    destinations.forEach {
      it.addListValue()?.setValue(value)
    }
  }

  override fun getTooltipText(): String = ""
}