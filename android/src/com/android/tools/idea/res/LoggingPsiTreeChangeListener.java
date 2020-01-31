/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.res;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.PsiTreeChangeListener;
import org.jetbrains.annotations.NotNull;

class LoggingPsiTreeChangeListener implements PsiTreeChangeListener {
  @NotNull private final PsiTreeChangeListener myWrappedListener;
  @NotNull private final Logger myLogger;

  LoggingPsiTreeChangeListener(@NotNull PsiTreeChangeListener wrappedListener, @NotNull Logger logger) {
    myWrappedListener = wrappedListener;
    myLogger = logger;
  }

  @Override
  public void beforeChildAddition(@NotNull PsiTreeChangeEvent event) {
    logEvent(event);
    myWrappedListener.beforeChildAddition(event);
  }

  @Override
  public void beforeChildRemoval(@NotNull PsiTreeChangeEvent event) {
    logEvent(event);
    myWrappedListener.beforeChildRemoval(event);
  }

  @Override
  public void beforeChildReplacement(@NotNull PsiTreeChangeEvent event) {
    logEvent(event);
    myWrappedListener.beforeChildReplacement(event);
  }

  @Override
  public void beforeChildMovement(@NotNull PsiTreeChangeEvent event) {
    logEvent(event);
    myWrappedListener.beforeChildMovement(event);
  }

  @Override
  public void beforeChildrenChange(@NotNull PsiTreeChangeEvent event) {
    logEvent(event);
    myWrappedListener.beforeChildrenChange(event);
  }

  @Override
  public void beforePropertyChange(@NotNull PsiTreeChangeEvent event) {
    logEvent(event);
    myWrappedListener.beforePropertyChange(event);
  }

  @Override
  public void childAdded(@NotNull PsiTreeChangeEvent event) {
    logEvent(event);
    myWrappedListener.childAdded(event);
  }

  @Override
  public void childRemoved(@NotNull PsiTreeChangeEvent event) {
    logEvent(event);
    myWrappedListener.childRemoved(event);
  }

  @Override
  public void childReplaced(@NotNull PsiTreeChangeEvent event) {
    logEvent(event);
    myWrappedListener.childReplaced(event);
  }

  @Override
  public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
    logEvent(event);
    myWrappedListener.childrenChanged(event);
  }

  @Override
  public void childMoved(@NotNull PsiTreeChangeEvent event) {
    logEvent(event);
    myWrappedListener.childMoved(event);
  }

  @Override
  public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
    logEvent(event);
    myWrappedListener.propertyChanged(event);
  }

  private void logEvent(@NotNull PsiTreeChangeEvent event) {
    myLogger.debug("Handling event ", event);
  }
}
