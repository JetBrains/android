/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.wizard.dynamic;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * This interface allows decoupling the dynamic wizard from underlying
 * widgets, allowing to put it into dialog, standalone JFrame or (in theory)
 * into a pane or editor.
 */
public interface DynamicWizardHost {
  /**
   * @return parent disposable for this wizard invocation
   */
  Disposable getDisposable();

  /**
   * Initializes peer with a wizard
   */
  void init(@NotNull DynamicWizard wizard);

  /**
   * Show modal wizard UI
   */
  void show();

  /**
   * Show modal wizard UI and wait for completion
   *
   * @return <code>false</code> if the user canceled the wizard
   */
  boolean showAndGet();

  /**
   * Close the wizard and mark it a completed or cancelled
   */
  void close(@NotNull CloseAction action);

  /**
   * Shake the window
   */
  void shakeWindow();

  /**
   * Update enabled status of the cancel, previous, next and finish buttons
   */
  void updateButtons(boolean canGoPrev, boolean canGoNext, boolean canCancel, boolean canFinish);

  /**
   * Set window title
   */
  void setTitle(String title);

  /**
   * Set preferred size for a window
   */
  void setPreferredWindowSize(Dimension dimension);

  /**
   * Update an icon
   */
  void setIcon(@Nullable Icon icon);

  /**
   * Runs a sensitive operation. These are operations that should not be interrupted, e.g.
   * by the user closing the window.
   */
  void runSensitiveOperation(@NotNull ProgressIndicator progressIndicator, boolean cancellable,
                             @NotNull Runnable operation);

  enum CloseAction {
    FINISH, CANCEL, EXIT
  }
}
