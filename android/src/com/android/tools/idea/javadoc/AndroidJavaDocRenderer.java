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
import com.android.ide.common.resources.ResourceFile;
import com.android.ide.common.resources.ResourceItem;
import com.android.resources.ResourceType;
import com.android.tools.idea.rendering.HtmlBuilder;
import com.android.tools.idea.rendering.ProjectResources;
import com.android.utils.XmlUtils;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AndroidJavaDocRenderer {
  /** Renders the Javadoc for a resource of given type and name. */
  @Nullable
  public static String render(ProjectResources projectResources, ResourceType type, String name) {
    if (ResourceType.STRING.equals(type) || ResourceType.DIMEN.equals(type)
        || ResourceType.INTEGER.equals(type)) {
      ResourceItem item = projectResources.getResourceItem(type, name);
      return renderKeyValues(sort(item.getSourceFileList()), type, name, new TextValueRenderer());
    } else if (ResourceType.DRAWABLE.equals(type)) {
      ResourceItem item = projectResources.getResourceItem(type, name);
      return renderKeyValues(sort(item.getSourceFileList()), type, name, new DrawableValueRenderer());
    } else {
      return null;
    }
  }

  private static List<ResourceFile> sort(List<ResourceFile> resourceFiles) {
    List<ResourceFile> copy = new ArrayList<ResourceFile>(resourceFiles);
    Collections.sort(copy, new Comparator<ResourceFile>() {
      @Override
      public int compare(ResourceFile f1, ResourceFile f2) {
        String k1 = f1.getFolder().getFolder().getName();
        String k2 = f2.getFolder().getFolder().getName();
        return k1.compareTo(k2);
      }
    });
    return copy;
  }

  @Nullable
  private static String renderKeyValues(List<ResourceFile> files, ResourceType type, String name,
                                        ResourceValueRenderer renderer) {
    if (files.isEmpty()) {
      return null;
    }

    HtmlBuilder builder = new HtmlBuilder();
    if (files.size() == 1) {
      String value = renderer.renderToHtml(files.get(0), type, name);
      builder.addHtml(value);
    } else {
      builder.beginTable();
      builder.addTableRow(true, "Configuration", "Value");

      for (ResourceFile f : files) {
        String v = renderer.renderToHtml(f, type, name);
        builder.addTableRow(renderFolderName(f.getFolder().getFolder().getName()), v);
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
    String renderToHtml(ResourceFile f, ResourceType type, String name);
  }

  private static class TextValueRenderer implements ResourceValueRenderer {
    @Override
    public String renderToHtml(ResourceFile f, ResourceType type, String name) {
      String v = f.getValue(type, name).getValue();
      return XmlUtils.toXmlTextValue(v);
    }
  }

  private static class DrawableValueRenderer implements ResourceValueRenderer {
    @Override
    public String renderToHtml(ResourceFile f, ResourceType type, String name) {
      String v = f.getValue(type, name).getValue();
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
            return builder.getHtml();
          }
        }
      }

      return XmlUtils.toXmlTextValue(v);
    }

    private static boolean isBitmapDrawable(String v) {
      return v.endsWith(SdkConstants.DOT_PNG)
             || v.endsWith(SdkConstants.DOT_9PNG)
             || v.endsWith(SdkConstants.DOT_GIF)
             || v.endsWith(SdkConstants.DOT_JPEG)
             || v.endsWith(SdkConstants.DOT_JPG);
    }
  }
}
