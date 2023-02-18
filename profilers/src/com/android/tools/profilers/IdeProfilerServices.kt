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
package com.android.tools.profilers

import com.android.tools.idea.codenavigation.CodeNavigator
import com.android.tools.profilers.analytics.FeatureTracker
import com.android.tools.profilers.cpu.config.ProfilingConfiguration
import com.android.tools.profilers.perfetto.traceprocessor.TraceProcessorService
import com.android.tools.profilers.stacktrace.NativeFrameSymbolizer
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

interface IdeProfilerServices {
  /**
   * Executor to run the tasks that should get back to the main thread.
   */
  val mainExecutor: Executor

  /**
   * Executor to run the tasks that should run in a thread from the pool.
   */
  val poolExecutor: Executor

  /**
   * Compute expensive intermediate value on "pool", then resume it on "main"
   */
  fun <R> runAsync(supplier: Supplier<R>, consumer: Consumer<R>) {
    CompletableFuture
      .supplyAsync(supplier, poolExecutor)
      .whenComplete { result: R , action -> mainExecutor.execute { consumer.accept(result) } }
  }

  /**
   * @return all classes that belong to the current project (excluding dependent libraries).
   */
  val allProjectClasses: Set<String>

  /**
   * Saves a file to the file system and have IDE internal state reflect this file addition.
   *
   * @param file                     File to save to.
   * @param fileOutputStreamConsumer [Consumer] to write the file contents into the supplied [FileOutputStream].
   * @param postRunnable             A callback for when the system finally finishes writing to and synchronizing the file.
   */
  fun saveFile(file: File, fileOutputStreamConsumer: Consumer<FileOutputStream>, postRunnable: Runnable?)

  /**
   * Returns a symbolizer wrapper that can be used for converting a module offset to a
   * [com.android.tools.profiler.proto.MemoryProfiler.NativeCallStack].
   */
  val nativeFrameSymbolizer: NativeFrameSymbolizer

  /**
   * Returns a service that can navigate to a target code location.
   *
   *
   * Implementors of this method should be sure to return the same instance each time, not a new
   * instance per call.
   */
  val codeNavigator: CodeNavigator

  /**
   * Returns an opt-in service that can report when certain features were used.
   *
   *
   * Implementors of this method should be sure to return the same instance each time, not a new
   * instance per call.
   */
  val featureTracker: FeatureTracker

  /**
   * Either enable advanced profiling or present the user with UI to make enabling it easy.
   *
   *
   * By default, advanced profiling features are not turned on, as they require instrumenting the
   * user's code, which at the very least requires a rebuild. Moreover, this may even potentially
   * interfere with the user's app logic or slow it down.
   *
   *
   * If this method is called, it means the user has expressed an intention to enable advanced
   * profiling. It is up to the implementor of this method to help the user accomplish this
   * request.
   */
  fun enableAdvancedProfiling()
  val featureConfig: FeatureConfig

  /**
   * Allows the profiler to cache settings within the current studio session.
   * e.g. settings are only preserved across profiling sessions within the same studio instance.
   */
  val temporaryProfilerPreferences: ProfilerPreferences

  /**
   * Allows the profiler to cache settings across multiple studio sessions.
   * e.g. settings are preserved when studio restarts.
   */
  val persistentProfilerPreferences: ProfilerPreferences

  /**
   * Displays a yes/no dialog warning the user and asking them if they want to proceed.
   *
   * @param message the message content
   * @param title the title
   * @param yesCallback callback to be run if user clicks "Yes"
   * @param noCallback  callback to be run if user clicks "No"
   */
  fun openYesNoDialog(message: String, title: String, yesCallback: Runnable, noCallback: Runnable)

  /**
   * Opens a dialog asking the user to select items from the listbox.
   *
   * @param title                      tile to be provided to the dialog box.
   * @param message                    optional message to be provided to the user about contents of listbox.
   * @param options                    options used to populate the listbox. The list should not be empty.
   * @param listBoxPresentationAdapter adapter that takes in an option and returns a string to be presented to the user.
   * @return The option the user selected. If the user cancels the return value will be null.
   */
  fun <T> openListBoxChooserDialog(
    title: String,
    message: String?,
    options: List<@JvmSuppressWildcards T>,
    listBoxPresentationAdapter: Function<T, String>
  ): T?

  /**
   * Returns the profiling configurations saved by the user for a project.
   * apiLevel is the Android API level for the selected device, so that it return only
   * the appropriate configurations that are available to run on a particular device.
   */
  fun getUserCpuProfilerConfigs(apiLevel: Int): List<ProfilingConfiguration>

  /**
   * Returns the default profiling configurations.
   * apiLevel is the Android API level for the selected device, so that it return only
   * the appropriate configurations that are available to run on a particular device.
   */
  fun getDefaultCpuProfilerConfigs(apiLevel: Int): List<ProfilingConfiguration>

  /**
   * Whether a native CPU profiling configuration is preferred over a Java one.
   * Native configurations can be preferred for native projects, for instance.
   */
  val isNativeProfilingConfigurationPreferred: Boolean

  /**
   * Get the native memory sampling rate based on the current configuration.
   */
  val nativeMemorySamplingRateForCurrentConfig: Int

  /**
   * Pops up a toast that contains information contained in the notification,
   * which should particularly draw attention to warning and error messages.
   */
  fun showNotification(notification: Notification)

  /**
   * Returns a list of symbol directories for a specific arch type.
   */
  val nativeSymbolsDirectories: List<String>

  /**
   * Returns a instance for the [TraceProcessorService] to be used to communicate with the TraceProcessor daemon in order to
   * parse and query Perfetto traces.
   */
  val traceProcessorService: TraceProcessorService

}