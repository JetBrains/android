/*
 * Copyright (C) 2016 The Android Open Source Project
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
package org.jetbrains.android.uipreview;

import com.android.ide.common.rendering.LayoutLibrary;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.idea.res.ResourceClassRegistry;
import com.google.common.io.Files;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.android.tools.idea.gradle.util.FilePaths.pathToIdeaUrl;
import static org.mockito.Mockito.mock;

public class ModuleClassLoaderTest extends AndroidTestCase {

  /**
   * Generates an empty R class file with one static field ID = "FileID"
   */
  @SuppressWarnings("SameParameterValue")
  private static void generateRClass(@NotNull String pkg, @NotNull File outputFile) throws IOException {
    File tmpDir = FileUtil.createTempDirectory("source", null);
    File tmpClass = new File(tmpDir, "R.java");
    FileUtil.writeToFile(tmpClass,
                         "package " + pkg + ";" +
                         "public class R {" +
                         "      public static final String ID = \"FileID\";" +
                         "}");

    JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    javac.run(null, System.out, System.err, tmpClass.getAbsolutePath());

    FileUtil.copy(new File(tmpDir, "R.class"), outputFile);
  }

  // Disabled. Failing in post-submit
  public void disabledTestModuleClassLoading() throws ClassNotFoundException, IOException {
    LayoutLibrary layoutLibrary = mock(LayoutLibrary.class);

    Module module = myFixture.getModule();
    File tmpDir = Files.createTempDir();
    File outputDir = new File(tmpDir, CompilerModuleExtension.PRODUCTION + "/" + module.getName() + "/test");
                              assertTrue(FileUtil.createDirectory(outputDir));
    CompilerProjectExtension.getInstance(getProject()).setCompilerOutputUrl(pathToIdeaUrl(tmpDir));

    generateRClass("test", new File(outputDir, "R.class"));

    ApplicationManager.getApplication().runReadAction(() -> {
      ModuleClassLoader loader = ModuleClassLoader.get(layoutLibrary, module);
      try {
        Class<?> rClass = loader.loadClass("test.R");
        String value = (String)rClass.getDeclaredField("ID").get(null);
        assertEquals("FileID", value);
      }
      catch (ClassNotFoundException | IllegalAccessException | NoSuchFieldException e) {
        fail("Unexpected exception " + e.getLocalizedMessage());
      }
    });
  }


  /**
   * Verifies that the AAR generated R classes are given priority vs the build generated files. This is important in cases like support
   * library upgrades/downgrades. In those cases, the build generated file, will be outdated so it shouldn't be used by the ModuleClassLoader.
   * By preferring the AAR geneated versions, we make sure we are always up-to-date.
   * See <a href="http://b.android.com/229382">229382</a>
   */
  public void testAARPriority() throws ClassNotFoundException, IOException {
    LayoutLibrary layoutLibrary = mock(LayoutLibrary.class);

    Module module = myFixture.getModule();
    File tmpDir = Files.createTempDir();
    File outputDir = new File(tmpDir, CompilerModuleExtension.PRODUCTION + "/" + module.getName() + "/test");
    assertTrue(FileUtil.createDirectory(outputDir));
    CompilerProjectExtension.getInstance(getProject()).setCompilerOutputUrl(pathToIdeaUrl(tmpDir));

    generateRClass("test", new File(outputDir, "R.class"));

    AppResourceRepository appResources = AppResourceRepository.getAppResources(module, true);
    ResourceClassRegistry rClassRegistry = ResourceClassRegistry.get(module.getProject());
    rClassRegistry.addLibrary(appResources, "test");

    AtomicBoolean noSuchField = new AtomicBoolean(false);
    ApplicationManager.getApplication().runReadAction(() -> {
      ModuleClassLoader loader = ModuleClassLoader.get(layoutLibrary, module);
      try {
        Class<?> rClass = loader.loadClass("test.R");
        rClass.getDeclaredField("ID");
      }
      catch (NoSuchFieldException e) {
        noSuchField.set(true);
      }
      catch (ClassNotFoundException e) {
        fail("Unexpected exception " + e.getLocalizedMessage());
      }
    });

    assertTrue(noSuchField.get());
  }

}