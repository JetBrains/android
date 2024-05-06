/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.adtui.device;

import static com.android.SdkConstants.DOT_PNG;
import static java.awt.RenderingHints.KEY_ANTIALIASING;
import static java.awt.RenderingHints.KEY_INTERPOLATION;
import static java.awt.RenderingHints.KEY_RENDERING;
import static java.awt.RenderingHints.VALUE_ANTIALIAS_ON;
import static java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR;
import static java.awt.RenderingHints.VALUE_RENDER_QUALITY;

import com.android.ninepatch.NinePatch;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Screen;
import com.android.tools.adtui.ImageUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.PathManager;
import com.intellij.ui.Gray;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.UIUtil;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A device frame painter is capable of directly painting a device frame surrounding
 * a given screen shot rectangle, or creating a new {@link BufferedImage} where it paints
 * both a screenshot and a surrounding device frame. It can also answer information about
 * how much extra space in the horizontal and vertical directions a device frame would
 * require (for zoom-to-fit geometry calculations).
 * <p>
 * This class is intended for repeated painting of device frames (e.g. in layout XML
 * preview and in the layout editor); it maintains a cache of composite background + shadow +
 * lighting images at multiple resolutions. It also uses cropping data from the device art
 * descriptor to remove extra padding around the images which is contained in the normal
 * device images and used by the screenshot action.
 */
public class DeviceArtPainter {
  @NotNull private static final DeviceArtPainter ourInstance = new DeviceArtPainter();
  @Nullable private static volatile String ourSystemPath;
  @NotNull private Map<Device,DeviceData> myDeviceData = Maps.newHashMap();
  @Nullable private List<DeviceArtDescriptor> myDescriptors;

  /** Use {@link #getInstance()} */
  private DeviceArtPainter() {
  }

  @NotNull
  public static DeviceArtPainter getInstance() {
    return ourInstance;
  }

  /** Returns true if we have a dedicated frame image for the given device */
  public boolean hasDeviceFrame(@Nullable Device device) {
    DeviceData deviceData = getDeviceData(device);
    if (deviceData == null) {
      return false;
    }
    return !deviceData.getDescriptor().isStretchable();
  }

  @Nullable
  private DeviceData getDeviceData(@Nullable Device device) {
    if (device == null) {
      return null;
    }
    DeviceData data = myDeviceData.get(device);
    if (data == null) {
      DeviceArtDescriptor spec = findDescriptor(device);
      data = new DeviceData(device, spec);
      myDeviceData.put(device, data);
    }
    return data;
  }

  @NotNull
  private DeviceArtDescriptor findDescriptor(@NotNull Device device) {
    String id = device.getId();
    String name = device.getDisplayName();

    // Make generic devices use the frames as well:
    if (id.equals("3.7in WVGA (Nexus One)")) {
      id = "nexus_one";
    } else if (id.equals("4in WVGA (Nexus S)")) {
      id = "nexus_s";
    } else if (id.equals("4.65in 720p (Galaxy Nexus)")) {
      id = "galaxy_nexus";
    } else {
      id = id.replace(' ', '_');
    }
    DeviceArtDescriptor descriptor = findDescriptor(id, name);
    if (descriptor == null) {
      // Fallback to generic stretchable images
      boolean isTablet = isTablet(device);
      descriptor = findDescriptor(isTablet ? "tablet" : "phone", null);
      assert descriptor != null; // These should always exist
    }

    return descriptor;
  }

  public static boolean isTablet(@NotNull Device device) {
    boolean isTablet = false;
    if (device.getId().contains("Tablet")) { // For example "10.1in WXGA (Tablet)"
      isTablet = true;
    }
    else {
      Screen screen = device.getDefaultHardware().getScreen();
      if (screen != null
          && screen.getDiagonalLength() >= Device.MINIMUM_TABLET_SIZE
          && !device.getDefaultHardware().getScreen().isFoldable()) {
        isTablet = true;
      }
    }
    return isTablet;
  }

  @Nullable
  private DeviceArtDescriptor findDescriptor(@NotNull String id, @Nullable String name) {
    for (DeviceArtDescriptor descriptor : getDescriptors()) {
      if (id.equalsIgnoreCase(descriptor.getId()) || name != null && name.equalsIgnoreCase(descriptor.getName())) {
        return descriptor;
      }
    }

    return null;
  }

  @VisibleForTesting
  @NotNull
  List<DeviceArtDescriptor> getDescriptors() {
    if (myDescriptors == null) {
      myDescriptors = DeviceArtDescriptor.getDescriptors(null);
    }

    return myDescriptors;
  }

  /**
   * Paint the device frame for the given device around the screenshot coordinates (x1,y1) to (x2,y2), optionally
   * with glare and shadow effects
   */
  public void paintFrame(@NotNull Graphics g,
                         @NotNull Device device,
                         @NotNull ScreenOrientation orientation,
                         boolean showEffects,
                         int x1,
                         int y1,
                         int height) {
    DeviceData data = getDeviceData(device);
    if (data == null || height == 0) {
      return;
    }

    FrameData frame = data.getFrameData(orientation, Integer.MAX_VALUE);
    BufferedImage image = frame.getImage(showEffects);
    if (image != null) {
      double scale = height / (double)frame.getScreenHeight();
      int dx1 = (int)(x1 - scale * frame.getScreenX());
      int dy1 = (int)(y1 - scale * frame.getScreenY());
      int dx2 = dx1 + (int)(scale * image.getWidth());
      int dy2 = dy1 + (int)(scale * image.getHeight());
      g.drawImage(image,
                  dx1, dy1, dx2, dy2,
                  // sx1, sy1, sx2, sy2
                  0,
                  0,
                  image.getWidth(),
                  image.getHeight(),
                  null);
    }
  }

  /** Creates a frame around the given image, using the given descriptor. */
  public static @NotNull BufferedImage createFrame(@NotNull BufferedImage image, @NotNull DeviceArtDescriptor descriptor) {
    return createFrame(image, descriptor, false, false);
  }

  public static @NotNull BufferedImage createFrame(@NotNull BufferedImage image, @NotNull DeviceArtDescriptor descriptor,
                                                   boolean addShadow, boolean addReflection) {
    double imgAspectRatio = image.getWidth() / (double) image.getHeight();
    ScreenOrientation orientation = imgAspectRatio >= (1 - ImageUtils.EPSILON) ? ScreenOrientation.LANDSCAPE : ScreenOrientation.PORTRAIT;

    if (!descriptor.canFrameImage(image, orientation)) {
      return image;
    }

    File shadow = descriptor.getDropShadow(orientation);
    File background = descriptor.getFrame(orientation);
    File reflection = descriptor.getReflectionOverlay(orientation);

    Graphics2D g2d = null;
    try {
      BufferedImage bg = ImageIO.read(background);
      Dimension screen = descriptor.getScreenSize(orientation); // Size of screen in ninepatch; will be stretched
      Dimension frameSize = descriptor.getFrameSize(orientation); // Size of full ninepatch, including stretchable screen area
      Point screenPos = descriptor.getScreenPos(orientation);
      boolean stretchable = descriptor.isStretchable();
      if (stretchable) {
        assert screen != null;
        assert frameSize != null;
        int newWidth = image.getWidth() + frameSize.width - screen.width;
        int newHeight = image.getHeight() + frameSize.height - screen.height;
        bg = stretchImage(bg, newWidth, newHeight);
      } else if (screen.width < image.getWidth()) {
        // if the frame isn't stretchable, but is smaller than the image, then scale down the image
        double scale = (double) screen.width / image.getWidth();
        if (Math.abs(scale - 1.0) > ImageUtils.EPSILON) {
          image = ImageUtils.scale(image, scale, scale);
        }
      }
      g2d = bg.createGraphics();

      if (addShadow && shadow != null) {
        BufferedImage shadowImage = ImageIO.read(shadow);
        if (stretchable) {
          shadowImage = stretchImage(shadowImage, bg.getWidth(), bg.getHeight());
        }
        g2d.drawImage(shadowImage, 0, 0, null, null);
      }

      // If the device art has a mask, make sure that the image is clipped by the mask
      File maskFile = descriptor.getMask(orientation);
      if (maskFile != null) {
        BufferedImage mask = ImageIO.read(maskFile);

        // Render the current image on top of the mask using it as the alpha composite
        Graphics2D maskG2d = mask.createGraphics();
        maskG2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_IN));
        maskG2d.drawImage(image, screenPos.x, screenPos.y, null);
        maskG2d.dispose();

        // Render the masked image to the destination
        g2d.drawImage(mask, 0, 0, null);
      }
      else {
        g2d.drawImage(image, screenPos.x, screenPos.y, null);
      }

      if (addReflection && reflection != null) { // Nexus One for example does not supply reflection image
        BufferedImage reflectionImage = ImageIO.read(reflection);
        if (stretchable) {
          reflectionImage = stretchImage(reflectionImage, bg.getWidth(), bg.getHeight());
        }
        g2d.drawImage(reflectionImage, 0, 0, null, null);
      }
      return bg;
    }
    catch (IOException e) {
      return image;
    }
    finally {
      if (g2d != null) {
        g2d.dispose();
      }
    }
  }

  @Nullable
  private static Shape getClip(@Nullable Device device, int x, int y, int width, int height) {
    boolean round = device != null && device.isScreenRound();
    if (round) {
      int slop = 3; // to hide mask aliasing effects under device chrome by a pixel or two
      return new Ellipse2D.Double(x - slop, y - slop, width + 2 * slop, height + 2 * slop);
    }

    return null;
  }


  /** Paints a rendered device image into the given graphics context  */
  public static void paintClipped(@NotNull Graphics2D g,
                                  @NotNull BufferedImage image,
                                  @Nullable Device device,
                                  int x,
                                  int y,
                                  boolean withRetina) {
    Shape prevClip = null;
    Shape clip = getClip(device, x, y, image.getWidth(), image.getHeight());
    if (clip != null) {
      prevClip = g.getClip();
      g.setClip(clip);
    }

    if (withRetina) {
      //noinspection ConstantConditions
      UIUtil.drawImage(g, image, x, y, null);
    } else {
      g.drawImage(image, x, y, null);
    }

    if (clip != null) {
      g.setClip(prevClip);
    }
  }

  @NotNull
  public BufferedImage createFrame(@NotNull BufferedImage image,
                                   @NotNull Device device,
                                   @NotNull ScreenOrientation orientation,
                                   boolean showEffects,
                                   double scale,
                                   @Nullable Rectangle outViewRectangle) {
    BufferedImage scaledImage = ImageUtils.scale(image, scale, scale, 0, 0);
    DeviceData data = getDeviceData(device);
    int scaledHeight = (int)(scale * image.getHeight());
    if (data == null || scaledHeight == 0) {
      return scaledImage;
    }

    // Tweak the scale down slightly; without this, rounding errors can lead to the frame image
    // being one or two pixels larger than the screen, such that the underlying theme background
    // shines through, which is quite visible on a black phone frame with a black navigation bar
    // for example
    scale = (scaledHeight - 1) / (double)image.getHeight();

    int scaledWidth = (int)(image.getWidth() * scale);

    boolean portrait = orientation != ScreenOrientation.LANDSCAPE;
    FrameData frame = portrait ? data.getPortraitData(scaledHeight) : data.getLandscapeData(scaledHeight);

    BufferedImage frameImage = frame.getImage(showEffects);
    if (frameImage != null) {

      int framedWidth = (int)(image.getWidth() * scale * frame.getFrameWidth() / (double) frame.getScreenWidth());
      int framedHeight = (int)(image.getHeight() * scale * frame.getFrameHeight() / (double) frame.getScreenHeight());
      if (framedWidth <= 0 || framedHeight <= 0) {
        return scaledImage;
      }

      double downScale = framedHeight / (double)frame.getFrameHeight();
      int screenX = (int)(downScale * frame.getScreenX());
      int screenY = (int)(downScale * frame.getScreenY());

      @SuppressWarnings("UndesirableClassUsage") // Don't need Retina image here, and it's more expensive
      BufferedImage result = new BufferedImage(framedWidth, framedHeight, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = result.createGraphics();
      g.setColor(Gray.TRANSPARENT);
      g.fillRect(0, 0, result.getWidth(), result.getHeight());

      paintClipped(g, scaledImage, device, screenX, screenY, false);

      BufferedImage scaledFrameImage = ImageUtils.scale(frameImage, downScale, downScale, 0, 0);
      g.drawImage(scaledFrameImage, 0, 0, null);
      g.dispose();

      if (outViewRectangle != null) {
        outViewRectangle.x = screenX;
        outViewRectangle.y = screenY;
        outViewRectangle.width = scaledWidth;
        outViewRectangle.height = scaledHeight;
      }

      return result;
    }

    return scaledImage;
  }

  @Nullable
  public Rectangle computeBounds(int imageWidth,
                                 int imageHeight,
                                 @NotNull Device device,
                                 @NotNull ScreenOrientation orientation,
                                 double scale) {
    DeviceData data = getDeviceData(device);
    int scaledHeight = (int)(scale * imageHeight);
    if (data == null || scaledHeight == 0) {
      return null;
    }

    // Tweak the scale down slightly; without this, rounding errors can lead to the frame image
    // being one or two pixels larger than the screen, such that the underlying theme background
    // shines through, which is quite visible on a black phone frame with a black navigation bar
    // for example
    scale = (scaledHeight - 1) / (double)imageHeight;

    int scaledWidth = (int)(imageWidth * scale);
    boolean portrait = orientation != ScreenOrientation.LANDSCAPE;
    FrameData frame = portrait ? data.getPortraitData(scaledHeight) : data.getLandscapeData(scaledHeight);
    int framedWidth = (int)(imageWidth * scale * frame.getFrameWidth() / (double) frame.getScreenWidth());
    int framedHeight = (int)(imageHeight * scale * frame.getFrameHeight() / (double) frame.getScreenHeight());
    if (framedWidth <= 0 || framedHeight <= 0) {
      return null;
    }

    double downScale = framedHeight / (double)frame.getFrameHeight();
    int screenX = (int)(downScale * frame.getScreenX());
    int screenY = (int)(downScale * frame.getScreenY());

    return new Rectangle(screenX, screenY, scaledWidth, scaledHeight);
  }

  @NotNull
  private static BufferedImage stretchImage(BufferedImage image, int width, int height) {
    @SuppressWarnings("UndesirableClassUsage") // Don't need Retina image here, and it's more expensive
    BufferedImage composite = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = composite.createGraphics();
    g.setColor(Gray.TRANSPARENT);
    g.fillRect(0, 0, composite.getWidth(), composite.getHeight());

    NinePatch ninePatch = NinePatch.load(image, true, false);
    assert ninePatch != null;
    ninePatch.draw(g, 0, 0, width, height);
    g.dispose();
    return composite;
  }

  @Nullable
  public Point getScreenPosition(@NotNull Device device, @NotNull ScreenOrientation orientation, int screenHeight) {
    DeviceData data = getDeviceData(device);
    if (data == null) {
      return null;
    }

    FrameData frame = data.getFrameData(orientation, Integer.MAX_VALUE);
    int screenX = frame.getScreenX();
    int screenY = frame.getScreenY();

    double scale = screenHeight / (double) frame.getScreenHeight();
    screenX *= scale;
    screenY *= scale;
    // TODO: Also consider the frame scale?

    return new Point(screenX, screenY);
  }

  /** Like {@link #getFrameWidthOverhead} and {@link #getFrameHeightOverhead}, but returns the max of the two */
  public double getFrameMaxOverhead(@NotNull Device device, @NotNull ScreenOrientation orientation) {
    DeviceData data = getDeviceData(device);
    if (data == null) {
      return 1;
    }

    FrameData frame = data.getFrameData(orientation, Integer.MAX_VALUE);
    return Math.max(frame.getFrameWidth() / (double) frame.getScreenWidth(), frame.getFrameHeight() / (double) frame.getScreenHeight());
  }

  /** Returns how much wider (as a factor of the width of the screenshot) the image will be with a device frame added in */
  public double getFrameWidthOverhead(@NotNull Device device, @NotNull ScreenOrientation orientation) {
    DeviceData data = getDeviceData(device);
    if (data == null) {
      return 1;
    }

    FrameData frame = data.getFrameData(orientation, Integer.MAX_VALUE);
    return frame.getFrameHeight() / (double) frame.getScreenHeight();
  }

  /** Returns how much taller (as a factor of the height of the screenshot) the image will be with a device frame added in */
  public double getFrameHeightOverhead(@NotNull Device device, @NotNull ScreenOrientation orientation) {
    DeviceData data = getDeviceData(device);
    if (data == null) {
      return 1;
    }

    FrameData frame = data.getFrameData(orientation, Integer.MAX_VALUE);
    return frame.getFrameWidth() / (double) frame.getScreenWidth();
  }

  /** Information about a particular device; keeps both portrait and landscape data, as well as multiple target image sizes */
  @VisibleForTesting
  static class DeviceData {
    @NotNull private final DeviceArtDescriptor myDescriptor;
    private final Device myDevice;

    @Nullable private FrameData myPortraitData;
    @Nullable private FrameData myLandscapeData;

    @Nullable private FrameData mySmallPortraitData;
    @Nullable private FrameData mySmallLandscapeData;

    @VisibleForTesting
    DeviceData(Device device, @NotNull DeviceArtDescriptor descriptor) {
      myDevice = device;
      myDescriptor = descriptor;
    }

    /** Derives a new {@link FrameData} from the given one, but with half size assets */
    @NotNull
    private FrameData getSmallFrameData(@NotNull FrameData large) {
      return new FrameData(this, large);
    }

    @NotNull
    public FrameData getFrameData(@NotNull ScreenOrientation orientation, int height) {
      return orientation == ScreenOrientation.PORTRAIT ? getPortraitData(height) : getLandscapeData(height);
    }

    @NotNull
    private FrameData getPortraitData(int height) {
      if (myPortraitData == null) {
        myPortraitData = new FrameData(this, ScreenOrientation.PORTRAIT);
      }

      if (height < myPortraitData.getScreenHeight() / 2) {
        if (mySmallPortraitData == null) {
          mySmallPortraitData = getSmallFrameData(myPortraitData);
        }

        return mySmallPortraitData;
      }

      return myPortraitData;
    }

    @NotNull
    private FrameData getLandscapeData(int height) {
      if (myLandscapeData == null) {
        myLandscapeData = new FrameData(this, ScreenOrientation.LANDSCAPE);
      }

      if (height < myLandscapeData.getScreenHeight() / 2) {
        if (mySmallLandscapeData == null) {
          mySmallLandscapeData = getSmallFrameData(myLandscapeData);
        }

        return mySmallLandscapeData;
      }

      return myLandscapeData;
    }

    @NotNull
    DeviceArtDescriptor getDescriptor() {
      return myDescriptor;
    }

    public Device getDevice() {
      return myDevice;
    }
  }

  /** Information for a particular frame picture of a device (e.g. either landscape or portrait). It can also be the half
   * size of a named larger version (if {@link #myDouble} points to an outer image). */
  @VisibleForTesting
  static class FrameData {
    @NotNull private final DeviceData myDeviceData;
    @NotNull private final ScreenOrientation myOrientation;
    private final int myX;
    private final int myY;
    private final int myWidth;
    private final int myHeight;
    private final int myCropX1;
    private final int myCropY1;
    private final int myCropX2;
    private final int myCropY2;
    private int myFrameWidth;
    private int myFrameHeight;
    private final FrameData myDouble;

    @SuppressWarnings("ConstantConditions")
    @NotNull private SoftReference<BufferedImage> myPlainImage = new SoftReference<BufferedImage>(null);
    @SuppressWarnings("ConstantConditions")
    @NotNull private SoftReference<BufferedImage> myEffectsImage = new SoftReference<BufferedImage>(null);

    private boolean isPortrait() {
      return myOrientation == ScreenOrientation.PORTRAIT;
    }

    private FrameData(@NotNull DeviceData deviceData, @NotNull ScreenOrientation orientation) {
      myDeviceData = deviceData;
      myOrientation = orientation;
      myDouble = null;

      DeviceArtDescriptor descriptor = deviceData.getDescriptor();
      if (!isStretchable()) {
        Dimension fullSize = descriptor.getFrameSize(myOrientation);
        int frameWidth = fullSize.width;
        int frameHeight = fullSize.height;

        Rectangle crop = descriptor.getCrop(myOrientation);
        if (crop != null) {
          myCropX1 = crop.x;
          myCropY1 = crop.y;
          myCropX2 = crop.x + crop.width;
          myCropY2 = crop.y + crop.height;
          frameWidth = crop.width;
          frameHeight = crop.height;
        } else {
          myCropX1 = 0;
          myCropY1 = 0;
          myCropX2 = frameWidth;
          myCropY2 = frameHeight;
        }

        myFrameWidth = frameWidth;
        myFrameHeight = frameHeight;

        Point screenPos = descriptor.getScreenPos(myOrientation);
        myX = screenPos.x - myCropX1;
        myY = screenPos.y - myCropY1;

        Dimension screenSize = descriptor.getScreenSize(myOrientation);
        myWidth = screenSize.width;
        myHeight = screenSize.height;
      } else {
        // Generic device: use stretchable images and pick actual size based on device screen size
        // plus overhead
        Device device = myDeviceData.getDevice();
        Dimension screenSize = device.getScreenSize(myOrientation); // Actual size of screen, e.g. 720x1280
        Dimension screen = descriptor.getScreenSize(myOrientation); // Size of screen in ninepatch; will be stretched
        Dimension frameSize = descriptor.getFrameSize(myOrientation); // Size of full ninepatch, including stretchable screen area
        Point screenPos = descriptor.getScreenPos(myOrientation);
        assert screenSize != null;
        assert screen != null;
        assert frameSize != null;
        myX = screenPos.x;
        myY = screenPos.y;
        myWidth = screenSize.width;
        myHeight = screenSize.height;

        myFrameWidth = myWidth + frameSize.width - screen.width;
        myFrameHeight = myHeight + frameSize.height - screen.height;
        myCropX1 = 0;
        myCropY1 = 0;
        myCropX2 = myFrameWidth;
        myCropY2 = myFrameHeight;
      }
    }

    /**
     * Copies the larger frame data and makes a smaller (half size) version; this is used for faster thumbnail painting
     * during render previews etc
     */
    private FrameData(@NotNull DeviceData deviceData, @NotNull FrameData large) {
      myDeviceData = deviceData;
      myDouble = large;
      myOrientation = large.myOrientation;

      myX = large.myX / 2;
      myY = large.myY / 2;
      myWidth = large.myWidth / 2;
      myHeight = large.myHeight / 2;

      myFrameWidth = large.myFrameWidth / 2;
      myFrameHeight = large.myFrameHeight / 2;

      // Already cropped into the upper image
      myCropX1 = 0;
      myCropY1 = 0;
      myCropX2 = myFrameWidth;
      myCropY2 = myFrameHeight;
    }

    /** Position of the screen within the image (x coordinate) */
    public int getScreenX() {
      return myX;
    }

    /** Position of the screen within the image (y coordinate) */
    public int getScreenY() {
      return myY;
    }

    public int getScreenWidth() {
      return myWidth;
    }

    public int getScreenHeight() {
      return myHeight;
    }

    public int getFrameWidth() {
      return myFrameWidth;
    }

    public int getFrameHeight() {
      return myFrameHeight;
    }

    @Nullable
    private BufferedImage getImage(@Nullable File file) {
      if (file == null) {
        return null;
      }
      assert myDouble == null; // Should be using image from parent
      if (file.exists()) {
        try {
          return ImageIO.read(file);
        }
        catch (IOException e) {
          // pass
        }
      }

      return null;
    }

    private static File getThumbnailCacheDir() {
      final String path = ourSystemPath != null ? ourSystemPath : (ourSystemPath = PathUtil.getCanonicalPath(PathManager.getSystemPath()));
      //noinspection HardCodedStringLiteral
      return new File(path, "android-devices" + File.separator + "v4");
    }

    @NotNull
    private File getCacheFile(boolean showEffects) {
      StringBuilder sb = new StringBuilder(20);
      DeviceArtDescriptor descriptor = myDeviceData.getDescriptor();
      sb.append(descriptor.getId());
      if (isStretchable()) {
        // Generic device
        // Store resolution as well, since we need different pre-cached images for different resolutions
        sb.append('-');
        sb.append(Integer.toString(myWidth));
        sb.append('x');
        sb.append(Integer.toString(myHeight));
      }
      sb.append('-');
      sb.append(isPortrait() ? "port" : "land");
      if (myDouble != null) {
        sb.append("-thumb");
      }
      if (showEffects) {
        sb.append("-effects");
      }
      sb.append(DOT_PNG);
      return new File(getThumbnailCacheDir(), sb.toString());
    }

    private boolean isStretchable() {
      DeviceArtDescriptor descriptor = myDeviceData.getDescriptor();
      return descriptor.isStretchable();
    }

    @Nullable
    private BufferedImage getCachedImage(boolean showEffects) {
      File file = getCacheFile(showEffects);
      if (file.exists()) {
        try {
          return ImageIO.read(file);
        }
        catch (IOException e) {
          // pass
        }
        catch (Throwable e) {
          // pass: corrupt cached image, e.g. I've seen
          //  java.lang.IndexOutOfBoundsException
          //  at java.io.RandomAccessFile.readBytes(Native Method)
          //  at java.io.RandomAccessFile.read(RandomAccessFile.java:338)
          //  at javax.imageio.stream.FileImageInputStream.read(FileImageInputStream.java:101)
          //  at com.sun.imageio.plugins.common.SubImageInputStream.read(SubImageInputStream.java:46)
        }
      }

      return null;
    }

    private void putCachedImage(boolean showEffects, @NotNull BufferedImage image) {
      File dir = getThumbnailCacheDir();
      if (!dir.exists()) {
        boolean ok = dir.mkdirs();
        if (!ok) {
          return;
        }
      }
      File file = getCacheFile(showEffects);
      if (file.exists()) {
        boolean deleted = file.delete();
        if (!deleted) {
          return;
        }
      }
      try {
        ImageIO.write(image, "PNG", file);
      }
      catch (IOException e) {
        // pass
        if (file.exists()) {
          //noinspection ResultOfMethodCallIgnored
          file.delete();
        }
      }
    }

    @Nullable
    public BufferedImage getImage(boolean showEffects) {
      BufferedImage image = showEffects ? myEffectsImage.get() : myPlainImage.get();
      if (image != null) {
        return image;
      }

      image = getCachedImage(showEffects);
      if (image == null) {
        image = computeImage(showEffects, myCropX1, myCropY1, myCropX2, myCropY2);
        if (image != null) {
          putCachedImage(showEffects, image);
        }
      }

      if (image != null) {
        if (showEffects) {
          myEffectsImage = new SoftReference<BufferedImage>(image);
        } else {
          myPlainImage = new SoftReference<BufferedImage>(image);
        }
      }

      return image;
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    @VisibleForTesting
    @Nullable
    BufferedImage computeImage(boolean showEffects, int cropX1, int cropY1, int cropX2, int cropY2) {
      if (myDouble != null) {
        BufferedImage source = myDouble.getImage(showEffects);
        if (source != null) {
          int sourceWidth = source.getWidth();
          int sourceHeight = source.getHeight();
          int destWidth = sourceWidth / 2;
          int destHeight = sourceHeight / 2;
          @SuppressWarnings("UndesirableClassUsage") // Don't need Retina image here, and it's more expensive
          BufferedImage dest = new BufferedImage(destWidth, destHeight, BufferedImage.TYPE_INT_ARGB);
          Graphics2D g = dest.createGraphics();
          g.setComposite(AlphaComposite.Src);
          //noinspection UseJBColor
          g.setColor(new Color(0, true));
          g.fillRect(0, 0, destWidth, destHeight);

          g.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_BILINEAR);
          g.setRenderingHint(KEY_RENDERING, VALUE_RENDER_QUALITY);
          g.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);
          g.drawImage(source, 0, 0, destWidth, destHeight, 0, 0, sourceWidth, sourceHeight, null);
          g.dispose();

          return dest;
        }
        assert false;
      }

      DeviceArtDescriptor descriptor = myDeviceData.getDescriptor();
      BufferedImage background = getImage(descriptor.getFrame(myOrientation));
      if (background == null) {
        return null;
      }

      boolean stretchable = isStretchable();
      if (stretchable) {
        background = stretchImage(background, myFrameWidth, myFrameHeight);
      }

      @SuppressWarnings("UndesirableClassUsage") // Don't need Retina image here, and it's more expensive
      BufferedImage composite = new BufferedImage(myFrameWidth, myFrameHeight, BufferedImage.TYPE_INT_ARGB);
      Graphics g = composite.createGraphics();
      g.setColor(Gray.TRANSPARENT);
      g.fillRect(0, 0, composite.getWidth(), composite.getHeight());

      Graphics2D g2d = (Graphics2D)g;
      g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

      BufferedImage mask = getImage(descriptor.getMask(myOrientation));

      // Draw background shadow, if effects are enabled
      if (showEffects) {
        BufferedImage shadow = getImage(descriptor.getDropShadow(myOrientation));
        if (shadow != null) {
          if (stretchable) {
            shadow = stretchImage(shadow, myFrameWidth, myFrameHeight);
          }

          g.drawImage(shadow, 0, 0, myFrameWidth, myFrameHeight, cropX1, cropY1, cropX2, cropY2, null);
        }

        // Ensure that the shadow background doesn't overlap the transparent screen rectangle in the middle
        //noinspection UseJBColor
        g.setColor(new Color(0, true));
        Composite prevComposite = g2d.getComposite();
        // Wipe out alpha channel
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC, 1.0f));

        if (mask != null) {
          g2d.setComposite(AlphaComposite.DstOut);
          g2d.drawImage(mask, -cropX1, -cropY1, null);
        } else {
          g2d.fillRect(myX, myY, myWidth, myHeight);
        }
        g2d.setComposite(prevComposite);
      }

      if (mask != null) {
        @SuppressWarnings("UndesirableClassUsage")
        BufferedImage maskedImage = new BufferedImage(background.getWidth(), background.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D maskGraphics = maskedImage.createGraphics();
        maskGraphics.drawImage(background, 0, 0, null);
        Composite prevComposite = g2d.getComposite();
        maskGraphics.setComposite(AlphaComposite.DstOut);
        maskGraphics.drawImage(mask, 0, 0, null);
        maskGraphics.dispose();
        g2d.setComposite(prevComposite);
        g.drawImage(maskedImage, 0, 0, myFrameWidth, myFrameHeight, cropX1, cropY1, cropX2, cropY2, null);
      } else {
        // More efficient painting of the hole

        /*

             A+-------------------------------+B
              |                               |
              |                               |
              |   myX,myY                     |
             C|     D+------------------+E    |F
              |      |                  |     |
              |      |                  |     |
              |      |                  |     |
              |      | myHeight         |     | background.getHeight
              |      |                  |     |
              |      |                  |     |
              |      |                  |     |
             G|     H+------------------+I    |J
              |            myWidth            |
              |                               |
              |                               |
             K+-------------------------------+L
                      background.getWidth
         */

        // Paint background:
        // Rectangle ABFC
        // Rectangle CDHG
        // Rectangle EFJI
        // Rectangle GJLK
        int dax = 0;
        int day = 0;
        int dcy = myY;
        int dex = myX + myWidth;
        int dfx = myFrameWidth;
        int dgy = myY + myHeight;
        int dhx = myX;
        int dly = myFrameHeight;


        int ax = cropX1;
        int ay = cropY1;
        int cy = cropY1 + myY;
        int ex = cropX1 + myX + myWidth;
        int fx = cropX2;
        int gy = cropY1 + myY + myHeight;
        int hx = cropX1 + myX;
        int ly = cropY2;

        // Draw rectangle ABFC
        g.drawImage(background, dax, day, dfx, dcy, ax, ay, fx, cy, null);
        // Draw rectangle GJKL
        g.drawImage(background, dax, dgy, dfx, dly, ax, gy, fx, ly, null);
        // Draw rectangle CDHG
        g.drawImage(background, dax, dcy, dhx, dgy, ax, cy, hx, gy, null);
        // Draw rectangle EFJI
        g.drawImage(background, dex, dcy, dfx, dgy, ex, cy, fx, gy, null);
      }

      // Draw screen glare, if effects are enabled
      if (showEffects) {
        BufferedImage glare = getImage(descriptor.getReflectionOverlay(myOrientation));
        if (glare != null) {
          if (stretchable) {
            glare = stretchImage(glare, myFrameWidth, myFrameHeight);
          }
          g.drawImage(glare, 0, 0, myFrameWidth, myFrameHeight, cropX1, cropY1, cropX2, cropY2, null);
        }
      }

      g.dispose();

      return composite;
    }
  }
}
