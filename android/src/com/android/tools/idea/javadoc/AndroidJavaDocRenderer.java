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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AndroidJavaDocRenderer {
  /** Renders the Javadoc for a resource of given type and name. */
  @Nullable
  public static String render(ProjectResources projectResources, ResourceType type, String name) {
    if (ResourceType.STRING.equals(type) || ResourceType.DIMEN.equals(type) || ResourceType.INTEGER.equals(type)) {
      ResourceItem item = projectResources.getResourceItem(type, name);
      return renderKeyValues(sort(item.getSourceFileList()), type, name);
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
  private static String renderKeyValues(List<ResourceFile> files, ResourceType type, String name) {
    if (files.isEmpty()) {
      return null;
    }

    HtmlBuilder builder = new HtmlBuilder();
    if (files.size() == 1) {
      String value = files.get(0).getValue(type, name).getValue();
      builder.add(value);
    } else {
      builder.beginTable();
      builder.addTableRow(true, "Configuration", "Value");

      for (ResourceFile f : files) {
        String v = f.getValue(type, name).getValue();
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
}
