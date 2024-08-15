package com.google.idea.sdkcompat.refactoring.safedelete;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.safeDelete.JavaSafeDeleteProcessor;
import com.intellij.refactoring.safeDelete.NonCodeUsageSearchInfo;
import com.intellij.usageView.UsageInfo;
import java.util.List;

/** Compat class for JavaSafeDeleteProcessor. */
public abstract class JavaSafeDeleteProcessorCompat extends JavaSafeDeleteProcessor {

  @Override
  public NonCodeUsageSearchInfo findUsages(
      PsiElement element, PsiElement[] allElementsToDelete, List<? super UsageInfo> result) {
    NonCodeUsageSearchInfo superResult = super.findUsages(element, allElementsToDelete, result);
    return doFindUsages(element, allElementsToDelete, result, superResult);
  }

  protected abstract NonCodeUsageSearchInfo doFindUsages(
      PsiElement element,
      PsiElement[] allElementsToDelete,
      List<? super UsageInfo> result,
      NonCodeUsageSearchInfo superResult);
}
