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
package com.android.tools.idea.flags;

import com.android.flags.Flag;
import com.android.flags.FlagGroup;
import com.android.flags.FlagOverrides;
import com.android.flags.Flags;
import com.android.flags.overrides.DefaultFlagOverrides;
import com.android.flags.overrides.PropertyOverrides;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

/**
 * A collection of all feature flags used by Android Studio. These flags can be used to gate
 * features entirely or branch internal logic of features, e.g. for experimentation or easy
 * rollback.
 *
 * For information on how to add your own flags, see the README.md file under
 * "//tools/base/flags".
 */
public final class StudioFlags {
  private static final Flags FLAGS = createFlags();

  @NotNull
  private static Flags createFlags() {
    Application app = ApplicationManager.getApplication();
    FlagOverrides userOverrides;
    if (app != null && !app.isUnitTestMode()) {
      userOverrides = StudioFlagSettings.getInstance();
    }
    else {
      userOverrides = new DefaultFlagOverrides();
    }
    return new Flags(userOverrides, new PropertyOverrides());
  }

  private static final FlagGroup NPW = new FlagGroup(FLAGS, "npw", "New Project Wizard");

  public static final Flag<Boolean> NPW_DUMP_TEMPLATE_VARS = Flag.create(
    NPW, "dump.template.vars", "Dump template variables to a scratch file",
    "Dump the variables used in creating a template to a scrach file that is opened after creating the project.",
    false);

  public static final Flag<Boolean> NPW_FIRST_RUN_WIZARD = Flag.create(
    NPW, "first.run.wizard", "Show new Welcome Wizard",
    "Show new version of the Welcome Wizard when Studio starts",
    false);

  private static final FlagGroup PROFILER = new FlagGroup(FLAGS, "profiler", "Android Profiler");
  public static final Flag<Boolean> PROFILER_ENABLED = Flag.create(
    PROFILER, "enabled", "Enable \"Android Profiler\" toolbar",
    "Enable the new Android Profiler toolbar, which replaces the Android Monitor toolbar " +
    "and provides more advanced CPU, event, memory, and network profiling information.",
    true);

  public static final Flag<Boolean> PROFILER_USE_JVMTI = Flag.create(
    PROFILER, "jvmti", "Enable JVMTI profiling",
    "Use JVMTI for profiling devices with Android O or newer. " +
    "This unlocks even more profiling features for these devices.",
    true);

  public static final Flag<Boolean> PROFILER_USE_SIMPLEPERF = Flag.create(
    PROFILER, "simpleperf", "Enable Simpleperf profiling",
    "Use Simpleperf for CPU profiling on devices with Android O or newer. " +
    "Simpleperf is a native profiler tool built for Android.",
    true);

  public static final Flag<Boolean> PROFILER_SHOW_THREADS_VIEW = Flag.create(
    PROFILER, "threads.view", "Show network threads view",
    "Show a view in the network profiler that groups connections by their creation thread.",
    true);

  public static final Flag<Boolean> PROFILER_CPU_CAPTURE_FILTER = Flag.create(
    PROFILER, "cpu.capture.filter", "Enable CPU Capture Filter",
    "Show a text input field in the CPU profiler that is used to filter methods in the method trace pane.",
    true);

  public static final Flag<Boolean> PROFILER_MEMORY_CAPTURE_FILTER = Flag.create(
    PROFILER, "memory.capture.filter", "Enable Memory Capture Filter",
    "Show a text input field in the Memory profiler that is used to filter class names.",
    true);

  public static final Flag<Boolean> PROFILER_USE_LIVE_ALLOCATIONS = Flag.create(
    PROFILER, "livealloc", "Enable JVMTI-based live allocation tracking",
    "For Android O or newer, allocations are tracked all the time while inside the Memory Profiler.",
    true);

  public static final Flag<Boolean> PROFILER_MEMORY_SNAPSHOT = Flag.create(
    PROFILER, "memory.livealloc.snapshot", "Enable Memory Class Histogram Display",
    "For Android O or newer, supports single-point selection which shows a snapshot of the heap at the specific time.",
    true);

  public static final Flag<Boolean> PROFILER_NETWORK_REQUEST_PAYLOAD = Flag.create(
    PROFILER, "network.request.payload", "Enable tracking and displaying connection request payload",
    "Add a new tab in the network profiler that shows the connection request payload",
    true);

  public static final Flag<Boolean> PROFILER_USE_ATRACE = Flag.create(
    PROFILER, "atrace", "Show the atrace option in CPU profiler",
    "Toggles if atrace is a valid option to choose from the CPU profiling dropdown.",
    false);

  private static final FlagGroup NELE = new FlagGroup(FLAGS, "nele", "Layout Editor");
  public static final Flag<Boolean> NELE_ANIMATIONS_PREVIEW = Flag.create(
    NELE, "animated.preview", "Show preview animations toolbar",
    "Show an animations bar that allows playback of vector drawable animations.",
    false);
  public static final Flag<Boolean> NELE_TRANSITION_LAYOUT_ANIMATIONS = Flag.create(
    NELE, "animated.transition.layout", "Show preview animations toolbar for TransitionLayout",
    "Show an animations bar that allows playback of TransitionLayout animations.",
    false);

  public static final Flag<Boolean> NELE_SAMPLE_DATA = Flag.create(
    NELE, "mock.data", "Enable \"Sample Data\" for the layout editor",
    "Enables the use of @sample references in the tools namespace to use sample data.",
    true);

  public static final Flag<Boolean> NELE_MOCKUP_EDITOR = Flag.create(
    NELE, "mockup.editor", "Enable the Mockup Editor",
    "Enable the Mockup Editor to ease the creation of Layouts from a design file.",
    false);

  public static final Flag<Boolean> NELE_LIVE_RENDER = Flag.create(
    NELE, "live.render", "Enable the Live Render",
    "Enable the continuous rendering of the surface when moving/resizing components.",
    true);

  public static final Flag<Boolean> NELE_WIDGET_ASSISTANT = Flag.create(
    NELE, "widget.assistant", "Enable the properties panel Widget Assistant",
    "Enable the Widget Assistant that provides common shortcuts for certain widgets.",
    false);

  public static final Flag<Boolean> NELE_CONVERT_VIEW = Flag.create(
    NELE, "convert.view", "Enable the Convert View Action",
    "Enable the Convert View Action when right clicking on a component",
    true);

  private static final FlagGroup ASSISTANT = new FlagGroup(FLAGS, "assistant", "Assistants");
  public static final Flag<Boolean> CONNECTION_ASSISTANT_ENABLED = Flag.create(
    ASSISTANT, "connection.enabled", "Enable the connection assistant",
    "If enabled, user can access the Connection Assistant under \"Tools\" and \"Deploy Target Dialog\"",
    true);

  public static final Flag<Boolean> NELE_TARGET_RELATIVE = Flag.create(
    NELE, "target.relative", "Enable the target architecture in relative layout",
    "Enable the new Target architecture in relative layout",
    true);

  public static final Flag<Boolean> NELE_NEW_PALETTE = Flag.create(
    NELE, "new.palette", "Enable the new Palette",
    "Enable the new Palette with advanced Search",
    true);

  private static final FlagGroup RUNDEBUG_GROUP = new FlagGroup(FLAGS, "rundebug", "Run/Debug");
  public static final Flag<Boolean> RUNDEBUG_LOGCAT_CONSOLE_OUTPUT_ENABLED = Flag.create(
    RUNDEBUG_GROUP, "logcat.console.output.enabled", "Show logcat process output in Run/Debug console window",
    "When running or debugging an Android process, output the logcat output of the process in the console window.",
    true);

  private static final FlagGroup GRADLE_IDE = new FlagGroup(FLAGS, "gradle.ide", "Gradle Project System");
  public static final Flag<Boolean> GRADLE_INVOCATIONS_INDEXING_AWARE = Flag.create(
    GRADLE_IDE, "indexing.aware", "Execute gradle actions in indexing-aware mode",
    "Make Gradle actions and IDE indexing mutually exclusive to allow better utilisation of machine resources.",
    true);
  public static final Flag<Boolean> NEW_SYNC_INFRA_ENABLED = Flag.create(
    GRADLE_IDE, "new.sync", "Enable \"New Sync\" infrastructure",
    "Turns on the new infrastructure for \"Gradle Sync\", resulting in faster Sync executions.", false);
  public static final Flag<Boolean> NEW_PSD_ENABLED = Flag.create(
    GRADLE_IDE, "new.psd", "Enable new \"Project Structure\" dialog",
    "Turns on the new \"Project Structure\" dialog.", false);

  private static final FlagGroup SQLITE_VIEWER = new FlagGroup(FLAGS, "sqlite.viewer", "SQLite Viewer");
  public static final Flag<Boolean> SQLITE_VIEWER_ENABLED = Flag.create(
    SQLITE_VIEWER, "enabled", "Enable the SQLite database viewer",
    "If enabled, SQLite files downloaded from Android devices or emulators are open in a custom SQLite editor window",
    false);

  private static final FlagGroup RESOURCES_MANAGEMENT = new FlagGroup(FLAGS, "res.manag", "Resource Management");
  public static final Flag<Boolean> RESOURCE_MANAGER_ENABLED = Flag.create(
    RESOURCES_MANAGEMENT, "enabled", "Enable the new resources management tools",
    "If enabled, the new resource magement tool are enabled. Subflags will also need to be enabled to enable all available new tools",
    false);

  private static final FlagGroup LAYOUT_INSPECTOR = new FlagGroup(FLAGS, "layout.inspector", "Layout Inspector");
  public static final Flag<Boolean> LAYOUT_INSPECTOR_LOAD_OVERLAY_ENABLED = Flag.create(
    LAYOUT_INSPECTOR, "load.overlay", "Enable the Load Overlay feature",
    "If enabled, Show actions to let user choose overlay image on preview.", true);
  public static final Flag<Boolean> LAYOUT_INSPECTOR_SUB_VIEW_ENABLED = Flag.create(
    LAYOUT_INSPECTOR, "sub.view", "Enable the sub view feature",
    "If enabled, changes the preview to focus on a component.", true);

  private static final FlagGroup REFACTORINGS = new FlagGroup(FLAGS, "refactor", "Refactor menu");
  public static final Flag<Boolean> MIGRATE_TO_APPCOMPAT_REFACTORING_ENABLED = Flag.create(
    REFACTORINGS, "enabled", "Enable the Migrate to AppCompat refactoring feature",
    "If enabled, show the action in the refactoring menu", true);

  private StudioFlags() {
  }
}
