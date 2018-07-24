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

import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchArtboard;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchPage;
import org.jetbrains.android.AndroidTestBase;

import java.util.List;

public class Sandbox {

  public static void main(String[] args) {

    readJSON("combinationsSingleArtboard.json");
  }

  private static void readJSON(String jsonTestFile) {
    SketchPage sketchPage = SketchParser.open(AndroidTestBase.getTestDataPath() + "/sketch/" + jsonTestFile);

    List<SketchArtboard> artboards = sketchPage.getArtboards();

    for (int i = 0; i < artboards.size(); i++) {
      System.out.println("Artboard" + i);
      List<String> artboardPaths = artboards.get(i).getPaths();
      for (int j = 0; j < artboardPaths.size(); j++) {
        System.out.println(artboardPaths.get(j));
      }
    }
  }
}
