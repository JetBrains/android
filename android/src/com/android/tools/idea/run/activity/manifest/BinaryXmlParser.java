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

import com.google.devrel.gmscore.tools.apk.arsc.BinaryResourceFile;
import com.google.devrel.gmscore.tools.apk.arsc.Chunk;
import com.google.devrel.gmscore.tools.apk.arsc.XmlChunk;
import com.google.devrel.gmscore.tools.apk.arsc.XmlEndElementChunk;
import com.google.devrel.gmscore.tools.apk.arsc.XmlStartElementChunk;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Stack;
import org.jetbrains.annotations.NotNull;

public class BinaryXmlParser {

  @NotNull
  public static XmlNode parse(@NotNull InputStream inputStream) throws IOException {

    BinaryResourceFile file = BinaryResourceFile.fromInputStream(inputStream);
    List<Chunk> chunks = file.getChunks();
    Stack<XmlNode> nodes = new Stack<>();

    if (chunks.isEmpty()) {
      throw new IllegalArgumentException("Invalid Binary XML, no chunks found");
    }

    if (!(chunks.get(0) instanceof XmlChunk)) {
      throw new IllegalArgumentException("Invalid Binary XML, chunk[0] != XmlChunk");
    }

    XmlChunk xmlChunk = (XmlChunk)chunks.get(0);

    XmlNode node = new XmlNode();
    nodes.push(node);

    for (Chunk chunk : xmlChunk.getChunks().values()) {
      if (chunk instanceof XmlStartElementChunk) {
        XmlStartElementChunk c = (XmlStartElementChunk)chunk;
        XmlNode child = new XmlNode(c, c.getName());
        node.childs().add(child);
        nodes.push(node);
        node = child;
      } else  if (chunk instanceof XmlEndElementChunk) {
        node = nodes.pop();
      }
    }

    if (node.childs().isEmpty()) {
      throw new IllegalStateException("Cannot parse binary XML without root node");
    }
    return node.childs().get(0);
  }
}

