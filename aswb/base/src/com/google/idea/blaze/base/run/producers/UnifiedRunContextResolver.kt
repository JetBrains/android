/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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

import com.google.idea.blaze.base.io.VfsUtils
import com.google.idea.blaze.base.run.SourceToTargetFinder
import com.google.idea.blaze.base.run.TestTargetHeuristic
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.awt.RelativePoint
import java.util.Optional
import kotlinx.coroutines.CancellationException

/** Asynchronously resolves pending source file specifications to concrete Bazel target information. */
object UnifiedRunContextResolver {

  /**
   * Resolves a pending target specification to a concrete Bazel target.
   *
   * If heuristic filtering narrows the candidates to 1 target, it auto-resolves immediately. If multiple candidate targets remain, it
   * invokes [TestTargetChooser.chooseTarget] on the EDT. If the user cancels the chooser popup, throws [CancellationException].
   */
  suspend fun resolveTargetSpec(
    project: Project,
    spec: TargetSpecification.PendingResolution,
    popupPosition: RelativePoint?,
  ): TargetSpecification {
    val psiFile = readAction {
      val vf = VfsUtils.resolveVirtualFile(spec.file, true) ?: VfsUtils.resolveVirtualFile(spec.file, false)
      if (vf != null) {
        PsiManager.getInstance(project).findFile(vf)
      } else {
        FilenameIndex.getFilesByName(project, spec.file.name, GlobalSearchScope.projectScope(project)).firstOrNull {
          it.virtualFile.path == spec.file.path || it.virtualFile.path.endsWith(spec.file.path)
        }
      }
    }
    val targets = SourceToTargetFinder.findTargetsForSourceFile(project, spec.file, Optional.ofNullable(spec.ruleType))
    val candidates = TestTargetHeuristic.filterTargetsForSourceFile(project, psiFile, spec.file, targets, spec.testSize)
    val chosen =
      when {
        candidates.size == 1 -> candidates[0]
        candidates.size > 1 -> TestTargetChooser.chooseTarget(popupPosition, candidates) ?: throw CancellationException("No target chosen")
        else -> null
      }
    return if (chosen != null) TargetSpecification.Resolved(chosen) else spec
  }
}
