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
package com.android.tools.idea.rendering.parsers;

import com.android.resources.Density;
import com.android.tools.idea.rendering.RenderLogger;
import com.google.common.collect.Sets;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.AndroidTestCase;

import java.util.Set;

import static com.android.SdkConstants.*;
import static org.xmlpull.v1.XmlPullParser.END_TAG;
import static org.xmlpull.v1.XmlPullParser.START_TAG;

@SuppressWarnings("ConstantConditions")
public class PaddingLayoutRenderPullParserTest extends AndroidTestCase {
  @SuppressWarnings("SpellCheckingInspection")
  public static final String BASE_PATH = "xmlpull/";

  public PaddingLayoutRenderPullParserTest() {
  }

  public void test() throws Exception {
    String filename = "layout.xml";
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + filename, "res/layout/" + filename);
    assertNotNull(file);
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    assertNotNull(psiFile);

    assertTrue(psiFile instanceof XmlFile);
    XmlFile xmlFile = (XmlFile)psiFile;

    // Second child of the root in layout.xml is a LinearLayout of id @+id/header
    XmlTag element = xmlFile.getRootTag().getSubTags()[1];
    assertEquals("@+id/header", element.getAttributeValue(ATTR_ID, ANDROID_URI));
    assertNull(element.getAttributeValue(ATTR_ID, ATTR_PADDING));

    Set<RenderXmlTag> explode = Sets.newHashSet(new PsiXmlTag(element));
    LayoutRenderPullParser
      parser = LayoutRenderPullParser.create(new PsiXmlFile(xmlFile), RenderLogger.NOP_RENDER_LOGGER, explode, Density.MEDIUM, null, null, true);
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("LinearLayout", parser.getName());
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("include", parser.getName());
    assertNull(parser.getAttributeValue(ANDROID_URI, ATTR_PADDING));
    assertEquals(END_TAG, parser.nextTag());
    assertEquals(START_TAG, parser.nextTag());
    assertEquals("LinearLayout", parser.getName());
    assertEquals("@+id/header", parser.getAttributeValue(ANDROID_URI, ATTR_ID));
    assertEquals("20px", parser.getAttributeValue(ANDROID_URI, ATTR_PADDING));
  }
}
