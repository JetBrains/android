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

  public static final Flag<Boolean> NPW_DUMP_TEMPLATE_VARS = Flag.create(
    NPW, "dump.template.vars", "Dump template variables to a scratch file",
    "Dump the variables used in creating a template to a scrach file that is opened after creating the project.",
    false);

  public static final Flag<Boolean> NPW_FIRST_RUN_WIZARD = Flag.create(
    NPW, "first.run.wizard", "Show new Welcome Wizard",
    "Show new version of the Welcome Wizard when Studio starts",
    false);

  public static final Flag<Boolean> NPW_USE_HOME_FOLDER_AS_EXTRA_TEMPLATE_ROOT_FOLDER = Flag.create(
    NPW, "home.template.root", "Use .android folder as a Template Root Folder",
    "Let the user keep templates in the .android folder such that they are kept after a Studio install/upgrade",
    true);

  public static final Flag<Boolean> NPW_FIRST_RUN_SHOW = Flag.create(
    NPW, "first.run.wizard.show", "Show Welcome Wizard always",
    "Show the Welcome Wizard when Studio starts",
    false);

  public static final Flag<Boolean> NPW_SHOW_JDK_STEP = Flag.create(
    NPW, "first.run.jdk.step", "Show JDK setup step",
    "Show JDK Setup Step in Welcome Wizard",
    true);

  public static final Flag<Boolean> NPW_EXPERIMENTAL_ACTIVITY_GALLERY = Flag.create(
    NPW, "experimental.activity.gallery", "Show experimental activity gallery",
    "Show experimental activity gallery which contains Kotlin templates passed through plugin in addition to the normal gallery",
    false);

  public static final Flag<Boolean> NPW_SHOW_FRAGMENT_GALLERY = Flag.create(
    NPW, "show.fragment.gallery", "Show fragment gallery",
    "Show fragment gallery which contains fragment based templates",
    false);
  //endregion

  //region Profiler
  private static final FlagGroup PROFILER = new FlagGroup(FLAGS, "profiler", "Android Profiler");

  public static final Flag<Boolean> PROFILER_CPU_CAPTURE_STAGE = Flag.create(
    PROFILER, "cpu.capture.stage", "Enable new capture stage",
    "With the new System Trace design we have a cpu capture stage. This flag uses that flow instead of the legacy " +
    "CpuProfilerStageView flow.",
    false);

  public static final Flag<Boolean> PROFILER_FRAGMENT_PROFILER_ENABLED = Flag.create(
    PROFILER, "event.fragment", "Enable fragment profiling",
    "Shows fragment information in event profiler's activity bar and tooltip.",
    true);

  public static final Flag<Boolean> PROFILER_UNIFIED_PIPELINE = Flag.create(
    PROFILER, "unified.pipeline", "Enables new event pipeline to be used for core components.",
    "Toggles usage of gRPC apis to fetch data from perfd and the datastore.",
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

  public static final Flag<Boolean> PROFILER_SIMPLEPERF_HOST = Flag.create(
    PROFILER, "cpu.simpleperf.host", "Enable simpleperf report-sample to be run on the host.",
    "If enabled, simpleperf report-sample commands are going to be run on the host instead of the device.",
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

  public static final Flag<Boolean> PROFILER_SAMPLE_LIVE_ALLOCATIONS = Flag.create(
    PROFILER, "memory.livealloc.sampled", "Enable Sampled Live Allocation Tracking",
    "For Android O or newer, allows users to configure the sampling mode of live allocation tracking",
    true);

  public static final Flag<Boolean> PROFILER_USE_ATRACE = Flag.create(
    PROFILER, "atrace", "Show the atrace option in CPU profiler",
    "Toggles if atrace is a valid option to choose from the CPU profiling dropdown.",
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

  public static final Flag<Boolean> PROFILER_AUDITS = Flag.create(
    PROFILER, "audits", "Enable profiler audits",
    "When enabled, profiler workflows such as capturing CPU atrace captures will generate audits",
    false);

  public static final Flag<Boolean> PROFILER_CUSTOM_EVENT_VISUALIZATION = Flag.create(
    PROFILER, "custom.event.visualization", "Enable Profiler Custom Event Visualization",
    "When enabled, profiler will track and display events defined through developer APIs",
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
    false);
  public static final Flag<Boolean> NELE_CONSTRAINT_SELECTOR = Flag.create(
    NELE, "constraint.selection", "Allow selection of Constraints",
    "Allow the selection of constraints.",
    true);
  public static final Flag<Boolean> NELE_MOTION_HORIZONTAL = Flag.create(
    NELE, "animated.motion.horizontal", "Display motion editor horizontally",
    "Controls the placement of the motion editor (horizontal versus vertical).",
    true);
  public static final Flag<Boolean> NELE_MOCKUP_EDITOR = Flag.create(
    NELE, "mockup.editor", "Enable the Mockup Editor",
    "Enable the Mockup Editor to ease the creation of Layouts from a design file.",
    false);

  public static final Flag<Boolean> NELE_DEFAULT_LIVE_RENDER = Flag.create(
    NELE, "live.render", "Enable the Live Render by default",
    "Enable the continuous rendering of the surface when moving/resizing components unless the user disables it.",
    true);

  public static final Flag<Boolean> NELE_SAMPLE_DATA_UI = Flag.create(
    NELE, "widget.assistant", "Enable the new Sample Data UI components",
    "Enable the Sample Data UI to setup tools attributes.",
    true);

  public static final Flag<Boolean> NELE_CONVERT_VIEW = Flag.create(
    NELE, "convert.view", "Enable the Convert View action",
    "Enable the Convert View Action when right clicking on a component",
    true);

  public static final Flag<Boolean> ENABLE_NEW_SCOUT = Flag.create(
    NELE, "exp.scout.engine", "Experimental version of the Scout inference system",
    "Enable experimental version of the Scout inference system",
    false);

  public static final Flag<Boolean> NELE_RENDER_DIAGNOSTICS = Flag.create(
    NELE, "diagnostics", "Enable rendering on-screen stats",
    "If enabled, the surface displays some debug information to diagnose performance",
    false);

  public static final Flag<Boolean> NELE_SHOW_ONLY_SELECTION = Flag.create(
    NELE, "show.only.selection", "Show only selection boundaries when mouse is not hovered in layout",
    "Enable this flag to show selection boundaries without other decoration when mouse is not hovered in layout",
    true);

  public static final Flag<Boolean> NELE_SPLIT_EDITOR = Flag.create(
    NELE, "split.layout.editor", "Enable design editors and XML side-by-side view.",
    "Enable this flag to display the design editors side-by-side with their text representation.",
    true);

  public static final Flag<Boolean> NELE_RESOURCE_POPUP_PICKER = Flag.create(
    NELE, "show.resource.popup.picker", "Enable popup  resource picker in layout editor.",
    "Show the popup picker for resource picking or attribute customization in layout editor.",
    true);

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

  public static final Flag<Boolean> WHATS_NEW_ASSISTANT_DOWNLOAD_CONTENT = Flag.create(
    ASSISTANT, "whats.new.download", "Downloads \"What's New\" assistant content from web",
    "If enabled, the \"What's New\" assistant will update its contents from the web whenever it is opened.",
    true);

  public static final Flag<Boolean> NELE_NEW_PROPERTY_PANEL = Flag.create(
    NELE, "new.property", "Enable the new Property Panel",
    "Enable the new Property Panel",
    true);

  public static final Flag<Boolean> NELE_NEW_PROPERTY_PANEL_WITH_TABS = Flag.create(
    NELE, "new.property.tabs", "Use a tab panel to switch to the advanced table",
    "Use a tab panel to switch to advanced",
    false);

  public static final Flag<Boolean> NELE_NEW_COLOR_PICKER = Flag.create(
    NELE, "new.color.picker", "New Color Picker",
    "Enable new Color Picker in Layout Editor",
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

  public static final Flag<Boolean> NELE_SIMPLER_RESIZE = Flag.create(
    NELE, "simpler.resize", "Simplify resize",
    "Simplify design surface resize",
    true);

  public static final Flag<Boolean> NELE_CONSTRAINT_SECTION = Flag.create(
    NELE, "constraint.section", "Constraint Section",
    "Show the constraint section for constraint widget in property panel",
    true);

  public static final Flag<Boolean> NELE_PROPERTY_PANEL_ACTIONBAR = Flag.create(
    NELE, "property.panel.actionbar", "Property Panel Actionbar",
    "Support Actionbar in property panel",
    false);

  public static final Flag<Boolean> NELE_DISPLAY_MODEL_NAME = Flag.create(
    NELE, "display.model.name", "Display Model Name",
    "Enable the feature which can display the model name in Layout Editor.",
    true);
  //endregion

  //region Navigation Editor
  private static final FlagGroup NAV_EDITOR = new FlagGroup(FLAGS, "nav", "Navigation Editor");
  public static final Flag<Boolean> NAV_NEW_PROPERTY_PANEL = Flag.create(
    NAV_EDITOR, "new.property", "Enable the new Property Panel",
    "Enable the new Property Panel",
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

  public static final Flag<Boolean> SELECT_DEVICE_SNAPSHOT_COMBO_BOX_SNAPSHOTS_ENABLED = Flag.create(
    RUNDEBUG,
    "select.device.snapshot.combo.box.snapshots.enabled",
    "Enable Select Device/Snapshot combo box snapshots",
    "So the new Instant Run can use the combo box",
    false);
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
  public static final Flag<Boolean> COMPOUND_SYNC_ENABLED = Flag.create(
    GRADLE_IDE, "compound.sync", "Enable new \"Compound Sync\"",
    "Turns on Compound Sync.", true);
  public static final Flag<Boolean> USE_DEVELOPMENT_OFFLINE_REPOS = Flag.create(
    GRADLE_IDE, "development.offline.repos", "Enable development offline repositories",
    "Makes Gradle use development offline repositories such as /out/repo", isDevBuild());
  public static final Flag<Boolean> BUILD_ATTRIBUTION_ENABLED = Flag.create(
    GRADLE_IDE, "build.attribution", "Enable build attribution",
    "Enable build attribution.", false);

  // REMOVE or change default to true after http://b/80245603 is fixed.
  public static final Flag<Boolean> L4_DEPENDENCY_MODEL = Flag.create(
    GRADLE_IDE, "level4.dependency.model", "Use L4 DependencyGraph Model",
    "Use level4 DependencyGraph model.", false);
  //endregion

  //region SQLite Inspector
  private static final FlagGroup SQLITE_VIEWER = new FlagGroup(FLAGS, "sqlite.viewer", "SQLite Viewer");
  public static final Flag<Boolean> SQLITE_VIEWER_ENABLED = Flag.create(
    SQLITE_VIEWER, "enabled", "Enable the SQLite database viewer",
    "If enabled, SQLite files downloaded from Android devices or emulators are open in a custom SQLite editor window",
    false);
  //endregion

  //region Resource Manager
  private static final FlagGroup RESOURCES_MANAGEMENT = new FlagGroup(FLAGS, "res.manag", "Resource Management");
  public static final Flag<Boolean> RESOURCE_MANAGER_ENABLED = Flag.create(
    RESOURCES_MANAGEMENT, "enabled", "Enable the new resources management tools",
    "If enabled, the new resource management tools are enabled. Subflags will also need to be enabled to enable all available new tools",
    true);
  public static final Flag<Boolean> RESOURCE_EXPLORER_PICKER = Flag.create(
    RESOURCES_MANAGEMENT, "picker", "Enable the resource explorer as picker",
    "If enabled, the new resource management tools are used for the resource picker in the property panel.",
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
  public static final Flag<Boolean>  DYNAMIC_LAYOUT_INSPECTOR_ENABLED = Flag.create(
    LAYOUT_INSPECTOR, "dynamic.layout.inspector", "Enable dynamic layout inspector",
    "Turns on the dynamic layout inspector.", false);
  //endregion

  //region Refactorings
  private static final FlagGroup REFACTORINGS = new FlagGroup(FLAGS, "refactor", "Refactor menu");
  public static final Flag<Boolean> MIGRATE_TO_APPCOMPAT_REFACTORING_ENABLED = Flag.create(
    REFACTORINGS, "migrateto.appcompat.enabled", "Enable the Migrate to AppCompat refactoring",
    "If enabled, show the action in the refactoring menu", true);
  public static final Flag<Boolean> MIGRATE_TO_ANDROID_X_REFACTORING_ENABLED = Flag.create(
    REFACTORINGS, "migrateto.androidx.enabled", "Enable the Migrate to AndroidX refactoring",
    "If enabled, show the action in the refactoring menu", true);
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

  public static final Flag<Boolean> ENABLE_CLANG_TIDY_INSPECTIONS = Flag.create(
    NDK, "clangtidyinspections", "Enable clang-tidy inspections",
    "If enabled, show inspections derived from clang-tidy.", true);

  public static final Flag<Boolean> APK_DEBUG_BUILD_ID_CHECK = Flag.create(
    NDK, "apkdebugbuildidcheck", "Enable build ID check in APK debugging",
    "If enabled, the build ID of user-provided symbol files are compared against the binaries inside the APK.", true);

  public static final Flag<Boolean> APK_DEBUG_RELOAD = Flag.create(
    NDK, "apkdebugreload", "Enable APK reloading feature",
    "If enabled, the user will be provided with an option to reload the APK inside an APK debugging project", false);

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
    false);

  public static final Flag<Boolean> RUN_DOM_EXTENDER = Flag.create(
    EDITOR,
    "run.dom.extender",
    "Run DOM extender",
    "When disabled AndroidDomExtender does nothing, simulating a situation where DOM extensions have not been " +
    "computed yet.",
    true);

  public static final Flag<Boolean> MULTI_DEX_KEEP_FILE_SUPPORT_ENABLED = Flag.create(
    EDITOR, "multidexkeepfile.support.enabled",
    "Enable support for MultiDexKeepFile format",
    "If enabled, it offers support (such as code completion) for the MultiDexKeepFile format.",
    false
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
    true
  );


  public static final Flag<Boolean> INCREMENTAL_RESOURCE_REPOSITORIES = Flag.create(
    EDITOR, "incremental.resource.repositories",
    "Handle PSI events incrementally in ResourceFolderRepository",
    "If enabled, ResourceFolderRepository will handle PSI events synchronously, rather than rescanning the whole file.",
    true
  );

  //endregion

  //region Analyzer
  private static final FlagGroup ANALYZER = new FlagGroup(FLAGS, "analyzer", "Apk/Bundle Analyzer");
  public static final Flag<Boolean> ENABLE_APP_SIZE_OPTIMIZER = Flag.create(
    ANALYZER, "enable.app.size.optimizer", "Enable size optimization suggestions in apk analyzer",
    "If enabled, it will enable the apk analyzer tool to display suggestions for reducing application size", false);
  //endregion

  //region Unified App Bundle
  private static final FlagGroup UAB = new FlagGroup(FLAGS, "uab", "Unified App Bundle");

  public static final Flag<Boolean> UAB_ENABLE_NEW_INSTANT_APP_RUN_CONFIGURATIONS = Flag.create(
    UAB, "enable.ia.run.configs", "Enable new instant app run configuration options",
    "If enabled, shows the new instant app deploy checkbox in the run configuration dialog and allows new instant app deploy workflow.",
    true
  );
  //endregion

  //region Project Upgrade
  private static final FlagGroup PROJECT_UPGRADE = new FlagGroup(FLAGS, "project.upgrade", "Project Upgrade");
  public static final Flag<Boolean> BALLOON_UPGRADE_NOTIFICATION = Flag.create(
    PROJECT_UPGRADE, "balloon.upgrade.notification", "Enable Balloon Notification for Project Upgrade",
    "If enabled, the notification of project upgrade will show as balloon in the right-bottom side.",
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
    true
  );

  public static final Flag<Boolean> NITROGEN = Flag.create(
    TESTING, "nitrogen", "Enables Nitrogen test runner features",
    "If enabled, Nitrogen test runner configuration becomes available in addition to traditional test runner configurations.",
    false
  );
  //endregion

  //region Translations Editor
  private static final FlagGroup TRANSLATIONS_EDITOR = new FlagGroup(FLAGS, "translations.editor", "Translations Editor");

  public static final Flag<Boolean> TRANSLATIONS_EDITOR_USE_LOGICAL_FONT = Flag.create(
    TRANSLATIONS_EDITOR,
    "translations.editor.use.logical.font",
    "Use a logical font",
    "Use a logical font to display translations. See https://docs.oracle.com/javase/tutorial/2d/text/fonts.html#logical-fonts",
    true);
  //endregion

  //region Memory
  private static final FlagGroup MEMORY_SETTINGS = new FlagGroup(FLAGS, "memory.settings", "Memory Settings");
  public static final Flag<Boolean> RECOMMENDATION_ENABLED = Flag.create(
    MEMORY_SETTINGS, "recommendation.enabled", "Enable memory recommendation",
    "If enabled, users could get memory settings recommendation",
    true);

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

  public static final Flag<Boolean> WIN32_DEPRECATION_NOTIFICATION_ENABLED = Flag.create(
    SYSTEM_HEALTH, "win32.deprecation.notification.enabled", "Enable win32 deprecation notification",
    "If enabled, a notification will be shown",true);
  //endregion

  //region Compose
  private static final FlagGroup COMPOSE = new FlagGroup(FLAGS, "compose", "Compose");
  public static final Flag<Boolean> COMPOSE_PREVIEW = Flag.create(
    COMPOSE, "preview.enabled", "Enable the Compose preview",
    "If enabled, a visual preview will be available for Compose.",
    false);
  //endregion

  //region Binding
  private static final FlagGroup BINDING = new FlagGroup(FLAGS, "binding", "Data/View Binding");
  public static final Flag<Boolean> VIEW_BINDING_ENABLED = Flag.create(
    BINDING, "view.binding.enabled", "Enable View Binding",
    "Enables view binding integration. Additionally, enabling the compiler may require updating Gradle settings as well",
    true);
  //endregion

  private StudioFlags() { }
}
