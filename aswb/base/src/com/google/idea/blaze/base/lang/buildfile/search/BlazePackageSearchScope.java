/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.buildfile.search;

import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** A scope limited to a single blaze/bazel package, which doesn't cross package boundaries. */
public class BlazePackageSearchScope extends GlobalSearchScope {

  private final BlazePackage blazePackage;
  private final boolean onlyBlazeFiles;

  public BlazePackageSearchScope(BlazePackage blazePackage, boolean onlyBlazeFiles) {
    super(blazePackage.buildFile.getProject());
    this.blazePackage = blazePackage;
    this.onlyBlazeFiles = onlyBlazeFiles;
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    if (onlyBlazeFiles && !(psiFile instanceof BuildFile)) {
      return false;
    }
    return blazePackage.equals(BlazePackage.getContainingPackage(psiFile));
  }

  @Override
  public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
    return 0;
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return true;
  }

  @Override
  public boolean isSearchInLibraries() {
    return false;
  }

  @Override
  public String toString() {
    return String.format(
        "%s directory scope: %s",
        Blaze.buildSystemName(getProject()), blazePackage.buildFile.getPackageLabel());
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof BlazePackageSearchScope)) {
      return false;
    }
    if (obj == this) {
      return true;
    }
    BlazePackageSearchScope other = (BlazePackageSearchScope) obj;
    return blazePackage.equals(other.blazePackage) && onlyBlazeFiles == other.onlyBlazeFiles;
  }

  @Override
  public int calcHashCode() {
    return Objects.hash(blazePackage, onlyBlazeFiles);
  }

  @Override
  public String getDisplayName() {
    return blazePackage.toString();
  }

  @Override
  public GlobalSearchScope uniteWith(@NotNull GlobalSearchScope scope) {
    if (scope instanceof BlazePackageSearchScope) {
      BlazePackageSearchScope other = (BlazePackageSearchScope) scope;
      if (!blazePackage.equals(other.blazePackage)) {
        return GlobalSearchScope.EMPTY_SCOPE;
      }
      return onlyBlazeFiles ? this : other;
    }
    return super.uniteWith(scope);
  }
}
