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
package com.android.tools.idea.resourceExplorer.sketchImporter.parser;

import com.android.tools.idea.resourceExplorer.sketchImporter.parser.deserializers.ColorDeserializer;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.deserializers.PointDeserializer;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.deserializers.SketchDocumentDeserializer;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.deserializers.SketchLayerDeserializer;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.document.SketchDocument;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.interfaces.SketchLayer;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchPage;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchPoint2D;
import com.android.tools.idea.resourceExplorer.sketchImporter.ui.SketchFile;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.intellij.openapi.diagnostic.Logger;
import java.awt.Color;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Parses sketch files.
 */
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
              SketchDocument document = parseDocument(zip.getInputStream(entry));
              if (document != null) {
                sketchFile.setDocument(document);
              }
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
  public static SketchPage parsePage(@NotNull InputStream in) {
    try (Reader reader = new BufferedReader(new InputStreamReader(in))) {
      return buildGson().fromJson(reader, SketchPage.class);
    }
    catch (Exception e) {
      LOG.warn("Could not read page from input stream.", e);
    }

    return null;
  }

  /**
   * Read document (json) from an input stream.
   *
   * @return a {@link SketchPage} or {@code null} if the parsing failed
   */
  @Nullable
  public static SketchDocument parseDocument(@NotNull InputStream in) {
    try (Reader reader = new BufferedReader(new InputStreamReader(in))) {
      return buildGson().fromJson(reader, SketchDocument.class);
    }
    catch (Exception e) {
      LOG.warn("Could not read document from input stream.", e);
    }

    return null;
  }

  /**
   * Parse page data (represented as JSON) from a reader
   *
   * @return a {@link Gson} or {@code null} if the {@code json} is at EOF
   * @throws JsonSyntaxException if there was a problem reading from the Reader
   * @throws JsonIOException     if json is not a valid representation for an object of type
   */
  @NotNull
  private static Gson buildGson() throws JsonSyntaxException, JsonIOException {
    // Tells GSON which deserializers to use for special classes and parses the file
    return new GsonBuilder()
      .registerTypeAdapter(SketchDocument.class, new SketchDocumentDeserializer())
      .registerTypeAdapter(SketchLayer.class, new SketchLayerDeserializer())
      .registerTypeAdapter(Color.class, new ColorDeserializer())
      .registerTypeAdapter(SketchPoint2D.class, new PointDeserializer())
      .create();
  }
}
