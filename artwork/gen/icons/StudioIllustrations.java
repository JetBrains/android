package icons;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * Generated file from illustrations mapping process.  DO NOT EDIT DIRECTLY
 **/
public class StudioIllustrations {
  // Collections of constants, do not instantiate.
  private StudioIllustrations() {}

  private static Icon load(String path) {
    return IconLoader.getIcon(path, StudioIllustrations.class);
  }

  public static class Common {
    public static final Icon DISCONNECT_PROFILER = load("/studio/illustrations/common/disconnect-profiler.svg"); // 171x97
    public static final Icon DISCONNECT = load("/studio/illustrations/common/disconnect.svg"); // 171x97
    public static final Icon PRODUCT_ICON = load("/studio/illustrations/common/product-icon.svg"); // 64x64
  }

  public static class FormFactors {
    public static final Icon CAR_LARGE = load("/studio/illustrations/form-factors/car-large.svg"); // 100x100
    public static final Icon CAR = load("/studio/illustrations/form-factors/car.svg"); // 64x64
    public static final Icon GLASS_LARGE = load("/studio/illustrations/form-factors/glass-large.svg"); // 100x100
    public static final Icon GLASS = load("/studio/illustrations/form-factors/glass.svg"); // 64x64
    public static final Icon MOBILE_LARGE = load("/studio/illustrations/form-factors/mobile-large.svg"); // 100x100
    public static final Icon MOBILE = load("/studio/illustrations/form-factors/mobile.svg"); // 64x64
    public static final Icon TV_LARGE = load("/studio/illustrations/form-factors/tv-large.svg"); // 100x100
    public static final Icon TV = load("/studio/illustrations/form-factors/tv.svg"); // 64x64
    public static final Icon WEAR_LARGE = load("/studio/illustrations/form-factors/wear-large.svg"); // 100x100
    public static final Icon WEAR = load("/studio/illustrations/form-factors/wear.svg"); // 64x64
  }

  public static class Wizards {
    public static final Icon ANDROID_MODULE = load("/studio/illustrations/wizards/android-module.svg"); // 512x512
    public static final Icon NO_ACTIVITY = load("/studio/illustrations/wizards/no-activity.svg"); // 512x512
  }
}