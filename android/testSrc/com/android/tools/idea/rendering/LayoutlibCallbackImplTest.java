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

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceValueImpl;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.layoutlib.LayoutLibrary;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.res.ResourceIdManager;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PsiTestUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.sdk.StudioEmbeddedRenderTarget;
import org.jetbrains.android.uipreview.ModuleClassLoader;
import org.jetbrains.android.uipreview.StudioModuleClassLoaderManager;
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

      ModuleRenderContext renderContext = ModuleRenderContext.forFile(psiFile);
      ModuleClassLoader classLoader = StudioModuleClassLoaderManager.get().getShared(layoutlib.getClassLoader(), renderContext, this);
      RenderModelModule module = new DefaultRenderModelModule(
        myModule,
        null,
        ResourceRepositoryManager.getInstance(myFacet),
        AndroidModuleInfo.getInstance(myFacet),
        null,
        ResourceIdManager.get(myModule)
      );
      LayoutlibCallbackImpl layoutlibCallback =
        new LayoutlibCallbackImpl(task, layoutlib, module, IRenderLogger.NULL_LOGGER, null, null, null, classLoader);
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
      StudioModuleClassLoaderManager.get().release(classLoader, this);
    });
  }

  // b/248473636
  public void testFontFromAarIsAccessible() throws IOException {
    @Language("XML") final String main = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                         "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                         "    android:layout_width=\"wrap_content\"\n" +
                                         "    android:layout_height=\"wrap_content\">\n" +
                                         "</LinearLayout>";
    PsiFile psiFile = myFixture.addFileToProject("res/layout/main.xml", main);

    File fontsFolder = createAarDependencyWithFont(myModule, "foobar");

    Configuration configuration = RenderTestUtil.getConfiguration(myModule, psiFile.getVirtualFile());
    RenderLogger logger = mock(RenderLogger.class);
    RenderTestUtil.withRenderTask(myFacet, psiFile.getVirtualFile(), configuration, logger, task -> {
      LayoutLibrary layoutlib = RenderService.getLayoutLibrary(myModule, StudioEmbeddedRenderTarget.getCompatibilityTarget(
        ConfigurationManager.getOrCreateInstance(myModule).getHighestApiTarget()));

      ModuleRenderContext renderContext = ModuleRenderContext.forFile(psiFile);
      ModuleClassLoader classLoader = StudioModuleClassLoaderManager.get().getShared(layoutlib.getClassLoader(), renderContext, this);
      RenderModelModule module = new DefaultRenderModelModule(
        myModule,
        null,
        ResourceRepositoryManager.getInstance(myFacet),
        AndroidModuleInfo.getInstance(myFacet),
        null,
        ResourceIdManager.get(myModule)
      );
      LayoutlibCallbackImpl layoutlibCallback =
        new LayoutlibCallbackImpl(task, layoutlib, module, IRenderLogger.NULL_LOGGER, null, null, null, classLoader);

      assertNotNull(layoutlibCallback.createXmlParserForPsiFile(fontsFolder.toPath().resolve("aar_font_family.xml").toAbsolutePath().toString()));

      StudioModuleClassLoaderManager.get().release(classLoader, this);
    });

  }

  private static File createAarDependencyWithFont(Module module, String libraryName) throws IOException {
    File aarDir = FileUtil.createTempDirectory(libraryName, "_exploded");
    createManifest(aarDir, "com.foo.bar");
    File resFolder = new File(aarDir,"res");
    resFolder.mkdirs();
    File fontsFolder = new File(resFolder, "font");
    fontsFolder.mkdirs();

    ClassLoader resourceClassLoader = LayoutlibCallbackImplTest.class.getClassLoader();

    String[] fontFileNames = new String[] {"aar_font_family.xml", "aar_font1.ttf"};
    for (String fontToCopy : fontFileNames) {
      File newFile = new File(fontsFolder, fontToCopy);
      try (InputStream is = resourceClassLoader.getResourceAsStream("fonts/" + fontToCopy)) {
        byte[] bytes = is.readAllBytes();
        try (FileOutputStream fo = new FileOutputStream(newFile)) {
          fo.write(bytes);
        }
      }
    }

    Library library = PsiTestUtil.addProjectLibrary(
      module,
      libraryName + ".aar",
      Lists.newArrayList(VfsUtil.findFileByIoFile(resFolder, true)),
      Collections.emptyList()
    );

    ModuleRootModificationUtil.addDependency(module, library);

    return fontsFolder;
  }

  private static void createManifest(File aarDir, String packageName) throws IOException {
    aarDir.mkdirs();
    String content = "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"" + packageName + "\">\n" +
                     "</manifest>";
    File manifest = new File(aarDir, SdkConstants.FN_ANDROID_MANIFEST_XML);
    try (FileOutputStream out = new FileOutputStream(manifest)) {
      out.write(content.getBytes(Charsets.UTF_8));
    }
  }
}
