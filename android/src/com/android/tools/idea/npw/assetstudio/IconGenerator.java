/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.npw.assetstudio;

import com.android.SdkConstants;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.npw.assetstudio.icon.CategoryIconMap;
import com.android.tools.idea.npw.assetstudio.icon.IconGeneratorResult;
import com.android.tools.idea.observable.core.OptionalProperty;
import com.android.tools.idea.observable.core.OptionalValueProperty;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.android.tools.idea.projectsystem.AndroidModuleTemplate;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

/**
 * Common base class for icon generators.
 */
public abstract class IconGenerator implements Disposable {
  private static final Map<Density, Pattern> DENSITY_PATTERNS;

  static {
    // Create regex patterns that search an icon path and find a valid density
    // Paths look like: /mipmap-hdpi/, /drawable-xxdpi/, /drawable-xxxdpi-v9/
    // Therefore, we search for the density value surrounded by symbols (especially to distinguish
    // xdpi, xxdpi, and xxxdpi).
    ImmutableMap.Builder<Density, Pattern> builder = ImmutableMap.builder();
    for (Density density : Density.values()) {
      builder.put(density, Pattern.compile(String.format(".*[^a-z]%s[^a-z].*", density.getResourceValue()), Pattern.CASE_INSENSITIVE));
    }
    DENSITY_PATTERNS = builder.build();
  }

  protected static final BufferedImage PLACEHOLDER_IMAGE = AssetStudioUtils.createDummyImage();

  private final OptionalProperty<BaseAsset> mySourceAsset = new OptionalValueProperty<>();
  private final StringProperty myOutputName = new StringValueProperty();

  protected final int myMinSdkVersion;

  @NotNull private final GraphicGeneratorContext myContext;

  /**
   * Initializes the icon generator. Every icon generator has to be disposed by calling {@link #dispose()}.
   *
   * @param minSdkVersion the minimal supported Android SDK version
   */
  public IconGenerator(int minSdkVersion) {
    this(minSdkVersion, new GraphicGeneratorContext(40));
  }

  /**
   * Initializes the icon generator. Every icon generator has to be disposed by calling {@link #dispose()}.
   *
   * @param minSdkVersion the minimal supported Android SDK version
   * @param context the graphic generator context
   */
  public IconGenerator(int minSdkVersion, @NotNull GraphicGeneratorContext context) {
    myMinSdkVersion = minSdkVersion;
    myContext = context;
    Disposer.register(this, context);
  }

  @Override
  public void dispose() {
  }

  @NotNull
  public GraphicGeneratorContext getGraphicGeneratorContext() {
    return myContext;
  }

  @NotNull
  public final OptionalProperty<BaseAsset> sourceAsset() {
    return mySourceAsset;
  }

  @NotNull
  public final StringProperty outputName() {
    return myOutputName;
  }

  @NotNull
  public IconGeneratorResult generateIcons(Options options) {
    return new IconGeneratorResult(generateIcons(myContext, options, myOutputName.get()), options);
  }

  /**
   * Generates icons into a map in memory. This is useful for generating previews.
   *
   * {@link #sourceAsset()} must both be set prior to calling this method or an exception will be
   * thrown.
   */
  @NotNull
  private CategoryIconMap generateIntoMemory() {
    if (!mySourceAsset.get().isPresent()) {
      throw new IllegalStateException("Can't generate icons without a source asset set first");
    }

    Options options = createOptions(false);
    return generateIntoMemory(options);
  }

  @NotNull
  private CategoryIconMap generateIntoMemory(Options options) {
    Map<String, Map<String, BufferedImage>> categoryMap = new HashMap<>();
    generate(null, categoryMap, myContext, options, myOutputName.get());
    return new CategoryIconMap(categoryMap);
  }

  /**
   * Like {@link #generateIntoMemory()} but returned in a format where it's easy to see which files
   * will be created / overwritten if {@link #generateImageIconsIntoPath(AndroidModuleTemplate)} is called.
   *
   * {@link #sourceAsset()} and {@link #outputName()} must both be set prior to calling this method or
   * an exception will be thrown.
   */
  @NotNull
  public final Map<File, BufferedImage> generateIntoFileMap(@NotNull AndroidModuleTemplate paths) {
    if (myOutputName.get().isEmpty()) {
      throw new IllegalStateException("Can't save icons to disk if a filename isn't set first");
    }

    File resDirectory = paths.getResDirectory();
    if (resDirectory == null || resDirectory.getParentFile() == null) {
      throw new IllegalArgumentException("Invalid paths used when trying to generate an icon");
    }

    CategoryIconMap categoryIconMap = generateIntoMemory();
    return categoryIconMap.toFileMap(resDirectory.getParentFile());
  }

  /**
   * Like {@link #generateIntoMemory()} but returned in a format where it's easy to see which files
   * will be created / overwritten if {@link #generateImageIconsIntoPath(AndroidModuleTemplate)} is called.
   *
   * {@link #sourceAsset()} and {@link #outputName()} must both be set prior to calling this method or
   * an exception will be thrown.
   */
  @NotNull
  public Map<File, GeneratedIcon> generateIntoIconMap(@NotNull AndroidModuleTemplate paths) {
    Options options = createOptions(false);
    return generateIntoIconMap(paths, options);
  }

  /**
   * Similar to {@link ##generateIntoIconMap(AndroidModuleTemplate)} but instead of generating real icons
   * uses placeholders that are much faster to produce.
   *
   * {@link #sourceAsset()} and {@link #outputName()} must both be set prior to calling this method or
   * an exception will be thrown.
   */
  @NotNull
  public final Map<File, GeneratedIcon> generateIconPlaceholders(@NotNull AndroidModuleTemplate paths) {
    Options options = createOptions(false);
    options.usePlaceholders = true;
    return generateIntoIconMap(paths, options);
  }

  /**
   * Like {@link #generateIntoMemory()} but returned in a format where it's easy to see which files
   * will be created / overwritten if {@link #generateImageIconsIntoPath(AndroidModuleTemplate)} is called.
   *
   * {@link #sourceAsset()} and {@link #outputName()} must both be set prior to calling this method or
   * an exception will be thrown.
   */
  @NotNull
  private Map<File, GeneratedIcon> generateIntoIconMap(@NotNull AndroidModuleTemplate paths, Options options) {
    if (myOutputName.get().isEmpty()) {
      throw new IllegalStateException("Can't save icons to disk if a filename isn't set first");
    }

    File resDirectory = paths.getResDirectory();
    if (resDirectory == null || resDirectory.getParentFile() == null) {
      throw new IllegalArgumentException("Invalid paths used when trying to generate an icon");
    }

    IconGeneratorResult icons = generateIcons(options);
    Map<File, GeneratedIcon> outputMap = new HashMap<>();
    icons.getIcons().forEach(icon -> {
      if (icon.getOutputPath() != null && icon.getCategory() != IconCategory.PREVIEW) {
        File path = new File(resDirectory.getParentFile(), icon.getOutputPath().toString());
        outputMap.put(path, icon);
      }
    });
    return outputMap;
  }

  /**
   * Generates png icons into the target path.
   *
   * {@link #sourceAsset()} and {@link #outputName()} must both be set prior to calling this method or
   * an exception will be thrown.
   *
   * This method must be called from within a WriteAction.
   */
  public void generateImageIconsIntoPath(@NotNull AndroidModuleTemplate paths) {
    Map<File, GeneratedIcon> pathIconMap = generateIntoIconMap(paths);

    ApplicationManager.getApplication().runWriteAction(() -> {
      for (Map.Entry<File, GeneratedIcon> fileImageEntry : pathIconMap.entrySet()) {
        File file = fileImageEntry.getKey();
        GeneratedIcon icon = fileImageEntry.getValue();

        if (icon instanceof GeneratedImageIcon) {
          if (FileUtilRt.extensionEquals(file.getName(), "png")) {
            writePngToDisk(file, ((GeneratedImageIcon)icon).getImage());
          }
          else {
            getLog().error("Please report this error. Unable to create icon for invalid file: " + file.getAbsolutePath());
          }
        }
        else if (icon instanceof GeneratedXmlResource) {
          if (FileUtilRt.extensionEquals(file.getName(), "xml")) {
            writeTextToDisk(file, ((GeneratedXmlResource)icon).getXmlText());
          }
          else {
            getLog().error("Please report this error. Unable to create icon for invalid file: " + file.getAbsolutePath());
          }
        }
        else {
          getLog().error("Please report this error. Unable to create icon for invalid file: " + file.getAbsolutePath());
        }
      }
    });
  }

  private void writePngToDisk(@NotNull File file, @NotNull BufferedImage image) {
    try {
      VirtualFile directory = VfsUtil.createDirectories(file.getParentFile().getAbsolutePath());
      VirtualFile imageFile = directory.findChild(file.getName());
      if (imageFile == null || !imageFile.exists()) {
        imageFile = directory.createChildData(this, file.getName());
      }
      try (OutputStream outputStream = imageFile.getOutputStream(this)) {
        ImageIO.write(image, "PNG", outputStream);
      }
    }
    catch (IOException e) {
      getLog().error(e);
    }
  }

  private void writeTextToDisk(@NotNull File file, @NotNull String text) {
    try {
      VirtualFile directory = VfsUtil.createDirectories(file.getParentFile().getAbsolutePath());
      VirtualFile imageFile = directory.findChild(file.getName());
      if (imageFile == null || !imageFile.exists()) {
        imageFile = directory.createChildData(this, file.getName());
      }
      try (OutputStream outputStream = imageFile.getOutputStream(this)) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        outputStream.write(bytes);
      }
    }
    catch (IOException e) {
      getLog().error(e);
    }
  }

  /**
   * Generates a single icon using the given options.
   *
   * @param context render context to use for looking up resources etc
   * @param options options controlling the appearance of the icon
   * @return a {@link BufferedImage} with the generated icon
   */
  @Nullable
  public GeneratedIcon generateIcon(@NotNull GraphicGeneratorContext context, @NotNull Options options, @NotNull String name,
                                    @NotNull IconCategory category) {
    BufferedImage image = generate(context, options);
    return new GeneratedImageIcon(getIconFileName(options, name), Paths.get(getIconPath(options, name)), category, options.density, image);
  }

  @NotNull
  public Collection<GeneratedIcon> generateIcons(@NotNull GraphicGeneratorContext context, @NotNull Options options, @NotNull String name) {
    Map<String, Map<String, BufferedImage>> categoryMap = new HashMap<>();
    generate(null, categoryMap, context, options, name);

    // Category map is a map from category name to a map from relative path to image.
    List<GeneratedIcon> icons = new ArrayList<>();
    categoryMap.forEach(
        (category, images) ->
            images.forEach(
                (path, image) -> {
                  Density density = pathToDensity(path);
                  // Could be a "Web" image
                  if (density == null) {
                    density = Density.NODPI;
                  }
                  GeneratedImageIcon icon = new GeneratedImageIcon(path, Paths.get(path), IconCategory.fromName(category), density, image);
                  icons.add(icon);
                }));
    return icons;
  }

  /**
   * Generates a single icon using the given options.
   *
   * @param context render context to use for looking up resources etc
   * @param options options controlling the appearance of the icon
   * @return a {@link BufferedImage} with the generated icon
   */
  @NotNull
  public abstract BufferedImage generate(@NotNull GraphicGeneratorContext context, @NotNull Options options);

  @NotNull
  public abstract Options createOptions(boolean forPreview);

  /**
   * Computes the target filename (relative to the Android project folder) where an icon rendered
   * with the given options should be stored. This is also used as the map keys in the result map
   * used by {@link #generate(String, Map, GraphicGeneratorContext, Options, String)}.
   *
   * @param options the options object used by the generator for the current image
   * @param iconName the base name to use when creating the path
   * @return a platform-independent path relative to the project folder where the image should be stored
   */
  @NotNull
  protected String getIconPath(@NotNull Options options, @NotNull String iconName) {
    return getIconFolder(options) + '/' + getIconFileName(options, iconName);
  }

  /**
   * Returns the name of an icon file.
   */
  @NotNull
  private static String getIconFileName(@NotNull Options options, @NotNull String iconName) {
    if (options.density == Density.ANYDPI) {
      return iconName + SdkConstants.DOT_XML;
    }
    return iconName + SdkConstants.DOT_PNG;
  }

  /**
   * Returns the name of the folder to contain the resource. It usually includes the density, but is also
   * sometimes modified by options. For example, in some notification icons we add in -v9 or -v11.
   */
  @NotNull
  protected String getIconFolder(@NotNull Options options) {
    switch (options.iconFolderKind) {
      case DRAWABLE:
        return getIconFolder(ResourceFolderType.DRAWABLE, options.density, options.apiVersion);
      case MIPMAP:
        return getIconFolder(ResourceFolderType.MIPMAP, options.density, options.apiVersion);
      case DRAWABLE_NO_DPI:
        return getIconFolder(ResourceFolderType.DRAWABLE, Density.NODPI, options.apiVersion);
      case VALUES:
        return getIconFolder(ResourceFolderType.VALUES, Density.NODPI, options.apiVersion);
      default:
        throw new IllegalArgumentException("Unexpected folder kind: " + options.iconFolderKind);
    }
  }

  @NotNull
  private static String getIconFolder(@NotNull ResourceFolderType folderType, @NotNull Density density, int apiVersion) {
    StringBuilder buf = new StringBuilder(50);
    buf.append(SdkConstants.FD_RES);
    buf.append('/');
    buf.append(folderType.getName());
    if (density != Density.NODPI) {
      buf.append('-');
      buf.append(density.getResourceValue());
    }
    if (apiVersion > 1) {
      buf.append("-v");
      buf.append(apiVersion);
    }
    return buf.toString();
  }

  /**
   * Generates a full set of icons into the given map. The values in the map will be the generated
   * images, and each value is keyed by the corresponding relative path of the image, which is
   * determined by the {@link #getIconPath(Options, String)} method.
   *
   * @param category the current category to place images into (if null the density name will be used)
   * @param categoryMap the map to put images into, should not be null. The map is a map from a category name,
   *                   to a map from file path to image.
   * @param context a generator context which for example can load resources
   * @param options options to apply to this generator
   * @param name the base name of the icons to generate
   */
  public void generate(@Nullable String category, @NotNull Map<String, Map<String, BufferedImage>> categoryMap,
                       @NotNull GraphicGeneratorContext context, @NotNull Options options, @NotNull String name) {
    // Vector image only need to generate one preview image, so we by pass all the other image densities.
    if (options.density == Density.ANYDPI) {
      generateImageAndUpdateMap(category, categoryMap, context, options, name);
      return;
    }
    Density[] densityValues = Density.values();
    // Sort density values into ascending order.
    Arrays.sort(densityValues, Comparator.comparingInt(Density::getDpiValue));
    for (Density density : densityValues) {
      if (!density.isValidValueForDevice()) {
        continue;
      }
      if (!includeDensity(density)) {
        // Not yet supported -- missing stencil image.
        // TODO don't manually check and instead gracefully handle missing stencils.
        continue;
      }
      options.density = density;
      generateImageAndUpdateMap(category, categoryMap, context, options, name);
    }
  }

  private void generateImageAndUpdateMap(@Nullable String category, @NotNull Map<String, Map<String, BufferedImage>> categoryMap,
                                         @NotNull GraphicGeneratorContext context, @NotNull Options options, @NotNull String name) {
    BufferedImage image = generate(context, options);
    // The category key is either the "category" parameter or the density if not present.
    String mapCategory = category;
    if (mapCategory == null) {
      mapCategory = options.density.getResourceValue();
    }
    Map<String, BufferedImage> imageMap = categoryMap.computeIfAbsent(mapCategory, k -> new LinkedHashMap<>());

    // Store image in map, where the key is the relative path to the image.
    imageMap.put(getIconPath(options, name), image);
  }

  protected boolean includeDensity(@NotNull Density density) {
    return density.isRecommended() && density != Density.LOW && density != Density.XXXHIGH;
  }

  /**
   * Returns the scale factor to apply for a given MDPI density to compute the absolute pixel count
   * to use to draw an icon of the given target density.
   *
   * @param density the density
   * @return a factor to multiple mdpi distances with to compute the target density
   */
  public static float getMdpiScaleFactor(@NotNull Density density) {
    if (density == Density.ANYDPI) {
      density = Density.XXXHIGH;
    }
    if (density == Density.NODPI) {
      density = Density.MEDIUM;
    }
    return density.getDpiValue() / (float)Density.MEDIUM.getDpiValue();
  }

  /**
   * Converts the path to a density, if possible. Output paths don't always map cleanly to density
   * values, such as the path for the "web" icon, so in those cases, {@code null} is returned.
   */
  @Nullable
  public static Density pathToDensity(@NotNull String iconPath) {
    iconPath = FileUtils.toSystemIndependentPath(iconPath);
    // Strip off the filename, in case the user names their icon "xxxhdpi" etc.
    // but leave the trailing slash, as it's used in the regex pattern.
    iconPath = iconPath.substring(0, iconPath.lastIndexOf('/') + 1);

    for (Density density : Density.values()) {
      if (DENSITY_PATTERNS.get(density).matcher(iconPath).matches()) {
        return density;
      }
    }

    return null;
  }

  @Nullable
  public static BufferedImage getTrimmedAndPaddedImage(@NotNull Options options) {
    if (options.sourceImageFuture == null) {
      return null;
    }
    try {
      BufferedImage image = options.sourceImageFuture.get();
      if (image != null) {
        if (options.isTrimmed) {
          image = AssetStudioUtils.trim(image);
        }
        if (options.paddingPercent != 0) {
          image = AssetStudioUtils.pad(image, options.paddingPercent);
        }
      }
      return image;
    }
    catch (InterruptedException | ExecutionException e) {
      return null;
    }
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(IconGenerator.class);
  }

  /**
   * Options used for all generators.
   */
  public static class Options {
    /** Indicates that the graphic generator may use placeholders instead of real images. */
    public boolean usePlaceholders;

    /** Source image to use as a basis for the icon. */
    @Nullable public ListenableFuture<BufferedImage> sourceImageFuture;

    /** Indicated whether the source image should be trimmed or not. */
    public boolean isTrimmed;

    /** Percent of padding for the source image. */
    public int paddingPercent;

    /** Controls the directory where to store the icon/resource. */
    @NotNull public IconFolderKind iconFolderKind = IconFolderKind.DRAWABLE;

    /** The density to generate the icon with. Web icons use {@link Density#NODPI}. */
    @NotNull public Density density = Density.XHIGH;

    /**
     * Controls the API version suffix, e.g. "-v23", of the directory where to store the icon/resource.
     * A value less than 2 means no suffix.
     */
    public int apiVersion;
  }

  public enum IconFolderKind {
    DRAWABLE,
    DRAWABLE_NO_DPI,
    MIPMAP,
    VALUES,
  }

  /** Shapes that can be used for icon backgrounds. */
  public enum Shape {
    /** No background */
    NONE("none"),
    /** Circular background */
    CIRCLE("circle"),
    /** Square background */
    SQUARE("square"),
    /** Vertical rectangular background */
    VRECT("vrect"),
    /** Horizontal rectangular background */
    HRECT("hrect"),
    /** Square background with Dog-ear effect */
    SQUARE_DOG("square_dogear"),
    /** Vertical rectangular background with Dog-ear effect */
    VRECT_DOG("vrect_dogear"),
    /** Horizontal rectangular background with Dog-ear effect */
    HRECT_DOG("hrect_dogear");

    /** Id, used in filenames to identify associated stencils */
    public final String id;

    Shape(String id) {
      this.id = id;
    }
  }

  /** Foreground effects styles */
  public enum Style {
    /** No effects */
    SIMPLE("fore1");

    /** Id, used in filenames to identify associated stencils */
    public final String id;

    Style(String id) {
      this.id = id;
    }
  }
}
