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
package com.android.tools.idea.resourceExplorer.sketchImporter.presenter;

import com.android.tools.idea.resourceExplorer.sketchImporter.model.SketchFile;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchPage;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchPoint2D;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.deserializers.ColorDeserializer;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.deserializers.PointDeserializer;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.deserializers.SketchLayerDeserializer;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.interfaces.SketchLayer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.intellij.openapi.diagnostic.Logger;
import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.*;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class SketchParser {
  private static final Logger LOG = Logger.getInstance(SketchParser.class);

  /**
   * Read data from the .sketch file (which is actually a zip archive) and turn it into an instance of {@code SketchFile}.
   *
   * @param path filepath to the .sketch file
   * @return {@link SketchFile} or {@code null} if the file could not be processed
   */
  @Nullable
  public static SketchFile read(@NotNull String path) {
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
              SketchPage page = parsePage(zip.getInputStream(entry));
              if (page != null) {
                sketchFile.addPage(page);
              }
          }
        }
      }

      return sketchFile;
    }
    catch (Exception e) {
      LOG.warn("Failed to read from sketch file: " + path + ".", e);
    }

    return null;
  }

  /**
   * Read page data (represented as JSON) from an input stream.
   *
   * @return a {@link SketchPage} or {@code null} if the parsing failed
   */
  @Nullable
  private static SketchPage parsePage(@NotNull InputStream in) {
    try (Reader reader = new BufferedReader(new InputStreamReader(in))) {
      return getPage(reader);
    }
    catch (Exception e) {
      LOG.warn("Could not read page from input stream.", e);
    }

    return null;
  }

  /**
   * Read page data (represented as JSON) from filepath.
   *
   * @return a {@link SketchPage} or {@code null} if the parsing failed
   */
  @Nullable
  public static SketchPage parsePage(@NotNull String path) {
    try (Reader reader = new BufferedReader(new FileReader(path))) {
      return getPage(reader);
    }
    catch (Exception e) {
      LOG.warn("Could not read page from " + path + ".", e);
    }

    return null;
  }

  /**
   * Parse page data (represented as JSON) from a reader
   *
   * @return a {@link SketchPage} or {@code null} if the {@code json} is at EOF
   * @throws JsonSyntaxException if there was a problem reading from the Reader
   * @throws JsonIOException     if json is not a valid representation for an object of type
   */
  @Nullable
  private static SketchPage getPage(@NotNull Reader reader) throws JsonSyntaxException, JsonIOException {
    // Tells GSON which deserializers to use for special classes and parses the file
    Gson gson = new GsonBuilder()
      .registerTypeAdapter(SketchLayer.class, new SketchLayerDeserializer())
      .registerTypeAdapter(Color.class, new ColorDeserializer())
      .registerTypeAdapter(SketchPoint2D.class, new PointDeserializer())
      .create();
    return gson.fromJson(reader, SketchPage.class);
  }
}
