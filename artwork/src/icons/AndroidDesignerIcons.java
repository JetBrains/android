package icons;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * Note: This file should be auto generated once build/scripts/icons.gant is part of CE.
 * https://youtrack.jetbrains.com/issue/IDEA-103558
 */
public class AndroidDesignerIcons {
  private static Icon load(String path) {
    return IconLoader.getIcon(path, AndroidDesignerIcons.class);
  }

  // Layout actions
  public static final Icon Baseline = load("/icons/baseline.png"); // 16x16
  public static final Icon NoBaseline = load("/icons/nobaseline.png"); // 16x16
  public static final Icon DistributeWeights = load("/icons/distribute.png"); // 16x16
  public static final Icon ClearWeights = load("/icons/clearweights.png"); // 16x16

  // ScrollView/HorizontalScrollView layout actions
  public static final Icon NormalRender = load("/icons/normal-render.png"); // 16x16
  public static final Icon ViewportRender = load("/icons/viewport-render.png"); // 16x16

}
