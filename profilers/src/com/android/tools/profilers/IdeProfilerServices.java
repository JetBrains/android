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

import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration;
import com.android.tools.profilers.perfetto.traceprocessor.TraceProcessorService;
import com.android.tools.profilers.stacktrace.CodeNavigator;
import com.android.tools.profilers.stacktrace.NativeFrameSymbolizer;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IdeProfilerServices {
  /**
   * Executor to run the tasks that should get back to the main thread.
   */
  @NotNull
  Executor getMainExecutor();

  /**
   * Executor to run the tasks that should run in a thread from the pool.
   */
  @NotNull
  Executor getPoolExecutor();

  /**
   * @return all classes that belong to the current project (excluding dependent libraries).
   */
  @NotNull
  Set<String> getAllProjectClasses();

  /**
   * Saves a file to the file system and have IDE internal state reflect this file addition.
   *
   * @param file                     File to save to.
   * @param fileOutputStreamConsumer {@link Consumer} to write the file contents into the supplied {@link FileOutputStream}.
   * @param postRunnable             A callback for when the system finally finishes writing to and synchronizing the file.
   */
  void saveFile(@NotNull File file, @NotNull Consumer<FileOutputStream> fileOutputStreamConsumer, @Nullable Runnable postRunnable);

  /**
   * Returns a symbolizer wrapper that can be used for converting a module offset to a
   * {@link com.android.tools.profiler.proto.MemoryProfiler.NativeCallStack}.
   */
  @NotNull
  NativeFrameSymbolizer getNativeFrameSymbolizer();

  /**
   * Returns a service that can navigate to a target code location.
   * <p>
   * Implementors of this method should be sure to return the same instance each time, not a new
   * instance per call.
   */
  @NotNull
  CodeNavigator getCodeNavigator();

  /**
   * Returns an opt-in service that can report when certain features were used.
   * <p>
   * Implementors of this method should be sure to return the same instance each time, not a new
   * instance per call.
   */
  @NotNull
  FeatureTracker getFeatureTracker();

  /**
   * Either enable advanced profiling or present the user with UI to make enabling it easy.
   * <p>
   * By default, advanced profiling features are not turned on, as they require instrumenting the
   * user's code, which at the very least requires a rebuild. Moreover, this may even potentially
   * interfere with the user's app logic or slow it down.
   * <p>
   * If this method is called, it means the user has expressed an intention to enable advanced
   * profiling. It is up to the implementor of this method to help the user accomplish this
   * request.
   */
  void enableAdvancedProfiling();

  @NotNull
  FeatureConfig getFeatureConfig();

  /**
   * Allows the profiler to cache settings within the current studio session.
   * e.g. settings are only preserved across profiling sessions within the same studio instance.
   */
  @NotNull
  ProfilerPreferences getTemporaryProfilerPreferences();

  /**
   * Allows the profiler to cache settings across multiple studio sessions.
   * e.g. settings are preserved when studio restarts.
   */
  @NotNull
  ProfilerPreferences getPersistentProfilerPreferences();

  /**
   * Displays a yes/no dialog warning the user and asking them if they want to proceed.
   *
   * @param message the message content
   * @param title the title
   * @param yesCallback callback to be run if user clicks "Yes"
   * @param noCallback  callback to be run if user clicks "No"
   */
  void openYesNoDialog(String message, String title, Runnable yesCallback, Runnable noCallback);

  /**
   * Opens a dialog giving the user to select items from the listbox.
   *
   * @param title                      tile to be provided to the dialog box.
   * @param message                    optional message to be provided to the user about contents of listbox.
   * @param options                    options used to populate the listbox. The list should not be empty.
   * @param listBoxPresentationAdapter adapter that takes in an option and returns a string to be presented to the user.
   * @return The option the user selected. If the user cancels the return value will be null.
   */
  @Nullable
  <T> T openListBoxChooserDialog(@NotNull String title,
                                 @Nullable String message,
                                 @NotNull List<T> options,
                                 @NotNull Function<T, String> listBoxPresentationAdapter);

  /**
   * Returns the profiling configurations saved by the user for a project.
   * apiLevel is the Android API level for the selected device, so that it return only
   * the appropriate configurations that are available to run on a particular device.
   */
  List<ProfilingConfiguration> getUserCpuProfilerConfigs(int apiLevel);

  /**
   * Returns the default profiling configurations.
   * apiLevel is the Android API level for the selected device, so that it return only
   * the appropriate configurations that are available to run on a particular device.
   */
  List<ProfilingConfiguration> getDefaultCpuProfilerConfigs(int apiLevel);

  /**
   * Whether a native CPU profiling configuration is preferred over a Java one.
   * Native configurations can be preferred for native projects, for instance.
   */
  boolean isNativeProfilingConfigurationPreferred();

  /**
   * Get the native memory sampling rate based on the current configuration.
   */
  int getNativeMemorySamplingRateForCurrentConfig();

  /**
   * Pops up a toast that contains information contained in the notification,
   * which should particularly draw attention to warning and error messages.
   */
  void showNotification(@NotNull Notification notification);

  /**
   * Returns a list of symbol directories for a specific arch type.
   */
  @NotNull
  List<String> getNativeSymbolsDirectories();

  /**
   * Returns a instance for the {@link TraceProcessorService} to be used to communicate with the TraceProcessor daemon in order to
   * parse and query Perfetto traces.
   */
  @NotNull TraceProcessorService getTraceProcessorService();
}
