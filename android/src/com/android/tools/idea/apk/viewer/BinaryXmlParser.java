/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.apk.viewer;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.api.XmlBuilder;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.devrel.gmscore.tools.apk.arsc.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class BinaryXmlParser {
  @NotNull
  public static byte[] decodeXml(@NotNull String fileName, @NotNull byte[] bytes) {
    ResourceFile file = new ResourceFile(bytes);
    List<Chunk> chunks = file.getChunks();
    if (chunks.size() != 1) {
      Logger.getInstance(BinaryXmlParser.class).warn("Expected 1, but got " + chunks.size() + " chunks while parsing " + fileName);
      return bytes;
    }

    if (!(chunks.get(0) instanceof XmlChunk)) {
      Logger.getInstance(BinaryXmlParser.class)
        .warn("First chunk in " + fileName + " is not an XmlChunk: " + chunks.get(0).getClass().getCanonicalName());
      return bytes;
    }

    XmlPrinter printer = new XmlPrinter();
    XmlChunk xmlChunk = (XmlChunk)chunks.get(0);

    visitChunks(xmlChunk.getChunks(), printer);

    @Language("XML")
    String reconstructedXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" + printer.getReconstructedXml();
    return reconstructedXml.getBytes(Charsets.UTF_8);
  }

  private static void visitChunks(@NotNull Map<Integer, Chunk> chunks, @NotNull XmlChunkHandler handler) {
    // sort the chunks by their offset in the file in order to traverse them in the right order
    List<Chunk> contentChunks = sortByOffset(chunks);

    for (Chunk chunk : contentChunks) {
      if (chunk instanceof StringPoolChunk) {
        handler.stringPool((StringPoolChunk)chunk);
      }
      else if (chunk instanceof XmlResourceMapChunk) {
        handler.xmlResourceMap((XmlResourceMapChunk)chunk);
      }
      else if (chunk instanceof XmlNamespaceStartChunk) {
        handler.startNamespace((XmlNamespaceStartChunk)chunk);
      }
      else if (chunk instanceof XmlNamespaceEndChunk) {
        handler.endNamespace((XmlNamespaceEndChunk)chunk);
      }
      else if (chunk instanceof XmlStartElementChunk) {
        handler.startElement((XmlStartElementChunk)chunk);
      }
      else if (chunk instanceof XmlEndElementChunk) {
        handler.endElement((XmlEndElementChunk)chunk);
      }
      else {
        Logger.getInstance(BinaryXmlParser.class).warn("XmlNode of type " + chunk.getClass().getCanonicalName() + " not handled.");
      }
    }
  }

  @NotNull
  private static List<Chunk> sortByOffset(Map<Integer, Chunk> contentChunks) {
    List<Integer> offsets = Lists.newArrayList(contentChunks.keySet());
    Collections.sort(offsets);
    List<Chunk> chunks = new ArrayList<>(offsets.size());
    for (Integer offset : offsets) {
      chunks.add(contentChunks.get(offset));
    }

    return chunks;
  }

  private interface XmlChunkHandler {
    default void stringPool(@NotNull StringPoolChunk chunk) {}
    default void xmlResourceMap(@NotNull XmlResourceMapChunk chunk) {}

    default void startNamespace(@NotNull XmlNamespaceStartChunk chunk) {}
    default void endNamespace(@NotNull XmlNamespaceEndChunk chunk) {}

    default void startElement(@NotNull XmlStartElementChunk chunk) {}
    default void endElement(@NotNull XmlEndElementChunk chunk) {}
  }

  private static class XmlPrinter implements XmlChunkHandler {
    private final XmlBuilder myBuilder;
    private Map<String, String> myNamespaces = new HashMap<>();
    private boolean myNamespacesAdded;
    private StringPoolChunk myStringPool;

    public XmlPrinter() {
      myBuilder = new XmlBuilder();
    }

    @Override
    public void stringPool(@NotNull StringPoolChunk chunk) {
      myStringPool = chunk;
    }

    @Override
    public void startNamespace(@NotNull XmlNamespaceStartChunk chunk) {
      // collect all the namespaces in use, and print them out later when we the first tag is seen
      myNamespaces.put(chunk.getUri(), chunk.getPrefix());
    }

    @Override
    public void startElement(@NotNull XmlStartElementChunk chunk) {
      myBuilder.startTag(chunk.getName());

      // if this is the first tag, also print out the namespaces
      if (!myNamespacesAdded && !myNamespaces.isEmpty()) {
        myNamespacesAdded = true;
        for (Map.Entry<String, String> entry : myNamespaces.entrySet()) {
          myBuilder.attribute(SdkConstants.XMLNS, entry.getValue(), entry.getKey());
        }
      }

      for (XmlAttribute xmlAttribute : chunk.getAttributes()) {
        String prefix = StringUtil.notNullize(myNamespaces.get(xmlAttribute.namespace()));
        myBuilder.attribute(prefix, xmlAttribute.name(), getValue(xmlAttribute));
      }
    }

    @Override
    public void endElement(@NotNull XmlEndElementChunk chunk) {
      myBuilder.endTag(chunk.getName());
    }

    @NotNull
    public String getReconstructedXml() {
      return myBuilder.toString();
    }

    @NotNull
    private String getValue(@NotNull XmlAttribute attribute) {
      String rawValue = attribute.rawValue();
      if (!StringUtil.isEmpty(rawValue)) {
        return rawValue;
      }

      ResourceValue resValue = attribute.typedValue();
      return formatValue(resValue, myStringPool);
    }
  }

  public static String formatValue(@NotNull ResourceValue resValue, @Nullable StringPoolChunk stringPool) {
    int data = resValue.data();

    switch (resValue.type()) {
      case NULL:
        return "null";
      case DYNAMIC_REFERENCE:
        return String.format(Locale.US, "@dref/0x%1$08x", data);
      case REFERENCE:
        return String.format(Locale.US, "@ref/0x%1$08x", data);
      case ATTRIBUTE:
        return String.format(Locale.US, "@attr/0x%1$x", data);
      case STRING:
        return stringPool != null && stringPool.getStringCount() < data
               ? stringPool.getString(data) : String.format(Locale.US, "@string/0x%1$x", data);
      case DIMENSION:
        return String.format(Locale.US, "dimension(%1$d)", data);
      case FRACTION:
        return String.format(Locale.US, "fraction(%1$d)", data);
      case FLOAT:
        return String.format(Locale.US, "%f", (float)data);
      case INT_DEC:
        return Integer.toString(data);
      case INT_HEX:
        return "0x" + Integer.toHexString(data);
      case INT_BOOLEAN:
        return Boolean.toString(data != 0);
      case INT_COLOR_ARGB8:
        return String.format("argb8(0x%x)", data);
      case INT_COLOR_RGB8:
        return String.format("rgb8(0x%x)", data);
      case INT_COLOR_ARGB4:
        return String.format("argb4(0x%x)", data);
      case INT_COLOR_RGB4:
        return String.format("rgb4(0x%x)", data);
    }

    return String.format("@res/0x%x", data);
  }
}
