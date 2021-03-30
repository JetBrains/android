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
import com.android.tools.idea.flags.overrides.ServerFlagOverrides;
import com.android.tools.idea.util.StudioPathManager;
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
    return new Flags(userOverrides, new PropertyOverrides(), new ServerFlagOverrides());
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

  public static final Flag<Boolean> NPW_SHOW_JDK_STEP = Flag.create(
    NPW, "first.run.jdk.step", "Show JDK setup step",
    "Show JDK Setup Step in Welcome Wizard",
    true);

  public static final Flag<Boolean> NPW_SHOW_FRAGMENT_GALLERY = Flag.create(
    NPW, "show.fragment.gallery", "Show fragment gallery",
    "Show fragment gallery which contains fragment based templates",
    true);

  public static final Flag<Boolean> NPW_SHOW_GRADLE_KTS_OPTION = Flag.create(
    NPW, "show.gradle.kts.option", "Show gradle kts option",
    "Shows an option on new Project/Module to allow the use of Kotlin script",
    true);

  public static final Flag<Boolean> NPW_NEW_NATIVE_MODULE = Flag.create(
    NPW, "new.native.module", "New Android Native Module",
    "Show template to create a new Android Native module in the new module wizard.",
    true);
  //endregion

  //region Profiler
  private static final FlagGroup PROFILER = new FlagGroup(FLAGS, "profiler", "Android Profiler");

  public static final Flag<Boolean> PROFILER_CPU_CAPTURE_STAGE = Flag.create(
    PROFILER, "cpu.capture.stage", "Enable new capture stage",
    "With the new System Trace design we have a cpu capture stage. This flag uses that flow instead of the legacy " +
    "CpuProfilerStageView flow.",
    true);

  public static final Flag<Boolean> PROFILER_ENABLE_NATIVE_SAMPLE = Flag.create(
    PROFILER, "memory.heapprofd", "Enable heapprofd captures in the memory profiler.",
    "Toggles if users can capture heapprofd recordings in the memory profiler. This gates mostly the UI and importing of traces. " +
    "The perfd functionality is not gated. This feature has a dependency on the trace processor.",
    true);

  public static final Flag<Boolean> PROFILER_UNIFIED_PIPELINE = Flag.create(
    PROFILER, "unified.pipeline", "Enables new event pipeline to be used for core components.",
    "Toggles usage of gRPC apis to fetch data from perfd and the datastore.",
    true);

  public static final Flag<Boolean> PROFILER_ENERGY_PROFILER_ENABLED = Flag.create(
    PROFILER, "energy", "Enable Energy profiling",
    "Enable the new energy profiler. It monitors battery usage of the selected app.", true);

  public static final Flag<Boolean> PROFILER_STARTUP_CPU_PROFILING = Flag.create(
    PROFILER, "startup.cpu.profiling", "Enable startup CPU Profiling",
    "Record a method trace on startup by enabling it in the Profiler tab of Run/Debug configuration.",
    true);

  public static final Flag<Boolean> PROFILER_USE_LIVE_ALLOCATIONS = Flag.create(
    PROFILER, "livealloc", "Enable JVMTI-based live allocation tracking",
    "For Android O or newer, allocations are tracked all the time while inside the Memory Profiler.",
    true);

  public static final Flag<Boolean> PROFILER_MEMORY_SNAPSHOT = Flag.create(
    PROFILER, "memory.livealloc.snapshot", "Enable Memory Class Histogram Display",
    "For Android O or newer, supports single-point selection which shows a snapshot of the heap at the specific time.",
    true);

  public static final Flag<Boolean> PROFILER_MEMORY_CSV_EXPORT = Flag.create(
    PROFILER, "memory.csv", "Allow exporting entries in memory profiler",
    "Allow exporting entries in the views for heap dump and native/JVM recordings in CSV format.",
    false);

  public static final Flag<Boolean> PROFILER_SAMPLE_LIVE_ALLOCATIONS = Flag.create(
    PROFILER, "memory.livealloc.sampled", "Enable Sampled Live Allocation Tracking",
    "For Android O or newer, allows users to configure the sampling mode of live allocation tracking",
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

  public static final Flag<Boolean> PROFILER_CPU_NEW_RECORDING_WORKFLOW = Flag.create(
    PROFILER, "cpu.new.recording.workflow", "Enable new CPU recording workflow",
    "Shows recording options and status of the ongoing recording in the method trace pane.",
    true
  );

  public static final Flag<Boolean> PROFILER_CUSTOM_EVENT_VISUALIZATION = Flag.create(
    PROFILER, "custom.event.visualization", "Enable Profiler Custom Event Visualization",
    "When enabled, profiler will track and display events defined through developer APIs",
    false);

  public static final Flag<Boolean> PROFILER_USE_TRACEPROCESSOR = Flag.create(
    PROFILER, "perfetto.traceprocessor", "Enable TraceProcessorDaemon",
    "Use TraceProcessor to parse Perfetto captures instead of Trebuchet/Atrace backend.",
    true);

  public static final Flag<Boolean> PROFILEABLE = Flag.create(
    PROFILER, "profileable", "Support profileable processes on S+",
    "Show profileable processes on S and later",
    false
  );
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
  public static final Flag<Boolean> NELE_MOTION_LAYOUT_ANIMATIONS = Flag.create(
    NELE, "animated.motion.layout", "Show preview animations toolbar for MotionLayout",
    "Show an animations bar that allows playback of MotionLayout animations.",
    false);
  public static final Flag<Boolean> NELE_MOTION_LAYOUT_EDITOR = Flag.create(
    NELE, "animated.motion.editor", "Show motion editor for MotionLayout",
    "Show the motion editor UI for MotionLayout.",
    true);
  public static final Flag<Boolean> NELE_CONSTRAINT_SELECTOR = Flag.create(
    NELE, "constraint.selection", "Allow selection of Constraints",
    "Allow the selection of constraints.",
    true);
  public static final Flag<Boolean> NELE_MOTION_HORIZONTAL = Flag.create(
    NELE, "animated.motion.horizontal", "Display motion editor horizontally",
    "Controls the placement of the motion editor (horizontal versus vertical).",
    false);
  public static final Flag<Boolean> NELE_MOCKUP_EDITOR = Flag.create(
    NELE, "mockup.editor", "Enable the Mockup Editor",
    "Enable the Mockup Editor to ease the creation of Layouts from a design file.",
    false);

  public static final Flag<Boolean> NELE_CONVERT_VIEW = Flag.create(
    NELE, "convert.view", "Enable the Convert View action",
    "Enable the Convert View Action when right clicking on a component",
    true);

  public static final Flag<Boolean> NELE_RENDER_DIAGNOSTICS = Flag.create(
    NELE, "diagnostics", "Enable rendering on-screen stats",
    "If enabled, the surface displays some debug information to diagnose performance",
    false);

  public static final Flag<Boolean> NELE_COLOR_RESOURCE_PICKER_FOR_FILE_EDITORS = Flag.create(
    NELE, "editor.color.picker", "Enable popup color resource picker for java and kotlin files.",
    "Show the popup color resource picker when clicking the gutter icon of color resource in java and kotlin files.",
    true);

  public static final Flag<Boolean> NELE_DRAWABLE_POPUP_PICKER = Flag.create(
    NELE, "show.drawable.popup.picker", "Enable drawable popup picker in Xml Editor.",
    "Show the resource popup picker for picking drawable resources from the Editor's gutter icon.",
    true);

  public static final Flag<Boolean> NELE_LOG_ANDROID_FRAMEWORK = Flag.create(
    NELE, "log.android.framework", "Log messages coming from Layoutlib Native.",
    "Log in the IDEA log the messages coming from Java and native code of Layoutlib Native.",
    false);

  public static final Flag<Boolean> NELE_SCENEVIEW_TOP_TOOLBAR = Flag.create(
    NELE, "sceneview.top.toolbar", "Enable the per SceneView top toolbar.",
    "Enable the per SceneView top toolbar that displays the SceneView contextual actions.",
    true);

  public static final Flag<Boolean> NELE_SCENEVIEW_BOTTOM_BAR = Flag.create(
    NELE, "sceneview.bottom.bar", "Enable the per SceneView bottom bar.",
    "Enable the per SceneView bottom bar that displays the SceneView contextual actions.",
    false);

  public static final Flag<Boolean> NELE_SCENEVIEW_LEFT_BAR = Flag.create(
    NELE, "sceneview.left.bar", "Enable SceneView left bar for overlay actions.",
    "Enable the SceneView left bar that displays the overlay actions.",
    true);

  public static final Flag<Boolean> NELE_SHOW_LAYOUTLIB_LEGACY = Flag.create(
    NELE, "hide.layoutlib.legacy", "Show the legacy version of Layoutlib.",
    "Show users ways of reverting to the legacy version of Layoutlib.",
    false);


  private static final FlagGroup ASSISTANT = new FlagGroup(FLAGS, "assistant", "Assistants");
  public static final Flag<Boolean> CONNECTION_ASSISTANT_ENABLED = Flag.create(
    ASSISTANT, "connection.enabled", "Enable the connection assistant",
    "If enabled, user can access the Connection Assistant under \"Tools\" and \"Deploy Target Dialog\"",
    true);

  public static final Flag<Boolean> NELE_CONSTRAINT_LAYOUT_ASSISTANT = Flag.create(
    ASSISTANT, "layout.editor.help.constraintlayout", "Display Help for Constraint Layout",
    "If enabled, the assistant panel will display helpful guide on using Constraint Layout.",
    true);

  public static final Flag<Boolean> NELE_MOTION_LAYOUT_ASSISTANT = Flag.create(
    ASSISTANT, "layout.editor.help.motionlayout", "Display Help for Motion Layout",
    "If enabled, the assistant panel will display helpful guide on using Motion Layout.",
    true);

  public static final Flag<Boolean> NELE_NAV_EDITOR_ASSISTANT = Flag.create(
    ASSISTANT, "layout.editor.help.naveditor", "Display Help for Navigation Editor",
    "If enabled, the assistant panel will display helpful guide on using the Navigation Editor.",
    true);

  public static final Flag<Boolean> NELE_DRAG_PLACEHOLDER = Flag.create(
    NELE, "drag.placeholder", "Dragging widgets with Placeholders",
    "New architecture for dragging widgets in Layout Editor",
    true);

  public static final Flag<Boolean> NELE_PROPERTY_PANEL_ACTIONBAR = Flag.create(
    NELE, "property.panel.actionbar", "Property Panel Actionbar",
    "Support Actionbar in property panel",
    false);

  public static final Flag<Boolean> NELE_VISUALIZATION_LOCALE_MODE = Flag.create(
    NELE, "visualization.locale", "Locale Mode in Layout Validation Tool",
    "Enable locale mode in Layout Validation Tool to preview layout in project's locales",
    true);

  public static final Flag<Boolean> NELE_SOURCE_CODE_EDITOR = Flag.create(
    NELE, "show.source.code.editor", "New Source Code Editor",
    "Enable new source code editor with preview(s) coming as a substitute to Compose and Custom View editors.",
    true);

  public static final Flag<Boolean> NELE_TOGGLE_TOOLS_ATTRIBUTES_IN_PREVIEW = Flag.create(
    NELE, "toggle.tools.attributes.preview", "New Toggle for Tools namespaces attributes",
    "Enable the new toggle in the Layout Editor. Allows toggling tools attributes in the Layout preview.",
    true);

  public static final Flag<Boolean> NELE_SHOW_RECYCLER_VIEW_SETUP_WIZARD = Flag.create(
    NELE, "recyclerview.setup.wizard", "Show setup wizard for recycler view",
    "When you right click recycler view in layout editor, you can now see \"Generate Adapter\" " +
    "that takes you through setup wizard",
    false);

  public static final Flag<Boolean> NELE_CUSTOM_SHORTCUT_KEYMAP = Flag.create(
    NELE, "custom.shortcut.keymap", "Design Tool Custom Shortcut",
    "Make the shortcuts of design tools configurable. The shortcut keymap can be changed in Preferences -> Keymap -> Android Design" +
    " Tools",
    true
  );

  public static final Flag<Boolean> NELE_LAYOUT_SCANNER_IN_EDITOR = Flag.create(
    NELE, "toggle.layout.editor.validator.a11y", "Toggle layout validator for layout editor.",
    "When the model changes, layout editor will run the series of layout validations and update lint output",
    true);

  public static final Flag<Boolean> NELE_LAYOUT_SCANNER_ADD_INCLUDE = Flag.create(
    NELE, "toggle.layout.editor.validator.a11y.include", "Toggle whether to show included layout or not.",
    "If the layout contains <include>, turning this flag on will run the scanner in the included layout.",
    false);

  public static final Flag<Boolean> NELE_TRANSFORM_PANEL = Flag.create(
    NELE, "toggle.layout.editor.transform.panel", "Toggle transform panel in layout editor and motion editor.",
    "Enable the new transform panel in the layout editor and motion editor",
    true);

  public static final Flag<Boolean> NELE_TRANSITION_PANEL = Flag.create(
    NELE, "toggle.layout.editor.transition.panel", "Toggle transition panel in motion editor.",
    "Enable the new transition panel in the motion editor",
    true);

  public static final Flag<Boolean> NELE_OVERLAY_PROVIDER = Flag.create(
    NELE, "toggle.overlay.provider.extension.point", "Toggle overlay provider extension point.",
    "Enable the overlay provider extension point",
    true);

  public static final Flag<Boolean> NELE_CLASS_BINARY_CACHE = Flag.create(
    NELE, "toggle.layout.editor.class.binary.cache", "Enable binary cache",
    "Enable binary cache of classes used in preview",
    true);

  public static final Flag<Boolean> NELE_STATE_LIST_PICKER = Flag.create(
    NELE, "state.list.picker", "Enable State List Picker",
    "Enable state list picker for selector drawable.",
    true);

  public static final Flag<Boolean> NELE_ASSET_REPOSITORY_INCLUDE_AARS_THROUGH_PROJECT_SYSTEM = Flag.create(
    NELE, "asset.repository.include.aars.through.project.system", "Include AARs through project system",
    "Include resource directories from AARs found through project system.",
    false);

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

  //region Run/Debug
  private static final FlagGroup RUNDEBUG = new FlagGroup(FLAGS, "rundebug", "Run/Debug");
  public static final Flag<Boolean> RUNDEBUG_LOGCAT_CONSOLE_OUTPUT_ENABLED = Flag.create(
    RUNDEBUG, "logcat.console.output.enabled", "Show logcat process output in Run/Debug console window",
    "When running or debugging an Android process, output the logcat output of the process in the console window.",
    true);

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

    /**
     * The level of APK change that will be supported by the deployment pipeline's optimistic
     * "deploy-without-installing" path. Deploying changes that exceed the level of support
     * configured here will cause the deployment to install via the package manager.
     */
    public enum OptimisticInstallSupportLevel {
        /** Always fall back to a package manager installation. */
        DISABLED,
        /** Support deploying changes to dex files only. */
        DEX,
        /** Support deploying changes to dex files and native libraries only. */
        DEX_AND_NATIVE,
        /** Support deploying changes to dex files, native libraries, and resources. */
        DEX_AND_NATIVE_AND_RESOURCES,
    }

    public static final Flag<OptimisticInstallSupportLevel> OPTIMISTIC_INSTALL_SUPPORT_LEVEL =
            Flag.create(
                    RUNDEBUG,
                    "optimisticinstall.supportlevel",
                    "The amount of support for using the 'Apply Changes 2.0' pipeline on Run.",
                    "This can be \"DISABLED\" to always use a package manager installation; \"DEX\" to use the pipeline for dex-only changes; \"DEX_AND_NATIVE\" to use the pipeline for dex and native library-only changes; or \"DEX_AND_NATIVE_AND_RESOURCES\" to use the pipeline for changes to dex, native libraries, and/or resource/asset files. Deploying changes that exceed the level of support configured here will cause the deployment to install via the package manager.",
                    OptimisticInstallSupportLevel.DEX_AND_NATIVE);

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

  public static final Flag<Boolean> APPLY_CHANGES_FAST_RESTART_ON_SWAP_FAIL = Flag.create(
    RUNDEBUG,
    "applychanges.swap.fastrestartonswapfail",
    "Allow fast restart on swap failure.",
    "Eliminate the need to build again when auto re-run checkbox is turned on.",
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

  public static final Flag<Boolean> RUN_ON_MULTIPLE_DEVICES_ACTION_ENABLED = Flag.create(
    RUNDEBUG,
    "run.on.multiple.devices.action.enabled",
    "Enable the Run on Multiple Devices action",
    "To revert to the 4.0 behavior until multiple devices are properly supported for the other executors",
    false);

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

  public static final Flag<Boolean> WEAR_DEVICE_PAIRING_ENABLED = Flag.create(
    RUNDEBUG,
    "wear.device.pairing.enabled",
    "Enable Wear emulator pairing assistant",
    "Show the Wear emulator pairing assistant",
    true);

  public static final Flag<Boolean> ADB_SERVER_MANAGEMENT_MODE_SETTINGS_VISIBLE = Flag.create(
    RUNDEBUG,
    "adb.server.management.mode.settings.visible",
    "Show ADB server management mode settings",
    "To allow toggling between automatic or user managed ADB server mode.",
    false);

  /**
   * The strategy that {@link com.android.tools.idea.run.activity.DefaultActivityLocator}
   * uses to obtain a list of activities from a given module's merged manifest.
   *
   * @see StudioFlags#DEFAULT_ACTIVITY_LOCATOR_STRATEGY
   */
  public enum DefaultActivityLocatorStrategy {
    /**
     * Unconditionally block on a fresh view of the merged manifest.
     */
    BLOCK,
    /**
     * Determine the list of activities using the {@link com.android.tools.idea.model.AndroidManifestIndex}.
     */
    INDEX,
    /**
     * Use a potentially stale view of the merged manifest if the caller is on the EDT.
     */
    STALE
  }

  public static final Flag<DefaultActivityLocatorStrategy> DEFAULT_ACTIVITY_LOCATOR_STRATEGY = Flag.create(
    RUNDEBUG,
    "default.activity.locator.strategy",
    "Choose a strategy for selecting the default activity to launch from the merged manifest.",
    "This can be \"BLOCK\" to unconditionally block on a fresh merged manifest, \"STALE\" to use a potentially stale manifest, "
    + "or \"INDEX\" to use the custom Android Manifest index (only select this option if manifest indexing is enabled).",
    DefaultActivityLocatorStrategy.INDEX
  );

  public static final Flag<Boolean> DEFAULT_ACTIVITY_LOCATOR_FROM_APKS = Flag.create(
    RUNDEBUG,
    "default.activity.locator.sourceoftruth",
    "Use APKs as source of truth",
    "Open APK and parse the manifest in order to discover default activity.",
    true);

  public static final Flag<Boolean> SUPPORT_FEATURE_ON_FEATURE_DEPS = Flag.create(
    RUNDEBUG,
    "feature.on.feature",
    "Enable feature-on-feature dependencies",
    "Enables Studio to understand feature-on-feature dependencies when launching dynamic apps.",
    false
  );
  //endregion

  //region Gradle Project System
  private static final FlagGroup GRADLE_IDE = new FlagGroup(FLAGS, "gradle.ide", "Gradle Project System");
  public static final Flag<Boolean> FIX_ANDROID_RUN_CONFIGURATIONS_ENABLED = Flag.create(
    GRADLE_IDE, "gradle.run.configuration.fix.enabled",
    "Check Android Run Configurations contains the \"Gradle-aware Make\" task and fix them",
    "When a project is loaded, automatically add a \"Gradle-aware Make\" task to each Run Configuration if the task is missing",
    true);

  public static final Flag<Boolean> NEW_PSD_ENABLED = Flag.create(
    GRADLE_IDE, "new.psd", "Enable new \"Project Structure\" dialog",
    "Turns on the new \"Project Structure\" dialog.", true);
  public static final Flag<Boolean> USE_DEVELOPMENT_OFFLINE_REPOS = Flag.create(
    GRADLE_IDE, "development.offline.repos", "Enable development offline repositories",
    "Makes Gradle use development offline repositories such as /out/repo", StudioPathManager.isRunningFromSources());
  public static final Flag<Boolean> BUILD_ATTRIBUTION_ENABLED = Flag.create(
    GRADLE_IDE, "build.attribution", "Enable build attribution",
    "Enable build attribution.", true);
  public static final Flag<Boolean> AGP_UPGRADE_ASSISTANT = Flag.create(
    GRADLE_IDE, "agp.upgrade.assistant", "Enable AGP Upgrade Assistant",
    "Enable the Upgrade Assistant for helping with AGP upgrades", true);
  public static final Flag<Boolean> AGP_UPGRADE_ASSISTANT_TOOL_WINDOW = Flag.create(
    GRADLE_IDE, "agp.upgrade.assistant.tool.window", "Enable the AGP Upgrade Assistant Tool Window",
    "Enable Tool Window-oriented interaction with the AGP Upgrade Assistant", true);
  public static final Flag<Boolean> DISABLE_FORCED_UPGRADES = Flag.create(
    GRADLE_IDE, "forced.agp.update", "Disable forced Android Gradle plugin upgrades",
    "This option is only respected when running Android Studio internally.", false);
  public static final Flag<Boolean> USE_MODULE_PER_SOURCE_SET = Flag.create(
    GRADLE_IDE, "module.per.source.set", "Enables creating multiple modules per Gradle project",
    "This allows the IDE to more closely represent how the project is configured in Gradle.", false);

  public static final Flag<Boolean> GRADLE_SYNC_PARALLEL_SYNC_ENABLED = Flag.create(
    GRADLE_IDE, "gradle.sync.parallel.sync.enabled", "Enables parallel sync",
    "This allows the IDE to fetch models in parallel (if supported by Gralde and enabled via org.gradle.parallel=true).", false);

  public static final Flag<Boolean> GRADLE_SYNC_PARALLEL_SYNC_PREFETCH_VARIANTS = Flag.create(
    GRADLE_IDE, "gradle.sync.parallel.sync.prefetch.variants", "Enables speculative syncing of current variants",
    "This allows the IDE to pre-fetch models for the currently selected variants in parallel before resolving the " +
    "new variant selection (which is less parallelizable process).", false);

  public static final Flag<Boolean> ALLOW_DIFFERENT_JDK_VERSION = Flag.create(
    GRADLE_IDE, "jdk.allow.different", "Allow different Gradle JDK", "Allow usage of a different JDK version when running Gradle.", true);

  public static final Flag<Boolean> ALLOW_JDK_PER_PROJECT = Flag.create(
    GRADLE_IDE, "jdk.allow.jdk.per.project", "Allow Gradle JDK per project", "Allows setting JDK per project.", true);

  public static final Flag<Boolean> SHOW_JDK_PATH = Flag.create(
    GRADLE_IDE, "jdk.show.path", "Show JDK path in settings", "Shows JDK path for each item in Gradle settings.", true);
  //endregion

  //region Database Inspector
  private static final FlagGroup DATABASE_INSPECTOR = new FlagGroup(FLAGS, "database.inspector", "Database Inspector");
  public static final Flag<Boolean> DATABASE_INSPECTOR_ENABLED = Flag.create(
    DATABASE_INSPECTOR,
    "enabled",
    "Enable Database Inspector",
    "If enabled the Database Inspector tool window will appear." +
    "SQLite files opened from the Device Explorer will be opened in the inspector.",
    true
  );
  public static final Flag<Boolean> DATABASE_INSPECTOR_OPEN_FILES_ENABLED = Flag.create(
    DATABASE_INSPECTOR,
    "open.files.enabled",
    "Enable support for opening SQLite files in Database Inspector",
    "If enabled, the Database Inspector tool will be able to open SQLite files." +
    "eg. SQLite files opened from the Device Explorer will open in the inspector.",
    false
  );
  public static final Flag<Boolean> DATABASE_INSPECTOR_OFFLINE_MODE_ENABLED = Flag.create(
    DATABASE_INSPECTOR,
    "offline.enabled",
    "Enable offline mode in Database Inspector",
    "If enabled, Database Inspector will download a copy of open databases when the connection to the device is lost.",
    true
  );
  public static final Flag<Boolean> DATABASE_INSPECTOR_EXPORT_TO_FILE_ENABLED = Flag.create(
    DATABASE_INSPECTOR,
    "export.to.file.enabled",
    "Enable Export to File feature in Database Inspector",
    "If enabled, Database Inspector will expose an ability for the user to export a table, query results, or the whole database " +
    "to a local file.",
    true
  );
  //endregion

  //region Layout Inspector
  private static final FlagGroup LAYOUT_INSPECTOR = new FlagGroup(FLAGS, "layout.inspector", "Layout Inspector");
  public static final Flag<Boolean> LAYOUT_INSPECTOR_LOAD_OVERLAY_ENABLED = Flag.create(
    LAYOUT_INSPECTOR, "load.overlay", "Enable the Load Overlay feature",
    "If enabled, show actions to let user choose overlay image on preview.", true);
  public static final Flag<Boolean> LAYOUT_INSPECTOR_SUB_VIEW_ENABLED = Flag.create(
    LAYOUT_INSPECTOR, "sub.view", "Enable the sub view feature",
    "If enabled, changes the preview to focus on a component.", true);
  public static final Flag<Boolean> LAYOUT_INSPECTOR_V2_PROTOCOL_ENABLED = Flag.create(
    LAYOUT_INSPECTOR, "capture.v2", "Enable using V2 protocol to capture view data",
    "If enabled, uses V2 protocol to capture view information from device.", false);
  public static final Flag<Boolean> LAYOUT_INSPECTOR_EDITING_ENABLED = Flag.create(
    LAYOUT_INSPECTOR, "ui.editing", "Enable editing ViewNode properties in the properties table.",
    "If enabled, users can edit properties in the properties table.", false);
  public static final Flag<Boolean> DYNAMIC_LAYOUT_INSPECTOR_ENABLED = Flag.create(
    LAYOUT_INSPECTOR, "dynamic.layout.inspector", "Enable dynamic layout inspector",
    "Turns on the dynamic layout inspector.", true);
  public static final Flag<Boolean> DYNAMIC_LAYOUT_INSPECTOR_EDITING_ENABLED = Flag.create(
    LAYOUT_INSPECTOR, "dynamic.layout.editor", "Enable dynamic layout editor",
    "If enabled, users can edit layout properties with live updates on a device while the dynamic layout inspector is running.",
    false);
  public static final Flag<Boolean> DYNAMIC_LAYOUT_INSPECTOR_USE_DEVBUILD_SKIA_SERVER = Flag.create(
    LAYOUT_INSPECTOR, "dynamic.layout.inspector.devbuild.skia", "Use the locally-built skia rendering server",
    "If enabled and this is a locally-built studio instance, use the locally-built skia server instead of one from the SDK.", false);
  public static final Flag<Boolean> DYNAMIC_LAYOUT_INSPECTOR_ENABLE_COMPOSE_SUPPORT = Flag.create(
    LAYOUT_INSPECTOR, "dynamic.layout.inspector.compose.support", "Show inspectables from Compose",
    "If enabled the component tree will include Composable nodes if they are wrapped in an Inspectable.", true);
  public static final Flag<Boolean> DYNAMIC_LAYOUT_INSPECTOR_USE_INSPECTION = Flag.create(
    LAYOUT_INSPECTOR, "dynamic.layout.inspector.use.inspection", "Use app inspection client",
    "If enabled, use a client built on the app inspection pipeline instead of the transport pipeline.", true);
  public static final Flag<Boolean> DYNAMIC_LAYOUT_INSPECTOR_COMPONENT_TREE_OPTIONS = Flag.create(
    LAYOUT_INSPECTOR, "dynamic.layout.inspector.tree.options", "Add view options to component tree",
    "If enabled, the component tree will have extra options under the gear dropdown.", false);
  //endregion

  //region Embedded Emulator
  private static final FlagGroup EMBEDDED_EMULATOR = new FlagGroup(FLAGS, "embedded.emulator", "Embedded Emulator");
  public static final Flag<Boolean> EMBEDDED_EMULATOR_EXTENDED_CONTROLS = Flag.create(
    EMBEDDED_EMULATOR, "extended.controls", "Enable Emulator Extended Controls",
    "Enables the extended controls in the Embedded Emulator",
    true);
  public static final Flag<Boolean> EMBEDDED_EMULATOR_VIRTUAL_SCENE_CAMERA = Flag.create(
    EMBEDDED_EMULATOR, "virtual.scene.camera", "Enable Emulator Virtual Scene Camera",
    "Enables the virtual scene camera in the Embedded Emulator",
    true);
  public static final Flag<Boolean> EMBEDDED_EMULATOR_MULTIPLE_DISPLAYS = Flag.create(
    EMBEDDED_EMULATOR, "multiple.displays", "Enable Emulator Multiple Displays",
    "Enables configurable multiple displays in the Embedded Emulator",
    true);
  public static final Flag<Boolean> EMBEDDED_EMULATOR_FOLDING = Flag.create(
    EMBEDDED_EMULATOR, "folding", "Enable Emulator Folding",
    "Enables display folding in the Embedded Emulator",
    true);
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

  //region Refactorings
  private static final FlagGroup REFACTORINGS = new FlagGroup(FLAGS, "refactor", "Refactor menu");

  public static final Flag<Boolean> MIGRATE_TO_RESOURCE_NAMESPACES_REFACTORING_ENABLED = Flag.create(
    REFACTORINGS, "migrateto.resourcenamespaces.enabled", "Enable the Migrate to Resource Namespaces refactoring",
    "If enabled, show the action in the refactoring menu", false);

  public static final Flag<Boolean> MIGRATE_TO_NON_TRANSITIVE_R_CLASSES_REFACTORING_ENABLED = Flag.create(
    REFACTORINGS, "migrateto.nontransitiverclasses.enabled", "Enable the Migrate to non-transitive R classes refactoring",
    "If enabled, show the action in the refactoring menu", true);
  //endregion

  //region IoT
  private static final FlagGroup IOT = new FlagGroup(FLAGS, "iot", "IoT features");
  public static final Flag<Boolean> UNINSTALL_LAUNCHER_APPS_ENABLED = Flag.create(
    IOT, "iot.uninstalllauncherapps.enabled", "Enable the Uninstall of IoT launcher apps feature",
    "If enabled, uninstall IoT launcher apps when installing a new one", false);
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

  public static final Flag<Boolean> USE_CONTENT_ROOTS_FOR_NATIVE_PROJECT_VIEW = Flag.create(
    NDK, "use.content.roots.for.native.project.view", "Use content roots for native project view",
    "If enabled, the C/C++ content roots are displayed in Android View and Project View. Otherwise, each individual native target " +
    "is displayed.",
    true);

  public static final Flag<Boolean> ENABLE_SHOW_FILES_UNKNOWN_TO_CMAKE = Flag.create(
    NDK, "ndk.projectview.showfilessunknowntocmake", "Enable option to show files unknown to CMake",
    "If enabled, for projects using CMake, Android project view menu would show an option to `Show Files Unknown To CMake`.",
    true
  );

  public static final Flag<Boolean> ENABLE_LLDB_NATVIS = Flag.create(
    NDK, "lldb.natvis", "Use NatVis visualizers in native debugger",
    "If enabled, native debugger formats variables using NatVis files found in the project.",
    true
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

  public static final Flag<Boolean> CUSTOM_JAVA_NEW_CLASS_DIALOG = Flag.create(
    EDITOR, "custom.new.class.dialog",
    "Enable custom New Class dialog",
    "If enabled, our custom UI for creating a new Java class is used. Otherwise the platform default is used.",
    false
  );


  public static final Flag<Boolean> INCREMENTAL_RESOURCE_REPOSITORIES = Flag.create(
    EDITOR, "incremental.resource.repositories",
    "Handle PSI events incrementally in ResourceFolderRepository",
    "If enabled, ResourceFolderRepository will handle PSI events synchronously, rather than rescanning the whole file.",
    true
  );


  public static final Flag<Boolean> TWEAK_COLOR_SCHEME = Flag.create(
    EDITOR, "tweak.color.scheme",
    "Change the default color scheme",
    "If enabled, we modify the default color scheme slightly.",
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

  public static final Flag<Boolean> MULTIDEVICE_INSTRUMENTATION_TESTS = Flag.create(
    TESTING, "multidevice.instrumentation.tests", "Allow running instrumentation tests on multiple devices at a time.",
    "If enabled, you can choose run-on-selected-devices for android instrumentation test run configurations.",
    true
  );

  public static final Flag<Boolean> UTP_TEST_RESULT_SUPPORT = Flag.create(
    TESTING, "utp.instrumentation.tests", "Allow importing UTP test results.",
    "If enabled, you can import UTP test results and display them in test result panel.",
    true
  );

  public static final Flag<Boolean> UTP_INSTRUMENTATION_TESTING = Flag.create(
    TESTING, "utp.instrumentation.testing", "Run instrumentation tests via UTP",
    "If enabled, switch to running instrumentation tests via UTP.",
    false
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
  public static final Flag<Boolean> COMPOSE_PREVIEW = Flag.create(
    COMPOSE, "preview.enabled", "Enable the Compose preview",
    "If enabled, a visual preview will be available for Compose.",
    true);

  public static final Flag<Boolean> COMPOSE_PREVIEW_ONLY_KOTLIN_BUILD = Flag.create(
    COMPOSE, "preview.fast.build.enabled", "Enable the use of \"compileDebugKotlin\" for the preview refresh",
    "If enabled, the refresh button will only trigger the \"compileDebugKotlin\" task as opposed to others like" +
    "\"generateDebugSources\" or \"compileJava\".",
    true);

  public static final Flag<Boolean> COMPOSE_PREVIEW_BUILD_ON_SAVE = Flag.create(
    COMPOSE, "preview.build.on.save.enabled", "Enable the compose \"build on save\"",
    "If enabled, the preview will automatically trigger a build after the user or IntelliJ save the documents.",
    false);

  public static final Flag<Boolean> COMPOSE_PREVIEW_RUN_CONFIGURATION = Flag.create(
    COMPOSE, "preview.run.configuration", "Enable running Compose Previews on device/emulator",
    "If enabled, it will be possible to create run configurations that launch a Compose Preview directly to the device/emulator.",
    true);

  public static final Flag<Boolean> COMPOSE_PREVIEW_DOUBLE_RENDER = Flag.create(
    COMPOSE, "preview.double.render", "Enable the Compose double render mode",
    "If enabled, preview components will be rendered twice so components depending on a recompose (like tableDecoration) " +
    "render correctly.",
    false);

  public static final Flag<Boolean> COMPOSE_PREVIEW_SCROLL_ON_CARET_MOVE = Flag.create(
    COMPOSE, "preview.scroll.on.caret.move", "Enable the Compose Preview scrolling when the caret moves",
    "If enabled, when moving the caret in the text editor, the Preview will show the preview currently under the cursor.",
    true);

  public static final Flag<Boolean> COMPOSE_PREVIEW_INTERRUPTIBLE = Flag.create(
    COMPOSE, "preview.interruptible", "Allows the Compose Preview to interrupt rendering calls",
    "If enabled, if a render takes too long, the preview will be able to interrupt the execution.",
    true);

  public static final Flag<Boolean> COMPOSE_EDITOR_SUPPORT = Flag.create(
    COMPOSE, "editor",
    "Compose-specific support in the code editor",
    "Controls whether Compose-specific editor features, like completion tweaks, are enabled. This flag has priority over " +
    "all flags in the `compose.editor.*` namespace.",
    true
  );

  public static final Flag<Boolean> COMPOSE_COMPLETION_PRESENTATION = Flag.create(
    COMPOSE, "editor.completion.presentation",
    "Custom presentation for code completion items for composable functions",
    "If enabled, code completion items for composable functions use a custom presentation (icon, text).",
    true
  );

  public static final Flag<Boolean> COMPOSE_COMPLETION_WEIGHER = Flag.create(
    COMPOSE, "editor.completion.weigher",
    "Custom weigher for Compose",
    "If enabled, code completion puts composable functions above other completion suggestions.",
    true
  );

  public static final Flag<Boolean> COMPOSE_COMPLETION_INSERT_HANDLER = Flag.create(
    COMPOSE, "editor.completion.insert.handler",
    "Custom insert handler for composable functions",
    "If enabled, code completion for composable functions uses a custom InsertHandler that inserts required parameter names.",
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

  public static final Flag<Boolean> COMPOSE_WIZARD_TEMPLATES = Flag.create(
    COMPOSE, "wizard.templates",
    "Show Compose Wizards",
    "If enabled, allows adding new Compose Projects/Modules/Activities through the wizards",
    true
  );

  public static final Flag<Boolean> COMPOSE_ANIMATED_PREVIEW = Flag.create(
    COMPOSE, "preview.animated.enable",
    "Enable animated compose preview",
    "If enabled, a user can switch compose preview to be animated",
    true
  );

  public static final Flag<Boolean> COMPOSE_ANIMATED_PREVIEW_SHOW_CLICK = Flag.create(
    COMPOSE, "preview.animated.click.enable",
    "Enable displaying clicks on the animated preview",
    "If enabled, clicking on the animated preview will generate a ripple",
    true
  );

  public static final Flag<Boolean> COMPOSE_ANIMATION_INSPECTOR = Flag.create(
    COMPOSE, "preview.animation.inspector",
    "Enable compose preview animation inspection",
    "If enabled, users can inspect animations in compose previews, e.g. play/pause and jump to specific frame",
    true
  );

  public static final Flag<Boolean> COMPOSE_ANIMATION_PREVIEW_LABEL_INSPECTION = Flag.create(
    COMPOSE, "preview.animation.label.inspection",
    "Enable 'label' parameter inspection of Compose animations PropKeys",
    "If enabled, show a warning when the 'label' parameter of Compose animations PropKeys are not set.",
    true
  );

  public static final Flag<Boolean> COMPOSE_LIVE_LITERALS = Flag.create(
    COMPOSE, "preview.live.literals",
    "Enable the live literals",
    "If enabled, the live literals feature is enabled",
    true
  );

  public static final Flag<Boolean> COMPOSE_DEPLOY_LIVE_LITERALS = Flag.create(
    COMPOSE, "deploy.live.literals.deploy",
    "Enable live literals deploy",
    "If enabled, literals within Composable functions are instantly deployed to device",
    true
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

  public static final Flag<Boolean> COMPOSE_BLUEPRINT_MODE = Flag.create(
    COMPOSE, "preview.blueprint",
    "Enable the blueprint mode for Compose previews",
    "If enabled, the user can change the mode of Compose previews, between design and blueprint mode",
    false
  );

  public static final Flag<Boolean> COMPOSE_QUICK_ANIMATED_PREVIEW = Flag.create(
    COMPOSE, "preview.animated.quick",
    "Speed up transition between static and animated compose previews",
    "If enabled, a transition between static and animated compose preview is almost instant",
    true
  );

  public static final Flag<Boolean> NL_COLORBLIND_MODE = Flag.create(
    COMPOSE, "nl.colorblind",
    "Enable the colorblind mode for Design Surface",
    "If enabled, the user can change the mode of layout previews, between different types of colorblind modes",
    true
  );

  public static final Flag<Boolean> COMPOSE_COLORBLIND_MODE = Flag.create(
    COMPOSE, "preview.colorblind",
    "Enable the colorblind mode for Compose previews",
    "If enabled, the user can change the mode of Compose previews, between different types of colorblind modes",
    true
  );

  public static final Flag<Boolean> COMPOSE_PIN_PREVIEW = Flag.create(
    COMPOSE, "preview.pin.enable",
    "Enable pinning compose previews",
    "If enabled, a user can pin a preview",
    false
  );

  public static final Flag<Boolean> COMPOSE_CONSTRAINT_VISUALIZATION = Flag.create(
    COMPOSE, "constraint.visualization",
    "Enable ConstraintLayout visualization in Compose previews",
    "If enabled, constraints from a ConstraintLayout composable will be shown in the preview",
    false
  );

  public static final Flag<Boolean> COMPOSE_INDIVIDUAL_PIN_PREVIEW = Flag.create(
    COMPOSE, "preview.individual.pin.enable",
    "Enable pinning of individual compose previews",
    "If enabled, a user can pin a single preview within a file",
    false
  );

  public static final Flag<Boolean> COMPOSE_INTERACTIVE_ANIMATION_SWITCH = Flag.create(
    COMPOSE, "preview.switch.animation.interactive",
    "Enable animation inspection switch from interactive preview (and disable from static preview)",
    "If enabled, a user can switch to animation inspection from interactive preview",
    true
  );
  //endregion

  //region Manifests
  private static final FlagGroup MANIFESTS = new FlagGroup(FLAGS, "manifests", "Android Manifests");
  public static final Flag<Boolean> ANDROID_MANIFEST_INDEX_ENABLED = Flag.create(
    MANIFESTS, "index.enabled", "Enable Android Manifest Indexing",
    "Enables a custom index for pre-parsing your project's AndroidManifest.xml files",
    true);

  //endregion

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
  // endregion

  // region WorkManager Inspector
  private static final FlagGroup WORK_MANAGER_INSPECTOR = new FlagGroup(FLAGS, "work.inspector", "WorkManager Inspector");
  public static final Flag<Boolean> ENABLE_WORK_MANAGER_INSPECTOR_TAB = Flag.create(
    WORK_MANAGER_INSPECTOR, "enable.tab", "Enable WorkManager Inspector Tab",
    "Enables a WorkManager Inspector Tab in the App Inspection tool window",
    true
  );

  public static final Flag<Boolean> ENABLE_WORK_MANAGER_GRAPH_VIEW = Flag.create(
    WORK_MANAGER_INSPECTOR, "enable.graph.view", "Enable WorkManager Graph View",
    "Enables a Graph View for visualizing work dependencies in the WorkManager Inspector Tab",
    true
  );
  // endregion

  // region Network Inspector
  private static final FlagGroup NETWORK_INSPECTOR = new FlagGroup(FLAGS, "network.inspector", "Network Inspector");
  public static final Flag<Boolean> ENABLE_NETWORK_MANAGER_INSPECTOR_TAB = Flag.create(
    NETWORK_INSPECTOR, "enable.network.inspector.tab", "Enable Network Inspector Tab",
    "Enables a Network Inspector Tab in the App Inspection tool window",
    false
  );
  // endregion

  //region Device Manager
  private static final FlagGroup DEVICE_MANAGER = new FlagGroup(FLAGS, "device.manager", "Device Manager");
  public static final Flag<Boolean> ENABLE_NEW_DEVICE_MANAGER_PANEL = Flag.create(
    DEVICE_MANAGER, "enable.device.manager", "Enable new Device Manager panel",
    "Enables the new Device Manager panel on the right. It will be a replacement for an AVD manager with additional functionality",
    false
  );
  public static final Flag<Boolean> ENABLE_DEVICE_MANAGER_GROUPS = Flag.create(
    DEVICE_MANAGER, "enable.device.manager.groups", "Enable groups tab",
    "Enables the device groups tab in the new Device Manager",
    false
  );
  // endregion

  //region Suggested Import(s)
  private static final FlagGroup SUGGESTED_IMPORT = new FlagGroup(FLAGS, "suggested.import", "Suggested import");
  public static final Flag<Boolean> ENABLE_SUGGESTED_IMPORT = Flag.create(
    SUGGESTED_IMPORT, "enable", "Enable suggested import",
    "Enables the code path where we get indices from dl.google.com/android/studio/gmaven/index/... and generate corresponding " +
    "lookup table from class names to GMaven coordinates. It will be a replacement for the hardcoded mapping data in " +
    "MavenClassRegistryFromHardcodedMap.",
    true
  );
  // endregion

  //region DDMLIB
  private static final FlagGroup DDMLIB = new FlagGroup(FLAGS, "ddmlib", "DDMLIB");
  public static final Flag<Boolean> ENABLE_JDWP_PROXY_SERVICE = Flag.create(
    DDMLIB, "enable.jdwp.proxy.service", "Enable jdwp proxy service",
    "Creates a proxy service within DDMLIB to allow shared device client connections.",
    true
  );
  // endregion DDMLIB

  //region SERVER_FLAGS
  private static final FlagGroup SERVER_FLAGS = new FlagGroup(FLAGS, "serverflags", "Server Flags");
  public static final Flag<Boolean> TEST_SERVER_FLAG = Flag.create(
    SERVER_FLAGS, "test", "Test Server Enabled Flag",
    "Creates a sample studio flag that can be set using a server flag",
    false
  );
  // endregion SERVER_FLAGS

  private StudioFlags() { }
}
