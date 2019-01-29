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
package com.android.tools.profilers;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface ExportDialog {
  /**
   * Opens an export dialog
   *
   * @param dialogTitleSupplier Title supplier for the title of the file chooser popup when the user clicks on the button.
   * @param extensionSupplier   Extension supplier for the extension of the target file.
   * @param saveToFile          File consumer for the file to save to (usually method to write to the file).
   */
  void open(@NotNull Supplier<String> dialogTitleSupplier,
            @NotNull Supplier<String> fileNameSupplier,
            @NotNull Supplier<String> extensionSupplier,
            @NotNull Consumer<File> saveToFile);
}
