/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT;
import static org.mockito.Mockito.mock;

import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceValueImpl;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.layoutlib.LayoutLibrary;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.intellij.psi.PsiFile;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.sdk.StudioEmbeddedRenderTarget;
import org.jetbrains.android.uipreview.ModuleClassLoader;
import org.jetbrains.android.uipreview.ModuleClassLoaderManager;
import org.jetbrains.android.uipreview.ModuleRenderContext;

public class LayoutlibCallbackImplTest extends AndroidTestCase {
  /**
   * Regression test for b/136632498<br/>
   * Resource resolver must be passed to the LayoutPullParser so the navigation graph <code>@layout</code> references can be resolved.
   * After the fix, when asking for the <code>app:navGraph</code> attribute on the &lt;fragment&gt; entry, the parser will return the start
   * destination <code>@layout/frament_blank</code>
   */
  public void testBug136632498() {
    @Language("XML") final String navGraph = "<navigation xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                                             "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                                             "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                             "    app:startDestination=\"@id/blankFragment\">\n" +
                                             "    <fragment\n" +
                                             "        android:id=\"@+id/blankFragment\"\n" +
                                             "        android:name=\"com.example.cashdog.cashdog.BlankFragment\"\n" +
                                             "        android:label=\"Blank\"\n" +
                                             "        tools:layout=\"@layout/fragment_blank\" />\n" +
                                             "</navigation>";

    @Language("XML") final String main = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                         "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                         "    android:layout_width=\"wrap_content\"\n" +
                                         "    android:layout_height=\"wrap_content\">\n" +
                                         "    <fragment xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                                         "        android:id=\"@+id/nav_host_fragment\"\n" +
                                         "        android:name=\"androidx.navigation.fragment.NavHostFragment\"\n" +
                                         "        android:layout_width=\"0dp\"\n" +
                                         "        android:layout_height=\"0dp\"\n" +
                                         "        app:defaultNavHost=\"true\"\n" +
                                         "        app:navGraph=\"@navigation/nav_graph\" />" +
                                         "</LinearLayout>";
    myFixture.addFileToProject("res/navigation/nav_graph.xml", navGraph);
    PsiFile psiFile = myFixture.addFileToProject("res/layout/main.xml", main);

    Configuration configuration = RenderTestUtil.getConfiguration(myModule, psiFile.getVirtualFile());
    RenderLogger logger = mock(RenderLogger.class);
    RenderTestUtil.withRenderTask(myFacet, psiFile.getVirtualFile(), configuration, logger, task -> {
      LayoutLibrary layoutlib = RenderService.getLayoutLibrary(myModule, StudioEmbeddedRenderTarget.getCompatibilityTarget(
        ConfigurationManager.getOrCreateInstance(myModule).getHighestApiTarget()));
      LocalResourceRepository appResources = ResourceRepositoryManager.getAppResources(myFacet);

      ModuleRenderContext renderContext = ModuleRenderContext.forFile(psiFile);
      ModuleClassLoader classLoader = ModuleClassLoaderManager.get().getShared(layoutlib.getClassLoader(), renderContext, this);
      LayoutlibCallbackImpl layoutlibCallback =
        new LayoutlibCallbackImpl(task, layoutlib, appResources, myModule, myFacet, IRenderLogger.NULL_LOGGER, null, null, null, classLoader);
      ILayoutPullParser parser = layoutlibCallback.getParser(new ResourceValueImpl(
        ResourceNamespace.ANDROID, ResourceType.LAYOUT, "main", psiFile.getVirtualFile().getCanonicalPath()
      ));

      assertNotNull(parser);
      try {
        parser.nextTag(); // Read top LinearLayout
        parser.nextTag(); // Read <fragment>
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
      String startDestination = parser.getAttributeValue(ANDROID_URI, ATTR_LAYOUT);
      assertEquals("@layout/fragment_blank", startDestination);
      ModuleClassLoaderManager.get().release(classLoader, this);
    });
  }
}
