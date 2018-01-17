/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync;

import com.android.tools.idea.gradle.project.sync.setup.post.PluginVersionUpgrade;
import com.android.tools.idea.testing.IdeComponents;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import static com.android.SdkConstants.DOT_GRADLE;
import static com.android.tools.idea.gradle.project.sync.LibraryDependenciesSubject.libraryDependencies;
import static com.android.tools.idea.gradle.project.sync.ModuleDependenciesSubject.moduleDependencies;
import static com.android.tools.idea.testing.AndroidGradleTests.*;
import static com.android.tools.idea.testing.AndroidGradleTests.updateLocalRepositories;
import static com.android.tools.idea.testing.AndroidGradleTests.updateTargetSdkVersion;
import static com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH1_DOT5;
import static com.android.tools.idea.testing.TestProjectPaths.TRANSITIVE_DEPENDENCIES_PRE30;
import static com.google.common.io.Files.write;
import static com.google.common.truth.Truth.assertAbout;
import static com.intellij.openapi.roots.DependencyScope.COMPILE;
import static com.intellij.openapi.roots.DependencyScope.PROVIDED;
import static com.intellij.openapi.util.io.FileUtil.notNullize;
import static org.jetbrains.plugins.gradle.settings.DistributionType.DEFAULT_WRAPPED;
import static org.mockito.Mockito.mock;

/**
 * Integration test for Gradle Sync with old versions of Android plugin.
 */
public class GradleSyncWithOlderPluginTest extends GradleSyncIntegrationTestCase {
  private TestSettings myTestSettings;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Project project = getProject();

    // We don't want the IDE to offer a plugin version upgrade.
    IdeComponents.replaceService(project, PluginVersionUpgrade.class, mock(PluginVersionUpgrade.class));

    GradleProjectSettings projectSettings = new GradleProjectSettings();
    projectSettings.setDistributionType(DEFAULT_WRAPPED);
    GradleSettings.getInstance(project).setLinkedProjectsSettings(Collections.singletonList(projectSettings));
    // Most of the tests in this class share the same settings, create in setUp for convenience, each test can overwrite the settings.
    myTestSettings = new TestSettings("2.4", "1.5.0");
  }

  @Override
  protected boolean useNewSyncInfrastructure() {
    return false;
  }

  @Override
  @NotNull
  protected File prepareProjectForImport(@NotNull String relativePath) throws IOException {
    File projectRoot = super.prepareProjectForImport(relativePath);
    createGradleWrapper(projectRoot, myTestSettings.gradleVersion);
    return projectRoot;
  }

  @Override
  protected void createGradleWrapper(@NotNull File projectRoot) throws IOException {
    // Do not create the Gradle wrapper automatically. Let each test method create it with the version of Gradle needed.
  }

  @Override
  protected void updateVersionAndDependencies(@NotNull File projectRoot) throws IOException {
    // In this overriden version we don't update versions of the Android plugin and use the one specified in the test project.
    updateVersionAndDependencies(projectRoot, getLocalRepositories());
  }

  private static void resetActivityMain(@NotNull File path) throws IOException {
    String contents = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                      "<android.support.constraint.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                      "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                      "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                      "    android:id=\"@+id/activity_main\"\n" +
                      "    android:layout_width=\"match_parent\"\n" +
                      "    android:layout_height=\"match_parent\">\n" +
                      "</android.support.constraint.ConstraintLayout>";
    write(contents, path, Charsets.UTF_8);
  }

  private void updateVersionAndDependencies(@NotNull File path, @NotNull String localRepositories) throws IOException {
    if (path.isDirectory()) {
      for (File child : notNullize(path.listFiles())) {
        updateVersionAndDependencies(child, localRepositories);
      }
    }
    else if (path.getPath().endsWith(DOT_GRADLE) && path.isFile()) {
      String contentsOrig = Files.toString(path, Charsets.UTF_8);
      String contents = contentsOrig;

      contents = replaceRegexGroup(contents, "classpath ['\"]com.android.tools.build:gradle:(.+)['\"]", myTestSettings.pluginVersion);
      if (myTestSettings.removeConstraintLayout) {
        // Remove constraint-layout, which was not supported by old plugins.
        contents = replaceRegexGroup(contents, "(compile 'com.android.support.constraint:constraint-layout:\\+')", "");
      }
      contents = updateBuildToolsVersion(contents);
      contents = updateCompileSdkVersion(contents);
      contents = updateTargetSdkVersion(contents);
      contents = updateLocalRepositories(contents, localRepositories);

      if (!contents.equals(contentsOrig)) {
        write(contents, path, Charsets.UTF_8);
      }
    }
    else if (myTestSettings.resetActivityMain && path.getName().equals("activity_main.xml")) {
      resetActivityMain(path);
    }
  }

  // Syncs a project with Android plugin 1.5.0 and Gradle 2.2.1
  public void testWithPluginOneDotFive() throws Exception {
    myTestSettings.resetActivityMain = false;
    // We are verifying that sync succeeds without errors.
    loadProject(PROJECT_WITH1_DOT5);
  }

  public void testWithInterAndroidModuleDependencies() throws Exception {
    loadProject(TRANSITIVE_DEPENDENCIES_PRE30);
    Module appModule = myModules.getAppModule();
    // 'app' -> 'library2'
    // Verify app module has library2 as module dependency and exporting it to consumer modules.
    assertAbout(moduleDependencies()).that(appModule).hasDependency("library2", COMPILE, true);
  }

  public void testWithInterJavaModuleDependencies() throws Exception {
    loadProject(TRANSITIVE_DEPENDENCIES_PRE30);
    Module appModule = myModules.getAppModule();
    // 'app' -> 'lib'
    // dependency should be set on the module not the compiled jar.
    assertAbout(moduleDependencies()).that(appModule).hasDependency("lib", COMPILE, true);
    assertAbout(libraryDependencies()).that(appModule).doesNotContain("lib", COMPILE);
  }

  public void testJavaLibraryDependenciesFromJavaModule() throws Exception {
    loadProject(TRANSITIVE_DEPENDENCIES_PRE30);
    Module javaLibModule = myModules.getModule("lib");
    // 'app' -> 'lib' -> 'guava'
    // For older versions of plugin, app might not direclty contain guava as library dependency.
    // Make sure lib has guava as library dependency, and exported is set to true, so that app has access to guava.
    assertAbout(libraryDependencies()).that(javaLibModule).containsMatching(true, "guava.*", COMPILE, PROVIDED);
  }

  public void testLocalJarDependenciesFromAndroidModule() throws Exception {
    loadProject(TRANSITIVE_DEPENDENCIES_PRE30);
    Module androidLibModule = myModules.getModule("library2");
    // 'app' -> 'library2' -> 'fakelib.jar'
    // Make sure library2 has fakelib as library dependency, and exported is set to true, so that app has access to fakelib.
    assertAbout(libraryDependencies()).that(androidLibModule).containsMatching(true, ".*fakelib.*", COMPILE);
  }

  public void testJavaLibraryDependenciesFromAndroidModule() throws Exception {
    loadProject(TRANSITIVE_DEPENDENCIES_PRE30);
    Module androidLibModule = myModules.getModule("library2");
    // 'app' -> 'library2' -> 'gson'
    // Make sure library2 has gson as library dependency, and exported is set to true, so that app has access to gson.
    assertAbout(libraryDependencies()).that(androidLibModule).containsMatching(true, ".*gson.*", COMPILE);
  }

  public void testAndroidModuleDependenciesFromAndroidModule() throws Exception {
    loadProject(TRANSITIVE_DEPENDENCIES_PRE30);
    Module androidLibModule = myModules.getModule("library2");
    // 'app' -> 'library2' -> 'library1'
    assertAbout(moduleDependencies()).that(androidLibModule).hasDependency("library1", COMPILE, true);
  }

  public void testAndroidLibraryDependenciesFromAndroidModule() throws Exception {
    loadProject(TRANSITIVE_DEPENDENCIES_PRE30);
    Module androidLibModule = myModules.getModule("library1");
    // 'app' -> 'library2' -> 'library1' -> 'commons-io'
    assertAbout(libraryDependencies()).that(androidLibModule).containsMatching(true, ".*commons-io.*", COMPILE);
  }

  private static class TestSettings {
    @NotNull final String gradleVersion;
    @NotNull final String pluginVersion;
    private boolean removeConstraintLayout = true; // Remove dependencies on constraint-layout, which was not supported in old plugins.
    private boolean resetActivityMain = true;

    public TestSettings(@NotNull String gradleVersion, @NotNull String pluginVersion) {
      this.gradleVersion = gradleVersion;
      this.pluginVersion = pluginVersion;
    }
  }
}
