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
package com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.deserializers;

import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.document.SketchAssetCollection;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.document.SketchDocument;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.document.SketchForeignStyle;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.document.SketchForeignSymbol;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.document.SketchForeignTextStyle;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.document.SketchSharedStyle;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.document.SketchSharedSymbol;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.lang.reflect.Type;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SketchDocumentDeserializer implements JsonDeserializer<SketchDocument> {
  private static final String ASSETS = "assets";
  private static final String COLOR_SPACE = "colorSpace";
  private static final String CURRENT_PAGE_INDEX = "currentPageIndex";
  private static final String EXT_LAYER_STYLES = "foreignLayerStyles";
  private static final String EXT_SYMBOLS = "foreignSymbols";
  private static final String EXT_TEXT_STYLES = "foreignTextStyles";
  private static final String LAYER_STYLES = "layerStyles";
  private static final String LAYER_SYMBOLS = "layerSymbols";
  private static final String LAYER_TEXT_STYLES = "layerTextStyles";
  private static final String OBJECTS = "objects";

  private static final short DEFAULT_COLOR_SPACE = 0;
  private static final int DEFAULT_CURRENT_PAGE_INDEX = 0;

  @Override
  @Nullable
  public SketchDocument deserialize(@NotNull JsonElement json,
                                    @NotNull Type typeOfT,
                                    @NotNull JsonDeserializationContext context) {

    final JsonObject jsonObject = json.getAsJsonObject();

    final SketchAssetCollection assets = context.deserialize(jsonObject.get(ASSETS), SketchAssetCollection.class);
    JsonElement colorSpaceElement = jsonObject.get(COLOR_SPACE);
    final short colorSpace = colorSpaceElement != null ? colorSpaceElement.getAsShort() : DEFAULT_COLOR_SPACE;
    JsonElement currentPageIndexElement = jsonObject.get(CURRENT_PAGE_INDEX);
    final int currentPageIndex = currentPageIndexElement != null ? currentPageIndexElement.getAsInt() : DEFAULT_CURRENT_PAGE_INDEX;

    final SketchForeignStyle[] foreignLayerStyles = context.deserialize(jsonObject.get(EXT_LAYER_STYLES), SketchForeignStyle[].class);
    final SketchForeignSymbol[] foreignSymbols = context.deserialize(jsonObject.get(EXT_SYMBOLS), SketchForeignSymbol[].class);
    final SketchForeignTextStyle[] foreignTextStyles = context.deserialize(jsonObject.get(EXT_TEXT_STYLES), SketchForeignTextStyle[].class);

    final SketchSharedStyle[] layerStyles = getObjectsFrom(jsonObject, SketchSharedStyle[].class, context, LAYER_STYLES);
    final SketchSharedSymbol[] layerSymbols = getObjectsFrom(jsonObject, SketchSharedSymbol[].class, context, LAYER_SYMBOLS);
    final SketchSharedStyle[] layerTextStyles = getObjectsFrom(jsonObject, SketchSharedStyle[].class, context, LAYER_TEXT_STYLES);

    return new SketchDocument(assets, colorSpace, currentPageIndex, foreignLayerStyles, foreignSymbols, foreignTextStyles, layerStyles,
                              layerSymbols, layerTextStyles);
  }

  @NotNull
  private static <T> T getObjectsFrom(@NotNull JsonObject jsonObject,
                                      @NotNull Type typeOfT,
                                      @NotNull JsonDeserializationContext context,
                                      @NotNull String memberName) {
    return context.deserialize(jsonObject.get(memberName).getAsJsonObject().get(OBJECTS), typeOfT);
  }
}
