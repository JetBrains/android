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

import static com.android.tools.idea.resourceExplorer.sketchImporter.converter.model_converters.SketchToShapeConverter.createAllDrawableShapes;
import static org.junit.Assert.assertEquals;

import com.android.tools.idea.resourceExplorer.sketchImporter.converter.SymbolsLibrary;
import com.android.tools.idea.resourceExplorer.sketchImporter.converter.builders.DrawableFileGenerator;
import com.android.tools.idea.resourceExplorer.sketchImporter.converter.models.VectorDrawable;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchArtboard;
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchPage;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.utils.Pair;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.ProjectRule;
import java.awt.Color;
import java.util.ArrayList;
import org.jetbrains.android.AndroidTestBase;
import org.junit.Rule;
import org.junit.Test;


public class DrawableFileGeneratorTest {

  @Rule
  public ProjectRule projectRule = new ProjectRule();
  public final AndroidProjectRule rule = AndroidProjectRule.onDisk("ScreenshotViewerTest");

  @Test
  public void createFileTest() {
    DrawableFileGenerator drawableFileGenerator = new DrawableFileGenerator(projectRule.getProject());
    LightVirtualFile file = drawableFileGenerator.generateDrawableFile(null);

    assertEquals("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"/>",
                 file.getContent());
  }

  @Test
  public void createColorsFileTest() {
    ArrayList<Pair<String, Color>> colors = new ArrayList<>();
    colors.add(Pair.of("colorPrimary", new Color(255, 0, 0, 255)));
    colors.add(Pair.of("colorPrimaryDark", new Color(0, 255, 0, 255)));
    colors.add(Pair.of("colorAccent", new Color(0, 0, 255, 255)));

    DrawableFileGenerator drawableFileGenerator = new DrawableFileGenerator(projectRule.getProject());
    LightVirtualFile file = drawableFileGenerator.generateColorsFile(colors);

    assertEquals(
      "<resources><color name=\"colorPrimary\">ffff0000</color><color name=\"colorPrimaryDark\">ff00ff00</color><color name=\"colorAccent\">ff0000ff</color></resources>",
      file.getContent());
  }

  @Test
  public void addShapeTest() {
    SketchPage sketchPage = SketchTestUtils.Companion.parsePage(getTestFilePath("/sketch/vectordrawable_addShape.json"));
    SketchArtboard artboard = sketchPage.getArtboards().get(0);
    VectorDrawable vectorDrawable = new VectorDrawable(artboard, createAllDrawableShapes(artboard, new SymbolsLibrary()));

    DrawableFileGenerator drawableFileGenerator = new DrawableFileGenerator(projectRule.getProject());
    LightVirtualFile file = drawableFileGenerator.generateDrawableFile(vectorDrawable);

    assertEquals(
      "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:height=\"401.0dp\" android:width=\"401.0dp\" android:viewportHeight=\"401.0\" android:viewportWidth=\"401.0\"><path android:pathData=\"M268.5,34 C227.23,34 194.76,66.3 194.5,107.5 L50,107.5 L50,325.5 L226.5,325.5 C226.5,325.67 226.5,325.83 226.5,326 C226.5,348.68 244.98,368 268.5,368 C291.18,368 310.5,348.68 310.5,326 C310.5,302.48 291.18,284 268.5,284 C268.33,284 268.17,284 268,284 L268,284 L268,182 L268,182 C268.17,182 268.33,182 268.5,182 C308.46,182 342.5,147.96 342.5,108 C342.5,66.56 308.46,34 268.5,34 z\" android:strokeColor=\"#ff979797\" android:strokeWidth=\"1\" android:fillColor=\"#ffd8d8d8\"/></vector>",
      file.getContent());
  }

  @Test
  public void shapeFillAndBorderTest() {
    SketchPage sketchPage = SketchTestUtils.Companion.parsePage(getTestFilePath("/sketch/vectordrawable_shapeFillAndBorder.json"));
    SketchArtboard artboard = sketchPage.getArtboards().get(0);
    VectorDrawable vectorDrawable = new VectorDrawable(artboard, createAllDrawableShapes(artboard, new SymbolsLibrary()));

    DrawableFileGenerator drawableFileGenerator = new DrawableFileGenerator(projectRule.getProject());
    LightVirtualFile file = drawableFileGenerator.generateDrawableFile(vectorDrawable);

    assertEquals(
      "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:height=\"401.0dp\" android:width=\"401.0dp\" android:viewportHeight=\"401.0\" android:viewportWidth=\"401.0\"><path android:pathData=\"M214.65,169 Q206.65,169 206.65,177 L206.65,189 Q206.65,197 214.65,197 L233.87,197 C243.09,188.5 249.33,182.75 249.33,182.75 L264.78,197 L284.65,197 Q292.65,197 292.65,189 L292.65,177 Q292.65,169 284.65,169 zM233.87,197 C207.83,221.02 158,266.96 158,266.96 L159.75,266.96 C159.67,267.02 159.63,267.04 159.63,267.04 L194.27,373.64 L306.35,373.64 L340.88,267.37 L340.88,267.37 C320.49,295.04 287.67,313 250.65,313 C213.46,313 180.49,294.87 160.13,266.96 L340.65,266.96 L264.78,197 z\" android:strokeColor=\"#ff4a90e2\" android:strokeWidth=\"3\" android:fillColor=\"#ff50e3c2\"/><path android:pathData=\"M180.5,100 C201.76,100 219,82.76 219,61.5 C219,40.24 201.76,23 180.5,23 C159.24,23 142,40.24 142,61.5 C142,82.76 159.24,100 180.5,100 \" android:strokeColor=\"#ffd0021b\" android:strokeWidth=\"1\"/><path android:pathData=\"M147,185 C160.81,185 172,173.81 172,160 C172,146.19 160.81,135 147,135 C133.19,135 122,146.19 122,160 C122,173.81 133.19,185 147,185 \" android:strokeColor=\"#ff9013fe\" android:strokeWidth=\"1\" android:fillColor=\"#ffd8d8d8\"/><path android:pathData=\"M83.5,115 C92.06,115 99,108.06 99,99.5 C99,90.94 92.06,84 83.5,84 C74.94,84 68,90.94 68,99.5 C68,108.06 74.94,115 83.5,115 \" android:strokeColor=\"#fff8e71c\" android:strokeWidth=\"1\" android:fillColor=\"#fff8e71c\"/><path android:pathData=\"M60.5,196 C78.45,196 93,181.45 93,163.5 C93,145.55 78.45,131 60.5,131 C42.55,131 28,145.55 28,163.5 C28,181.45 42.55,196 60.5,196 \" android:strokeColor=\"#ff4a4a4a\" android:strokeWidth=\"1\" android:fillColor=\"#ff7ed321\"/><path android:pathData=\"M323.71,62.89 C323.71,62.89 308.25,39.04 282.63,46.99 C257,54.94 252.59,88.52 267.61,96.03 C282.63,103.54 327.25,100.44 317.53,131.37 C307.81,162.3 346.69,179.53 363.92,162.74 C381.15,145.95 406.77,119 377.17,86.31 C377.17,86.31 323.71,112.61 323.71,62.89 \" android:strokeColor=\"#ff979797\" android:strokeWidth=\"1\" android:fillColor=\"#ff000000\"/></vector>",
      file.getContent());
  }

  @Test
  public void shapeRotationTest() {
    SketchPage sketchPage = SketchTestUtils.Companion.parsePage(getTestFilePath("/sketch/vectordrawable_shapeRotation.json"));
    SketchArtboard artboard = sketchPage.getArtboards().get(0);
    VectorDrawable vectorDrawable = new VectorDrawable(artboard, createAllDrawableShapes(artboard, new SymbolsLibrary()));

    DrawableFileGenerator drawableFileGenerator = new DrawableFileGenerator(projectRule.getProject());
    LightVirtualFile file = drawableFileGenerator.generateDrawableFile(vectorDrawable);

    assertEquals(
      "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:height=\"401.0dp\" android:width=\"401.0dp\" android:viewportHeight=\"401.0\" android:viewportWidth=\"401.0\"><path android:pathData=\"M197.44,94.27 L197.44,94.27 L197.44,94.27 C197.44,94.27 197.44,94.27 197.44,94.27 L197.44,94.27 zM89.27,109.45 L89.27,109.45 C89.27,109.45 103.29,141.8 107.65,151.87 L107.65,151.87 L114.09,115.38 L89.27,109.45 zM233.39,143.81 C235.81,143.81 237.85,144.43 239.4,145.73 C248.29,153.19 237.93,180.15 216.28,205.96 C201.95,223.03 186.27,235.32 174.72,239.53 C171.86,240.57 169.26,241.11 166.99,241.11 C164.57,241.11 162.53,240.49 160.98,239.19 C152.1,231.73 162.45,204.77 184.11,178.96 C198.43,161.89 214.12,149.6 225.67,145.39 C228.52,144.35 231.13,143.81 233.39,143.81 zM218.14,66.36 L218.14,66.36 C218.14,66.36 204.92,84.19 197.44,94.27 L197.44,94.27 L120.21,80.65 L114.09,115.38 L137.37,120.94 L137.37,120.94 L108.93,154.83 C108.93,154.83 108.46,153.73 107.65,151.87 C107.65,151.87 107.65,151.87 107.65,151.87 L107.65,151.87 L107.65,151.87 L85.65,276.63 L281.63,311.19 L316.19,115.21 L228.05,99.67 L228.05,99.67 L218.14,66.36 z\" android:strokeColor=\"#ff979797\" android:strokeWidth=\"1\" android:fillColor=\"#ffd8d8d8\"/></vector>",
      file.getContent());
  }

  @Test
  public void fillGradientTest() {
    SketchPage sketchPage = SketchTestUtils.Companion.parsePage(getTestFilePath("/sketch/vectordrawable_fillGradient.json"));
    SketchArtboard artboard = sketchPage.getArtboards().get(0);
    VectorDrawable vectorDrawable = new VectorDrawable(artboard, createAllDrawableShapes(artboard, new SymbolsLibrary()));

    DrawableFileGenerator drawableFileGenerator = new DrawableFileGenerator(projectRule.getProject());
    LightVirtualFile file = drawableFileGenerator.generateDrawableFile(vectorDrawable);

    assertEquals(
      "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:height=\"401.0dp\" android:width=\"401.0dp\" android:viewportHeight=\"401.0\" android:viewportWidth=\"401.0\" xmlns:aapt=\"http://schemas.android.com/aapt\"><path android:pathData=\"M-3.45,111.55 L126.45,36.55 L201.45,166.45 L71.55,241.45 z\" android:strokeColor=\"#ff979797\" android:strokeWidth=\"1\"><aapt:attr name = \"android:fillColor\"><gradient android:endX=\"136.5\" android:endY=\"203.9519052838329\" android:startX=\"61.5\" android:startY=\"74.0480947161671\" android:type=\"linear\"><item android:color=\"#ffb4ec51\" android:offset=\"0.0\"/><item android:color=\"#ff429321\" android:offset=\"1.0\"/></gradient></aapt:attr></path><path android:pathData=\"M307,197 C272.21,197 244,168.79 244,134 C244,99.21 272.21,71 307,71 C341.79,71 370,99.21 370,134 C370,168.79 341.79,197 307,197 \" android:strokeColor=\"#ff979797\" android:strokeWidth=\"1\"><aapt:attr name = \"android:fillColor\"><gradient android:centerX=\"324.0165822897214\" android:centerY=\"158.24032699766252\" android:gradientRadius=\"85.4989153541967\" android:type=\"radial\"><item android:color=\"#ff3023ae\" android:offset=\"0.0\"/><item android:color=\"#ff53a0fd\" android:offset=\"0.6997698737109772\"/><item android:color=\"#ffb4ec51\" android:offset=\"1.0\"/></gradient></aapt:attr></path><path android:pathData=\"M147,267 Q147,259 155,259 L279,259 Q287,259 287,267 L287,373 Q287,381 279,381 L155,381 Q147,381 147,373 z\" android:strokeColor=\"#ff979797\" android:strokeWidth=\"1\"><aapt:attr name = \"android:fillColor\"><gradient android:centerX=\"217.0\" android:centerY=\"320.0\" android:type=\"sweep\"><item android:color=\"#fff5515f\" android:offset=\"0.0\"/><item android:color=\"#ff9f041b\" android:offset=\"1.0\"/></gradient></aapt:attr></path><path android:pathData=\"M79,276 L112,344 L46,344 C46,344 79,276 79,276 \" android:strokeColor=\"#ff979797\" android:strokeWidth=\"1\" android:fillColor=\"#ff50e3c2\"/></vector>",
      file.getContent());
  }

  @Test
  public void shapeMirroringTest() {
    SketchPage sketchPage = SketchTestUtils.Companion.parsePage(getTestFilePath("/sketch/vectordrawable_shapeMirroring.json"));
    SketchArtboard artboard = sketchPage.getArtboards().get(0);
    VectorDrawable vectorDrawable = new VectorDrawable(artboard, createAllDrawableShapes(artboard, new SymbolsLibrary()));

    DrawableFileGenerator drawableFileGenerator = new DrawableFileGenerator(projectRule.getProject());
    LightVirtualFile file = drawableFileGenerator.generateDrawableFile(vectorDrawable);

    assertEquals(
      "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:height=\"401.0dp\" android:width=\"401.0dp\" android:viewportHeight=\"401.0\" android:viewportWidth=\"401.0\"><path android:pathData=\"M247,180 L137.43,281.43 L82.64,246.18 C82.64,246.18 247,180 247,180 zM75,123 C75,123 75,289.82 75,289.82 L314,289.82 L222.76,123 z\" android:strokeColor=\"#ff979797\" android:strokeWidth=\"1\" android:fillColor=\"#ffd8d8d8\"/></vector>",
      file.getContent());
  }

  @Test
  public void clippingTest() {
    SketchPage sketchPage = SketchTestUtils.Companion.parsePage(getTestFilePath("/sketch/vectordrawable_clipping.json"));
    SketchArtboard artboard = sketchPage.getArtboards().get(0);
    VectorDrawable vectorDrawable = new VectorDrawable(artboard, createAllDrawableShapes(artboard, new SymbolsLibrary()));

    DrawableFileGenerator drawableGenerator = new DrawableFileGenerator(projectRule.getProject());
    LightVirtualFile file = drawableGenerator.generateDrawableFile(vectorDrawable);

    assertEquals(
      "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:height=\"1006.0dp\" android:width=\"1006.0dp\" android:viewportHeight=\"1006.0\" android:viewportWidth=\"1006.0\"><clip-path android:pathData=\"M472,18 L860,946 L84,946 C84,946 472,18 472,18 \"/><path android:pathData=\"M472,18 L860,946 L84,946 C84,946 472,18 472,18 \" android:strokeColor=\"#ff979797\" android:strokeWidth=\"1\" android:fillColor=\"#ffd8d8d8\"/><group><clip-path android:pathData=\"M681.5,946 C803.83,946 903,846.83 903,724.5 C903,602.17 803.83,503 681.5,503 C559.17,503 460,602.17 460,724.5 C460,846.83 559.17,946 681.5,946 \"/><path android:pathData=\"M681.5,946 C803.83,946 903,846.83 903,724.5 C903,602.17 803.83,503 681.5,503 C559.17,503 460,602.17 460,724.5 C460,846.83 559.17,946 681.5,946 \" android:strokeColor=\"#ff979797\" android:strokeWidth=\"1\" android:fillColor=\"#ffff0000\"/><path android:pathData=\"M289,531 L1071,531 L1071,917 L289,917 z\" android:strokeColor=\"#ff979797\" android:strokeWidth=\"1\" android:fillColor=\"#fff8e71c\"/><path android:pathData=\"M529,761 L670,761 L670,902 L529,902 z\" android:strokeColor=\"#ff979797\" android:strokeWidth=\"1\" android:fillColor=\"#ff50e3c2\"/></group><path android:pathData=\"M618,677 L759,677 L759,818 L618,818 z\" android:strokeColor=\"#ff979797\" android:strokeWidth=\"1\" android:fillColor=\"#ffb8e986\"/><path android:pathData=\"M213.5,771 C264.03,771 305,730.03 305,679.5 C305,628.97 264.03,588 213.5,588 C162.97,588 122,628.97 122,679.5 C122,730.03 162.97,771 213.5,771 \" android:strokeColor=\"#ff979797\" android:strokeWidth=\"1\" android:fillColor=\"#ff000000\"/><group><clip-path android:pathData=\"M343.5,515 C465.83,515 565,415.83 565,293.5 C565,171.17 465.83,72 343.5,72 C221.17,72 122,171.17 122,293.5 C122,415.83 221.17,515 343.5,515 \"/><path android:pathData=\"M343.5,515 C465.83,515 565,415.83 565,293.5 C565,171.17 465.83,72 343.5,72 C221.17,72 122,171.17 122,293.5 C122,415.83 221.17,515 343.5,515 \" android:strokeColor=\"#ff979797\" android:strokeWidth=\"1\" android:fillColor=\"#ffff0000\"/><path android:pathData=\"M-49,100 L733,100 L733,486 L-49,486 z\" android:strokeColor=\"#ff979797\" android:strokeWidth=\"1\" android:fillColor=\"#fff8e71c\"/><path android:pathData=\"M191,330 L332,330 L332,471 L191,471 z\" android:strokeColor=\"#ff979797\" android:strokeWidth=\"1\" android:fillColor=\"#ff50e3c2\"/></group><path android:pathData=\"M403,63 L544,63 L544,204 L403,204 z\" android:strokeColor=\"#ff979797\" android:strokeWidth=\"1\" android:fillColor=\"#ffb8e986\"/></vector>",
      file.getContent());
  }

  @Test
  public void additiveTransparencyTest() {
    SketchPage sketchPage = SketchTestUtils.Companion.parsePage(getTestFilePath("/sketch/vectordrawable_additiveTransparency.json"));
    SketchArtboard artboard = sketchPage.getArtboards().get(0);
    VectorDrawable vectorDrawable = new VectorDrawable(artboard, createAllDrawableShapes(artboard, new SymbolsLibrary()));

    DrawableFileGenerator drawableGenerator = new DrawableFileGenerator(projectRule.getProject());
    LightVirtualFile file = drawableGenerator.generateDrawableFile(vectorDrawable);

    assertEquals(
      "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:height=\"908.0dp\" android:width=\"908.0dp\" android:viewportHeight=\"908.0\" android:viewportWidth=\"908.0\" xmlns:aapt=\"http://schemas.android.com/aapt\"><path android:pathData=\"M64,638 L432,638 L432,847 L64,847 z\" android:strokeColor=\"#7f979797\" android:strokeWidth=\"1\" android:fillColor=\"#7fff0000\"/><path android:pathData=\"M538,297 L872,297 L872,513 L538,513 z\" android:strokeColor=\"#8e979797\" android:strokeWidth=\"1\" android:fillColor=\"#64ff0000\"/><path android:pathData=\"M257,371 L695,371 L695,581 L257,581 z\" android:strokeColor=\"#b2979797\" android:strokeWidth=\"1\" android:fillColor=\"#6bff0000\"/><path android:pathData=\"M476,513 L776,513 L776,847 L476,847 z\" android:strokeColor=\"#a0979797\" android:strokeWidth=\"1\"><aapt:attr name = \"android:fillColor\"><gradient android:endX=\"626.0\" android:endY=\"847.0\" android:startX=\"626.0\" android:startY=\"513.0\" android:type=\"linear\"><item android:color=\"#70ff0000\" android:offset=\"0.0\"/><item android:color=\"#4eff0000\" android:offset=\"1.0\"/></gradient></aapt:attr></path><path android:pathData=\"M110,130 L386,130 L386,297 L110,297 z\" android:strokeColor=\"#a0979797\" android:strokeWidth=\"1\" android:fillColor=\"#80ff0000\"/><path android:pathData=\"M413,47 L695,47 L695,363 L413,363 z\" android:strokeColor=\"#a0979797\" android:strokeWidth=\"1\" android:fillColor=\"#90ff0000\"/><path android:pathData=\"M22,279 L318,279 L318,447 L22,447 z\" android:strokeColor=\"#7f979797\" android:strokeWidth=\"1\" android:fillColor=\"#7fff0000\"/></vector>",
      file.getContent());
  }

  private static String getTestFilePath(String file) {
    return AndroidTestBase.getTestDataPath() + file;
  }
}
