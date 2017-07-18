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
package com.android.tools.profilers;

import com.android.tools.profilers.stacktrace.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface IdeProfilerComponents {

  /**
   * @param delayMs Amount of delay when the loading panel should show up. value <= 0 indicates no delay.
   */
  @NotNull
  LoadingPanel createLoadingPanel(int delayMs);

  @NotNull
  StackTraceView createStackView(@NotNull StackTraceModel model);

  /**
   * Installs an IntelliJ context menu on a {@link JComponent} which, when clicked, will navigate
   * to a code location provided by the {@code codeLocationSupplier}.
   *
   * @param component            The target {@link JComponent} that the context menu is to be installed on.
   * @param navigator            A {@link CodeNavigator} that should ultimately handle the navigation, allowing the profiler to respond to
   * @param codeLocationSupplier A {@link Supplier} of the desired code to navigate to. When the supplier is resolved, the system is not
   *                             necessarily ready to conduct the navigation (i.e. displaying the menu popup, awaiting user input).
   */
  void installNavigationContextMenu(@NotNull JComponent component,
                                    @NotNull CodeNavigator navigator,
                                    @NotNull Supplier<CodeLocation> codeLocationSupplier);

  /**
   * Installs a generic IntelliJ context menu on a {@code component} from the specified {@code contextMenu}.
   * TODO - handles shortcut
   */
  void installContextMenu(@NotNull JComponent component, @NotNull ContextMenuItem contextMenuItem);

  /**
   * Opens an export dialog
   *
   * @param dialogTitleSupplier Title supplier for the title of the file chooser popup when the user clicks on the button.
   * @param extensionSupplier   Extension supplier for the extension of the target file.
   * @param saveToFile          File consumer for the file to save to (usually method to write to the file).
   */
  void openExportDialog(@NotNull Supplier<String> dialogTitleSupplier,
                        @NotNull Supplier<String> extensionSupplier,
                        @NotNull Consumer<File> saveToFile);

  @NotNull
  DataViewer createFileViewer(@NotNull File file);

  @NotNull
  JComponent createResizableImageComponent(@NotNull BufferedImage image);
}
