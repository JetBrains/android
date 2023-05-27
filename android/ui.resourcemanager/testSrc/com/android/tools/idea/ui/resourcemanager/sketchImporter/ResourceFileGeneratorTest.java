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
package com.android.tools.idea.ui.resourcemanager.sketchImporter;

import static org.junit.Assert.assertEquals;

import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.SketchLibrary;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.builders.ResourceFileGenerator;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.builders.SketchToStudioConverter;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.DrawableAssetModel;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchArtboard;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchPage;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.ui.SketchFile;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.ProjectRule;
import java.awt.Color;
import java.util.ArrayList;
import kotlin.Pair;
import org.jetbrains.android.AndroidTestBase;
import org.junit.Rule;
import org.junit.Test;


public class ResourceFileGeneratorTest {

  @Rule
  public ProjectRule projectRule = new ProjectRule();
  public final AndroidProjectRule rule = AndroidProjectRule.onDisk("ScreenshotViewerTest");

  @Test
  public void createFileTest() {
    ResourceFileGenerator resourceFileGenerator = new ResourceFileGenerator(projectRule.getProject());
    LightVirtualFile file = resourceFileGenerator.generateDrawableFile(null);

    assertEquals("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"/>",
                 file.getContent());
  }

  @Test
  public void createColorsFileTest() {
    ArrayList<Pair<Color, String>> colors = new ArrayList<>();
    colors.add(new Pair<>(new Color(255, 0, 0, 255), "colorPrimary"));
    colors.add(new Pair<>(new Color(0, 255, 0, 255), "colorPrimaryDark"));
    colors.add(new Pair<>(new Color(0, 0, 255, 255), "colorAccent"));

    ResourceFileGenerator resourceFileGenerator = new ResourceFileGenerator(projectRule.getProject());
    LightVirtualFile file = resourceFileGenerator.generateColorsFile(colors);

    assertEquals(
      "<resources><color name=\"colorPrimary\">#FFFF0000</color><color name=\"colorPrimaryDark\">#FF00FF00</color><color name=\"colorAccent\">#FF0000FF</color></resources>",
      file.getContent());
  }

  @Test
  public void addShapeTest() {
    SketchPage sketchPage = SketchTestUtils.Companion.parsePage(getTestFilePath("/sketch/vectordrawable_addShape.json"));
    SketchArtboard artboard = SketchFile.getArtboards(sketchPage).get(0);
    DrawableAssetModel drawableAsset = SketchToStudioConverter.createDrawableAsset(artboard, new SketchLibrary());

    ResourceFileGenerator resourceFileGenerator = new ResourceFileGenerator(projectRule.getProject());
    LightVirtualFile file = resourceFileGenerator.generateDrawableFile(drawableAsset);

    assertEquals(
      "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:height=\"401.0dp\" android:width=\"401.0dp\" android:viewportHeight=\"401.0\" android:viewportWidth=\"401.0\"><path android:pathData=\"M268.5,34 C227.23,34 194.76,66.3 194.5,107.5 L50,107.5 C50,107.5 50,325.5 50,325.5 L226.5,325.5 C226.5,325.67 226.5,325.83 226.5,326 C226.5,348.68 244.98,368 268.5,368 C291.18,368 310.5,348.68 310.5,326 C310.5,302.48 291.18,284 268.5,284 C268.33,284 268.17,284 268,284 L268,284 L268,182 L268,182 C268.17,182 268.33,182 268.5,182 C308.46,182 342.5,147.96 342.5,108 C342.5,66.56 308.46,34 268.5,34 z\" android:strokeColor=\"#FF979797\" android:strokeWidth=\"1\" android:fillColor=\"#FFD8D8D8\"/></vector>",
      file.getContent());
  }

  @Test
  public void shapeFillAndBorderTest() {
    SketchPage sketchPage = SketchTestUtils.Companion.parsePage(getTestFilePath("/sketch/vectordrawable_shapeFillAndBorder.json"));
    SketchArtboard artboard = SketchFile.getArtboards(sketchPage).get(0);
    DrawableAssetModel drawableAsset = SketchToStudioConverter.createDrawableAsset(artboard, new SketchLibrary());

    ResourceFileGenerator resourceFileGenerator = new ResourceFileGenerator(projectRule.getProject());
    LightVirtualFile file = resourceFileGenerator.generateDrawableFile(drawableAsset);

    assertEquals(
      "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:height=\"401.0dp\" android:width=\"401.0dp\" android:viewportHeight=\"401.0\" android:viewportWidth=\"401.0\"><path android:pathData=\"M214.65,169 Q206.65,169 206.65,177 L206.65,189 Q206.65,197 214.65,197 L233.87,197 C243.09,188.5 249.33,182.75 249.33,182.75 L264.78,197 L284.65,197 Q292.65,197 292.65,189 L292.65,177 Q292.65,169 284.65,169 zM233.87,197 C207.83,221.02 158,266.96 158,266.96 L159.75,266.96 C159.67,267.02 159.63,267.04 159.63,267.04 L194.27,373.64 L306.35,373.64 L340.88,267.37 L340.88,267.37 C320.49,295.04 287.67,313 250.65,313 C213.46,313 180.49,294.87 160.13,266.96 L340.65,266.96 L264.78,197 z\" android:strokeColor=\"#FF4A90E2\" android:strokeWidth=\"3\" android:fillColor=\"#FF50E3C2\"/><path android:pathData=\"M180.5,100 C201.76,100 219,82.76 219,61.5 C219,40.24 201.76,23 180.5,23 C159.24,23 142,40.24 142,61.5 C142,82.76 159.24,100 180.5,100 \" android:strokeColor=\"#FFD0021B\" android:strokeWidth=\"1\"/><path android:pathData=\"M147,185 C160.81,185 172,173.81 172,160 C172,146.19 160.81,135 147,135 C133.19,135 122,146.19 122,160 C122,173.81 133.19,185 147,185 \" android:strokeColor=\"#FF9013FE\" android:strokeWidth=\"1\" android:fillColor=\"#FFD8D8D8\"/><path android:pathData=\"M83.5,115 C92.06,115 99,108.06 99,99.5 C99,90.94 92.06,84 83.5,84 C74.94,84 68,90.94 68,99.5 C68,108.06 74.94,115 83.5,115 \" android:fillColor=\"#FFF8E71C\"/><path android:pathData=\"M60.5,196 C78.45,196 93,181.45 93,163.5 C93,145.55 78.45,131 60.5,131 C42.55,131 28,145.55 28,163.5 C28,181.45 42.55,196 60.5,196 \" android:strokeColor=\"#FF4A4A4A\" android:strokeWidth=\"1\" android:fillColor=\"#FF7ED321\"/><path android:pathData=\"M323.71,62.89 C323.71,62.89 308.25,39.04 282.63,46.99 C257,54.94 252.59,88.52 267.61,96.03 C282.63,103.54 327.25,100.44 317.53,131.37 C307.81,162.3 346.69,179.53 363.92,162.74 C381.15,145.95 406.77,119 377.17,86.31 C377.17,86.31 323.71,112.61 323.71,62.89 \" android:strokeColor=\"#FF979797\" android:strokeWidth=\"1\" android:fillColor=\"#FF000000\"/></vector>",
      file.getContent());
  }

  @Test
  public void shapeRotationTest() {
    SketchPage sketchPage = SketchTestUtils.Companion.parsePage(getTestFilePath("/sketch/vectordrawable_shapeRotation.json"));
    SketchArtboard artboard = SketchFile.getArtboards(sketchPage).get(0);
    DrawableAssetModel drawableAsset = SketchToStudioConverter.createDrawableAsset(artboard, new SketchLibrary());

    ResourceFileGenerator resourceFileGenerator = new ResourceFileGenerator(projectRule.getProject());
    LightVirtualFile file = resourceFileGenerator.generateDrawableFile(drawableAsset);

    assertEquals(
      "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:height=\"401.0dp\" android:width=\"401.0dp\" android:viewportHeight=\"401.0\" android:viewportWidth=\"401.0\"><path android:pathData=\"M207.7,79.62 L207.7,79.62 L207.7,79.62 C207.7,79.62 207.7,79.62 207.7,79.62 L207.7,79.62 zM243.65,129.16 C246.07,129.16 248.11,129.78 249.66,131.08 C258.55,138.53 248.19,165.5 226.54,191.31 C212.21,208.38 196.53,220.67 184.98,224.87 C182.12,225.91 179.52,226.46 177.25,226.46 C174.83,226.46 172.79,225.84 171.24,224.54 C162.36,217.08 172.71,190.12 194.37,164.31 C208.69,147.23 224.38,134.94 235.93,130.74 C238.78,129.7 241.39,129.16 243.65,129.16 zM228.4,51.71 L228.4,51.71 C228.4,51.71 215.18,69.54 207.7,79.62 L207.7,79.62 L130.47,66 C130.47,66 127.98,80.1 124.35,100.73 L124.35,100.73 L99.53,94.8 L99.53,94.8 C99.53,94.8 113.55,127.14 117.91,137.22 L117.91,137.22 C120.24,123.99 122.45,111.47 124.35,100.73 L124.35,100.73 L147.63,106.29 L147.63,106.29 L119.19,140.17 C119.19,140.17 118.72,139.07 117.91,137.22 L117.91,137.22 C108.03,193.27 95.91,261.98 95.91,261.98 L291.89,296.53 L326.45,100.56 L238.31,85.02 L238.31,85.02 L228.4,51.71 z\" android:strokeColor=\"#FF979797\" android:strokeWidth=\"1\" android:fillColor=\"#FFD8D8D8\"/></vector>",
      file.getContent());
  }

  @Test
  public void fillGradientTest() {
    SketchPage sketchPage = SketchTestUtils.Companion.parsePage(getTestFilePath("/sketch/vectordrawable_fillGradient.json"));
    SketchArtboard artboard = SketchFile.getArtboards(sketchPage).get(0);
    DrawableAssetModel drawableAsset = SketchToStudioConverter.createDrawableAsset(artboard, new SketchLibrary());

    ResourceFileGenerator resourceFileGenerator = new ResourceFileGenerator(projectRule.getProject());
    LightVirtualFile file = resourceFileGenerator.generateDrawableFile(drawableAsset);

    assertEquals(
      "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:height=\"401.0dp\" android:width=\"401.0dp\" android:viewportHeight=\"401.0\" android:viewportWidth=\"401.0\" xmlns:aapt=\"http://schemas.android.com/aapt\"><path android:pathData=\"M-3.45,111.55 L126.45,36.55 L201.45,166.45 L71.55,241.45 C71.55,241.45 -3.45,111.55 -3.45,111.55 \"><aapt:attr name = \"android:fillColor\"><gradient android:endX=\"136.5\" android:endY=\"203.9519052838329\" android:startX=\"61.5\" android:startY=\"74.0480947161671\" android:type=\"linear\"><item android:color=\"#FFB4EC51\" android:offset=\"0.0\"/><item android:color=\"#FF429321\" android:offset=\"1.0\"/></gradient></aapt:attr></path><path android:pathData=\"M307,197 C272.21,197 244,168.79 244,134 C244,99.21 272.21,71 307,71 C341.79,71 370,99.21 370,134 C370,168.79 341.79,197 307,197 \" android:strokeColor=\"#FF979797\" android:strokeWidth=\"1\"><aapt:attr name = \"android:fillColor\"><gradient android:centerX=\"324.0165822897214\" android:centerY=\"158.24032699766252\" android:gradientRadius=\"85.4989153541967\" android:type=\"radial\"><item android:color=\"#FF3023AE\" android:offset=\"0.0\"/><item android:color=\"#FF53A0FD\" android:offset=\"0.6997698737109772\"/><item android:color=\"#FFB4EC51\" android:offset=\"1.0\"/></gradient></aapt:attr></path><path android:pathData=\"M147,267 Q147,259 155,259 L279,259 Q287,259 287,267 L287,373 Q287,381 279,381 L155,381 Q147,381 147,373 z\" android:strokeColor=\"#FF979797\" android:strokeWidth=\"1\"><aapt:attr name = \"android:fillColor\"><gradient android:centerX=\"217.0\" android:centerY=\"320.0\" android:type=\"sweep\"><item android:color=\"#FFF5515F\" android:offset=\"0.0\"/><item android:color=\"#FF9F041B\" android:offset=\"1.0\"/></gradient></aapt:attr></path><path android:pathData=\"M79,276 L112,344 L46,344 C46,344 79,276 79,276 \" android:strokeColor=\"#FF979797\" android:strokeWidth=\"1\" android:fillColor=\"#FF50E3C2\"/></vector>",
      file.getContent());
  }

  @Test
  public void shapeMirroringTest() {
    SketchPage sketchPage = SketchTestUtils.Companion.parsePage(getTestFilePath("/sketch/vectordrawable_shapeMirroring.json"));
    SketchArtboard artboard = SketchFile.getArtboards(sketchPage).get(0);
    DrawableAssetModel drawableAsset = SketchToStudioConverter.createDrawableAsset(artboard, new SketchLibrary());

    ResourceFileGenerator resourceFileGenerator = new ResourceFileGenerator(projectRule.getProject());
    LightVirtualFile file = resourceFileGenerator.generateDrawableFile(drawableAsset);

    assertEquals(
      "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:height=\"401.0dp\" android:width=\"401.0dp\" android:viewportHeight=\"401.0\" android:viewportWidth=\"401.0\"><path android:pathData=\"M247,180 L137.43,281.43 L82.64,246.18 C82.64,246.18 247,180 247,180 zM75,123 C75,123 75,289.82 75,289.82 L314,289.82 L222.76,123 z\" android:strokeColor=\"#FF979797\" android:strokeWidth=\"1\" android:fillColor=\"#FFD8D8D8\"/></vector>",
      file.getContent());
  }

  @Test
  public void clippingTest() {
    SketchPage sketchPage = SketchTestUtils.Companion.parsePage(getTestFilePath("/sketch/vectordrawable_clipping.json"));
    SketchArtboard artboard = SketchFile.getArtboards(sketchPage).get(0);
    DrawableAssetModel drawableAsset = SketchToStudioConverter.createDrawableAsset(artboard, new SketchLibrary());

    ResourceFileGenerator drawableGenerator = new ResourceFileGenerator(projectRule.getProject());
    LightVirtualFile file = drawableGenerator.generateDrawableFile(drawableAsset);

    assertEquals(
      "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:height=\"1006.0dp\" android:width=\"1006.0dp\" android:viewportHeight=\"1006.0\" android:viewportWidth=\"1006.0\"><clip-path android:pathData=\"M472,18 L860,946 L84,946 C84,946 472,18 472,18 \"/><path android:pathData=\"M472,18 L860,946 L84,946 C84,946 472,18 472,18 \" android:fillColor=\"#FFD8D8D8\"/><group><clip-path android:pathData=\"M681.5,946 C803.83,946 903,846.83 903,724.5 C903,602.17 803.83,503 681.5,503 C559.17,503 460,602.17 460,724.5 C460,846.83 559.17,946 681.5,946 \"/><path android:pathData=\"M681.5,946 C803.83,946 903,846.83 903,724.5 C903,602.17 803.83,503 681.5,503 C559.17,503 460,602.17 460,724.5 C460,846.83 559.17,946 681.5,946 \" android:fillColor=\"#FFFF0000\"/><path android:pathData=\"M289,531 L1071,531 L1071,917 L289,917 C289,917 289,531 289,531 \" android:strokeColor=\"#FF979797\" android:strokeWidth=\"1\" android:fillColor=\"#FFF8E71C\"/><path android:pathData=\"M529,761 L670,761 L670,902 L529,902 C529,902 529,761 529,761 \" android:strokeColor=\"#FF979797\" android:strokeWidth=\"1\" android:fillColor=\"#FF50E3C2\"/></group><path android:pathData=\"M618,677 L759,677 L759,818 L618,818 C618,818 618,677 618,677 \" android:strokeColor=\"#FF979797\" android:strokeWidth=\"1\" android:fillColor=\"#FFB8E986\"/><path android:pathData=\"M213.5,771 C264.03,771 305,730.03 305,679.5 C305,628.97 264.03,588 213.5,588 C162.97,588 122,628.97 122,679.5 C122,730.03 162.97,771 213.5,771 \" android:fillColor=\"#FF000000\"/><group><clip-path android:pathData=\"M343.5,515 C465.83,515 565,415.83 565,293.5 C565,171.17 465.83,72 343.5,72 C221.17,72 122,171.17 122,293.5 C122,415.83 221.17,515 343.5,515 \"/><path android:pathData=\"M343.5,515 C465.83,515 565,415.83 565,293.5 C565,171.17 465.83,72 343.5,72 C221.17,72 122,171.17 122,293.5 C122,415.83 221.17,515 343.5,515 \" android:fillColor=\"#FFFF0000\"/><path android:pathData=\"M-49,100 L733,100 L733,486 L-49,486 C-49,486 -49,100 -49,100 \" android:strokeColor=\"#FF979797\" android:strokeWidth=\"1\" android:fillColor=\"#FFF8E71C\"/><path android:pathData=\"M191,330 L332,330 L332,471 L191,471 C191,471 191,330 191,330 \" android:strokeColor=\"#FF979797\" android:strokeWidth=\"1\" android:fillColor=\"#FF50E3C2\"/></group><path android:pathData=\"M403,63 L544,63 L544,204 L403,204 C403,204 403,63 403,63 \" android:strokeColor=\"#FF979797\" android:strokeWidth=\"1\" android:fillColor=\"#FFB8E986\"/></vector>",
      file.getContent());
  }

  @Test
  public void additiveTransparencyTest() {
    SketchPage sketchPage = SketchTestUtils.Companion.parsePage(getTestFilePath("/sketch/vectordrawable_additiveTransparency.json"));
    SketchArtboard artboard = SketchFile.getArtboards(sketchPage).get(0);
    DrawableAssetModel drawableAsset = SketchToStudioConverter.createDrawableAsset(artboard, new SketchLibrary());

    ResourceFileGenerator drawableGenerator = new ResourceFileGenerator(projectRule.getProject());
    LightVirtualFile file = drawableGenerator.generateDrawableFile(drawableAsset);

    assertEquals(
      "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:height=\"908.0dp\" android:width=\"908.0dp\" android:viewportHeight=\"908.0\" android:viewportWidth=\"908.0\" xmlns:aapt=\"http://schemas.android.com/aapt\"><path android:pathData=\"M64,638 L432,638 L432,847 L64,847 C64,847 64,638 64,638 \" android:fillColor=\"#7FFF0000\"/><path android:pathData=\"M538,297 L872,297 L872,513 L538,513 C538,513 538,297 538,297 \" android:fillColor=\"#64FF0000\"/><path android:pathData=\"M257,371 L695,371 L695,581 L257,581 C257,581 257,371 257,371 \" android:fillColor=\"#6BFF0000\"/><path android:pathData=\"M476,513 L776,513 L776,847 L476,847 C476,847 476,513 476,513 \"><aapt:attr name = \"android:fillColor\"><gradient android:endX=\"626.0\" android:endY=\"847.0\" android:startX=\"626.0\" android:startY=\"513.0\" android:type=\"linear\"><item android:color=\"#70FF0000\" android:offset=\"0.0\"/><item android:color=\"#4EFF0000\" android:offset=\"1.0\"/></gradient></aapt:attr></path><path android:pathData=\"M110,130 L386,130 L386,297 L110,297 C110,297 110,130 110,130 \" android:fillColor=\"#80FF0000\"/><path android:pathData=\"M413,47 L695,47 L695,363 L413,363 C413,363 413,47 413,47 \" android:fillColor=\"#90FF0000\"/><path android:pathData=\"M22,279 L318,279 L318,447 L22,447 C22,447 22,279 22,279 \" android:fillColor=\"#7FFF0000\"/></vector>",
      file.getContent());
  }

  private static String getTestFilePath(String file) {
    return AndroidTestBase.getTestDataPath() + file;
  }
}
