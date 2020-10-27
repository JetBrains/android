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
package com.android.tools.idea.run.activity.manifest;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class ManifestActivityInfo {

  private List<NodeActivity> myActivities;
  private String myPackageName;

  private ManifestActivityInfo() {
    myActivities = new ArrayList<>();
    myPackageName = "";
  }

  @NotNull
  public String packageName() {
    return myPackageName;
  }

  @NotNull
  public List<NodeActivity> activities() {
    return myActivities;
  }

  @NotNull
  public static ManifestActivityInfo parseBinaryFromStream(@NotNull InputStream inputStream) throws IOException {
    XmlNode node = BinaryXmlParser.parse(inputStream);
    ManifestActivityInfo manifest = new ManifestActivityInfo();
    manifest.parseNode(node);
    return manifest;
  }

  private void parseNode(@NotNull XmlNode node) {
    for (String attribute : node.attributes().keySet()) {
      String value = node.attributes().get(attribute);
      if ("package".equals(attribute)) {
        myPackageName = value;
      }
    }

    for(XmlNode child : node.childs()) {
      if ("application".equals(child.name())) {
        parseApplication(child);
      }
    }
  }

  private void parseApplication(@NotNull XmlNode node) {
    for(XmlNode child : node.childs()) {
      if ("activity".equals(child.name()) || "activity-alias".equals(child.name())) {
        NodeActivity activity = new NodeActivity(child, myPackageName);
        myActivities.add(activity);
      }
    }
  }
}
