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
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.ResourceFile;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.resources.Density;
import com.android.resources.ResourceType;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.rendering.*;
import com.android.utils.XmlUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
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

import static org.jetbrains.android.util.AndroidUtils.hasImageExtension;

public class AndroidJavaDocRenderer {
  /** Renders the Javadoc for a resource of given type and name. */
  @Nullable public static String render(Module module, ResourceType type, String name) {
    ProjectResources projectResources = ProjectResources.get(module, true, true);
    if (projectResources == null) {
      return null;
    }

    if (ResourceType.STRING.equals(type) || ResourceType.DIMEN.equals(type)
        || ResourceType.INTEGER.equals(type) || ResourceType.BOOL.equals(type)) {
      return renderKeyValues(module, new TextValueRenderer(), projectResources, type, name);
    } else if (ResourceType.DRAWABLE.equals(type)) {
      return renderKeyValues(module, new DrawableValueRenderer(), projectResources, type, name);
    }
    return null;
  }

  @Nullable
  private static String renderKeyValues(Module module, ResourceValueRenderer renderer,
                                        ProjectResources resources, ResourceType type, String name) {
    List<ItemInfo> items = gatherItems(module, resources, type, name);
    if (items != null) {
      Collections.sort(items);
      return renderKeyValues(items, renderer, resources);
    }

    return null;
  }

  @Nullable
  private static List<ItemInfo> gatherItems(Module module, ProjectResources resources, ResourceType type, String name) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return null;
    }

    List<ItemInfo> results = Lists.newArrayList();

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
        ProjectResources primary = ProjectResources.get(module, false);
        MultiResourceRepository multi = (MultiResourceRepository)resources;
        for (ProjectResources dependency : multi.getChildren()) {
          if (dependency != primary) {
            addItemsFromRepository(dependency.getDisplayName(), MASK_NORMAL, rank++, dependency, type, name, results);
          }
        }
      }
    } else {
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

  private static void addItemsFromRepository(@Nullable String flavor,
                                             int mask,
                                             int rank,
                                             @NotNull ProjectResources resources,
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
        ItemInfo info = new ItemInfo(item, folder, flavor, rank, mask);
        results.add(info);
      }
    }
  }

  @Nullable
  private static String renderKeyValues(List<ItemInfo> items, ResourceValueRenderer renderer,
                                        ProjectResources resources) {
    if (items.isEmpty()) {
      return null;
    }

    markHidden(items);

    HtmlBuilder builder = new HtmlBuilder();
    builder.openHtmlBody();
    if (items.size() == 1) {
      String value = renderer.renderToHtml(resources, items.get(0).item);
      builder.addHtml(value);
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
        String value = renderer.renderToHtml(resources, info.item);
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

  private interface ResourceValueRenderer {
    String renderToHtml(ProjectResources resources, @NotNull ResourceItem item);
  }

  private static class TextValueRenderer implements ResourceValueRenderer {
    @Override
    public String renderToHtml(ProjectResources resources, @NotNull ResourceItem item) {
      String value = getStringValue(resources, item);
      if (value == null) {
        return "";
      }

      return value;
    }
  }

  @Nullable
  private static String getStringValue(@NotNull ProjectResources resources, @NotNull ResourceItem item) {
    String v = item.getValueText();
    if (v == null) {
      ResourceValue value = item.getResourceValue(resources.isFramework());
      if (value != null) {
        return value.getValue();
      }
    }
    return v;
  }

  private static class DrawableValueRenderer implements ResourceValueRenderer {
    @Override
    public String renderToHtml(ProjectResources resources, @NotNull ResourceItem item) {
      ResourceFile source = item.getSource();
      if (source == null) {
        return "";
      }
      String v = source.getFile().getPath();
      if (!hasImageExtension(v)) {
        v = getStringValue(resources, item);
        if (v == null) {
          return "";
        }
      }

      if (hasImageExtension(v)) {
        File bitmap = new File(v);
        if (bitmap.exists()) {
          URL url = null;
          try {
            url = bitmap.toURI().toURL();
          }
          catch (MalformedURLException e) {
            // pass
          }

          if (url != null) {
            HtmlBuilder builder = new HtmlBuilder();
            builder.beginDiv("background-color:gray;padding:10px");
            builder.addImage(url, v);
            builder.endDiv();

            Dimension size = getSize(bitmap);
            if (size != null) {
              DensityQualifier densityQualifier = item.getConfiguration().getDensityQualifier();
              Density density = densityQualifier == null ? Density.MEDIUM : densityQualifier.getValue();

              builder.addHtml(String.format(Locale.US, "%1$d&#xd7;%2$d px (%3$d&#xd7;%4$d dp @ %5$s)", size.width, size.height,
                                        px2dp(size.width, density), px2dp(size.height, density), density.getResourceValue()));
              builder.newline();
            }

            return builder.getHtml();
          }
        }
      }

      return XmlUtils.toXmlTextValue(v);
    }

    private static int px2dp(int px, Density density) {
      return (int)((float)px * Density.MEDIUM.getDpiValue() / density.getDpiValue());
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
    @NotNull public final ResourceItem item;
    @Nullable public final String flavor;
    @NotNull public final String folder;
    public final int rank;
    public int displayMask;

    private ItemInfo(@NotNull ResourceItem item, @NotNull String folder, @Nullable String flavor, int rank, int initialMask) {
      this.item = item;
      this.flavor = flavor;
      this.folder = folder;
      this.displayMask = initialMask;
      this.rank = rank;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ItemInfo line = (ItemInfo)o;

      if (!item.equals(line.item)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return item.hashCode();
    }

    @Override
    public int compareTo(@NotNull ItemInfo other) {
      if (rank != other.rank) {
        return rank - other.rank;
      }
      ResourceFile file1 = item.getSource();
      ResourceFile file2 = other.item.getSource();
      String parent1 = file1 != null ? file1.getFile().getParentFile().getName() : "";
      String parent2 = file2 != null ? file2.getFile().getParentFile().getName() : "";
      return parent1.compareTo(parent2);
    }
  }
}
