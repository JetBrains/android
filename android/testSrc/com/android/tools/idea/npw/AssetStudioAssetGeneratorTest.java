/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.npw;

import com.android.assetstudiolib.*;
import com.android.ide.common.util.AssetUtil;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.wizard.WizardConstants;
import com.android.tools.idea.wizard.template.TemplateWizardState;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.Nullable;
import org.mockito.ArgumentCaptor;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;

import static com.android.assetstudiolib.BitmapGeneratorTest.assertImageSimilar;
import static com.android.tools.idea.npw.AssetStudioAssetGenerator.*;
import static org.mockito.Mockito.*;

/**
 * Tests for generation of asset images.
 */
@SuppressWarnings("unchecked")
public class AssetStudioAssetGeneratorTest extends AndroidTestCase {

  private NotificationIconGenerator myNotificationIconGenerator;
  private ActionBarIconGenerator myActionBarIconGenerator;
  private LauncherIconGenerator myLauncherIconGenerator;
  private VectorIconGenerator myVectorIconGenerator;
  private TemplateWizardState myState = new TemplateWizardState();
  private AssetStudioAssetGenerator myAssetGenerator;
  private static final String ASSET_NAME = "ThisIsTheFileName";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myNotificationIconGenerator = mock(NotificationIconGenerator.class);
    myActionBarIconGenerator = mock(ActionBarIconGenerator.class);
    myLauncherIconGenerator = mock(LauncherIconGenerator.class);
    myVectorIconGenerator = mock(VectorIconGenerator.class);
    myAssetGenerator = new AssetStudioAssetGenerator(new TemplateWizardContextAdapter(myState), myActionBarIconGenerator,
                                                     myNotificationIconGenerator, myLauncherIconGenerator,
                                                     myVectorIconGenerator);
    pickImage(myState);
    myState.put(ATTR_ASSET_NAME, ASSET_NAME);
  }

  private static void pickImage(TemplateWizardState state) {
    // This is no longer done by the asset state by default, but by the
    // RasterAssetSetStep#initialize method
    state.put(ATTR_IMAGE_PATH, new File(TemplateManager.getTemplateRootFolder(), FileUtil
      .join(Template.CATEGORY_PROJECTS, WizardConstants.MODULE_TEMPLATE_NAME, "root", "res", "mipmap-xhdpi", "ic_launcher.png"))
      .getAbsolutePath());
  }

  public void testNoOp() throws Exception {
    myAssetGenerator.generateImages(true);
    verify(myNotificationIconGenerator, never())
      .generate(any(GraphicGeneratorContext.class), any(GraphicGenerator.Options.class));
    verify(myNotificationIconGenerator, never())
      .generate(anyString(), any(Map.class),
                any(GraphicGeneratorContext.class), any(GraphicGenerator.Options.class), anyString());

    verify(myActionBarIconGenerator, never())
      .generate(any(GraphicGeneratorContext.class), any(GraphicGenerator.Options.class));
    verify(myActionBarIconGenerator, never())
      .generate(anyString(), any(Map.class),
                any(GraphicGeneratorContext.class), any(GraphicGenerator.Options.class), anyString());

    verify(myLauncherIconGenerator, never())
      .generate(any(GraphicGeneratorContext.class), any(GraphicGenerator.Options.class));
    verify(myLauncherIconGenerator, never())
      .generate(anyString(), any(Map.class),
                any(GraphicGeneratorContext.class), any(GraphicGenerator.Options.class), anyString());

    verify(myVectorIconGenerator, never())
      .generate(any(GraphicGeneratorContext.class), any(GraphicGenerator.Options.class));
    verify(myVectorIconGenerator, never())
      .generate(anyString(), any(Map.class),
                any(GraphicGeneratorContext.class), any(GraphicGenerator.Options.class), anyString());
  }

  public void testLauncherIcons() throws Exception {
    myState.put(ATTR_ASSET_TYPE, AssetType.LAUNCHER.name());
    myAssetGenerator.generateImages(true);

    verify(myNotificationIconGenerator, never())
      .generate(any(GraphicGeneratorContext.class), any(GraphicGenerator.Options.class));
    verify(myNotificationIconGenerator, never())
      .generate(anyString(), any(Map.class),
                any(GraphicGeneratorContext.class), any(GraphicGenerator.Options.class), anyString());

    verify(myActionBarIconGenerator, never())
      .generate(any(GraphicGeneratorContext.class), any(GraphicGenerator.Options.class));
    verify(myActionBarIconGenerator, never())
      .generate(anyString(), any(Map.class),
                any(GraphicGeneratorContext.class), any(GraphicGenerator.Options.class), anyString());

    verify(myLauncherIconGenerator, never())
      .generate(eq(myAssetGenerator), any(LauncherIconGenerator.LauncherOptions.class));
    verify(myLauncherIconGenerator, times(1))
      .generate(isNull(String.class), any(Map.class), eq(myAssetGenerator),
                any(LauncherIconGenerator.LauncherOptions.class), eq(ASSET_NAME));

    verify(myVectorIconGenerator, never())
      .generate(any(GraphicGeneratorContext.class), any(GraphicGenerator.Options.class));
    verify(myVectorIconGenerator, never())
      .generate(anyString(), any(Map.class),
                any(GraphicGeneratorContext.class), any(GraphicGenerator.Options.class), anyString());
  }

  public void testNotificationIcons() throws Exception {
    myState.put(ATTR_ASSET_TYPE, AssetType.NOTIFICATION.name());
    myAssetGenerator.generateImages(true);

    verify(myNotificationIconGenerator, never())
      .generate(eq(myAssetGenerator), any(NotificationIconGenerator.NotificationOptions.class));
    verify(myNotificationIconGenerator, times(1))
      .generate(isNull(String.class), any(Map.class), eq(myAssetGenerator),
                any(NotificationIconGenerator.NotificationOptions.class), eq(ASSET_NAME));

    verify(myActionBarIconGenerator, never())
      .generate(any(GraphicGeneratorContext.class), any(GraphicGenerator.Options.class));
    verify(myActionBarIconGenerator, never())
      .generate(anyString(), any(Map.class),
                any(GraphicGeneratorContext.class), any(GraphicGenerator.Options.class), anyString());

    verify(myLauncherIconGenerator, never())
      .generate(any(GraphicGeneratorContext.class), any(GraphicGenerator.Options.class));
    verify(myLauncherIconGenerator, never())
      .generate(anyString(), any(Map.class),
                any(GraphicGeneratorContext.class), any(GraphicGenerator.Options.class), anyString());

    verify(myVectorIconGenerator, never())
      .generate(any(GraphicGeneratorContext.class), any(GraphicGenerator.Options.class));
    verify(myVectorIconGenerator, never())
      .generate(anyString(), any(Map.class),
                any(GraphicGeneratorContext.class), any(GraphicGenerator.Options.class), anyString());
  }

  public void testActionBarIcons() throws Exception {
    myState.put(ATTR_ASSET_TYPE, AssetType.ACTIONBAR.name());
    myAssetGenerator.generateImages(true);

    verify(myNotificationIconGenerator, never())
      .generate(any(GraphicGeneratorContext.class), any(GraphicGenerator.Options.class));
    verify(myNotificationIconGenerator, never())
      .generate(anyString(), any(Map.class),
                any(GraphicGeneratorContext.class), any(GraphicGenerator.Options.class), anyString());

    verify(myActionBarIconGenerator, never())
      .generate(eq(myAssetGenerator), any(ActionBarIconGenerator.ActionBarOptions.class));
    verify(myActionBarIconGenerator, times(1))
      .generate(isNull(String.class), any(Map.class), eq(myAssetGenerator),
                any(ActionBarIconGenerator.ActionBarOptions.class), eq(ASSET_NAME));

    verify(myLauncherIconGenerator, never())
      .generate(any(GraphicGeneratorContext.class), any(GraphicGenerator.Options.class));
    verify(myLauncherIconGenerator, never())
      .generate(anyString(), any(Map.class),
                any(GraphicGeneratorContext.class), any(GraphicGenerator.Options.class), anyString());

    verify(myVectorIconGenerator, never())
      .generate(any(GraphicGeneratorContext.class), any(GraphicGenerator.Options.class));
    verify(myVectorIconGenerator, never())
      .generate(anyString(), any(Map.class),
                any(GraphicGeneratorContext.class), any(GraphicGenerator.Options.class), anyString());
  }

  public void testVectorIcons() throws Exception {
    myState.put(ATTR_SOURCE_TYPE, SourceType.SVG);
    myState.put(ATTR_ASSET_TYPE, AssetType.ACTIONBAR.name());
    myState.put(ATTR_IMAGE_PATH, new File(TemplateManager.getTemplateRootFolder(), FileUtil
      .join(Template.CATEGORY_PROJECTS, WizardConstants.MODULE_TEMPLATE_NAME, "root", "res", "mipmap-anydpi", "test.svg"))
      .getAbsolutePath());
    myState.put(ATTR_VECTOR_DRAWBLE_WIDTH, "24");
    myState.put(ATTR_VECTOR_DRAWBLE_HEIGHT, "24");
    myState.put(ATTR_VECTOR_DRAWBLE_OPACTITY, 100);
    myState.put(ATTR_VECTOR_DRAWBLE_AUTO_MIRRORED, false);

    myAssetGenerator.generateImages(true);

    verify(myNotificationIconGenerator, never())
      .generate(any(GraphicGeneratorContext.class), any(GraphicGenerator.Options.class));
    verify(myNotificationIconGenerator, never())
      .generate(anyString(), any(Map.class), any(GraphicGeneratorContext.class),
                any(GraphicGenerator.Options.class), anyString());

    verify(myActionBarIconGenerator, never())
      .generate(any(GraphicGeneratorContext.class), any(GraphicGenerator.Options.class));
    verify(myActionBarIconGenerator, never())
      .generate(anyString(), any(Map.class),
                any(GraphicGeneratorContext.class), any(GraphicGenerator.Options.class), anyString());

    verify(myLauncherIconGenerator, never())
      .generate(any(GraphicGeneratorContext.class), any(GraphicGenerator.Options.class));
    verify(myLauncherIconGenerator, never())
      .generate(anyString(), any(Map.class),
                any(GraphicGeneratorContext.class), any(GraphicGenerator.Options.class), anyString());

    verify(myVectorIconGenerator, never())
      .generate(any(GraphicGeneratorContext.class), any(GraphicGenerator.Options.class));
    verify(myVectorIconGenerator, times(1))
      .generate(isNull(String.class), any(Map.class),
                eq(myAssetGenerator), any(VectorIconGenerator.Options.class), eq(ASSET_NAME));
  }

  public void testThemes() throws Exception {
    assertThemeUsed(ActionBarIconGenerator.Theme.HOLO_DARK, null);
    assertThemeUsed(ActionBarIconGenerator.Theme.HOLO_LIGHT, null);
    assertThemeUsed(ActionBarIconGenerator.Theme.CUSTOM, Color.MAGENTA);
  }

  private static void assertThemeUsed(ActionBarIconGenerator.Theme theme,
                                      @Nullable Color color) throws Exception {
    ArgumentCaptor<ActionBarIconGenerator.ActionBarOptions> argument =
      ArgumentCaptor.forClass(ActionBarIconGenerator.ActionBarOptions.class);

    ActionBarIconGenerator generator = mock(ActionBarIconGenerator.class);

    TemplateWizardState state = new TemplateWizardState();
    AssetStudioAssetGenerator studioGenerator = new AssetStudioAssetGenerator(new TemplateWizardContextAdapter(state),
                                                                              generator, null, null, null);
    pickImage(state);
    state.put(ATTR_ASSET_TYPE, AssetType.ACTIONBAR.name());
    state.put(ATTR_ASSET_THEME, theme.name());
    state.put(ATTR_FOREGROUND_COLOR, color);
    studioGenerator.generateImages(true);

    verify(generator, times(1))
      .generate(isNull(String.class), any(Map.class), eq(studioGenerator),
                argument.capture(), anyString());

    assertEquals(theme, argument.getValue().theme);

    if (color != null && theme.equals(ActionBarIconGenerator.Theme.CUSTOM)) {
      assertEquals(color.getRGB(), argument.getValue().customThemeColor);
    }
  }

  private ArgumentCaptor<ActionBarIconGenerator.ActionBarOptions> runImageTest() throws Exception {
    ArgumentCaptor<ActionBarIconGenerator.ActionBarOptions> argument =
      ArgumentCaptor.forClass(ActionBarIconGenerator.ActionBarOptions.class);
    myState.put(ATTR_ASSET_TYPE, AssetType.ACTIONBAR.name());
    myState.put(ATTR_ASSET_THEME, ActionBarIconGenerator.Theme.HOLO_DARK.name());

    myAssetGenerator.generateImages(true);

    verify(myActionBarIconGenerator, times(1))
      .generate(isNull(String.class), any(Map.class), eq(myAssetGenerator),
                argument.capture(), anyString());

    return argument;
  }

  @SuppressWarnings("UndesirableClassUsage")
  private static void assertImagesSimilar(String name, BufferedImage expected, BufferedImage actual, float allowedDifference) throws Exception {
    BufferedImage convertedExpected = new BufferedImage(expected.getWidth(), expected.getHeight(), BufferedImage.TYPE_INT_ARGB);
    convertedExpected.getGraphics().drawImage(expected, 0, 0, null);
    BufferedImage convertedActual = new BufferedImage(actual.getWidth(), actual.getHeight(), BufferedImage.TYPE_INT_ARGB);
    convertedActual.getGraphics().drawImage(actual, 0, 0, null);
    assertImageSimilar(name, convertedExpected, convertedActual, allowedDifference);
  }

  public void testClipartSource() throws Exception {
    myState.put(ATTR_SOURCE_TYPE, SourceType.CLIPART);

    ArgumentCaptor<ActionBarIconGenerator.ActionBarOptions> argument = runImageTest();

    BufferedImage expectedImage = GraphicGenerator.getClipartImage(myState.getString(ATTR_CLIPART_NAME));
    assertImagesSimilar("ClipartImage", expectedImage, argument.getValue().sourceImage, 5.0f);
  }

  public void testTextSource() throws Exception {
    myState.put(ATTR_SOURCE_TYPE, SourceType.TEXT);

    ArgumentCaptor<ActionBarIconGenerator.ActionBarOptions> argument = runImageTest();

    TextRenderUtil.Options options = new TextRenderUtil.Options();
    options.font = Font.decode(myState.getString(ATTR_FONT) + " " + myState.getInt(ATTR_FONT_SIZE));
    options.foregroundColor = 0xFFFFFFFF;
    BufferedImage expectedImage = TextRenderUtil.renderTextImage(myState.getString(ATTR_TEXT), 1, options);

    assertImagesSimilar("TextImage", expectedImage, argument.getValue().sourceImage, 5.0f);
  }

  public void testImageSource() throws Exception {
    myState.put(ATTR_SOURCE_TYPE, SourceType.IMAGE);

    ArgumentCaptor<ActionBarIconGenerator.ActionBarOptions> argument = runImageTest();

    BufferedImage expectedImage = getImage(myState.getString(ATTR_IMAGE_PATH), false);

    assertImagesSimilar("ImageImage", expectedImage, argument.getValue().sourceImage, 5.0f);
  }

  public void testTrim() throws Exception {
    myState.put(ATTR_TRIM, true);
    myState.put(ATTR_SOURCE_TYPE, SourceType.IMAGE);

    ArgumentCaptor<ActionBarIconGenerator.ActionBarOptions> argument = runImageTest();

    BufferedImage expectedImage = crop(getImage(myState.getString(ATTR_IMAGE_PATH), false));

    assertImagesSimilar("TrimmedImage", expectedImage, argument.getValue().sourceImage, 5.0f);
  }

  public void testPadding() throws Exception {
    myState.put(ATTR_PADDING, 50);
    myState.put(ATTR_SOURCE_TYPE, SourceType.IMAGE);

    ArgumentCaptor<ActionBarIconGenerator.ActionBarOptions> argument = runImageTest();

    BufferedImage expectedImage = AssetUtil
      .paddedImage(getImage(myState.getString(ATTR_IMAGE_PATH), false), 50);

    assertImagesSimilar("PaddedImage", expectedImage, argument.getValue().sourceImage, 5.0f);
  }
}
