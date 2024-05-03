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
 * Tests for {@link TvChannelIconGenerator}.
 */
public class TvChannelIconGeneratorTest extends AdaptiveIconGeneratorTest {
  private TvChannelIconGenerator myIconGenerator;

  @Override
  protected AdaptiveIconGenerator getIconGenerator() {
    return myIconGenerator;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    DrawableRenderer renderer = new DrawableRenderer(myFacet, myFixture.findFileInTempDir("res"));
    myIconGenerator = new TvChannelIconGenerator(getProject(), 15, renderer);
    disposeOnTearDown(myIconGenerator);
    disposeOnTearDown(renderer);
    myIconGenerator.outputName().set("ic_channel");
    myIconGenerator.foregroundLayerName().set("ic_channel_foreground");
    myIconGenerator.backgroundLayerName().set("ic_channel_background");
  }

  public void testDrawableBackgroundAndForeground() throws Exception {
    String[] expectedFilenames = {
        "resources/mipmap-anydpi-v26/ic_channel.xml",
        "resources/drawable/ic_channel_background.xml",
        "resources/drawable-v24/ic_channel_foreground.xml",
        "resources/mipmap-xhdpi/ic_channel.png"};
    myIconGenerator.sourceAsset().setValue(createImageAsset("foreground.xml"));
    myIconGenerator.backgroundImageAsset().setValue(createImageAsset("background.xml"));
    checkGeneratedIcons(expectedFilenames);
  }

  public void testSingleLineText() throws Exception {
    String[] expectedFilenames = {
        "resources/mipmap-anydpi-v26/ic_channel.xml",
        "resources/drawable/ic_channel_foreground.xml",
        "resources/mipmap-xhdpi/ic_channel.png",
        "resources/values/ic_channel_background.xml"};
    TextAsset textAsset = new TextAsset();
    textAsset.text().set("AL");
    textAsset.fontFamily().set("Droid Sans");
    textAsset.color().setValue(new Color(0x0000FF));
    myIconGenerator.sourceAsset().setValue(textAsset);
    myIconGenerator.backgroundImageAsset().setNullableValue(null);
    myIconGenerator.backgroundColor().set(new Color(0xFFFFFF));
    // Don't compare context of ic_channel_foreground.xml because it is slightly platform dependent.
    checkGeneratedIcons(expectedFilenames, "resources/drawable/ic_channel_foreground.xml");
  }

  public void testMultiLineText() throws Exception {
    String[] expectedFilenames = {
        "resources/mipmap-anydpi-v26/ic_channel.xml",
        "resources/drawable/ic_channel_foreground.xml",
        "resources/mipmap-xhdpi/ic_channel.png",
        "resources/values/ic_channel_background.xml"};
    TextAsset textAsset = new TextAsset();
    textAsset.text().set("A\nW");
    textAsset.fontFamily().set("Droid Sans");
    textAsset.color().setValue(new Color(0x0000FF));
    myIconGenerator.sourceAsset().setValue(textAsset);
    myIconGenerator.backgroundImageAsset().setNullableValue(null);
    myIconGenerator.backgroundColor().set(new Color(0xFFFFFF));
    // Don't compare context of ic_channel_foreground.xml because it is slightly platform dependent.
    checkGeneratedIcons(expectedFilenames, "resources/drawable/ic_channel_foreground.xml");
  }

  public void testImageBackgroundAndForeground() throws Exception {
    String[] expectedFilenames = {
        "resources/mipmap-anydpi-v26/ic_channel.xml",
        "resources/mipmap-xhdpi/ic_channel.png",
        "resources/mipmap-xhdpi/ic_channel_background.png",
        "resources/mipmap-xhdpi/ic_channel_foreground.png"};
    myIconGenerator.sourceAsset().setValue(createImageAsset("foreground.png"));
    myIconGenerator.backgroundImageAsset().setValue(createImageAsset("background.png"));
    checkGeneratedIcons(expectedFilenames);
  }

  public void testClipart() throws Exception {
    String[] expectedFilenames = {
        "resources/mipmap-anydpi-v26/ic_channel.xml",
        "resources/drawable/ic_channel_foreground.xml",
        "resources/mipmap-xhdpi/ic_channel.png",
        "resources/values/ic_channel_background.xml"};
    ImageAsset asset = createClipartAsset("ic_android_black_24dp.xml");
    asset.color().setValue(new Color(0x00FF00));
    asset.trimmed().set(true);
    myIconGenerator.sourceAsset().setValue(asset);
    myIconGenerator.backgroundImageAsset().setNullableValue(null);
    myIconGenerator.backgroundColor().set(new Color(0xFFFFFF));
    checkGeneratedIcons(expectedFilenames);
  }
}
