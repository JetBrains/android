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
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import java.util.Optional

/** Asynchronously resolves pending source file specifications to concrete Bazel target information. */
object UnifiedRunContextResolver {
  fun resolveTargetSpec(project: Project, spec: TargetSpecification.PendingResolution): TargetSpecification {
    val vf = VfsUtils.resolveVirtualFile(spec.file, true) ?: VfsUtils.resolveVirtualFile(spec.file, false)
    val psiFile =
      if (vf != null) {
        PsiManager.getInstance(project).findFile(vf)
      } else {
        FilenameIndex.getFilesByName(project, spec.file.name, GlobalSearchScope.projectScope(project)).firstOrNull {
          it.virtualFile.path == spec.file.path || it.virtualFile.path.endsWith(spec.file.path)
        }
      }
    val targets = SourceToTargetFinder.findTargetsForSourceFile(project, spec.file, Optional.ofNullable(spec.ruleType))
    val targetInfo =
      TestTargetHeuristic.chooseTestTargetForSourceFile(project, psiFile, spec.file, targets, spec.testSize) ?: targets.firstOrNull()
    return if (targetInfo != null) TargetSpecification.Resolved(targetInfo) else spec
  }
}
