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
package com.android.tools.idea.npw;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.GradleModel;
import com.android.tools.idea.gradle.eclipse.GradleImport;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.parser.GradleSettingsFile;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.gradle.stubs.gradle.GradleProjectStub;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Closer;
import com.google.common.io.Files;
import com.google.common.util.concurrent.Atomics;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.gradle.tooling.model.GradleProject;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

public final class WrapArchiveWizardPathTest extends AndroidTestBase {
  public static final String LIB_DIR_NAME = "lib";
  public static final String LIBS_DEPENDENCY = "compile fileTree(include: ['*.jar'], dir: '" + LIB_DIR_NAME + "')";
  public static final String LIBRARY_JAR_NAME = "library.jar";
  private static final String TOP_LEVEL_BUILD_GRADLE = "buildscript {\n" +
                                                       "    repositories {\n" +
                                                       GradleImport.MAVEN_REPOSITORY +
                                                       "    }\n" +
                                                       "    dependencies {\n" +
                                                       "        classpath 'com.android.tools.build:gradle:" +
                                                       SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION + "'\n" +
                                                       "    }\n" +
                                                       "}\n" +
                                                       "\n" +
                                                       "allprojects {\n" +
                                                       "    repositories {\n" +
                                                       GradleImport.MAVEN_REPOSITORY +
                                                       "    }\n" +
                                                       "}\n";
  private static final String BUILD_GRADLE_TEMPLATE = "apply plugin: 'java'\n\n" +
                                                      "dependencies {\n" +
                                                      "    compile 'com.android.support:support-v4:13.0.+'\n" +
                                                      "    %s\n" +
                                                      "}\n\n";
  private File dir;
  private File myJarFile;

  private static void assertJarImport(@NotNull Project project,
                                      @NotNull String gradlePath,
                                      @NotNull File jarFile,
                                      boolean sourceFileShouldExist) throws IOException {
    File defaultSubprojectLocation = GradleUtil.getModuleDefaultPath(project.getBaseDir(), gradlePath);
    File copy = new File(defaultSubprojectLocation, jarFile.getName());
    assertTrue(String.format("File %s does not exist", copy), copy.exists());
    File buildGradle = new File(defaultSubprojectLocation, SdkConstants.FN_BUILD_GRADLE);
    assertTrue(String.format("File %s does not exist", buildGradle), buildGradle.exists());
    VirtualFile vfile = VfsUtil.findFileByIoFile(buildGradle, true);
    assert vfile != null;
    assertEquals(CreateModuleFromArchiveAction.getBuildGradleText(jarFile), VfsUtilCore.loadText(vfile));
    GradleSettingsFile settingsFile = GradleSettingsFile.get(project);
    assert settingsFile != null;
    Iterable<String> modules = settingsFile.getModules();
    assertTrue("{ " + Joiner.on(", ").join(modules) + " }", Iterables.contains(modules, gradlePath));
    assertEquals("Source file existance", sourceFileShouldExist, jarFile.isFile());
  }

  /**
   * We need real Jar contents as this test will actually run Gradle that will peek inside the archive.
   */
  @NotNull
  private static byte[] createRealJarArchive() throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    Closer closer = Closer.create();
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    JarOutputStream jar = closer.register(new JarOutputStream(buffer, manifest));
    try {
      jar.putNextEntry(new JarEntry("/dummy.txt"));
      jar.write(TOP_LEVEL_BUILD_GRADLE.getBytes());
      closer.close();
      return buffer.toByteArray();
    }
    catch (IOException e) {
      closer.close();
      throw closer.rethrow(e);
    }
  }

  private static VirtualFile createFile(VirtualFile directory, String fname, String contents) throws IOException {
    VirtualFile archive = directory.createChildData(Integer.valueOf(0), fname);
    VfsUtil.saveText(archive, contents);
    return archive;
  }

  private static Module[] getModules(final Project project, String... moduleNames) {
    if (moduleNames.length == 0) {
      return new Module[0];
    }
    else {
      List<Module> modules = Lists.newArrayList();
      for (String name : moduleNames) {
        Module module = GradleUtil.findModuleByGradlePath(project, WrapArchiveWizardPath.makeAbsolute(name));
        assert module != null : "Module \"" + WrapArchiveWizardPath.makeAbsolute(name) + "\" was not found in the project ";
        modules.add(module);
      }
      return Iterables.toArray(modules, Module.class);
    }
  }

  private static void createModule(@NotNull Project project,
                                   @NotNull File archive,
                                   @NotNull String gradlePath,
                                   boolean moveFile,
                                   @Nullable Module[] modulesToUpdateDependency) throws IOException {
    NewModuleWizardState wizardState = new NewModuleWizardState();
    WrapArchiveWizardPath path = new WrapArchiveWizardPath(wizardState, project, null, null);

    wizardState.templateChanged(project, NewModuleWizardState.ARCHIVE_IMPORT_NAME);
    wizardState.put(WrapArchiveWizardPath.KEY_ARCHIVE, archive.getAbsolutePath());
    wizardState.put(WrapArchiveWizardPath.KEY_GRADLE_PATH, gradlePath);

    wizardState.put(WrapArchiveWizardPath.KEY_MOVE_ARCHIVE, moveFile);
    wizardState.put(WrapArchiveWizardPath.KEY_MODULES_FOR_DEPENDENCY_UPDATE, modulesToUpdateDependency);

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

    myJarFile = new File(dir, LIBRARY_JAR_NAME);
    Files.write(createRealJarArchive(), myJarFile);

    VirtualFile baseDir = getProject().getBaseDir();
    AndroidGradleTestCase.createGradleWrapper(virtualToIoFile(baseDir));
    System.out.printf("Project location: %s\n", baseDir);
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
    String gradlePath = WrapArchiveWizardPath.makeAbsolute(Files.getNameWithoutExtension(myJarFile.getAbsolutePath()));
    createModule(project, myJarFile, gradlePath, false, null);
    assertJarImport(project, gradlePath, myJarFile, true);
  }

  public void testCreateModuleNonDefaultSimpleName() throws Exception {
    Project project = myFixture.getProject();
    String gradlePath = WrapArchiveWizardPath.makeAbsolute("testmodule");
    createModule(project, myJarFile, gradlePath, false, null);
    assertJarImport(project, gradlePath, myJarFile, true);
  }

  public void testCreateModuleNonDefaultNestedName() throws Exception {
    Project project = myFixture.getProject();
    String gradlePath = WrapArchiveWizardPath.makeAbsolute(":category:module");
    createModule(project, myJarFile, gradlePath, false, null);
    assertJarImport(project, gradlePath, myJarFile, true);
  }

  public void testMoveJarFromProject() throws IOException {
    String buildGradleText = String.format(BUILD_GRADLE_TEMPLATE, LIBS_DEPENDENCY);
    String newModuleName = WrapArchiveWizardPath.makeAbsolute(Files.getNameWithoutExtension(LIBRARY_JAR_NAME));
    String buildGradleWithReplacedDependency = String.format(BUILD_GRADLE_TEMPLATE,
                                                             LIBS_DEPENDENCY + "\n    compile project('" + newModuleName + "')");
    asseryJarProperlyMoved(buildGradleText, buildGradleWithReplacedDependency);
  }

  public void testMoveJarFromProjectReplaceFileDependency() throws IOException {
    String buildGradleText = String.format(BUILD_GRADLE_TEMPLATE,
                                           LIBS_DEPENDENCY + "\n    compile files('lib/" + LIBRARY_JAR_NAME + "')");
    String newModuleName = WrapArchiveWizardPath.makeAbsolute(Files.getNameWithoutExtension(LIBRARY_JAR_NAME));
    String buildGradleWithReplacedDependency = String.format(BUILD_GRADLE_TEMPLATE,
                                                             LIBS_DEPENDENCY + "\n    compile project('" + newModuleName + "')");
    asseryJarProperlyMoved(buildGradleText, buildGradleWithReplacedDependency);
  }

  public void testMoveJarFromProjectReplaceFileDependencyFromList() throws IOException {
    String buildGradleText = String.format(BUILD_GRADLE_TEMPLATE,
                                           LIBS_DEPENDENCY +
                                           "\n    compile files('lib/" + LIBRARY_JAR_NAME + "', 'some/other/file.jar')");
    String newModuleName = WrapArchiveWizardPath.makeAbsolute(Files.getNameWithoutExtension(LIBRARY_JAR_NAME));
    String buildGradleWithReplacedDependency = String.format(BUILD_GRADLE_TEMPLATE,
                                                             LIBS_DEPENDENCY +
                                                             "\n    compile files('some/other/file.jar')" +
                                                             "\n    compile project('" + newModuleName + "')");
    asseryJarProperlyMoved(buildGradleText, buildGradleWithReplacedDependency);
  }

  private void asseryJarProperlyMoved(String initialBuildGradle, String expectedBuildGradle) throws IOException {
    String moduleName = "mymodule";
    Project project = getProject();

    File fileToImport = new CreateAndroidStudioProjectAction(project, moduleName, initialBuildGradle).execute().getResultObject();
    String newModuleName = WrapArchiveWizardPath.makeAbsolute(Files.getNameWithoutExtension(LIBRARY_JAR_NAME));
    createModule(project, fileToImport, newModuleName, true, getModules(project, moduleName));
    assertJarImport(project, newModuleName, fileToImport, false);
    VirtualFile buildGradle = project.getBaseDir().findFileByRelativePath(moduleName + "/" + SdkConstants.FN_BUILD_GRADLE);
    assert buildGradle != null;
    GradleBuildFile buildModel = new GradleBuildFile(buildGradle, project);
    assertEquals(expectedBuildGradle, buildModel.getPsiFile().getText());
  }

  private static class CreateAndroidStudioProjectAction extends WriteCommandAction<File> {
    private final Project myProject;
    private final String myModuleName;
    @NotNull private final String myModuleBuildGradleBody;

    public CreateAndroidStudioProjectAction(Project project, String moduleName, @NotNull String moduleBuildGradleBody) {
      super(project);
      myProject = project;
      myModuleName = moduleName;
      myModuleBuildGradleBody = moduleBuildGradleBody;
    }

    @Override
    protected void run(@NotNull final Result<File> result) throws Throwable {
      File path = new File(virtualToIoFile(myProject.getBaseDir()), myModuleName + File.separator + LIB_DIR_NAME);
      VirtualFile directory = VfsUtil.createDirectories(path.getAbsolutePath());
      VirtualFile archive = directory.createChildData(this, LIBRARY_JAR_NAME);
      archive.setBinaryContent(createRealJarArchive());
      final VirtualFile moduleBuildGradle = createFile(directory.getParent(), SdkConstants.FN_BUILD_GRADLE, myModuleBuildGradleBody);
      final VirtualFile topBuildGradle = createFile(myProject.getBaseDir(), SdkConstants.FN_BUILD_GRADLE, TOP_LEVEL_BUILD_GRADLE);
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      GradleSettingsFile settingsGradle = GradleSettingsFile.getOrCreate(myProject);
      settingsGradle.addModule(WrapArchiveWizardPath.makeAbsolute(myModuleName), virtualToIoFile(directory.getParent()));
      final AtomicReference<String> error = Atomics.newReference();
      final AtomicBoolean done = new AtomicBoolean(false);
      GradleProjectImporter.getInstance().requestProjectSync(myProject, new GradleSyncListener.Adapter() {
        @Override
        public void syncSucceeded(@NotNull Project project) {
          Module module = ModuleManager.getInstance(myProject).findModuleByName(myModuleName);
          assert module != null;
          FacetManager facetManager = FacetManager.getInstance(module);
          ModifiableFacetModel modifiableModel = facetManager.createModifiableModel();
          AndroidGradleFacet facet = facetManager.createFacet(AndroidGradleFacet.getFacetType(), AndroidGradleFacet.NAME, null);
          GradleProject gradleProject = new GradleProjectStub(myProject.getName(),
                                                              WrapArchiveWizardPath.makeAbsolute(myModuleName),
                                                              virtualToIoFile(topBuildGradle),
                                                              "compile");
          GradleModel gradleModel = GradleModel.create(myModuleName, gradleProject, virtualToIoFile(moduleBuildGradle), "2.2.1");
          facet.setGradleModel(gradleModel);
          modifiableModel.addFacet(facet);
          modifiableModel.commit();
          assert Projects.isBuildWithGradle(module);
          done.set(true);
        }

        @Override
        public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
          error.set(errorMessage);
        }
      });
      if (error.get() != null) {
        throw new IllegalStateException(error.get());
      }
      if (!done.get()) {
        throw new IllegalStateException("Sync should've been complete by now");
      }
      result.setResult(virtualToIoFile(archive));
    }

  }
}