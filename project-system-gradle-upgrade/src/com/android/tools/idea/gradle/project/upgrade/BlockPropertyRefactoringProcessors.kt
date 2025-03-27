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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor

/**
 * Abstract processor that looks for a Gradle property and blocks upgrades unless this property is not present. If the property is present
 * and its value is the same as a noop, then instead of blocking it will simply remove the property and allow the upgrade.
 *
 * Derived processors are intended to be used for properties whose default values were not changed prior to being removed. If the property
 * default value was changed, use [AbstractBlockPropertyWithPreviousDefaultProcessor].
 */
abstract class AbstractBlockPropertyUnlessNoOpProcessor: AgpUpgradeComponentRefactoringProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  private var _isPropertyAppliedCache: Boolean? = null

  abstract val featureName: String
  abstract val propertyKey: String
  abstract val propertyRemovedVersion: AgpVersion
  abstract val noOpValue: Boolean
  abstract val componentKind: UpgradeAssistantComponentKind

  // Make it abstract again to force subclasses to define their own id
  abstract override fun getRefactoringId(): String

  override val necessityInfo
    get() = PointNecessity(propertyRemovedVersion)

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
    if (this.current < propertyRemovedVersion && this.new >= propertyRemovedVersion && isPropertyApplied()) {
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

  protected inner class PropertyUsedAfterRemoval: BlockReason("Property $propertyKey has been removed in $propertyRemovedVersion.", description = "Remove it from gradle.properties and make sure your project builds correctly before continuing")

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

/**
 * Abstract processor that looks for a Gradle property and blocks upgrades unless this property is not present. If the property is present
 * and its value is the same as a noop, then instead of blocking it will simply remove the property and allow the upgrade.
 *
 * These types of processors are intended to be used when there is already another refactoring that change the default value of the property.
 * If there are no previous changes in default value, use [AbstractBlockPropertyUnlessNoOpProcessor] instead.
 */
abstract class AbstractBlockPropertyWithPreviousDefaultProcessor: AbstractBlockPropertyUnlessNoOpProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  abstract val defaultChangedVersion: AgpVersion

  override fun blockProcessorReasons(): List<BlockReason> {
    if (this.current < defaultChangedVersion && this.new >= propertyRemovedVersion) {
      return listOf(AgpVersionTooOldForPropertyRemoved())
    }
    return super.blockProcessorReasons()
  }

  inner class AgpVersionTooOldForPropertyRemoved: BlockReason("There have been changes in how $featureName is configured.",description = "Please first update AGP to a version greater or equal to $defaultChangedVersion but lower than $propertyRemovedVersion to make the applicable changes")
}

/**
 * Processor that blocks AGP upgrades if android.defaults.buildfeatures.aidl is present in gradle.properties after moving to AGP 9.0.0-alpha01
 */
class BlockAidlProcessor: AbstractBlockPropertyWithPreviousDefaultProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion) : super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor) : super(processor)

  override val featureName = "AIDL"
  override val propertyKey = "android.defaults.buildfeatures.aidl"
  override val propertyRemovedVersion = AgpVersion.parse("9.0.0-alpha01")
  override val componentKind = UpgradeAssistantComponentKind.BLOCK_AIDL_PROPERTY_PRESENT
  override val defaultChangedVersion = AidlDefaultRefactoringProcessor.DEFAULT_CHANGED
  override val noOpValue = false
  override fun getRefactoringId() = "com.android.tools.agp.upgrade.aidlBlockProperty"
}

/**
 * Processor that blocks AGP upgrades if android.experimental.lint.analysisPerComponent is used after AGP 9.0.0-alpha01
 */
class BlockAnalysisPerComponentProcessor: AbstractBlockPropertyUnlessNoOpProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion) : super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor) : super(processor)

  override val featureName = "Lint Analysis Per Component"
  override val propertyKey = "android.experimental.lint.analysisPerComponent"
  override val propertyRemovedVersion = AgpVersion.parse("9.0.0-alpha01")
  override val componentKind = UpgradeAssistantComponentKind.BLOCK_LINT_ANALYSIS_PER_COMPONENT_PRESENT
  override val noOpValue = true
  override fun getRefactoringId() = "com.android.tools.agp.upgrade.lintAnalysisPerComponentBlockProperty"
}

/**
 * Processor that blocks AGP upgrades if android.experimental.androidTest.enableEmulatorControl is used after AGP 9.0.0-alpha01
 */
class BlockEmulatorControlProcessor: AbstractBlockPropertyUnlessNoOpProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion) : super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor) : super(processor)

  override val featureName = "Android Test Emulator Control"
  override val propertyKey = "android.experimental.androidTest.enableEmulatorControl"
  override val propertyRemovedVersion = AgpVersion.parse("9.0.0-alpha01")
  override val componentKind = UpgradeAssistantComponentKind.BLOCK_EMULATOR_CONTROL_PRESENT
  override val noOpValue = true
  override fun getRefactoringId() = "com.android.tools.agp.upgrade.emulatorControlBlockProperty"
}

/**
 * Processor that blocks AGP upgrades if android.disableMinifyLocalDependenciesForLibraries is used after AGP 10.0.0-alpha01
 */
class BlockMinifyLocalDependenciesLibrariesProcessor: AbstractBlockPropertyUnlessNoOpProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion) : super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor) : super(processor)

  override val featureName = "Disable Minify Local Dependencies For Libraries"
  override val propertyKey = "android.disableMinifyLocalDependenciesForLibraries"
  override val propertyRemovedVersion = AgpVersion.parse("10.0.0-alpha01")
  override val componentKind = UpgradeAssistantComponentKind.BLOCK_MINIFY_LOCAL_DEPENDENCIES_LIBRARY_PRESENT
  override val noOpValue = true
  override fun getRefactoringId() = "com.android.tools.agp.upgrade.minifyLocalDependenciesLibrariesBlockProperty"
}

/**
 * Processor that blocks AGP upgrades if android.enableNewResourceShrinker.preciseShrinking is used after AGP 9.1.0-alpha01
 */
class BlockPreciseShrinkingProcessor: AbstractBlockPropertyUnlessNoOpProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion) : super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor) : super(processor)

  override val featureName = "Precise Shrinking"
  override val propertyKey = "android.enableNewResourceShrinker.preciseShrinking"
  override val propertyRemovedVersion = AgpVersion.parse("9.1.0-alpha01")
  override val componentKind = UpgradeAssistantComponentKind.BLOCK_PRECISE_SHRINKING_PRESENT
  override val noOpValue = true
  override fun getRefactoringId() = "com.android.tools.agp.upgrade.preciseShrinkingBlockProperty"
}

/**
 * Processor that blocks AGP upgrades if android.defaults.buildfeatures.renderscript is present in gradle.properties after moving to AGP 9.0
 */
class BlockRenderScriptProcessor: AbstractBlockPropertyWithPreviousDefaultProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion) : super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor) : super(processor)

  override val featureName = "Render Script"
  override val propertyKey = "android.defaults.buildfeatures.renderscript"
  override val propertyRemovedVersion = AgpVersion.parse("9.0.0-alpha01")
  override val componentKind = UpgradeAssistantComponentKind.BLOCK_RENDER_SCRIPT_PROPERTY_PRESENT
  override val defaultChangedVersion = RenderScriptDefaultRefactoringProcessor.DEFAULT_CHANGED
  override val noOpValue = false
  override fun getRefactoringId() = "com.android.tools.agp.upgrade.renderScriptBlockProperty"
}

/**
 * Processor that blocks AGP upgrades if android.enableResourceOptimizations is used after AGP 9.1.0-alpha01
 */
class BlockResourceOptimizationsProcessor: AbstractBlockPropertyUnlessNoOpProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion) : super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor) : super(processor)

  override val featureName = "Resource Optimizations"
  override val propertyKey = "android.enableResourceOptimizations"
  override val propertyRemovedVersion = AgpVersion.parse("9.1.0-alpha01")
  override val componentKind = UpgradeAssistantComponentKind.BLOCK_RESOURCE_OPTIMIZATIONS_PRESENT
  override val noOpValue = true
  override fun getRefactoringId() = "com.android.tools.agp.upgrade.resourceOptimizationsBlockProperty"
}

/**
 * Processor that blocks AGP upgrades if android.experimental.androidTest.useUnifiedTestPlatform is used after AGP 9.0.0-alpha01
 */
class BlockUnifiedTestPlatformProcessor: AbstractBlockPropertyUnlessNoOpProcessor {
  constructor(project: Project, current: AgpVersion, new: AgpVersion) : super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor) : super(processor)

  override val featureName = "Android Test Unified Test Platform"
  override val propertyKey = "android.experimental.androidTest.useUnifiedTestPlatform"
  override val propertyRemovedVersion = AgpVersion.parse("9.0.0-alpha01")
  override val componentKind = UpgradeAssistantComponentKind.BLOCK_UNIFIED_TEST_PLATFORM_PRESENT
  override val noOpValue = true
  override fun getRefactoringId() = "com.android.tools.agp.upgrade.unifiedTestPlatformBlockProperty"
}