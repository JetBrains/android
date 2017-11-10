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

import com.android.tools.profilers.stacktrace.DataViewer;
import com.android.tools.profilers.stacktrace.LoadingPanel;
import com.android.tools.profilers.stacktrace.StackTraceModel;
import com.android.tools.profilers.stacktrace.StackTraceView;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collection;

/**
 * Abstractions for various, custom UI components that are useful to use throughout the profilers,
 * which should be implemented by any system wishing to display our profilers.
 *
 * Note: Expectations are that methods in here should alwayws return a created component. In other
 * words, this shouldn't become a home for 'void' utility methods. If such methods are needed,
 * create a helper interface with those void methods, and return that instead.
 */
public interface IdeProfilerComponents {

  /**
   * @param delayMs amount of delay when the loading panel should show up. value <= 0 indicates no delay.
   */
  @NotNull
  LoadingPanel createLoadingPanel(int delayMs);

  @NotNull
  StackTraceView createStackView(@NotNull StackTraceModel model);

  @NotNull
  ContextMenuInstaller createContextMenuInstaller();

  @NotNull
  ExportDialog createExportDialog();

  @NotNull
  DataViewer createFileViewer(@NotNull File file);

  @NotNull
  JComponent createResizableImageComponent(@NotNull BufferedImage image);

  @NotNull
  AutoCompleteTextField createAutoCompleteTextField(@NotNull String placeHolder,
                                                    @NotNull String value,
                                                    @NotNull Collection<String> variants);

  /**
   * Returns the SearchComponent with Regex and MatchCase checkboxes.
   *
   * @param propertyName   the propertyName. Components under the same propertyName share search history.
   * @param textFieldWidth the width of the search text area.
   * @param delayMs        amount of delay when the callback should get called. This delay does not apply to the checkbox.
   * @return
   */
  @NotNull
  SearchComponent createProfilerSearchTextArea(@NotNull String propertyName, int textFieldWidth, int delayMs);
}
