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
package com.android.tools.idea.resourceExplorer.sketchImporter.structure.deserializers;

import com.android.tools.idea.resourceExplorer.sketchImporter.structure.*;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;

public class SketchLayerDeserializer implements JsonDeserializer<SketchLayer> {
  public static final String SHAPE_PATH_CLASS_TYPE = "shapePath";
  public static final String RECTANGLE_CLASS_TYPE = "rectangle";
  public static final String OVAL_CLASS_TYPE = "oval";
  public static final String STAR_CLASS_TYPE = "star";
  public static final String POLYGON_CLASS_TYPE = "polygon";
  public static final String SHAPE_GROUP_CLASS_TYPE = "shapeGroup";
  public static final String PAGE_CLASS_TYPE = "page";
  public static final String ARTBOARD_CLASS_TYPE = "artboard";
  public static final String SLICE_CLASS_TYPE = "slice";
  public static final String SYMBOL_MASTER_CLASS_TYPE = "symbolMaster";
  public static final String GROUP_CLASS_TYPE = "group";

  @Override
  public SketchLayer deserialize(@NotNull JsonElement json,
                                 @NotNull Type typeOfT,
                                 @NotNull JsonDeserializationContext context) {

    final JsonObject jsonObject = json.getAsJsonObject();

    final String classType = jsonObject.get("_class").getAsString();

    switch (classType) {
      case ARTBOARD_CLASS_TYPE:
        return context.deserialize(json, SketchArtboard.class);
      case PAGE_CLASS_TYPE:
      case GROUP_CLASS_TYPE:
        return context.deserialize(json, SketchPage.class);
      case SHAPE_GROUP_CLASS_TYPE:
        return context.deserialize(json, SketchShapeGroup.class);
      case SHAPE_PATH_CLASS_TYPE:
      case RECTANGLE_CLASS_TYPE:
      case OVAL_CLASS_TYPE:
      case STAR_CLASS_TYPE:
      case POLYGON_CLASS_TYPE:
        return context.deserialize(json, SketchShapePath.class);
      case SLICE_CLASS_TYPE:
        return context.deserialize(json, SketchSlice.class);
      case SYMBOL_MASTER_CLASS_TYPE:
        return context.deserialize(json, SketchSymbolMaster.class);
      default:
        Logger.getInstance(SketchLayerDeserializer.class).warn("Class not found!");
        return null;
    }
  }
}
