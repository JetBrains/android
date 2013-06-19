/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.wizard;

import com.android.assetstudiolib.*;
import com.android.tools.idea.rendering.ImageUtils;
import com.android.tools.idea.templates.TemplateManager;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import static java.awt.image.BufferedImage.TYPE_INT_ARGB;

public class LauncherIconWizardState extends TemplateWizardState implements GraphicGeneratorContext {
  public static final String ATTR_TEXT = "text";
  public static final String ATTR_SCALING = "scaling";
  public static final String ATTR_SHAPE = "shape";
  public static final String ATTR_PADDING = "padding";
  public static final String ATTR_TRIM = "trim";
  public static final String ATTR_FONT = "font";
  public static final String ATTR_FONT_SIZE = "fontSize";
  public static final String ATTR_SOURCE_TYPE = "sourceType";
  public static final String ATTR_IMAGE_PATH = "imagePath";
  public static final String ATTR_CLIPART_NAME = "clipartPath";
  public static final String ATTR_FOREGROUND_COLOR = "foregroundColor";
  public static final String ATTR_BACKGROUND_COLOR = "backgroundColor";
  public static final String LAUNCHER_ICON_BASE_NAME = "ic_launcher";

  private static final Logger LOG = Logger.getInstance("#" + LauncherIconWizardState.class.getName());
  private static final String OUTPUT_DIRECTORY = "src/main/";
  private static Cache<String, BufferedImage> ourImageCache = CacheBuilder.newBuilder().build();

  public static class ImageGeneratorException extends Exception {
    public ImageGeneratorException(String message) {
      super(message);
    }
  }

  /**
   * Types of sources that the asset studio can use to generate icons from
   */
  public enum SourceType {
    IMAGE, CLIPART, TEXT
  }


  public static enum Scaling {
    CENTER, CROP
  }

  /**
   * The type of asset to create: launcher icon, menu icon, etc.
   */
  public enum AssetType {
    /**
     * Launcher icon to be shown in the application list
     */
    LAUNCHER("Launcher Icons", "ic_launcher"),

    /**
     * Icons shown in the action bar
     */
    ACTIONBAR("Action Bar and Tab Icons (Android 3.0+)", "ic_action_%s"),

    /**
     * Icons shown in a notification message
     */
    NOTIFICATION("Notification Icons", "ic_stat_%s"),

    /**
     * Icons shown as part of tabs
     */
    TAB("Pre-Android 3.0 Tab Icons", "ic_tab_%s"),

    /**
     * Icons shown in menus
     */
    MENU("Pre-Android 3.0 Menu Icons", "ic_menu_%s");
    /**
     * Display name to show to the user in the asset type selection list
     */
    private final String myDisplayName;
    /**
     * Default asset name format
     */
    private String myDefaultNameFormat;

    AssetType(String displayName, String defaultNameFormat) {
      myDisplayName = displayName;
      myDefaultNameFormat = defaultNameFormat;
    }

    /**
     * Returns the display name of this asset type to show to the user in the
     * asset wizard selection page etc
     */
    public String getDisplayName() {
      return myDisplayName;
    }

    /**
     * Returns the default format to use to suggest a name for the asset
     */
    public String getDefaultNameFormat() {
      return myDefaultNameFormat;
    }

    /**
     * Whether this asset type configures foreground scaling
     */
    public boolean needsForegroundScaling() {
      return this == LAUNCHER;
    }

    /**
     * Whether this asset type needs a shape parameter
     */
    public boolean needsShape() {
      return this == LAUNCHER;
    }

    /**
     * Whether this asset type needs foreground and background color parameters
     */
    public boolean needsColors() {
      return this == LAUNCHER;
    }

    /**
     * Whether this asset type needs an effects parameter
     */
    public boolean needsEffects() {
      return this == LAUNCHER;
    }

    /**
     * Whether this asset type needs a theme parameter
     */
    public boolean needsTheme() {
      return this == ACTIONBAR;
    }
  }

  public LauncherIconWizardState() {
    put(ATTR_TEXT, "Aa");
    put(ATTR_FONT, "Arial Black");
    put(ATTR_SCALING, Scaling.CROP);
    put(ATTR_SHAPE, GraphicGenerator.Shape.NONE);
    put(ATTR_FONT_SIZE, 144);
    put(ATTR_SOURCE_TYPE, LauncherIconWizardState.SourceType.IMAGE);
    put(ATTR_IMAGE_PATH,
        new File(TemplateManager.getTemplateRootFolder(), "projects/NewAndroidApplication/root/res/drawable-xhdpi/ic_launcher.png")
          .getAbsolutePath());
    put(ATTR_CLIPART_NAME, "android.png");
    put(ATTR_FOREGROUND_COLOR, Color.BLUE);
    put(ATTR_BACKGROUND_COLOR, Color.WHITE);
  }

  @Override
  @Nullable
  public BufferedImage loadImageResource(@NotNull final String path) {
    try {
      return ourImageCache.get(path, new Callable<BufferedImage>() {
        @Override
        public BufferedImage call() throws Exception {
          return getImage(path, true);
        }
      });
    }
    catch (ExecutionException e) {
      LOG.error(e);
      return null;
    }
  }

  /**
   * Generate images using the given wizard state
   *
   * @param previewOnly whether we are only generating previews
   * @return a map of image objects
   */
  @NotNull
  public Map<String, Map<String, BufferedImage>> generateImages(boolean previewOnly) throws ImageGeneratorException {
    Map<String, Map<String, BufferedImage>> categoryMap = new LinkedHashMap<String, Map<String, BufferedImage>>();

    AssetType type = AssetType.LAUNCHER;
    boolean trim = (Boolean)get(ATTR_TRIM);
    int padding = (Integer)get(ATTR_PADDING);

    BufferedImage sourceImage = null;
    switch ((LauncherIconWizardState.SourceType)get(ATTR_SOURCE_TYPE)) {
      case IMAGE: {
        String path = (String)get(ATTR_IMAGE_PATH);
        if (path == null || path.isEmpty()) {
          throw new ImageGeneratorException("Path to image is empty.");
        }

        try {
          sourceImage = getImage(path, false);
        }
        catch (FileNotFoundException e) {
          throw new ImageGeneratorException("Image file not found: " + path);
        }
        catch (IOException e) {
          throw new ImageGeneratorException("Unable to load image file: " + path);
        }
        break;
      }

      case CLIPART: {
        String clipartName = (String)get(ATTR_CLIPART_NAME);
        try {
          sourceImage = GraphicGenerator.getClipartImage(clipartName);
        }
        catch (IOException e) {
          throw new ImageGeneratorException("Unable to load clip art image: " + clipartName);
        }

        if (type.needsColors()) {
          Paint paint = (Color)get(ATTR_FOREGROUND_COLOR);
          sourceImage = Util.filledImage(sourceImage, paint);
        }
        break;
      }

      case TEXT: {
        TextRenderUtil.Options options = new TextRenderUtil.Options();
        options.font = Font.decode((String)get(ATTR_FONT) + " " + (Integer)get(ATTR_FONT_SIZE));
        options.foregroundColor = type.needsColors() ? ((Color)get(ATTR_FOREGROUND_COLOR)).getRGB() : 0xFFFFFFFF;
        sourceImage = TextRenderUtil.renderTextImage((String)get(ATTR_TEXT), 1, options);

        break;
      }
    }

    if (trim) {
      sourceImage = crop(sourceImage);
    }

    if (padding != 0) {
      sourceImage = Util.paddedImage(sourceImage, padding);
    }

    if (type != AssetType.LAUNCHER) {
      // TODO: Refactor this code and handle other types: MENU, ACTIONBAR, NOTIFICATION, TAB
      throw new ImageGeneratorException("Only launcher icons can be customized");
    }

    GraphicGenerator generator = new LauncherIconGenerator();
    LauncherIconGenerator.LauncherOptions launcherOptions = new LauncherIconGenerator.LauncherOptions();
    launcherOptions.shape = (GraphicGenerator.Shape)get(ATTR_SHAPE);
    launcherOptions.crop = Scaling.CROP.equals(get(ATTR_SCALING));
    launcherOptions.style = GraphicGenerator.Style.SIMPLE;
    launcherOptions.backgroundColor = ((Color)get(ATTR_BACKGROUND_COLOR)).getRGB();
    launcherOptions.isWebGraphic = !previewOnly;
    GraphicGenerator.Options options = launcherOptions;
    options.sourceImage = sourceImage;

    generator.generate(null, categoryMap, this, options, LAUNCHER_ICON_BASE_NAME);

    return categoryMap;
  }

  /**
   * Outputs final-rendered images to disk, rooted at the given directory.
   */
  public void outputImages(@NotNull File contentRoot) {
    try {
      Map<String, Map<String, BufferedImage>> images = generateImages(false);
      for (String density : images.keySet()) {
        // TODO: The output directory needs to take flavor and build type into account, which will need to be configurable by the user
        File directory = new File(contentRoot, OUTPUT_DIRECTORY);
        Map<String, BufferedImage> filenameMap = images.get(density);
        for (String filename : filenameMap.keySet()) {
          File outFile = new File(directory, filename);
          try {
            outFile.getParentFile().mkdirs();
            BufferedImage image = filenameMap.get(filename);
            ImageIO.write(image, "PNG", new FileOutputStream(outFile));
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
      }
    } catch (Exception e) {
      LOG.error(e);
    }
  }

  @NotNull
  private static BufferedImage getImage(@NotNull String path, boolean isPluginRelative) throws IOException {
    BufferedImage image = null;
    if (isPluginRelative) {
      image = GraphicGenerator.getStencilImage(path);
    }
    else {
      image = ImageIO.read(new File(path));
    }

    if (image == null) {
      image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    }

    return image;
  }

  @NotNull
  private static BufferedImage crop(@NotNull BufferedImage sourceImage) {
    BufferedImage cropped = ImageUtils.cropBlank(sourceImage, null, TYPE_INT_ARGB);
    return cropped != null ? cropped : sourceImage;
  }
}
