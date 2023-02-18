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
import com.android.tools.idea.flags.enums.PowerProfilerDisplayMode;
import com.android.tools.idea.flags.overrides.ServerFlagOverrides;
import com.android.tools.idea.util.StudioPathManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

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
    return new Flags(userOverrides, new PropertyOverrides(), new ServerFlagOverrides());
  }

  @TestOnly
  public static void validate() {
    FLAGS.validate();
  }

  //region New Project Wizard
  private static final FlagGroup NPW = new FlagGroup(FLAGS, "npw", "New Project Wizard");

  public static final Flag<Boolean> NPW_FIRST_RUN_WIZARD = Flag.create(
    NPW, "first.run.wizard", "Show new Welcome Wizard",
    "Show new version of the Welcome Wizard when Studio starts",
    false);

  public static final Flag<Boolean> NPW_FIRST_RUN_SHOW = Flag.create(
    NPW, "first.run.wizard.show", "Show Welcome Wizard always",
    "Show the Welcome Wizard when Studio starts",
    false);

  public static final Flag<Boolean> NPW_SHOW_FRAGMENT_GALLERY = Flag.create(
    NPW, "show.fragment.gallery", "Show fragment gallery",
    "Show fragment gallery which contains fragment based templates",
    true);

  public static final Flag<Boolean> NPW_SHOW_GRADLE_KTS_OPTION = Flag.create(
    NPW, "show.gradle.kts.option", "Show gradle kts option",
    "Shows an option on new Project/Module to allow the use of Kotlin script",
    false);

  public static final Flag<Boolean> NPW_NEW_NATIVE_MODULE = Flag.create(
    NPW, "new.native.module", "New Android Native Module",
    "Show template to create a new Android Native module in the new module wizard.",
    true);

  public static final Flag<Boolean> NPW_NEW_MACRO_BENCHMARK_MODULE = Flag.create(
    NPW, "new.macro.benchmark.module", "New Macro Benchmark Module",
    "Show template to create a new Macro Benchmark module in the new module wizard.",
    true);
  //endregion

  //region Memory Usage Reporting
  private static final FlagGroup MEMORY_USAGE_REPORTING = new FlagGroup(FLAGS, "memory.usage.reporting", "Memory Usage Reporting");

  public static final Flag<Boolean> USE_DISPOSER_TREE_REFERENCES = Flag.create(
    MEMORY_USAGE_REPORTING, "use.disposer.tree.references", "Memory report collection traversal will use disposer tree reference.",
    "If enabled, the memory report collecting traversal will consider disposer tree references as an object graph edges.",
    false);
  //endregion

  //region Profiler
  private static final FlagGroup PROFILER = new FlagGroup(FLAGS, "profiler", "Android Profiler");

  public static final Flag<Boolean> PROFILER_UNIFIED_PIPELINE = Flag.create(
    PROFILER, "unified.pipeline", "Enables new event pipeline to be used for core components.",
    "Toggles usage of gRPC apis to fetch data from perfd and the datastore.",
    true);

  public static final Flag<Boolean> PROFILER_ENERGY_PROFILER_ENABLED = Flag.create(
    PROFILER, "energy", "Enable Energy profiling",
    "Enable the new energy profiler. It monitors battery usage of the selected app.", true);

  public static final Flag<Boolean> PROFILER_MEMORY_CSV_EXPORT = Flag.create(
    PROFILER, "memory.csv", "Allow exporting entries in memory profiler",
    "Allow exporting entries in the views for heap dump and native/JVM recordings in CSV format.",
    false);

  public static final Flag<Boolean> PROFILER_PERFORMANCE_MONITORING = Flag.create(
    PROFILER, "performance.monitoring", "Enable Profiler Performance Monitoring Options",
    "Toggles if profiler performance metrics options are enabled.",
    false
  );

  public static final Flag<Boolean> PROFILER_JANK_DETECTION_UI = Flag.create(
    PROFILER, "jank.ui", "Enable jank detection UI",
    "Add a track in the display group showing frame janks.",
    true
  );

  public static final Flag<Boolean> PROFILER_CUSTOM_EVENT_VISUALIZATION = Flag.create(
    PROFILER, "custom.event.visualization", "Enable Profiler Custom Event Visualization",
    "When enabled, profiler will track and display events defined through developer APIs",
    false);

  public static final Flag<Boolean> PROFILEABLE_BUILDS = Flag.create(
    PROFILER, "profileable.builds", "Support building profileable apps",
    "Allow users to build apps as profileable with a supported Gradle plugin version (>7.3.0)",
    true);

  public static final Flag<PowerProfilerDisplayMode> PROFILER_SYSTEM_TRACE_POWER_PROFILER_DISPLAY_MODE = Flag.create(
    PROFILER, "power.tracks", "Set display mode of power rails and battery counters in system trace UI",
    "Allows users to customize whether the power rail and battery counter tracks are shown in the system trace UI, " +
    "and if shown, which type of graph displays the tracks. " +
    "When set to HIDE, hides power and battery data track groups in the system trace. " +
    "When set to MINMAX, shows the power rail tracks in a min-max view and keep the battery counter rails in a zero-based view. " +
    "When set to DELTA, shows the power rail tracks in a delta view and keeps the battery counter rails in a zero-based view.",
    PowerProfilerDisplayMode.HIDE);

  // TODO(b/211154220): Pending user's feedback, either completely remove the keyboard event functionality in
  // Event Timeline or find a proper way to support it for Android S and newer.
  public static final Flag<Boolean> PROFILER_KEYBOARD_EVENT = Flag.create(
    PROFILER, "keyboard.event", "Enable keyboard event",
    "Enable the keyboard event functionality in Event Timeline",
    false);

  public static final Flag<Boolean> PERFETTO_SDK_TRACING = Flag.create(
    PROFILER, "perfetto.sdk.tracing", "Automatically instrument perfetto sdk builds",
    "A cpu trace intercept command is added that will enable perfetto instrumentation for apps" +
    " that use the perfetto SDK",
    true);

  public static final Flag<Boolean> COMPOSE_TRACING_NAVIGATE_TO_SOURCE = Flag.create(
    PROFILER, "perfetto.sdk.tracing.compose.navigation", "Navigate-to-source action for Compose Tracing",
    "Enables navigate-to-source action in Profiler for Compose Tracing slices",
    true);
  //endregion

  //region ML
  private static final FlagGroup ML = new FlagGroup(FLAGS, "ml", "ML");
  public static final Flag<Boolean> ML_MODEL_BINDING = Flag.create(
    ML, "modelbinding", "Enable ML model binding",
    "When enabled, TFLite model file will be recognized and indexed. Please invalidates file caches after enabling " +
    "(File -> Invalidate Caches...) in order to reindex model files.",
    true);
  //endregion

  //region Asset Studio
  private static final FlagGroup ASSET = new FlagGroup(FLAGS, "asset", "Asset Studio");
  public static final Flag<Boolean> ASSET_COPY_MATERIAL_ICONS = Flag.create(
    ASSET, "copy.material.icons", "Allow copying icons to Sdk folder",
    "Allow the IconPickerDialog in Asset Studio to copy bundled material icons in to the Android/Sdk folder",
    true);
  public static final Flag<Boolean> ASSET_DOWNLOAD_MATERIAL_ICONS = Flag.create(
    ASSET, "download.material.icons", "Allow downloading icons to Sdk folder",
    "Allow the IconPickerDialog in Asset Studio to download any new material icons in to the Android/Sdk folder",
    true);
  //endregion

  //region Design Tools
  private static final FlagGroup DESIGN_TOOLS = new FlagGroup(FLAGS, "design.tools", "Design Tools");
  public static final Flag<Boolean> DESIGN_TOOLS_POWER_SAVE_MODE_SUPPORT = Flag.create(
    DESIGN_TOOLS, "power.save.support", "Enable previews support for PowerSave mode",
    "If enabled, the the Layout Editor and Compose Preview will respect the Power Save mode and avoid auto-refresh, reduce FPS, etc.",
    true);

  public static final Flag<Integer> PROJECT_SYSTEM_CLASS_LOADER_CACHE_LIMIT = Flag.create(
    DESIGN_TOOLS,
    "project.system.class.loader.cache.max.size",
    "Configure the max size of the cache used by ProjectSystemClassLoader",
    "Allow configuring the maximum size (in bytes) of the cache used by the ProjectSystemClassLoader to load classes from JAR files. " +
    "Files larger than the cache limit will cause a file miss and the file will need to be read again.",
    1_000_000
  );
  //endregion

  //region Layout Editor
  private static final FlagGroup NELE = new FlagGroup(FLAGS, "nele", "Layout Editor");
  public static final Flag<Boolean> NELE_ANIMATIONS_PREVIEW = Flag.create(
    NELE, "animated.preview", "Show preview animations toolbar",
    "Show an animations bar that allows playback of vector drawable animations.",
    true);
  public static final Flag<Boolean> NELE_ANIMATED_SELECTOR_PREVIEW = Flag.create(
    NELE, "animated.selector.preview", "Show preview animations toolbar for animated selector",
    "Show an animations bar that allows playback of transitions in animated selector.",
    true);
  public static final Flag<Boolean> NELE_ANIMATIONS_LIST_PREVIEW = Flag.create(
    NELE, "animated.list.preview", "Show preview animations toolbar for animation list",
    "Show an animations bar that allows playback of animation list files.",
    true);
  public static final Flag<Boolean> NELE_MOTION_AREA_GRAPH = Flag.create(
    NELE, "motion.area.graph", "Show area graph in Timeline panel",
    "Show area graph in Timeline panel for Motion Editor.",
    true);
  public static final Flag<Boolean> NELE_MOTION_SAVE_GIF = Flag.create(
    NELE, "motion.save.gif", "Enable save GIF feature",
    "Enable save a selected transition as a GIF file in Motion Editor.",
    true);
  public static final Flag<Boolean> NELE_MOTION_HORIZONTAL = Flag.create(
    NELE, "animated.motion.horizontal", "Display motion editor horizontally",
    "Controls the placement of the motion editor (horizontal versus vertical).",
    false);

  public static final Flag<Boolean> NELE_RENDER_DIAGNOSTICS = Flag.create(
    NELE, "diagnostics", "Enable rendering on-screen stats",
    "If enabled, the surface displays some debug information to diagnose performance",
    false);

  public static final Flag<Boolean> NELE_LOG_ANDROID_FRAMEWORK = Flag.create(
    NELE, "log.android.framework", "Log messages coming from Layoutlib Native.",
    "Log in the IDEA log the messages coming from Java and native code of Layoutlib Native.",
    false);

  public static final Flag<Boolean> NELE_DRAG_PLACEHOLDER = Flag.create(
    NELE, "drag.placeholder", "Dragging widgets with Placeholders",
    "New architecture for dragging widgets in Layout Editor",
    true);

  public static final Flag<Boolean> NELE_PROPERTY_PANEL_ACTIONBAR = Flag.create(
    NELE, "property.panel.actionbar", "Property Panel Actionbar",
    "Support Actionbar in property panel",
    false);

  public static final Flag<Boolean> NELE_NEW_DEVICE_MENU = Flag.create(
    NELE, "new.device.menu", "New Device Menu in Layout Editor",
    "Use the new designed device menu to support device classes",
    true);

  public static final Flag<Boolean> NELE_SOURCE_CODE_EDITOR = Flag.create(
    NELE, "show.source.code.editor", "New Source Code Editor",
    "Enable new source code editor with preview(s) coming as a substitute to Compose and Custom View editors.",
    true);

  public static final Flag<Boolean> NELE_SHOW_RECYCLER_VIEW_SETUP_WIZARD = Flag.create(
    NELE, "recyclerview.setup.wizard", "Show setup wizard for recycler view",
    "When you right click recycler view in layout editor, you can now see \"Generate Adapter\" " +
    "that takes you through setup wizard",
    false);

  public static final Flag<Boolean> NELE_LAYOUT_SCANNER_ADD_INCLUDE = Flag.create(
    NELE, "toggle.layout.editor.validator.a11y.include", "Toggle whether to show included layout or not.",
    "If the layout contains <include>, turning this flag on will run the scanner in the included layout.",
    false);

  public static final Flag<Boolean> NELE_LAYOUT_SCANNER_COMMON_ERROR_PANEL = Flag.create(
    NELE, "toggle.layout.editor.validator.a11y.common.panel", "Enable common error panel to display scanner results.",
    "If the xml layout contains atf results, it will be shown in the common error panel as well as issue panel.",
    false);

  public static final Flag<Boolean> NELE_USE_SHARED_ISSUE_PANEL_FOR_DESIGN_TOOLS = Flag.create(
    NELE, "use.shared.issue.panel.for.design.tools", "Enabled shared issue panels",
    "Use a shared issue panel to display the issue for all design tools",
    true);

  public static final Flag<Boolean> NELE_SHOW_VISUAL_LINT_ISSUE_IN_COMMON_PROBLEMS_PANEL = Flag.create(
    NELE, "show.issue.in.common.panel", "Enable showing issues in common problems panel.",
    "If the xml layout contains any visual lint issues, it will be shown in the common error panel as well as in laytout validation issue panel.",
    false);

  public static final Flag<Boolean> NELE_VISUAL_LINT_ALWAYS_RUN = Flag.create(
    NELE, "visual.lint.always.run", "Run visual lint in the background when the layout editor is opened",
    "Enable so that visual lint always runs in the background of the layout editor for select configurations. This is also known as the background linting",
    true);

  public static final Flag<Boolean> NELE_USE_CUSTOM_TRAFFIC_LIGHTS_FOR_RESOURCES = Flag.create(
    NELE, "use.custom.traffic.lights.for.resources", "Base traffic lights on the errors from the shared issue panel",
    "Use errors from the current file and qualifiers tab in the traffic light rendering for resource files.",
    true);

  public static final Flag<Boolean> NELE_ON_SWIPE_PANEL = Flag.create(
    NELE, "toggle.layout.editor.on.swipe.panel", "Toggle on swipe panel in motion editor.",
    "Enable the new on swipe panel in the motion editor",
    true);

  public static final Flag<Boolean> NELE_OVERLAY_PROVIDER = Flag.create(
    NELE, "toggle.overlay.provider.extension.point", "Toggle overlay provider extension point.",
    "Enable the overlay provider extension point",
    true);

  public static final Flag<Boolean> NELE_CLASS_BINARY_CACHE = Flag.create(
    NELE, "toggle.layout.editor.class.binary.cache", "Enable binary cache",
    "Enable binary cache of classes used in preview",
    true);

  public static final Flag<Boolean> NELE_ASSET_REPOSITORY_INCLUDE_AARS_THROUGH_PROJECT_SYSTEM = Flag.create(
    NELE, "asset.repository.include.aars.through.project.system", "Include AARs through project system",
    "Include resource directories from AARs found through project system.",
    false);

  public static final Flag<Boolean> NELE_VISUAL_LINT = Flag.create(
    NELE, "visual.lint", "Enable visual linting for layouts",
    "Enable all the various tools related to visual linting of layouts.",
    true);

  public static final Flag<Boolean> NELE_ATF_IN_VISUAL_LINT = Flag.create(
    NELE, "visual.lint.atf", "Enable ATF integration in visual linting for layouts",
    "Enable ATF integration in visual linting of layouts.",
    true);

  public static final Flag<Boolean> NELE_WARN_NEW_THREADS = Flag.create(
    NELE, "preview.warn.new.threads", "Enable new threads warning",
    "Display a warning if user code creates new threads in the preview",
    true);

  public static final Flag<Boolean> NELE_CLASS_PRELOADING_DIAGNOSTICS = Flag.create(
    NELE, "preview.class.preloading.diagnostics", "Enable class preloading overlay",
    "If enabled, the surface displays background class preloading progress",
    false);

  public static final Flag<Boolean> NELE_DYNAMIC_THEMING_ACTION = Flag.create(
    NELE, "dynamic.theming.action", "Enable previewing dynamic themes in Design Tools",
    "If enabled, Design Tools have an action to use various backgrounds to preview dynamic themes.",
    true);

  public static final Flag<Boolean> NELE_DP_SIZED_PREVIEW = Flag.create(
    NELE, "dp.sized.preview", "Use dp size instead of px size for previews",
    "If enabled, the size of previews will be proportional to screen dp size instead of screen px size.",
    true);

  public static final Flag<Boolean> NELE_NEW_COMPONENT_TREE = Flag.create(
    NELE, "use.component.tree.builder", "Use the Component Tree builder",
    "If enabled, use the Component Tree builder for the Nele component tree",
    true);
  //endregion

  //region Navigation Editor
  private static final FlagGroup NAV_EDITOR = new FlagGroup(FLAGS, "nav", "Navigation Editor");
  public static final Flag<Boolean> NAV_SAFE_ARGS_SUPPORT = Flag.create(
    NAV_EDITOR, "safe.args.enabled", "Enable support for Safe Args",
    "Generate in-memory Safe Args classes if the current module is using the feature.",
    true);
  //endregion

  //region Resource Manager
  private static final FlagGroup RES_MANAGER = new FlagGroup(FLAGS, "res.manager", "Resource Manager");
  public static final Flag<Boolean> EXTENDED_TYPE_FILTERS = Flag.create(
    RES_MANAGER, "extended.filters", "Enable extended filters for resources",
    "Adds more filter options for resources based on the selected ResourceType. Includes options to filter by resource XML tag or "
    + "File extension.",
    true);

  public static final Flag<Boolean> NAVIGATION_PREVIEW = Flag.create(
    RES_MANAGER, "nav.preview", "Enable previews for Navigation resources",
    "Adds a visual preview to the Navigation resources in the Resource Manager. The preview corresponds to the start destination " +
    "of the graph.",
    true);
  //endregion

  //region Resource Repository
  private static final FlagGroup RESOURCE_REPOSITORY = new FlagGroup(FLAGS, "resource.repository", "Resource Repository");
  public static final Flag<Integer> RESOURCE_REPOSITORY_TRACE_SIZE = Flag.create(
    RESOURCE_REPOSITORY, "trace.size", "Maximum Size of Resource Repository Update Trace",
    "Size of the in-memory cyclic buffer used for tracing of resource repository updates",
    10000);
  //endregion

  //region Run/Debug
  private static final FlagGroup RUNDEBUG = new FlagGroup(FLAGS, "rundebug", "Run/Debug");
  public static final Flag<Boolean> RUNDEBUG_LOGCAT_CONSOLE_OUTPUT_ENABLED = Flag.create(
    RUNDEBUG, "logcat.console.output.enabled", "Show logcat process output in Run/Debug console window",
    "When running or debugging an Android process, output the logcat output of the process in the console window.",
    false);

  public static final Flag<Boolean> RUNDEBUG_ANDROID_BUILD_BUNDLE_ENABLED = Flag.create(
    RUNDEBUG, "android.bundle.build.enabled", "Enable the Build Bundle action",
    "If enabled, the \"Build Bundle(s)\" menu item is enabled. " +
    "Changing the value of this flag requires restarting Android Studio.",
    true);

  public static final Flag<Boolean> DELTA_INSTALL = Flag.create(
    RUNDEBUG,
    "deltainstall",
    "Delta install",
    "Upon installing, if application is already on device, only send parts of the apks which have changed (the delta).",
    true);

  public static final Flag<Boolean> APPLY_CHANGES_OPTIMISTIC_SWAP = Flag.create(
    RUNDEBUG,
    "applychanges.optimisticswap",
    "Use the 'Apply Changes 2.0' deployment pipeline",
    "Supports Install-without-Install, Speculative Diff and Structural Redefinition",
    true);

  public static final Flag<Boolean> APPLY_CHANGES_OPTIMISTIC_RESOURCE_SWAP = Flag.create(
    RUNDEBUG,
    "applychanges.optimisticresourceswap",
    "Use the 'Apply Changes 2.0' deployment pipeline for full Apply Changes",
    "Requires applychanges.optimisticswap to be true.",
    true);

  public static final Flag<Boolean> NEW_EXECUTION_FLOW_ENABLED = Flag.create(
    RUNDEBUG, "android.new.execution.flow.enabled", "Enable new Execution flow",
    "If enabled, AS executes Run Configuration via new.AndroidRunProfileState",
    false);

  /**
   * The level of APK change that will be supported by the deployment pipeline's optimistic
   * "deploy-without-installing" path. Deploying changes that exceed the level of support
   * configured here will cause the deployment to install via the package manager.
   */
  public enum OptimisticInstallSupportLevel {
    /**
     * Always fall back to a package manager installation.
     */
    DISABLED,
    /**
     * Support deploying changes to dex files only.
     */
    DEX,
    /**
     * Support deploying changes to dex files and native libraries only.
     */
    DEX_AND_NATIVE,
    /**
     * Support deploying changes to dex files, native libraries, and resources.
     */
    DEX_AND_NATIVE_AND_RESOURCES,
  }

  public static final Flag<OptimisticInstallSupportLevel> OPTIMISTIC_INSTALL_SUPPORT_LEVEL = Flag.create(
    RUNDEBUG,
    "optimisticinstall.supportlevel",
    "The amount of support for using the 'Apply Changes 2.0' pipeline on Run.",
    "This can be \"DISABLED\" to always use a package manager installation; \"DEX\" to use the pipeline for dex-only changes;" +
    " \"DEX_AND_NATIVE\" to use the pipeline for dex and native library-only changes;" +
    " or \"DEX_AND_NATIVE_AND_RESOURCES\" to use the pipeline for changes to dex, native libraries, and/or resource/asset files." +
    " Deploying changes that exceed the level of support configured here will cause the deployment to install via the package manager.",
    OptimisticInstallSupportLevel.DEX);

  public static final Flag<Boolean> APPLY_CHANGES_STRUCTURAL_DEFINITION = Flag.create(
    RUNDEBUG,
    "applychanges.structuralredefinition",
    "Use ART's new structural redefinition extension for Apply Changes.",
    "Requires applychanges.optimisticswap to be true.",
    true);

  public static final Flag<Boolean> APPLY_CHANGES_VARIABLE_REINITIALIZATION = Flag.create(
    RUNDEBUG,
    "applychanges.variablereinitialization",
    "Use ART's new variable reinitializaiton extension for Apply Changes.",
    "Requires applychanges.structuralredefinition to be true.",
    true);

  public static final Flag<Boolean> APPLY_CHANGES_KEEP_CONNECTION_ALIVE = Flag.create(
    RUNDEBUG,
    "applychanges.connection.keepalive",
    "Keep connection to device alive.",
    "Eliminate the cost of opening a connection and spawning a process when using Apply Changes.",
    true);

  public static final Flag<Boolean> SELECT_DEVICE_SNAPSHOT_COMBO_BOX_SNAPSHOTS_ENABLED = Flag.create(
    RUNDEBUG,
    "select.device.snapshot.combo.box.snapshots.enabled",
    "Enable Select Device/Snapshot combo box snapshots",
    "So the new Instant Run can use the combo box",
    true);

  public static final Flag<Boolean> ADB_CONNECTION_STATUS_WIDGET_ENABLED = Flag.create(
    RUNDEBUG,
    "adb.connection.status.widget.enabled",
    "Enable and Show ADB Connection Widget",
    "Enables and shows the ADB connection status widget in the status bar",
    false);

  public static final Flag<Boolean> ADB_WIRELESS_PAIRING_ENABLED = Flag.create(
    RUNDEBUG,
    "adb.wireless.enabled",
    "Enable pairing devices through ADB wireless",
    "Allow pairing new physical device through QR Code pairing via ADB wireless",
    true);

  public static final Flag<Boolean> ADB_SERVER_MANAGEMENT_MODE_SETTINGS_VISIBLE = Flag.create(
    RUNDEBUG,
    "adb.server.management.mode.settings.visible",
    "Show ADB server management mode settings",
    "To allow toggling between automatic or user managed ADB server mode.",
    false);

  public static final Flag<Boolean> MERGED_DEVICE_FILE_EXPLORER_AND_DEVICE_MONITOR_TOOL_WINDOW_ENABLED = Flag.create(
    RUNDEBUG,
    "adb.device.monitor.enable",
    "Enable the \"Device Explorer\" tool window",
    "Enable the \"Device Explorer\" tool window which contains Device File Explorer and Device Monitor.\n" +
    "Changing the value of this flag requires restarting Android Studio.",
    true);

  public static final Flag<Boolean> ADBLIB_MIGRATION_DEVICE_EXPLORER = Flag.create(
    RUNDEBUG,
    "adblib.migration.device.explorer",
    "Use adblib in Device Explorer",
    "Use adblib instead of ddmlib for Device Explorer",
    true);

  public static final Flag<Boolean> ADBLIB_MIGRATION_WIFI_PAIRING = Flag.create(
    RUNDEBUG,
    "adblib.migration.wifi.pairing",
    "Use adblib in Pair Device over Wi-Fi",
    "Use adblib instead of ddmlib for Pair Device over Wi-Fi",
    true);

  /** b/262404780: forces the use of legacy shell to collect ps output. */
  public static final Flag<Boolean> ADBLIB_LEGACY_SHELL_FOR_PS_MONITOR = Flag.create(
    RUNDEBUG,
    "adblib.legacy.shell.for.psname.monitor",
    "Use adblib's legacy shell connection to monitor process names",
    "Use adblib's legacy shell instead of shell-v2 to monitor process names",
    true);

  public static final Flag<Boolean> ADBLIB_MIGRATION_DDMLIB_CLIENT_MANAGER = Flag.create(
    RUNDEBUG,
    "adblib.migration.ddmlib.clientmanager",
    "Use adblib to track device processes (Client)",
    "Use adblib instead of ddmlib to track processes (Client) on devices and handle debug sessions. " +
    "Note: Changing the value of this flag requires restarting Android Studio.",
    true);

  public static final Flag<Boolean> JDWP_TRACER = Flag.create(
    RUNDEBUG,
    "adb.jdwp.tracer.enabled",
    "Enable JDWP Traces",
    "Enables capture of JDWP traffic and generate a perfetto report",
    false);

  public static final Flag<Boolean> SUPPORT_FEATURE_ON_FEATURE_DEPS = Flag.create(
    RUNDEBUG,
    "feature.on.feature",
    "Enable feature-on-feature dependencies",
    "Enables Studio to understand feature-on-feature dependencies when launching dynamic apps.",
    false
  );

  public static final Flag<Boolean> COROUTINE_DEBUGGER_ENABLE = Flag.create(
    RUNDEBUG,
    "coroutine.debugger.enable",
    "Enable Coroutine Debugger",
    "Enables the Coroutine Debugger, that shows up as a panel in the debugger when debugging an app that uses coroutines",
    false
  );

  public static final Flag<Boolean> DDMLIB_ABB_EXEC_INSTALL_ENABLE = Flag.create(
    RUNDEBUG,
    "ddmlib.abb.exec.install.enable",
    "Allow DDMLib to use ABB_EXEC on install when device supports it.",
    "Allow DDMLib to use ABB_EXEC on install instead of the 'legacy' EXEC/CMD or EXEC/PM combos. This only occurs if device and adb support abb_exec",
    true
  );

  public static final Flag<Boolean> ASWB_RUN_WEAR_ENABLE = Flag.create(
    RUNDEBUG,
    "aswb.run.wear",
    "Enable launching wear surfaces in ASwB.",
    "Enable launching wear surfaces in ASwB, by enabling the UI option in the Blaze Android Binary Editor.",
    false
  );

  // TODO(b/247842651): Clean up this flag.
  public static final Flag<Boolean> DEBUG_DEVICE_SDK_SOURCES_ENABLE = Flag.create(
    RUNDEBUG,
    "debug.device.sdk.sources.enable",
    "Enable SDK source resolution using debug device API level.",
    "Enable SDK source resolution using debug device API level and related fallbacks.",
    true
  );

  public static final Flag<Boolean> DEBUG_ATTEMPT_SUSPENDED_START = Flag.create(
    RUNDEBUG,
    "debug.app.suspend.upon.start.enable",
    "Start activity suspended when debugging.",
    "Start activity suspended when debugging. This reduce the amount of time 'Waiting for Debugger' panel is shown on device",
    true
  );

  //endregion

  //region Logcat
  private static final FlagGroup LOGCAT = new FlagGroup(FLAGS, "logcat", "Logcat");

  // Deprecated: Old logcat tool window
  public static final Flag<Boolean> LOGCAT_EXPRESSION_FILTER_ENABLE = Flag.create(
    LOGCAT,
    "logcat.expression.filter.enable",
    "Enable expression filter in Logcat (deprecated)",
    "Enables the expression filter in Logcat",
    false
  );

  // Deprecated: Old logcat tool window
  public static final Flag<Boolean> LOGCAT_SUPPRESSED_TAGS_ENABLE = Flag.create(
    LOGCAT,
    "logcat.suppressed.tags.enable",
    "Enable Suppressed Tags Dialog in Logcat (deprecated)",
    "Enables a dialog that allows the user to maintain a global set of tags to be suppressed in Logcat",
    false
  );

  public static final Flag<Boolean> LOGCAT_NAMED_FILTERS_ENABLE = Flag.create(
    LOGCAT,
    "logcat.named.filters.enable",
    "Enable Logcat named filters feature",
    "Enables the named filters feature in the Logcat tool window",
    false
  );

  public static final Flag<Boolean> LOGCAT_CUSTOM_FORMAT_ACTION = Flag.create(
    LOGCAT,
    "logcat.custom.format.action",
    "Enable Logcat custom format action",
    "Enables the custom format action in the Logcat tool window action bar",
    false
  );

  public static final Flag<Boolean> LOGCAT_CLICK_TO_ADD_FILTER = Flag.create(
    LOGCAT,
    "logcat.click.to.add.filter",
    "Enable Logcat click to add/remove filter feature",
    "Enable Logcat click to add/remove filter feature",
    true
  );

  public static final Flag<Boolean> LOGCAT_IS_FILTER = Flag.create(
    LOGCAT,
    "logcat.is.filter",
    "Enable Logcat 'is:...' filter",
    "Enables a Logcat filter using the 'is' keyword for example 'is:stacktrace'is:crash' etc",
    true
  );

  public static final Flag<Integer> LOGCAT_MAX_MESSAGES_PER_BATCH = Flag.create(
    LOGCAT,
    "logcat.max.messages.per.batch",
    "Set the max number of messages that are appended to the UI component",
    "Set the max number of messages that are appended to the UI component",
    1000
  );
  //endregion

  //region Gradle Project System
  private static final FlagGroup GRADLE_IDE = new FlagGroup(FLAGS, "gradle.ide", "Gradle Project System");

  public static final Flag<Boolean> API_OPTIMIZATION_ENABLE = Flag.create(
    GRADLE_IDE, "build.injection.device.api.enabled",
    "Enable injection of device api level optimization from IDE",
    "Enable injection of device api level optimization from IDE",
    true
  );

  public static final Flag<Boolean> FIX_ANDROID_RUN_CONFIGURATIONS_ENABLED = Flag.create(
    GRADLE_IDE, "gradle.run.configuration.fix.enabled",
    "Check Android Run Configurations contains the \"Gradle-aware Make\" task and fix them",
    "When a project is loaded, automatically add a \"Gradle-aware Make\" task to each Run Configuration if the task is missing",
    true);

  public static final Flag<Boolean> USE_DEVELOPMENT_OFFLINE_REPOS = Flag.create(
    GRADLE_IDE, "development.offline.repos", "Enable development offline repositories",
    "Makes Gradle use development offline repositories such as /out/repo", StudioPathManager.isRunningFromSources());
  public static final Flag<Boolean> BUILD_ANALYZER_JETIFIER_ENABLED = Flag.create(
    GRADLE_IDE, "build.analyzer.jetifier.warning", "Enable Jetifier usage analyzis",
    "Enable Jetifier usage analyzis is Build Analyzer.", true);
  public static final Flag<Boolean> BUILD_ANALYZER_DOWNLOADS_ANALYSIS = Flag.create(
    GRADLE_IDE, "build.analyzer.downloads.analysis", "Enable Downloads analysis",
    "Enable Downloads analysis in Build Analyzer.", true);

  public static final Flag<Boolean> BUILD_ANALYZER_HISTORY = Flag.create(
    GRADLE_IDE, "build.analyzer.history", "Enable access to historic build analysis",
    "Enable access to historic build analysis in Build Analyzer.", false);
  public static final Flag<Boolean> BUILD_ANALYZER_CATEGORY_ANALYSIS = Flag.create(
    GRADLE_IDE, "build.analyzer.category.analysis", "Enable 'Group by Task Category' category task analysis",
    "Enable 'Group by Task Category' category task analysis in Build Analyzer.", true);

  public static final Flag<Boolean> DISABLE_FORCED_UPGRADES = Flag.create(
    GRADLE_IDE, "forced.agp.update", "Disable forced Android Gradle plugin upgrades",
    "This option is only respected when running Android Studio internally.", false);

  public static final Flag<Boolean> GRADLE_SYNC_PARALLEL_SYNC_ENABLED = Flag.create(
    GRADLE_IDE, "gradle.sync.parallel.sync.enabled", "Enables parallel sync",
    "This allows the IDE to fetch models in parallel (if supported by Gradle and enabled via org.gradle.parallel=true).", true);

  public static final Flag<Boolean> GRADLE_SYNC_PARALLEL_SYNC_PREFETCH_VARIANTS = Flag.create(
    GRADLE_IDE, "gradle.sync.parallel.sync.prefetch.variants", "Enables speculative syncing of current variants",
    "This allows the IDE to pre-fetch models for the currently selected variants in parallel before resolving the " +
    "new variant selection (which is less parallelizable process).", false);

  public static final Flag<Boolean> GRADLE_SYNC_OUTPUT_SYNC_STATS = Flag.create(
    GRADLE_IDE, "gradle.sync.output.sync.stats", "Enables printing sync stats to build output",
    "When enabled sync execution stats for models requested by Android Studio are printed to the build output tool window when" +
    "sync completes.", false);

  public static final Flag<Boolean> GRADLE_SYNC_ENABLE_CACHED_VARIANTS = Flag.create(
    GRADLE_IDE, "gradle.sync.enable.cached.variants", "Enables caching of build variants",
    "Enables caching of build variant data so that the IDE does not always run Gradle when switching between build variants. " +
    "While faster this mode may be incompatible with some plugins.", true);

  public static final Flag<Boolean> ALLOW_DIFFERENT_JDK_VERSION = Flag.create(
    GRADLE_IDE, "jdk.allow.different", "Allow different Gradle JDK", "Allow usage of a different JDK version when running Gradle.", true);

  public static final Flag<Boolean> GRADLE_SYNC_USE_V2_MODEL = Flag.create(
    GRADLE_IDE, "gradle.sync.use.v2", "Use V2 Builder models", "Enable fetching V2 builder models from AGP when syncing.", true);

  public static final Flag<Boolean> GRADLE_SYNC_RECREATE_JDK = Flag.create(
    GRADLE_IDE, "gradle.sync.recreate.jdk", "Recreate JDK on sync", "Recreate Gradle JDK when syncing if there are changed roots.", true);

  public static final Flag<Boolean> GRADLE_DSL_TOML_WRITE_SUPPORT = Flag.create(
    GRADLE_IDE, "gradle.dsl.toml.write", "Write TOML files", "Write changes to TOML Version Catalog files.", true);

  public static final Flag<Boolean> GRADLE_VERSION_CATALOG_EXTENDED_SUPPORT = Flag.create(
    GRADLE_IDE, "gradle.extended.version.catalog", "Gradle version catalog support", "Multiple TOML files, catalog variables in PSD", true);

  public static final Flag<Boolean> GRADLE_SAVE_LOG_TO_FILE = Flag.create(
    GRADLE_IDE, "save.log.to.file", "Save log to file", "Appends the build log to the given file", false);

  public static final Flag<String> AGP_VERSION_TO_USE = Flag.create(
    GRADLE_IDE, "agp.version.to.use", "Version of AGP to use",
    "The AGP version to use when making a new project, e.g. \"8.0.0-dev\". When set, a compatible Gradle version will also be " +
    "selected. If unset, the latest AGP version and the latest Gradle version will be used.",
    ""
  );

  public static final Flag<String> GRADLE_LOCAL_DISTRIBUTION_URL = Flag.create(
    GRADLE_IDE, "local.distribution.url", "Local override for distributionUrl",
    "When creating a project, Gradle updates the distributionUrl to point to a server accessible via the internet. When internet egress " +
    "is unavailable, this flag can be used to override the server destination to be a local URI.",
    ""
  );

  public static final Flag<String> GRADLE_HPROF_OUTPUT_DIRECTORY = Flag.create(
    GRADLE_IDE,
    "gradle.hprof.output.directory",
    "Gradle sync HPROF output directory",
    "If set, HPROF snapshots will be created at certain points during project sync and saved in the directory",
    ""
  );

  public static final Flag<String> GRADLE_HEAP_ANALYSIS_OUTPUT_DIRECTORY = Flag.create(
    GRADLE_IDE,
    "gradle.heap.analysis.output.directory",
    "Gradle heap analysis output directory",
    "If set, files with information about heap usage such as total live objects size and the strongly reachable objects size, will be dumped" +
    "to a file at certain points during project sync.",
    ""
  );

  //endregion

  //region Database Inspector
  private static final FlagGroup DATABASE_INSPECTOR = new FlagGroup(FLAGS, "database.inspector", "Database Inspector");
  public static final Flag<Boolean> DATABASE_INSPECTOR_OPEN_FILES_ENABLED = Flag.create(
    DATABASE_INSPECTOR,
    "open.files.enabled",
    "Enable support for opening SQLite files in Database Inspector",
    "If enabled, the Database Inspector tool will be able to open SQLite files." +
    "eg. SQLite files opened from the Device Explorer will open in the inspector.",
    false
  );
  //endregion

  //region Layout Inspector
  private static final FlagGroup LAYOUT_INSPECTOR = new FlagGroup(FLAGS, "layout.inspector", "Layout Inspector");
  public static final Flag<Boolean> DYNAMIC_LAYOUT_INSPECTOR_USE_DEVBUILD_SKIA_SERVER = Flag.create(
    LAYOUT_INSPECTOR, "dynamic.layout.inspector.devbuild.skia", "Use the locally-built skia rendering server",
    "If enabled and this is a locally-built studio instance, use the locally-built skia server instead of one from the SDK.", false);
  public static final Flag<Boolean> DYNAMIC_LAYOUT_INSPECTOR_ENABLE_RECOMPOSITION_COUNTS = Flag.create(
    LAYOUT_INSPECTOR, "dynamic.layout.inspector.enable.recomposition.counts", "Enable recomposition counts",
    "Enable gathering and display of recomposition counts in the layout inspector.", true);
  public static final Flag<Boolean> DYNAMIC_LAYOUT_INSPECTOR_ENABLE_RECOMPOSITION_HIGHLIGHTS = Flag.create(
    LAYOUT_INSPECTOR, "dynamic.layout.inspector.enable.recomposition.highlights", "Enable recomposition highlights",
    "Enable recomposition highlights on the image in the layout inspector.", true);

  public static final Flag<Boolean> DYNAMIC_LAYOUT_INSPECTOR_AUTO_CONNECT_TO_FOREGROUND_PROCESS_ENABLED = Flag.create(
    LAYOUT_INSPECTOR, "dynamic.layout.inspector.enable.auto.connect.foreground", "Enable automatically connecting to foreground process",
    "When this flag is enabled, LayoutInspector will automatically connect to whatever debuggable process is in the foreground on the phone.",
    true);

  public static final Flag<Boolean> DYNAMIC_LAYOUT_INSPECTOR_IGNORE_RECOMPOSITIONS_IN_FRAMEWORK = Flag.create(
    LAYOUT_INSPECTOR, "dynamic.layout.inspector.ignore.framework.recompositions", "Ignore recompositions in compose framework",
    "When this flag is enabled, LayoutInspector will disregard all recomposition counts for framework composables, " +
    "such that the user can concentrate on their own code.",
    true);

  public static final Flag<String> DYNAMIC_LAYOUT_INSPECTOR_COMPOSE_UI_INSPECTION_DEVELOPMENT_FOLDER = Flag.create(
    LAYOUT_INSPECTOR, "dev.jar.location", "Location of prebuilt compose app inspection jar for development",
    "If APP_INSPECTION_USE_DEV_JAR is enabled use this location to load the inspector jar in development.",
    "prebuilts/tools/common/app-inspection/androidx/compose/ui/"
  );

  public static final Flag<String> DYNAMIC_LAYOUT_INSPECTOR_COMPOSE_UI_INSPECTION_RELEASE_FOLDER = Flag.create(
    LAYOUT_INSPECTOR, "rel.jar.location", "Location of prebuilt compose app inspection jar for releases",
    "If APP_INSPECTION_USE_DEV_JAR is enabled use this location to load the inspector jar in releases.",
    ""
  );
  //endregion

  //region Embedded Emulator
  private static final FlagGroup EMBEDDED_EMULATOR = new FlagGroup(FLAGS, "embedded.emulator", "Embedded Emulator");
  public static final Flag<Boolean> EMBEDDED_EMULATOR_SCREENSHOT_STATISTICS = Flag.create(
    EMBEDDED_EMULATOR, "screenshot.statistics", "Enable Collection of Screenshot Statistics",
    "Captures statistics of received Emulator screenshots",
    false);
  public static final Flag<Integer> EMBEDDED_EMULATOR_STATISTICS_INTERVAL_SECONDS = Flag.create(
    EMBEDDED_EMULATOR, "screenshot.statistics.interval", "Aggregation Interval for Screenshot Statistics",
    "Aggregation interval in seconds for statistics of received Emulator screenshots",
    120);
  public static final Flag<Boolean> EMBEDDED_EMULATOR_TRACE_GRPC_CALLS = Flag.create(
    EMBEDDED_EMULATOR, "trace.grpc.calls", "Enable Emulator gRPC Tracing",
    "Enables tracing of most Emulator gRPC calls",
    false);
  public static final Flag<Boolean> EMBEDDED_EMULATOR_TRACE_HIGH_VOLUME_GRPC_CALLS = Flag.create(
    EMBEDDED_EMULATOR, "trace.high.volume.grpc.calls", "Enable High Volume Emulator gRPC Tracing",
    "Enables tracing of high volume Emulator gRPC calls",
    false);
  public static final Flag<Boolean> EMBEDDED_EMULATOR_TRACE_SCREENSHOTS = Flag.create(
    EMBEDDED_EMULATOR, "trace.screenshots", "Enable Emulator Screenshot Tracing",
    "Enables tracing of received Emulator screenshots",
    false);
  public static final Flag<Boolean> EMBEDDED_EMULATOR_TRACE_NOTIFICATIONS = Flag.create(
    EMBEDDED_EMULATOR, "trace.notifications", "Enable Emulator Notification Tracing",
    "Enables tracing of received Emulator notifications",
    false);
  public static final Flag<Boolean> EMBEDDED_EMULATOR_TRACE_DISCOVERY = Flag.create(
    EMBEDDED_EMULATOR, "trace.discovery", "Enable Tracing of Emulator Discovery",
    "Enables tracing of Emulator discovery",
    false);
  //endregion

  //region Device Mirroring
  private static final FlagGroup DEVICE_MIRRORING = new FlagGroup(FLAGS, "device.mirroring", "Device Mirroring");
  public static final Flag<Boolean> DEVICE_MIRRORING_ENABLED_BY_DEFAULT = Flag.create(
    DEVICE_MIRRORING, "enabled", "Enable Mirroring of Physical Devices by Default",
    "Mirrors displays of connected physical devices",
    false);
  public static final Flag<Boolean> DEVICE_MIRRORING_STANDALONE_EMULATORS = Flag.create(
    DEVICE_MIRRORING, "allow.standalone.emulators", "Allow Mirroring of Standalone Emulators",
    "Treats standalone emulators the same as physical devices for the purpose of display mirroring",
    false);
  public static final Flag<String> DEVICE_MIRRORING_AGENT_LOG_LEVEL = Flag.create(
    DEVICE_MIRRORING, "agent.log.level", "On Device Logging Level for Mirroring",
    "The log level used by the screen sharing agent, one of \"verbose\", \"debug\", \"info\", \"warn\" or \"error\"",
    "info");
  public static final Flag<Integer> DEVICE_MIRRORING_MAX_BIT_RATE = Flag.create(
    DEVICE_MIRRORING, "max.bit.rate", "Maximum Bit Rate for Mirroring of Physical Devices",
    "The maximum bit rate of video stream, zero means no limit",
    0);
  public static final Flag<String> DEVICE_MIRRORING_VIDEO_CODEC = Flag.create(
    DEVICE_MIRRORING, "video.codec", "Video Codec Used for Mirroring of Physical Devices",
    "The name of a video codec, e.g. \"vp8\" or \"vp9\"",
    "vp8");
  //endregion

  //region Refactorings
  private static final FlagGroup REFACTORINGS = new FlagGroup(FLAGS, "refactor", "Refactor menu");

  public static final Flag<Boolean> MIGRATE_TO_RESOURCE_NAMESPACES_REFACTORING_ENABLED = Flag.create(
    REFACTORINGS, "migrateto.resourcenamespaces.enabled", "Enable the Migrate to Resource Namespaces refactoring",
    "If enabled, show the action in the refactoring menu", false);

  public static final Flag<Boolean> MIGRATE_TO_NON_TRANSITIVE_R_CLASSES_REFACTORING_ENABLED = Flag.create(
    REFACTORINGS, "migrateto.nontransitiverclasses.enabled", "Enable the Migrate to non-transitive R classes refactoring",
    "If enabled, show the action in the refactoring menu", true);

  public static final Flag<Boolean> INFER_ANNOTATIONS_REFACTORING_ENABLED = Flag.create(
    REFACTORINGS, "infer.annotations.enabled", "Enable the Infer Annotations refactoring",
    "If enabled, show the action in the refactoring menu", false);
  //endregion

  //region NDK
  private static final FlagGroup NDK = new FlagGroup(FLAGS, "ndk", "Native code features");
  public static final Flag<Boolean> CMAKE_ENABLE_FEATURES_FROM_CLION = Flag.create(
    NDK, "cmakeclionfeatures", "Enable CMake language support from CLion",
    "If enabled, language support features (e.g. syntax highlighting) currently present in CLion will be turned on.", true);

  public static final Flag<Boolean> APK_DEBUG_BUILD_ID_CHECK = Flag.create(
    NDK, "apkdebugbuildidcheck", "Enable build ID check in APK debugging",
    "If enabled, the build ID of user-provided symbol files are compared against the binaries inside the APK.", true);

  public static final Flag<Boolean> APK_DEBUG_RELOAD = Flag.create(
    NDK, "apkdebugreload", "Enable APK reloading feature",
    "If enabled, the user will be provided with an option to reload the APK inside an APK debugging project", true);

  private static final FlagGroup NDK_SIDE_BY_SIDE = new FlagGroup(FLAGS, "ndk.sxs", "NDK Side by Side");
  public static final Flag<Boolean> NDK_SIDE_BY_SIDE_ENABLED = Flag.create(
    NDK_SIDE_BY_SIDE, "ndk.sxs.enabled", "Enable side by side NDK support",
    "If enabled, C/C++ projects will have NDK side by side support",
    true);

  public static final Flag<Boolean> ENABLE_SHOW_FILES_UNKNOWN_TO_CMAKE = Flag.create(
    NDK, "ndk.projectview.showfilessunknowntocmake", "Enable option to show files unknown to CMake",
    "If enabled, for projects using CMake, Android project view menu would show an option to `Show Files Unknown To CMake`.",
    true
  );

  // b/202709703: Disable jb_formatters (which is used to pull Natvis) temporarily, because
  // the latest changes in cidr-debugger cause the jb_formatters to conflict with the
  // built-in lldb formatters.
  public static final Flag<Boolean> ENABLE_LLDB_NATVIS = Flag.create(
    NDK, "lldb.natvis", "Use NatVis visualizers in native debugger",
    "If enabled, native debugger formats variables using NatVis files found in the project.",
    false
  );
  //endregion

  //region Editor
  private static final FlagGroup EDITOR = new FlagGroup(FLAGS, "editor", "Editor features");

  public static final Flag<Boolean> COLLAPSE_ANDROID_NAMESPACE = Flag.create(
    EDITOR,
    "collapse.android.namespace",
    "Collapse the android namespace in XML code completion",
    "If enabled, XML code completion doesn't include resources from the android namespace. Instead a fake completion item " +
    "is used to offer just the namespace prefix.", true);

  public static final Flag<Boolean> AGSL_LANGUAGE_SUPPORT = Flag.create(
    EDITOR, "agsl.support.enabled",
    "Enable editor support for AGSL (Android Graphics Shading Language)",
    "If enabled, it offers basic editor support (syntax highlighting and basic validation) for AGSL",
    true
  );

  public static final Flag<Boolean> ADVANCED_JNI_ASSISTANCE = Flag.create(
    EDITOR, "advanced.jni.assistance",
    "Enable advanced JNI assistance",
    "If enabled, additional inspection, completion, and refactoring supports are provided related to JNI. If disabled, some " +
    "inspections related to JNI may stop working.",
    true
  );

  public static final Flag<Boolean> SAMPLES_SUPPORT_ENABLED = Flag.create(
    EDITOR, "samples.support.enabled",
    "Enable supports of samples (tag inside KDoc) that are used in quick documentation",
    "Enable supports of samples (tag inside KDoc) that are used in quick documentation",
    false
  );

  public static final Flag<Boolean> DAGGER_SUPPORT_ENABLED = Flag.create(
    EDITOR, "dagger.support.enabled",
    "Enable editor support for Dagger",
    "If enabled adds Dagger specific find usages, gutter icons and new parsing for Dagger errors",
    true
  );

  public static final Flag<Boolean> DAGGER_BUILT_IN_SEARCH_ENABLED = Flag.create(
    EDITOR, "dagger.built.in.search.enabled",
    "Enable using the built in annotation search for dagger",
    "If enabled, uses the built in annotation search for dagger usages rather than the custom DaggerAnnotatedElementsSearch",
    false
  );

  public static final Flag<Boolean> DAGGER_USING_INDEX_ENABLED = Flag.create(
    EDITOR, "dagger.index.enabled",
    "Enable the dagger index",
    "If enabled, the dagger index is used for gutter icon and find usages support.",
    false
  );

  public static final Flag<Boolean> TRANSLATIONS_EDITOR_SYNCHRONIZATION = Flag.create(
    EDITOR, "translations.editor.synchronization",
    "Synchronize translations editor with resource file updates",
    "If enabled, causes the translations editor to reload data when resource files are edited",
    false
  );

  public static final Flag<Boolean> JFR_MANIFEST_MERGE_ENABLED = Flag.create(
    EDITOR, "jfr.manifest.merge.enabled",
    "Enable JFR for manifest merge",
    "If enabled, allows JFR reports to be generated when manifest merge exceeds the defined threshold",
    true
  );

  public static final Flag<Boolean> JFR_TYPING_LATENCY_ENABLED = Flag.create(
    EDITOR, "jfr.typing.latency.enabled",
    "Enable JFR for typing latency",
    "If enabled, allows JFR reports to be generated when typing latency exceeds the defined threshold",
    true
  );

  //endregion

  //region Unified App Bundle
  private static final FlagGroup UAB = new FlagGroup(FLAGS, "uab", "Unified App Bundle");

  public static final Flag<Boolean> UAB_ENABLE_NEW_INSTANT_APP_RUN_CONFIGURATIONS = Flag.create(
    UAB, "enable.ia.run.configs", "Enable new instant app run configuration options",
    "If enabled, shows the new instant app deploy checkbox in the run configuration dialog and allows new instant app deploy workflow.",
    true
  );
  //endregion

  //region Testing
  private static final FlagGroup TESTING = new FlagGroup(FLAGS, "testing", "Testing support");

  public static final Flag<Boolean> PRINT_INSTRUMENTATION_STATUS = Flag.create(
    TESTING, "print.instrumentation.status", "Print instrumentation status information when testing",
    "If enabled, instrumentation output keys (from calling Instrumentation#sendStatus) that begin with 'android.studio.display.' "
    + "will have their values printed after a test has finished running.",
    true
  );

  public static final Flag<Boolean> UTP_TEST_RESULT_SUPPORT = Flag.create(
    TESTING, "utp.instrumentation.tests", "Allow importing UTP test results.",
    "If enabled, you can import UTP test results and display them in test result panel.",
    true
  );

  public static final Flag<Boolean> UTP_INSTRUMENTATION_TESTING = Flag.create(
    TESTING, "utp.instrumentation.testing", "Run instrumentation tests via UTP",
    "If enabled, a checkbox to opt-in to running instrumentation tests via UTP feature is displayed in the settings.",
    true
  );

  public static final Flag<Integer> ANDROID_PLATFORM_TO_AUTOCREATE = Flag.create(
    TESTING,
    "android.platform.to.autocreate",
    "Android platform to auto-create",
    "Automatically sets up the JDK table at initialization time and points to the specified API level of the Android SDK " +
    "(rather than always pointing to the latest). This is largely intended for use by tests where Android Studio can't be easily " +
    "configured ahead of time. If this value is 0, then this flag is considered to be off and no platform will be automatically created. " +
    "If this value is -1, then the platform will be automatically created with the latest version.",
    0
  );
  //endregion

  //region Memory
  private static final FlagGroup MEMORY_SETTINGS = new FlagGroup(FLAGS, "memory.settings", "Memory Settings");
  public static final Flag<Boolean> LOW_IDE_XMX_CAP = Flag.create(
    MEMORY_SETTINGS, "low.ide.xmx.cap", "Set low IDE Xmx cap in memory settings",
    "If set, IDE Xmx is capped at 4GB in the configuration dialog. Otherwise, the cap is 8GB",
    true);
  //endregion

  //region System Health
  private static final FlagGroup SYSTEM_HEALTH = new FlagGroup(FLAGS, "system.health", "System Health");
  public static final Flag<Boolean> WINDOWS_UCRT_CHECK_ENABLED = Flag.create(
    SYSTEM_HEALTH, "windows.ucrt.check.enabled", "Enable Universal C Runtime system health check",
    "If enabled, a notification will be shown if the Universal C Runtime in Windows is not installed",
    false);

  public static final Flag<Boolean> ANTIVIRUS_NOTIFICATION_ENABLED = Flag.create(
    SYSTEM_HEALTH, "antivirus.notification.enabled", "Enable antivirus system health check",
    "If enabled, a notification will be shown if antivirus realtime scanning is enabled and directories relevant to build performance aren't excluded",
    true);

  public static final Flag<Boolean> ANTIVIRUS_METRICS_ENABLED = Flag.create(
    SYSTEM_HEALTH, "antivirus.metrics.enabled", "Enable antivirus metrics collection",
    "If enabled, metrics about the status of antivirus realtime scanning and excluded directories will be collected",
    true);

  public static final Flag<Boolean> ANTIVIRUS_CHECK_USE_REGISTRY = Flag.create(
    SYSTEM_HEALTH, "antivirus.check.registry", "Use registry instead of PowerShell for checking antivirus status",
    "If enabled, the antivirus status checker will use the Windows registry instead of PowerShell commands",
    true);

  //endregion

  //region Compose
  private static final FlagGroup COMPOSE = new FlagGroup(FLAGS, "compose", "Compose");

  public static final Flag<Boolean> COMPOSE_PREVIEW_DOUBLE_RENDER = Flag.create(
    COMPOSE, "preview.double.render", "Enable the Compose double render mode",
    "If enabled, preview components will be rendered twice so components depending on a recompose (like tableDecoration) " +
    "render correctly.",
    false);

  public static final Flag<Boolean> COMPOSE_PREVIEW_SCROLL_ON_CARET_MOVE = Flag.create(
    COMPOSE, "preview.scroll.on.caret.move", "Enable the Compose Preview scrolling when the caret moves",
    "If enabled, when moving the caret in the text editor, the Preview will show the preview currently under the cursor.",
    false);

  public static final Flag<Boolean> COMPOSE_CONSTRAINTLAYOUT_COMPLETION = Flag.create(
    COMPOSE, "editor.completion.constraintlayout.json",
    "Completion for ConstraintLayout JSON syntax",
    "If enabled, code completion will be abailable for the JSON syntax of Compose ConstraintLayout.",
    true
  );

  public static final Flag<Boolean> COMPOSE_AUTO_DOCUMENTATION = Flag.create(
    COMPOSE, "editor.auto.documentation",
    "Show quick documentation automatically for Compose",
    "If enabled, during code completion popup with documentation shows automatically",
    true
  );

  public static final Flag<Boolean> COMPOSE_RENDER_SAMPLE_IN_DOCUMENTATION = Flag.create(
    COMPOSE, "editor.render.sample",
    "Render samples of compose elements inside documentation",
    "If enabled, adds rendered image of sample for compose element if such exists",
    false
  );

  public static final Flag<Boolean> COMPOSE_RENDER_SAMPLE_IN_DOCUMENTATION_SLOW = Flag.create(
    COMPOSE, "editor.render.sample.slow",
    "Slow down rendering of samples of compose elements inside documentation",
    "If enabled, slow down rendering of samples of compose elements inside documentation, this flag is used for demonstration of non-blocking behavior",
    false
  );

  public static final Flag<Boolean> COMPOSE_FUNCTION_EXTRACTION = Flag.create(
    COMPOSE, "editor.function.extraction",
    "Enables extracting @Composable function from other composables",
    "If enabled, function extracted from @Composable function will annotated @Composable",
    true
  );

  public static final Flag<Boolean> COMPOSE_DEPLOY_LIVE_EDIT = Flag.create(
    COMPOSE, "deploy.live.edit.deploy",
    "Enable live edit deploy",
    "If enabled, Live Edit will be visible and available",
    true
  );

  public static final Flag<Boolean> COMPOSE_DEPLOY_LIVE_EDIT_ADVANCED_SETTINGS_MENU = Flag.create(
    COMPOSE, "deploy.live.edit.deploy.advanced.settings",
    "Enable live edit deploy settings menu",
    "If enabled, advanced Live Edit settings menu will be visible",
    false
  );

  public static final Flag<Integer> COMPOSE_LIVE_LITERALS_UPDATE_RATE = Flag.create(
    COMPOSE, "deploy.live.literals.updaterate",
    "Update rate of live literals edits",
    "The rate of which live literals are updated in milliseconds",
    50
  );

  public static final Flag<Boolean> COMPOSE_DEBUG_BOUNDS = Flag.create(
    COMPOSE, "preview.debug.bounds",
    "Enable the debug bounds switch controls",
    "If enabled, the user can enable/disable the painting of debug bounds",
    false
  );

  public static final Flag<Boolean> COMPOSE_PREVIEW_ELEMENT_PICKER = Flag.create(
    COMPOSE, "preview.element.picker.enable",
    "Enable @Preview picker",
    "If enabled, the picker for @Preview elements will be available",
    true
  );

  public static final Flag<Boolean> COMPOSE_PREVIEW_DEVICESPEC_INJECTOR = Flag.create(
    COMPOSE, "preview.element.injector.enable",
    "Enable injecting DeviceSpec Language",
    "If enabled, the DeviceSpec Language will be injected in @Preview.device string values",
    true
  );

  public static final Flag<Boolean> COMPOSE_SPRING_PICKER = Flag.create(
    COMPOSE, "preview.spring.picker",
    "Enable the SpringSpec picker",
    "If enabled, a picker will be available in SpringSpec calls on the Editor gutter",
    false
  );

  public static final Flag<Boolean> COMPOSE_COLORBLIND_MODE = Flag.create(
    COMPOSE, "preview.colorblind",
    "Enable the colorblind mode for Compose previews",
    "If enabled, the user can change the mode of Compose previews, between different types of colorblind modes",
    true
  );

  public static final Flag<Boolean> COMPOSE_VIEW_INSPECTOR = Flag.create(
    COMPOSE, "view.inspector",
    "Show the switch of view inspection tool in Compose",
    "If enabled, the user can toggle the mouse inspection tool in the dropdown menu of Compose Preview. The tools is disabled by default",
    true
  );

  public static final Flag<Boolean> COMPOSE_CONSTRAINT_VISUALIZATION = Flag.create(
    COMPOSE, "constraint.visualization",
    "Enable ConstraintLayout visualization in Compose previews",
    "If enabled, constraints from a ConstraintLayout composable will be shown in the preview",
    true
  );

  public static final Flag<Integer> COMPOSE_INTERACTIVE_FPS_LIMIT = Flag.create(
    COMPOSE, "preview.interactive.fps.limit",
    "Interactive Preview FPS limit",
    "Controls the maximum number of frames per second in Compose Interactive Preview",
    30
  );

  public static final Flag<Boolean> COMPOSE_STATE_OBJECT_CUSTOM_RENDERER = Flag.create(
    COMPOSE, "custom.renderer.for.compose.state.objects",
    "Enable custom renderers for compose state objects",
    "If enabled, a given compose 'StateObject' type object will be rendered by the corresponding custom renderer",
    true
  );

  public static final Flag<Boolean> COMPOSE_ANIMATION_PREVIEW_COORDINATION_DRAG = Flag.create(
    COMPOSE, "preview.animation.coordination.drag",
    "Enable animation dragging in timeline for Animation Inspector",
    "If enabled, animation dragging will be available in Animation Inspector timeline.",
    false
  );

  public static final Flag<Boolean> COMPOSE_ANIMATION_PREVIEW_ANIMATE_X_AS_STATE = Flag.create(
    COMPOSE, "preview.animation.animate.as.state", "Enable animate*AsState support",
    "If enabled, the animate*AsState Compose API support will be available in Animation Preview.",
    true);

  public static final Flag<Boolean> COMPOSE_ANIMATION_PREVIEW_ANIMATED_CONTENT = Flag.create(
    COMPOSE, "preview.animation.animated.content", "Enable animatedContent support",
    "If enabled, the animatedContent Compose API support will be available in Animation Preview.",
    true);

  public static final Flag<Boolean> COMPOSE_ANIMATION_PREVIEW_INFINITE_TRANSITION = Flag.create(
    COMPOSE, "preview.animation.infinite.transition", "Enable rememberInfiniteTransition support",
    "If enabled, the rememberInfiniteTransition Compose API support will be available in Animation Preview.",
    true);

  public static final Flag<Boolean> COMPOSE_FAST_PREVIEW = Flag.create(
    COMPOSE, "preview.fast.reload.enabled", "Enable the Compose fast-reload preview",
    "If enabled, the preview enabled the fast-reload feature.",
    true);

  public static final Flag<Boolean> COMPOSE_FAST_PREVIEW_DAEMON_DEBUG = Flag.create(
    COMPOSE, "preview.fast.reload.debug.daemon", "Starts the Live Edit daemon in debug mode",
    "If enabled, the compiler daemon will wait for a debugger to be attached.",
    false);

  public static final Flag<Boolean> COMPOSE_MULTIPREVIEW = Flag.create(
    COMPOSE, "preview.multipreview.enabled", "Enable Compose Multipreview",
    "If enabled, annotation classes annotated with Preview, and its usages, will be considered when finding Previews in a file",
    true);

  public static final Flag<Boolean> COMPOSE_NEW_PREVIEW_LAYOUT = Flag.create(
    COMPOSE, "new.preview.layout", "Enable the new layout options of Compose Preview",
    "If enabled, the options of new layout designs of compose preview will be shown in Compose Preview",
    true);

  public static final Flag<Boolean> COMPOSE_PROJECT_USES_COMPOSE_OVERRIDE = Flag.create(
    COMPOSE, "project.uses.compose.override", "Forces the Compose project detection",
    "If enabled, the project will be treated as a Compose project, showing Previews if available and enhancing the Compose editing",
    false);

  public static final Flag<Boolean> COMPOSE_FAST_PREVIEW_AUTO_DISABLE = Flag.create(
    COMPOSE, "fast.preview.auto.disable", "If enabled, Fast Preview can auto-disable",
    "If enabled, if fast preview finds a compiler problem, it will be auto disable until the user re-enables it",
    false);

  public static final Flag<Boolean> COMPOSE_ALLOCATION_LIMITER = Flag.create(
    COMPOSE, "allocation.limiter", "If enabled, limits allocations per render",
    "If enabled, limits the number of allocations that user code can do in a single render action",
    false);
  //endregion

  // region Wear surfaces
  private static final FlagGroup WEAR_SURFACES = new FlagGroup(FLAGS, "wear.surfaces", "Wear Surfaces");

  public static final Flag<Boolean> GLANCE_APP_WIDGET_PREVIEW = Flag.create(
    WEAR_SURFACES, "glance.preview.appwidget.enabled", "Enable Glance AppWidget preview",
    "If enabled, a preview for annotated glance app widget composable functions is displayed",
    true);

  public static final Flag<Boolean> GLANCE_TILE_PREVIEW = Flag.create(
    WEAR_SURFACES, "glance.preview.tile.enabled", "Enable Glance Tile preview",
    "If enabled, a preview for annotated glance tile composable functions is displayed",
    false);
  // endregion

  // region App Inspection
  private static final FlagGroup APP_INSPECTION = new FlagGroup(FLAGS, "appinspection", "App Inspection");
  public static final Flag<Boolean> ENABLE_APP_INSPECTION_TOOL_WINDOW = Flag.create(
    APP_INSPECTION, "enable.tool.window", "Enable App Inspection Tool Window",
    "Enables the top-level App Inspection tool window, which will contain tabs to various feature inspectors",
    true
  );

  public static final Flag<Boolean> APP_INSPECTION_USE_DEV_JAR = Flag.create(
    APP_INSPECTION, "use.dev.jar", "Use a precompiled, prebuilt inspector jar",
    "If enabled, grab inspector jars from prebuilt locations, skipping over version checking and dynamic resolving of " +
    "inspector artifacts from maven. This is useful for devs who want to load locally built inspectors.",
    false
  );

  public static final Flag<Boolean> APP_INSPECTION_USE_SNAPSHOT_JAR = Flag.create(
    APP_INSPECTION, "use.snapshot.jar", "Always extract latest inspector jar from library",
    "If enabled, override normal inspector resolution logic, instead searching the IDE cache directly. This allows finding " +
    "inspectors bundled in local, snapshot builds of Android libraries, as opposed to those released through the normal process on maven.",
    false
  );

  public static final Flag<Boolean> COMPOSE_USE_LOADER_WITH_AFFINITY = Flag.create(
    COMPOSE, "preview.loader.affinity", "Enable the class loading affinity.",
    "If enabled, the class loading will cache which class loaders are more likely to have the class.",
    true);
  // endregion

  // region Network Inspector
  private static final FlagGroup NETWORK_INSPECTOR = new FlagGroup(FLAGS, "network.inspector", "Network Inspector");
  public static final Flag<Boolean> ENABLE_NETWORK_MANAGER_INSPECTOR_TAB = Flag.create(
    NETWORK_INSPECTOR, "enable.network.inspector.tab", "Enable Network Inspector Tab",
    "Enables a Network Inspector Tab in the App Inspection tool window",
    true
  );
  public static final Flag<Boolean> ENABLE_NETWORK_INTERCEPTION = Flag.create(
    NETWORK_INSPECTOR, "enable.network.interception", "Enable Network Interception",
    "Enables interceptions on network requests and responses",
    true
  );
  // endregion

  // region BackgroundTask Inspector
  private static final FlagGroup BACKGROUND_TASK_INSPECTOR =
    new FlagGroup(FLAGS, "backgroundtask.inspector", "BackgroundTask Inspector");
  public static final Flag<Boolean> ENABLE_BACKGROUND_TASK_INSPECTOR_TAB = Flag.create(
    BACKGROUND_TASK_INSPECTOR, "enable.backgroundtask.inspector.tab", "Enable BackgroundTask Inspector Tab",
    "Enables a BackgroundTask Inspector Tab in the App Inspection tool window",
    true
  );
  // endregion

  //region Device Manager
  private static final FlagGroup DEVICE_MANAGER = new FlagGroup(FLAGS, "device.manager", "Device Manager");

  public static final Flag<Boolean> WEAR_OS_VIRTUAL_DEVICE_PAIRING_ASSISTANT_ENABLED = Flag.create(
    DEVICE_MANAGER,
    "wear.os.virtual.device.pairing.assistant.enabled",
    "Enable the Wear OS virtual device pairing assistant",
    "Enable the Wear OS virtual device pairing assistant",
    true);

  public static final Flag<Boolean> PAIRED_DEVICES_TAB_ENABLED = Flag.create(
    DEVICE_MANAGER,
    "paired.devices.tab.enabled",
    "Enable the Paired devices tab",
    "Enable the Paired devices tab in the details panel",
    true);

  public static final Flag<Boolean> VIRTUAL_DEVICE_WATCHER_ENABLED = Flag.create(
    DEVICE_MANAGER,
    "virtual.device.watcher.enabled",
    "Enable VirtualDeviceWatcher",
    "Enable VirtualDeviceWatcher to update the Virtual table based on disk changes",
    true);
  // endregion

  //region DDMLIB
  private static final FlagGroup DDMLIB = new FlagGroup(FLAGS, "ddmlib", "DDMLIB");
  public static final Flag<Boolean> ENABLE_JDWP_PROXY_SERVICE = Flag.create(
    DDMLIB, "enable.jdwp.proxy.service", "Enable jdwp proxy service",
    "Creates a proxy service within DDMLIB to allow shared device client connections.",
    false
  );
  public static final Flag<Boolean> ENABLE_DDMLIB_COMMAND_SERVICE = Flag.create(
    DDMLIB, "enable.ddmlib.command.service", "Enable ddmlib command service",
    "Creates a service within DDMLIB to allow external processes to issue commands to ddmlib.",
    false
  );
  // endregion DDMLIB

  // region Deployment
  private static final FlagGroup DEPLOYMENT = new FlagGroup(FLAGS, "deployment", "Deployment");

  public static final @NotNull Flag<Boolean> LOGGERS_ERRORS_ENABLED =
    Flag.create(DEPLOYMENT,
                "loggers.errors.enabled",
                "Enable the logging of errors from Loggers::errorOrWarn",
                "Enable the logging of errors from Loggers::errorOrWarn",
                false);
  // endregion

  //region SERVER_FLAGS
  private static final FlagGroup SERVER_FLAGS = new FlagGroup(FLAGS, "serverflags", "Server Flags");
  public static final Flag<Boolean> TEST_SERVER_FLAG = Flag.create(
    SERVER_FLAGS, "test", "Test Server Enabled Flag",
    "Creates a sample studio flag that can be set using a server flag",
    false
  );
  // endregion SERVER_FLAGS

  // region Firebase Test Lab
  private static final FlagGroup FIREBASE_TEST_LAB = new FlagGroup(FLAGS, "firebasetestlab", "Firebase Test Lab");

  public static final Flag<Boolean> DIRECT_ACCESS =
    Flag.create(
      FIREBASE_TEST_LAB,
      "direct.access",
      "Direct Access",
      "Enable FTL DirectAccess",
      false);

  public static final Flag<Boolean> DIRECT_ACCESS_MULTIPLE_DEVICES =
    Flag.create(
      FIREBASE_TEST_LAB,
      "direct.access.multiple.devices",
      "Multiple devices",
      "Enables running multiple devices from FTL",
      false
    );

  public static final Flag<String> DIRECT_ACCESS_PROJECT =
    Flag.create(
      FIREBASE_TEST_LAB,
      "direct.access.project",
      "GCP Project for Direct Access authentication",
      "The project to use for FTL Direct Access",
      "ftl-direct-access-internal-eap"
    );

  public static final Flag<String> DIRECT_ACCESS_ENDPOINT =
    Flag.create(
      FIREBASE_TEST_LAB,
      "direct.access.endpoint",
      "FTL Direct Access endpoint",
      "The URL for FTL Direct Access to connect to, in host:port form (with no protocol specified).",
      "testing.googleapis.com"
    );

  public static final Flag<String> DIRECT_ACCESS_DEVICE_FILTER =
    Flag.create(
      FIREBASE_TEST_LAB,
      "direct.access.device.filter",
      "FTL Direct Access enabled devices",
      "The devices to show in FTL, in the format device1codename/apilevel,device2codename/apilevel,etc.",
      "cheetah/33,oriole/32,b2q/31,q2q/31,f2q/30,OnePlus5T/28,TC77/27,redfin/30"
    );
  // endregion Firebase Test Lab

  // region App Insights
  private static final FlagGroup APP_INSIGHTS = new FlagGroup(FLAGS, "appinsights", "App Insights");
  public static final Flag<Boolean> APP_INSIGHTS_ENABLED =
    Flag.create(
      APP_INSIGHTS,
      "enabled",
      "Enabled",
      "Enable App Insights tool window and highlighting support.",
      true);

  public static final Flag<Boolean> APP_INSIGHTS_GUTTER_SUPPORT =
    Flag.create(
      APP_INSIGHTS,
      "insights.gutter",
      "Gutter Support",
      "Use gutter icons rather than code highlight to display insights in the editor",
      true);

  public static final Flag<String> CRASHLYTICS_GRPC_SERVER =
    Flag.create(
      APP_INSIGHTS,
      "crashlytics.grpc.server",
      "Set Crashlytics gRpc server address",
      "Set Crashlytics gRpc server address, mainly used for testing purposes.",
      "firebasecrashlytics.googleapis.com");

  public static final Flag<Boolean> CRASHLYTICS_GRPC_USE_TRANSPORT_SECURITY =
    Flag.create(
      APP_INSIGHTS,
      "crashlytics.grpc.use.transport.security",
      "Use transport security",
      "Set Crashlytics gRpc channel to use transport security",
      true);

  public static final Flag<Boolean> OPEN_CLOSE_ISSUES_ENABLED =
    Flag.create(
      APP_INSIGHTS,
      "enable.open.close.issues",
      "Enable open/close issue functionality.",
      "Add open/close button to App Quality Insights panel.",
      true);

  public static final Flag<Boolean> ADDITIONAL_FILTERS_ENABLED =
    Flag.create(
      APP_INSIGHTS,
      "enable.issue.filters",
      "Enable additional issue filters.",
      "Add device, OS, Play Track filters to App Quality Insights panel.",
      true);

  public static final Flag<Boolean> OFFLINE_MODE_SUPPORT_ENABLED =
    Flag.create(
      APP_INSIGHTS,
      "enable.offline.mode.support",
      "Enable offline mode support.",
      "Show previously cached data when network has issues.",
      true);

  public static final Flag<Boolean> NOTES_ENABLED =
    Flag.create(
      APP_INSIGHTS,
      "enable.notes",
      "Enable read/write notes functionality.",
      "Add notes tab to App Quality Insights panel.",
      true);
  // endregion App Insights

  // region App Links Assistant
  private static final FlagGroup APP_LINKS_ASSISTANT = new FlagGroup(FLAGS, "app.links.assistant", "App Links Assistant");
  public static final Flag<Boolean> KOTLIN_INTENT_HANDLING =
    Flag.create(APP_LINKS_ASSISTANT, "kotlin.intent.handling", "Kotlin Intent Handling",
                "Support adding logic for intent handling in Kotlin.", true);
  public static final Flag<Boolean> APP_LINKS_ASSISTANT_V2 =
    Flag.create(APP_LINKS_ASSISTANT, "v2", "App Links Assistant V2",
                "Revamped App Links Assistant (new surfaces and navigation between surfaces).", false);
  public static final Flag<Boolean> WEBSITE_ASSOCIATION_GENERATOR_V2 =
    Flag.create(APP_LINKS_ASSISTANT, "website.association.generator.v2", "Website Association Generator V2",
                "Improvements to Website Association Generator.", false);
  public static final Flag<String> DEEPLINKS_GRPC_SERVER =
    Flag.create(APP_LINKS_ASSISTANT, "deeplinks.grpc.server", "Deep links gRPC server address",
                "Deep links gRPC server address. Use a non-default value for testing purposes.",
                "deeplinkassistant-pa.googleapis.com");
  // endregion App Links Assistant

  // region GOOGLE_PLAY_SDK_INDEX
  private static final FlagGroup GOOGLE_PLAY_SDK_INDEX = new FlagGroup(FLAGS, "google.play.sdk.index", "Google Play SDK Index");
  public static final Flag<Boolean> SHOW_SDK_INDEX_MESSAGES = Flag.create(
    GOOGLE_PLAY_SDK_INDEX, "show.sdk.index.messages", "Show SDK Index messages",
    "Show messages related to Google Play SDK Index",
    true
  );
  public static final Flag<Boolean> INCLUDE_LINKS_TO_SDK_INDEX = Flag.create(
    GOOGLE_PLAY_SDK_INDEX, "include.links.to.sdk.index", "Include links to SDK Index",
    "Whether or not links to Google Play SDK Index should be included in the SDK Index messages",
    true
  );
  public static final Flag<Boolean> SHOW_SDK_INDEX_CRITICAL_ISSUES = Flag.create(
    GOOGLE_PLAY_SDK_INDEX, "show.sdk.critical.issues", "Show SDK Index critical issues",
    "Whether or not critical issues from library authors should be shown",
    true
  );
  public static final Flag<Boolean> SHOW_SDK_INDEX_POLICY_ISSUES = Flag.create(
    GOOGLE_PLAY_SDK_INDEX, "show.sdk.policy.issues", "Show SDK Index policy issues",
    "Whether or not show issues when libraries are not policy complaint",
    false
  );
  // endregion GOOGLE_PLAY_SDK_INDEX

  // region NEW_COLLECT_LOGS_DIALOG
  private static final FlagGroup NEW_COLLECT_LOGS_DIALOG = new FlagGroup(FLAGS, "new.collect.logs", "New Collect Logs Dialog");
  public static final Flag<Boolean> ENABLE_NEW_COLLECT_LOGS_DIALOG = Flag.create(
    NEW_COLLECT_LOGS_DIALOG, "enable.new.collect.logs.dialog", "Enable new collect logs dialog",
    "Enable the new send feedback dialog",
    false
  );
  // endregion NEW_COLLECT_LOGS_DIALOG

  // region TargetSDKVersion Upgrade Assistant
  private static final FlagGroup TSDKVUA = new FlagGroup(FLAGS, "tsdkvua", "Android SDK Upgrade Assistant");
  public static final Flag<Boolean> TSDKVUA_ENABLE = Flag.create(TSDKVUA, "enable", "Enable the Android SDK Upgrade Assistant", "Enable the Android SDK Upgrade Assistant", true);
  public static final Flag<Boolean> TSDKVUA_FILTERS = Flag.create(TSDKVUA, "filters", "Enable relevance filtering", "Enable relevance filtering", false);
  // endregion TargetSDKVersion Upgrade Assistant

  // region PROCESS_NAME_MONITOR
  private static final FlagGroup PROCESS_NAME_MONITOR = new FlagGroup(FLAGS, "processnamemonitor", "Process Name Monitor");
  public static final Flag<Boolean> ENABLE_PROCESS_NAME_POLLING = Flag.create(
    PROCESS_NAME_MONITOR, "processnamemonitor.enable.process.name.polling", "Enable process name polling",
    "Enable process name polling. Changing the value of this flag requires restarting Android Studio.",
    false
  );
  // endregion NEW_SEND_FEEDBACK_DIALOG

  private StudioFlags() { }
}
