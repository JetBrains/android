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
package com.android.tools.idea.resourceExplorer.sketchImporter.ui;

import com.android.tools.idea.resourceExplorer.sketchImporter.converter.SketchLibrary;
import com.android.tools.idea.resourceExplorer.sketchImporter.converter.SymbolsLibrary;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.document.SketchDocument;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.document.SketchForeignStyle;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.document.SketchForeignSymbol;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.document.SketchSharedStyle;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.document.SketchSharedSymbol;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.interfaces.SketchLayer;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.interfaces.SketchLayerable;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.interfaces.SketchSymbol;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.meta.SketchMeta;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchPage;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchStyle;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchSymbolMaster;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  private static ImmutableList<SketchSymbolMaster> getAllSymbolMasters(@NotNull SketchPage page) {
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

  /**
   * Recursively search through all pages in the file for the layer with the corresponding {@code objectId}.
   *
   * @return the found layer or {@code null} if no layer was found
   */
  @Nullable
  public SketchLayer findLayer(@NotNull String objectId) {
    for (SketchPage page : myPages) {
      SketchLayer foundLayer = findLayer(objectId, page);
      if (foundLayer != null) {
        return foundLayer;
      }
    }

    return null;
  }

  /**
   * Recursively search through all pages in the file for the symbol with the corresponding {@code symbolId}.
   *
   * @return the found symbol or {@code null} if no layer was found
   */
  @Nullable
  public SketchSymbol findSymbol(@NotNull String symbolId) {
    for (SketchPage page : myPages) {
      SketchSymbol foundSymbol = findSymbol(symbolId, page);
      if (foundSymbol != null) {
        return foundSymbol;
      }
    }

    return null;
  }

  @NotNull
  public SketchLibrary getLibrary() {
    return myLibrary;
  }

  /**
   * Recursively search for the layer with the corresponding {@code objectId} starting at {@code currentLayer}.
   *
   * @return the found layer or {@code null} if no layer was found
   */
  @Nullable
  private static SketchLayer findLayer(@NotNull String objectId, @NotNull SketchLayer currentLayer) {
    if (currentLayer.getObjectId().equals(objectId)) {
      return currentLayer;
    }

    if (currentLayer instanceof SketchLayerable) {
      for (SketchLayer layer : ((SketchLayerable)currentLayer).getLayers()) {
        SketchLayer foundLayer = findLayer(objectId, layer);
        if (foundLayer != null) {
          return foundLayer;
        }
      }
    }

    return null;
  }

  /**
   * Recursively search for the symbol with the corresponding {@code symbolId} starting at {@code currentLayer}.
   *
   * @return the found symbol or {@code null} if no layer was found
   */
  @Nullable
  private static SketchSymbol findSymbol(@NotNull String symbolId, @NotNull SketchLayer currentLayer) {
    if (currentLayer instanceof SketchSymbol) {
      if (((SketchSymbol)currentLayer).getSymbolId().equals(symbolId)) {
        return (SketchSymbol)currentLayer;
      }
    }

    if (currentLayer instanceof SketchLayerable) {
      for (SketchLayer layer : ((SketchLayerable)currentLayer).getLayers()) {
        SketchSymbol foundSymbol = findSymbol(symbolId, layer);
        if (foundSymbol != null) {
          return foundSymbol;
        }
      }
    }

    return null;
  }
}
