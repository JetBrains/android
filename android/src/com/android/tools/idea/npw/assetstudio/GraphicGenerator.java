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
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.utils.FileUtils;
import com.android.utils.SdkUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.EmptyIterator;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The base Generator class.
 */
public abstract class GraphicGenerator {
    public static final BufferedImage PLACEHOLDER_IMAGE = AssetStudioUtils.createDummyImage();

    private static final Map<Density, Pattern> DENSITY_PATTERNS;

    static {
        // Create regex patterns that search an icon path and find a valid density
        // Paths look like: /mipmap-hdpi/, /drawable-xxdpi/, /drawable-xxxdpi-v9/
        // Therefore, we search for the density value surrounded by symbols (especially to distinguish
        // xdpi, xxdpi, and xxxdpi)
        ImmutableMap.Builder<Density, Pattern> builder = ImmutableMap.builder();
        for (Density density : Density.values()) {
            builder.put(
                    density,
                    Pattern.compile(
                            String.format(".*[^a-z]%s[^a-z].*", density.getResourceValue()),
                            Pattern.CASE_INSENSITIVE));
        }
        DENSITY_PATTERNS = builder.build();
    }

    /**
     * Options used for all generators.
     */
    public static class Options {
        /** Indicates that the graphic generator may use placeholders instead of real images. */
        public boolean usePlaceholders;

        /** Minimum version (API level) of the SDK to generate icons for. */
        public int minSdk = 1;

        /** Source image to use as a basis for the icon. */
        @Nullable public ListenableFuture<BufferedImage> sourceImageFuture;

        /** Indicated whether the source image should be trimmed or not. */
        public boolean isTrimmed;

        /** Percent of padding for the source image. */
        public int paddingPercent;

        /** The density to generate the icon with. */
        @NotNull public Density density = Density.XHIGH;

        /** Controls the directory where to store the icon/resource. */
        @NotNull public IconFolderKind iconFolderKind = IconFolderKind.DRAWABLE;
    }

    public enum IconFolderKind {
        DRAWABLE,
        MIPMAP,
        // Note: Temporary, see b/62316340
        MIPMAP_V26,
        DRAWABLE_NO_DPI,
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

    /**
     * Generate a single icon using the given options
     *
     * @param context render context to use for looking up resources etc
     * @param options options controlling the appearance of the icon
     * @return a {@link BufferedImage} with the generated icon
     */
    @Nullable
    public GeneratedIcon generateIcon(
            @NonNull GraphicGeneratorContext context,
            @NonNull Options options,
            @NonNull String name,
            @NonNull IconCategory category) {
        BufferedImage image = generate(context, options);
        return new GeneratedImageIcon(
                getIconName(options, name),
                Paths.get(getIconPath(options, name)),
                category,
                options.density,
                image);
    }

    @NonNull
    public GeneratedIcons generateIcons(
            @NonNull GraphicGeneratorContext context,
            @NonNull Options options,
            @NonNull String name) {
        Map<String, Map<String, BufferedImage>> categoryMap = new HashMap<>();
        generate(null, categoryMap, context, options, name);

        // Category map is a map from category name to a map from relative path to image.
        GeneratedIcons icons = new GeneratedIcons();
        categoryMap.forEach(
                (category, images) ->
                        images.forEach(
                                (path, image) -> {
                                    Density density = pathToDensity(path);
                                    // Could be a "Web" image
                                    if (density == null) {
                                        density = Density.NODPI;
                                    }
                                    GeneratedImageIcon icon =
                                            new GeneratedImageIcon(
                                                    path,
                                                    Paths.get(path),
                                                    IconCategory.fromName(category),
                                                    density,
                                                    image);
                                    icons.add(icon);
                                }));
        return icons;
    }

    /**
     * Generate a single icon using the given options
     *
     * @param context render context to use for looking up resources etc
     * @param options options controlling the appearance of the icon
     * @return a {@link BufferedImage} with the generated icon
     */
    @NonNull
    public abstract BufferedImage generate(@NonNull GraphicGeneratorContext context, @NonNull Options options);

    /**
     * Computes the target filename (relative to the Android project folder) where an icon rendered
     * with the given options should be stored. This is also used as the map keys in the result map
     * used by {@link #generate(String, Map, GraphicGeneratorContext, Options, String)}.
     *
     * @param options the options object used by the generator for the current image
     * @param name the base name to use when creating the path
     * @return a path relative to the res/ folder where the image should be stored (will always use
     *     / as a path separator, not \ on Windows)
     */
    @NonNull
    protected String getIconPath(@NonNull Options options, @NonNull String name) {
        return getIconFolder(options) + '/' + getIconName(options, name);
    }

    /**
     * Gets name of the file itself. It is sometimes modified by options, for example in unselected
     * tabs we change foo.png to foo-unselected.png
     */
    @NonNull
    protected String getIconName(@NonNull Options options, @NonNull String name) {
        if (options.density == Density.ANYDPI) {
            return name + SdkConstants.DOT_XML;
        }
        return name + SdkConstants.DOT_PNG; //$NON-NLS-1$
    }

    /**
     * Gets name of the folder to contain the resource. It usually includes the density, but is also
     * sometimes modified by options. For example, in some notification icons we add in -v9 or -v11.
     */
    @NonNull
    protected String getIconFolder(@NonNull Options options) {
        switch (options.iconFolderKind) {
            case DRAWABLE:
                return getIconFolder(ResourceFolderType.DRAWABLE, options.density);
            case MIPMAP:
                return getIconFolder(ResourceFolderType.MIPMAP, options.density);
            case MIPMAP_V26:
                return getIconFolder(ResourceFolderType.MIPMAP, options.density) + "-v26";
            case DRAWABLE_NO_DPI:
                return getIconFolder(ResourceFolderType.DRAWABLE, Density.NODPI);
            case VALUES:
                return getIconFolder(ResourceFolderType.VALUES, Density.NODPI);
            default:
                throw new IllegalArgumentException();
        }
    }

    @NonNull
    private static String getIconFolder(
            @NonNull ResourceFolderType folderType, @NonNull Density density) {
        StringBuilder sb = new StringBuilder(50);
        sb.append(SdkConstants.FD_RES);
        sb.append('/');
        sb.append(folderType.getName());
        if (density != Density.NODPI) {
            sb.append('-');
            sb.append(density.getResourceValue());
        }
        return sb.toString();
    }

    /**
     * Generates a full set of icons into the given map. The values in the map will be the generated
     * images, and each value is keyed by the corresponding relative path of the image, which is
     * determined by the {@link #getIconPath(Options, String)} method.
     *
     * @param category the current category to place images into (if null the density name will be
     *     used)
     * @param categoryMap the map to put images into, should not be null. The map is a map from a
     *     category name, to a map from file path to image.
     * @param context a generator context which for example can load resources
     * @param options options to apply to this generator
     * @param name the base name of the icons to generate
     */
    public void generate(
            String category,
            Map<String, Map<String, BufferedImage>> categoryMap,
            GraphicGeneratorContext context,
            Options options,
            String name) {
        generateAllDensities(category, categoryMap, context, options, name);
    }

    private void generateAllDensities(
            String category,
            Map<String, Map<String, BufferedImage>> categoryMap,
            GraphicGeneratorContext context,
            Options options,
            String name) {
        // Vector image only need to generate one preview image, so we by pass all the
        // other image densities.
        if (options.density == Density.ANYDPI) {
            generateImageAndUpdateMap(category, categoryMap, context, options, name);
            return;
        }
        Density[] densityValues = Density.values();
        // Sort density values into ascending order
        Arrays.sort(densityValues, Comparator.comparingInt(Density::getDpiValue));
        for (Density density : densityValues) {
            if (!density.isValidValueForDevice()) {
                continue;
            }
            if (!includeDensity(density)) {
                // Not yet supported -- missing stencil image
                // TODO don't manually check and instead gracefully handle missing stencils.
                continue;
            }
            options.density = density;
            generateImageAndUpdateMap(category, categoryMap, context, options, name);
        }
    }

    private void generateImageAndUpdateMap(
            String category,
            Map<String, Map<String, BufferedImage>> categoryMap,
            GraphicGeneratorContext context,
            Options options,
            String name) {
        BufferedImage image = generate(context, options);
        // The category key is either the "category" parameter or the density if not present
        String mapCategory = category;
        if (mapCategory == null) {
            mapCategory = options.density.getResourceValue();
        }
        Map<String, BufferedImage> imageMap =
                categoryMap.computeIfAbsent(mapCategory, k -> new LinkedHashMap<>());

        // Store image in map, where the key is the relative path to the image
        imageMap.put(getIconPath(options, name), image);
    }

    protected boolean includeDensity(@NonNull Density density) {
        return density.isRecommended() && density != Density.LOW && density != Density.XXXHIGH;
    }

    /**
     * Returns the scale factor to apply for a given MDPI density to compute the
     * absolute pixel count to use to draw an icon of the given target density
     *
     * @param density the density
     * @return a factor to multiple mdpi distances with to compute the target density
     */
    public static float getMdpiScaleFactor(Density density) {
        if (density == Density.ANYDPI) {
            density = Density.XXXHIGH;
        }
        if (density == Density.NODPI) {
            density = Density.MEDIUM;
        }
        return density.getDpiValue() / (float) Density.MEDIUM.getDpiValue();
    }

    /**
     * Returns one of the built in stencil images, or null if the image was not found.
     *
     * @param relativePath stencil path such as "launcher-stencil/square/web/back.png"
     * @return the image, or null
     * @throws IOException if an unexpected I/O error occurs
     */
    public static BufferedImage getStencilImage(String relativePath) throws IOException {
        try (InputStream is = GraphicGenerator.class.getResourceAsStream(relativePath)) {
            return is == null ? null : ImageIO.read(is);
        }
    }

    /**
     * Returns the icon (32x32) for a given clip art image.
     *
     * @param name the name of the image to be loaded (which can be looked up via {@link
     *     #getResourcesNames(String, String)} ()})
     * @return the icon image
     * @throws IOException if the image cannot be loaded
     */
    private static BufferedImage getClipartIcon(String name) throws IOException {
        try (InputStream is = GraphicGenerator.class.getResourceAsStream("/images/clipart/small/" + name)) {
            return ImageIO.read(is);
        }
    }

    /**
     * Returns the full size clip art image for a given image name.
     *
     * @param name the name of the image to be loaded (which can be looked up via {@link
     *     #getResourcesNames(String, String)})
     * @return the clip art image
     * @throws IOException if the image cannot be loaded
     */
    public static BufferedImage getClipartImage(String name) throws IOException {
        try (InputStream is = GraphicGenerator.class.getResourceAsStream("/images/clipart/big/" + name)) {
            return ImageIO.read(is);
        }
    }

    /**
     * Returns the names of available clip art images which can be obtained by passing the
     * name to {@link #getClipartIcon(String)} or
     * {@link GraphicGenerator#getClipartImage(String)}
     *
     * @return an iterator for the available image names
     */
    public static Iterator<String> getResourcesNames(String pathPrefix, String filenameExtension) {
        List<String> names = new ArrayList<>(80);
        try {
            ZipFile zipFile = null;
            ProtectionDomain protectionDomain = GraphicGenerator.class.getProtectionDomain();
            URL url = protectionDomain.getCodeSource().getLocation();
            if (url != null && url.getProtocol().equals("jar")) {
                File file = SdkUtils.urlToFile(url);
                zipFile = new JarFile(file);
            } else {
                Enumeration<URL> en = GraphicGenerator.class.getClassLoader().getResources(pathPrefix);
                if (en.hasMoreElements()) {
                    url = en.nextElement();
                    URLConnection urlConnection = url.openConnection();
                    if (urlConnection instanceof JarURLConnection) {
                        JarURLConnection urlConn = (JarURLConnection) (urlConnection);
                        zipFile = urlConn.getJarFile();
                    } else if ("file".equals(url.getProtocol())) { //$NON-NLS-1$
                        File directory = new File(url.getPath());
                        String[] list = directory.list();
                        if (list == null) {
                            return EmptyIterator.getInstance();
                        }
                        return ImmutableList.copyOf(list).iterator();
                    }

                }
            }
            Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry zipEntry = enumeration.nextElement();
                String name = zipEntry.getName();
                if (!name.startsWith(pathPrefix)
                        || !name.endsWith(filenameExtension)) { //$NON-NLS-1$
                    continue;
                }

                int lastSlash = name.lastIndexOf('/');
                if (lastSlash != -1) {
                    name = name.substring(lastSlash + 1);
                }
                names.add(name);
            }
        } catch (Exception e) {
            getLog().error(e);
        }

        return names.iterator();
    }

    /**
     * Convert the path to a density, if possible. Output paths don't always map cleanly to density
     * values, such as the path for the "web" icon, so in those cases, {@code null} is returned.
     */
    @Nullable
    public static Density pathToDensity(@NonNull String iconPath) {
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
    public static BufferedImage getTrimmedAndPaddedImage(Options options) {
        return getTrimmedAndPaddedImage(options.sourceImageFuture, options.isTrimmed, options.paddingPercent);
    }

    @Nullable
    public static BufferedImage getTrimmedAndPaddedImage(@Nullable ListenableFuture<BufferedImage> imageFuture, boolean isTrimmed,
                                                         int paddingPercent) {
        if (imageFuture == null) {
            return null;
        }
        try {
            BufferedImage image = imageFuture.get();
            if (image != null) {
                if (isTrimmed) {
                    image = AssetStudioUtils.trim(image);
                }
                if (paddingPercent != 0) {
                    image = AssetStudioUtils.pad(image, paddingPercent);
                }
            }
            return image;
        }
        catch (InterruptedException | ExecutionException e) {
            return null;
        }
    }

    private static Logger getLog() {
        return Logger.getInstance(GraphicGenerator.class);
    }
}
