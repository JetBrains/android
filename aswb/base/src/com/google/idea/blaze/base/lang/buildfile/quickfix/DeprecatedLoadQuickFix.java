/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.buildfile.quickfix;

import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.lang.buildfile.references.LabelUtils;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import java.io.File;
import javax.annotation.Nullable;

/** Offer to convert deprecated statements to label format. */
public class DeprecatedLoadQuickFix implements LocalQuickFix, HighPriorityAction {

  public static final DeprecatedLoadQuickFix INSTANCE = new DeprecatedLoadQuickFix();

  @Override
  public String getFamilyName() {
    return "Fix load statement format";
  }

  @Override
  public String getName() {
    return getFamilyName();
  }

  @Override
  public void applyFix(Project project, ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    if (element instanceof StringLiteral) {
      fixLoadString(project, (StringLiteral) element);
    }
  }

  private static void fixLoadString(Project project, StringLiteral importString) {
    String contents = importString.getStringContents();
    if (!contents.startsWith("/") || LabelUtils.isAbsolute(contents)) {
      return;
    }
    WorkspaceRoot root = WorkspaceRoot.fromProjectSafe(project);
    if (root == null) {
      return;
    }
    WorkspacePath workspacePath = WorkspacePath.createIfValid(contents.substring(1));
    if (workspacePath == null) {
      return;
    }
    File file = root.fileForPath(workspacePath);
    File parentPackageFile = findContainingPackage(project, file);
    if (parentPackageFile == null) {
      return;
    }
    WorkspacePath packagePath = root.workspacePathForSafe(parentPackageFile);
    if (packagePath == null) {
      return;
    }
    String relativePath;
    try {
      relativePath = packagePath.asPath().relativize(workspacePath.asPath()).toString();
    }
    catch (IllegalArgumentException ignored) {
      // workspacePath is not under packagePath.
      return;
    }
    String newString = "//" + packagePath + ":" + relativePath + ".bzl";

    ASTNode node = importString.getNode();
    node.replaceChild(
        node.getFirstChildNode(), PsiUtils.createNewLabel(importString.getProject(), newString));
  }

  @Nullable
  private static File findContainingPackage(Project project, File file) {
    BuildSystemProvider provider = Blaze.getBuildSystemProvider(project);
    file = file.getParentFile();
    while (file != null) {
      ProgressManager.checkCanceled();
      File buildFile = provider.findBuildFileInDirectory(file);
      if (buildFile != null) {
        return file;
      }
      file = file.getParentFile();
    }
    return null;
  }
}
