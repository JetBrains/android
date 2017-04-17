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

package com.android.tools.idea.npw.importing;

import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.GradleSettingsModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.FileTreeDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ModuleDependencyModel;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.android.tools.idea.testing.TestProjectPaths.IMPORTING;
import static com.google.common.truth.Truth.*;
import static com.intellij.openapi.util.io.FileUtil.copyFileOrDir;
import static com.intellij.openapi.util.io.FileUtil.join;

/**
 * This class tests {@link ArchiveToGradleModuleModel}. It is testing that a JAR or AAR can be imported into a project correctly. Several of
 * the tests check that an archive that is not part of the project but is under the project root can be imported correctly into the parent
 * project (this is required for importing multi-module projects from the Eclipse ADT to Android Studio).
 */
@Ignore("http://b/35788310")
public class ArchiveToGradleModuleModelTest extends AndroidGradleTestCase {
  public void testFake() {
  }

  private static final String ARCHIVE_DEFAULT_GRADLE_PATH = ":library";
  private static final String ARCHIVE_JAR_NAME = "library.jar";
  private static final String ARCHIVE_JAR_PATH = join("lib", ARCHIVE_JAR_NAME);
  private static final String DEPENDENCY_MODULE_NAME = "withdependency";
  private static final String MULTI_DEPENDENCY_MODULE_NAME = "withmultipledependencies";
  private static final String PARENT_MODULE_NAME = "nested";
  private static final String SIMPLE_MODULE_NAME = "simple";
  private static final String NESTED_MODULE_NAME = "sourcemodule";

  private File myJarOutsideProject;

  private void assertArchiveImportedCorrectly(@NotNull String newModuleGradlePath,
                                              @NotNull File archiveToImport) throws IOException {
    Project project = getProject();

    File defaultSubprojectLocation = GradleUtil.getModuleDefaultPath(project.getBaseDir(), newModuleGradlePath);
    File importedArchive = new File(defaultSubprojectLocation, archiveToImport.getName());
    assertAbout(file()).that(importedArchive).isFile();

    File buildGradle = new File(defaultSubprojectLocation, FN_BUILD_GRADLE);
    assertAbout(file()).that(buildGradle).isFile();
    VirtualFile vFile = VfsUtil.findFileByIoFile(buildGradle, true);
    assertNotNull(vFile);
    assertEquals(VfsUtilCore.loadText(vFile), CreateModuleFromArchiveAction.getBuildGradleText(archiveToImport));

    GradleSettingsModel settingsModel = GradleSettingsModel.get(project);
    assertNotNull(settingsModel);
    List<String> modules = settingsModel.modulePaths();
    assertThat(modules).contains(newModuleGradlePath);
  }

  private static void assertIsValidAndroidBuildModel(@NotNull GradleBuildModel model) {
    assertEquals(1, model.appliedPlugins().stream().filter(name -> name.value().equals("com.android.application")).count());

    assertNotNull(model.android());

    DependenciesModel dependencies = model.dependencies();
    assertNotNull(dependencies);

    List<ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertEquals(1, artifacts.size());
    assertEquals("com.android.support:support-v4:+", artifacts.get(0).compactNotation().value());

    List<FileTreeDependencyModel> fileTrees = dependencies.fileTrees();
    assertEquals(1, fileTrees.size());
    FileTreeDependencyModel fileTreeModel = fileTrees.get(0);
    assertEquals("lib", fileTreeModel.dir().value());
    assertEquals(1, fileTreeModel.includes().size());
    assertEquals("*.jar", fileTreeModel.includes().get(0).value());
  }

  private static void assertHasAddedModuleDependency(@NotNull GradleBuildModel model) {
    DependenciesModel dependencies = model.dependencies();
    assertNotNull(dependencies);

    List<ModuleDependencyModel> modules = dependencies.modules();

    assertEquals(1, modules.size());
    assertEquals(ARCHIVE_DEFAULT_GRADLE_PATH, modules.get(0).path().value());
  }

  @NotNull
  private GradleBuildModel getBuildModel(String moduleName) {
    ModuleManager moduleManager = ModuleManager.getInstance(getProject());

    for (Module module : moduleManager.getModules()) {
      if (moduleName.equals(module.getName())) {
        GradleBuildModel model = GradleBuildModel.get(module);
        assertNotNull(model);
        return model;
      }
    }

    fail("Expected to find module called " + moduleName + "in project.");
    return null;
  }

  @NotNull
  private File loadProjectAndDoImport(@NotNull String relativePath, @NotNull String moduleName, boolean move) throws Exception {
    loadProject(relativePath);
    String archivePath = join(moduleName.equals(NESTED_MODULE_NAME) ? join(PARENT_MODULE_NAME, moduleName) : moduleName, ARCHIVE_JAR_PATH);
    File archiveToImport = new File(getProject().getBasePath(), archivePath);

    ArchiveToGradleModuleModel model = new ArchiveToGradleModuleModel(getProject());
    model.archive().set(archiveToImport.getAbsolutePath());
    model.gradlePath().set(ARCHIVE_DEFAULT_GRADLE_PATH);
    model.moveArchive().set(move);
    model.handleFinished();

    return archiveToImport;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myJarOutsideProject = new File(getTestDataPath(), join(IMPORTING, SIMPLE_MODULE_NAME, ARCHIVE_JAR_PATH));
  }

  public void /*test*/ImportStandaloneArchive() throws Exception {
    ArchiveToGradleModuleModel model = new ArchiveToGradleModuleModel(getProject());
    model.archive().set(myJarOutsideProject.getAbsolutePath());

    model.gradlePath().set(ARCHIVE_DEFAULT_GRADLE_PATH);
    model.handleFinished();

    assertArchiveImportedCorrectly(ARCHIVE_DEFAULT_GRADLE_PATH, myJarOutsideProject);
    assertWithMessage("Source file still exists").that(myJarOutsideProject.isFile()).isTrue();
  }

  public void /*test*/ImportStandaloneArchiveWithCustomPath() throws Exception {
    ArchiveToGradleModuleModel model = new ArchiveToGradleModuleModel(getProject());
    model.archive().set(myJarOutsideProject.getAbsolutePath());

    String gradlePath = ":amodulename";
    model.gradlePath().set(gradlePath);
    model.handleFinished();

    assertArchiveImportedCorrectly(gradlePath, myJarOutsideProject);
    assertWithMessage("Source file still exists").that(myJarOutsideProject.isFile()).isTrue();
  }

  public void /*test*/ImportStandaloneArchiveWithNestedPath() throws Exception {
    ArchiveToGradleModuleModel model = new ArchiveToGradleModuleModel(getProject());
    model.archive().set(myJarOutsideProject.getAbsolutePath());

    String gradlePath = ":category:module";
    model.gradlePath().set(gradlePath);
    model.handleFinished();

    assertArchiveImportedCorrectly(gradlePath, myJarOutsideProject);
    assertWithMessage("Source file still exists").that(myJarOutsideProject.isFile()).isTrue();
  }

  public void /*test*/MoveStandaloneArchive() throws Exception {
    // Have to copy the file so we don't delete test data!
    File archiveToImport = new File(FileUtil.createTempDirectory("archiveLocation", null), ARCHIVE_JAR_NAME);
    copyFileOrDir(myJarOutsideProject, archiveToImport);

    ArchiveToGradleModuleModel model = new ArchiveToGradleModuleModel(getProject());
    model.archive().set(archiveToImport.getAbsolutePath());

    model.gradlePath().set(ARCHIVE_DEFAULT_GRADLE_PATH);
    model.moveArchive().set(true);
    model.handleFinished();

    assertArchiveImportedCorrectly(ARCHIVE_DEFAULT_GRADLE_PATH, archiveToImport);
    assertWithMessage("Source file deleted").that(archiveToImport.isFile()).isFalse();
  }

  public void /*test*/ImportArchiveFromModuleWithinProject() throws Exception {
    String moduleName = SIMPLE_MODULE_NAME;
    File archiveToImport = loadProjectAndDoImport(IMPORTING, moduleName, false);

    assertArchiveImportedCorrectly(ARCHIVE_DEFAULT_GRADLE_PATH, archiveToImport);
    assertWithMessage("Source file still exists").that(archiveToImport.isFile()).isTrue();

    GradleBuildModel buildModel = getBuildModel(moduleName);
    assertIsValidAndroidBuildModel(buildModel);
  }

  public void /*test*/ImportArchiveFromNestedModuleWithinProject() throws Exception {
    String moduleName = NESTED_MODULE_NAME;
    File archiveToImport = loadProjectAndDoImport(IMPORTING, moduleName, false);

    assertArchiveImportedCorrectly(ARCHIVE_DEFAULT_GRADLE_PATH, archiveToImport);
    assertWithMessage("Source file still exists").that(archiveToImport.isFile()).isTrue();

    GradleBuildModel buildModel = getBuildModel(moduleName);
    assertIsValidAndroidBuildModel(buildModel);
  }


  public void /*test*/MoveArchiveFromModuleWithinProject() throws Exception {
    String moduleName = SIMPLE_MODULE_NAME;
    File archiveToImport = loadProjectAndDoImport(IMPORTING, moduleName, true);

    assertArchiveImportedCorrectly(ARCHIVE_DEFAULT_GRADLE_PATH, archiveToImport);
    assertWithMessage("Source file deleted").that(archiveToImport.isFile()).isFalse();

    GradleBuildModel buildModel = getBuildModel(moduleName);
    assertIsValidAndroidBuildModel(buildModel);
    assertHasAddedModuleDependency(buildModel);
  }

  public void /*test*/MoveArchiveFromNestedModuleWithinProject() throws Exception {
    String moduleName = NESTED_MODULE_NAME;
    File archiveToImport = loadProjectAndDoImport(IMPORTING, moduleName, true);

    assertArchiveImportedCorrectly(ARCHIVE_DEFAULT_GRADLE_PATH, archiveToImport);
    assertWithMessage("Source file deleted").that(archiveToImport.isFile()).isFalse();

    GradleBuildModel buildModel = getBuildModel(moduleName);
    assertIsValidAndroidBuildModel(buildModel);
    assertHasAddedModuleDependency(buildModel);
  }

  public void /*test*/MoveArchiveFromModuleWithFileDependencyWithinProject() throws Exception {
    String moduleName = DEPENDENCY_MODULE_NAME;
    File archiveToImport = loadProjectAndDoImport(IMPORTING, moduleName, true);

    assertArchiveImportedCorrectly(ARCHIVE_DEFAULT_GRADLE_PATH, archiveToImport);
    assertWithMessage("Source file deleted").that(archiveToImport.isFile()).isFalse();

    GradleBuildModel buildModel = getBuildModel(moduleName);
    assertIsValidAndroidBuildModel(buildModel);
    assertHasAddedModuleDependency(buildModel);

    // Test that now-unused dependency is stripped
    assertEquals(0, buildModel.dependencies().files().size());
  }

  public void /*test*/MoveArchiveFromModuleWithMultipleFileDependenciesWithinProject() throws Exception {
    String moduleName = MULTI_DEPENDENCY_MODULE_NAME;
    File archiveToImport = loadProjectAndDoImport(IMPORTING, moduleName, true);

    assertArchiveImportedCorrectly(ARCHIVE_DEFAULT_GRADLE_PATH, archiveToImport);
    assertWithMessage("Source file deleted").that(archiveToImport.isFile()).isFalse();

    GradleBuildModel buildModel = getBuildModel(moduleName);
    assertIsValidAndroidBuildModel(buildModel);
    assertHasAddedModuleDependency(buildModel);

    // Test that other dependency is preserved
    assertEquals(1, buildModel.dependencies().files().size());
    assertEquals("some/other/file.jar", buildModel.dependencies().files().get(0).file().value());
  }

  public void /*test*/PropertiesAreStripped() {
    String testString = "some Test String";
    ArchiveToGradleModuleModel model = new ArchiveToGradleModuleModel(getProject());

    model.archive().set(" " + testString + " ");
    assertEquals(testString, model.archive().get());

    model.gradlePath().set(" " + testString + " ");
    assertEquals(testString, model.gradlePath().get());
  }
}