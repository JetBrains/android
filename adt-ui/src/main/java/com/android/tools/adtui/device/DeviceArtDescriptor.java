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

import com.android.SdkConstants;
import com.android.resources.ScreenOrientation;
import com.android.tools.adtui.ImageUtils;
import com.android.tools.idea.util.StudioPathManager;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.android.utils.XmlUtils.getSubTags;

/**
 * Descriptor for a device frame picture (background, shadow, reflection) which can be
 * painted around a screenshot or device rendering
 */
public class DeviceArtDescriptor {
  @NotNull public static final DeviceArtDescriptor NONE = new DeviceArtDescriptor(null, null);

  private final String myId;
  private final String myName;
  private final File myFolder;
  private OrientationData myPortrait;
  private OrientationData myLandscape;

  @NonNls private static final String FN_BASE = "device-art-resources";
  @NonNls private static final String FN_DESCRIPTOR = "device-art.xml";

  /** Returns the absolute path to {@link #FN_BASE} folder, or null if it couldn't be located. */
  @Nullable
  public static File getBundledDescriptorsFolder() {
    // In the IDE distribution, this should be in plugins/android/resources/FN_BASE
    String base = FileUtil.join(PathManager.getHomePath(), "plugins", "android", "resources");
    if (StudioPathManager.isRunningFromSources()) {
      base = StudioPathManager.resolvePathFromSourcesRoot("tools/adt/idea/artwork/resources").toString();
    }
    File dir = new File(base, FN_BASE);
    if (dir.exists() && dir.isDirectory()) {
      return dir;
    }
    return null;
  }

  @Nullable
  private static File getDescriptorFile(@NotNull File folder) {
    File file = new File(folder, FN_DESCRIPTOR);
    return file.isFile() ? file : null;
  }

  private static List<File> getDescriptorFiles(@Nullable File[] additionalRoots) {
    Set<File> roots = new HashSet<File>();

    File base = getBundledDescriptorsFolder();
    if (base != null) {
      roots.add(base);
    }

    if (additionalRoots != null) {
      Collections.addAll(roots, additionalRoots);
    }

    List<File> files = new ArrayList<File>(roots.size());
    for (File root : roots) {
      File file = getDescriptorFile(root);
      if (file != null) {
        files.add(file);
      }
    }

    return files;
  }

  public static List<DeviceArtDescriptor> getDescriptors(@Nullable File[] folders) {
    List<File> files = getDescriptorFiles(folders);
    List<DeviceArtDescriptor> result = new ArrayList<>();

    for (File file : files)
      try {
        String xml = Files.toString(file, Charsets.UTF_8);
        Document document = XmlUtils.parseDocumentSilently(xml, false);
        if (document != null) {
          File baseFolder = file.getParentFile();
          addDescriptors(result, document, baseFolder);
        }
        else {
          Logger.getInstance(DeviceArtDescriptor.class).error("Couldn't parse " + file);
        }
      }
      catch (IOException e) {
        Logger.getInstance(DeviceArtDescriptor.class).error(e);
      }

    return result;
  }

  private DeviceArtDescriptor(@Nullable File baseFolder, @Nullable Element element) {
    if (element == null) {
      myId = myName = "";
      myFolder = null;
    } else {
      myId = element.getAttribute(SdkConstants.ATTR_ID);
      myName = element.getAttribute(SdkConstants.ATTR_NAME);
      myFolder = new File(baseFolder, myId);

      for (Element child : getSubTags(element)) {
        OrientationData orientation = new OrientationData(this, child);
        if (orientation.isPortrait()) {
          myPortrait = orientation;
        } else {
          myLandscape = orientation;
        }
      }
    }
  }

  static void addDescriptors(List<DeviceArtDescriptor> result, Document document, File baseFolder) {
    NodeList deviceList = document.getElementsByTagName("device");
    for (int i = 0; i < deviceList.getLength(); i++) {
      Element element = (Element)deviceList.item(i);
      DeviceArtDescriptor descriptor = new DeviceArtDescriptor(baseFolder, element);
      result.add(descriptor);
    }
  }

  @NotNull
  public String getId() {
    return myId;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public OrientationData getArtDescriptor(@NotNull ScreenOrientation orientation) {
    return orientation == ScreenOrientation.PORTRAIT ? myPortrait : myLandscape;
  }

  public File getBaseFolder() {
    return myFolder;
  }

  public Dimension getScreenSize(@NotNull ScreenOrientation orientation) {
    return getArtDescriptor(orientation).getScreenSize();
  }

  public Point getScreenPos(@NotNull ScreenOrientation orientation) {
    return getArtDescriptor(orientation).getScreenPos();
  }

  public Dimension getFrameSize(@NotNull ScreenOrientation orientation) {
    return getArtDescriptor(orientation).getFrameSize();
  }

  public Rectangle getCrop(@NotNull ScreenOrientation orientation) {
    return getArtDescriptor(orientation).getCrop();
  }

  @Nullable
  public File getFrame(@NotNull ScreenOrientation orientation) {
    return getArtDescriptor(orientation).getBackgroundFile();
  }

  @Nullable
  public File getDropShadow(@NotNull ScreenOrientation orientation) {
    return getArtDescriptor(orientation).getShadowFile();
  }

  @Nullable
  public File getReflectionOverlay(@NotNull ScreenOrientation orientation) {
    return getArtDescriptor(orientation).getReflectionFile();
  }

  @Nullable
  public File getMask(@NotNull ScreenOrientation orientation) {
    return getArtDescriptor(orientation).getMaskFile();
  }

  public double getAspectRatio(ScreenOrientation orientation) {
    return getArtDescriptor(orientation).getAspectRatio();
  }

  public boolean isStretchable() {
    return myId.equals("phone") || myId.equals("tablet");
  }

  /** Returns whether this descriptor can frame the given image. */
  public boolean canFrameImage(BufferedImage image, ScreenOrientation orientation) {
    if (isStretchable()) {
      return true;
    }

    // Not all devices are available in all orientations
    if (orientation == ScreenOrientation.PORTRAIT && myPortrait == null) {
      return false;
    } else if (orientation == ScreenOrientation.LANDSCAPE && myLandscape == null) {
      return false;
    }

    // Don't support framing images smaller than our screen size (we don't want to stretch the image)
    Dimension screenSize = getArtDescriptor(orientation).getScreenSize();
    if (image.getWidth() < screenSize.getWidth() || image.getHeight() < screenSize.getHeight()) {
      return false;
    }

    // Make sure that the aspect ratio is nearly identical to the image aspect ratio
    double imgAspectRatio = image.getWidth() / (double) image.getHeight();
    double descriptorAspectRatio = getAspectRatio(orientation);
    return Math.abs(imgAspectRatio - descriptorAspectRatio) < ImageUtils.EPSILON;
  }

  /** Descriptor for a particular device frame (e.g. a set of images for a particular device in a particular orientation) */
  private static class OrientationData {
    private final DeviceArtDescriptor myDevice;
    private final String myShadowName;
    private final String myBackgroundName;
    private final String myReflectionName;
    private final String myMaskName;
    private final Dimension myScreenSize;
    private final Point myScreenPos;
    private final Dimension myFrameSize;
    private final Rectangle myCrop;
    private final ScreenOrientation myOrientation;

    OrientationData(DeviceArtDescriptor device, Element element) {
      myDevice = device;
      String orientation = element.getAttribute(SdkConstants.ATTR_NAME);
      if ("port".equals(orientation)) {
        myOrientation = ScreenOrientation.PORTRAIT;
      } else {
        assert "land".equals(orientation) : orientation;
        myOrientation = ScreenOrientation.LANDSCAPE;
      }

      myFrameSize = getDimension(element.getAttribute("size"));
      myScreenSize = getDimension(element.getAttribute("screenSize"));
      myScreenPos = getPoint(element.getAttribute("screenPos"));
      myCrop = getRectangle(element.getAttribute("crop"));

      myBackgroundName = getFileName(element, "back");
      myShadowName = getFileName(element, "shadow");
      myReflectionName = getFileName(element, "lights");
      myMaskName = getFileName(element, "mask");
    }

    @Nullable
    private static String getFileName(Element element, String name) {
      return name != null && !name.isEmpty() ? element.getAttribute(name) : null;
    }

    @Nullable
    private static Dimension getDimension(String value) {
      if (value == null || value.isEmpty()) {
        return null;
      }

      int comma = value.indexOf(',');
      if (comma == -1) {
        return null;
      }
      return new Dimension(getInteger(value.substring(0, comma)), getInteger(value.substring(comma + 1)));
    }

    @Nullable
    private static Point getPoint(String value) {
      if (value == null || value.isEmpty()) {
        return null;
      }

      int comma = value.indexOf(',');
      if (comma == -1) {
        return null;
      }
      return new Point(getInteger(value.substring(0, comma)), getInteger(value.substring(comma + 1)));
    }

    @Nullable
    private static Rectangle getRectangle(String value) {
      if (value == null || value.isEmpty()) {
        return null;
      }

      int comma1 = value.indexOf(',');
      if (comma1 == -1) {
        return null;
      }
      int comma2 = value.indexOf(',', comma1 + 1);
      if (comma2 == -1) {
        return null;
      }
      int comma3 = value.indexOf(',', comma2 + 1);
      if (comma3 == -1) {
        return null;
      }
      String x = value.substring(0, comma1);
      String y = value.substring(comma1 + 1, comma2);
      String w = value.substring(comma2 + 1, comma3);
      String h = value.substring(comma3 + 1);
      return new Rectangle(getInteger(x), getInteger(y), getInteger(w), getInteger(h));
    }

    private static int getInteger(String value) {
      return Integer.parseInt(value);
    }

    public boolean isPortrait() {
      return myOrientation == ScreenOrientation.PORTRAIT;
    }

    public Dimension getScreenSize() {
      return myScreenSize;
    }

    public Point getScreenPos() {
      return myScreenPos;
    }

    public Dimension getFrameSize() {
      return myFrameSize;
    }

    public Rectangle getCrop() {
      return myCrop;
    }

    @NotNull
    public ScreenOrientation getOrientation() {
      return myOrientation;
    }

    @Nullable
    public File getBackgroundFile() {
      return !StringUtil.isEmpty(myBackgroundName) ? new File(myDevice.getBaseFolder(), myBackgroundName) : null;
    }

    @Nullable
    public File getShadowFile() {
      return !StringUtil.isEmpty(myShadowName) ? new File(myDevice.getBaseFolder(), myShadowName) : null;
    }

    @Nullable
    public File getReflectionFile() {
      return !StringUtil.isEmpty(myReflectionName) ? new File(myDevice.getBaseFolder(), myReflectionName) : null;
    }

    @Nullable
    public File getMaskFile() {
      return !StringUtil.isEmpty(myMaskName) ? new File(myDevice.getBaseFolder(), myMaskName) : null;
    }

    public double getAspectRatio() {
      return myScreenSize.width / (double) myScreenSize.height;
    }
  }
}
