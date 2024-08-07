/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run.producers;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import java.io.File;
import javax.annotation.Nullable;

/** Runs all tests in a single selected BUILD file. */
class AllInBuildFileTestContextProvider implements TestContextProvider {

  @Nullable
  @Override
  public RunConfigurationContext getTestContext(ConfigurationContext context) {
    PsiElement location = context.getPsiLocation();

    if (!(location instanceof PsiFile)) {
      return null;
    }

    PsiFile file = (PsiFile) location;
    if (!isBuildFile(context, file) || file.getParent() == null) {
      return null;
    }

    WorkspaceRoot root = WorkspaceRoot.fromProject(context.getProject());
    return fromDirectoryNonRecursive(root, file.getParent());
  }

  @Nullable
  private static RunConfigurationContext fromDirectoryNonRecursive(
      WorkspaceRoot root, PsiDirectory dir) {
    WorkspacePath packagePath = getWorkspaceRelativePath(root, dir.getVirtualFile());
    if (packagePath == null) {
      return null;
    }
    return RunConfigurationContext.fromKnownTarget(
        TargetExpression.allFromPackageNonRecursive(packagePath), BlazeCommandName.TEST, dir);
  }

  @Nullable
  private static WorkspacePath getWorkspaceRelativePath(WorkspaceRoot root, VirtualFile vf) {
    return root.workspacePathForSafe(new File(vf.getPath()));
  }

  private static boolean isBuildFile(ConfigurationContext context, PsiFile file) {
    return Blaze.getBuildSystemProvider(context.getProject()).isBuildFile(file.getName());
  }
}
