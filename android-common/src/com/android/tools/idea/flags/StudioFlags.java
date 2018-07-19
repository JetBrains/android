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
 * <p>
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

  public static final Flag<Boolean> NPW_DYNAMIC_APPS = Flag.create( // TODO: Remove in b/109788793
    NPW, "dynamic.apps", "New Dynamic App Project flow",
    "Use new Dynamic App flow when creating a New Mobile Project",
    false);

  public static final Flag<Boolean> NPW_USE_HOME_FOLDER_AS_EXTRA_TEMPLATE_ROOT_FOLDER = Flag.create(
    NPW, "home.template.root", "Use .android folder as a Template Root Folder",
    "Let the user keep templates in the .android folder such that they are kept after a Studio install/upgrade",
    true);

  private static final FlagGroup PROFILER = new FlagGroup(FLAGS, "profiler", "Android Profiler");

  public static final Flag<Boolean> PROFILER_SHOW_SESSIONS = Flag.create(
    PROFILER, "show.session", "Enable the sessions panel",
    "Shows the sessions panel used for managing and navigating profiling data.",
    true);

  public static final Flag<Boolean> PROFILER_IMPORT_SESSION = Flag.create(
    PROFILER, "import.session", "Enable the session import dialog",
    "Shows the file open drop down menu for session import.",
    true);

  public static final Flag<Boolean> PROFILER_ENERGY_PROFILER_ENABLED = Flag.create(
    PROFILER, "energy", "Enable Energy profiling",
    "Enable the new energy profiler. It monitors battery usage of the selected app.", true);

  public static final Flag<Boolean> PROFILER_IMPORT_CPU_TRACE = Flag.create(
    PROFILER, "cpu.import.trace", "Enable CPU trace importing",
    "Add the option to import CPU trace files when right-clicking the CPU profiler usage chart.",
    true);

  public static final Flag<Boolean> PROFILER_EXPORT_CPU_TRACE = Flag.create(
    PROFILER, "cpu.export.trace", "Enable CPU trace exporting",
    "Add the option to export CPU trace files when right-clicking a CPU capture.",
    true);

  public static final Flag<Boolean> PROFILER_OPEN_CAPTURES = Flag.create(
    PROFILER, "profiler.open.captures", "Enable opening .trace and .hprof files",
    "Allow opening .hprof and .trace files (e.g. File -> Open; via Drag & Drop) which imports them into Android Profiler.",
    true);

  public static final Flag<Boolean> PROFILER_STARTUP_CPU_PROFILING = Flag.create(
    PROFILER, "startup.cpu.profiling", "Enable startup CPU Profiling",
    "Record a method trace on startup by enabling it in the Profiler tab of Run/Debug configuration.",
    true);

  public static final Flag<Boolean> PROFILER_CPU_API_TRACING = Flag.create(
    PROFILER, "cpu.api.tracing", "Enable CPU API Tracing",
    "Support method tracing through APIs from android.os.Debug.",
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
    true);

  public static final Flag<Boolean> PROFILER_TRACK_JNI_REFS = Flag.create(
    PROFILER, "jni", "Enable JVMTI-based JNI reference tracking.",
    "For Android O or newer, JNI references are tracked in Memory Profiler and shown in JNI heap.",
    true);

  public static final Flag<Boolean> PROFILER_PERFORMANCE_MONITORING = Flag.create(
    PROFILER, "performance.monitoring", "Enable Profiler Performance Monitoring Options",
    "Toggles if profiler performance metrics options are enabled.",
    false
  );

  private static final FlagGroup NELE = new FlagGroup(FLAGS, "nele", "Layout Editor");
  public static final Flag<Boolean> NELE_ANIMATIONS_PREVIEW = Flag.create(
    NELE, "animated.preview", "Show preview animations toolbar",
    "Show an animations bar that allows playback of vector drawable animations.",
    false);
  public static final Flag<Boolean> NELE_MOTION_LAYOUT_ANIMATIONS = Flag.create(
    NELE, "animated.motion.layout", "Show preview animations toolbar for MotionLayout",
    "Show an animations bar that allows playback of MotionLayout animations.",
    false);
  public static final Flag<Boolean> NELE_MOTION_LAYOUT_EDITOR = Flag.create(
    NELE, "animated.motion.editor", "Show motion editor for MotionLayout",
    "Show the motion editor UI for MotionLayout.",
    false);
  public static final Flag<Boolean> NELE_MOTION_HORIZONTAL = Flag.create(
    NELE, "animated.motion.horizontal", "Display motion editor horizontally",
    "Controls the placement of the motion editor (horizontal versus vertical).",
    true);
  public static final Flag<Boolean> NELE_MOCKUP_EDITOR = Flag.create(
    NELE, "mockup.editor", "Enable the Mockup Editor",
    "Enable the Mockup Editor to ease the creation of Layouts from a design file.",
    false);

  public static final Flag<Boolean> NELE_LIVE_RENDER = Flag.create(
    NELE, "live.render", "Enable the Live Render",
    "Enable the continuous rendering of the surface when moving/resizing components.",
    true);

  public static final Flag<Boolean> NELE_SAMPLE_DATA_UI = Flag.create(
    NELE, "widget.assistant", "Enable the new Sample Data UI components",
    "Enable the Sample Data UI to setup tools attributes.",
    true);

  public static final Flag<Boolean> NELE_CONVERT_VIEW = Flag.create(
    NELE, "convert.view", "Enable the Convert View Action",
    "Enable the Convert View Action when right clicking on a component",
    true);

  public static final Flag<Boolean> ENABLE_NEW_SCOUT = Flag.create(
    NELE, "exp.scout.engine", "Experimental version of the Scout inference system",
    "Enable experimental version of the Scout inference system",
    false);

  public static final Flag<Boolean> NELE_USE_ANDROIDX_DEFAULT = Flag.create(
    NELE, "androidx.default", "Use androidx. support lib by default",
    "Enable the use of androidx dependencies by default when the old support library is not present",
    true);

  public static final Flag<Boolean> NELE_RENDER_DIAGNOSTICS = Flag.create(
    NELE, "diagnostics", "Enable rendering on-screen stats",
    "If enabled, the surface displays some debug information to diagnose performance",
    false);

  private static final FlagGroup ASSISTANT = new FlagGroup(FLAGS, "assistant", "Assistants");
  public static final Flag<Boolean> CONNECTION_ASSISTANT_ENABLED = Flag.create(
    ASSISTANT, "connection.enabled", "Enable the connection assistant",
    "If enabled, user can access the Connection Assistant under \"Tools\" and \"Deploy Target Dialog\"",
    true);

  public static final Flag<Boolean> WHATS_NEW_ASSISTANT_ENABLED = Flag.create(
    ASSISTANT, "whats.new.enabled", "Enable the \"What's New\" assistant",
    "If enabled, user can access the \"What's New\" assistant under \"Help\" and \"What's New in Android Studio\"",
    true);

  public static final Flag<Boolean> WHATS_NEW_ASSISTANT_AUTO_SHOW = Flag.create(
    ASSISTANT, "whats.new.auto.show", "Displays the \"What's New\" assistant on first start",
    "If enabled, the \"What's New\" assistant will be displayed the first time user opens a new version of Android Studio.",
    true);

  public static final Flag<Boolean> NELE_NEW_PROPERTY_PANEL = Flag.create(
    NELE, "new.property", "Enable the new Property Panel",
    "Enable the new Property Panel",
    false);

  public static final Flag<Boolean> NELE_NEW_COLOR_PICKER = Flag.create(
    NELE, "new.color.picker", "New Color Picker",
    "Enable new Color Picker in layout Editor",
    false);

  private static final FlagGroup RUNDEBUG = new FlagGroup(FLAGS, "rundebug", "Run/Debug");
  public static final Flag<Boolean> RUNDEBUG_LOGCAT_CONSOLE_OUTPUT_ENABLED = Flag.create(
    RUNDEBUG, "logcat.console.output.enabled", "Show logcat process output in Run/Debug console window",
    "When running or debugging an Android process, output the logcat output of the process in the console window.",
    true);
  public static final Flag<Boolean> RUNDEBUG_USE_AIA_SDK_LIBRARY = Flag.create(
    RUNDEBUG, "instantapps.sdklib.enabled", "Use external SDK library to launch Instant Apps",
    "When provisioning devices and launching Instant Apps, use the AIA SDK library JAR to perform these functions if available",
    true);

  public static final Flag<Boolean> RUNDEBUG_ANDROID_BUILD_BUNDLE_ENABLED = Flag.create(
    RUNDEBUG, "android.bundle.build.enabled", "Enable the Build Bundle action",
    "If enabled, the \"Build Bundle(s)\" menu item is enabled. " +
    "Changing the value of this flag requires restarting Android Studio.",
    true);

  public static final Flag<Boolean> SELECT_DEVICE_COMBO_BOX_ACTION_VISIBLE = Flag.create(
    RUNDEBUG,
    "select.device.combo.box.action.visible",
    "Show the Select Device combo box action",
    "Show the Select Device combo box action next to the Select Run/Debug Configuration one in the toolbar",
    false);

  public static final Flag<Boolean> JVMTI_REFRESH = Flag.create(
    RUNDEBUG,
    "jvmti.refresh",
    "Application refresh with JVMTI",
    "Use JVMTI to support application refresh. This implies incremental deployment",
    false);

  private static final FlagGroup GRADLE_IDE = new FlagGroup(FLAGS, "gradle.ide", "Gradle Project System");
  public static final Flag<Boolean> FIX_ANDROID_RUN_CONFIGURATIONS_ENABLED = Flag.create(
    GRADLE_IDE, "gradle.run.configuration.fix.enabled",
    "Check Android Run Configurations contains the \"Gradle-aware Make\" task and fix them",
    "When a project is loaded, automatically add a \"Gradle-aware Make\" task to each Run Configuration if the task is missing",
    true);

  public static final Flag<Boolean> GRADLE_INVOCATIONS_INDEXING_AWARE = Flag.create(
    GRADLE_IDE, "indexing.aware", "Execute gradle actions in indexing-aware mode",
    "Make Gradle actions and IDE indexing mutually exclusive to allow better utilisation of machine resources.",
    true);
  public static final Flag<Boolean> NEW_SYNC_INFRA_ENABLED = Flag.create(
    GRADLE_IDE, "new.sync", "Enable \"New Sync\" infrastructure",
    "Turns on the new infrastructure for \"Gradle Sync\", resulting in faster Sync executions.", false);
  public static final Flag<Boolean> NEW_PSD_ENABLED = Flag.create(
    GRADLE_IDE, "new.psd", "Enable new \"Project Structure\" dialog",
    "Turns on the new \"Project Structure\" dialog.", true);
  public static final Flag<Boolean> SINGLE_VARIANT_SYNC_ENABLED = Flag.create(
    GRADLE_IDE, "single.variant.sync", "Enable new \"Single-Variant Sync\"",
    "Turns on Single-Variant Sync.", false);
  public static final Flag<Boolean> COMPOUND_SYNC_ENABLED = Flag.create(
    GRADLE_IDE, "compound.sync", "Enable new \"Compound Sync\"",
    "Turns on Compound Sync.", false);
  public static final Flag<Boolean> SHIPPED_SYNC_ENABLED = Flag.create(
    GRADLE_IDE, "shipped.sync", "Enable \"Shipped Sync\"",
    "Use shipped Gradle Sync when possible e.g. in freshly created projects. Allows to avoid running an actual Gradle Sync.",
    false);

  // REMOVE or change default to true after http://b/80245603 is fixed.
  public static final Flag<Boolean> L4_DEPENDENCY_MODEL = Flag.create(
    GRADLE_IDE, "level4.dependency.model", "Use L4 DependencyGraph Model",
    "Use level4 DependencyGraph model.", false);

  private static final FlagGroup SQLITE_VIEWER = new FlagGroup(FLAGS, "sqlite.viewer", "SQLite Viewer");
  public static final Flag<Boolean> SQLITE_VIEWER_ENABLED = Flag.create(
    SQLITE_VIEWER, "enabled", "Enable the SQLite database viewer",
    "If enabled, SQLite files downloaded from Android devices or emulators are open in a custom SQLite editor window",
    false);

  private static final FlagGroup RESOURCES_MANAGEMENT = new FlagGroup(FLAGS, "res.manag", "Resource Management");
  public static final Flag<Boolean> RESOURCE_MANAGER_ENABLED = Flag.create(
    RESOURCES_MANAGEMENT, "enabled", "Enable the new resources management tools",
    "If enabled, the new resource management tools are enabled. Subflags will also need to be enabled to enable all available new tools",
    false);

  private static final FlagGroup LAYOUT_INSPECTOR = new FlagGroup(FLAGS, "layout.inspector", "Layout Inspector");
  public static final Flag<Boolean> LAYOUT_INSPECTOR_LOAD_OVERLAY_ENABLED = Flag.create(
    LAYOUT_INSPECTOR, "load.overlay", "Enable the Load Overlay feature",
    "If enabled, Show actions to let user choose overlay image on preview.", true);
  public static final Flag<Boolean> LAYOUT_INSPECTOR_SUB_VIEW_ENABLED = Flag.create(
    LAYOUT_INSPECTOR, "sub.view", "Enable the sub view feature",
    "If enabled, changes the preview to focus on a component.", true);
  public static final Flag<Boolean> LAYOUT_INSPECTOR_V2_PROTOCOL_ENABLED = Flag.create(
    LAYOUT_INSPECTOR, "capture.v2", "Enable using V2 protocol to capture view data",
    "If enabled, uses V2 protocol to capture view information from device.", false);
  public static final Flag<Boolean> LAYOUT_INSPECTOR_EDITING_ENABLED = Flag.create(
    LAYOUT_INSPECTOR, "ui.editing", "Enable editing ViewNode properties in the properties table.",
    "If enabled, users can edit properties in the properties table.", false);

  private static final FlagGroup REFACTORINGS = new FlagGroup(FLAGS, "refactor", "Refactor menu");
  public static final Flag<Boolean> MIGRATE_TO_APPCOMPAT_REFACTORING_ENABLED = Flag.create(
    REFACTORINGS, "migrateto.appcompat.enabled", "Enable the Migrate to AppCompat refactoring feature",
    "If enabled, show the action in the refactoring menu", true);
  public static final Flag<Boolean> MIGRATE_TO_ANDROID_X_REFACTORING_ENABLED = Flag.create(
    REFACTORINGS, "migrateto.androidx.enabled", "Enable the Migrate to AndroidX refactoring feature",
    "If enabled, show the action in the refactoring menu", true);
  public static final Flag<Boolean> MIGRATE_TO_RESOURCE_NAMESPACES_REFACTORING_ENABLED = Flag.create(
    REFACTORINGS, "migrateto.resourcenamespaces.enabled", "Enable the Migrate to Resource Namespaces refactoring feature",
    "If enabled, show the action in the refactoring menu", false);

  private static final FlagGroup IOT = new FlagGroup(FLAGS, "iot", "IoT features");
  public static final Flag<Boolean> UNINSTALL_LAUNCHER_APPS_ENABLED = Flag.create(
    IOT, "iot.uninstalllauncherapps.enabled", "Enable the Uninstall of IoT launcher apps feature",
    "If enabled, uninstall IoT launcher apps when installing a new one", false);

  private static final FlagGroup NDK = new FlagGroup(FLAGS, "ndk", "Native code features");
  public static final Flag<Boolean> CMAKE_ENABLE_FEATURES_FROM_CLION = Flag
    .create(NDK, "cmakeclionfeatures", "Enable CMake language support from CLion",
            "If enabled, language support features (e.g. syntax highlighting) currently present in CLion will be turned on.", true);
  public static final Flag<Boolean> LLDB_ASSEMBLY_DEBUGGING = Flag.create(
    NDK, "debugging.assembly", "Enable assembly debugging",
    "If enabled, frames without sources will show the assembly of the function and allow breakpoints to be set there", false);

  public static final Flag<Boolean> ENABLE_ENHANCED_NATIVE_HEADER_SUPPORT = Flag
    .create(NDK, "enhancednativeheadersupport", "Enable enhanced native header support",
            "If enabled, project system view will show a new include node with organized header files", true);

  private static final FlagGroup NAVIGATION = new FlagGroup(FLAGS, "navigation", "Navigation Editor");
  public static final Flag<Boolean> ENABLE_NAV_EDITOR = Flag.create(
    NAVIGATION, "enable.nav.editor", "Enable the Navigation Editor",
    "If enabled, it will be possible to create and edit navigation resource files", true);

  private static final FlagGroup EDITOR = new FlagGroup(FLAGS, "editor", "Editor features");

  // To enable temporarily, use -Deditor.in.memory.r.classes=true
  public static final Flag<Boolean> IN_MEMORY_R_CLASSES = Flag.create(
    EDITOR,
    "in.memory.r.classes",
    "Generate R classes fully in memory",
    "If enabled, R classes are generated in memory", false);

  public static final Flag<Boolean> COLLAPSE_ANDROID_NAMESPACE = Flag.create(
    EDITOR,
    "collapse.android.namespace",
    "Collapse the android namespace in XML code completion",
    "If enabled, XML code completion doesn't include resources from the android namespace. Instead a fake completion item " +
    "is used to offer just the namespace prefix.", true);

  private static final FlagGroup ANALYZER = new FlagGroup(FLAGS, "analyzer", "Apk/Bundle Analyzer");
  public static final Flag<Boolean> ENABLE_APP_SIZE_OPTIMIZER = Flag.create(
    ANALYZER, "enable.app.size.optimizer", "Enable size optimization suggestions in apk analyzer",
    "If enabled, it will enable the apk analyzer tool to display suggestions for reducing application size", false);

  private StudioFlags() {
  }
}
