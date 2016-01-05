/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.ui;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;

public final class ApplicationUtils {
  private ApplicationUtils() {
  }

  /**
   * Executes writeAction synchronously on the AWT event dispatch thread.
   */
  public static void invokeWriteActionAndWait(ModalityState state, final Runnable writeAction) {
    final Application application = ApplicationManager.getApplication();

    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        application.runWriteAction(writeAction);
      }
    };

    application.invokeAndWait(runnable, state);
  }
}
