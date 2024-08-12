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

import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.python.resolve.BlazePyResolverUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext;
import java.io.File;
import javax.annotation.Nullable;

/**
 * Bazel has a charmingly simple system for resolving imports. Import paths correspond exactly to
 * either workspace paths or bazel-genfiles-relative paths.
 *
 * <p>Who knew such a straightforward system was even possible?
 */
public class BazelPyImportResolverStrategy extends AbstractPyImportResolverStrategy {

  @Override
  public boolean appliesToBuildSystem(BuildSystemName buildSystemName) {
    return buildSystemName == BuildSystemName.Bazel;
  }

  @Nullable
  @Override
  public PsiElement resolveToWorkspaceSource(
      QualifiedName name, PyQualifiedNameResolveContext context) {
    String relativePath = name.join(File.separator);
    return BlazePyResolverUtils.resolvePath(context, relativePath);
  }

  @Nullable
  @Override
  protected QualifiedName toImportString(ArtifactLocation source) {
    if (source.isGenerated() || !source.getRelativePath().endsWith(".py")) {
      return null;
    }
    return fromRelativePath(source.getRelativePath());
  }
}
