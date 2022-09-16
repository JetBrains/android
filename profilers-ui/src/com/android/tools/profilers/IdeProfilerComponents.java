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

import com.android.tools.inspectors.common.api.stacktrace.StackTraceModel;
import com.android.tools.inspectors.common.ui.ContextMenuInstaller;
import com.android.tools.inspectors.common.ui.stacktrace.StackTraceGroup;
import com.android.tools.inspectors.common.ui.stacktrace.StackTraceView;
import com.android.tools.profilers.cpu.config.CpuProfilerConfigModel;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration;
import com.android.tools.profilers.stacktrace.LoadingPanel;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

/**
 * Abstractions for various, custom UI components that are useful to use throughout the profilers,
 * which should be implemented by any system wishing to display our profilers.
 * <p>
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
  StackTraceGroup createStackGroup();

  @NotNull
  default StackTraceView createStackView(@NotNull StackTraceModel model) {
    // Delegate to StackTraceGroup to simply create a stack trace view of group size 1
    return createStackGroup().createStackView(model);
  }

  @NotNull
  ContextMenuInstaller createContextMenuInstaller();

  @NotNull
  ExportDialog createExportDialog();

  @NotNull
  ImportDialog createImportDialog();

  @NotNull
  JComponent createResizableImageComponent(@NotNull BufferedImage image);

  @NotNull
  UiMessageHandler createUiMessageHandler();

  /**
   * Open the dialog for managing the CPU profiling configurations.
   *
   * @param profilerModel  {@link CpuProfilerConfigModel} corresponding to the {@link ProfilingConfiguration} to be selected when opening
   *                       the dialog.
   * @param deviceLevel    API level of the device.
   * @param dialogCallback Callback to be called once the dialog is closed. Takes a {@link ProfilingConfiguration}
   *                       that was selected on the configurations list when the dialog was closed.
   */
  void openCpuProfilingConfigurationsDialog(@NotNull CpuProfilerConfigModel profilerModel, int deviceLevel,
                                            @NotNull Consumer<ProfilingConfiguration> dialogCallback);
}
