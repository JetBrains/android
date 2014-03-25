/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.parser.GradleSettingsFile;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;

/**
 * <p>Test case for GradleProjectImport#importModule method. It requires substantially different setup then {@link }</p>
 * <p/>
 * To run this test case, configure:
 * <ul><li>$ADT_TEST_SDK_PATH</li></ul>
 */
@SuppressWarnings("JUnitTestCaseWithNoTests") // Named differently, didn't want to do too much unnecessary setups
public final class GradleModuleImportTest extends AndroidTestBase {
  public static final String MODULE_NAME = "guadeloupe";

  private VirtualFile myModule;

  private static VirtualFile createGradleProjectToImport() throws IOException {
    File temp = Files.createTempDir();
    File moduleDir = new File(temp, MODULE_NAME);
    if (!moduleDir.mkdir()) {
      throw new IllegalStateException("Unable to create module");
    }
    Files.write("apply plugin: 'android-library'\n\n" +
                "dependencies {\n" +
                "    compile 'com.android.support:support-v4:13.0.+'\n" +
                "}\n\n" +
                "android {\n" +
                "    compileSdkVersion 19\n" +
                "    buildToolsVersion \"19\"\n\n" +
                "    defaultConfig {\n" +
                "        minSdkVersion 8\n" +
                "        targetSdkVersion 19\n" +
                "    }\n\n" +
                "    sourceSets {\n" +
                "        main {\n" +
                "            manifest.srcFile 'AndroidManifest.xml'\n" +
                "            java.srcDirs = ['src']\n" +
                "            res.srcDirs = ['res']\n" +
                "        }\n" +
                "    }\n" +
                "}\n", new File(moduleDir, SdkConstants.FN_BUILD_GRADLE), Charset.defaultCharset());
    VirtualFile moduleFile = LocalFileSystem.getInstance().findFileByPath(moduleDir.getAbsolutePath());
    if (moduleFile == null) {
      throw new IllegalStateException("Cannot get virtual file for module we just created");
    }
    else {
      return moduleFile;
    }
  }

  private static void assertModuleInSettingsFile(Project project, String name) throws IOException {
    GradleSettingsFile settingsFile = GradleSettingsFile.get(project);
    assertNotNull("Missing " + SdkConstants.FN_SETTINGS_GRADLE, settingsFile);
    Iterable<String> modules = settingsFile.getModules();
    if (!Iterables.contains(modules, ":" + name)) {
      fail(String.format("Subproject was not added to %s. Modules in the file are: %s", SdkConstants.FN_SETTINGS_GRADLE,
                         Joiner.on(", ").join(modules)));
    }
  }

  private static void assertNoFilesAdded(VirtualFile[] moduleChildren) {
    if (moduleChildren.length != 1) {
      StringBuilder failure = new StringBuilder("Files were altered in the source directory:");
      Joiner.on(", ").appendTo(failure, Iterables.transform(Arrays.asList(moduleChildren), new Function<VirtualFile, String>() {
        @Override
        public String apply(VirtualFile input) {
          return input.getName();
        }
      }));
      fail(failure.toString());
    }
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    final TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder =
      IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    myFixture.setUp();
    myFixture.setTestDataPath(getTestDataPath());

    myModule = createGradleProjectToImport();

    System.out.printf("Project location: %s\n", getProject().getBaseDir());
    System.out.printf("Gradle subproject location: %s\n", myModule.getPath());
  }

  /**
   * Test importing simple module into even simpler project
   */
  public void testImportGradleModule() throws IOException, ConfigurationException {
    new GradleProjectImporter().importModule(myModule, getProject(), new GradleProjectImporter.Callback() {
      @Override
      public void projectImported(@NotNull Project project) {
        // Nothing here
      }

      @Override
      public void importFailed(@NotNull Project project, @NotNull String errorMessage) {
        fail(String.format("Module import failed: %s", errorMessage));
      }
    });
    assertNotNull("Module sources were not copied", getProject().getBaseDir().findChild(MODULE_NAME));
    final VirtualFile[] moduleChildren = myModule.getChildren();
    assertNoFilesAdded(moduleChildren);
    assertEquals(SdkConstants.FN_BUILD_GRADLE, moduleChildren[0].getName());
    assertModuleInSettingsFile(getProject(), myModule.getName());
  }

  /**
   * Test exception is thrown if the target folder already exists.
   */
  public void testFailsIfDirExists() throws ConfigurationException, IOException {
    ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<Boolean, IOException>() {
      @Override
      public Boolean compute() throws IOException {
        getProject().getBaseDir().createChildDirectory(this, MODULE_NAME);
        return true;
      }
    });
    try {
      new GradleProjectImporter().importModule(myModule, getProject(), null);
      fail("Exception was not raised");
    }
    catch (IOException e) {
      // Good.
    }
  }


  @Override
  protected void tearDown() throws Exception {
    if (myFixture != null) {
      myFixture.tearDown();
      myFixture = null;
    }
    ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    Project[] openProjects = projectManager.getOpenProjects();
    if (openProjects.length > 0) {
      final Project project = openProjects[0];
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          Disposer.dispose(project);
          ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
          if (projectManager instanceof ProjectManagerImpl) {
            Collection<Project> projectsStillOpen = projectManager.closeTestProject(project);
            if (!projectsStillOpen.isEmpty()) {
              Project project = projectsStillOpen.iterator().next();
              projectsStillOpen.clear();
              throw new AssertionError("Test project is not disposed: " + project + ";\n created in: " +
                                       PlatformTestCase.getCreationPlace(project));
            }
          }
        }
      });
    }

    super.tearDown();
  }
}
