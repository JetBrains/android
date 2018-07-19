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
package com.android.tools.idea.resourceExplorer.sketchImporter.structure;

import com.android.utils.Pair;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SketchParser {
  public static @Nullable
  SketchPage open(@NotNull String path) {

    try (Reader reader = new FileReader(path)) {
      Gson gson = new GsonBuilder().create();
      return gson.fromJson(reader, SketchPage.class);
    }
    catch (IOException e) {
      Logger.getGlobal().log(Level.WARNING, "Sketch file not found.", e);
    }

    return null;
  }

  /**
   * @param positionString e.g. '{0.5, 0.67135115527602085}'
   * @return pair of (floating-point) coords
   */
  public static
  Pair<Double, Double> getPosition(@NotNull String positionString) {
    String[] parts = positionString.split("[{}, ]");

    return Pair.of(Double.parseDouble(parts[1]), Double.parseDouble(parts[3]));
  }
}
