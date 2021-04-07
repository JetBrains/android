package icons;

import com.intellij.openapi.util.IconLoader;
import javax.swing.Icon;

public class AndroidIcons {
  private static Icon load(String path) {
    return IconLoader.getIcon(path, AndroidIcons.class);
  }

  public static final Icon Activity = load("/icons/activity.png"); // 16x16

  public static class Wizards {
    public static final Icon StudioProductIcon = load("/icons/wizards/studio_product.svg"); // 64x64
    // Template thumbnails
    public static final Icon AndroidModule = load("/icons/wizards/android_module.png"); // 256x256
    public static final Icon NoActivity = load("/icons/wizards/no_activity.png"); // 256x256
  }

  public static class DeviceExplorer {
    public static final Icon DevicesLineup = load("/icons/explorer/devices-lineup.png"); // 300x150
  }
}
