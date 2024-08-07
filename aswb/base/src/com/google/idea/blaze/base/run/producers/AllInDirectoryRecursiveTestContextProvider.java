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
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import java.io.File;
import javax.annotation.Nullable;

/** Runs all tests in a selected directory recursively. */
class AllInDirectoryRecursiveTestContextProvider implements TestContextProvider {

  @Nullable
  @Override
  public RunConfigurationContext getTestContext(ConfigurationContext context) {
    PsiElement location = context.getPsiLocation();
    if (!(location instanceof PsiDirectory)) {
      return null;
    }
    WorkspaceRoot root = WorkspaceRoot.fromProject(context.getProject());
    return fromDirectoryRecursive(root, (PsiDirectory) location);
  }

  @Nullable
  private static RunConfigurationContext fromDirectoryRecursive(
      WorkspaceRoot root, PsiDirectory dir) {
    WorkspacePath packagePath = getWorkspaceRelativePath(root, dir.getVirtualFile());
    if (packagePath == null) {
      return null;
    }
    return RunConfigurationContext.fromKnownTarget(
        TargetExpression.allFromPackageRecursive(packagePath), BlazeCommandName.TEST, dir);
  }

  @Nullable
  private static WorkspacePath getWorkspaceRelativePath(WorkspaceRoot root, VirtualFile vf) {
    return root.workspacePathForSafe(new File(vf.getPath()));
  }
}
