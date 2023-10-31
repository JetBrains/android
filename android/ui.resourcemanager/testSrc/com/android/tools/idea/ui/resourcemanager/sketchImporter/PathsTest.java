/*
 * Copyright (C) 2018 The Android parsePage Source Project
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
package com.android.tools.idea.ui.resourcemanager.sketchImporter;

import static com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.builders.SketchToStudioConverter.createDrawableAsset;
import static org.junit.Assert.assertEquals;

import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.SketchLibrary;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.ShapeModel;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchArtboard;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchPage;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.ui.SketchFile;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.android.AndroidTestBase;
import org.junit.Test;

public class PathsTest {

  @Test
  public void linePathTest() {
    SketchPage sketchPage = SketchTestUtils.Companion.parsePage(AndroidTestBase.getTestDataPath() + "/sketch/" + "paths_linePath.json");

    List<String> artboardPaths = getFirstArtboardPaths(sketchPage);

    assertEquals("M5,12 L11,8 ", artboardPaths.get(0));
  }

  @Test
  public void curvePathTest() {
    SketchPage sketchPage = SketchTestUtils.Companion.parsePage(AndroidTestBase.getTestDataPath() + "/sketch/" + "paths_curvePath.json");

    List<String> artboardPaths = getFirstArtboardPaths(sketchPage);

    assertEquals("M4,10 C4,7 5,6 8,6 C11,6 12,7 12,10 ", artboardPaths.get(0));
  }

  @Test
  public void rectanglePathTest() {
    SketchPage sketchPage =
      SketchTestUtils.Companion.parsePage(AndroidTestBase.getTestDataPath() + "/sketch/" + "paths_rectanglePath.json");

    List<String> artboardPaths = getFirstArtboardPaths(sketchPage);

    assertEquals("M26,1 L103,1 L103,21 L26,21 C26,21 26,1 26,1 ", artboardPaths.get(0));
  }

  @Test

  public void roundRectanglePathTest() {
    SketchPage sketchPage =
      SketchTestUtils.Companion.parsePage(AndroidTestBase.getTestDataPath() + "/sketch/" + "paths_roundRectanglePath.json");

    List<String> artboardPaths = getFirstArtboardPaths(sketchPage);


    assertEquals("M30,20 L30,20 L90,20 Q110,20 110,40 L110,80 Q110,120 70,120 Q30,120 30,80 z",
                 artboardPaths.get(0));
  }

  @Test
  public void singleShapePathTest() {
    SketchPage sketchPage =
      SketchTestUtils.Companion.parsePage(AndroidTestBase.getTestDataPath() + "/sketch/" + "paths_singleShapePath.json");

    List<String> artboardPaths = getFirstArtboardPaths(sketchPage);

    assertEquals("M149,59 L201,304 L97,304 ", artboardPaths.get(0));
  }

  @Test
  public void shapeUnionTest() {
    SketchPage sketchPage = SketchTestUtils.Companion.parsePage(AndroidTestBase.getTestDataPath() + "/sketch/" + "paths_shapeUnion.json");

    List<String> artboardPaths = getFirstArtboardPaths(sketchPage);

    assertEquals(
      "M268.5,34 C227.23,34 194.76,66.3 194.5,107.5 L50,107.5 C50,107.5 50,325.5 50,325.5 L226.5,325.5 C226.5,325.67 226.5,325.83 226.5,326 C226.5,348.68 244.98,368 268.5,368 C291.18,368 310.5,348.68 310.5,326 C310.5,302.48 291.18,284 268.5,284 C268.33,284 268.17,284 268,284 L268,284 L268,182 L268,182 C268.17,182 268.33,182 268.5,182 C308.46,182 342.5,147.96 342.5,108 C342.5,66.56 308.46,34 268.5,34 z",
      artboardPaths.get(0));
  }

  @Test
  public void shapeSubtractionTest() {
    SketchPage sketchPage =
      SketchTestUtils.Companion.parsePage(AndroidTestBase.getTestDataPath() + "/sketch/" + "paths_shapeSubstraction.json");

    List<String> artboardPaths = getFirstArtboardPaths(sketchPage);

    assertEquals(
      "M50,107.5 C50,107.5 50,325.5 50,325.5 L226.5,325.5 C226.76,302.39 244.88,284.26 268,284 L268,284 L268,182 L268,182 C226.8,181.73 194.5,147.8 194.5,108 C194.5,107.83 194.5,107.67 194.5,107.5 z",

      artboardPaths.get(0));
  }

  @Test
  public void shapeDifferenceTest() {
    SketchPage sketchPage =
      SketchTestUtils.Companion.parsePage(AndroidTestBase.getTestDataPath() + "/sketch/" + "paths_shapeDifference.json");

    List<String> artboardPaths = getFirstArtboardPaths(sketchPage);

    assertEquals(
      "M268.5,34 C227.23,34 194.76,66.3 194.5,107.5 L268,107.5 L268,182 L268,182 C226.8,181.73 194.5,147.8 194.5,108 C194.5,107.83 194.5,107.67 194.5,107.5 L50,107.5 C50,107.5 50,325.5 50,325.5 L226.5,325.5 C226.76,302.39 244.88,284.26 268,284 L268,284 L268,325.5 L226.5,325.5 C226.5,325.67 226.5,325.83 226.5,326 C226.5,348.68 244.98,368 268.5,368 C291.18,368 310.5,348.68 310.5,326 C310.5,302.48 291.18,284 268.5,284 C268.33,284 268.17,284 268,284 L268,284 L268,182 L268,182 C268.17,182 268.33,182 268.5,182 C308.46,182 342.5,147.96 342.5,108 C342.5,66.56 308.46,34 268.5,34 z",
      artboardPaths.get(0));
  }

  @Test
  public void shapeIntersectTest() {
    SketchPage sketchPage =
      SketchTestUtils.Companion.parsePage(AndroidTestBase.getTestDataPath() + "/sketch/" + "paths_shapeIntersect.json");

    List<String> artboardPaths = getFirstArtboardPaths(sketchPage);

    assertEquals(
      "M194.5,107.5 C194.5,107.67 194.5,107.83 194.5,108 C194.5,147.8 226.8,181.73 268,182 L268,182 L268,107.5 z",
      artboardPaths.get(0));
  }

  @Test
  public void combinationsSingleArtboardTest() {
    SketchPage sketchPage =
      SketchTestUtils.Companion.parsePage(AndroidTestBase.getTestDataPath() + "/sketch/" + "paths_combinationsSingleArtboard.json");

    List<String> artboardPaths = getFirstArtboardPaths(sketchPage);

    assertEquals(
      "M71,180 C94.2,180 113,198.8 113,222 C113,245.2 94.2,264 71,264 C47.8,264 29,245.2 29,222 C29,198.8 47.8,180 71,180 zM287.67,225.22 C296.68,237.48 302,252.62 302,269 C302,283.72 297.7,297.44 290.29,308.97 L290.29,308.97 C267.89,308.07 250,289.62 250,267 C250,245.27 266.51,227.39 287.67,225.22 zM10,164 C10,164 10,382.47 10,382.47 L77,382.47 C77,382.32 77,382.16 77,382 C77,358.8 95.8,340 119,340 C142.2,340 161,358.8 161,382 C161,382.16 161,382.32 161,382.47 L228.47,382.47 L228.47,343 L228.47,343 C254.42,342.84 277.21,329.32 290.29,308.97 L290.29,308.97 C290.86,308.99 291.43,309 292,309 C315.2,309 334,290.2 334,267 C334,243.8 315.2,225 292,225 C290.54,225 289.09,225.07 287.67,225.22 L287.67,225.22 C274.28,207.01 252.77,195.15 228.47,195 L228.47,195 L228.47,164 z",
      artboardPaths.get(0));
    assertEquals(
      "M347,206 C370.2,206 389,187.2 389,164 C389,140.8 370.2,122 347,122 C323.8,122 305,140.8 305,164 C305,187.2 323.8,206 347,206 ",
      artboardPaths.get(1));
    assertEquals(
      "M263,106 C281.23,106 296,91.23 296,73 C296,54.77 281.23,40 263,40 C244.77,40 230,54.77 230,73 C230,91.23 244.77,106 263,106 ",
      artboardPaths.get(2));
    assertEquals(
      "M188.44,106 C169.42,106 154,120.67 154,138.76 C154,141.97 154.48,145.07 155.39,148 L181.28,148 Q189.28,148 189.28,140 L189.28,106.01 L189.28,106.01 C189,106 188.72,106 188.44,106 z",
      artboardPaths.get(3));
  }

  @Test
  public void combinationsMultipleArtboardsTest() {
    SketchPage sketchPage = SketchTestUtils.Companion
      .parsePage(AndroidTestBase.getTestDataPath() + "/sketch/" + "paths_combinationsMultipleArtboards.json");

    List<SketchArtboard> artboards = SketchFile.getArtboards(sketchPage);
    List<ShapeModel> firstArtboardShapes = createDrawableAsset(artboards.get(0), new SketchLibrary()).getShapeModels();

    assertEquals(
      "M71,180 C94.2,180 113,198.8 113,222 C113,245.2 94.2,264 71,264 C47.8,264 29,245.2 29,222 C29,198.8 47.8,180 71,180 zM287.67,225.22 C296.68,237.48 302,252.62 302,269 C302,283.72 297.7,297.44 290.29,308.97 L290.29,308.97 C267.89,308.07 250,289.62 250,267 C250,245.27 266.51,227.39 287.67,225.22 zM10,164 C10,164 10,382.47 10,382.47 L77,382.47 C77,382.32 77,382.16 77,382 C77,358.8 95.8,340 119,340 C142.2,340 161,358.8 161,382 C161,382.16 161,382.32 161,382.47 L228.47,382.47 L228.47,343 L228.47,343 C254.42,342.84 277.21,329.32 290.29,308.97 L290.29,308.97 C290.86,308.99 291.43,309 292,309 C315.2,309 334,290.2 334,267 C334,243.8 315.2,225 292,225 C290.54,225 289.09,225.07 287.67,225.22 L287.67,225.22 C274.28,207.01 252.77,195.15 228.47,195 L228.47,195 L228.47,164 z",
      firstArtboardShapes.get(0).getPathString());
    assertEquals(
      "M347,206 C370.2,206 389,187.2 389,164 C389,140.8 370.2,122 347,122 C323.8,122 305,140.8 305,164 C305,187.2 323.8,206 347,206 ",
      firstArtboardShapes.get(1).getPathString());
    assertEquals(
      "M263,106 C281.23,106 296,91.23 296,73 C296,54.77 281.23,40 263,40 C244.77,40 230,54.77 230,73 C230,91.23 244.77,106 263,106 ",
      firstArtboardShapes.get(2).getPathString());
    assertEquals(
      "M188.44,106 C169.42,106 154,120.67 154,138.76 C154,141.97 154.48,145.07 155.39,148 L181.28,148 Q189.28,148 189.28,140 L189.28,106.01 L189.28,106.01 C189,106 188.72,106 188.44,106 z",
      firstArtboardShapes.get(3).getPathString());

    List<ShapeModel> secondArtboardPaths = createDrawableAsset(artboards.get(0), new SketchLibrary()).getShapeModels();

    assertEquals(
      "M71,180 C94.2,180 113,198.8 113,222 C113,245.2 94.2,264 71,264 C47.8,264 29,245.2 29,222 C29,198.8 47.8,180 71,180 zM287.67,225.22 C296.68,237.48 302,252.62 302,269 C302,283.72 297.7,297.44 290.29,308.97 L290.29,308.97 C267.89,308.07 250,289.62 250,267 C250,245.27 266.51,227.39 287.67,225.22 zM10,164 C10,164 10,382.47 10,382.47 L77,382.47 C77,382.32 77,382.16 77,382 C77,358.8 95.8,340 119,340 C142.2,340 161,358.8 161,382 C161,382.16 161,382.32 161,382.47 L228.47,382.47 L228.47,343 L228.47,343 C254.42,342.84 277.21,329.32 290.29,308.97 L290.29,308.97 C290.86,308.99 291.43,309 292,309 C315.2,309 334,290.2 334,267 C334,243.8 315.2,225 292,225 C290.54,225 289.09,225.07 287.67,225.22 L287.67,225.22 C274.28,207.01 252.77,195.15 228.47,195 L228.47,195 L228.47,164 z",
      secondArtboardPaths.get(0).getPathString());
    assertEquals(
      "M347,206 C370.2,206 389,187.2 389,164 C389,140.8 370.2,122 347,122 C323.8,122 305,140.8 305,164 C305,187.2 323.8,206 347,206 ",
      secondArtboardPaths.get(1).getPathString());
    assertEquals(
      "M263,106 C281.23,106 296,91.23 296,73 C296,54.77 281.23,40 263,40 C244.77,40 230,54.77 230,73 C230,91.23 244.77,106 263,106 ",
      secondArtboardPaths.get(2).getPathString());
    assertEquals(
      "M188.44,106 C169.42,106 154,120.67 154,138.76 C154,141.97 154.48,145.07 155.39,148 L181.28,148 Q189.28,148 189.28,140 L189.28,106.01 L189.28,106.01 C189,106 188.72,106 188.44,106 z",
      secondArtboardPaths.get(3).getPathString());
  }

  public List<String> getFirstArtboardPaths(SketchPage sketchPage) {
    List<SketchArtboard> artboards = SketchFile.getArtboards(sketchPage);
    if (!artboards.isEmpty()) {
      return createDrawableAsset(artboards.get(0), new SketchLibrary()).getShapeModels()
                                                                        .stream()
                                                                        .map((shape) -> shape.getPathString())
                                                                        .collect(Collectors.toList());
    }
    return ImmutableList.of();
  }
}
