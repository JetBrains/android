package com.google.idea.sdkcompat.psi.impl.imports;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import java.util.Collection;

/** Compat class for FileReference. */
public abstract class FileReferenceCompat extends FileReference {
  public FileReferenceCompat(
      FileReferenceSet fileReferenceSet, TextRange range, int index, String text) {
    super(fileReferenceSet, range, index, text);
  }

  @Override
  protected void innerResolveInContext(
      String text,
      PsiFileSystemItem context,
      Collection<? super ResolveResult> result,
      boolean caseSensitive) {
    if (doInnerResolveInContext(text, context, result, caseSensitive)) {
      super.innerResolveInContext(text, context, result, caseSensitive);
    }
  }

  protected abstract boolean doInnerResolveInContext(
      String text,
      PsiFileSystemItem context,
      Collection<? super ResolveResult> result,
      boolean caseSensitive);
}
