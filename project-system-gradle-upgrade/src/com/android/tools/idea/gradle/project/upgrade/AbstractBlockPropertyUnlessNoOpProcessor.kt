/*
 * Copyright (C) 2024 The Android Open Source Project
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
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.lang.properties.psi.PropertiesList
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.util.SlowOperations.allowSlowOperations

/**
 * Abstract processor that looks for a Gradle property and blocks upgrades unless this property is not present. If the property is present
 * and its value is the same as a noop, then instead of blocking it will simply remove the property and allow the upgrade.
 *
 * Derived refactorings are intended to be used when there is already another refactoring that change the default value of the property.
 */
abstract class AbstractBlockPropertyUnlessNoOpProcessor: AgpUpgradeComponentRefactoringProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  private var _isPropertyAppliedCache: Boolean? = null

  abstract val featureName: String
  abstract val propertyKey: String
  abstract val defaultChangedVersion: AgpVersion
  abstract val propertyRemovedVersion: AgpVersion
  abstract val noOpValue: Boolean
  abstract val componentKind: UpgradeAssistantComponentKind

  // Make it abstract again to force subclasses to define their own id
  abstract override fun getRefactoringId(): String

  override fun findComponentUsages(): Array<out UsageInfo> {
    val usages = mutableListOf<UsageInfo>()
    val baseDir = project.guessProjectDir() ?: return usages.toTypedArray()
    val gradlePropertiesVirtualFile = baseDir.findChild("gradle.properties")
    if (gradlePropertiesVirtualFile != null && gradlePropertiesVirtualFile.exists()) {
      val gradlePropertiesPsiFile = PsiManager.getInstance(project).findFile(gradlePropertiesVirtualFile)
      if (gradlePropertiesPsiFile is PropertiesFile) {
        val property = gradlePropertiesPsiFile.findPropertyByKey(propertyKey)
        if (property != null) {
          val wrappedPsiElement = WrappedPsiElement(property.psiElement, this, null)
          usages.add(RemovedPropertyUsageInfo(wrappedPsiElement))
        }
      }
    }
    return usages.toTypedArray()
  }

  override fun getCommandName() = "Remove $propertyKey if set to $noOpValue"

  override fun getShortDescription() = """
    The property $propertyKey is being removed.
    Upgrading to AGP $propertyRemovedVersion or newer
    will be blocked if this property is present and
    not set to $noOpValue.
  """.trimIndent()

  override fun blockProcessorReasons(): List<BlockReason> {
    val reasons: MutableList<BlockReason> = mutableListOf()
    reasons.addAll(super.blockProcessorReasons())
    if (this.current < defaultChangedVersion && this.new >= propertyRemovedVersion) {
      reasons.add(AgpVersionTooOldForPropertyRemoved())
    }
    else if (this.current < propertyRemovedVersion && this.new >= propertyRemovedVersion && isPropertyApplied()) {
      // Check that the no op value is used
      reasons.add(PropertyUsedAfterRemoval())
    }
    return reasons
  }

  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder {
    return builder.setKind(componentKind)
  }

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() = "$propertyKey is being used"
    }
  }

  override fun initializeComponentExtraCaches() {
    super.initializeComponentExtraCaches()
    _isPropertyAppliedCache = isPropertyAppliedNoCache()
  }

  inner class AgpVersionTooOldForPropertyRemoved: BlockReason("There have been changes in how $featureName is configured.",description = "Please first update AGP to a version greater or equal to $defaultChangedVersion but lower than $propertyRemovedVersion to make the applicable changes")

  inner class PropertyUsedAfterRemoval: BlockReason("Property $propertyKey has been removed in $propertyRemovedVersion.", description = "Remove it from gradle.properties and make sure your project builds correctly before continuing")

  inner class RemovedPropertyUsageInfo(private val wrappedElement: WrappedPsiElement) : GradleBuildModelUsageInfo(wrappedElement) {
    override fun getTooltipText(): String = "This property has been removed in AGP $propertyRemovedVersion"

    override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
      val realElement = wrappedElement.realElement
      var parent = realElement.parent
      if (parent is PropertiesList) {
        parent = parent.parent
      }
      if (parent is PsiFile) {
        otherAffectedFiles.add(parent)
      }
      realElement.delete()
      FileDocumentManager.getInstance().saveAllDocuments()
    }
  }

  // Returns true if the property is present, and it is indeed modifying what the build does
  private fun isPropertyApplied(): Boolean {
    if (_isPropertyAppliedCache == null) {
      _isPropertyAppliedCache = isPropertyAppliedNoCache()
    }
    return _isPropertyAppliedCache!!
  }

  private fun isPropertyAppliedNoCache(): Boolean {
    val baseDir = project.guessProjectDir() ?: return false
    var propertyValue: Boolean? = null
    val gradlePropertiesVirtualFile = baseDir.findChild("gradle.properties")
    if (gradlePropertiesVirtualFile != null && gradlePropertiesVirtualFile.exists()) {
      val gradlePropertiesPsiFile = PsiManager.getInstance(project).findFile(gradlePropertiesVirtualFile)
      if (gradlePropertiesPsiFile is PropertiesFile) {
        val property = gradlePropertiesPsiFile.findPropertyByKey(propertyKey)
        if (property != null) {
          propertyValue = property.value.toBoolean()
        }
      }
    }
    return propertyValue != null && propertyValue != noOpValue
  }
}