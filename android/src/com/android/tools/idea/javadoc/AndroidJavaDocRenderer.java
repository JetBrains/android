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
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.ResourceFile;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.resources.Density;
import com.android.resources.ResourceType;
import com.android.tools.idea.rendering.HtmlBuilder;
import com.android.tools.idea.rendering.ProjectResources;
import com.android.utils.XmlUtils;
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

public class AndroidJavaDocRenderer {
  /** Renders the Javadoc for a resource of given type and name. */
  @Nullable
  public static String render(ProjectResources projectResources, ResourceType type, String name) {
    if (ResourceType.STRING.equals(type) || ResourceType.DIMEN.equals(type)
        || ResourceType.INTEGER.equals(type) || ResourceType.BOOL.equals(type)) {
      List<ResourceItem> items = projectResources.getResourceItem(type, name);
      if (items != null) {
        return renderKeyValues(sort(items), type, name, new TextValueRenderer(), projectResources);
      }
    } else if (ResourceType.DRAWABLE.equals(type)) {
      List<ResourceItem> items = projectResources.getResourceItem(type, name);
      if (items != null) {
        return renderKeyValues(sort(items), type, name, new DrawableValueRenderer(), projectResources);
      }
    }
    return null;
  }

  private static List<ResourceItem> sort(@NotNull List<ResourceItem> resourceFiles) {
    List<ResourceItem> copy = new ArrayList<ResourceItem>(resourceFiles);
    Collections.sort(copy, new Comparator<ResourceItem>() {
      @Override
      public int compare(ResourceItem item1, ResourceItem item2) {
        ResourceFile file1 = item1.getSource();
        ResourceFile file2 = item2.getSource();
        String parent1 = file1 != null ? file1.getFile().getParentFile().getName() : "";
        String parent2 = file2 != null ? file2.getFile().getParentFile().getName() : "";
        return parent1.compareTo(parent2);
      }
    });
    return copy;
  }

  @Nullable
  private static String renderKeyValues(List<ResourceItem> items, ResourceType type, String name,
                                        ResourceValueRenderer renderer, ProjectResources resources) {
    if (items.isEmpty()) {
      return null;
    }

    HtmlBuilder builder = new HtmlBuilder();
    if (items.size() == 1) {
      String value = renderer.renderToHtml(resources, items.get(0));
      builder.addHtml(value);
    } else {
      builder.beginTable("valign=\"top\"");
      builder.addTableRow(true, "Configuration", "Value");

      for (ResourceItem f : items) {
        String v = renderer.renderToHtml(resources, f);
        String folderName = "?";
        ResourceFile source = f.getSource();
        if (source != null) {
          folderName = source.getFile().getParentFile().getName();
        }
        builder.addTableRow(renderFolderName(folderName), v);
      }

      builder.endTable();
    }
    return String.format("<html><body>%s</body></html>", builder.getHtml());
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
      if (!isBitmapDrawable(v)) {
        v = getStringValue(resources, item);
        if (v == null) {
          return "";
        }
      }

      if (isBitmapDrawable(v)) {
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

    private static boolean isBitmapDrawable(String v) {
      return v.endsWith(SdkConstants.DOT_PNG)
             || v.endsWith(SdkConstants.DOT_9PNG)
             || v.endsWith(SdkConstants.DOT_GIF)
             || v.endsWith(SdkConstants.DOT_JPEG)
             || v.endsWith(SdkConstants.DOT_JPG);
    }
  }

  /**
   * Returns the dimensions of an Image if it can be obtained without fully reading it into memory.
   * This is a copy of the method in {@link com.android.tools.lint.checks.IconDetector}.
   */
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
          if (input != null) {
            input.close();
          }
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
}
