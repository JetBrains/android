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
package com.android.tools.idea.wizard;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.parser.GradleSettingsFile;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.fixtures.impl.BaseFixture;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

public final class WrapArchiveWizardPathTest extends AndroidTestBase {
  private static final byte[] FAKE_FILE_CONTENTS = {(byte)0xCA, (byte)0xFE, (byte)0xBA, (byte)0xBE};

  private File dir;
  private File myJarFile;

  private static void assertJarImport(@NotNull Project project, @NotNull String gradlePath, @NotNull File jarFile) throws IOException {
    File defaultSubprojectLocation = GradleUtil.getDefaultSubprojectLocation(project.getBaseDir(), gradlePath);
    File copy = new File(defaultSubprojectLocation, jarFile.getName());
    assertTrue(String.format("File %s does not exist", copy), copy.exists());
    File buildGradle = new File(defaultSubprojectLocation, SdkConstants.FN_BUILD_GRADLE);
    assertTrue(String.format("File %s does not exist", buildGradle), buildGradle.exists());
    VirtualFile vfile = VfsUtil.findFileByIoFile(buildGradle, true);
    assert vfile != null;
    assertEquals(WrapArchiveWizardPath.getBuildGradleText(jarFile), VfsUtilCore.loadText(vfile));
    GradleSettingsFile settingsFile = GradleSettingsFile.get(project);
    assert settingsFile != null;
    Iterable<String> modules = settingsFile.getModules();
    assertTrue("{ " + Joiner.on(", ").join(modules) + " }", Iterables.contains(modules, gradlePath));
  }

  private void createModule(@NotNull Project project,
                            @NotNull File archive,
                            @NotNull String gradlePath) throws IOException {
    NewModuleWizardState wizardState = new NewModuleWizardState();
    WrapArchiveWizardPath path = new WrapArchiveWizardPath(wizardState, project, null,
                                                           ((BaseFixture)myFixture).getTestRootDisposable());

    wizardState.templateChanged(project, NewModuleWizardState.ARCHIVE_IMPORT_NAME);
    wizardState.put(WrapArchiveWizardPath.KEY_ARCHIVE, archive.getAbsolutePath());
    wizardState.put(WrapArchiveWizardPath.KEY_GRADLE_PATH, gradlePath);
    path.createModule();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    final TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder =
      IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    myFixture.setUp();
    myFixture.setTestDataPath(getTestDataPath());

    dir = Files.createTempDir();

    myJarFile = new File(dir, StringUtil.getShortName(getClass()) + ".jar");
    Files.write(FAKE_FILE_CONTENTS, myJarFile);

    System.out.printf("Project location: %s\n", getProject().getBaseDir());
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
    if (dir != null && dir.isDirectory()) {
      ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<Boolean, IOException>() {
        @Override
        public Boolean compute() throws IOException {
          VirtualFile vfile = VfsUtil.findFileByIoFile(dir, true);
          if (vfile != null) {
            vfile.delete(this);
          }
          return true;
        }
      });
    }
    super.tearDown();
  }

  public void testCreateModuleDefaultName() throws Exception {
    Project project = myFixture.getProject();
    String gradlePath = GradleUtil.makeAbsolute(Files.getNameWithoutExtension(myJarFile.getAbsolutePath()));
    assert gradlePath != null;
    createModule(project, myJarFile, gradlePath);
    assertJarImport(project, gradlePath, myJarFile);
  }

  public void testCreateModuleNonDefaultSimpleName() throws Exception {
    Project project = myFixture.getProject();
    String gradlePath = GradleUtil.makeAbsolute("testmodule");
    assert gradlePath != null;
    createModule(project, myJarFile, gradlePath);
    assertJarImport(project, gradlePath, myJarFile);
  }

  public void testCreateModuleNonDefaultNestedName() throws Exception {
    Project project = myFixture.getProject();
    String gradlePath = GradleUtil.makeAbsolute(":category:module");
    assert gradlePath != null;
    createModule(project, myJarFile, gradlePath);
    assertJarImport(project, gradlePath, myJarFile);
  }
}