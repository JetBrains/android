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

import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces.SketchLayer;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchArtboard;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchBitmap;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchPage;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchShapeGroup;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchShapePath;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchSlice;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchSymbolInstance;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchSymbolMaster;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchText;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.interfaces.SketchLayer;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchArtboard;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchBitmap;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchPage;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchShapeGroup;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchShapePath;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchSlice;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchSymbolInstance;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchSymbolMaster;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchText;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.diagnostic.Logger;
import java.lang.reflect.Type;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Since abstract classes (such as {@link SketchLayer} cannot be instantiated, GSON needs to know how to handle fields of type
 * {@link SketchLayer}.
 * This is a {@link JsonDeserializer} that uses the "_class" field in the JSON file to determine which type of layer should be instantiated.
 */
public class SketchLayerDeserializer implements JsonDeserializer<SketchLayer> {
  public static final String ARTBOARD_CLASS_TYPE = "artboard";
  public static final String BITMAP_CLASS_TYPE = "bitmap";
  public static final String GROUP_CLASS_TYPE = "group";
  public static final String OVAL_CLASS_TYPE = "oval";
  public static final String PAGE_CLASS_TYPE = "page";
  public static final String POLYGON_CLASS_TYPE = "polygon";
  public static final String RECTANGLE_CLASS_TYPE = "rectangle";
  public static final String SHAPE_GROUP_CLASS_TYPE = "shapeGroup";
  public static final String SHAPE_PATH_CLASS_TYPE = "shapePath";
  public static final String SLICE_CLASS_TYPE = "slice";
  public static final String STAR_CLASS_TYPE = "star";
  public static final String SYMBOL_INSTANCE_CLASS_TYPE = "symbolInstance";
  public static final String SYMBOL_MASTER_CLASS_TYPE = "symbolMaster";
  public static final String TEXT_CLASS_TYPE = "text";
  public static final String TRIANGLE_CLASS_TYPE = "triangle";

  @Override
  @Nullable
  public SketchLayer deserialize(@NotNull JsonElement json,
                                 @NotNull Type typeOfT,
                                 @NotNull JsonDeserializationContext context) {

    final JsonObject jsonObject = json.getAsJsonObject();

    final String classType = jsonObject.get("_class").getAsString();

    switch (classType) {
      case ARTBOARD_CLASS_TYPE:
        return context.deserialize(json, SketchArtboard.class);
      case BITMAP_CLASS_TYPE:
        return context.deserialize(json, SketchBitmap.class);
      case GROUP_CLASS_TYPE:
      case PAGE_CLASS_TYPE:
        return context.deserialize(json, SketchPage.class);
      case OVAL_CLASS_TYPE:
      case POLYGON_CLASS_TYPE:
      case RECTANGLE_CLASS_TYPE:
      case SHAPE_PATH_CLASS_TYPE:
      case STAR_CLASS_TYPE:
      case TRIANGLE_CLASS_TYPE:
        return context.deserialize(json, SketchShapePath.class);
      case SHAPE_GROUP_CLASS_TYPE:
        return context.deserialize(json, SketchShapeGroup.class);
      case SLICE_CLASS_TYPE:
        return context.deserialize(json, SketchSlice.class);
      case SYMBOL_INSTANCE_CLASS_TYPE:
        return context.deserialize(json, SketchSymbolInstance.class);
      case SYMBOL_MASTER_CLASS_TYPE:
        return context.deserialize(json, SketchSymbolMaster.class);
      case TEXT_CLASS_TYPE:
        return context.deserialize(json, SketchText.class);
      default:
        Logger.getInstance(SketchLayerDeserializer.class).warn("Class " + classType + " not found.");
        return null;
    }
  }
}