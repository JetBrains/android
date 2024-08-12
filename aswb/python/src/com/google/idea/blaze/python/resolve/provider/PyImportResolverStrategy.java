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
package com.google.idea.blaze.python.resolve.provider;

import com.google.idea.blaze.base.settings.BuildSystemName;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.codeInsight.imports.AutoImportQuickFix;
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext;
import javax.annotation.Nullable;

/** A strategy for conversion between import strings and blaze artifacts. */
public interface PyImportResolverStrategy {

  ExtensionPointName<PyImportResolverStrategy> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.PyImportResolverStrategy");

  /**
   * Find a python source somewhere in the Blaze workspace, corresponding to the given import
   * string. Not limited to .blazeproject source roots.
   */
  @Nullable
  PsiElement resolveToWorkspaceSource(QualifiedName name, PyQualifiedNameResolveContext context);

  /** Find a python source corresponding to the given name, available during the last blaze sync. */
  @Nullable
  PsiElement resolveFromSyncData(QualifiedName name, PyQualifiedNameResolveContext context);

  /**
   * Add quick fix import suggestions for a given symbol, searching a symbol index built up during
   * the last blaze sync.
   */
  void addImportCandidates(PsiReference reference, String name, AutoImportQuickFix quickFix);

  /** Whether this import resolver strategy is applicable to the given build system */
  boolean appliesToBuildSystem(BuildSystemName buildSystemName);
}
