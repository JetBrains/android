/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface ImportDialog {
  /**
   * Opens an import dialog
   *
   * @param dialogTitleSupplier Supplier for the title of the file chooser popup when the user clicks on the button.
   * @param validExtensions     Valid extensions of the target file.
   * @param fileOpenedCallback  Callback to be called once a file is selected in the import dialog.
   */
  void open(@NotNull Supplier<String> dialogTitleSupplier,
            @NotNull List<String> validExtensions,
            @NotNull Consumer<VirtualFile> fileOpenedCallback);
}
