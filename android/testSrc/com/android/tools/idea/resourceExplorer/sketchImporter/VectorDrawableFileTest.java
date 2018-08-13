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

import com.android.tools.idea.resourceExplorer.sketchImporter.logic.VectorDrawableFile;
import com.android.tools.idea.resourceExplorer.sketchImporter.presenter.SketchParser;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchArtboard;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchPage;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.ProjectRule;
import org.jetbrains.android.AndroidTestBase;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class VectorDrawableFileTest {

  @Rule
  public ProjectRule projectRule = new ProjectRule();
  public final AndroidProjectRule rule = AndroidProjectRule.onDisk("ScreenshotViewerTest");

  @Test
  public void createFileTest() {
    VectorDrawableFile vectorDrawableFile = new VectorDrawableFile(projectRule.getProject());
    vectorDrawableFile.createVectorDrawable();
    LightVirtualFile file = vectorDrawableFile.generateFile();

    assertEquals("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"",
                 file.getContent());
  }

  @Test
  public void addShapeTest() {

    SketchPage sketchPage = SketchParser.parsePage(getTestFilePath("/sketch/vectordrawable_addShape.json"));
    SketchArtboard artboard = sketchPage.getArtboards().get(0);

    VectorDrawableFile vectorDrawableFile = new VectorDrawableFile(projectRule.getProject(), artboard);
    LightVirtualFile file = vectorDrawableFile.generateFile();

    assertEquals(
      "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:height=\"401.0dp\" android:width=\"401.0dp\" android:viewportHeight=\"401.0\" android:viewportWidth=\"401.0\"><path android:name=\"Combined Shape\" android:pathData=\"M268.5,34.0 C227.22681106905614,34.0 194.76227556749063,66.29839590772073 194.50158121846573,107.5 L50.0,107.5 L50.0,325.5 L226.5027875876822,325.5 C226.5009307973233,325.66640960999274 226.5,325.83307787808866 226.5,326.0 C226.5,348.68 244.98000000000002,368.0 268.5,368.0 C291.18,368.0 310.5,348.68 310.5,326.0 C310.5,302.48 291.18,284.0 268.5,284.0 C268.3329080451577,284.0 268.1660704609547,284.00093269234424 267.99949206635785,284.0027932580658 L268.0,284.0027932580658 L268.0,181.9983471275387 L268.00000002679803,181.9983471275387 C268.166521767314,181.99944843113553 268.33318893989207,182.0 268.5,182.0 C308.46000000000004,182.0 342.5,147.96 342.5,108.0 C342.5,66.56 308.46000000000004,34.0 268.5,34.0 z\" android:strokeColor=\"#ff979797\" android:strokeWidth=\"1\" android:fillColor=\"#ffd8d8d8\"/></vector>",
      file.getContent());
  }

  @Test
  public void shapeFillAndBorderTest() {
    SketchPage sketchPage = SketchParser.parsePage(getTestFilePath("/sketch/vectordrawable_shapeFillAndBorder.json"));
    SketchArtboard sketchArtboard = sketchPage.getArtboards().get(0);

    VectorDrawableFile vectorDrawableFile = new VectorDrawableFile(projectRule.getProject(), sketchArtboard);
    LightVirtualFile file = vectorDrawableFile.generateFile();

    assertEquals(
      "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:height=\"401.0dp\" android:width=\"401.0dp\" android:viewportHeight=\"401.0\" android:viewportWidth=\"401.0\"><path android:name=\"Combined Shape\" android:pathData=\"M214.65074923291172,169.0 Q206.65074923291172,169.0 206.65074923291172,177.0 L206.65074923291172,189.0 Q206.65074923291172,197.0 214.65074923291172,197.0 L233.87111472473228,197.0 C243.08697031716272,188.50201287793575 249.32537461645592,182.74954867470245 249.32537461645592,182.74954867470245 L264.7796345081796,197.0 L284.6507492329117,197.0 Q292.6507492329117,197.0 292.6507492329117,189.0 L292.6507492329117,177.0 Q292.6507492329117,169.0 284.6507492329117,169.0 zM233.87111472473228,197.0 C207.82596959685134,221.01636024666215 158.0,266.9611391911356 158.0,266.9611391911356 L159.74714815776215,266.9611391911356 C159.67186270613888,267.01583727348003 159.63342870341367,267.04376121098136 159.63342870341367,267.04376121098136 L194.26837986487962,373.639180211494 L306.3494362207688,373.639180211494 L340.879924278726,267.3652655848922 L340.879924244638,267.3652655848922 C320.48822228455214,295.0431056988451 287.66651445119106,313.0 250.65074923291172,313.0 C213.4551337714681,313.0 180.4944304344295,294.8681857653931 160.12522111675239,266.9611391911356 L340.6507492329117,266.9611391911356 L264.7796345081796,197.0 z\" android:strokeColor=\"#ff4a90e2\" android:strokeWidth=\"3\" android:fillColor=\"#ff50e3c2\"/><path android:name=\"Oval 2\" android:pathData=\"M180.5,100.0 C201.76296286730002,100.0 219.0,82.7629628673 219.0,61.5 C219.0,40.237037132699996 201.76296286730002,23.0 180.5,23.0 C159.2370371327,23.0 142.0,40.237037132699996 142.0,61.5 C142.0,82.7629628673 159.2370371327,100.0 180.5,100.0 \" android:strokeColor=\"#ffd0021b\" android:strokeWidth=\"1\"/><path android:name=\"Oval 3\" android:pathData=\"M147.00000000000003,184.99999999999991 C160.80711874500003,184.99999999999991 172.00000000000006,173.80711874499994 172.00000000000006,159.99999999999994 C172.00000000000006,146.19288125499997 160.80711874500003,135.0 147.00000000000003,135.0 C133.192881255,135.0 122.0,146.19288125499997 122.0,159.99999999999994 C122.0,173.80711874499994 133.192881255,184.99999999999991 147.00000000000003,184.99999999999991 \" android:strokeColor=\"#ff9013fe\" android:strokeWidth=\"1\" android:fillColor=\"#ffd8d8d8\"/><path android:name=\"Oval 4\" android:pathData=\"M83.49999999999999,115.00000000000003 C92.06041362189998,115.00000000000003 98.99999999999997,108.06041362190001 98.99999999999997,99.50000000000001 C98.99999999999997,90.9395863781 92.06041362189998,84.0 83.49999999999999,84.0 C74.93958637809999,84.0 68.0,90.9395863781 68.0,99.50000000000001 C68.0,108.06041362190001 74.93958637809999,115.00000000000003 83.49999999999999,115.00000000000003 \" android:fillColor=\"#fff8e71c\"/><path android:name=\"Oval 5\" android:pathData=\"M60.5,195.99999999999994 C78.4492543685,195.99999999999994 93.0,181.44925436849996 93.0,163.49999999999997 C93.0,145.55074563149998 78.4492543685,131.0 60.5,131.0 C42.5507456315,131.0 28.0,145.55074563149998 28.0,163.49999999999997 C28.0,181.44925436849996 42.5507456315,195.99999999999994 60.5,195.99999999999994 \" android:strokeColor=\"#ff4a4a4a\" android:strokeWidth=\"1\" android:fillColor=\"#ff7ed321\"/><path android:name=\"Path 2\" android:pathData=\"M323.71349962496504,62.89255732215321 C323.71349962496504,62.89255732215321 308.2507957663141,39.035814225948975 282.6268865148355,46.98806192468371 C257.0029772633569,54.94030962341844 252.5850618751709,88.51646657363182 267.6059741950032,96.02692273354796 C282.6268865148355,103.53737889346412 327.2478319355137,100.44483812173397 317.5284180815046,131.37024583903573 C307.80900422749545,162.29565355633756 346.68665964353204,179.52552357026283 363.91652965745726,162.73744509515618 C381.1463996713826,145.94936662004943 406.7703089228611,119.00008275211499 377.1702758220151,86.30750887953882 C377.1702758220151,86.30750887953882 323.71349962496504,112.61109039361169 323.71349962496504,62.89255732215321 \" android:strokeColor=\"#ff979797\" android:strokeWidth=\"1\" android:fillColor=\"#ff000000\"/></vector>",
      file.getContent());
  }

  @Test
  public void shapeRotationTest() {
    SketchPage sketchPage = SketchParser.parsePage(getTestFilePath("/sketch/vectordrawable_shapeRotation.json"));
    SketchArtboard artboard = sketchPage.getArtboards().get(0);

    VectorDrawableFile vectorDrawableFile = new VectorDrawableFile(projectRule.getProject(), artboard);
    LightVirtualFile file = vectorDrawableFile.generateFile();

    assertEquals(
      "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:height=\"401.0dp\" android:width=\"401.0dp\" android:viewportHeight=\"401.0\" android:viewportWidth=\"401.0\"><path android:name=\"Combined Shape\" android:pathData=\"M197.4422909105832,94.27144913376834 L197.44229750845244,94.2714502971507 L197.44228726688192,94.27145402477751 C197.4422884741688,94.27145239709105 197.44228968145586,94.27145076940438 197.44229088874306,94.27144914171751 L197.4422909105832,94.27144913376834 zM89.26577889107907,109.45109265586046 L89.26577887927905,109.45109266015531 C89.26603244596673,109.45167763896502 103.28568202833199,141.79503472484245 107.6520201114035,151.86818462411557 L107.65202011050884,151.86818462411557 L114.08585902932035,115.3800709335112 L89.26577889107907,109.45109265586046 zM233.39379610774418,143.80768638596655 C235.8136370966711,143.80768638596655 237.8502116274566,144.42848561585654 239.4023476761837,145.73088240188042 C248.2869156118602,153.1859200798761 237.93434623640974,180.15057875573305 216.27923679080328,205.9581332355554 C201.95143371645207,223.03334402339578 186.26560825659038,235.32385569785998 174.71764597436868,239.52697023501747 C171.8611191070395,240.56666098810697 169.25777961067084,241.1115004783096 166.99081086286535,241.1115004783096 C164.57096987393845,241.1115004783096 162.53439534315297,240.4907012484196 160.98225929442586,239.1883044623957 C152.09769135874933,231.73326678440006 162.45026073419973,204.7686081085431 184.1053701798062,178.96105362872075 C198.43317325415734,161.88584284088037 214.11899871401909,149.5953311664162 225.66696099624085,145.3922166292587 C228.52348786357,144.35252587616918 231.1268273599387,143.80768638596655 233.39379610774418,143.80768638596655 zM218.1443775130561,66.36051414726914 L218.14437748849087,66.36051415621014 C218.14379209703847,66.36130339176329 204.91710627949408,84.1937651655301 197.44229089648312,94.27144913128222 L197.44229089648366,94.27144913128222 L120.20914992969651,80.65316257397738 L114.08585902932035,115.3800709335112 L137.36716787074994,120.94147015653138 L108.9343102693366,154.82643035073528 C108.9343102693366,154.82643035073528 108.45736714085075,153.72612168583981 107.65202105612278,151.86818680358468 C107.65202074355518,151.8681860824907 107.65202043098755,151.8681853613966 107.65202011841987,151.86818464030236 L107.65202010765468,151.86818464030236 L107.6520196350055,151.86818732082907 L85.65316257397734,276.62990542340674 L85.65316257397734,276.6299054234068 L281.62990542340674,311.18589277912594 L316.18589277912594,115.2091499296965 L228.05187354207555,99.66874441992563 L218.1443775130561,66.36051414726914 z\" android:strokeColor=\"#ff979797\" android:strokeWidth=\"1\" android:fillColor=\"#ffd8d8d8\"/></vector>",
      file.getContent());
  }

  @Test
  public void fillGradientTest() {
    SketchPage sketchPage = SketchParser.parsePage(getTestFilePath("/sketch/vectordrawable_fillGradient.json"));
    SketchArtboard sketchArtboard = sketchPage.getArtboards().get(0);

    VectorDrawableFile vectorDrawableFile = new VectorDrawableFile(projectRule.getProject(), sketchArtboard);
    LightVirtualFile file = vectorDrawableFile.generateFile();

    assertEquals(
      "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:height=\"401.0dp\" android:width=\"401.0dp\" android:viewportHeight=\"401.0\" android:viewportWidth=\"401.0\" xmlns:aapt=\"http://schemas.android.com/aapt\"><path android:name=\"Rectangle\" android:pathData=\"M24.0,64.0 L174.0,64.0 L174.0,214.0 L24.0,214.0 z\"><aapt:attr name = \"android:fillColor\"><gradient android:endX=\"99.0\" android:endY=\"214.0\" android:startX=\"99.0\" android:startY=\"64.0\" android:type=\"linear\"><item android:color=\"#ffb4ec51\" android:offset=\"0.0\"/><item android:color=\"#ff429321\" android:offset=\"1.0\"/></gradient></aapt:attr></path><path android:name=\"Oval\" android:pathData=\"M307.0,197.0 C341.7939392374,197.0 370.0,168.79393923740002 370.0,134.0 C370.0,99.2060607626 341.7939392374,71.0 307.0,71.0 C272.2060607626,71.0 244.0,99.2060607626 244.0,134.0 C244.0,168.79393923740002 272.2060607626,197.0 307.0,197.0 \" android:strokeColor=\"#ff979797\" android:strokeWidth=\"1\"><aapt:attr name = \"android:fillColor\"><gradient android:centerX=\"289.9834177102786\" android:centerY=\"158.24032699766252\" android:gradientRadius=\"85.4989153541967\" android:type=\"radial\"><item android:color=\"#ff3023ae\" android:offset=\"0.0\"/><item android:color=\"#ff53a0fd\" android:offset=\"0.6997698737109772\"/><item android:color=\"#ffb4ec51\" android:offset=\"1.0\"/></gradient></aapt:attr></path><path android:name=\"Rectangle 2\" android:pathData=\"M147.0,267.0 Q147.0,259.0 155.0,259.0 L279.0,259.0 Q287.0,259.0 287.0,267.0 L287.0,373.0 Q287.0,381.0 279.0,381.0 L155.0,381.0 Q147.0,381.0 147.0,373.0 z\" android:strokeColor=\"#ff979797\" android:strokeWidth=\"1\"><aapt:attr name = \"android:fillColor\"><gradient android:centerX=\"217.0\" android:centerY=\"320.0\" android:type=\"sweep\"><item android:color=\"#fff5515f\" android:offset=\"0.0\"/><item android:color=\"#ff9f041b\" android:offset=\"1.0\"/></gradient></aapt:attr></path><path android:name=\"Triangle\" android:pathData=\"M79.0,276.0 L112.0,344.0 L46.0,344.0 C46.0,344.0 79.0,276.0 79.0,276.0 \" android:strokeColor=\"#ff979797\" android:strokeWidth=\"1\" android:fillColor=\"#ff50e3c2\"/></vector>",
      file.getContent());
  }

  @Test
  public void shapeMirroringTest() {
    SketchPage sketchPage = SketchParser.parsePage(getTestFilePath("/sketch/vectordrawable_shapeMirroring.json"));
    SketchArtboard artboard = sketchPage.getArtboards().get(0);

    VectorDrawableFile vectorDrawableFile = new VectorDrawableFile(projectRule.getProject(), artboard);
    LightVirtualFile file = vectorDrawableFile.generateFile();

    assertEquals(
      "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:height=\"401.0dp\" android:width=\"401.0dp\" android:viewportHeight=\"401.0\" android:viewportWidth=\"401.0\"><path android:name=\"Combined Shape\" android:pathData=\"M246.99999998098792,180.00000001760017 L137.42979500396802,281.43380363494043 L82.6446925059521,246.17606440354405 C82.6446925059521,246.17606440354405 246.99690500193742,180.00124617083705 246.99999995628798,180.00000001760017 zM75.0,123.0 C75.0,123.0 75.0,289.8233618233618 75.0,289.8233618233618 L313.9999999999999,289.8233618233618 L222.75783475783467,123.0 z\" android:strokeColor=\"#ff979797\" android:strokeWidth=\"1\" android:fillColor=\"#ffd8d8d8\"/></vector>",
      file.getContent());
  }

  private static String getTestFilePath(String file) {
    return AndroidTestBase.getTestDataPath() + file;
  }
}


//LightVirtualFile virtualFile = new LightVirtualFile();
//virtualFile.setContent();
//new VectorDrawableAssetRenderer().getImage(virtualFile, rule.module, new Dimension(10,10));
//ImageDiffUtil.assertImageSimilar();
//String resultingFilePath = getTestFilePath("/sketch/generated/test.xml");
//vectorDrawableFile.saveDrawableToDisk(resultingFilePath);
