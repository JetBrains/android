package icons;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;
import java.awt.*;

/**
 * Note: This file should be auto generated once build/scripts/icons.gant is part of CE.
 * https://youtrack.jetbrains.com/issue/IDEA-103558
 */
public class AndroidIcons {
  private static Icon load(String path) {
    return IconLoader.getIcon(path, AndroidIcons.class);
  }

  private static ImageIcon loadImage(String path) {
    return new ImageIcon(AndroidIcons.class.getClassLoader().getResource(path));
  }

  public static final Icon Android = load("/icons/android.png"); // 16x16
  public static final Icon Android24 = load("/icons/android24.png"); // 24x24
  public static final Icon AndroidLarge = load("/icons/androidLarge.png"); // 64x64
  public static final Icon AndroidPreview = load("/icons/androidPreview.png"); // 13x13
  public static final Icon AndroidToolWindow = load("/icons/androidToolWindow.png"); // 13x13

  public static final Icon AvdManager = load("/icons/avd_manager.png"); // 16x16
  public static final Icon SdkManager = load("/icons/sdk_manager.png"); // 16x16
  public static final Icon SdkManagerLarge = load("/icons/sdk_manager_large.png"); // 32x32
  public static final Icon NavigationEditor = load("/icons/navigation_editor.png"); // 16x16

  public static final Icon ZoomActual = load("/icons/zoomActual.png"); // 16x16
  public static final Icon ZoomFit = load("/icons/zoomFit.png"); // 16x16
  public static final Icon ZoomIn = load("/icons/zoomIn.png"); // 16x16
  public static final Icon ZoomOut = load("/icons/zoomOut.png"); // 16x16
  public static final Icon ZoomReal = load("/icons/zoomReal.png"); // 16x16
  public static final Icon Renderscript = load("/icons/renderscript.png"); // 16x16
  public static final Icon ErrorBadge = load("/icons/error-badge.png"); // 16x16
  public static final Icon WarningBadge = load("/icons/warning-badge.png"); // 16x16
  public static final Icon RenderError = load("/icons/renderError.png");
  public static final Icon RefreshPreview = load("/icons/refreshPreview.png"); // 16x16
  public static final Icon ArrowDown = load("/icons/comboArrow.png"); // 16x16
  public static final Icon GreyArrowDown = load("/icons/dropArrow.png"); // 20x20
  public static final Icon NotMatch = load("/icons/notMatch.png");

  public static final Icon AndroidFile = load("/icons/android_file.png"); // 16x16
  public static final Icon ManifestFile = load("/icons/manifest_file.png");
  public static final Icon Configuration = load("/icons/configuration.png"); // 16x16
  public static final Icon Activity = load("/icons/activity.png"); // 16x16
  public static final Icon Targets = load("/icons/targets.png"); // 16x16
  public static final Icon Globe = load("/icons/globe.png"); // 16x16
  public static final Icon Square = load("/icons/square.png"); // 16x16
  public static final Icon Landscape = load("/icons/landscape.png"); // 16x16
  public static final Icon Portrait = load("/icons/portrait.png"); // 16x16
  public static final Icon FlipLandscape = load("/icons/flip_landscape.png"); // 16x16
  public static final Icon FlipPortrait = load("/icons/flip_portrait.png"); // 16x16
  public static final Icon Display = load("/icons/display.png"); // 16x16
  public static final Icon Themes = load("/icons/themes.png"); // 16x16
  public static final Icon ThemesPreview = load("/icons/themesPreview.png"); // 13x13
  public static final Icon Sunburst = load("/icons/sunburst.png"); // 16x16

  public static final Icon EmptyFlag = load("/icons/flags/flag_empty.png"); // 16x16

  public static final Icon GradleSync = load("/icons/gradlesync.png"); // 16x16
  public static final Icon GradleConsole = load("/icons/gradle_console.png"); // 16x16
  public static final Icon GradleConsoleToolWindow = load("/icons/gradle_console_tool_window.png"); // 13x13
  public static final Icon MavenLogo = load("/icons/mavenLogo.png"); // 16x16

  public static final Icon CapturesToolWindow = load("/icons/captures.png"); // 13x13
  public static final Icon MemoryMonitor = load("/icons/memory_monitor.png"); // 16x16
  public static final Icon CpuMonitor = load("/icons/cpu_monitor.png"); // 16x16
  public static final Icon GpuMonitor = load("/icons/gpu_monitor.png"); // 16x16
  public static final Icon NetworkMonitor = load("/icons/network_monitor.png"); // 16x16

  public static final Icon Variant = load("/icons/variant.png");

  public static final Icon AppModule = load("/icons/appModule.png");
  public static final Icon LibraryModule = load("/icons/libraryModule.png");
  public static final Icon AndroidTestRoot = load("/icons/androidTestRoot.png");

  public static final Icon GreyQuestionMark = load("/icons/grey_question.png"); // 23x23

  public static class ProjectStructure {
    public static final Icon UnknownLibrary = load("/icons/psd/unknownLibrary.png");
    public static final Icon LibraryWarning = load("/icons/psd/libraryWarning.png");
  }

  // Form factors
  public static class FormFactors {
    public static final Icon Wear_16 = load("/icons/wear.png");                          // 16x16
    public static final Icon Car_16 = load("/icons/car.png");                            // 16x16
    public static final Icon Glass_16 = load("/icons/glass.png");                        // 16x16
    public static final Icon Mobile_16 = load("/icons/mobile.png");                      // 16x16
    public static final Icon Tv_16 = load("/icons/tv.png");                              // 16x16

    public static final Icon Wear_32 = load("/icons/formfactors/wear_32.png");           // 32x32
    public static final Icon Car_32 = load("/icons/formfactors/car_32.png");             // 32x32
    public static final Icon Glass_32 = load("/icons/formfactors/glass_32.png");         // 32x32
    public static final Icon Mobile_32 = load("/icons/formfactors/phone_tablet_32.png"); // 32x32
    public static final Icon Tv_32 = load("/icons/formfactors/tv_32.png");               // 32x32

    public static final Icon Wear_64 = load("/icons/formfactors/64/wear.png");           // 64x64
    public static final Icon Car_64 = load("/icons/formfactors/64/car.png");             // 64x64
    public static final Icon Glass_64 = load("/icons/formfactors/64/glass.png");         // 64x64
    public static final Icon Mobile_64 = load("/icons/formfactors/64/phone_tablet.png"); // 64x64
    public static final Icon Tv_64 = load("/icons/formfactors/64/tv.png");               // 64x64

    public static final Icon Wear_128 = load("/icons/formfactors/128/wear.png");         // 128x128
    public static final Icon Car_128 = load("/icons/formfactors/128/car.png");           // 128x128
    public static final Icon Glass_128 = load("/icons/formfactors/128/glass.png");       // 128x128
    public static final Icon Mobile_128 = load("/icons/formfactors/128/mobile.png");     // 128x128
    public static final Icon Tv_128 = load("/icons/formfactors/128/tv.png");             // 128x128
  }

  public static class Configs {
    public static final Icon Dock = load("/icons/dockmode.png");
    public static final Icon Night = load("/icons/nightmode.png");
    public static final Icon Dimension = load("/icons/dimension.png");
    public static final Icon Dpi = load("/icons/dpi.png");
    public static final Icon Height = load("/icons/height.png");
    public static final Icon Keyboard = load("/icons/keyboard.png");
    public static final Icon Locale = load("/icons/locale.png");
    public static final Icon Mcc = load("/icons/mcc.png");
    public static final Icon Mnc = load("/icons/mnc.png");
    public static final Icon Navpad = load("/icons/navpad.png");
    public static final Icon NavpadMethod = load("/icons/navpad_method.png");
    public static final Icon Orientation = load("/icons/orientation.png");
    public static final Icon Ratio = load("/icons/ratio.png");
    public static final Icon Size = load("/icons/size.png");
    public static final Icon SmallestWidth = load("/icons/swidth.png");
    public static final Icon TextInput = load("/icons/text_input.png");
    public static final Icon Touch = load("/icons/touch.png");
    public static final Icon Width = load("/icons/width.png");
    public static final Icon LayoutDirection = load("/icons/direction.png");

    // We might be able to remove these, but perhaps they're useful if we have individual
    // buttons (for example in the translation editor) where you can independently add language
    // or region?
    public static final Icon Language = load("/icons/language.png");
    public static final Icon Region = load("/icons/region.png");
  }

  public static class Ddms {
    public static final Icon AllocationTracker = load("/icons/ddms/allocation_tracker.png"); // 16x16
    public static final Icon AttachDebugger = load("/icons/ddms/attachDebugger.png"); // 16x16
    public static final Icon Gc = load("/icons/ddms/cause_garbage_collection.png"); // 16x16
    public static final Icon DumpHprof = load("/icons/ddms/dump_hprof_file.png"); // 16x16
    public static final Icon Emulator = load("/icons/ddms/emulator.png"); // 16x16
    public static final Icon Emulator2 = load("/icons/ddms/emulator_02.png"); // 16x16
    public static final Icon FileExplorer = load("/icons/ddms/file_explorer.png"); // 16x16
    public static final Icon Heap = load("/icons/ddms/heap.png"); // 16x16
    public static final Icon HeapInfo = load("/icons/ddms/heap_info.png"); // 16x16
    public static final Icon Logcat = load("/icons/ddms/logcat.png"); // 16x16
    /**
     * The {@link #LogcatAutoFilterSelectedPid} icon is a copy of the AllIcons.Diff.diff icon to be replaced if we get a better icon.
     */
    public static final Icon LogcatAutoFilterSelectedPid = load("/icons/ddms/logcat_filter_pid.png"); // 16x16
    public static final Icon RealDevice = load("/icons/ddms/real_device.png"); // 16x16
    public static final Icon EmulatorDevice = load("/icons/ddms/emulator_device.png"); // 16x16
    public static final Icon ScreenCapture = load("/icons/ddms/screen_capture.png"); // 16x16
    public static final Icon StartMethodProfiling = load("/icons/ddms/start_method_profiling.png"); // 16x16
    public static final Icon Threads = load("/icons/ddms/threads.png"); // 16x16
    public static final Icon SysInfo = load("/icons/ddms/sysinfo.png"); // 16x16 - this is a copy of AllIcons.Actions.Preview
    public static final Icon HierarchyView = load("/icons/ddms/hierarchyview.png"); // 16x16
  }

  public static class Wizards {
    public static final Icon StudioProductIcon = load("/icons/wizards/studio_product.png"); // 60x60
    public static final Icon NewModuleSidePanel = load("/icons/wizards/newModule.png"); // 143x627
    public static final Icon NewProjectSidePanel = load("/icons/wizards/newProject.png"); // 143x627
    public static final Icon DefaultTemplate = load("/icons/wizards/defaultTemplate.png"); //512x512
    public static final Icon DefaultTemplate256 = load("/icons/wizards/defaultTemplate256.png"); // 256x256
    public static final Icon GithubIcon = load("/icons/wizards/github_icon.png"); // 256x256
    /**
     * @deprecated Name is bad - this icon is used outside of project creation. Migrate to StudioProductIcon.
     * TODO: Post wizard migration: delete constant and welcome_green.png
     */
    public static final Icon NewProjectMascotGreen = load("/icons/wizards/welcome_green.png"); // 60x60
  }

  public static class NeleIcons {
    // All 16x16 and 32x32 in Retina mode
    public static final Icon Api = load("/icons/nele/api.png");
    public static final Icon Language = load("/icons/nele/language.png");
    public static final Icon Preview = load("/icons/nele/preview.png");
    public static final Icon Rotate = load("/icons/nele/rotate.png");
    public static final Icon Size = load("/icons/nele/size.png");
    public static final Icon Phone = load("/icons/nele/phone.png");
    public static final Icon Tablet = load("/icons/nele/tablet.png");
    public static final Icon Wear = load("/icons/nele/wear.png");
    public static final Icon Tv = load("/icons/nele/tv.png");
    public static final Icon Theme = load("/icons/nele/theme.png");
  }

  public static class RunIcons {
    public static final Icon Attach = load("/icons/run/attach.png");

    public static final Icon Debug = load("/icons/run/debug.png");
    public static final Icon DebugReattach = load("/icons/run/debug2.png");

    public static final Icon Play = load("/icons/run/play.png");
    public static final Icon Replay = load("/icons/run/play2.png");
    public static final Icon Restart = load("/icons/run/restart.png");
    public static final Icon CleanRerun = load("/icons/run/clean_rerun.png");
  }

  public static class SherpaIcons {
    public static final Icon ShowConstraints = load("/icons/sherpa/show_constraints.png");
    public static final Icon ShowNoConstraints = load("/icons/sherpa/show_no_constraints.png");
    public static final Icon ShowText = load("/icons/sherpa/show_text.png");
    public static final Icon ShowNoText = load("/icons/sherpa/show_no_text.png");
    public static final Icon ShowBlueprintOn = load("/icons/sherpa/switch_blueprint_on.png");
    public static final Icon ShowBlueprintOff = load("/icons/sherpa/switch_blueprint_off.png");
    public static final Icon ShowBlueprintBoth = load("/icons/sherpa/switch_blueprint_both.png");
    public static final Icon ShowInfoIdOn = load("/icons/sherpa/info_on.png");
    public static final Icon ShowInfoIdOff = load("/icons/sherpa/info_off.png");
    public static final ImageIcon Delete = loadImage("/icons/sherpa/delete.png");
    public static final ImageIcon LeftConstraintCursor = loadImage("/icons/sherpa/left_constraint_cursor.png");
    public static final ImageIcon TopConstraintCursor = loadImage("/icons/sherpa/top_constraint_cursor.png");
    public static final ImageIcon RightConstraintCursor = loadImage("/icons/sherpa/right_constraint_cursor.png");
    public static final ImageIcon BottomConstraintCursor = loadImage("/icons/sherpa/bottom_constraint_cursor.png");
    public static final ImageIcon BaselineConstraintCursor = loadImage("/icons/sherpa/baseline_constraint_cursor.png");
    public static final ImageIcon UnlinkConstraintCursor = loadImage("/icons/sherpa/unlink_constraint_cursor.png");
  }

  public static class Views {
    public static final Icon AbsoluteLayout = load("/icons/views/AbsoluteLayout.png"); // 16x16
    public static final Icon AdapterViewFlipper = load("/icons/views/AdapterViewFlipper.png"); // 16x16
    public static final Icon AdView = load("/icons/views/AdView.png"); // 16x16
    public static final Icon AnalogClock = load("/icons/views/AnalogClock.png"); // 16x16
    public static final Icon AppbarLayout = load("/icons/views/AppBarLayout.png"); // 16x16
    public static final Icon AutoCompleteTextView = load("/icons/views/AutoCompleteTextView.png"); // 16x16
    public static final Icon BrowseFragment = load("/icons/views/BrowseFragment.png"); // 16x16
    public static final Icon Button = load("/icons/views/Button.png"); // 16x16
    public static final Icon CardView = load("/icons/views/CardView.png"); // 16x16
    public static final Icon CalendarView = load("/icons/views/CalendarView.png"); // 16x16
    public static final Icon CheckBox = load("/icons/views/CheckBox.png"); // 16x16
    public static final Icon CheckedTextView = load("/icons/views/CheckedTextView.png"); // 16x16
    public static final Icon Chronometer = load("/icons/views/Chronometer.png"); // 16x16
    public static final Icon CoordinatorLayout = load("/icons/views/CoordinatorLayout.png"); // 16x16
    public static final Icon DatePicker = load("/icons/views/DatePicker.png"); // 16x16
    public static final Icon DetailsFragment = load("/icons/views/DetailsFragment.png"); // 16x16
    public static final Icon DeviceScreen = load("/icons/views/DeviceScreen.png"); // 16x16
    public static final Icon DialerFilter = load("/icons/views/DialerFilter.png"); // 16x16
    public static final Icon DigitalClock = load("/icons/views/DigitalClock.png"); // 16x16
    public static final Icon EditText = load("/icons/views/EditText.png"); // 16x16
    public static final Icon ExpandableListView = load("/icons/views/ExpandableListView.png"); // 16x16
    public static final Icon FloatingActionButton = load("/icons/views/FloatingActionButton.png"); // 16x16
    public static final Icon Fragment = load("/icons/views/fragment.png"); // 16x16
    public static final Icon FrameLayout = load("/icons/views/FrameLayout.png"); // 16x16
    public static final Icon Gallery = load("/icons/views/Gallery.png"); // 16x16
    public static final Icon GestureOverlayView = load("/icons/views/GestureOverlayView.png"); // 16x16
    public static final Icon GridLayout = load("/icons/views/GridLayout.png"); // 16x16
    public static final Icon GridView = load("/icons/views/GridView.png"); // 16x16
    public static final Icon HorizontalScrollView = load("/icons/views/HorizontalScrollView.png"); // 16x16
    public static final Icon ImageButton = load("/icons/views/ImageButton.png"); // 16x16
    public static final Icon ImageSwitcher = load("/icons/views/ImageSwitcher.png"); // 16x16
    public static final Icon ImageView = load("/icons/views/ImageView.png"); // 16x16
    public static final Icon Include = load("/icons/views/include.png"); // 16x16
    public static final Icon LinearLayout = load("/icons/views/LinearLayout.png"); // 16x16
    public static final Icon VerticalLinearLayout = load("/icons/views/VerticalLinearLayout.png"); // 16x16
    public static final Icon LinearLayout3 = load("/icons/views/LinearLayout3.png"); // 16x16
    public static final Icon ListView = load("/icons/views/ListView.png"); // 16x16
    public static final Icon MapFragment = load("/icons/views/MapFragment.png"); // 16x16
    public static final Icon MapView = load("/icons/views/MapView.png"); // 16x16
    public static final Icon MediaController = load("/icons/views/MediaController.png"); // 16x16
    public static final Icon Merge = load("/icons/views/merge.png"); // 16x16
    public static final Icon MultiAutoCompleteTextView = load("/icons/views/MultiAutoCompleteTextView.png"); // 16x16
    public static final Icon NestedScrollView = load("/icons/views/NestedScrollView.png"); // 16x16
    public static final Icon NumberPicker = load("/icons/views/NumberPicker.png"); // 16x16
    public static final Icon PlaybackOverlayFragment = load("/icons/views/PlaybackOverlayFragment.png"); // 16x16
    public static final Icon ProgressBar = load("/icons/views/ProgressBar.png"); // 16x16
    public static final Icon QuickContactBadge = load("/icons/views/QuickContactBadge.png"); // 16x16
    public static final Icon RadioButton = load("/icons/views/RadioButton.png"); // 16x16
    public static final Icon RadioGroup = load("/icons/views/RadioGroup.png"); // 16x16
    public static final Icon RatingBar = load("/icons/views/RatingBar.png"); // 16x16
    public static final Icon RecyclerView = load("/icons/views/RecyclerView.png"); // 16x16
    public static final Icon RelativeLayout = load("/icons/views/RelativeLayout.png"); // 16x16
    public static final Icon RequestFocus = load("/icons/views/requestFocus.png"); // 16x16
    public static final Icon ScrollView = load("/icons/views/ScrollView.png"); // 16x16
    public static final Icon SearchFragment = load("/icons/views/SearchFragment.png"); // 16x16
    public static final Icon SearchView = load("/icons/views/SearchView.png"); // 16x16
    public static final Icon SeekBar = load("/icons/views/SeekBar.png"); // 16x16
    public static final Icon SlidingDrawer = load("/icons/views/SlidingDrawer.png"); // 16x16
    public static final Icon Space = load("/icons/views/Space.png"); // 16x16
    public static final Icon Spinner = load("/icons/views/Spinner.png"); // 16x16
    public static final Icon StackView = load("/icons/views/StackView.png"); // 16x16
    public static final Icon SurfaceView = load("/icons/views/SurfaceView.png"); // 16x16
    public static final Icon Switch = load("/icons/views/Switch.png"); // 16x16
    public static final Icon TabHost = load("/icons/views/TabHost.png"); // 16x16
    public static final Icon TableLayout = load("/icons/views/TableLayout.png"); // 16x16
    public static final Icon TableRow = load("/icons/views/TableRow.png"); // 16x16
    public static final Icon TabWidget = load("/icons/views/TabWidget.png"); // 16x16
    public static final Icon TextClock = load("/icons/views/TextClock.png"); // 16x16
    public static final Icon TextInputLayout = load("/icons/views/TextInputLayout.png"); // 16x16
    public static final Icon TextSwitcher = load("/icons/views/TextSwitcher.png"); // 16x16
    public static final Icon TextureView = load("/icons/views/TextureView.png"); // 16x16
    public static final Icon TextView = load("/icons/views/TextView.png"); // 16x16
    public static final Icon TimePicker = load("/icons/views/TimePicker.png"); // 16x16
    public static final Icon ToggleButton = load("/icons/views/ToggleButton.png"); // 16x16
    public static final Icon Toolbar = load("/icons/views/Toolbar.png"); // 16x16
    public static final Icon TwoLineListItem = load("/icons/views/TwoLineListItem.png"); // 16x16
    public static final Icon Unknown = load("/icons/views/customView.png"); // 16x16
    public static final Icon VideoView = load("/icons/views/VideoView.png"); // 16x13
    public static final Icon View = load("/icons/views/View.png"); // 16x16
    public static final Icon ViewAnimator = load("/icons/views/ViewAnimator.png"); // 16x16
    public static final Icon ViewFlipper = load("/icons/views/ViewFlipper.png"); // 16x16
    public static final Icon ViewStub = load("/icons/views/ViewStub.png"); // 16x16
    public static final Icon ViewSwitcher = load("/icons/views/ViewSwitcher.png"); // 16x16
    public static final Icon WebView = load("/icons/views/WebView.png"); // 16x16
    public static final Icon ZoomButton = load("/icons/views/ZoomButton.png"); // 16x16
    public static final Icon ZoomControls = load("/icons/views/ZoomControls.png"); // 16x16
  }

  public static class Versions {
    public static final Icon Froyo = load("/icons/versions/Froyo.png"); // 128x128
    public static final Icon Gingerbread = load("/icons/versions/Gingerbread.png"); // 512x512
    public static final Icon Honeycomb = load("/icons/versions/Honeycomb.png"); // 128x128
    public static final Icon IceCreamSandwich = load("/icons/versions/IceCreamSandwich.png"); // 128x128
    public static final Icon JellyBean = load("/icons/versions/Jelly Bean.png"); // 128x128
    public static final Icon KitKat = load("/icons/versions/KitKat.png"); // 128x128
    public static final Icon Lollipop = load("/icons/versions/Lollipop.png"); // 128x128
    public static final Icon Marshmallow = load("/icons/versions/Marshmallow.png"); // 128x128
  }

  public static class ModuleTemplates {
    public static final Icon Wear = load("/icons/module_templates/wear_module.png"); // 512x512
    public static final Icon Car = load("/icons/module_templates/car_module.png"); // 512x512
    public static final Icon Glass = load("/icons/module_templates/glass_module.png"); // 512x512
    public static final Icon Mobile = load("/icons/module_templates/mobile_module.png"); // 512x512
    public static final Icon Tv = load("/icons/module_templates/tv_module.png"); // 512x512
    public static final Icon Android = load("/icons/module_templates/android_module.png"); // 512x512
    public static final Icon EclipseModule = load("/icons/module_templates/eclipse_module.png"); // 512x512
    public static final Icon GradleModule = load("/icons/module_templates/gradle_module.png"); // 512x512
  }

  public static class ToolWindows {
    public static final Icon HeapAnalysis = IconLoader.getIcon("/icons/toolwindows/toolWindowHeapAnalysis.png"); // 13x13
    public static final Icon Warning = IconLoader.getIcon("/icons/toolwindows/toolWindowWarning.png"); // 13x13
  }

  public static class GfxTrace {
    public static final Icon TraceFile = load("/icons/gfxtrace/trace_file.png"); // 16x16
    public static final Icon ListenForTrace = load("/icons/gfxtrace/listen_for_trace.png"); // 16x16
    public static final Icon InjectSpy = load("/icons/gfxtrace/inject_spy.png"); // 16x16
    public static final Icon DepthBuffer = load("/icons/gfxtrace/depth_buffer.png"); // 16x16
    public static final Icon ColorBuffer = load("/icons/gfxtrace/color_buffer.png"); // 16x16
    public static final Icon WireframeNone = load("/icons/gfxtrace/wireframe_none.png"); // 16x16
    public static final Icon WireframeOverlay = load("/icons/gfxtrace/wireframe_overlay.png"); // 16x16
    public static final Icon WireframeAll = load("/icons/gfxtrace/wireframe_all.png"); // 16x16
    public static final Icon PointCloud = load("/icons/gfxtrace/point_cloud.png"); // 16x16
    public static final Icon FlipVertically = load("/icons/gfxtrace/flip_vertically.png"); // 16x16
    public static final Icon Opacity = load("/icons/gfxtrace/opacity.png"); // 16x16
    public static final Icon YUp = load("/icons/gfxtrace/yup.png"); // 16x16
    public static final Icon ZUp = load("/icons/gfxtrace/zup.png"); // 16x16
    public static final Icon WindingCCW = load("/icons/gfxtrace/winding_ccw.png"); // 16x16
    public static final Icon WindingCW = load("/icons/gfxtrace/winding_cw.png"); // 16x16
    public static final Icon Smooth = load("/icons/gfxtrace/smooth.png"); // 16x16
    public static final Icon Faceted = load("/icons/gfxtrace/faceted.png"); // 16x16
    public static final Icon CullingEnabled = load("/icons/gfxtrace/culling_enabled.png"); // 16x16
    public static final Icon CullingDisabled = load("/icons/gfxtrace/culling_disabled.png"); // 16x16
    public static final Icon Lit = load("/icons/gfxtrace/lit.png"); // 16x16
    public static final Icon Flat = load("/icons/gfxtrace/flat.png"); // 16x16
    public static final Icon Normals = load("/icons/gfxtrace/normals.png"); // 16x16
  }
}
