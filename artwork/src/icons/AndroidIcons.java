package icons;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * Note: This file should be auto generated once build/scripts/icons.gant is part of CE.
 * https://youtrack.jetbrains.com/issue/IDEA-103558
 */
public class AndroidIcons {
  private static Icon load(String path) {
    return IconLoader.getIcon(path, AndroidIcons.class);
  }

  public static final Icon Android = load("/icons/android.svg"); // 16x16

  public static final Icon AvdManager = load("/icons/avd_manager.png"); // 16x16
  public static final Icon SdkManager = load("/icons/sdk_manager.png"); // 16x16

  public static final Icon Renderscript = load("/icons/render-script.png"); // 16x16
  public static final Icon GreyArrowDown = load("/icons/dropArrow.png"); // 20x20
  public static final Icon NotMatch = load("/icons/notMatch.png");

  public static final Icon AndroidFile = load("/icons/android_file.png"); // 16x16
  public static final Icon Activity = load("/icons/activity.png"); // 16x16
  public static final Icon Targets = load("/icons/targets.png"); // 16x16
  public static final Icon Square = load("/icons/square.png"); // 16x16
  public static final Icon Landscape = load("/icons/landscape.png"); // 16x16
  public static final Icon Portrait = load("/icons/portrait.png"); // 16x16
  public static final Icon Display = load("/icons/display.png"); // 16x16
  public static final Icon ThemesPreview = load("/icons/themesPreview.png"); // 13x13

  public static final Icon EmptyFlag = load("/icons/flags/flag_empty.png"); // 16x16


  public static final Icon Variant = load("/icons/variant.png");

  public static final Icon GreyQuestionMark = load("/icons/grey_question.png"); // 23x23

  public static class Ddms {
    public static final Icon Emulator2 = load("/icons/ddms/emulator_02.png"); // 16x16
    /**
     * The {@link #LogcatAutoFilterSelectedPid} icon is a copy of the AllIcons.Diff.diff icon to be replaced if we get a better icon.
     */
    public static final Icon RealDevice = load("/icons/ddms/real_device.png"); // 16x16
    public static final Icon EmulatorDevice = load("/icons/ddms/emulator_device.png"); // 16x16
    public static final Icon ScreenCapture = load("/icons/ddms/screen_capture.png"); // 16x16
    public static final Icon ScreenRecorder = load("/icons/ddms/screen_recorder.png"); // 16x16
  }

  public static class Wizards {
    public static final Icon StudioProductIcon = load("/icons/wizards/studio_product.png"); // 60x60
    public static final Icon NewModuleSidePanel = load("/icons/wizards/newModule.png"); // 143x627
    public static final Icon NavigationDrawer = load("/icons/wizards/navigation/navigation_drawer.png"); // 256x256
    public static final Icon BottomNavigation = load("/icons/wizards/navigation/bottom_navigation.png"); // 256x256
    public static final Icon NavigationTabs = load("/icons/wizards/navigation/navigation_tabs.png"); // 256x256
    /**
     * @deprecated Name is bad - this icon is used outside of project creation. Migrate to StudioProductIcon.
     * TODO: Post wizard migration: delete constant and welcome_green.png
     */
    public static final Icon NewProjectMascotGreen = load("/icons/wizards/welcome_green.png"); // 60x60
    public static final Icon CppConfiguration = load("/icons/wizards/cpp_configure.png"); // 256x256
  }

  public static class NeleIcons {
    // All 16x16 and 32x32 in Retina mode
    public static final Icon DeviceScreen = load("/icons/nele/DeviceScreen.png");
  }

  public static class RunIcons {
    public static final Icon Replay = load("/icons/run/play2.png"); // TODO: update blaze and remove this
    public static final Icon Restart = load("/icons/run/restart.png");
  }

  public static class SherpaIcons {
    public static final Icon Layer = load("/icons/sherpa/switch_blueprint_off.png");
  }

  public static class ToolWindows {
    public static final Icon Warning = IconLoader.getIcon("/icons/toolwindows/toolWindowWarning.svg"); // 13x13
  }

  public static class Issue {
    public static final Icon ErrorBadge = load("/icons/nele/issue/error-badge.png"); // 8x8
    public static final Icon WarningBadge = load("/icons/nele/issue/warning-badge.png"); // 8x8
  }

  public static class DeviceExplorer {
    public static final Icon DatabaseFolder = load("/icons/explorer/DatabaseFolder.png"); // 16x16
    public static final Icon DevicesLineup = load("/icons/explorer/devices-lineup.png"); // 300x150
  }

  public static class Assistant {
    public static final Icon TutorialIndicator = load("/icons/assistant/tutorialIndicator.png"); // 16x16
  }

  public static class Mockup {
    public static final Icon Mockup = load("/icons/mockup/mockup.png"); // 16x16
    public static final Icon Crop = load("/icons/mockup/crop.png"); // 16x16
    public static final Icon CreateWidget = load("/icons/mockup/mockup_add.png"); // 16x16
    public static final Icon CreateLayout = load("/icons/mockup/new_layout.png"); // 16x16
    public static final Icon MatchWidget = load("/icons/mockup/aspect_ratio.png"); // 16x16
    public static final Icon NoMockup = load("/icons/mockup/no_mockup.png"); // 100x100
    public static final Icon ExtractBg = load("/icons/mockup/extract_bg.png"); // 16x16
  }
}
