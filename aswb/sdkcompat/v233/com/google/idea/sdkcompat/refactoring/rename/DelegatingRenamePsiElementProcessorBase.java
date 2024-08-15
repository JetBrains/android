package com.google.idea.sdkcompat.refactoring.rename;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import javax.annotation.Nullable;

/** Compat content for DelegatingRenamePsiElementProcessor. */
public abstract class DelegatingRenamePsiElementProcessorBase extends RenamePsiElementProcessor {
  private volatile RenamePsiElementProcessor baseProcessor;

  @Override
  public boolean isInplaceRenameSupported() {
    return baseProcessor != null
        ? baseProcessor.isInplaceRenameSupported()
        : super.isInplaceRenameSupported();
  }

  @Override
  public boolean forcesShowPreview() {
    return baseProcessor != null ? baseProcessor.forcesShowPreview() : super.forcesShowPreview();
  }

  @Nullable
  protected abstract RenamePsiElementProcessor getDelegate(PsiElement element);

  @Nullable
  protected RenamePsiElementProcessor getDelegateAndStoreState(PsiElement element) {
    RenamePsiElementProcessor delegate = getDelegate(element);
    baseProcessor = delegate;
    return delegate;
  }

  @Override
  public void substituteElementToRename(
      PsiElement element, Editor editor, Pass<? super PsiElement> renameCallback) {
    RenamePsiElementProcessor processor = getDelegateAndStoreState(element);
    if (processor != null) {
      processor.substituteElementToRename(element, editor, renameCallback);
    } else {
      super.substituteElementToRename(element, editor, renameCallback);
    }
  }
}
