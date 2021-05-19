/*
 * Copyright (C) 2020 The Android Open Source Project
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
@file:JvmName("DeprecatedConfigurationsUpgrade")
package com.android.tools.idea.gradle.project.upgrade

import com.android.annotations.concurrency.Slow
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance

@Slow
fun performDeprecatedConfigurationsUpgrade(project: Project, element: PsiElement) {
  val recommended = GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get())
  val current = AndroidPluginInfo.find(project)?.pluginVersion ?: recommended
  val processor = AgpUpgradeRefactoringProcessor(project, current, recommended)
  val compileRuntimeProcessor = processor.componentRefactoringProcessors.firstIsInstance<CompileRuntimeConfigurationRefactoringProcessor>()
  processor.setCommandName("Replace Deprecated Configurations")
  val wrappedElement = WrappedPsiElement(element, compileRuntimeProcessor, null, "Upgrading deprecated configurations")
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
