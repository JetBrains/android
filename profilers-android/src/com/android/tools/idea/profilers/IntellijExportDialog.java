/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.profilers;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class IntellijExportDialog implements com.android.tools.profilers.ExportDialog {
  private final Project myProject;

  public IntellijExportDialog(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void open(@NotNull Supplier<String> dialogTitleSupplier,
                   @NotNull Supplier<String> extensionSupplier,
                   @NotNull Consumer<File> saveToFile) {
    ApplicationManager.getApplication().invokeLater(() -> {
      String extension = extensionSupplier.get();
      if (extension != null) {
        ExportDialog dialog = new ExportDialog(myProject, dialogTitleSupplier.get(), extension);
        if (dialog.showAndGet()) {
          saveToFile.accept(dialog.getFile());
        }
      }
    });
  }
}
