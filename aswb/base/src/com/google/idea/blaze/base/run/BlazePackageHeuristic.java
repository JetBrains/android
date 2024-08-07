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
package com.google.idea.blaze.base.run;

import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.dependencies.TestSize;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import java.io.File;
import javax.annotation.Nullable;

/** Looks for a test rule in the same blaze package as the source file. */
class BlazePackageHeuristic implements TestTargetHeuristic {

  @Override
  public boolean matchesSource(
      Project project,
      TargetInfo target,
      @Nullable PsiFile sourcePsiFile,
      File sourceFile,
      @Nullable TestSize testSize) {
    VirtualFile vf =
        sourcePsiFile != null
            ? sourcePsiFile.getVirtualFile()
            : VfsUtils.resolveVirtualFile(sourceFile, /* refreshIfNeeded= */ true);
    WorkspacePath sourcePackage = findBlazePackage(project, vf);
    if (sourcePackage == null) {
      return false;
    }
    WorkspacePath targetPackage = target.label.blazePackage();
    return sourcePackage.equals(targetPackage);
  }

  @Nullable
  private static WorkspacePath findBlazePackage(Project project, @Nullable VirtualFile vf) {
    BuildSystemProvider provider = Blaze.getBuildSystemProvider(project);
    WorkspaceRoot root = WorkspaceRoot.fromProjectSafe(project);
    if (root == null) {
      return null;
    }
    while (vf != null) {
      if (vf.isDirectory() && provider.findBuildFileInDirectory(vf) != null) {
        return root.workspacePathForSafe(new File(vf.getPath()));
      }
      vf = vf.getParent();
    }
    return null;
  }
}
