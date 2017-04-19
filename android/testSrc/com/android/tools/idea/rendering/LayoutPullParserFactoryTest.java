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
package com.android.tools.idea.rendering;

import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.tools.idea.fonts.FontDetail;
import com.android.tools.idea.fonts.FontFamily;
import com.android.tools.idea.fonts.GoogleFontProvider;
import com.android.utils.SdkUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;

import java.io.File;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("SpellCheckingInspection")
public class LayoutPullParserFactoryTest extends RenderTestBase {
  @SuppressWarnings("ConstantConditions")
  public void testIsSupported() throws Exception {
    VirtualFile layoutFile = myFixture.copyFileToProject("xmlpull/layout.xml", "res/layout-land-v14/foo.xml");
    VirtualFile menuFile = myFixture.copyFileToProject("menus/menu1.xml", "res/menu/menu1.xml");
    VirtualFile drawableFile = myFixture.copyFileToProject("menus/menu1.xml", "res/menu/menu1.xml");

    PsiManager psiManager = PsiManager.getInstance(getProject());

    assertTrue(LayoutPullParserFactory.isSupported(psiManager.findFile(layoutFile)));
    assertTrue(LayoutPullParserFactory.isSupported(psiManager.findFile(menuFile)));
    assertTrue(LayoutPullParserFactory.isSupported(psiManager.findFile(drawableFile)));
  }

  public void testRenderDrawable() throws Exception {
    VirtualFile file = myFixture.copyFileToProject("drawables/progress_horizontal.xml", "res/drawable/progress_horizontal.xml");
    assertNotNull(file);
    RenderTask task = createRenderTask(file);
    assertNotNull(task);
    ILayoutPullParser parser = LayoutPullParserFactory.create(task);
    assertTrue(parser instanceof DomPullParser);
    Element root = ((DomPullParser)parser).getRoot();

    String actualLayout = XmlPrettyPrinter.prettyPrint(root, true);
    String expectedLayout = Joiner.on(SdkUtils.getLineSeparator()).join(
      "<ImageView",
      "xmlns:android=\"http://schemas.android.com/apk/res/android\"",
      "layout_width=\"fill_parent\"",
      "layout_height=\"fill_parent\"",
      "src=\"@drawable/progress_horizontal\" />",
      ""
    );

    assertEquals(expectedLayout, actualLayout);

    checkRendering(task, "drawable/progress_horizontal.png");
  }

  public void testRenderAdaptiveIcon() throws Exception {
    // TODO: Replace the drawable with an actual adaptive-icon (see TODO below)
    VirtualFile file = myFixture.copyFileToProject("drawables/progress_horizontal.xml", "res/mipmap/adaptive.xml");
    assertNotNull(file);
    RenderTask task = createRenderTask(file);
    assertNotNull(task);
    ILayoutPullParser parser = LayoutPullParserFactory.create(task);
    assertTrue(parser instanceof DomPullParser);
    Element root = ((DomPullParser)parser).getRoot();

    String actualLayout = XmlPrettyPrinter.prettyPrint(root, true);
    String expectedLayout = Joiner.on(SdkUtils.getLineSeparator()).join(
      "<ImageView",
      "xmlns:android=\"http://schemas.android.com/apk/res/android\"",
      "layout_width=\"fill_parent\"",
      "layout_height=\"fill_parent\"",
      "src=\"@mipmap/adaptive\" />",
      ""
    );

    assertEquals(expectedLayout, actualLayout);

    // TODO: Create the golden image once layoutlib adaptive-icon rendering is merged
    //checkRendering(task, "mipmap/adaptive.png");
  }


  public void testFontFamily() throws Exception {
    myFixture.copyFileToProject("fonts/customfont.ttf", "res/font/fonta.ttf");
    myFixture.copyFileToProject("fonts/customfont.ttf", "res/font/fontb.ttf");
    VirtualFile file = myFixture.copyFileToProject("fonts/my_font_family.xml", "res/font/my_font_family.xml");
    assertNotNull(file);
    RenderTask task = createRenderTask(file);
    assertNotNull(task);
    ILayoutPullParser parser = LayoutPullParserFactory.create(task);
    assertTrue(parser instanceof DomPullParser);
    Element root = ((DomPullParser)parser).getRoot();

    String actualLayout = XmlPrettyPrinter.prettyPrint(root, true);
    String labelColor = "#" + ColorUtil.toHex(UIUtil.getLabelForeground());
    String expectedLayout = Joiner.on(SdkUtils.getLineSeparator()).join(
      "<LinearLayout",
      "xmlns:android=\"http://schemas.android.com/apk/res/android\"",
      "layout_width=\"fill_parent\"",
      "layout_height=\"wrap_content\"",
      "orientation=\"vertical\" >",
      "<TextView",
      "    layout_width=\"wrap_content\"",
      "    layout_height=\"wrap_content\"",
      "    fontFamily=\"@font/my_font_family\"",
      "    paddingBottom=\"20dp\"",
      "    text=\"Lorem ipsum dolor sit amet, consectetur adipisicing elit.\"",
      "    textColor=\"" + labelColor + "\"",
      "    textSize=\"30sp\"",
      "    textStyle=\"normal\" />",
      "<TextView",
      "    layout_width=\"wrap_content\"",
      "    layout_height=\"wrap_content\"",
      "    fontFamily=\"@font/my_font_family\"",
      "    paddingBottom=\"20dp\"",
      "    text=\"Lorem ipsum dolor sit amet, consectetur adipisicing elit.\"",
      "    textColor=\"" + labelColor + "\"",
      "    textSize=\"30sp\"",
      "    textStyle=\"italic\" />",
      "</LinearLayout>",
      "");

    assertEquals(expectedLayout, actualLayout);

    checkRendering(task, "fonts/fontFamily.png");
  }

  @NotNull
  private static FontFamily createMockFontFamily(boolean fileExists) {
    File fileMock = mock(File.class);
    when(fileMock.exists()).thenReturn(fileExists);
    FontDetail fontDetail = mock(FontDetail.class);
    when(fontDetail.isItalics()).thenReturn(true);
    when(fontDetail.getStyleName()).thenReturn("Italic");
    when(fontDetail.getFontStyle()).thenReturn("italic");
    when(fontDetail.getCachedFontFile()).thenReturn(fileMock);
    return FontFamily.createCompound(GoogleFontProvider.INSTANCE,
                                     FontFamily.FontSource.DOWNLOADABLE,
                                     "myFont",
                                     "Menu name",
                                     ImmutableList.of(fontDetail));
  }

  public void testDownloadedFontFamily() throws Exception {
    FontFamily compoundFontFamily = createMockFontFamily(true);

    VirtualFile file = myFixture.copyFileToProject("fonts/my_downloadable_font_family.xml", "res/font/my_downloadable_font_family.xml");
    assertNotNull(file);

    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    ILayoutPullParser parser = LayoutPullParserFactory.createFontFamilyParser((XmlFile)psiFile, (name) -> compoundFontFamily);
    assertTrue(parser instanceof DomPullParser);
    Element root = ((DomPullParser)parser).getRoot();

    String actualLayout = XmlPrettyPrinter.prettyPrint(root, true);
    String labelColor = "#" + ColorUtil.toHex(UIUtil.getLabelForeground());
    String expectedLayout = Joiner.on(SdkUtils.getLineSeparator()).join(
      "<LinearLayout",
      "xmlns:android=\"http://schemas.android.com/apk/res/android\"",
      "layout_width=\"fill_parent\"",
      "layout_height=\"wrap_content\"",
      "orientation=\"vertical\" >",
      "<TextView",
      "    layout_width=\"wrap_content\"",
      "    layout_height=\"wrap_content\"",
      "    fontFamily=\"@font/my_downloadable_font_family\"",
      "    paddingBottom=\"20dp\"",
      "    text=\"Lorem ipsum dolor sit amet, consectetur adipisicing elit.\"",
      "    textColor=\"" + labelColor + "\"",
      "    textSize=\"30sp\"",
      "    textStyle=\"italic\" />",
      "</LinearLayout>",
      "");

    assertEquals(expectedLayout, actualLayout);
  }

  public void testDownloadableFontWithoutFile() throws Exception {
    // This is a downloadable font that hasn't been cached yet
    FontFamily compoundFontFamily = createMockFontFamily(false);

    VirtualFile file = myFixture.copyFileToProject("fonts/my_downloadable_font_family.xml", "res/font/my_downloadable_font_family.xml");
    assertNotNull(file);

    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    assertNull(LayoutPullParserFactory.createFontFamilyParser((XmlFile)psiFile, (name) -> compoundFontFamily));
  }
}
