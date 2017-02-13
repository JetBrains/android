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

import com.android.tools.profilers.common.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface IdeProfilerComponents {

  @NotNull
  LoadingPanel createLoadingPanel();

  @NotNull
  TabsPanel createTabsPanel();

  @NotNull
  StackTraceView createStackView(@NotNull StackTraceModel model);

  /**
   * Installs an IntelliJ context menu on a {@link JComponent}.
   *
   * @param component            The target {@link JComponent} that the context menu is to be installed on.
   * @param codeLocationSupplier A {@link Supplier} of the desired code to navigate to. When the supplier is resolved, the system is not
   *                             necessarily ready to conduct the navigation (i.e. displaying the menu popup, awaiting user input).
   * @param navigationCallback   A runnable callback invoked just prior to the actual act of navigating to the code.
   */
  void installNavigationContextMenu(@NotNull JComponent component,
                                    @NotNull Supplier<CodeLocation> codeLocationSupplier,
                                    @Nullable Runnable navigationCallback);

  /**
   * Installs a generic IntelliJ context menu on a {@code component} from the specified {@code contextMenu}.
   * TODO - handles shortcut
   */
  void installContextMenu(@NotNull JComponent component, @NotNull ContextMenuItem contextMenuItem);

  /**
   * Creates an export button placeable in the UI.
   *
   * @param buttonText          Text to display for the button.
   * @param tooltip             Tooltip when the user hovers over the button.
   * @param dialogTitleSupplier Title supplier for the title of the file chooser popup when the user clicks on the button.
   * @param extensionSupplier   Extension supplier for the extension of the target file.
   * @param saveToFile          File consumer for the file to save to (usually method to write to the file).
   */
  @NotNull
  JButton createExportButton(@Nullable String buttonText,
                             @Nullable String tooltip,
                             @NotNull Supplier<String> dialogTitleSupplier,
                             @NotNull Supplier<String> extensionSupplier,
                             @NotNull Consumer<File> saveToFile);

  @NotNull
  FileViewer createFileViewer(@NotNull File file);
}
