// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.android.refactoring;

import com.intellij.psi.PsiElement;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.TestOnly;

public class AndroidInlineTestConfig {
  private final boolean myInlineThisOnly;
  private MultiMap<PsiElement, String> myConflicts = null;

  @TestOnly
  AndroidInlineTestConfig(boolean inlineThisOnly) {
    myInlineThisOnly = inlineThisOnly;
  }

  public boolean isInlineThisOnly() {
    return myInlineThisOnly;
  }

  public void setConflicts(MultiMap<PsiElement, String> conflicts) {
    myConflicts = conflicts;
  }

  @TestOnly
  public MultiMap<PsiElement, String> getConflicts() {
    return myConflicts;
  }
}
