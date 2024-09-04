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
package com.google.idea.blaze.base.lang.buildfile.validation;

import com.google.idea.blaze.base.lang.buildfile.psi.BuildElementVisitor;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;

/** Base class for Annotator implementations using type-specific methods in BuildElementVisitor */
public abstract class BuildAnnotator extends BuildElementVisitor implements Annotator {

  private volatile AnnotationHolder holder;

  protected AnnotationHolder getHolder() {
    return holder;
  }

  @Override
  public synchronized void annotate(PsiElement element, AnnotationHolder holder) {
    this.holder = holder;
    try {
      element.accept(this);
    } finally {
      this.holder = null;
    }
  }

  protected void markError(PsiElement element, String message) {
    getHolder().newAnnotation(HighlightSeverity.ERROR, message).range(element).create();
  }

  protected void markWarning(PsiElement element, String message) {
    getHolder().newAnnotation(HighlightSeverity.WARNING, message).range(element).create();
  }
}
