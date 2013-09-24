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

package com.android.tools.idea.javadoc;

import com.android.SdkConstants;
import com.android.builder.model.*;
import com.android.ide.common.rendering.api.ArrayResourceValue;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.AbstractResourceRepository;
import com.android.ide.common.res2.ResourceFile;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.FrameworkResources;
import com.android.ide.common.resources.ResourceItemResolver;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.Density;
import com.android.resources.ResourceType;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.rendering.*;
import com.android.utils.HtmlBuilder;
import com.android.utils.SdkUtils;
import com.android.utils.XmlUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColorUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.Locale;

import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.SdkConstants.PREFIX_THEME_REF;
import static org.jetbrains.android.util.AndroidUtils.hasImageExtension;

public class AndroidJavaDocRenderer {
  /** Renders the Javadoc for a resource of given type and name. */
  @Nullable
  public static String render(@NotNull Module module, @NotNull ResourceType type, @NotNull String name, boolean framework) {
    ResourceValueRenderer renderer = ResourceValueRenderer.create(type, module);
    if (renderer == null || framework && renderer.getFrameworkResources() == null || !framework && renderer.getAppResources() == null) {
      return null;
    }

    return renderer.render(type, name, framework);
  }

  /** Combines external javadoc into the documentation rendered by the {@link #render} method */
  @Nullable
  public static String injectExternalDocumentation(@Nullable String rendered, @Nullable String external) {
    if (rendered == null) {
      return external;
    } else if (external == null) {
      return rendered;
    }
    // Strip out HTML tags from the external documentation
    external = external.replace("<HTML>","").replace("</HTML>","");
    // Strip out styles.
    int styleStart = external.indexOf("<style");
    int styleEnd = external.indexOf("</style>");
    if (styleStart != -1 && styleEnd != -1) {
      String style = external.substring(styleStart, styleEnd + "</style>".length());
      external = external.substring(0, styleStart) + external.substring(styleEnd + "</style>".length());
      // Insert into our own head
      int insert = rendered.indexOf("<body>");
      if (insert != -1) {
        int headEnd = rendered.lastIndexOf("</head>", insert);
        if (headEnd != -1) {
          insert = headEnd;
          rendered = rendered.substring(0, insert) + style + rendered.substring(insert);
        } else {
          rendered = rendered.substring(0, insert) + "<head>" + style + "</head>" + rendered.substring(insert);
        }
      }
    }

    int bodyEnd = rendered.indexOf("</body>");
    if (bodyEnd != -1) {
      rendered = rendered.substring(0, bodyEnd) + external + rendered.substring(bodyEnd);
    }

    return rendered;
  }

  private static abstract class ResourceValueRenderer implements ResourceItemResolver.ResourceProvider {
    private final Module myModule;
    private FrameworkResources myFrameworkResources;
    private ProjectResources myAppResources;

    protected ResourceValueRenderer(Module module) {
      myModule = module;
    }

    @Nullable
    public abstract String renderToHtml(@NotNull ItemInfo item, @NotNull ResourceType type, @NotNull String name);

    /** Creates a renderer suitable for the given resource type */
    @Nullable
    public static ResourceValueRenderer create(@NotNull ResourceType type, @NotNull Module module) {
      switch (type) {
        case STRING:
        case DIMEN:
        case INTEGER:
        case BOOL:
          return new TextValueRenderer(module);
        case ARRAY:
          return new ArrayRenderer(module);
        case DRAWABLE:
          return new DrawableValueRenderer(module);
        case COLOR:
          return new ColorValueRenderer(module);
        default:
          // Ignore
          return null;
      }
    }

    @Nullable
    private static FrameworkResources getFrameworkResources(Module module) {
      AndroidPlatform platform = AndroidPlatform.getPlatform(module);
      if (platform != null) {
        AndroidTargetData targetData = AndroidTargetData.getTargetData(platform.getTarget(), module);
        if (targetData != null) {
          try {
            return targetData.getFrameworkResources();
          }
          catch (IOException e) {
            // Ignore docs
          }
        }
      }

      return null;
    }

    @Nullable
    public String render(@NotNull ResourceType type, @NotNull String name, boolean framework) {
      List<ItemInfo> items = gatherItems(type, name, framework);
      if (items != null) {
        Collections.sort(items);
        return renderKeyValues(items, type, name);
      }

      return null;
    }

    @Nullable
    private List<ItemInfo> gatherItems(@NotNull ResourceType type, @NotNull String name, boolean framework) {
      if (framework) {
        List<ItemInfo> results = Lists.newArrayList();
        addItemsFromFramework(null, MASK_NORMAL, 0, type, name, results);
        return results;
      }

      AndroidFacet facet = AndroidFacet.getInstance(myModule);
      if (facet == null) {
        return null;
      }

      List<ItemInfo> results = Lists.newArrayList();

      AbstractResourceRepository resources = getAppResources();
      IdeaAndroidProject ideaAndroidProject = facet.getIdeaAndroidProject();
      if (ideaAndroidProject != null) {
        assert facet.isGradleProject();
        AndroidProject delegate = ideaAndroidProject.getDelegate();
        Variant selectedVariant = ideaAndroidProject.getSelectedVariant();
        Set<SourceProvider> selectedProviders = Sets.newHashSet();

        SourceProvider buildType = delegate.getBuildTypes().get(selectedVariant.getBuildType()).getSourceProvider();
        String buildTypeName = selectedVariant.getName();
        int rank = 0;
        addItemsFromSourceSet(buildTypeName, MASK_FLAVOR_SELECTED, rank++, buildType, type, name, results, facet);
        selectedProviders.add(buildType);

        List<String> productFlavors = selectedVariant.getProductFlavors();
        // Iterate in *reverse* order
        for (int i = productFlavors.size() - 1; i >= 0; i--) {
          String flavor = productFlavors.get(i);
          SourceProvider provider = delegate.getProductFlavors().get(flavor).getSourceProvider();
          addItemsFromSourceSet(flavor, MASK_FLAVOR_SELECTED, rank++, provider, type, name, results, facet);
          selectedProviders.add(provider);
        }

        SourceProvider main = delegate.getDefaultConfig().getSourceProvider();
        addItemsFromSourceSet("main", MASK_FLAVOR_SELECTED, rank++, main, type, name, results, facet);
        selectedProviders.add(main);

        // Next display any source sets that are *not* in the selected flavors or build types!
        Collection<BuildTypeContainer> buildTypes = delegate.getBuildTypes().values();
        for (BuildTypeContainer container : buildTypes) {
          SourceProvider provider = container.getSourceProvider();
          if (!selectedProviders.contains(provider)) {
            addItemsFromSourceSet(container.getBuildType().getName(), MASK_NORMAL, rank++, provider, type, name, results, facet);
            selectedProviders.add(provider);
          }
        }

        Map<String,ProductFlavorContainer> flavors = delegate.getProductFlavors();
        for (Map.Entry<String,ProductFlavorContainer> entry : flavors.entrySet()) {
          ProductFlavorContainer container = entry.getValue();
          SourceProvider provider = container.getSourceProvider();
          if (!selectedProviders.contains(provider)) {
            addItemsFromSourceSet(entry.getKey(), MASK_NORMAL, rank++, provider, type, name, results, facet);
            selectedProviders.add(provider);
          }
        }

        // Also pull in items from libraries; this will include items from the current module as well,
        // so add them to a temporary list so we can only add the items that are missing
        if (resources instanceof MultiResourceRepository) {
          ProjectResources primary = ProjectResources.get(myModule, false);
          MultiResourceRepository multi = (MultiResourceRepository)resources;
          for (ProjectResources dependency : multi.getChildren()) {
            if (dependency != primary) {
              addItemsFromRepository(dependency.getDisplayName(), MASK_NORMAL, rank++, dependency, type, name, results);
            }
          }
        }
      } else if (resources != null) {
        addItemsFromRepository(null, MASK_NORMAL, 0, resources, type, name, results);
      }

      return results;
    }

    private static void addItemsFromSourceSet(@Nullable String flavor,
                                              int mask,
                                              int rank,
                                              @NotNull SourceProvider sourceProvider,
                                              @NotNull ResourceType type,
                                              @NotNull String name,
                                              @NotNull List<ItemInfo> results,
                                              @NotNull AndroidFacet facet) {
      Set<File> resDirectories = sourceProvider.getResDirectories();
      LocalFileSystem fileSystem = LocalFileSystem.getInstance();
      for (File dir : resDirectories) {
        VirtualFile virtualFile = fileSystem.findFileByIoFile(dir);
        if (virtualFile != null) {
          ResourceFolderRepository resources = ResourceFolderRegistry.get(facet,  virtualFile);
          addItemsFromRepository(flavor, mask, rank, resources, type, name, results);
        }
      }
    }

    private void addItemsFromFramework(@Nullable String flavor,
                                       int mask,
                                       int rank,
                                       @NotNull ResourceType type,
                                       @NotNull String name,
                                       @NotNull List<ItemInfo> results) {
      ResourceRepository frameworkResources = getFrameworkResources();
      if (frameworkResources == null) {
        return;
      }
      if (frameworkResources.hasResourceItem(type, name)) {
        com.android.ide.common.resources.ResourceItem item = frameworkResources.getResourceItem(type, name);

        for (com.android.ide.common.resources.ResourceFile resourceFile : item.getSourceFileList()) {
          FolderConfiguration configuration = resourceFile.getConfiguration();
          ResourceValue value = resourceFile.getValue(type, name);

          String folderName = resourceFile.getFolder().getFolder().getName();
          String folder = renderFolderName(folderName);
          ItemInfo info = new ItemInfo(value, configuration, folder, flavor, rank, mask);
          results.add(info);
        }
      }
    }

    private static void addItemsFromRepository(@Nullable String flavor,
                                               int mask,
                                               int rank,
                                               @NotNull AbstractResourceRepository resources,
                                               @NotNull ResourceType type,
                                               @NotNull String name,
                                               @NotNull List<ItemInfo> results) {
      List<ResourceItem> items = resources.getResourceItem(type, name);
      if (items != null) {
        for (ResourceItem item : items) {
          String folderName = "?";
          ResourceFile source = item.getSource();
          if (source != null) {
            folderName = source.getFile().getParentFile().getName();
          }
          String folder = renderFolderName(folderName);
          ResourceValue value = item.getResourceValue(resources.isFramework());
          ItemInfo info = new ItemInfo(value, item.getConfiguration(), folder, flavor, rank, mask);
          results.add(info);
        }
      }
    }

    @Nullable
    private String renderKeyValues(@NotNull List<ItemInfo> items, @NotNull ResourceType type, @NotNull String name) {
      if (items.isEmpty()) {
        return null;
      }

      markHidden(items);

      HtmlBuilder builder = new HtmlBuilder();
      builder.openHtmlBody();
      if (items.size() == 1) {
        String value = renderToHtml(items.get(0), type, name);
        if (value != null) {
          builder.addHtml(value);
        }
      } else {
        //noinspection SpellCheckingInspection
        builder.beginTable("valign=\"top\"");

        boolean haveFlavors = haveFlavors(items);
        if (haveFlavors) {
          builder.addTableRow(true, "Flavor/Library", "Configuration", "Value");
        } else {
          builder.addTableRow(true, "Configuration", "Value");
        }

        String prevFlavor = null;
        for (ItemInfo info : items) {
          String value = renderToHtml(info, type, name);
          if (value == null) {
            value = "";
          }
          String folder = info.folder;
          String flavor = StringUtil.notNullize(info.flavor);
          if (flavor.equals(prevFlavor)) {
            flavor = "";
          } else {
            prevFlavor = flavor;
          }

          builder.addHtml("<tr>");
          if (haveFlavors) {
            // Bold selected flavors?
            String style = ( (info.displayMask & MASK_FLAVOR_SELECTED) != 0) ? "b" : null;
            addTableCell(builder, flavor, style);
          }
          addTableCell(builder, folder, null);
          String style = ( (info.displayMask & MASK_ITEM_HIDDEN) != 0) ? "s" : null;
          addTableCell(builder, value, style);
          builder.addHtml("</tr>");
        }

        builder.endTable();
      }
      builder.addHtml("</body></html>");
      return builder.getHtml();
    }

    protected void displayChain(@NotNull ResourceType type, @NotNull String name, @NotNull List<ResourceValue> lookupChain,
                                @NotNull HtmlBuilder builder) {
      if (lookupChain.size() > 1) {
        builder.add(PREFIX_RESOURCE_REF);
        builder.add(type.getName());
        builder.add("/");
        builder.add(name);
        ResourceValue prev = null;
        for (ResourceValue element : lookupChain) {
          if (element == null) {
            continue;
          }
          String text = element.getValue();
          if (prev != null && text.equals(prev.getValue())) {
            continue;
          }

          builder.add(" => ");

          // Strip paths
          if (!(text.startsWith(PREFIX_THEME_REF) || text.startsWith(PREFIX_RESOURCE_REF))) {
            int end = Math.max(text.lastIndexOf('/'), text.lastIndexOf('\\'));
            if (end != -1) {
              text = text.substring(end + 1);
            }
          }
          builder.add(text);
          prev = element;
        }
        builder.newline();
      }
    }


    // ---- Implements ResourceItemResolver.ResourceProvider ----

    @Override
    @Nullable
    public ResourceRepository getFrameworkResources() {
      if (myFrameworkResources == null) {
        myFrameworkResources = getFrameworkResources(myModule);
      }

      return myFrameworkResources;
    }

    @Override
    @Nullable
    public ProjectResources getAppResources() {
      if (myAppResources == null) {
        myAppResources = ProjectResources.get(myModule, true, true);
      }

      return myAppResources;
    }

    @Override
    @Nullable
    public ResourceResolver getResolver(boolean createIfNecessary) {
      return null;
    }
  }

  private static boolean haveFlavors(List<ItemInfo> items) {
    for (ItemInfo info : items) {
      if (info.flavor != null) {
        return true;
      }
    }

    return false;
  }

  private static void markHidden(List<ItemInfo> items) {
    Set<String> hiddenQualifiers = Sets.newHashSet();
    for (ItemInfo info : items) {
      String folder = info.folder;

      if (hiddenQualifiers.contains(folder)) {
        info.displayMask |= MASK_ITEM_HIDDEN;
      }
      hiddenQualifiers.add(folder);
    }
  }

  private static void addTableCell(HtmlBuilder builder, String text, @Nullable String attribute) {
    //noinspection SpellCheckingInspection
    builder.addHtml("<td valign=\"top\">");
    if (attribute != null) {
      builder.addHtml("<").addHtml(attribute).addHtml(">");
    }

    builder.addHtml(text);

    if (attribute != null) {
      builder.addHtml("</").addHtml(attribute).addHtml(">");
    }
    builder.addHtml("</td>");
  }

  private static String renderFolderName(String name) {
    String prefix = SdkConstants.FD_RES_VALUES;

    if (name.equals(prefix)) {
      return "Default";
    }

    if (name.startsWith(prefix + '-')) {
      return name.substring(prefix.length() + 1);
    } else {
      return name;
    }
  }

  private static class ArrayRenderer extends ResourceValueRenderer {
    private ArrayRenderer(Module module) {
      super(module);
    }

    @Override
    public String renderToHtml(@NotNull ItemInfo item, @NotNull ResourceType type, @NotNull String name) {
      ResourceValue value = item.value;
      if (value instanceof ArrayResourceValue) {
        ArrayResourceValue arv = (ArrayResourceValue)value;
        return Joiner.on(", ").skipNulls().join(arv);
      }

      if (item.value != null) {
        return item.value.getValue();
      }
      return null;
    }
  }

  private static class TextValueRenderer extends ResourceValueRenderer {
    private TextValueRenderer(Module module) {
      super(module);
    }

    @Override
    public String renderToHtml(@NotNull ItemInfo item, @NotNull ResourceType type, @NotNull String name) {
      if (item.value != null) {
        return item.value.getValue();
      }
      return null;
    }
  }

  private static class DrawableValueRenderer extends ResourceValueRenderer {
    private DrawableValueRenderer(Module module) {
      super(module);
    }

    @Override
    public String renderToHtml(@NotNull ItemInfo item, @NotNull ResourceType type, @NotNull String name) {
      ResourceValue value = item.value;
      ResourceItemResolver resolver = new ResourceItemResolver(item.configuration, this, null);
      List<ResourceValue> lookupChain = Lists.newArrayList();
      lookupChain.add(value);
      resolver.setLookupChainList(lookupChain);
      File bitmap = ResourceHelper.resolveDrawable(resolver, value);
      if (bitmap != null && bitmap.exists() && hasImageExtension(bitmap.getPath())) {
        URL url = null;
        try {
          url = SdkUtils.fileToUrl(bitmap);
        }
        catch (MalformedURLException e) {
          // pass
        }

        if (url != null) {
          HtmlBuilder builder = new HtmlBuilder();
          builder.beginDiv("background-color:gray;padding:10px");
          builder.addImage(url, bitmap.getPath());
          builder.endDiv();

          Dimension size = getSize(bitmap);
          if (size != null) {
            DensityQualifier densityQualifier = item.configuration.getDensityQualifier();
            Density density = densityQualifier == null ? Density.MEDIUM : densityQualifier.getValue();

            builder.addHtml(String.format(Locale.US, "%1$d&#xd7;%2$d px (%3$d&#xd7;%4$d dp @ %5$s)", size.width, size.height,
                                          px2dp(size.width, density), px2dp(size.height, density), density.getResourceValue()));
            builder.newline();
            displayChain(type, name, lookupChain, builder);
          }

          return builder.getHtml();
        }
      }

      if (value != null) {
        return XmlUtils.toXmlTextValue(value.getValue());
      }

      return null;
    }

    private static int px2dp(int px, Density density) {
      return (int)((float)px * Density.MEDIUM.getDpiValue() / density.getDpiValue());
    }
  }

  private static class ColorValueRenderer extends ResourceValueRenderer {
    private ColorValueRenderer(Module module) {
      super(module);
    }

    @Override
    public String renderToHtml(@NotNull ItemInfo item, @NotNull ResourceType type, @NotNull String name) {
      ResourceValue value = item.value;
      ResourceItemResolver resolver = new ResourceItemResolver(item.configuration, this, null);
      List<ResourceValue> lookupChain = Lists.newArrayList();
      lookupChain.add(value);
      resolver.setLookupChainList(lookupChain);
      Color color = ResourceHelper.resolveColor(resolver, value);
      if (color != null) {
        HtmlBuilder builder = new HtmlBuilder();
        String colorString = String.format(Locale.US, "rgb(%d,%d,%d)", color.getRed(), color.getGreen(), color.getBlue());
        String foregroundColor = ColorUtil.isDark(color) ? "white" : "black";
        String css = "background-color:" + colorString + ";color:" + foregroundColor +
                     ";width:200px;text-align:center;vertical-align:middle;";
        // Use <table> tag such that we can center the color text (Java's HTML renderer doesn't support
        // vertical-align:middle on divs)
        builder.addHtml("<table style=\"" + css + "\" border=\"0\"><tr height=\"100\">");
        builder.addHtml("<td align=\"center\" valign=\"middle\" height=\"100\">");
        builder.add('#' + ColorUtil.toHex(color));
        builder.addHtml("</td></tr></table>");
        builder.newline();
        displayChain(type, name, lookupChain, builder);

        return builder.getHtml();
      }

      if (value != null) {
        return XmlUtils.toXmlTextValue(value.getValue());
      }

      return null;
    }
  }

  /**
   * Returns the dimensions of an Image if it can be obtained without fully reading it into memory.
   * This is a copy of the method in {@link com.android.tools.lint.checks.IconDetector}.
   */
  @Nullable
  private static Dimension getSize(File file) {
    try {
      ImageInputStream input = ImageIO.createImageInputStream(file);
      if (input != null) {
        try {
          Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
          if (readers.hasNext()) {
            ImageReader reader = readers.next();
            try {
              reader.setInput(input);
              return new Dimension(reader.getWidth(0), reader.getHeight(0));
            } finally {
              reader.dispose();
            }
          }
        } finally {
          input.close();
        }
      }

      // Fallback: read the image using the normal means
      //BufferedImage image = ImageIO.read(file);
      //if (image != null) {
      //  return new Dimension(image.getWidth(), image.getHeight());
      //} else {
      //  return null;
      //}
      return null;
    } catch (IOException e) {
      // Pass -- we can't handle all image types, warn about those we can
      return null;
    }
  }

  /** Normal display style */
  private static final int MASK_NORMAL = 0;
  /** Display style for flavor folders that are selected */
  private static final int MASK_FLAVOR_SELECTED = 1;
  /** Display style for items that are hidden by later resource folders */
  private static final int MASK_ITEM_HIDDEN = 2;

  /**
   * Information about {@link ResourceItem} instances to be displayed; in addition to the item and the
   * folder name, we can also record the flavor or library name, as well as display attributes indicating
   * whether the item is from a selected flavor, or whether the item is masked by a higher priority repository
   */
  private static class ItemInfo implements Comparable<ItemInfo> {
    @Nullable public final ResourceValue value;
    @NotNull public final FolderConfiguration configuration;
    @Nullable public final String flavor;
    @NotNull public final String folder;
    public final int rank;
    public int displayMask;

    private ItemInfo(@Nullable ResourceValue value, @NotNull FolderConfiguration configuration,
                     @NotNull String folder, @Nullable String flavor, int rank, int initialMask) {
      this.value = value;
      this.configuration = configuration;
      this.flavor = flavor;
      this.folder = folder;
      this.displayMask = initialMask;
      this.rank = rank;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ItemInfo itemInfo = (ItemInfo)o;

      if (rank != itemInfo.rank) return false;
      if (!configuration.equals(itemInfo.configuration)) return false;
      if (flavor != null ? !flavor.equals(itemInfo.flavor) : itemInfo.flavor != null) return false;
      if (!folder.equals(itemInfo.folder)) return false;
      if (value != null ? !value.equals(itemInfo.value) : itemInfo.value != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = value != null ? value.hashCode() : 0;
      result = 31 * result + configuration.hashCode();
      result = 31 * result + (flavor != null ? flavor.hashCode() : 0);
      result = 31 * result + folder.hashCode();
      result = 31 * result + rank;
      return result;
    }

    @Override
    public int compareTo(@NotNull ItemInfo other) {
      if (rank != other.rank) {
        return rank - other.rank;
      }

      // Special case density: when we're showing multiple drawables for different densities,
      // sort by density value, not alphabetical name.
      DensityQualifier density1 = configuration.getDensityQualifier();
      DensityQualifier density2 = other.configuration.getDensityQualifier();
      if (density1 != null && density2 != null) {
        // Start with the lowest densities to avoid case where you have a giant asset (say xxxhdpi)
        // and you only see the top left corner in the documentation window.
        int delta = density2.getValue().compareTo(density1.getValue());
        if (delta != 0) {
          return delta;
        }
      }

      return configuration.compareTo(other.configuration);
    }
  }
}
