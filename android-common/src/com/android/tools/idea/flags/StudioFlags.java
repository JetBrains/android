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

import static com.intellij.util.PlatformUtils.getPlatformPrefix;

import com.android.flags.BooleanFlag;
import com.android.flags.Flag;
import com.android.flags.FlagGroup;
import com.android.flags.FlagOverrides;
import com.android.flags.Flags;
import com.android.flags.IntFlag;
import com.android.flags.StringFlag;
import com.android.flags.overrides.DefaultFlagOverrides;
import com.android.flags.overrides.PropertyOverrides;
import com.android.tools.idea.IdeChannel;
import com.android.tools.idea.flags.enums.PowerProfilerDisplayMode;
import com.android.tools.idea.flags.overrides.ServerFlagOverrides;
import com.android.tools.idea.util.StudioPathManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.util.PlatformUtils;
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

  private static boolean applicationIsInternal() {
    Application application = ApplicationManager.getApplication();
    return application != null && !application.isUnitTestMode() && application.isInternal();
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

  public static final Flag<Boolean> NPW_SHOW_KTS_GRADLE_COMBO_BOX = Flag.create(
    NPW, "show.kts.gradle.combobox", "Show KTS/Gradle Combobox",
    "Show KTS/Gradle Combobox to which build script is used for the generated code",
    true);

  public static final Flag<Boolean> NPW_SHOW_AGP_VERSION_COMBO_BOX = Flag.create(
    NPW, "show.agp.version.combobox", "Show AGP version combobox",
    "Show a combobox to select the version of Android Gradle plugin used for the new project",
    applicationIsInternal());

  public static final Flag<Boolean> NPW_NEW_NATIVE_MODULE = Flag.create(
    NPW, "new.native.module", "New Android Native Module",
    "Show template to create a new Android Native module in the new module wizard.",
    true);

  public static final Flag<Boolean> NPW_NEW_MACRO_BENCHMARK_MODULE = Flag.create(
    NPW, "new.macro.benchmark.module", "New Macro Benchmark Module",
    "Show template to create a new Macro Benchmark module in the new module wizard.",
    true);

  public static final Flag<Boolean> NPW_NEW_BASELINE_PROFILES_MODULE = Flag.create(
    NPW, "new.baseline.profiles.module", "New Baseline Profile Module",
    "Show template to create a new Baseline Profile module in the new module wizard.",
    true);
  public static final Flag<Boolean> NPW_ENABLE_GRADLE_VERSION_CATALOG = Flag.create(
    NPW, "enable.version.catalog", "Enable Gradle Version Catalog",
    "Use Gradle Version Catalogs for dependencies added in the new project/module wizard. (when existing project already uses Version Catalogs for new modules)",
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

  public static final Flag<Boolean> PROFILER_TESTING_MODE = Flag.create(
    PROFILER, "testing.mode", "Enable the testing mode in Profiler",
    "Toggles the testing mode for more logging and Actions to facilitate automatic testing.",
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
    "When set to CUMULATIVE, shows power rails and battery counters in their raw view (cumulative counters). " +
    "When set to DELTA, shows the power rails in a delta view and battery counters in their raw view (cumulative counters).",
    PowerProfilerDisplayMode.DELTA);

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

  public static final Flag<Boolean> PROFILER_TASK_BASED_UX = Flag.create(PROFILER, "task.based.ux", "Task-based UX",
    "Enables a simpler profilers UX, with tabs for specific tasks which an app developer usually performs (e.g. Reduce jank)", false);

  public static final Flag<Boolean> PROFILER_TRACEBOX =
    Flag.create(PROFILER, "tracebox", "Tracebox", "Tracebox for versions M,N,O,P of Android", false);
  //endregion

  //region ML
  private static final FlagGroup ML = new FlagGroup(FLAGS, "ml", "ML");
  public static final Flag<Boolean> ML_MODEL_BINDING = Flag.create(
    ML, "modelbinding", "Enable ML model binding",
    "When enabled, TFLite model file will be recognized and indexed. Please invalidates file caches after enabling " +
    "(File -> Invalidate Caches...) in order to reindex model files.",
    true);
  //endregion

  //region Design Tools
  private static final FlagGroup DESIGN_TOOLS = new FlagGroup(FLAGS, "design.tools", "Design Tools");

  public static final Flag<Long> PROJECT_SYSTEM_CLASS_LOADER_CACHE_LIMIT = Flag.create(
    DESIGN_TOOLS,
    "project.system.class.loader.cache.max.size",
    "Configure the max size of the cache used by ProjectSystemClassLoader",
    "Allow configuring the maximum size (in bytes) of the cache used by the ProjectSystemClassLoader to load classes from JAR files. " +
    "Files larger than the cache limit will cause a file miss and the file will need to be read again.",
    20_000_000L
  );

  public static final Flag<Long> GRADLE_CLASS_FINDER_CACHE_LIMIT = Flag.create(
    DESIGN_TOOLS,
    "gradle.class.finder.cache.max.size",
    "Configure the max size of the cache used by GradleClassFileFinder",
    "Allow configuring the maximum number of file references to be kept.",
    150L
  );
  //endregion

  //region Layout Editor
  private static final FlagGroup NELE = new FlagGroup(FLAGS, "nele", "Layout Editor");

  public static final Flag<Boolean> NELE_RENDER_DIAGNOSTICS = Flag.create(
    NELE, "diagnostics", "Enable rendering on-screen stats",
    "If enabled, the surface displays some debug information to diagnose performance",
    false);

  public static final Flag<Boolean> NELE_LOG_ANDROID_FRAMEWORK = Flag.create(
    NELE, "log.android.framework", "Log messages coming from Layoutlib Native.",
    "Log in the IDEA log the messages coming from Java and native code of Layoutlib Native.",
    false);

  public static final Flag<Boolean> NELE_USE_CUSTOM_TRAFFIC_LIGHTS_FOR_RESOURCES = Flag.create(
    NELE, "use.custom.traffic.lights.for.resources", "Base traffic lights on the errors from the shared issue panel",
    "Use errors from the current file and qualifiers tab in the traffic light rendering for resource files.",
    isAndroidStudio());

  public static final Flag<Boolean> NELE_ASSET_REPOSITORY_INCLUDE_AARS_THROUGH_PROJECT_SYSTEM = Flag.create(
    NELE, "asset.repository.include.aars.through.project.system", "Include AARs through project system",
    "Include resource directories from AARs found through project system.",
    false);

  public static final Flag<Boolean> NELE_ATF_FOR_COMPOSE = Flag.create(
    NELE, "atf.for.compose", "Enable ATF checks for Compose",
    "Allow running accessibility checks for Compose using ATF.",
    true);

  public static final Flag<Boolean> NELE_COMPOSE_UI_CHECK_MODE = Flag.create(
    NELE, "compose.ui.check.mode", "Enable UI Check mode for Compose preview",
    "Enable UI Check mode in Compose preview for running ATF checks and Visual Linting",
    true);

  public static final Flag<Boolean> NELE_COMPOSE_UI_CHECK_COLORBLIND_MODE = Flag.create(
    NELE, "compose.ui.check.mode.colorblind", "Enable colorblind mode in UI Check for Compose preview",
    "Enable colorblind Check mode in UI Check Mode for Compose preview",
    false);

  public static final Flag<Boolean> NELE_COMPOSE_VISUAL_LINT_RUN = Flag.create(
    NELE, "compose.visual.lint.run", "Enable visual lint for Compose Preview",
    "Enable so that visual lint runs on previews in the Compose Preview.",
    true);

  public static final Flag<Boolean> NELE_CLASS_PRELOADING_DIAGNOSTICS = Flag.create(
    NELE, "preview.class.preloading.diagnostics", "Enable class preloading overlay",
    "If enabled, the surface displays background class preloading progress",
    false);

  public static final Flag<Boolean> NELE_NEW_COMPONENT_TREE = Flag.create(
    NELE, "use.component.tree.builder", "Use the Component Tree builder",
    "If enabled, use the Component Tree builder for the Nele component tree",
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
    RUNDEBUG, "console.output.enabled", "Show logcat process output in Run/Debug console window",
    "When running or debugging an Android process, output the logcat output of the process in the console window.",
    false);

  public static final Flag<Boolean> RUNDEBUG_ANDROID_BUILD_BUNDLE_ENABLED = Flag.create(
    RUNDEBUG, "android.bundle.build.enabled", "Enable the Build Bundle action",
    "If enabled, the \"Build Bundle(s)\" menu item is enabled. " +
    "Changing the value of this flag requires restarting " + getFullProductName() + ".",
    true);

  public static final Flag<Boolean> GENERATE_BASELINE_PROFILE_GUTTER_ICON = Flag.create(
    RUNDEBUG,
    "baselineprofile.guttericon.enabled",
    "Enables generating baseline profiles from gutter icon",
    "When opening a UI test with applied BaselineProfileRule, an option to generate baseline profiles is shown in the gutter popup.",
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

  public static final Flag<Boolean> ADB_CONNECTION_STATUS_WIDGET_ENABLED = Flag.create(
    RUNDEBUG,
    "adb.connection.status.widget.enabled",
    "Enable and Show ADB Connection Widget",
    "Enables and shows the ADB connection status widget in the status bar",
    false);

  public static final Flag<Boolean> ADB_SERVER_MANAGEMENT_MODE_SETTINGS_VISIBLE = Flag.create(
    RUNDEBUG,
    "adb.server.management.mode.settings.visible",
    "Show ADB server management mode settings",
    "To allow toggling between automatic or user managed ADB server mode.",
    false);

  public static final Flag<Boolean> DEPLOYMENT_TARGET_DEVICE_PROVISIONER_MIGRATION = new BooleanFlag(
    RUNDEBUG,
    "deployment.target.deviceprovisioner",
    "Use Device Provisioner to provide deployment targets",
    "Uses the Device Provisioner to get the list of potential devices to deploy to.",
    ChannelDefault.of(PlatformUtils.isFleetBackend())
      .withDevOverride(true)
      .withNightlyOverride(true)
      .withCanaryOverride(true));

  public static final Flag<Boolean> DEVICE_EXPLORER_PROCESSES_PACKAGE_FILTER = Flag.create(
    RUNDEBUG,
    "adb.device.explorer.package.filter.enable",
    "Enable package filtering for the \"Device Explorer\" tool window",
    "Enable package filtering for the \"Device Explorer\" tool window, which allows users to filter processes by app package ids.\n" +
    "Changing the value of this flag requires restarting Android Studio.",
    true);

  public static final Flag<Boolean> ADBLIB_MIGRATION_WIFI_PAIRING = Flag.create(
    RUNDEBUG,
    "adblib.migration.wifi.pairing",
    "Use adblib in Pair Device over Wi-Fi",
    "Use adblib instead of ddmlib for Pair Device over Wi-Fi",
    true);

  public static final Flag<Boolean> ADBLIB_MIGRATION_DDMLIB_CLIENT_MANAGER = Flag.create(
    RUNDEBUG,
    "adblib.migration.ddmlib.clientmanager",
    "Use adblib to track device processes (Client)",
    "Use adblib instead of ddmlib to track processes (Client) on devices and handle debug sessions. " +
    "Note: Changing the value of this flag requires restarting Android Studio.",
    true);

  public static final Flag<Boolean> ADBLIB_MIGRATION_DDMLIB_IDEVICE_MANAGER = Flag.create(
    RUNDEBUG,
    "adblib.migration.ddmlib.idevicemanager",
    "Use adblib to track devices (IDevice)",
    "Use adblib instead of ddmlib to track and implement `IDevice` instances. " +
    "Note: Changing the value of this flag requires restarting Android Studio.",
    false);

  public static final Flag<Boolean> ADBLIB_MIGRATION_DDMLIB_IDEVICE_USAGE_TRACKER = Flag.create(
    RUNDEBUG,
    "adblib.migration.ddmlib.ideviceusage.tracker",
    "Enable Android Studio usage stats for IDevice methods",
    "Track IDevice method calls and success rates. " +
    "Note: Changing the value of this flag requires restarting Android Studio.",
    false);

  public static final Flag<Boolean> ADBLIB_ONE_SESSION_PER_PROJECT = Flag.create(
    RUNDEBUG,
    "adblib.one.session.per.project",
    "Creates one AdbSession per project",
    "Creates one AdbSession per project, as opposed to one shared Application level instance. " +
    "Note: Changing the value of this flag requires restarting Android Studio.",
    true);

  public static final Flag<Boolean> JDWP_TRACER = Flag.create(
    RUNDEBUG,
    "adb.jdwp.tracer.enabled",
    "Enable JDWP Traces",
    "Enables capture of JDWP traffic and generate a perfetto report",
    false);

  public static final Flag<Boolean> JDWP_SCACHE = Flag.create(
    RUNDEBUG,
    "adb.jdwp.scache.enabled",
    "Enable JDWP SCache",
    "Enables JDWP Speculative Cache (SCache)",
    true);

  public static final Flag<Boolean> JDWP_SCACHE_REMOTE_ONLY = Flag.create(
    RUNDEBUG,
    "adb.jdwp.scache.remote.only.enabled",
    "Enable JDWP SCache for remote devices only",
    "Enables JDWP Speculative Cache (SCache) for remote devices only",
    true);

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

  public static final Flag<Boolean> DEBUG_ATTEMPT_SUSPENDED_START = Flag.create(
    RUNDEBUG,
    "debug.app.suspend.upon.start.enable",
    "Start activity suspended when debugging.",
    "Start activity suspended when debugging. This reduce the amount of time 'Waiting for Debugger' panel is shown on device",
    true
  );

  public static final Flag<Boolean> ATTACH_ON_WAIT_FOR_DEBUGGER = Flag.create(
    RUNDEBUG,
    "debug.app.auto.attach.wait.for.debugger",
    "Auto attach debugger on Debug.waitForDebugger().",
    "If the user has Debug.waitForDebugger() calls within the app code, this will allow the debugger to automatically attach to the app.",
    true
  );

  public static final Flag<Boolean> USE_BITMAP_POPUP_EVALUATOR_V2 = Flag.create(
    RUNDEBUG,
    "use.bitmap.popup.evaluator.v2",
    "Use the new BitmapPopupEvaluatorV2",
    "BitmapPopupEvaluatorV2 uses Bitmap.getPixels() instead of Bitmap.copyPixelsToBuffer() which it makes platform independent",
    true
  );

  public static final Flag<Boolean> EMIT_CONSOLE_OUTPUT_TO_LOGCAT = Flag.create(
    RUNDEBUG,
    "emit.console.output.to.logcat",
    "Emit console output to Logcat",
    "Emit console output, specifically breakpoint log expressions, to Logcat.",
    true
  );

  //endregion

  //region Logcat
  private static final FlagGroup LOGCAT = new FlagGroup(FLAGS, "logcat", "Logcat");

  public static final Flag<Boolean> LOGCAT_CLICK_TO_ADD_FILTER = Flag.create(
    LOGCAT,
    "click.to.add.filter",
    "Enable Logcat click to add/remove filter feature",
    "Enable Logcat click to add/remove filter feature",
    true
  );

  public static final Flag<Boolean> LOGCAT_IS_FILTER = Flag.create(
    LOGCAT,
    "is.filter",
    "Enable Logcat 'is:...' filter",
    "Enables a Logcat filter using the 'is' keyword for example 'is:stacktrace'is:crash' etc",
    true
  );

  public static final Flag<Integer> LOGCAT_MAX_MESSAGES_PER_BATCH = Flag.create(
    LOGCAT,
    "max.messages.per.batch",
    "Set the max number of messages that are appended to the UI component",
    "Set the max number of messages that are appended to the UI component",
    1000
  );

  public static final Flag<Boolean> LOGCAT_PANEL_MEMORY_SAVER = Flag.create(
    LOGCAT,
    "panel.memory.saver",
    "Enable Logcat Panel memory saving feature",
    "Reduces memory usage of Logcat tool by writing data to a file when the panel is not visible",
    true
  );

  public static final Flag<Boolean> LOGCAT_TERMINATE_APP_ACTIONS_ENABLED = Flag.create(
    LOGCAT,
    "terminate.app.actions.enable",
    "Enable right-click actions for terminating the application",
    "Enable right-click actions for terminating the application. " +
    "Note that this feature is only enabled if the flag ADBLIB_MIGRATION_DDMLIB_CLIENT_MANAGER is also true. " +
    "Changing the value of this flag requires restarting Android Studio.",
    true
  );

  public static final Flag<Boolean> LOGCAT_IGNORE_STUDIO_TAGS = Flag.create(
    LOGCAT,
    "ignore.studio.tags",
    "Ignore tags that Studio itself is responsible for",
    "Ignore tags that Studio itself is responsible for",
    true
  );

  public static final Flag<Boolean> LOGCAT_EXPORT_IMPORT_ENABLED = Flag.create(
    LOGCAT,
    "export.import.enable",
    "Enable Export/Import feature",
    "Enable Export/Import feature",
    true
  );

  public static final Flag<Boolean> LOGCAT_PROTOBUF_ENABLED = Flag.create(
    LOGCAT,
    "protobuf.enable",
    "Enable Logcat Protobuf format",
    "Enable Logcat Protobuf format",
    true
  );
  //endregion

  //region Gradle Project System
  private static final FlagGroup GRADLE_IDE = new FlagGroup(FLAGS, "gradle.ide", "Gradle Project System");

  public static final Flag<Boolean> ANDROID_SDK_AND_IDE_COMPATIBILITY_RULES = Flag.create(
    GRADLE_IDE, "android.sdk.ide.compatibility.rules",
    "Enable compatibility rules support between IDE version and compile SDK version",
    "Enable compatibility rules support between IDE version and compile SDK version",
    true
  );

  public static final Flag<Boolean> API_OPTIMIZATION_ENABLE = Flag.create(
    GRADLE_IDE, "build.injection.device.api.enabled",
    "Enable injection of device api level optimization from IDE",
    "Enable injection of device api level optimization from IDE",
    true
  );

  public static final Flag<Boolean> INJECT_DEVICE_SERIAL_ENABLED = Flag.create(
    GRADLE_IDE, "internal.build.injection.device.serial.number",
    "For internal use only. Enables injection of device serial from the IDE into Gradle build.",
    "For internal use only. Enables injection of device serial from the IDE into Gradle build.",
    false
  );

  public static final Flag<Boolean> USE_DEVELOPMENT_OFFLINE_REPOS = Flag.create(
    GRADLE_IDE, "development.offline.repos", "Enable development offline repositories",
    "Uses the development offline repositories " +
    "(which can come from STUDIO_CUSTOM_REPO or from a local build of AGP when running studio from IDEA) " +
    "in the new project templates and for determining which versions of AGP are avaliable for the upgrade assistant.",
    StudioPathManager.isRunningFromSources());

  public static final Flag<Boolean> INJECT_EXTRA_GRADLE_REPOSITORIES_WITH_INIT_SCRIPT = Flag.create(
    GRADLE_IDE, "inject.repos.with.init.script",
    "Inject repositories using a Gradle init script",
    "Also inject any development offline repos (if gradle.ide.development.offline.repos is set) " +
    "and the customised GMAVEN_TEST_BASE_URL if set using a Gradle init script at every build and sync invocation. " +
    "Note this this is disabled by default as it can break projects that would otherwise sync and build correctly with " +
    "published versions of AGP, including the relatively common case of projects that depend on AGP in buildSrc.",
    false);

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

  /**
   * @see #isBuildOutputShowsDownloadInfo
   */
  public static final Flag<Boolean> BUILD_OUTPUT_DOWNLOADS_INFORMATION = Flag.create(
    GRADLE_IDE, "build.output.downloads.information", "Enable downloads information in Build/Sync View",
    "Show separate node with downloads information in Build and Sync views.", true);

  public static final Flag<Boolean> DISABLE_FORCED_UPGRADES = Flag.create(
    GRADLE_IDE, "forced.agp.update", "Disable forced Android Gradle plugin upgrades",
    "This option is only respected when running " + getFullProductName() + " internally.", false);

  public static final Flag<Boolean> SUPPORT_FUTURE_AGP_VERSIONS = Flag.create(
    GRADLE_IDE, "support.future.agp.versions", "Support opening projects that use future AGPs",
    "Respect the Android Gradle plugin's minimum model consumer version (i.e. minimum required Studio version), " +
    "if present in AGP, superseding the hardcoded maximum supported version of AGP. " +
    "This opens the possibility for Studio to open versions of AGP released after it was, if that version of AGP declares " +
    "that it is compatible.", false);

  public static final Flag<Boolean> GRADLE_SYNC_PARALLEL_SYNC_ENABLED = Flag.create(
    GRADLE_IDE, "gradle.sync.parallel.sync.enabled", "Enables parallel sync",
    "This allows the IDE to fetch models in parallel (if supported by Gradle and enabled via org.gradle.parallel=true).", true);

  public static final Flag<Boolean> GRADLE_SYNC_PARALLEL_SYNC_PREFETCH_VARIANTS = Flag.create(
    GRADLE_IDE, "gradle.sync.parallel.sync.prefetch.variants", "Enables speculative syncing of current variants",
    "This allows the IDE to pre-fetch models for the currently selected variants in parallel before resolving the " +
    "new variant selection (which is less parallelizable process).", false);

  public static final Flag<Boolean> GRADLE_SYNC_FETCH_KOTLIN_MODELS_IN_PARALLEL = Flag.create(
    GRADLE_IDE, "gradle.sync.fetch.kotlin.models.in.parallel", "Enables parallel fetching of Kotlin models",
    "This allows the IDE to fetch Kotlin models in parallel", true);


  public static final Flag<String> SYNC_STATS_OUTPUT_DIRECTORY = Flag.create(
    GRADLE_IDE, "sync.stats.output.directory", "Enables printing sync stats to a file",
    "If not empty, sync execution stats for models requested by Android Studio are printed to a file in the given directory when" +
    "sync completes.", "");

  public static final Flag<Boolean> GRADLE_SYNC_ENABLE_CACHED_VARIANTS = Flag.create(
    GRADLE_IDE, "gradle.sync.enable.cached.variants", "Enables caching of build variants",
    "Enables caching of build variant data so that the IDE does not always run Gradle when switching between build variants. " +
    "While faster this mode may be incompatible with some plugins.", true);

  public static final Flag<Boolean> GRADLE_SYNC_USE_V2_MODEL = Flag.create(
    GRADLE_IDE, "gradle.sync.use.v2", "Use V2 Builder models", "Enable fetching V2 builder models from AGP when syncing.", true);

  public static final Flag<Boolean> GRADLE_SYNC_RECREATE_JDK = Flag.create(
    GRADLE_IDE, "gradle.sync.recreate.jdk", "Recreate JDK on sync", "Recreate Gradle JDK when syncing if there are changed roots.", true);

  public static final Flag<Boolean> GRADLE_USES_LOCAL_JAVA_HOME_FOR_NEW_CREATED_PROJECTS = Flag.create(
    GRADLE_IDE, "gradle.uses.local.java.home.for.new.created.projects",
    "Gradle uses local java.home for new created projects",
    "When creating new projects the gradleJvm will be configured with #GRADLE_LOCAL_JAVA_HOME macro, using the java.home value " +
    "specified under .gradle/config.properties to trigger Gradle sync.", true);

  public static final Flag<Boolean> MIGRATE_PROJECT_TO_GRADLE_LOCAL_JAVA_HOME = Flag.create(
    GRADLE_IDE, "migrate.project.to.gradle.local.java.home",
    "Migrate project to Gradle local java.home",
    "Suggest migrating current project JDK configuration to .gradle/config.properties where gradleJvm uses the " +
    "#GRADLE_LOCAL_JAVA_HOME macro and the java.home stores the JDK path to trigger Gradle sync.", true);

  public static final Flag<Boolean> DECLARATIVE_PLUGIN_STUDIO_SUPPORT =
    Flag.create(GRADLE_IDE, "declarative.plugin.studio.support", "Studio support for AGP declarative plugin",
                "Enable support for gradle.build.toml in PSD and Assistants", false);

  public static final Flag<Boolean> GRADLE_SAVE_LOG_TO_FILE = Flag.create(
    GRADLE_IDE, "save.log.to.file", "Save log to file", "Appends the build log to the given file", false);

  /**
   * Don't read this directly, use AgpVersions.agpVersionStudioFlagOverride which handles the 'stable' alias
   */
  public static final Flag<String> AGP_VERSION_TO_USE = Flag.create(
    GRADLE_IDE, "agp.version.to.use", "Version of AGP to use",
    "The AGP version to use when making a new project, e.g. \"8.0.0-dev\". To use the latest stable version of AGP, set the value" +
    "to \"stable\". When set, a compatible Gradle version will also be " +
    "selected. If unset, the latest AGP version and the latest Gradle version will be used.",
    ""
  );

  public static final Flag<Boolean> GRADLE_SKIP_RUNTIME_CLASSPATH_FOR_LIBRARIES = Flag.create(
    GRADLE_IDE,
    "gradle.skip.runtime.classpath.for.libraries",
    "Enable the Gradle experimental setting to skip runtime classpath resolution for libraries",
    "Enables the Gradle experimental setting to skip the runtime classpath resolution for libraries," +
    " instead obtain the information from the applications dependency graph.",
    true
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

  public static final Flag<Boolean> GRADLE_HEAP_ANALYSIS_LIGHTWEIGHT_MODE = Flag.create(
    GRADLE_IDE,
    "gradle.heap.analysis.lightweight.mode",
    "Gradle heap analysis lightweight mode",
    "If set, the analysis will just get a histogram using standard JVM APIs. It's suggested to use -XX:SoftRefLRUPolicyMSPerMB=0 in gradle " +
    "jvm args to reduce the variance in these readings.",
    false
  );

  public static final Flag<Boolean> GRADLE_MULTI_VARIANT_ADDITIONAL_ARTIFACT_SUPPORT = Flag.create(
    GRADLE_IDE,
    "gradle.multi.variant.additional.artifact.support",
    "Gradle multi variant additional artifact support",
    "Enable an option in the Gradle experimental settings to switch to building additional artifacts (javadocs/srcs/samples) " +
    "inside Gradle rather than an injected model builder. This allows us to support variant specific artifacts and prevents the IDE from" +
    " having to match by Gradle coordinate. This flag will have no effect if used with a version of AGP before 8.1.0-alpha8.",
    true
  );

  public static final Flag<Boolean> USE_NEW_DEPENDENCY_GRAPH_MODEL = Flag.create(
    GRADLE_IDE,
    "use.new.dependency.graph.model",
    "Switches to a new dependency graph model that reduces memory use",
    "Switches to a new dependency graph model that reduces memory use. This Flag is introduced as a killswitch in case there" +
    "unexpected issues with the new model.",
    true
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

  public static final Flag<Boolean> DYNAMIC_LAYOUT_INSPECTOR_IN_RUNNING_DEVICES_ENABLED = Flag.create(
    LAYOUT_INSPECTOR, "dynamic.layout.inspector.enable.running.devices", "Enable Layout Inspector in Running Devices",
    "When this flag is enabled, LayoutInspector be integrated in the Running Devices tool window, instead of in its own tool window.",
    true);

  public static final Flag<Boolean> DYNAMIC_LAYOUT_INSPECTOR_THROW_UNEXPECTED_ERROR = Flag.create(
    LAYOUT_INSPECTOR, "dynamic.layout.inspector.enable.throw.unexpected.error", "Throw exception when encountering an unexpected error",
    "When this flag is enabled, LayoutInspector will throw an exception when an unexpected error is being logged to the metrics.",
    StudioPathManager.isRunningFromSources());

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

  public static final Flag<Boolean> DYNAMIC_LAYOUT_INSPECTOR_EXTRA_LOGGING = Flag.create(
    LAYOUT_INSPECTOR, "dynamic.layout.inspector.extra.logging", "Add extra logging for problem detection",
    "When this flag is enabled, LayoutInspector will add extra logging for detection of various problems.",
    false);
  //endregion

  //region Embedded Emulator
  private static final FlagGroup EMBEDDED_EMULATOR = new FlagGroup(FLAGS, "embedded.emulator", "Embedded Emulator");
  public static final Flag<Boolean> EMBEDDED_EMULATOR_RESIZABLE_FOLDING = new BooleanFlag(
    EMBEDDED_EMULATOR, "resizable.folding", "Folding Support in Resizable AVD",
    "Folding toolbar button in the Foldable mode of Resizable AVD",
    true);
  public static final Flag<Boolean> EMBEDDED_EMULATOR_SCREENSHOT_STATISTICS = new BooleanFlag(
    EMBEDDED_EMULATOR, "screenshot.statistics", "Enable Collection of Screenshot Statistics",
    "Captures statistics of received Emulator screenshots",
    false);
  public static final Flag<Integer> EMBEDDED_EMULATOR_STATISTICS_INTERVAL_SECONDS = new IntFlag(
    EMBEDDED_EMULATOR, "screenshot.statistics.interval", "Aggregation Interval for Screenshot Statistics",
    "Aggregation interval in seconds for statistics of received Emulator screenshots",
    120);
  public static final Flag<Boolean> EMBEDDED_EMULATOR_TRACE_GRPC_CALLS = new BooleanFlag(
    EMBEDDED_EMULATOR, "trace.grpc.calls", "Enable Emulator gRPC Tracing",
    "Enables tracing of most Emulator gRPC calls",
    false);
  public static final Flag<Boolean> EMBEDDED_EMULATOR_TRACE_HIGH_VOLUME_GRPC_CALLS = new BooleanFlag(
    EMBEDDED_EMULATOR, "trace.high.volume.grpc.calls", "Enable High Volume Emulator gRPC Tracing",
    "Enables tracing of high volume Emulator gRPC calls",
    false);
  public static final Flag<Boolean> EMBEDDED_EMULATOR_TRACE_SCREENSHOTS = new BooleanFlag(
    EMBEDDED_EMULATOR, "trace.screenshots", "Enable Emulator Screenshot Tracing",
    "Enables tracing of received Emulator screenshots",
    false);
  public static final Flag<Boolean> EMBEDDED_EMULATOR_TRACE_NOTIFICATIONS = new BooleanFlag(
    EMBEDDED_EMULATOR, "trace.notifications", "Enable Emulator Notification Tracing",
    "Enables tracing of received Emulator notifications",
    true);
  public static final Flag<Boolean> EMBEDDED_EMULATOR_TRACE_DISCOVERY = new BooleanFlag(
    EMBEDDED_EMULATOR, "trace.discovery", "Enable Tracing of Emulator Discovery",
    "Enables tracing of Emulator discovery",
    false);
  public static final Flag<Boolean> EMBEDDED_EMULATOR_SETTINGS_PICKER = Flag.create(
    EMBEDDED_EMULATOR, "settings.picker", "Show settings picker",
    "Enables the settings picker to be shown for testing an application",
    false);
  //endregion

  //region Device Mirroring
  private static final FlagGroup DEVICE_MIRRORING = new FlagGroup(FLAGS, "device.mirroring", "Device Mirroring");
  public static final Flag<Boolean> DEVICE_MIRRORING_STANDALONE_EMULATORS = new BooleanFlag(
    DEVICE_MIRRORING, "allow.standalone.emulators", "Allow Mirroring of Standalone Emulators",
    "Treats standalone emulators the same as physical devices for the purpose of display mirroring;" +
    " not intended for production use due to slowness of video encoding in emulated mode",
    false);
  public static final Flag<Boolean> DEVICE_MIRRORING_REMOTE_EMULATORS = new BooleanFlag(
    DEVICE_MIRRORING, "allow.remote.emulators", "Allow Mirroring of Remote Emulators",
    "Treats remote emulators the same as physical devices for the purpose of display mirroring",
    false);
  public static final Flag<Boolean> B_303684492_WORKAROUND = new BooleanFlag(
    DEVICE_MIRRORING, "b.303684492.workaround", "Workaround for Bug 303684492",
    "Workaround for Android bug 303684492",
    false);
  public static final Flag<Boolean> DEVICE_MIRRORING_TAB_DND = new BooleanFlag(
    DEVICE_MIRRORING, "tab.dnd", "Drag and Drop of Device Tabs",
    "Allow drag and drop of device tabs",
    false);
  public static final Flag<String> DEVICE_MIRRORING_AGENT_LOG_LEVEL = new StringFlag(
    DEVICE_MIRRORING, "agent.log.level", "On Device Logging Level for Mirroring",
    "The log level used by the screen sharing agent, one of \"verbose\", \"debug\", \"info\", \"warn\" or \"error\";" +
    " the default is \"info\"",
    "");
  public static final Flag<Integer> DEVICE_MIRRORING_CONNECTION_TIMEOUT_MILLIS = new IntFlag(
    DEVICE_MIRRORING, "connection.timeout", "Connection Timeout for Mirroring",
    "Connection timeout for mirroring in milliseconds",
    10_000);
  public static final Flag<Integer> DEVICE_MIRRORING_MAX_BIT_RATE = new IntFlag(
    DEVICE_MIRRORING, "max.bit.rate", "Maximum Bit Rate for Mirroring of Physical Devices",
    "The maximum bit rate of video stream, zero means no limit",
    0);
  public static final Flag<String> DEVICE_MIRRORING_VIDEO_CODEC = new StringFlag(
    DEVICE_MIRRORING, "video.codec", "Video Codec Used for Mirroring of Physical Devices",
    "The name of a video codec, e.g. \"vp8\" or \"vp9\"; the default is \"vp8\"",
    "");
  //endregion

  // region Device Definition Download Service
  private static final FlagGroup DEVICE_DEFINITION_DOWNLOAD_SERVICE =
    new FlagGroup(FLAGS,
                  "device.definition.download.service",
                  "Device Definition Download Service");

  @NotNull
  public static final Flag<String> DEVICE_DEFINITION_DOWNLOAD_SERVICE_URL =
    Flag.create(DEVICE_DEFINITION_DOWNLOAD_SERVICE,
                "url",
                "URL",
                "The URL to download the device definitions from",
                "");
  // endregion

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

  public static final Flag<Boolean> MIGRATE_BUILDCONFIG_FROM_GRADLE_PROPERTIES_REFACTORING_ENABLED = Flag.create(
    REFACTORINGS, "migrateto.dslbuildconfig.enabled", "Enable the Migrate buildConfig from gradle.properties refactoring",
    "If enabled, show the action in the refactoring menu", true);
  //endregion

  //region NDK
  private static final FlagGroup NDK = new FlagGroup(FLAGS, "ndk", "Native code features");

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
    true
  );

  public static final Flag<Boolean> DAGGER_SUPPORT_ENABLED = Flag.create(
    EDITOR, "dagger.support.enabled",
    "Enable editor support for Dagger",
    "If enabled adds Dagger specific find usages, gutter icons and new parsing for Dagger errors",
    true
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

  public static final Flag<Boolean> COMPOSE_STATE_READ_INLAY_HINTS_ENABLED = new BooleanFlag(
    EDITOR, "compose.state.read.inlay.hints.enabled",
    "Enable inlay hints for State reads in @Composable functions",
    "If enabled, calls out reads of variables of type State inside @Composable functions.",
    ChannelDefault.of(false)
      .withDevOverride(true)
      .withNightlyOverride(true)
      .withCanaryOverride(true));

  public static final Flag<Boolean> RENDER_DRAWABLES_IN_AUTOCOMPLETE_ENABLED = Flag.create(
    EDITOR, "render.drawables.in.autocomplete.enabled",
    "Enable rendering of drawable resources in autocomplete popup UI",
    "If enabled, renders drawable resources in the autocomplete popup UI.",
    true
  );

  public static final FlagGroup ESSENTIALS_MODE = new FlagGroup(FLAGS, "essentialsmode", "Essentials Mode");


  public static final Flag<Boolean> ESSENTIALS_MODE_VISIBLE = Flag.create(
    ESSENTIALS_MODE, "essentials.mode.action.visible",
    "Show Essentials Mode visible in File drop down menu",
    "If enabled, makes Essential Highlighting action visible",
    false
  );
  public static final Flag<Boolean> ESSENTIALS_HIGHLIGHTING_MODE = Flag.create(
    ESSENTIALS_MODE, "essential.highlighting.in.essentials.mode",
    "Essential Highlighting mode on in Essentials mode",
   "When enabled turns on Essential Highlighting mode when in Essentials Mode. Essential Highlighting mode enables " +
   "limited code inspections and highlighting while editing until a save all action is received e.g. Lint.",
   false);

  public static final Flag<Boolean> ESSENTIALS_MODE_GETS_RECOMMENDED = Flag.create(
    ESSENTIALS_MODE, "essentials.mode.gets.recommend",
    "Essentials Mode is able to get recommended to the user",
    "When enabled this allows Android Studio to drive adoption of Essentials Mode by recommending users should try it out.",
    false);

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

  public static final Flag<Boolean> ENABLE_SCREENSHOT_TESTING = Flag.create(
    TESTING, "screenshot.testing", "Run screenshot tests",
    "If enabled, a screenshotTest source set will be added for running screenshot tests",
    false
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

  //region System Health
  private static final FlagGroup SYSTEM_HEALTH = new FlagGroup(FLAGS, "system.health", "System Health");
  public static final Flag<Boolean> WINDOWS_UCRT_CHECK_ENABLED = Flag.create(
    SYSTEM_HEALTH, "windows.ucrt.check.enabled", "Enable Universal C Runtime system health check",
    "If enabled, a notification will be shown if the Universal C Runtime in Windows is not installed",
    true);

  public static final Flag<Boolean> ANTIVIRUS_NOTIFICATION_ENABLED = Flag.create(
    SYSTEM_HEALTH, "antivirus.notification.enabled", "Enable antivirus system health check",
    "If enabled, a notification will be shown if antivirus realtime scanning is enabled and directories relevant to build performance aren't excluded",
    true);

  public static final Flag<Boolean> ANTIVIRUS_METRICS_ENABLED = Flag.create(
    SYSTEM_HEALTH, "antivirus.metrics.enabled", "Enable antivirus metrics collection",
    "If enabled, metrics about the status of antivirus realtime scanning and excluded directories will be collected",
    true);
  //endregion

  //region Compose
  private static final FlagGroup COMPOSE = new FlagGroup(FLAGS, "compose", "Compose");

  public static final Flag<Boolean> COMPOSE_PREVIEW_ESSENTIALS_MODE = Flag.create(
    COMPOSE, "preview.essentials.mode", "Enable Compose Preview Essentials Mode",
    "If enabled, Compose Preview Essentials Mode will be enabled.",
    IdeChannel.getChannel().isAtMost(IdeChannel.Channel.CANARY));

  public static final Flag<Boolean> COMPOSE_PREVIEW_DOUBLE_RENDER = Flag.create(
    COMPOSE, "preview.double.render", "Enable the Compose double render mode",
    "If enabled, preview components will be rendered twice so components depending on a recompose (like tableDecoration) " +
    "render correctly.",
    false);

  public static final Flag<Boolean> COMPOSE_PREVIEW_SCROLL_ON_CARET_MOVE = Flag.create(
    COMPOSE, "preview.scroll.on.caret.move", "Enable the Compose Preview scrolling when the caret moves",
    "If enabled, when moving the caret in the text editor, the Preview will show the preview currently under the cursor.",
    false);

  public static final Flag<Boolean> COMPOSE_DEPLOY_LIVE_EDIT_ADVANCED_SETTINGS_MENU = Flag.create(
    COMPOSE, "deploy.live.edit.deploy.advanced.settings",
    "Enable live edit deploy settings menu",
    "If enabled, advanced Live Edit settings menu will be visible",
    false
  );

  public static final Flag<Boolean> COMPOSE_DEPLOY_LIVE_EDIT_CLASS_DIFFER = Flag.create(
    COMPOSE, "deploy.live.edit.deploy.differ",
    "LiveEdit: Resolve changed classes and group IDs with the class differ.",
    "If enabled, the class differ will be used inside of the LE compiler",
    true
  );

  public static final Flag<Boolean> COMPOSE_DEPLOY_LIVE_EDIT_CONFINED_ANALYSIS = Flag.create(
    COMPOSE, "deploy.live.edit.deploy.confined.analysis",
    "LiveEdit: Limit compilation error analysis to only the current file",
    "If enabled, Live Edit will aggressively live update even if there are analysis errors " +
      "provided that the current file is error-free.",
    false
  );

  public static final Flag<Boolean> COMPOSE_DEPLOY_LIVE_EDIT_R8_DESUGAR = Flag.create(
    COMPOSE, "deploy.live.edit.deploy.desugar.r8",
    "LiveEdit: Desugar kotlinc outputs with R8",
    "If enabled, the outputs of kotlinc are desugared before being sent to LiveEdit engine. This improves " +
    "the odds of matching what was produced by the Build system",
    true
  );

  public static final Flag<Boolean> COMPOSE_DEPLOY_LIVE_EDIT_ALLOW_MULTIPLE_MIN_API_DEX_MARKERS_IN_APK = Flag.create(
    COMPOSE, "deploy.live.edit.allow.multiple.min.api.dex.markers.in.apk",
    "LiveEdit: Allow multiple min api dex markers in apk",
    "If enabled, apk may contain multiple min api dex markers and LiveEdit picks the lowest among them",
   false
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

  public static final Flag<Boolean> COMPOSE_SPRING_PICKER = Flag.create(
    COMPOSE, "preview.spring.picker",
    "Enable the SpringSpec picker",
    "If enabled, a picker will be available in SpringSpec calls on the Editor gutter",
    false
  );

  public static final Flag<Boolean> COMPOSE_VIEW_INSPECTOR = Flag.create(
    COMPOSE, "view.inspector",
    "Show the switch of view inspection tool in Compose",
    "If enabled, the user can toggle the mouse inspection tool in the dropdown menu of Compose Preview. The tools is disabled by default",
    false
  );

  public static final Flag<Boolean> COMPOSE_VIEW_FILTER = Flag.create(
    COMPOSE, "view.filter",
    "Support filter the previews in Compose",
    "If enabled, the user can find the filter actions to filter the visible previews in compose preview",
    false
  );

  public static final Flag<Boolean> COMPOSE_ZOOM_CONTROLS_DROPDOWN = Flag.create(
    COMPOSE, "preview.zoom.controls.dropdown",
    "Include Zoom Controls in the Compose Preview dropdown action",
    "If enabled, the zoom controls will also be displayed in the Compose Preview dropdown action, located on the top-left corner",
    false
  );

  public static final Flag<Integer> COMPOSE_INTERACTIVE_FPS_LIMIT = Flag.create(
    COMPOSE, "preview.interactive.fps.limit",
    "Interactive Preview FPS limit",
    "Controls the maximum number of frames per second in Compose Interactive Preview",
    30
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

  public static final Flag<Boolean> COMPOSE_FAST_PREVIEW_DAEMON_DEBUG = Flag.create(
    COMPOSE, "preview.fast.reload.debug.daemon", "Starts the Live Edit daemon in debug mode",
    "If enabled, the compiler daemon will wait for a debugger to be attached.",
    false);

  public static final Flag<Boolean> COMPOSE_PREVIEW_GROUP_LAYOUT = Flag.create(
    COMPOSE, "preview.group.layout", "Enable organization of Compose Preview in groups",
    "If enabled, multiple previews associated with composable will be grouped. Please invalidates file caches after " +
    "enabling or disabling (File -> Invalidate Caches...)", false);

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
    true);
  public static final Flag<Boolean> COMPOSE_PREVIEW_SELECTION = Flag.create(
    COMPOSE, "compose.preview.selection", "Enable the select/deselect interaction with Previews",
    "If enabled, Previews will be selectable, and some interactions will only be enabled for selected Previews",
    true);

  public static final Flag<Boolean> COMPOSE_PREVIEW_RENDER_QUALITY = Flag.create(
    COMPOSE, "compose.preview.render.quality", "Enable the usage of a render quality management mechanism for Compose Preview",
    "If enabled, different Previews will be rendered with different qualities according to zoom level, layout and scroll position",
    true);

  public static final Flag<Long> COMPOSE_PREVIEW_RENDER_QUALITY_DEBOUNCE_TIME = Flag.create(
    COMPOSE, "compose.preview.render.quality.debounce.time", "Render quality debounce time",
    "Milliseconds to wait before adjusting the quality of Previews, after a scroll or zoom change happens",
    100L);

  public static final Flag<Integer> COMPOSE_PREVIEW_RENDER_QUALITY_VISIBILITY_THRESHOLD = Flag.create(
    COMPOSE, "compose.preview.render.quality.visibility.threshold", "Render quality zoom visibility threshold",
    "When the zoom level is lower than this value, all previews will be rendered at low quality",
    20);

  public static final Flag<Boolean> COMPOSE_PREVIEW_RENDER_QUALITY_NOTIFY_REFRESH_TIME = Flag.create(
    COMPOSE, "compose.preview.render.quality.notify.time", "Notify refresh time for render quality refreshes",
    "If enabled, the time taken in render quality refreshes will be notified each time",
    false);

  public static final Flag<Boolean> COMPOSE_PREVIEW_KEEP_IMAGE_ON_ERROR = Flag.create(
    COMPOSE, "compose.preview.keep.image.on.error", "Keeps the last valid image after a render error",
    "If enabled, when an error happens, the surface will keep the last valid image",
    IdeChannel.getChannel().isAtMost(IdeChannel.Channel.CANARY));
  //endregion

  // region Wear surfaces
  private static final FlagGroup WEAR_SURFACES = new FlagGroup(FLAGS, "wear.surfaces", "Wear Surfaces");

  public static final Flag<Boolean> GLANCE_APP_WIDGET_PREVIEW = Flag.create(
    WEAR_SURFACES, "glance.preview.appwidget.enabled", "Enable Glance AppWidget preview",
    "If enabled, a preview for annotated glance app widget composable functions is displayed",
    IdeChannel.getChannel().isAtMost(IdeChannel.Channel.CANARY));

  public static final Flag<Boolean> GLANCE_TILE_PREVIEW = Flag.create(
    WEAR_SURFACES, "glance.preview.tile.enabled", "Enable Glance Tile preview",
    "If enabled, a preview for annotated glance tile composable functions is displayed",
    false);

  public static final Flag<Boolean> WEAR_TILE_PREVIEW = Flag.create(
    WEAR_SURFACES, "wear.tile.preview.enabled", "Enable Wear Tile preview",
    "If enabled, a preview for functions annotated with @Preview and returning TilePreviewData is displayed",
    IdeChannel.getChannel().isAtMost(IdeChannel.Channel.CANARY));
  // endregion

  // region Wear Health Services

  private static final FlagGroup WEAR_HEALTH_SERVICES = new FlagGroup(FLAGS, "wear.health.services", "Wear Health Services");

  public static final Flag<Boolean> SYNTHETIC_HAL_PANEL = Flag.create(
    WEAR_HEALTH_SERVICES, "synthetic.hal.panel.enabled", "Enable synthetic HAL panel",
    "If enabled, a button to display panel for modifying emulator sensors will appear",
    false
  );
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
  public static final Flag<Boolean> NETWORK_INSPECTOR_STATIC_TIMELINE = Flag.create(
    NETWORK_INSPECTOR, "static.timeline", "Use static timeline in Network Inspector",
    "Use static timeline in Network Inspector",
    true
  );
  public static final Flag<Boolean> NETWORK_INSPECTOR_GRPC = Flag.create(
    NETWORK_INSPECTOR, "grpc", "Track gRPC Connections",
    "Track gRPC Connections",
    true
  );
  public static final Flag<Boolean> NETWORK_INSPECTOR_COPY_AS_CURL = Flag.create(
    NETWORK_INSPECTOR, "copy.as.curl",
    "Copy as a cURL command",
    "Copy as a cURL command",
    false
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

  public static final Flag<Boolean> VIRTUAL_DEVICE_WATCHER_ENABLED = Flag.create(
    DEVICE_MANAGER,
    "virtual.device.watcher.enabled",
    "Enable VirtualDeviceWatcher",
    "Enable VirtualDeviceWatcher to update the Virtual table based on disk changes",
    true);

  public static final Flag<Boolean> UNIFIED_DEVICE_MANAGER_ENABLED = Flag.create(
    DEVICE_MANAGER,
    "unified.device.manager.enabled",
    "Enable unified device manager",
    "Enable new Device Manager UI with unified device list",
    true);

  public static final Flag<Boolean> DUAL_DEVICE_MANAGER_ENABLED = Flag.create(
    DEVICE_MANAGER,
    "dual.device.manager.enabled",
    "Enable unified device manager alongside existing device manager",
    "Continue showing original Device Manager even with unified device manager enabled",
    false);
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

  // region Firebase Test Lab
  private static final FlagGroup FIREBASE_TEST_LAB = new FlagGroup(FLAGS, "firebasetestlab", "Firebase Test Lab");

  // TODO(b/304622231) deprecate StudioFlags.DIRECT_ACCESS
  public static final Flag<Boolean> DIRECT_ACCESS =
    Flag.create(
      FIREBASE_TEST_LAB,
      "direct.access",
      "Direct Access",
      "Enable FTL DirectAccess",
      false);

  public static final Flag<Boolean> DIRECT_ACCESS_SETTINGS_PAGE =
    Flag.create(
      FIREBASE_TEST_LAB,
      "direct.access.settings.page",
      "Device Streaming Settings Page",
      "Show Device Streaming Settings Page",
      false);

  public static final Flag<Boolean> DIRECT_ACCESS_ADD_DEVICE =
    Flag.create(
      FIREBASE_TEST_LAB,
      "direct.access.add.device",
      "Direct Access Add Device",
      "Enable the new FTL DirectAccess Add Device workflow.",
      true);

  public static final Flag<String> DIRECT_ACCESS_ENDPOINT =
    Flag.create(
      FIREBASE_TEST_LAB,
      "direct.access.endpoint",
      "FTL Direct Access endpoint",
      "The URL for FTL Direct Access to connect to, in host:port form (with no protocol specified).",
      "testing.googleapis.com"
    );

  public static final Flag<String> DIRECT_ACCESS_MONITORING_ENDPOINT =
    Flag.create(
      FIREBASE_TEST_LAB,
      "direct.access.monitoring.endpoint",
      "FTL Direct Access Monitoring endpoint",
      "The URL for FTL Direct Access to monitor quota usage and limit.",
      "monitoring.googleapis.com"
    );
  // endregion Firebase Test Lab

  // region App Insights
  private static final FlagGroup APP_INSIGHTS = new FlagGroup(FLAGS, "appinsights", "App Insights");

  public static final Flag<Boolean> APP_INSIGHTS_CHANGE_AWARE_ANNOTATION_SUPPORT =
    Flag.create(
      APP_INSIGHTS,
      "insights.change.aware.annotation",
      "Change-aware Annotation Support",
      "Enhance annotation to aid crash investigation with the recorded VCS info",
      true);

  public static final Flag<Boolean> APP_INSIGHTS_VCS_SUPPORT =
    Flag.create(
      APP_INSIGHTS,
      "insights.vcs",
      "VCS Support",
      "Enhance code navigation to aid crash investigation with the recorded VCS info",
      true);

  public static final Flag<String> CRASHLYTICS_GRPC_SERVER =
    Flag.create(
      APP_INSIGHTS,
      "crashlytics.grpc.server",
      "Set Crashlytics gRpc server address",
      "Set Crashlytics gRpc server address, mainly used for testing purposes.",
      "firebasecrashlytics.googleapis.com");

  public static final Flag<Boolean> CRASHLYTICS_INTEGRATION_TEST_MODE =
    Flag.create(
      APP_INSIGHTS,
      "crashlytics.integration.test.mode",
      "Crashlytics Integration Test Mode",
      "Set Crashlytics to be in integration test mode.",
      false);

  public static final Flag<Boolean> CRASHLYTICS_VARIANTS =
    Flag.create(
      APP_INSIGHTS,
      "crashlytics.variants",
      "Crashlytics Variants Support",
      "Enabled Variant Selection in AQI Crashlytics",
      true
    );

  public static final Flag<Boolean> CRASHLYTICS_J_UI =
    Flag.create(
      APP_INSIGHTS,
      "crashlytics.2023h2.ui",
      "Crashlytics UI changes for J",
      "Enabled Logs & Keys, Multi-event",
      false
    );

  public static final Flag<Boolean> PLAY_VITALS_ENABLED =
    Flag.create(
      APP_INSIGHTS,
      "enable.play.vitals",
      "Enable the play vitals tool window tab.",
      "Enables the play vitals tab and its associated functionality.",
      true);

  public static final Flag<String> PLAY_VITALS_GRPC_SERVER =
    Flag.create(
      APP_INSIGHTS,
      "play.vitals.grpc.server",
      "Set Play Vitals gRpc server address",
      "Set Play Vitals gRpc server address, mainly used for testing purposes.",
      "playdeveloperreporting.googleapis.com");

  public static final Flag<Boolean> PLAY_VITALS_GRPC_USE_TRANSPORT_SECURITY =
    Flag.create(
      APP_INSIGHTS,
      "play.vitals.grpc.use.transport.security",
      "Use transport security",
      "Set Play Vitals gRpc channel to use transport security",
      true);

  public static final Flag<Boolean> PLAY_VITALS_VCS_INTEGRATION_ENABLED =
    Flag.create(
      APP_INSIGHTS,
      "play.vitals.vcs.integration",
      "Enable VCS integration for Play Vitals.",
      "Enhance code navigation in the Play Vitals tab to aid crash investigation with the recorded VCS info",
      true);
  // endregion App Insights

  // region App Links Assistant
  private static final FlagGroup APP_LINKS_ASSISTANT = new FlagGroup(FLAGS, "app.links.assistant", "App Links Assistant");
  public static final Flag<Boolean> WEBSITE_ASSOCIATION_GENERATOR_V2 =
    Flag.create(APP_LINKS_ASSISTANT, "website.association.generator.v2", "Website Association Generator V2",
                "Improvements to Website Association Generator.", false);
  public static final Flag<String> DEEPLINKS_GRPC_SERVER =
    Flag.create(APP_LINKS_ASSISTANT, "deeplinks.grpc.server", "Deep links gRPC server address",
                "Deep links gRPC server address. Use a non-default value for testing purposes.",
                "deeplinkassistant-pa.googleapis.com");
  public static final Flag<Boolean> CREATE_APP_LINKS_V2 =
    Flag.create(APP_LINKS_ASSISTANT, "create.app.links.v2", "Create App Links V2",
                "Improvements to the Create App Links functionalities.", false);
  public static final Flag<Boolean> IMPACT_TRACKING =
    Flag.create(APP_LINKS_ASSISTANT, "app.links.assistant.impact.tracking", "App Links Assistant impact tracking",
                "Impact tracking for the App Links Assistant", false);
  // endregion App Links Assistant

  // region GOOGLE_PLAY_SDK_INDEX
  private static final FlagGroup GOOGLE_PLAY_SDK_INDEX = new FlagGroup(FLAGS, "google.play.sdk.index", "Google Play SDK Index");
  public static final Flag<Boolean> SHOW_SDK_INDEX_POLICY_ISSUES = Flag.create(
    GOOGLE_PLAY_SDK_INDEX, "show.sdk.policy.issues", "Show SDK Index policy issues",
    "Whether or not SDK Index policy issues should be shown",
    true
  );
  // endregion GOOGLE_PLAY_SDK_INDEX

  // region NEW_COLLECT_LOGS_DIALOG
  private static final FlagGroup NEW_COLLECT_LOGS_DIALOG = new FlagGroup(FLAGS, "new.collect.logs", "New Collect Logs Dialog");
  public static final Flag<Boolean> ENABLE_NEW_COLLECT_LOGS_DIALOG = Flag.create(
    NEW_COLLECT_LOGS_DIALOG, "enable.new.collect.logs.dialog", "Enable new collect logs dialog",
    "Enable the collect logs dialog",
    true
  );
  // endregion NEW_COLLECT_LOGS_DIALOG

  // region TargetSDKVersion Upgrade Assistant
  private static final FlagGroup TSDKVUA = new FlagGroup(FLAGS, "tsdkvua", "Android SDK Upgrade Assistant");
  public static final Flag<Boolean> TSDKVUA_FILTERS_ONSTART = Flag.create(TSDKVUA, "filters.onstart", "Run filters on assistant startup", "Run filters on assistant startup", true);
  public static final Flag<Boolean> TSDKVUA_FILTERS_ONSTART_RESET = Flag.create(TSDKVUA, "filters.onstart.reset", "Reset the results cache before running filters on startup", "Reset the results cache before running filters on startup", true);
  public static final Flag<Boolean> TSDKVUA_FILTERS_WIP = Flag.create(TSDKVUA, "filters.wip", "Enable WIP relevance filters", "Enable WIP relevance filters", false);
  public static final Flag<Boolean> TSDKVUA_FILTERS_REDOABLE = Flag.create(TSDKVUA, "filters.redoable", "Enable button to rerun a filter and display results", "Enable button to rerun a filter an display results", true);
  public static final Flag<Boolean> TSDKVUA_API_34 = Flag.create(TSDKVUA, "api34", "Enable support for API 34", "Enable support for API 34", true);
  // endregion TargetSDKVersion Upgrade Assistant

  // region PROCESS_NAME_MONITOR
  private static final FlagGroup PROCESS_NAME_MONITOR = new FlagGroup(FLAGS, "processnamemonitor", "Process Name Monitor");
  public static final Flag<Integer> PROCESS_NAME_MONITOR_MAX_RETENTION = Flag.create(
    PROCESS_NAME_MONITOR, "processnamemonitor.max.retention", "Set max process retention",
    "Maximum number of processes to retain after they are terminated. Changing the value of this flag requires restarting Android Studio.",
    100
  );
  public static final Flag<Boolean> PROCESS_NAME_TRACKER_AGENT_ENABLE = Flag.create(
    PROCESS_NAME_MONITOR, "processnamemonitor.tracker.agent.enable", "Enable process tracking agent",
    "Enable process tracking using an agent deployed to the device. Changing the value of this flag requires restarting Android Studio.",
    true
  );
  public static final Flag<Integer> PROCESS_NAME_TRACKER_AGENT_INTERVAL_MS = Flag.create(
    PROCESS_NAME_MONITOR, "processnamemonitor.tracker.agent.interval", "Process tracking agent polling interval",
    "Process tracking agent polling interval in milliseconds. Changing the value of this flag requires restarting Android Studio.",
    1000
  );
  public static final Flag<Boolean> PROCESS_NAME_MONITOR_ADBLIB_ENABLED = Flag.create(
    PROCESS_NAME_MONITOR, "processnamemonitor.adblib.enable", "Enable Adblib monitor",
    "Enable the Adblib version of the process name monitor. " +
    "Note that adblib process tracking can not work concurrently with ddmlib process tracking because only one concurrent JDWP " +
    "session can be open per process per device. Therefore, this feature is only enabled if the flag " +
    "ADBLIB_MIGRATION_DDMLIB_CLIENT_MANAGER is also true. " +
    "Changing the value of this flag requires restarting Android Studio.",
    true
  );
  // endregion NEW_SEND_FEEDBACK_DIALOG

  // region AVD Command Line Options
  private static final FlagGroup
    AVD_COMMAND_LINE_OPTIONS = new FlagGroup(FLAGS, "avd.command.line.options", "AVD Command-Line Options");
  public static final Flag<Boolean> AVD_COMMAND_LINE_OPTIONS_ENABLED = Flag.create(
    AVD_COMMAND_LINE_OPTIONS, "enable", "Enable the AVD Command-Line Options setting",
    "Enable the AVD Command-Line Options setting in the AVD advanced settings panel.",
    false
  );
  // endregion

  // region PRIVACY_SANDBOX_SDK
  private static final FlagGroup PRIVACY_SANDBOX_SDK = new FlagGroup(FLAGS, "privacysandboxsdk", "Privacy Sandbox SDK");
  public static final Flag<Boolean> LAUNCH_SANDBOX_SDK_PROCESS_WITH_DEBUGGER_ATTACHED_ON_DEBUG = Flag.create(
    PRIVACY_SANDBOX_SDK, "launch.process.with.debugger.attached.on.debug", "Launch sandbox SDK process with debugger attached on debug",
    "Whether or not sandbox SDK should launch a process with the debugger attached on debug action.",
    false);
  // endregion PRIVACY_SANDBOX_SDK

  public static Boolean isBuildOutputShowsDownloadInfo() {
    return BUILD_OUTPUT_DOWNLOADS_INFORMATION.isOverridden()
           ? BUILD_OUTPUT_DOWNLOADS_INFORMATION.get()
           : isAndroidStudio();
  }

  private static boolean isAndroidStudio() {
    return "AndroidStudio".equals(getPlatformPrefix());
  }

  private static String getFullProductName() {
    return StudioPathManager.isRunningFromSources() ? "IntelliJ IDEA" : ApplicationNamesInfo.getInstance().getFullProductName();
  }

  // region STUDIO_BOT
  private static final FlagGroup STUDIOBOT = new FlagGroup(FLAGS, "studiobot", "Studio Bot");
  public static final Flag<Boolean> STUDIOBOT_ENABLED =
    Flag.create(STUDIOBOT, "enabled", "Enable Studio Bot", "Enable Studio Bot Tool Window", false);

  public static final Flag<Boolean> STUDIOBOT_INLINE_CODE_COMPLETION_ENABLED =
    new BooleanFlag(STUDIOBOT, "inline.code.completion.enabled", "Enable inline code completion",
                    "When enabled, inline code completion suggestions will be shown.",
                    false);

  public static final Flag<Boolean> STUDIOBOT_INLINE_CODE_COMPLETION_CES_TELEMETRY_ENABLED =
    Flag.create(STUDIOBOT, "inline.code.completion.ces.telemetry.enabled",
                "Enable sending inline code completion metrics to the AIDA CES service",
                "When enabled, metrics related to inline code completion suggestions will be sent to the CES service for AIDA.", false);
  // endregion STUDIO_BOT

  // region EXPERIMENTAL_UI
  private static final FlagGroup EXPERIMENTAL_UI = new FlagGroup(FLAGS, "experimentalui", "Experimental UI");
  public static final Flag<Boolean> EXPERIMENTAL_UI_SURVEY_ENABLED =
    Flag.create(EXPERIMENTAL_UI, "enabled", "Enable Experimental UI Survey", "Enable the experimental UI survey.", true);
  // endregion EXPERIMENTAL_UI

  // region WEAR_RUN_CONFIGS_AUTOCREATE
  private static final FlagGroup WEAR_RUN_CONFIGS_AUTOCREATE =
    new FlagGroup(FLAGS, "wear.runconfigs.autocreate", "Autocreate Wear Run Configs");
  public static final Flag<Boolean> WEAR_RUN_CONFIGS_AUTOCREATE_ENABLED =
    Flag.create(WEAR_RUN_CONFIGS_AUTOCREATE, "enabled", "Enable Autocreate Wear Run Configs",
                "When enabled, Wear run configurations will be automatically created.", true);
  public static final Flag<Integer> WEAR_RUN_CONFIGS_AUTOCREATE_MAX_TOTAL_RUN_CONFIGS =
    Flag.create(WEAR_RUN_CONFIGS_AUTOCREATE, "max.total.runconfigs", "Maximum total run configurations",
                "Maximum total number of all types of run configurations that can be reached after autocreating Wear Run Configs. Wear Run Configurations will not be created if this limit is breached.",
                10);
  // endregion WEAR_RUN_CONFIGS_AUTOCREATE

  // region GOOGLE_LOGIN
  private static final FlagGroup GOOGLE_LOGIN =
    new FlagGroup(FLAGS, "google.login", "Google Login");
  public static final Flag<Boolean> ENABLE_SETTINGS_ACCOUNT_UI =
    Flag.create(GOOGLE_LOGIN, "enabled", "Enable new login settings UI",
                "When enabled, a login settings page will replace the popup from the login action in the top right.", false);
  // endregion GOOGLE_LOGIN

  private StudioFlags() { }
}
