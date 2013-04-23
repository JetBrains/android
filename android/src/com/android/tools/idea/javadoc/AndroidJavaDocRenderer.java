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
import com.android.ide.common.resources.ResourceFile;
import com.android.ide.common.resources.ResourceItem;
import com.android.resources.Density;
import com.android.resources.ResourceType;
import com.android.tools.idea.rendering.ProjectResources;
import com.android.utils.XmlUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class AndroidJavaDocRenderer {
  /** Renders the Javadoc for a resource of given type and name. */
  @Nullable
  public static String render(ProjectResources projectResources, ResourceType type, String name) {
    if (ResourceType.STRING.equals(type)) {
      ResourceItem item = projectResources.getResourceItem(type, name);
      return renderStringValues(sort(item.getSourceFileList()), type, name);
    } else if (ResourceType.DIMEN.equals(type)) {
      ResourceItem item = projectResources.getResourceItem(type, name);
      return renderDimension(sort(item.getSourceFileList()), type, name);
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
  private static String renderDimension(List<ResourceFile> files, ResourceType type, String name) {
    if (files.isEmpty()) {
      return null;
    }

    StringBuilder sb = new StringBuilder(files.size() * 40);

    startTableRow(sb);
    addTableColumnData(sb, "Configuration");
    addTableColumnData(sb, "Value");
    for (Density d : Density.values()) {
      if (!d.isValidValueForDevice()) {
        continue;
      }

      addTableColumnData(sb, d.getResourceValue().toUpperCase());
    }
    endTableRow(sb);

    for (ResourceFile f : files) {
      String v = f.getValue(type, name).getValue();
      startTableRow(sb);
      addTableColumnData(sb, renderFolderName(f.getFolder().getFolder().getName()));
      addTableColumnData(sb, "<b>" + v + "</b>");
      for (Density d : Density.values()) {
        if (!d.isValidValueForDevice()) {
          continue;
        }

        addTableColumnData(sb, dpToPixels(v, d));
      }
      endTableRow(sb);
    }

    return String.format("<html><body><table>%s</table><body></html>", sb.toString());
  }

  private static void addTableColumnData(StringBuilder sb, String data) {
    sb.append("<td>");
    sb.append(data);
    sb.append("</td>");
  }

  private static void endTableRow(StringBuilder sb) {
    sb.append("</tr>");
  }

  private static void startTableRow(StringBuilder sb) {
    sb.append("<tr>");
  }

  /** Converts from {@link SdkConstants#UNIT_DP}'s at given density to pixels. */
  public static String dpToPixels(@NotNull String dp, Density density) {
    if (!dp.endsWith(SdkConstants.UNIT_DIP) && !dp.endsWith(SdkConstants.UNIT_DP)) {
      return dp;
    }

    float dpf;
    try {
      dpf = (float) (Integer.parseInt(dp.substring(0, dp.indexOf('d'))));
    } catch (NumberFormatException e) {
      return dp;
    }

    return String.format(Locale.US, "%dpx",
                         (int)(dpf * density.getDpiValue() / Density.DEFAULT_DENSITY));
  }

  @Nullable
  private static String renderStringValues(List<ResourceFile> files, ResourceType type, String name) {
    if (files.isEmpty()) {
      return null;
    }

    if (files.size() == 1) {
      return String.format("<html><body>%1$s</body></html>",
                           files.get(0).getValue(type, name).getValue());
    }

    StringBuilder sb = new StringBuilder(files.size() * 20);
    for (ResourceFile f : files) {
      ResourceValue value = f.getValue(type, name);
      String k = renderFolderName(f.getFolder().getFolder().getName());
      String v = XmlUtils.toXmlTextValue(value.getValue());
      sb.append(String.format("<tr><td>%1$s</td><td>%2$s</td></tr>", k, v));
    }

    return String.format("<html><body><table>%s</table><body></html>", sb.toString());
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
}
