/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.explorer;

import com.android.tools.idea.concurrency.FutureCallbackExecutor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

class ExecutorUtil {
  /**
   * Utility function that runs the runnable in a write-safe context, without waiting for modal dialogs to be closed.
   */
  static void executeInWriteSafeContextWithAnyModality(@NotNull Project project, @NotNull FutureCallbackExecutor edtExecutor, @NotNull Runnable runnable) {
    edtExecutor.execute(() -> {
      // edtExecutor is wrapped with FutureCallbackExecutor, therefore we can't check that it's the correct type of executor.
      ApplicationManager.getApplication().assertIsDispatchThread();
      // invokeLater has to be called on EDT, otherwise it waits for all modal dialogs to be closed.
      ApplicationManager.getApplication().invokeLater(runnable, project.getDisposed());
    });
  }
}
