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
import com.android.tools.idea.Projects
import com.android.tools.idea.gradle.dsl.api.PluginModel
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.INTERPOLATED_TEXT_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder
import com.android.tools.idea.gradle.dsl.parser.dependencies.FakeArtifactElement
import com.android.tools.idea.util.toVirtualFile
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usages.impl.rules.UsageType
import com.intellij.util.ThreeState
import org.jetbrains.android.util.AndroidBundle
import java.io.File
import java.util.Locale

class AgpVersionRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {

  constructor(project: Project, current: GradleVersion, new: GradleVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  override fun necessity() = AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT

  object AgpVersionNotFound: BlockReason(
    shortDescription = "Cannot find AGP version in build files.",
    description = "Cannot locate the version specification for the Android Gradle Plugin dependency, \n" +
                  "possibly because the project's build files use features not currently support by the \n" +
                  "Upgrade Assistant (for example: using constants defined in buildSrc).",
    readMoreUrl = ReadMoreUrlRedirect("agp-version-not-found")
  )

  object Pre80MavenPublish: BlockReason(
    shortDescription = "Use of implicitly-created components in maven-publish.",
    description = "Starting with version 8.0, Android Gradle Plugin will no longer implicitly create \n" +
                  "components for the maven-publish plugin.  You will have to adapt the publishing \n" +
                  "blocks to use the new API (and mark the project as migrated by adding \n" +
                  "<tt>android.disableAutomaticComponentCreation=true</tt> to the project's gradle.properties \n" +
                  "file.",
    readMoreUrl = ReadMoreUrlRedirect("pre-80-maven-publish")
  )

  private var _isPre80MavenPublish: Boolean? = null
  val isPre80MavenPublish: Boolean
    get() {
      if (_isPre80MavenPublish == null) {
        _isPre80MavenPublish = runReadAction { computeIsPre80MavenPublish() }
      }
      return _isPre80MavenPublish!!
    }

  private fun computeIsPre80MavenPublish(): Boolean {
    val mavenPublishUsed = projectBuildModel.allIncludedBuildModels.flatMap { it.plugins() }.any { it.name().toString() == "maven-publish" }
    val disableAutomaticComponentCreation =
      projectBuildModel.projectBuildModel?.propertiesModel?.declaredProperties
        ?.firstOrNull { it.name == "android.disableAutomaticComponentCreation" }
        ?.run { getValue(STRING_TYPE) }
        ?.run { lowercase(Locale.US) == "true" }
      ?: false
    return mavenPublishUsed && !disableAutomaticComponentCreation
  }

  override fun initializeComponentExtraCaches() {
    _isPre80MavenPublish = computeIsPre80MavenPublish()
  }

  override fun blockProcessorReasons(): List<BlockReason> =
    listOfNotNull(
      AgpVersionNotFound.takeIf { isAlwaysNoOpForProject && current != new },
      Pre80MavenPublish.takeIf { isPre80MavenPublish && current < GradleVersion.parse("8.0.0-alpha01") && new >= GradleVersion.parse("8.0.0-alpha01") },
    )

  override fun findComponentUsages(): Array<UsageInfo> {
    val usages = ArrayList<UsageInfo>()
    fun addUsagesFor(plugin: PluginModel) {
      if (plugin.name().toString().startsWith("com.android")) {
        val version = GradleVersion.tryParse(plugin.version().toString()) ?: return
        if (version == current && version < new)  {
          val resultModel = when (plugin.version().valueType) {
            GradlePropertyModel.ValueType.INTERPOLATED -> {
              val interpolatedText = plugin.version().getValue(INTERPOLATED_TEXT_TYPE) ?: return
              if (interpolatedText.interpolationElements.size != 1) return
              val element = interpolatedText.interpolationElements.get(0)
              val reference = element.referenceItem ?: return
              GradlePropertyModelBuilder.create(reference.referredElement).buildResolved()
            }
            else -> plugin.version().resultModel
          }
          val psiElement = when (val element = resultModel.rawElement) {
            null -> return
            else -> element.psiElement
          }
          val presentableText = AndroidBundle.message("project.upgrade.agpVersionRefactoringProcessor.target.presentableText")
          psiElement?.let {
            usages.add(AgpVersionUsageInfo(WrappedPsiElement(it, this, USAGE_TYPE, presentableText), current, new, resultModel))
          }
        }
      }
    }

    val buildSrcDir = File(Projects.getBaseDirPath(project), "buildSrc").toVirtualFile()
    projectBuildModel.allIncludedBuildModels.forEach model@{ model ->
      // Using the buildModel, look for classpath dependencies on AGP, and if we find one, record it as a usage.
      model.buildscript().dependencies().artifacts(CommonConfigurationNames.CLASSPATH).forEach dep@{ dep ->
        when (isUpdatablePluginDependency(new, dep)) {
          ThreeState.YES -> {
            val resultModel = dep.version().resultModel
            val psiElement = when (val element = resultModel.rawElement) {
              null -> return@dep
              // TODO(xof): most likely we need a range in PsiElement, if the dependency is expressed in compactNotation
              is FakeArtifactElement -> element.realExpression.psiElement
              else -> element.psiElement
            }
            // This text gets used in the `target` display of the preview, and so needs to conform with our user interface
            // (having this be more of a verb than a noun).
            val presentableText = AndroidBundle.message("project.upgrade.agpVersionRefactoringProcessor.target.presentableText")
            psiElement?.let {
              usages.add(AgpVersionUsageInfo(WrappedPsiElement(it, this, USAGE_TYPE, presentableText), current, new, resultModel))
            }
          }
          else -> Unit
        }
      }
      // buildSrc run-time dependencies are project build-time (classpath) dependencies.
      if (model.moduleRootDirectory.toVirtualFile() == buildSrcDir) {
        model.dependencies().artifacts().forEach dep@{ dep ->
          when (isUpdatablePluginRelatedDependency(new, dep)) {
            ThreeState.YES -> {
              val resultModel = dep.version().resultModel
              val psiElement = when (val element = resultModel.rawElement) {
                null -> return@dep
                // TODO(xof): most likely we need a range in PsiElement, if the dependency is expressed in compactNotation
                is FakeArtifactElement -> element.realExpression.psiElement
                else -> element.psiElement
              }
              // it would be weird for there to be an AGP dependency in buildSrc without there being one in the main project, but just in
              // case...
              val presentableText = AndroidBundle.message("project.upgrade.agpVersionRefactoringProcessor.target.presentableText")
              psiElement?.let {
                usages.add(AgpVersionUsageInfo(WrappedPsiElement(it, this, USAGE_TYPE, presentableText), current, new, resultModel))
              }
            }
            else -> Unit
          }
        }
      }
      // Examine plugins for plugin Dsl declarations.
      model.plugins().forEach(::addUsagesFor)
    }
    projectBuildModel.projectSettingsModel?.let {
      it.pluginManagement()?.plugins()?.plugins()?.forEach(::addUsagesFor)
      it.plugins()?.plugins()?.forEach(::addUsagesFor)
    }
    return usages.toTypedArray()
  }

  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder =
    builder.setKind(UpgradeAssistantComponentKind.AGP_CLASSPATH_DEPENDENCY)

  override fun getCommandName(): String = AndroidBundle.message("project.upgrade.agpVersionRefactoringProcessor.commandName", current, new)

  override fun getShortDescription(): String =
    """
      Changing the version of the Android Gradle Plugin dependency
      effectively upgrades the project.  Pre-upgrade steps must be run
      no later than this version change; post-upgrade steps must be run
      no earlier, but can be run afterwards by continuing to use this
      assistant after running the upgrade.
    """.trimIndent()

  override fun getRefactoringId(): String = "com.android.tools.agp.upgrade.agpVersion"

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() = AndroidBundle.message("project.upgrade.agpVersionRefactoringProcessor.usageView.header", current, new)
    }
  }

  companion object {
    val USAGE_TYPE = UsageType(AndroidBundle.messagePointer("project.upgrade.agpVersionRefactoringProcessor.usageType"))
  }
}

class AgpVersionUsageInfo(
  element: WrappedPsiElement,
  val current: GradleVersion,
  val new: GradleVersion,
  private val resultModel: GradlePropertyModel
) : GradleBuildModelUsageInfo(element) {
  override fun getTooltipText(): String = AndroidBundle.message("project.upgrade.agpVersionUsageInfo.tooltipText", current, new)

  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    resultModel.setValue(new.toString())
  }
}
