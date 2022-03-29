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

import com.android.annotations.concurrency.Slow
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.google.wireless.android.sdk.stats.UpgradeAssistantEventInfo
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.android.util.firstNotNullResult

private val LOG = Logger.getInstance("Upgrade Assistant")

class AssistantInvokerImpl : AssistantInvoker {
  @Slow
  override fun showAndGetAgpUpgradeDialog(processor: AgpUpgradeRefactoringProcessor, preserveProcessorConfigurations: Boolean): Boolean {
    val java8Processor = processor.componentRefactoringProcessors.firstNotNullResult { it as? Java8DefaultRefactoringProcessor }
    if (java8Processor == null) {
      LOG.error("no Java8Default processor found in AGP Upgrade Processor")
    }
    // we will need parsed models to decide what to show in the dialog.  Ensure that they are available now, while we are (in theory)
    // not on the EDT.
    processor.ensureParsedModels()
    val hasChangesInBuildFiles = !isCleanEnoughProject(processor.project)
    if (hasChangesInBuildFiles) {
      LOG.warn("changes found in project build files")
    }
    val runProcessor = invokeAndWaitIfNeeded(ModalityState.NON_MODAL) {
      if (processor.classpathRefactoringProcessor.isAlwaysNoOpForProject) {
        processor.trackProcessorUsage(UpgradeAssistantEventInfo.UpgradeAssistantEventKind.FAILURE_PREDICTED)
        LOG.warn("cannot upgrade: classpath processor is always a no-op")
        val dialog = AgpUpgradeRefactoringProcessorCannotUpgradeDialog(processor)
        dialog.show()
        return@invokeAndWaitIfNeeded false
      }
      val dialog = AgpUpgradeRefactoringProcessorWithJava8SpecialCaseDialog(
        processor, java8Processor!!, hasChangesInBuildFiles, preserveProcessorConfigurations
      )
      dialog.showAndGet()
    }
    return runProcessor
  }

  @Slow
  override fun performDeprecatedConfigurationsUpgrade(project: Project, element: PsiElement) {
    val recommended = GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())
    val current = AndroidPluginInfo.find(project)?.pluginVersion ?: recommended
    val processor = AgpUpgradeRefactoringProcessor(project, current, recommended)
    val compileRuntimeProcessor = processor.componentRefactoringProcessors
      .firstNotNullResult { it as? CompileRuntimeConfigurationRefactoringProcessor }
    if (compileRuntimeProcessor == null) {
      LOG.error("no CompileRuntimeConfiguration processor found in AGP Upgrade Processor")
    }
    processor.setCommandName("Replace Deprecated Configurations")
    val wrappedElement = WrappedPsiElement(element, compileRuntimeProcessor!!, null, "Upgrading deprecated configurations")
    processor.targets.add(wrappedElement)
    processor.ensureParsedModels()
    val runProcessor = invokeAndWaitIfNeeded(ModalityState.NON_MODAL) {
      val dialog = AgpUpgradeRefactoringProcessorWithCompileRuntimeSpecialCaseDialog(processor, compileRuntimeProcessor)
      dialog.showAndGet()
    }
    if (runProcessor) {
      DumbService.getInstance(project).smartInvokeLater { processor.run() }
    }
  }

  override fun createProcessor(project: Project, current: GradleVersion, new: GradleVersion) =
    AgpUpgradeRefactoringProcessor(project, current, new)
}