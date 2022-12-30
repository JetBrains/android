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
package com.android.tools.idea.run;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.options.ConfigurationQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;

public final class ValidationUtil {

  public static void promptAndQuickFixErrors(@NotNull Project project, @NotNull DataContext dataContext, @NotNull Collection<ValidationError> errors) throws ExecutionException {
    if (errors.isEmpty()) {
      return;
    }

    for (ValidationError error: errors) {
      ConfigurationQuickFix quickfix = error.getQuickfix();
      if (quickfix != null) {
        if (Messages.showYesNoDialog(project, error.getMessage() + " - do you want to fix it?", "Quick fix", null) == Messages.YES) {
          quickfix.applyFix(dataContext);
          continue;
        }
      }
      throw new ExecutionException(error.getMessage());
    }
  }
}
