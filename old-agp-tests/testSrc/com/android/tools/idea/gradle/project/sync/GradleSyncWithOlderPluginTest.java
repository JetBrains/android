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
package com.android.tools.idea.gradle.project.sync;

import static com.android.tools.idea.gradle.project.sync.LibraryDependenciesSubject.libraryDependencies;
import static com.android.tools.idea.gradle.project.sync.ModuleDependenciesSubject.moduleDependencies;
import static com.android.tools.idea.testing.HighlightInfos.getHighlightInfos;
import static com.android.tools.idea.testing.TestModuleUtil.findModule;
import static com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH1_DOT5;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION_PRE30;
import static com.android.tools.idea.testing.TestProjectPaths.TRANSITIVE_DEPENDENCIES_PRE30;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.roots.DependencyScope.COMPILE;
import static com.intellij.openapi.roots.DependencyScope.PROVIDED;
import static com.intellij.openapi.util.io.FileUtil.createTempDirectory;
import static org.jetbrains.plugins.gradle.settings.DistributionType.DEFAULT_WRAPPED;

import com.android.ide.common.gradle.model.IdeAndroidArtifactOutput;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.testing.TestModuleUtil;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.idea.Bombed;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.internal.daemon.GradleDaemonServices;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

/**
 * Integration test for Gradle Sync with old versions of Android plugin.
 */
@Bombed(year = 2021, month = Calendar.MAY, day = 20, user = "Andrei.Kuznetsov",
  description = "Gradle 2.6 is too old to run on Java11.")
public class GradleSyncWithOlderPluginTest extends GradleSyncIntegrationTestCase {

  private static final String myGradleVersion = "2.6";
  private static final String myPluginVersion = "1.5.0";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Project project = getProject();

    GradleProjectSettings projectSettings = new GradleProjectSettings();
    projectSettings.setDistributionType(DEFAULT_WRAPPED);
    GradleSettings.getInstance(project).setLinkedProjectsSettings(Collections.singletonList(projectSettings));
  }

  private void loadProjectWithOlderPlugin(@NotNull String relativePath) throws Exception {
    loadProject(relativePath, null, myGradleVersion, myPluginVersion);
  }

  // Syncs a project with Android plugin 1.5.0 and Gradle 2.2.1
  // Disabled due to https://github.com/gradle/gradle/issues/8431
  public void /*test*/WithPluginOneDotFive() throws Exception {
    // We are verifying that sync succeeds without errors.
    loadProjectWithOlderPlugin(PROJECT_WITH1_DOT5);
  }

  // Disabled due to https://github.com/gradle/gradle/issues/8431
  public void /*test*/GetOutputFileWithPluginOneDotFive() throws Exception {
    loadProjectWithOlderPlugin(PROJECT_WITH1_DOT5);
    requestSyncAndWait();
    Module appModule = TestModuleUtil.findAppModule(getProject());
    AndroidModuleModel androidModel = AndroidModuleModel.get(appModule);
    assertNotNull(androidModel);
    @SuppressWarnings("deprecation")
    List<IdeAndroidArtifactOutput> outputs = androidModel.getMainArtifact().getOutputs();
    assertNotEmpty(outputs);
    assertThat(outputs.iterator().next().getOutputFile().getName()).isEqualTo("app-debug.apk");
  }

  // Disabled due to https://github.com/gradle/gradle/issues/8431
  public void /*test*/WithInterAndroidModuleDependencies() throws Exception {
    loadProjectWithOlderPlugin(TRANSITIVE_DEPENDENCIES_PRE30);
    Module appModule = TestModuleUtil.findAppModule(getProject());
    // 'app' -> 'library2'
    // Verify app module has library2 as module dependency and exporting it to consumer modules.
    assertAbout(moduleDependencies()).that(appModule).hasDependency(findModule(getProject(), "library2").getName(), COMPILE, true);
  }

  // Disabled due to https://github.com/gradle/gradle/issues/8431
  public void /*test*/WithInterJavaModuleDependencies() throws Exception {
    loadProjectWithOlderPlugin(TRANSITIVE_DEPENDENCIES_PRE30);
    Module appModule = TestModuleUtil.findAppModule(getProject());
    // 'app' -> 'lib'
    // dependency should be set on the module not the compiled jar.
    assertAbout(moduleDependencies()).that(appModule).hasDependency(findModule(getProject(), "javalib1").getName(), COMPILE, true);
    assertAbout(libraryDependencies()).that(appModule).doesNotContain("javalib1", COMPILE);
  }

  // Disabled due to https://github.com/gradle/gradle/issues/8431
  public void /*test*/JavaLibraryDependenciesFromJavaModule() throws Exception {
    loadProjectWithOlderPlugin(TRANSITIVE_DEPENDENCIES_PRE30);
    Module javaLibModule = findModule(getProject(), "javalib1");
    // 'app' -> 'javalib1' -> 'guava'
    // For older versions of plugin, app might not directly contain guava as library dependency.
    // Make sure lib has guava as library dependency, and exported is set to true, so that app has access to guava.
    assertAbout(libraryDependencies()).that(javaLibModule).containsMatching(true, ".*guava.*", COMPILE, PROVIDED);
    assertAbout(moduleDependencies()).that(javaLibModule).hasDependency(findModule(getProject(), "javalib2").getName(), COMPILE, true);
  }

  // Disabled due to https://github.com/gradle/gradle/issues/8431
  public void /*test*/LocalJarDependenciesFromAndroidModule() throws Exception {
    loadProjectWithOlderPlugin(TRANSITIVE_DEPENDENCIES_PRE30);
    Module androidLibModule = findModule(getProject(), "library2");
    // 'app' -> 'library2' -> 'fakelib.jar'
    // Make sure library2 has fakelib as library dependency, and exported is set to true, so that app has access to fakelib.
    assertAbout(libraryDependencies()).that(androidLibModule).containsMatching(true, ".*fakelib.*", COMPILE);
  }

  public void /*test*/JavaLibraryDependenciesFromAndroidModule() throws Exception {
    loadProjectWithOlderPlugin(TRANSITIVE_DEPENDENCIES_PRE30);
    Module androidLibModule = findModule(getProject(), "library2");
    // 'app' -> 'library2' -> 'gson'
    // Make sure library2 has gson as library dependency, and exported is set to true, so that app has access to gson.
    assertAbout(libraryDependencies()).that(androidLibModule).containsMatching(true, ".*gson.*", COMPILE);
  }

  // Disabled due to https://github.com/gradle/gradle/issues/8431
  public void /*test*/AndroidModuleDependenciesFromAndroidModule() throws Exception {
    loadProjectWithOlderPlugin(TRANSITIVE_DEPENDENCIES_PRE30);
    Module androidLibModule = findModule(getProject(), "library2");
    // 'app' -> 'library2' -> 'library1'
    assertAbout(moduleDependencies()).that(androidLibModule).hasDependency(findModule(getProject(), "library1").getName(), COMPILE, true);
  }

  // Disabled due to https://github.com/gradle/gradle/issues/8431
  public void /*test*/AndroidLibraryDependenciesFromAndroidModule() throws Exception {
    loadProjectWithOlderPlugin(TRANSITIVE_DEPENDENCIES_PRE30);
    Module androidLibModule = findModule(getProject(), "library1");
    // 'app' -> 'library2' -> 'library1' -> 'commons-io'
    assertAbout(libraryDependencies()).that(androidLibModule).containsMatching(true, ".*commons-io.*", COMPILE);
  }

  // Disabled due to https://github.com/gradle/gradle/issues/8431
  public void /*test*/SyncWithGradleBuildCacheUninitialized() throws Exception {
    prepareProjectForImport(TRANSITIVE_DEPENDENCIES_PRE30, myGradleVersion, myPluginVersion, null);
    Project project = getProject();
    BuildCacheSyncTest.setBuildCachePath(createTempDirectory("build-cache", ""), project);

    importProject();

    File mainActivityFile = new File("app/src/main/java/com/example/alruiz/transitive_dependencies/MainActivity.java");
    Predicate<HighlightInfo> matchByDescription = info -> "Cannot resolve symbol 'AppCompatActivity'".equals(info.getDescription());
    List<HighlightInfo> highlights = getHighlightInfos(project, mainActivityFile, matchByDescription);

    // It is expected that symbols in AppCompatActivity cannot be resolved yet, since AARs have not been exploded yet.
    assertThat(highlights).isNotEmpty();
    // Generate sources to explode AARs in build cache.
    generateSources();

    highlights = getHighlightInfos(project, mainActivityFile, matchByDescription);
    // All symbols in AppCompatActivity should be resolved now.
    assertThat(highlights).isEmpty();
  }

  /**
   * Verify that Gradle daemons can be stopped for Gradle 3.5 (b/155991417).
   * @throws Exception
   */
  // Disabled due to https://github.com/gradle/gradle/issues/8431
  public void /*test*/DaemonStops3Dot5() throws Exception {
    loadProject(SIMPLE_APPLICATION_PRE30, null, "3.5", "2.2.0");
    verifyDaemonStops();
  }

  /**
   * Verify that Gradle daemons can be stopped for Gradle 4.5 (b/155991417).
   * @throws Exception
   */
  // Disabled due to https://github.com/gradle/gradle/issues/8431
  public void /*test*/DaemonStops4Dot5() throws Exception {
    loadProject(SIMPLE_APPLICATION, null, "4.5", "3.0.0");
    verifyDaemonStops();
  }

  /**
   * Verify that Gradle daemons can be stopped for Gradle 5.3.1 (b/155991417).
   * @throws Exception
   */
  public void testDaemonStops5Dot3Dot1() throws Exception {
    loadProject(SIMPLE_APPLICATION, null, "5.3.1", "3.3.2");
    verifyDaemonStops();
  }

  void verifyDaemonStops() throws Exception {
    GradleDaemonServices.stopDaemons();
    assertThat(areGradleDaemonsRunning()).isFalse();
    requestSyncAndWait();
    assertThat(areGradleDaemonsRunning()).isTrue();
    GradleDaemonServices.stopDaemons();
    assertThat(areGradleDaemonsRunning()).isFalse();
  }
}
