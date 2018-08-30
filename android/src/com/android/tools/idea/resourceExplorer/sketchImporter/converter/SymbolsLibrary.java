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
package com.android.tools.idea.resourceExplorer.sketchImporter.converter;

import static com.android.tools.idea.resourceExplorer.sketchImporter.converter.model_converters.SketchToShapeConverter.DEFAULT_OPACITY;
import static com.android.tools.idea.resourceExplorer.sketchImporter.converter.model_converters.SketchToShapeConverter.createShapeModelsFromLayerable;

import com.android.tools.idea.resourceExplorer.sketchImporter.converter.models.ShapeModel;
import com.android.tools.idea.resourceExplorer.sketchImporter.converter.models.SymbolModel;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchSymbolMaster;
import com.google.common.collect.ImmutableList;
import com.intellij.util.containers.hash.HashMap;
import java.awt.geom.Point2D;
import org.jetbrains.annotations.NotNull;

/**
 * Class that holds a mapping between a {@link SketchSymbolMaster} and its symbolId
 * <p>
 * Contains methods to add symbols to the map and compute the corresponding
 * {@link SymbolModel}
 */
public class SymbolsLibrary {
  HashMap<String, SketchSymbolMaster> mySymbolMasterHashMap = new HashMap<>();

  public void addSymbols(@NotNull ImmutableList<SketchSymbolMaster> symbolMasters) {
    for (SketchSymbolMaster symbolMaster : symbolMasters) {
      mySymbolMasterHashMap.put(symbolMaster.getSymbolId(), symbolMaster);
    }
  }

  public SymbolModel getSymbolModel(@NotNull String symbolId) {
    SketchSymbolMaster symbolMaster = mySymbolMasterHashMap.get(symbolId);
    ImmutableList<ShapeModel> shapeModels = createShapeModelsFromLayerable(symbolMaster, new Point2D.Double(), DEFAULT_OPACITY, this);
    return new SymbolModel(shapeModels, symbolMaster);
  }

  public boolean isEmpty() {
    return mySymbolMasterHashMap.isEmpty();
  }
}