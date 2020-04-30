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

import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link NodeActivity} from {@link SimpleXmlNode} entry that contains a "targetActivity" attribute.
 */
public class NodeActivityAlias extends NodeActivity {

  public NodeActivityAlias(@NotNull XmlNode node, @NotNull String name, @NotNull List<NodeActivity> activities) {
    super(node, name);

    for (String attribute : node.attributes().keySet()) {
      String value = node.attributes().get(attribute);
      if ("targetActivity".equals(attribute)) {
        for(NodeActivity activity : activities) {
          if (value.equals(activity.getName())) {
            myQname = activity.getQualifiedName();
          }
        }
      }
      if (myQname.equals("")) {
        throw new IllegalStateException("Unable to find target of activity-alias '" + name + "''");
      }
    }

  }

}
