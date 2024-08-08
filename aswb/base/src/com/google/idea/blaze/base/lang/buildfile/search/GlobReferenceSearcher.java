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
import com.google.idea.blaze.base.lang.buildfile.psi.GlobExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch.SearchParameters;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Searches for references to a file in globs. These aren't picked up by a standard string search,
 * and are only evaluated on demand, so we can't just check a reference cache.
 *
 * <p>Unlike resolving a glob, this requires no file system calls (beyond finding the parent blaze
 * package), because we're only interested in a single file, which is already known to exist.
 *
 * <p>This is always a local search (as glob references can't cross package boundaries).
 */
public class GlobReferenceSearcher extends QueryExecutorBase<PsiReference, SearchParameters> {

  public GlobReferenceSearcher() {
    super(true);
  }

  @Override
  public void processQuery(
      SearchParameters queryParameters, Processor<? super PsiReference> consumer) {
    PsiFileSystemItem file =
        ResolveUtil.asFileSystemItemSearch(queryParameters.getElementToSearch());
    if (file == null) {
      return;
    }
    BlazePackage containingPackage = BlazePackage.getContainingPackage(file);
    if (containingPackage == null || !inScope(queryParameters, containingPackage.buildFile)) {
      return;
    }
    String relativePath = containingPackage.getRelativePathToChild(file.getVirtualFile());
    if (relativePath == null) {
      return;
    }

    List<GlobExpression> globs =
        PsiUtils.findAllChildrenOfClassRecursive(containingPackage.buildFile, GlobExpression.class);
    for (GlobExpression glob : globs) {
      if (glob.matches(relativePath, file.isDirectory())) {
        consumer.process(globReference(glob, file));
      }
    }
  }

  private static PsiReference globReference(GlobExpression glob, PsiFileSystemItem file) {
    return new PsiReferenceBase.Immediate<GlobExpression>(
        glob, glob.getReferenceTextRange(), file) {
      @Override
      public PsiElement bindToElement(@NotNull PsiElement element)
          throws IncorrectOperationException {
        return glob;
      }
    };
  }

  private static boolean inScope(SearchParameters queryParameters, BuildFile buildFile) {
    SearchScope scope = queryParameters.getScopeDeterminedByUser();
    if (scope instanceof GlobalSearchScope) {
      return ((GlobalSearchScope) scope).contains(buildFile.getVirtualFile());
    }
    return ((LocalSearchScope) scope).isInScope(buildFile.getVirtualFile());
  }
}
