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

import com.android.tools.idea.resourceExplorer.sketchImporter.structure.DrawableShape;
import com.android.tools.idea.resourceExplorer.sketchImporter.logic.VectorDrawableFile;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchArtboard;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchPage;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.testFramework.ProjectRule;
import org.jetbrains.android.AndroidTestBase;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Scanner;

import static org.junit.Assert.assertEquals;


public class VectorDrawableFileTest {

  @Rule
  public ProjectRule projectRule = new ProjectRule();
  public final AndroidProjectRule rule = AndroidProjectRule.onDisk("ScreenshotViewerTest");

  @Test
  public void createFileTest() throws IOException {
    VectorDrawableFile vectorDrawableFile = new VectorDrawableFile(projectRule.getProject());
    vectorDrawableFile.createVectorDrawable();
    String path = getFilePath("/sketch/test.xml");
    vectorDrawableFile.saveDrawableToDisk(path);

    assertEquals("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"",
                 readFromFile(path));
  }

  @Test
  public void addShapeTest() throws IOException {

    SketchPage sketchPage = SketchParser.parsePage(getFilePath("/sketch/vectordrawable_addShape.json"));
    SketchArtboard sketchArtboard = sketchPage.getArtboards().get(0);

    VectorDrawableFile vectorDrawableFile = new VectorDrawableFile(projectRule.getProject());
    vectorDrawableFile.createVectorDrawable();
    vectorDrawableFile.setVectorDimensions(sketchArtboard.getFrame().getHeight(), sketchArtboard.getFrame().getWidth());
    vectorDrawableFile.setViewportDimensions(sketchArtboard.getFrame().getHeight(), sketchArtboard.getFrame().getWidth());
    List<DrawableShape> shapes = sketchArtboard.getShapes();
    for(DrawableShape shape:shapes){
      vectorDrawableFile.addPath(shape);
    }
    String path = getFilePath("/sketch/test.xml");
    vectorDrawableFile.saveDrawableToDisk(path);

    assertEquals("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:height=\"401.0dp\" android:width=\"401.0dp\" android:viewportHeight=\"401.0\" android:viewportWidth=\"401.0\"><path android:name=\"Combined Shape\" android:pathData=\"M268.5,34.0 C227.22681106905614,34.0 194.76227556749063,66.29839590772073 194.50158121846573,107.5 L50.0,107.5 L50.0,325.5 L226.5027875876822,325.5 C226.5009307973233,325.66640960999274 226.5,325.83307787808866 226.5,326.0 C226.5,348.68 244.98000000000002,368.0 268.5,368.0 C291.18,368.0 310.5,348.68 310.5,326.0 C310.5,302.48 291.18,284.0 268.5,284.0 C268.3329080451577,284.0 268.1660704609547,284.00093269234424 267.99949206635785,284.0027932580658 L268.0,284.0027932580658 L268.0,181.9983471275387 L268.00000002679803,181.9983471275387 C268.166521767314,181.99944843113553 268.33318893989207,182.0 268.5,182.0 C308.46000000000004,182.0 342.5,147.96 342.5,108.0 C342.5,66.56 308.46000000000004,34.0 268.5,34.0 z\" android:fillColor=\"#ffd8d8d8\" android:strokeColor=\"#ff979797\" android:strokeWidth=\"1\"/></vector>",
                 readFromFile(path));
  }

  @Test
  public void shapeFillAndBorderTest(){
    SketchPage sketchPage = SketchParser.parsePage(getFilePath("/sketch/vectordrawable_shapeFillAndBorder.json"));
    SketchArtboard sketchArtboard = sketchPage.getArtboards().get(0);

    VectorDrawableFile vectorDrawableFile = new VectorDrawableFile(projectRule.getProject());
    vectorDrawableFile.createVectorDrawable();
    vectorDrawableFile.setVectorDimensions(sketchArtboard.getFrame().getHeight(), sketchArtboard.getFrame().getWidth());
    vectorDrawableFile.setViewportDimensions(sketchArtboard.getFrame().getHeight(), sketchArtboard.getFrame().getWidth());
    List<DrawableShape> shapes = sketchArtboard.getShapes();
    for(DrawableShape shape:shapes){
      vectorDrawableFile.addPath(shape);
    }
    String path = getFilePath("/sketch/test.xml");
    vectorDrawableFile.saveDrawableToDisk(path);
  }

  public String readFromFile(String path) throws IOException {
    StringBuilder fileContentBuilder = new StringBuilder();
    File file = new File(path);
    Scanner sc = new Scanner(file);

    while (sc.hasNextLine()){
      fileContentBuilder.append(sc.nextLine());
      }

    return fileContentBuilder.toString();
  }

  private static String getFilePath(String file){
    return AndroidTestBase.getTestDataPath() + file;
  }
}


//LightVirtualFile virtualFile = new LightVirtualFile();
//virtualFile.setContent();
//new VectorDrawableAssetRenderer().getImage(virtualFile, rule.module, new Dimension(10,10));
//ImageDiffUtil.assertImageSimilar();
