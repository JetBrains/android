/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.sketchImporter;

import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchFile;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchLayer;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchPage;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchPoint2D;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.deserializers.ColorDeserializer;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.deserializers.PointDeserializer;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.deserializers.SketchLayerDeserializer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.diagnostic.Logger;
import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.Point2D;
import java.io.*;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class SketchParser {
  @Nullable
  public static SketchFile unzip(String path) {
    try (ZipFile zip = new ZipFile(path)) {

      SketchFile sketchFile = new SketchFile();

      for (Enumeration e = zip.entries(); e.hasMoreElements(); ) {
        ZipEntry entry = (ZipEntry)e.nextElement();

        String entryName = entry.getName();

        if (FilenameUtils.getExtension(entryName).equals("json")) {
          switch (entryName) {
            case "document.json":
              // TODO
              break;
            case "meta.json":
              // TODO
              break;
            case "user.json":
              // TODO
              break;
            default:
              sketchFile.addPage(parsePage(zip.getInputStream(entry)));
          }
        }
      }

      return sketchFile;
    }
    catch (IOException e) {
      Logger.getInstance(SketchParser.class).warn("Failed to read from sketch file!");
    }

    return null;
  }

  @Nullable
  public static SketchPage parsePage(@NotNull InputStream in) {
    try (Reader reader = new BufferedReader(new InputStreamReader(in))) {
      return getPage(reader);
    }
    catch (IOException e) {
      Logger.getInstance(SketchParser.class).warn("Could not read page.", e);
    }

    return null;
  }

  @Nullable
  public static SketchPage parsePage(@NotNull String path) {
    try (Reader reader = new FileReader(path)) {
      return getPage(reader);
    }
    catch (IOException e) {
      Logger.getInstance(SketchParser.class).warn("Page " + path + " not found.", e);
    }

    return null;
  }

  @Nullable
  private static SketchPage getPage(Reader reader) {
    Gson gson = new GsonBuilder()
      .registerTypeAdapter(SketchLayer.class, new SketchLayerDeserializer())
      .registerTypeAdapter(Color.class, new ColorDeserializer())
      .registerTypeAdapter(Point2D.Double.class, new PointDeserializer())
      .registerTypeAdapter(SketchPoint2D.class, new PointDeserializer())
      .create();
    return gson.fromJson(reader, SketchPage.class);
  }
}
