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
package com.google.idea.blaze.java.lang.build;

import com.google.idea.blaze.base.lang.buildfile.references.GlobReference;
import com.google.idea.blaze.base.lang.buildfile.search.BlazePackage;
import com.google.idea.blaze.base.lang.buildfile.search.ResolveUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.refactoring.safeDelete.JavaSafeDeleteProcessor;
import com.intellij.refactoring.safeDelete.NonCodeUsageSearchInfo;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * Removes glob references which don't refer directly to the item(s) being deleted (b/28979434)
 * (e.g. indirect references from glob([*.java])).
 *
 * <p>Runs before JavaSafeDeleteProcessor, and delegates to it, removing indirect glob references.
 * Only the first valid SafeDeleteProcessorDelegate is used in almost all cases*, so this class
 * effectively replaces JavaSafeDeleteProcessor (*in the situations where all processors are used,
 * this class has no effect).
 */
public class BuildFileSafeDeleteProcessor extends JavaSafeDeleteProcessor {

  @Override
  public NonCodeUsageSearchInfo findUsages(
    PsiElement element, PsiElement[] allElementsToDelete, List<? super UsageInfo> result) {
    NonCodeUsageSearchInfo superResult = super.findUsages(element, allElementsToDelete, result);
    result.removeIf(BuildFileSafeDeleteProcessor::ignoreUsage);
    return superResult;
  }

  /**
   * We keep globs which reference the file directly (i.e. without wildcards), and remove all
   * indirect references for the purposes of the 'safe delete' action.
   */
  private static boolean ignoreUsage(Object o) {
    UsageInfo usage;
    if (o instanceof UsageInfo) {
      usage = (UsageInfo) o;
    } else {
      return false;
    }
    if (usage.getReference() instanceof GlobReference && usage instanceof SafeDeleteUsageInfo) {
      PsiElement referencedElement = ((SafeDeleteUsageInfo) usage).getReferencedElement();
      PsiFileSystemItem file = ResolveUtil.asFileSystemItemSearch(referencedElement);
      String relativePath = getBlazePackageRelativePathToFile(file);
      if (relativePath == null) {
        return false;
      }
      return !((GlobReference) usage.getReference())
          .matchesDirectly(relativePath, file.isDirectory());
    }
    return false;
  }

  @Nullable
  private static String getBlazePackageRelativePathToFile(@Nullable PsiFileSystemItem file) {
    if (file == null) {
      return null;
    }
    BlazePackage containingPackage = BlazePackage.getContainingPackage(file);
    if (containingPackage == null) {
      return null;
    }
    return containingPackage.getRelativePathToChild(file.getVirtualFile());
  }

  @Nullable
  @Override
  public Collection<String> findConflicts(
      @NotNull PsiElement element, @NotNull PsiElement[] allElementsToDelete) {
    return super.findConflicts(element, allElementsToDelete);
  }

  @Nullable
  @Override
  public UsageInfo[] preprocessUsages(Project project, UsageInfo[] usages) {
    return usages;
  }

  @Override
  public void prepareForDeletion(PsiElement element) throws IncorrectOperationException {}

}
