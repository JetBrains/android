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
import com.android.tools.idea.configurations.Configuration;
import com.android.utils.StringHelper;
import com.intellij.openapi.vfs.VirtualFile;
import org.w3c.dom.Element;

import java.io.IOException;

public class MenuPreviewRendererTest extends RenderTestBase {

  public void test() throws Exception {
    myFixture.copyFileToProject("menus/strings.xml", "res/menu/strings.xml");
    VirtualFile file = myFixture.copyFileToProject("menus/menu1.xml", "res/menu/menu1.xml");
    assertNotNull(file);
    RenderTask task = createRenderTask(file);
    assertNotNull(task);
    ILayoutPullParser parser = LayoutPullParsers.create(task);
    assertTrue(parser instanceof DomPullParser);
    Element root = ((DomPullParser)parser).getRoot();

    String layout = XmlPrettyPrinter.prettyPrint(root, true);

    String newXml = "<FrameLayout\n" +
                    "xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "android:layout_width=\"match_parent\"\n" +
                    "android:layout_height=\"match_parent\" />\n";

    newXml = StringHelper.toSystemLineSeparator(newXml);

    checkRendering(task, "menu/menu1.png");
    assertEquals(newXml, layout);
  }

  public void testLightTheme() throws IOException {
    myFixture.copyFileToProject("menus/strings.xml", "res/menu/strings.xml");
    VirtualFile file = myFixture.copyFileToProject("menus/menu1.xml", "res/menu/menu1.xml");
    assertNotNull(file);
    Configuration configuration = getConfiguration(file, DEFAULT_DEVICE_ID, "@android:style/Theme.Holo.Light");
    RenderTask task = createRenderTask(file, configuration);
    assertNotNull(task);

    checkRendering(task, "menu/menu1-light.png");
  }
}
