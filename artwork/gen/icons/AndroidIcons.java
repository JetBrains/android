package icons;

import com.intellij.openapi.util.IconLoader;
import javax.swing.Icon;

public class AndroidIcons {
  private static Icon load(String path) {
    return IconLoader.getIcon(path, AndroidIcons.class);
  }

  public static class DeviceExplorer {
    public static final Icon DevicesLineup = load("/icons/explorer/devices-lineup.png"); // 300x150
  }
}
