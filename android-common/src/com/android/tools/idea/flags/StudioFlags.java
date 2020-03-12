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
import com.intellij.openapi.application.ApplicationInfo;
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

  private static boolean isDevBuild() {
    if (ApplicationManager.getApplication() == null) {
      return true;
    }
    ApplicationInfo applicationInfo = ApplicationInfo.getInstance();
    return applicationInfo == null || applicationInfo.getStrictVersion().equals("0.0.0.0");
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
    false);
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

  public static final Flag<Boolean> PROFILER_CPU_API_TRACING = Flag.create(
    PROFILER, "cpu.api.tracing", "Enable CPU API Tracing",
    "Support method tracing through APIs from android.os.Debug.",
    true);

  public static final Flag<Boolean> PROFILER_USE_LIVE_ALLOCATIONS = Flag.create(
    PROFILER, "livealloc", "Enable JVMTI-based live allocation tracking",
    "For Android O or newer, allocations are tracked all the time while inside the Memory Profiler.",
    true);

  public static final Flag<Boolean> PROFILER_MEMORY_SNAPSHOT = Flag.create(
    PROFILER, "memory.livealloc.snapshot", "Enable Memory Class Histogram Display",
    "For Android O or newer, supports single-point selection which shows a snapshot of the heap at the specific time.",
    true);

  public static final Flag<Boolean> PROFILER_SAMPLE_LIVE_ALLOCATIONS = Flag.create(
    PROFILER, "memory.livealloc.sampled", "Enable Sampled Live Allocation Tracking",
    "For Android O or newer, allows users to configure the sampling mode of live allocation tracking",
    true);


  public static final Flag<Boolean> PROFILER_USE_PERFETTO = Flag.create(
    PROFILER, "perfetto", "Allows importing and recording of perfetto traces.",
    "Toggles if we check for perfetto traces when importing. This also sets a flag on the agent config to toggle perfetto" +
    "based recording on device.",
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

  public static final Flag<Boolean> PROFILER_HEAPDUMP_SEPARATE = Flag.create(
    PROFILER, "memory.heapdump.separate", "Show heap dump separately",
    "Show heap dump as a separate view instead of sharing with the memory monitor",
    false);
  //endregion

  //region ML Kit
  private static final FlagGroup MLKIT = new FlagGroup(FLAGS, "mlkit", "ML Kit");
  public static final Flag<Boolean> MLKIT_TFLITE_MODEL_FILE_TYPE = Flag.create(
    MLKIT, "modelfiletype", "Enable TFLite model file type",
    "When enabled, TFLite model file can be recognized as a particular type and has its own viewer.",
    false);
  public static final Flag<Boolean> MLKIT_LIGHT_CLASSES = Flag.create(
    MLKIT, "lightclasses", "Enable light model classes generation",
    "When enabled, light model classes will be generated for each recognized TFLite model file. Please invalidates file " +
    "caches after enabling (File -> Invalidate Caches...) in order to reindex model files.",
    false);
  //endregion

  //region Asset Studio
  private static final FlagGroup ASSET = new FlagGroup(FLAGS, "asset", "Asset Studio");
  public static final Flag<Boolean> ASSET_COPY_MATERIAL_ICONS = Flag.create(
    ASSET, "copy.material.icons", "Allow copying icons to Sdk folder",
    "Allow the IconPickerDialog in Asset Studio to copy bundled material icons in to the Android/Sdk folder",
    false);
  public static final Flag<Boolean> ASSET_DOWNLOAD_MATERIAL_ICONS = Flag.create(
    ASSET, "download.material.icons", "Allow downloading icons to Sdk folder",
    "Allow the IconPickerDialog in Asset Studio to download any new material icons in to the Android/Sdk folder",
    false);
  //endregion

  //region Layout Editor
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

  public static final Flag<Boolean> NELE_DEFAULT_LIVE_RENDER = Flag.create(
    NELE, "live.render", "Enable the Live Render by default",
    "Enable the continuous rendering of the surface when moving/resizing components unless the user disables it.",
    true);

  public static final Flag<Boolean> NELE_CONVERT_VIEW = Flag.create(
    NELE, "convert.view", "Enable the Convert View action",
    "Enable the Convert View Action when right clicking on a component",
    true);

  public static final Flag<Boolean> NELE_RENDER_DIAGNOSTICS = Flag.create(
    NELE, "diagnostics", "Enable rendering on-screen stats",
    "If enabled, the surface displays some debug information to diagnose performance",
    false);

  public static final Flag<Boolean> NELE_RESOURCE_POPUP_PICKER = Flag.create(
    NELE, "show.resource.popup.picker", "Enable popup  resource picker in layout editor.",
    "Show the popup picker for resource picking or attribute customization in layout editor.",
    true);

  public static final Flag<Boolean> NELE_LOG_ANDROID_FRAMEWORK = Flag.create(
    NELE, "log.android.framework", "Log messages coming from Layoutlib Native.",
    "Log in the IDEA log the messages coming from Java and native code of Layoutlib Native.",
    false);

  public static final Flag<Boolean> NELE_SCENEVIEW_TOP_TOOLBAR = Flag.create(
    NELE, "sceneview.top.toolbar", "Enable the per SceneView top toolbar.",
    "Enable the per SceneView top toolbar that displays the SceneView contextual actions.",
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

  public static final Flag<Boolean> NELE_NEW_PROPERTY_PANEL = Flag.create(
    NELE, "new.property", "Enable the new Property Panel",
    "Enable the new Property Panel",
    true);

  public static final Flag<Boolean> NELE_DRAG_PLACEHOLDER = Flag.create(
    NELE, "drag.placeholder", "Dragging widgets with Placeholders",
    "New architecture for dragging widgets in Layout Editor",
    true);

  public static final Flag<Boolean> NELE_RENDER_HIGH_QUALITY_SHADOW = Flag.create(
    NELE, "high.quality.shadow", "Enable the high quality shadows",
    "Enable the high quality shadow rendering in Layout Editor",
    true);

  public static final Flag<Boolean> NELE_ENABLE_SHADOW = Flag.create(
    NELE, "enable.shadow", "Enable shadows",
    "Enable the shadow rendering in Layout Editor",
    true);

  public static final Flag<Boolean> NELE_PROPERTY_PANEL_ACTIONBAR = Flag.create(
    NELE, "property.panel.actionbar", "Property Panel Actionbar",
    "Support Actionbar in property panel",
    false);

  public static final Flag<Boolean> NELE_DESIGN_SURFACE_ZOOM = Flag.create(
    NELE, "design.surface.zoom", "Zoom panel in Design Surface",
    "Enable zoom controls in the design surface, substitutes any zoom controls on the top toolbar of the editor.",
    true);

  public static final Flag<Boolean> NELE_VISUALIZATION = Flag.create(
    NELE, "visualisation", "Layout Visualisation Tool",
    "Enable Visualisation Tool to preview layout in multiple devices at the same time",
    true);

  public static final Flag<Boolean> NELE_COLOR_BLIND_MODE = Flag.create(
    NELE, "color.blind.mode", "Color Blind Mode",
    "Enable Visualisation Tool to preview layouts in multiple color blind modes at the same time",
    true);

  public static final Flag<Boolean> NELE_LARGE_FONT_MODE = Flag.create(
    NELE, "large.font.mode", "Large Font Mode",
    "Enable Visualisation Tool to preview layouts in multiple font sizes at the same time",
    true);

  public static final Flag<Boolean> NELE_SOURCE_CODE_EDITOR = Flag.create(
    NELE, "show.source.code.editor", "New Source Code Editor",
    "Enable new source code editor with preview(s) coming as a substitute to Compose and Custom View editors.",
    true);

  public static final Flag<Boolean> NELE_TOGGLE_TOOLS_ATTRIBUTES_IN_PREVIEW = Flag.create(
    NELE, "toggle.tools.attributes.preview", "New Toggle for Tools namespaces attributes",
    "Enable the new toggle in the Layout Editor. Allows toggling tools attributes in the Layout preview.",
    false);

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
  //endregion

  //region Navigation Editor
  private static final FlagGroup NAV_EDITOR = new FlagGroup(FLAGS, "nav", "Navigation Editor");
  public static final Flag<Boolean> NAV_NEW_PROPERTY_PANEL = Flag.create(
    NAV_EDITOR, "new.property", "Enable the new Property Panel",
    "Enable the new Property Panel",
    true);
  public static final Flag<Boolean> NAV_NEW_COMPONENT_TREE = Flag.create(
    NAV_EDITOR, "new.component", "Enable the new Component Tree",
    "Enable the new Component Tree",
    false);
  public static final Flag<Boolean> NAV_DYNAMIC_SUPPORT = Flag.create(
    NAV_EDITOR, "dynamic.support", "Support for Dynamic Feature Modules",
    "Support for Dynamic Feature Modules",
    true);

  public static final Flag<Boolean> NAV_SAFE_ARGS_SUPPORT = Flag.create(
    NAV_EDITOR, "safe.args.enabled", "Enable support for Safe Args",
    "Generate in-memory Safe Args classes if the current module is using the feature.",
    false);
  //endregion

  //region Resource Manager
  private static final FlagGroup RES_MANAGER = new FlagGroup(FLAGS, "res.manager", "Resource Manager");
  public static final Flag<Boolean> EXTENDED_TYPE_FILTERS = Flag.create(
    RES_MANAGER, "extended.filters", "Enable extended filters for resources",
    "Adds more filter options for resources based on the selected ResourceType. Includes options to filter by resource XML tag or "
    + "File extension.",
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

  public static final Flag<Boolean> APPLY_CHANGES_STRUCTURAL_DEFINITION = Flag.create(
    RUNDEBUG,
    "applychanges.structuralredefinition",
    "Use ART's new structural redefinition extension for Apply Changes.",
    "Requires applychanges.optimisticswap to be true.",
    false);

  public static final Flag<Boolean> SELECT_DEVICE_SNAPSHOT_COMBO_BOX_SNAPSHOTS_ENABLED = Flag.create(
    RUNDEBUG,
    "select.device.snapshot.combo.box.snapshots.enabled",
    "Enable Select Device/Snapshot combo box snapshots",
    "So the new Instant Run can use the combo box",
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
  public static final Flag<Boolean> SINGLE_VARIANT_SYNC_ENABLED = Flag.create(
    GRADLE_IDE, "single.variant.sync", "Enable new \"Single-Variant Sync\"",
    "Turns on Single-Variant Sync.", false);
  public static final Flag<Boolean> USE_DEVELOPMENT_OFFLINE_REPOS = Flag.create(
    GRADLE_IDE, "development.offline.repos", "Enable development offline repositories",
    "Makes Gradle use development offline repositories such as /out/repo", isDevBuild());
  public static final Flag<Boolean> BUILD_ATTRIBUTION_ENABLED = Flag.create(
    GRADLE_IDE, "build.attribution", "Enable build attribution",
    "Enable build attribution.", true);
  public static final Flag<Boolean> KOTLIN_DSL_PARSING = Flag.create(
    GRADLE_IDE, "kotlin.dsl", "Enable parsing for Kotlin build files",
    "Enables parsing for Gradle build files written using Kotlin (.gradle.kts)", true);
  public static final Flag<Boolean> DISABLE_FORCED_UPGRADES = Flag.create(
    GRADLE_IDE, "forced.agp.update", "Disable forced Android Gradle plugin upgrades",
    "This option is only respected when running Android Studio internally.", false
  );

  // REMOVE or change default to true after http://b/80245603 is fixed.
  public static final Flag<Boolean> L4_DEPENDENCY_MODEL = Flag.create(
    GRADLE_IDE, "level4.dependency.model", "Use L4 DependencyGraph Model",
    "Use level4 DependencyGraph model.", false);

  public static final Flag<Boolean> ALLOW_DIFFERENT_JDK_VERSION = Flag.create(
    GRADLE_IDE, "jdk.allow.different", "Allow different Gradle JDK", "Allow usage of a different JDK version when running Gradle.", true);
  //endregion

  //region Database Inspector
  private static final FlagGroup DATABASE_INSPECTOR = new FlagGroup(FLAGS, "database.inspector", "Database Inspector");
  public static final Flag<Boolean> DATABASE_INSPECTOR_ENABLED = Flag.create(
    DATABASE_INSPECTOR, "enabled", "Enable Database Inspector",
    "If enabled the Database Inspector tool window will appear. SQLite files opened from the Device Explorer will be opened in the inspector.",
    false);

  // TODO(b/144073974) why do we need a separate flag for this?
  public static final Flag<Boolean> SQLITE_APP_INSPECTOR_ENABLED = Flag.create(
    DATABASE_INSPECTOR, "sqlite.app.inspector", "Enable experimental SQLite inspector",
    "SQLite inspector runs and executes all operations in app process",
    false);
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
  //endregion

  //region Embedded Emulator
  private static final FlagGroup EMBEDDED_EMULATOR = new FlagGroup(FLAGS, "embedded.emulator", "Embedded Emulator");
  public static final Flag<Boolean> EMBEDDED_EMULATOR_ENABLED = Flag.create(
    EMBEDDED_EMULATOR, "embedded.emulator.enabled", "Enable Embedded Emulator",
    "Enables the Embedded Emulator tool window",
    false);
  public static final Flag<Boolean> EMBEDDED_EMULATOR_TRACE_GRPC_CALLS = Flag.create(
    EMBEDDED_EMULATOR, "embedded.emulator.trace.grpc.calls", "Enable Emulator gRPC Tracing",
    "Enables tracing of most Emulator gRPC calls",
    false);
  public static final Flag<Boolean> EMBEDDED_EMULATOR_TRACE_HIGH_VOLUME_GRPC_CALLS = Flag.create(
    EMBEDDED_EMULATOR, "embedded.emulator.trace.high.volume.grpc.calls", "Enable High Volume Emulator gRPC Tracing",
    "Enables tracing of high volume Emulator gRPC calls",
    false);
  public static final Flag<Boolean> EMBEDDED_EMULATOR_TRACE_SCREENSHOTS = Flag.create(
    EMBEDDED_EMULATOR, "embedded.emulator.trace.screenshots", "Enable Emulator Screenshot Tracing",
    "Enables tracing of received Emulator screenshots",
    false);
  //endregion

  //region Refactorings
  private static final FlagGroup REFACTORINGS = new FlagGroup(FLAGS, "refactor", "Refactor menu");
  public static final Flag<Boolean> MIGRATE_TO_RESOURCE_NAMESPACES_REFACTORING_ENABLED = Flag.create(
    REFACTORINGS, "migrateto.resourcenamespaces.enabled", "Enable the Migrate to Resource Namespaces refactoring",
    "If enabled, show the action in the refactoring menu", false);
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
  public static final Flag<Boolean> LLDB_ASSEMBLY_DEBUGGING = Flag.create(
    NDK, "debugging.assembly", "Enable assembly debugging",
    "If enabled, frames without sources will show the assembly of the function and allow breakpoints to be set there", false);

  public static final Flag<Boolean> ENABLE_ENHANCED_NATIVE_HEADER_SUPPORT = Flag.create(
    NDK, "enhancednativeheadersupport", "Enable enhanced native header support",
    "If enabled, project system view will show a new include node with organized header files", true);

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
  //endregion

  //region Editor
  private static final FlagGroup EDITOR = new FlagGroup(FLAGS, "editor", "Editor features");

  public static final Flag<Boolean> COLLAPSE_ANDROID_NAMESPACE = Flag.create(
    EDITOR,
    "collapse.android.namespace",
    "Collapse the android namespace in XML code completion",
    "If enabled, XML code completion doesn't include resources from the android namespace. Instead a fake completion item " +
    "is used to offer just the namespace prefix.", true);

  public static final Flag<Boolean> RESOLVE_USING_REPOS = Flag.create(
    EDITOR,
    "resolve.using.repos",
    "Resolve references using resource repositories",
    "Use ResourceRepository to resolve references, not ResourceManager.",
    true);

  public enum LayoutXmlMode {
    DEFAULT,
    /**
     * Don't run AndroidDomExtender at all, to see how other parts of the XML stack work.
     */
    NO_DOM_EXTENDER,
    /**
     * Don't use TagToClassMapper when computing tag attributes in AttributeProcessingUtil.
     */
    ATTRIBUTES_FROM_STYLEABLES,
  }

  public static final Flag<LayoutXmlMode> LAYOUT_XML_MODE = Flag.create(
    EDITOR,
    "layout.mode",
    "Layout XML editing mode",
    "Controls how XML editing in layout files works.",
    LayoutXmlMode.DEFAULT);

  public static final Flag<Boolean> MULTI_DEX_KEEP_FILE_SUPPORT_ENABLED = Flag.create(
    EDITOR, "multidexkeepfile.support.enabled",
    "Enable support for MultiDexKeepFile format",
    "If enabled, it offers support (such as code completion) for the MultiDexKeepFile format.",
    true
  );

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

  public static final Flag<Boolean> R8_SUPPORT_ENABLED = Flag.create(
    EDITOR, "r8.support.enabled",
    "Enable support for R8 in editor",
    "If enabled, it offers support (such as code completion) for the R8 format.",
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
    false
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

  public static final Flag<Boolean> KOTLIN_INCORRECT_SCOPE_CHECK_IN_TESTS = Flag.create(
    TESTING, "kotlin.incorrect.scope.check", "Checks the scope of classes being used in kotlin test files",
    "If enabled, an inspection will run that shows an error when a class is used in a kotlin test file that is not is scope.",
    false
  );

  public static final Flag<Boolean> MULTIDEVICE_INSTRUMENTATION_TESTS = Flag.create(
    TESTING, "multidevice.instrumentation.tests", "Allow running instrumentation tests on multiple devices at a time.",
    "If enabled, you can choose run-on-selected-devices for android instrumentation test run configurations.",
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

  public static final Flag<Boolean> COMPOSE_PREVIEW_AUTO_BUILD = Flag.create(
    COMPOSE, "preview.auto.build.enabled", "Enable the compose auto-build",
    "If enabled, the preview will automatically trigger a build after the user finishes typing.",
    false);

  public static final Flag<Boolean> COMPOSE_PREVIEW_RUN_CONFIGURATION = Flag.create(
    COMPOSE, "preview.run.configuration", "Enable running Compose Previews on device/emulator",
    "If enabled, it will be possible to create run configurations that launch a Compose Preview directly to the device/emulator.",
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

  public static final Flag<Boolean> COMPOSE_SURROUND_WITH_WIDGET = Flag.create(
    COMPOSE, "editor.surround.with.widget.action",
    "Enables \"Surround with widget\" intention and template",
    "Enables \"Surround with widget\" intention inside composable functions and adds \"Surround with widget\" live template",
    false
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
    false
  );

  public static final Flag<Boolean> COMPOSE_DEBUG_BOUNDS = Flag.create(
    COMPOSE, "preview.debug.bounds",
    "Enable the debug bounds switch controls",
    "If enabled, the user can enable/disable the painting of debug bounds",
    false
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
    false
  );
  // endregion

  private StudioFlags() { }
}
