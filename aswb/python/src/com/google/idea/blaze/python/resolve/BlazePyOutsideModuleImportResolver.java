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
package com.google.idea.blaze.python.resolve;

import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.python.PySdkUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.impl.PyImportResolver;
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import javax.annotation.Nullable;

/** Resolves python SDK imports in files outside the project (e.g. in bazel-genfiles). */
public class BlazePyOutsideModuleImportResolver implements PyImportResolver {

  @Nullable
  @Override
  public PsiElement resolveImportReference(
      QualifiedName name, PyQualifiedNameResolveContext context, boolean withRoots) {
    Project project = context.getProject();
    if (!Blaze.isBlazeProject(project)) {
      return null;
    }
    if (context.getModule() != null) {
      // the file is associated with a module, so this import resolver is not necessary.
      return null;
    }
    if (context.getFoothold() == null) {
      // we're not resolving in the context of a specific py file, so this hack is unnecessary.
      return null;
    }
    Sdk projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
    if (projectSdk != null && projectSdk.getSdkType() instanceof PythonSdkType) {
      // if this is a python workspace type, imports in external files are already resolved by the
      // python plugin.
      return null;
    }
    Sdk pythonSdk = PySdkUtils.getPythonSdk(context.getProject());
    if (pythonSdk == null) {
      return null;
    }
    for (VirtualFile root : pythonSdk.getRootProvider().getFiles(OrderRootType.CLASSES)) {
      if (!root.isValid() || !root.isDirectory()) {
        continue;
      }
      PsiElement element =
          resolveModuleAt(context.getPsiManager().findDirectory(root), name, context);
      if (element != null) {
        return element;
      }
    }
    return null;
  }

  @Nullable
  private static PsiElement resolveModuleAt(
      @Nullable PsiDirectory directory,
      QualifiedName qualifiedName,
      PyQualifiedNameResolveContext context) {
    if (directory == null || !directory.isValid()) {
      return null;
    }
    PsiElement seeker = directory;
    for (String name : qualifiedName.getComponents()) {
      if (name == null) {
        return null;
      }
      seeker =
          ResolveImportUtil.resolveChild(
              seeker, name, context.getFootholdFile(), false, true, false);
    }
    return seeker;
  }
}
