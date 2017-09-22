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

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.FileTreeDependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ModuleDependencyModel;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.tools.idea.gradle.util.GradleUtil.getModuleDefaultPath;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.android.tools.idea.testing.TestProjectPaths.IMPORTING;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.loadText;

/**
 * This class tests {@link ArchiveToGradleModuleModel}. It is testing that a JAR or AAR can be imported into a project correctly. Several of
 * the tests check that an archive that is not part of the project but is under the project root can be imported correctly into the parent
 * project (this is required for importing multi-module projects from the Eclipse ADT to Android Studio).
 */
public class ArchiveToGradleModuleModelTest extends AndroidGradleTestCase {

  private File myJarOutsideProject;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myJarOutsideProject = new File(getTestDataPath(), join(IMPORTING, "simple", "lib", "library.jar"));
  }

  public void testImportStandaloneArchive() throws Exception {
    ArchiveToGradleModuleModel model = new ArchiveToGradleModuleModel(getProject());
    model.archive().set(myJarOutsideProject.getAbsolutePath());

    model.gradlePath().set(":library");
    model.handleFinished();

    assertArchiveImportedCorrectly(":library", myJarOutsideProject);
    assertAbout(file()).that(myJarOutsideProject).isFile();
  }

  public void testImportStandaloneArchiveWithCustomPath() throws Exception {
    ArchiveToGradleModuleModel model = new ArchiveToGradleModuleModel(getProject());
    model.archive().set(myJarOutsideProject.getAbsolutePath());

    String gradlePath = ":amodulename";
    model.gradlePath().set(gradlePath);
    model.handleFinished();

    assertArchiveImportedCorrectly(gradlePath, myJarOutsideProject);
    assertAbout(file()).that(myJarOutsideProject).isFile();
  }

  public void testImportStandaloneArchiveWithNestedPath() throws Exception {
    ArchiveToGradleModuleModel model = new ArchiveToGradleModuleModel(getProject());
    model.archive().set(myJarOutsideProject.getAbsolutePath());

    String gradlePath = ":category:module";
    model.gradlePath().set(gradlePath);
    model.handleFinished();

    assertArchiveImportedCorrectly(gradlePath, myJarOutsideProject);
    assertAbout(file()).that(myJarOutsideProject).isFile();
  }

  public void testMoveStandaloneArchive() throws Exception {
    // Have to copy the file so we don't delete test data!
    File archiveToImport = new File(createTempDirectory("archiveLocation", null), "library.jar");
    copyFileOrDir(myJarOutsideProject, archiveToImport);

    ArchiveToGradleModuleModel model = new ArchiveToGradleModuleModel(getProject());
    model.archive().set(archiveToImport.getAbsolutePath());

    model.gradlePath().set(":library");
    model.moveArchive().set(true);
    model.handleFinished();

    assertArchiveImportedCorrectly(":library", archiveToImport);
    assertAbout(file()).that(archiveToImport).doesNotExist();
  }

  public void testImportArchiveFromModuleWithinProject() throws Exception {
    String moduleName = "simple";
    File archiveToImport = loadProjectAndDoImport(IMPORTING, moduleName, false);

    assertArchiveImportedCorrectly(":library", archiveToImport);
    assertAbout(file()).that(archiveToImport).isFile();

    GradleBuildModel buildModel = getBuildModel(moduleName);
    assertIsValidBuildModel(buildModel);
  }

  public void testImportArchiveFromNestedModuleWithinProject() throws Exception {
    String moduleName = "sourcemodule";
    File archiveToImport = loadProjectAndDoImport(IMPORTING, moduleName, false);

    assertArchiveImportedCorrectly(":library", archiveToImport);
    assertAbout(file()).that(archiveToImport).isFile();

    GradleBuildModel buildModel = getBuildModel(moduleName);
    assertIsValidBuildModel(buildModel);
  }

  public void testMoveArchiveFromModuleWithinProject() throws Exception {
    String moduleName = "simple";
    File archiveToImport = loadProjectAndDoImport(IMPORTING, moduleName, true);

    assertArchiveImportedCorrectly(":library", archiveToImport);
    assertAbout(file()).that(archiveToImport).doesNotExist();

    GradleBuildModel buildModel = getBuildModel(moduleName);
    assertIsValidBuildModel(buildModel);
    assertHasAddedModuleDependency(buildModel);
  }

  public void testMoveArchiveFromNestedModuleWithinProject() throws Exception {
    String moduleName = "sourcemodule";
    File archiveToImport = loadProjectAndDoImport(IMPORTING, moduleName, true);

    assertArchiveImportedCorrectly(":library", archiveToImport);
    assertAbout(file()).that(archiveToImport).doesNotExist();

    GradleBuildModel buildModel = getBuildModel(moduleName);
    assertIsValidBuildModel(buildModel);
    assertHasAddedModuleDependency(buildModel);
  }

  public void testMoveArchiveFromModuleWithFileDependencyWithinProject() throws Exception {
    String moduleName = "withdependency";
    File archiveToImport = loadProjectAndDoImport(IMPORTING, moduleName, true);

    assertArchiveImportedCorrectly(":library", archiveToImport);
    assertAbout(file()).that(archiveToImport).doesNotExist();

    GradleBuildModel buildModel = getBuildModel(moduleName);
    assertIsValidBuildModel(buildModel);
    assertHasAddedModuleDependency(buildModel);

    // Test that now-unused dependency is stripped
    assertEquals(0, buildModel.dependencies().files().size());
  }

  public void testMoveArchiveFromModuleWithMultipleFileDependenciesWithinProject() throws Exception {
    String moduleName = "withmultipledependencies";
    File archiveToImport = loadProjectAndDoImport(IMPORTING, moduleName, true);

    assertArchiveImportedCorrectly(":library", archiveToImport);
    assertAbout(file()).that(archiveToImport).doesNotExist();

    GradleBuildModel buildModel = getBuildModel(moduleName);
    assertIsValidBuildModel(buildModel);
    assertHasAddedModuleDependency(buildModel);

    // Test that other dependency is preserved
    assertThat(buildModel.dependencies().files()).hasSize(1);
    assertEquals("some/other/file.jar", buildModel.dependencies().files().get(0).file().value());
  }

  public void testPropertiesAreStripped() {
    String testString = "some Test String";
    ArchiveToGradleModuleModel model = new ArchiveToGradleModuleModel(getProject());

    model.archive().set(" " + testString + " ");
    assertEquals(testString, model.archive().get());

    model.gradlePath().set(" " + testString + " ");
    assertEquals(testString, model.gradlePath().get());
  }

  private void assertArchiveImportedCorrectly(@NotNull String newModuleGradlePath,
                                              @NotNull File archiveToImport) throws IOException {
    Project project = getProject();

    File defaultSubprojectLocation = getModuleDefaultPath(project.getBaseDir(), newModuleGradlePath);
    File importedArchive = new File(defaultSubprojectLocation, archiveToImport.getName());
    assertAbout(file()).that(importedArchive).isFile();

    File buildGradle = new File(defaultSubprojectLocation, FN_BUILD_GRADLE);
    assertAbout(file()).that(buildGradle).isFile();
    VirtualFile vFile = findFileByIoFile(buildGradle, true);
    assertNotNull(vFile);
    assertEquals(loadText(vFile), CreateModuleFromArchiveAction.getBuildGradleText(archiveToImport));

    GradleSettingsModel settingsModel = GradleSettingsModel.get(project);
    assertNotNull(settingsModel);
    List<String> modules = settingsModel.modulePaths();
    assertThat(modules).contains(newModuleGradlePath);
  }

  // Checks that the model obtained from the generated build file has the expected values. This verifies that properties such as the plugin
  // used and the dependencies present in the original file are correctly preserved in the new build file generated by the import.
  private static void assertIsValidBuildModel(@NotNull GradleBuildModel model) {
    assertEquals(1, model.appliedPlugins().stream().filter(name -> name.value().equals("java")).count());

    DependenciesModel dependencies = model.dependencies();
    assertNotNull(dependencies);

    List<? extends ArtifactDependencyModel> artifacts = dependencies.artifacts();
    assertThat(artifacts).hasSize(1);
    assertEquals("com.google.guava:guava:22.0", artifacts.get(0).compactNotation().value());

    List<? extends FileTreeDependencyModel> fileTrees = dependencies.fileTrees();
    assertThat(fileTrees).hasSize(1);
    FileTreeDependencyModel fileTreeModel = fileTrees.get(0);
    assertEquals("lib", fileTreeModel.dir().value());
    assertThat(fileTreeModel.includes()).hasSize(1);
    assertEquals("*.jar", fileTreeModel.includes().get(0).value());
  }

  private static void assertHasAddedModuleDependency(@NotNull GradleBuildModel model) {
    DependenciesModel dependencies = model.dependencies();
    assertNotNull(dependencies);

    List<? extends ModuleDependencyModel> modules = dependencies.modules();

    assertThat(modules).hasSize(1);
    assertEquals(":library", modules.get(0).path().value());
  }

  @NotNull
  private GradleBuildModel getBuildModel(String moduleName) {
    Module module = getModule(moduleName);
    GradleBuildModel model = GradleBuildModel.get(module);
    assertNotNull(model);
    return model;
  }

  @NotNull
  private File loadProjectAndDoImport(@NotNull String relativePath, @NotNull String moduleName, boolean move) throws Exception {
    loadProject(relativePath);
    if (moduleName.equals("sourcemodule")) {
      moduleName = join("nested", moduleName);
    }
    String archivePath = join(moduleName, "lib", "library.jar");
    File archiveToImport = new File(getProject().getBasePath(), archivePath);

    ArchiveToGradleModuleModel model = new ArchiveToGradleModuleModel(getProject());
    model.archive().set(archiveToImport.getAbsolutePath());
    model.gradlePath().set(":library");
    model.moveArchive().set(move);
    model.handleFinished();

    return archiveToImport;
  }
}