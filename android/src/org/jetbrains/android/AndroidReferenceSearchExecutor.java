// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidReferenceSearchExecutor extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
  public AndroidReferenceSearchExecutor() {
    super(true);
  }

  @Override
  public void processQuery(@NotNull ReferencesSearch.SearchParameters params, @NotNull Processor<? super PsiReference> consumer) {
    final PsiElement refElement = params.getElementToSearch();

    if (!(refElement instanceof PsiFile)) {
      return;
    }
    final VirtualFile vFile = ((PsiFile)refElement).getVirtualFile();
    if (vFile == null) {
      return;
    }
    LocalResourceManager manager = LocalResourceManager.getInstance(refElement);
    if (manager == null) {
      return;
    }

    String resType = manager.getFileResourceType((PsiFile)refElement);
    if (resType != null) {
      String resName = AndroidCommonUtils.getResourceName(resType, vFile.getName());
      // unless references can be found by a simple CachedBasedRefSearcher
      if (!resName.equals(vFile.getNameWithoutExtension()) && StringUtil.isNotEmpty(resName)) {
        params.getOptimizer().searchWord(resName, params.getEffectiveSearchScope(), true, refElement);
      }
    }
  }
}
