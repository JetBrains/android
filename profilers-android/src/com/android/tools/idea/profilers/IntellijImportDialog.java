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

import com.android.tools.profilers.ImportDialog;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class IntellijImportDialog implements ImportDialog {
  private final Project myProject;

  public IntellijImportDialog(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void open(@NotNull Supplier<String> dialogTitleSupplier,
                   @NotNull List<String> validExtensions,
                   @NotNull Consumer<VirtualFile> fileOpenedCallback) {
    ApplicationManager.getApplication().invokeLater(() -> {
      // Update default file path to user home if myProject.getBasePath() is not valid
      VirtualFile importDir = myProject.getBaseDir();
      if (importDir == null || !importDir.exists()) {
        importDir = VfsUtil.getUserHomeDir();
      }
      // Configure title and extension
      FileChooserDescriptor chooserDescriptor = FileChooserDescriptorFactory
        .createSingleFileDescriptor()
        .withFileFilter(file -> validExtensions.stream()
          .anyMatch(extension -> Comparing.equal(file.getExtension(), extension, file.isCaseSensitive())))
        .withHideIgnored(false);
      chooserDescriptor.setTitle(dialogTitleSupplier.get());
      chooserDescriptor.setDescription("Open file from");
      // Open the dialog with openFromFile as the callback
      FileChooser.chooseFiles(chooserDescriptor, null, importDir,
                              files -> fileOpenedCallback.accept(files.get(0)));
    });
  }
}
