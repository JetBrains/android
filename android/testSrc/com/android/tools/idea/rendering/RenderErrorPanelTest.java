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

import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

public class RenderErrorPanelTest extends AndroidTestCase {
  public static final String BASE_PATH = "render/";

  @Override
  protected boolean requireRecentSdk() {
    // Need valid layoutlib install
    return true;
  }

  public void testPanel() {
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout1.xml");
    assertNotNull(file);
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    assertNotNull(psiFile);

    ConfigurationManager configurationManager = facet.getConfigurationManager();
    assertNotNull(configurationManager);
    Configuration configuration = configurationManager.getConfiguration(file);
    RenderLogger logger = new RenderLogger("mylogger", myModule);
    RenderService service = RenderService.create(facet, myModule, psiFile, configuration, logger, null);
    assertNotNull(service);
    RenderResult render = service.render();
    assertNotNull(render);
    assertTrue(logger.hasProblems());
    RenderErrorPanel panel = new RenderErrorPanel();
    String html = panel.showErrors(render);

    html = stripImages(html);

    assertEquals(
      "<html><body><A HREF=\"action:close\"></A><font style=\"font-weight:bold; color:#005555;\">Rendering Problems</font><BR/>\n" +
      "<B>NOTE: One or more layouts are missing the layout_width or layout_height attributes. These are required in most layouts.</B><BR/>\n" +
      "&lt;LinearLayout> does not set the required layout_width attribute: <BR/>\n" +
      "&nbsp;&nbsp;&nbsp;&nbsp;<A HREF=\"command:0\">Set to wrap_content</A>, <A HREF=\"command:1\">Set to match_parent</A><BR/>\n" +
      "&lt;LinearLayout> does not set the required layout_height attribute: <BR/>\n" +
      "&nbsp;&nbsp;&nbsp;&nbsp;<A HREF=\"command:2\">Set to wrap_content</A>, <A HREF=\"command:3\">Set to match_parent</A><BR/>\n" +
      "<BR/>\n" +
      "Or: <A HREF=\"command:4\">Automatically add all missing attributes</A><BR/>\n" +
      "<BR/>\n" +
      "<BR/>\n" +
      "</body></html>",
     html);
  }

  // Image paths will include full resource urls which depends on the test environment
  private static String stripImages(@NotNull String html) {
    while (true) {
      int index = html.indexOf("<img");
      if (index == -1) {
        return html;
      }
      int end = html.indexOf('>', index);
      if (end == -1) {
        return html;
      } else {
        html = html.substring(0, index) + html.substring(end + 1);
      }
    }
  }
}
