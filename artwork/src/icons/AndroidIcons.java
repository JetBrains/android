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
  }

  public static class DeviceExplorer {
    public static final Icon DevicesLineup = load("/icons/explorer/devices-lineup.png"); // 300x150
  }
}
