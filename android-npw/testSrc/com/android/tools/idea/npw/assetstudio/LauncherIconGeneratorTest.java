/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.npw.assetstudio;

import com.android.tools.idea.npw.assetstudio.assets.ImageAsset;
import com.android.tools.idea.npw.assetstudio.assets.TextAsset;
import com.android.tools.idea.rendering.DrawableRenderer;
import java.awt.Color;

/**
 * Tests for {@link LauncherIconGenerator}.
 */
public class LauncherIconGeneratorTest extends AdaptiveIconGeneratorTest {
  private LauncherIconGenerator myIconGenerator;

  @Override
  protected AdaptiveIconGenerator getIconGenerator() {
    return myIconGenerator;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    DrawableRenderer renderer = new DrawableRenderer(myFacet);
    myIconGenerator = new LauncherIconGenerator(getProject(), 15, renderer);
    disposeOnTearDown(myIconGenerator);
    disposeOnTearDown(renderer);
    myIconGenerator.outputName().set("ic_launcher");
    myIconGenerator.foregroundLayerName().set("ic_launcher_foreground");
    myIconGenerator.backgroundLayerName().set("ic_launcher_background");
    myIconGenerator.generateWebpIcons().set(false);
  }

  public void testDrawableBackgroundAndForeground() throws Exception {
    String[] expectedFilenames = {
        "resources/mipmap-anydpi-v26/ic_launcher.xml",
        "resources/mipmap-anydpi-v26/ic_launcher_round.xml",
        "resources/drawable/ic_launcher_background.xml",
        "resources/drawable-v24/ic_launcher_foreground.xml",
        "resources/mipmap-xxxhdpi/ic_launcher.png",
        "resources/mipmap-xxxhdpi/ic_launcher_round.png",
        "resources/mipmap-xxhdpi/ic_launcher.png",
        "resources/mipmap-xxhdpi/ic_launcher_round.png",
        "resources/mipmap-xhdpi/ic_launcher.png",
        "resources/mipmap-xhdpi/ic_launcher_round.png",
        "resources/mipmap-hdpi/ic_launcher.png",
        "resources/mipmap-hdpi/ic_launcher_round.png",
        "resources/mipmap-mdpi/ic_launcher.png",
        "resources/mipmap-mdpi/ic_launcher_round.png",
        "manifests/ic_launcher-playstore.png"};
    myIconGenerator.sourceAsset().setValue(createImageAsset("foreground.xml"));
    myIconGenerator.backgroundImageAsset().setValue(createImageAsset("background.xml"));
    checkGeneratedIcons(expectedFilenames);
  }

  public void testSingleLineText() throws Exception {
    String[] expectedFilenames = {
        "resources/mipmap-anydpi-v26/ic_launcher.xml",
        "resources/drawable/ic_launcher_foreground.xml",
        "resources/mipmap-xxxhdpi/ic_launcher.png",
        "resources/mipmap-xxhdpi/ic_launcher.png",
        "resources/mipmap-xhdpi/ic_launcher.png",
        "resources/mipmap-hdpi/ic_launcher.png",
        "resources/mipmap-mdpi/ic_launcher.png",
        "resources/values/ic_launcher_background.xml",
        "manifests/ic_launcher-playstore.png"};
    TextAsset textAsset = new TextAsset();
    textAsset.text().set("AL");
    textAsset.fontFamily().set("Droid Sans");
    textAsset.color().setValue(new Color(0x0000FF));
    myIconGenerator.sourceAsset().setValue(textAsset);
    myIconGenerator.backgroundImageAsset().setNullableValue(null);
    myIconGenerator.backgroundColor().set(new Color(0xFFFFFF));
    myIconGenerator.generateRoundIcon().set(false);
    // Don't compare context of ic_launcher_foreground.xml because it is slightly platform dependent.
    checkGeneratedIcons(expectedFilenames, "resources/drawable/ic_launcher_foreground.xml");
  }

  public void testMultiLineText() throws Exception {
    String[] expectedFilenames = {
        "resources/mipmap-anydpi-v26/ic_launcher.xml",
        "resources/drawable/ic_launcher_foreground.xml",
        "resources/mipmap-xxxhdpi/ic_launcher.png",
        "resources/mipmap-xxhdpi/ic_launcher.png",
        "resources/mipmap-xhdpi/ic_launcher.png",
        "resources/mipmap-hdpi/ic_launcher.png",
        "resources/mipmap-mdpi/ic_launcher.png",
        "resources/values/ic_launcher_background.xml",
        "manifests/ic_launcher-playstore.png"};
    TextAsset textAsset = new TextAsset();
    textAsset.text().set("A\nW");
    textAsset.fontFamily().set("Droid Sans");
    textAsset.color().setValue(new Color(0x0000FF));
    myIconGenerator.sourceAsset().setValue(textAsset);
    myIconGenerator.backgroundImageAsset().setNullableValue(null);
    myIconGenerator.backgroundColor().set(new Color(0xFFFFFF));
    myIconGenerator.generateRoundIcon().set(false);
    // Don't compare context of ic_launcher_foreground.xml because it is slightly platform dependent.
    checkGeneratedIcons(expectedFilenames, "resources/drawable/ic_launcher_foreground.xml");
  }

  public void testImageBackgroundAndForeground() throws Exception {
    String[] expectedFilenames = {
        "resources/mipmap-anydpi-v26/ic_launcher.xml",
        "resources/mipmap-anydpi-v26/ic_launcher_round.xml",
        "resources/mipmap-xxxhdpi/ic_launcher.png",
        "resources/mipmap-xxxhdpi/ic_launcher_background.png",
        "resources/mipmap-xxxhdpi/ic_launcher_foreground.png",
        "resources/mipmap-xxxhdpi/ic_launcher_round.png",
        "resources/mipmap-xxhdpi/ic_launcher.png",
        "resources/mipmap-xxhdpi/ic_launcher_background.png",
        "resources/mipmap-xxhdpi/ic_launcher_foreground.png",
        "resources/mipmap-xxhdpi/ic_launcher_round.png",
        "resources/mipmap-xhdpi/ic_launcher.png",
        "resources/mipmap-xhdpi/ic_launcher_background.png",
        "resources/mipmap-xhdpi/ic_launcher_foreground.png",
        "resources/mipmap-xhdpi/ic_launcher_round.png",
        "resources/mipmap-hdpi/ic_launcher.png",
        "resources/mipmap-hdpi/ic_launcher_background.png",
        "resources/mipmap-hdpi/ic_launcher_foreground.png",
        "resources/mipmap-hdpi/ic_launcher_round.png",
        "resources/mipmap-mdpi/ic_launcher.png",
        "resources/mipmap-mdpi/ic_launcher_background.png",
        "resources/mipmap-mdpi/ic_launcher_foreground.png",
        "resources/mipmap-mdpi/ic_launcher_round.png",
        "manifests/ic_launcher-playstore.png"};
    myIconGenerator.sourceAsset().setValue(createImageAsset("foreground.png"));
    myIconGenerator.backgroundImageAsset().setValue(createImageAsset("background.png"));
    checkGeneratedIcons(expectedFilenames);
  }

  public void testClipart() throws Exception {
    String[] expectedFilenames = {
        "resources/mipmap-anydpi-v26/ic_launcher.xml",
        "resources/drawable/ic_launcher_foreground.xml",
        "resources/mipmap-xxxhdpi/ic_launcher.png",
        "resources/mipmap-xxhdpi/ic_launcher.png",
        "resources/mipmap-xhdpi/ic_launcher.png",
        "resources/mipmap-hdpi/ic_launcher.png",
        "resources/mipmap-mdpi/ic_launcher.png",
        "resources/values/ic_launcher_background.xml",
        "manifests/ic_launcher-playstore.png"};
    ImageAsset asset = createClipartAsset("ic_android_black_24dp.xml");
    asset.color().setValue(new Color(0x00FF00));
    asset.trimmed().set(true);
    myIconGenerator.sourceAsset().setValue(asset);
    myIconGenerator.backgroundImageAsset().setNullableValue(null);
    //noinspection UseJBColor
    myIconGenerator.backgroundColor().set(new Color(0xFFFFFF));
    myIconGenerator.generateRoundIcon().set(false);
    myIconGenerator.legacyIconShape().set(IconGenerator.Shape.VRECT);
    checkGeneratedIcons(expectedFilenames);
  }
}
