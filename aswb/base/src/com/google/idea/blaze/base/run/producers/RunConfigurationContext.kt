/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.run.producers

import com.google.idea.blaze.base.command.BlazeCommandName
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiQualifiedNamedElement

/** A context used to configure a blaze run configuration, possibly asynchronously. */
interface RunConfigurationContext {

  /** The [PsiElement] most relevant to this context (e.g. a method, class, file, etc.). */
  val sourceElement: PsiElement

  /** Returns true if the run configuration was successfully configured. */
  fun setupRunConfiguration(config: BlazeCommandRunConfiguration): Boolean

  /** Returns true if the run configuration matches this [RunConfigurationContext]. */
  fun matchesRunConfiguration(config: BlazeCommandRunConfiguration): Boolean

  /** Stage 2 (EDT): Interactive user prompts (e.g. choosing subclass for abstract test classes). */
  suspend fun refine(context: ConfigurationContext): RunConfigurationContext = this

  /** Stage 2 (Background): Resolves the context (e.g. fetches target info in background). */
  suspend fun resolve(project: Project): RunConfigurationContext = this

  companion object {
    @JvmStatic
    fun fromKnownTarget(targetPattern: String, command: BlazeCommandName, sourceElement: PsiElement): RunConfigurationContext {
      return UnifiedRunContext(
        sourceElement = sourceElement,
        target = TargetSpecification.ExplicitPatterns(listOf(targetPattern)),
        filter = null,
        command = CommandComponent(command, emptyList()),
      )
    }
  }
}

/** Convert a [RunConfigurationContext.sourceElement] into an uniquely identifiable string. */
fun RunConfigurationContext.getSourceElementString(): String {
  if (!ApplicationManager.getApplication().isReadAccessAllowed) {
    return ReadAction.compute<String, RuntimeException> { this.getSourceElementString() }
  }
  val element = sourceElement
  if (element is PsiFile) {
    return element.virtualFile?.path ?: element.toString()
  }
  val path = (element.containingFile?.virtualFile?.path ?: "") + '#'
  return when (element) {
    is PsiQualifiedNamedElement -> path + element.qualifiedName
    is PsiNamedElement -> path + element.name
    else -> path + element.toString()
  }
}
