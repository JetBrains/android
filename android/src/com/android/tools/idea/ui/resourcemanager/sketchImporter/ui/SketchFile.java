/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.sketchImporter.ui;

import static com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.deserializers.SketchLayerDeserializer.ARTBOARD_CLASS_TYPE;

import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.SketchLibrary;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.document.SketchDocument;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.document.SketchForeignStyle;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.document.SketchForeignSymbol;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.document.SketchSharedStyle;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.document.SketchSharedSymbol;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces.SketchLayer;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces.SketchLayerable;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.meta.SketchMeta;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchArtboard;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchPage;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchStyle;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchSymbolMaster;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.SketchLibrary;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces.SketchLayer;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces.SketchLayerable;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.meta.SketchMeta;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchArtboard;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchPage;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchStyle;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchSymbolMaster;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Class to hold the contents of a .sketch file, which is actually a zip archive with the following structure as per the
 * <a href="https://sketchplugins.com/d/87-new-file-format-in-sketch-43">official announcement</a>:
 * <ul>
 * <li><b>"images" folder</b> - Contains all the bitmaps that are used in the document, at their original size.</li>
 * <li><b>"pages" folder</b> - Contains a JSON file per page of the document. Each file describes the contents of a page.</li>
 * <li><b>"previews" folder</b> - Contains a preview image of the last page edited by the user.</li>
 * <li><b>"document.json"</b> - Contains common data for all pages of a document, like shared styles, and a link to the JSON files in the pages folder.</li>
 * <li><b>"meta.json"</b> - Contains metadata about the document itself: a list of pages and artboards, fonts used etc.</li>
 * <li><b>"user.json"</b> - Contains user metadata for the file, like the canvas viewport & zoom level for each page, UI metadata for the app etc.</li>
 * </ul>
 * <p>
 * With respect to the MVP pattern developed for the Sketch Importer UI, this class is part of the model that forms the backbone of the
 * information presented in the interface.
 */
public class SketchFile {
  private SketchDocument myDocument;
  private SketchMeta myMeta;
  private List<SketchPage> myPages = new ArrayList<>();
  private SketchLibrary myLibrary = new SketchLibrary();

  public void addPage(@NotNull SketchPage page) {
    myPages.add(page);
    myLibrary.addSymbols(getAllSymbolMasters(page));
  }

  @NotNull
  private static ImmutableList<SketchStyle> getAllStyles(@NotNull SketchDocument document) {
    ImmutableList.Builder<SketchStyle> styles = new ImmutableList.Builder<>();
    SketchForeignStyle[] foreignStyles = document.getForeignLayerStyles();
    if (foreignStyles != null) {
      for (SketchForeignStyle foreignStyle : foreignStyles) {
        // TODO after implementing foreign styles
      }
    }
    SketchSharedStyle[] sharedStyles = document.getLayerStyles();
    if (sharedStyles != null) {
      for (SketchSharedStyle sharedStyle : sharedStyles) {
        styles.add(sharedStyle.getValue());
      }
    }
    return styles.build();
  }

  @NotNull
  private static ImmutableList<SketchSymbolMaster> getAllSymbolMasters(@NotNull SketchDocument document) {
    ImmutableList.Builder<SketchSymbolMaster> masters = new ImmutableList.Builder<>();
    SketchForeignSymbol[] foreignSymbols = document.getForeignSymbols();
    if (foreignSymbols != null) {
      for (SketchForeignSymbol foreignSymbol : foreignSymbols) {
        masters.add(foreignSymbol.getSymbolMaster());
      }
    }
    SketchSharedSymbol[] sharedSymbols = document.getLayerSymbols();
    if (sharedSymbols != null) {
      for (SketchSharedSymbol sharedSymbol : sharedSymbols) {
        // TODO after implementing shared symbols
      }
    }
    return masters.build();
  }


  @NotNull
  public static ImmutableList<SketchArtboard> getArtboards(@NotNull SketchPage page) {
    ImmutableList.Builder<SketchArtboard> artboards = new ImmutableList.Builder<>();

    for (SketchLayer layer : page.getLayers()) {
      if (layer.getClassType().equals(ARTBOARD_CLASS_TYPE)) {
        artboards.add((SketchArtboard)layer);
      }
    }

    return artboards.build();
  }

  @NotNull
  public static ImmutableList<SketchSymbolMaster> getAllSymbolMasters(@NotNull SketchPage page) {
    ImmutableList.Builder<SketchSymbolMaster> masters = new ImmutableList.Builder<>();
    for (SketchLayer layer : page.getLayers()) {
      masters.addAll(getSymbolMasters(layer));
    }

    return masters.build();
  }

  @NotNull
  private static ImmutableList<SketchSymbolMaster> getSymbolMasters(@NotNull SketchLayer layer) {
    ImmutableList.Builder<SketchSymbolMaster> masters = new ImmutableList.Builder<>();
    if (layer instanceof SketchLayerable) {
      if (layer instanceof SketchSymbolMaster) {
        masters.add((SketchSymbolMaster)layer);
      }
      for (SketchLayer subLayer : ((SketchLayerable)layer).getLayers()) {
        masters.addAll(getSymbolMasters(subLayer));
      }
    }

    return masters.build();
  }

  @NotNull
  public List<SketchPage> getPages() {
    return myPages;
  }

  @NotNull
  public SketchDocument getDocument() {
    return myDocument;
  }

  public void setDocument(@NotNull SketchDocument document) {
    myDocument = document;
    myLibrary.addSymbols(getAllSymbolMasters(document));
    myLibrary.addStyles(getAllStyles(document));
  }

  @NotNull
  public SketchMeta getMeta() {
    return myMeta;
  }

  public void setMeta(@NotNull SketchMeta meta) {
    myMeta = meta;
  }

  @NotNull
  public SketchLibrary getLibrary() {
    return myLibrary;
  }
}
