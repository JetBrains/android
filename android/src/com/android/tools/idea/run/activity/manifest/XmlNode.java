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

import com.google.devrel.gmscore.tools.apk.arsc.BinaryResourceValue;
import com.google.devrel.gmscore.tools.apk.arsc.XmlAttribute;
import com.google.devrel.gmscore.tools.apk.arsc.XmlStartElementChunk;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Simple representation of an xml code (name, attributes and child nodes) used when
 * decoding an xml document in binary resource format.
 */
public class XmlNode {

  private final String myName;
  private final List<XmlNode> myChilds = new ArrayList<>();
  private final Map<String, String> myAttributes = new HashMap<>();

  public XmlNode() {
    myName = "";
  }

  public XmlNode(XmlStartElementChunk chunk, String chunkName) {
    myName = chunkName;
    for (XmlAttribute attribute : chunk.getAttributes()) {
      String name = attribute.name();
      String value;
      BinaryResourceValue typeValue = attribute.typedValue();
      if (typeValue.type() == BinaryResourceValue.Type.INT_BOOLEAN) {
         value = typeValue.data() == 0 ? "false" : "true";
      } else{
        value = attribute.rawValue();
      }
      myAttributes.put(name, value);
    }
  }

  @NotNull
  public String name() {
    return myName;
  }

  @NotNull
  public List<XmlNode> childs() {
    return myChilds;
  }

  @NotNull
  public Map<String, String> attributes() {
    return myAttributes;
  }
}
