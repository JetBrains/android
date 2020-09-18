package icons;

import com.intellij.openapi.util.IconLoader;
import javax.swing.Icon;

public class AndroidIcons {
  private static Icon load(String path) {
    return IconLoader.getIcon(path, AndroidIcons.class);
  }

  public static final Icon Android = load("/icons/android.svg"); // 16x16

  public static final Icon NotMatch = load("/icons/notMatch.png");

  public static final Icon Activity = load("/icons/activity.png"); // 16x16

  public static final Icon EmptyFlag = load("/icons/flags/flag_empty.png"); // 16x16


  public static final Icon Variant = load("/icons/variant.png");

  public static class Wizards {
    public static final Icon StudioProductIcon = load("/icons/wizards/studio_product.svg"); // 64x64
    // Template thumbnails
    public static final Icon AndroidModule = load("/icons/wizards/android_module.png"); // 256x256
    public static final Icon AutomotiveModule = load("/icons/wizards/automotive_module.png"); // 256x256
    public static final Icon BenchmarkModule = load("/icons/wizards/benchmark_module.png"); // 256x256
    public static final Icon DynamicFeatureModule = load("/icons/wizards/dynamic_feature_module.png"); // 256x256
    public static final Icon EclipseModule = load("/icons/wizards/eclipse_module.png"); // 256x256
    public static final Icon GradleModule = load("/icons/wizards/gradle_module.png"); // 256x256
    public static final Icon InstantDynamicFeatureModule = load("/icons/wizards/instant_dynamic_feature_module.png"); // 256x256
    public static final Icon MobileModule = load("/icons/wizards/mobile_module.png"); // 256x256
    public static final Icon ThingsModule = load("/icons/wizards/things_module.png"); // 256x256
    public static final Icon TvModule = load("/icons/wizards/tv_module.png"); // 256x256
    public static final Icon WearModule = load("/icons/wizards/wear_module.png"); // 256x256
    public static final Icon NoActivity = load("/icons/wizards/no_activity.png"); // 256x256
  }

  public static class DeviceExplorer {
    public static final Icon DatabaseFolder = load("/icons/explorer/DatabaseFolder.png"); // 16x16
    public static final Icon DevicesLineup = load("/icons/explorer/devices-lineup.png"); // 300x150
  }

  public static class Assistant {
    public static final Icon TutorialIndicator = load("/icons/assistant/tutorialIndicator.png"); // 16x16
  }
}
