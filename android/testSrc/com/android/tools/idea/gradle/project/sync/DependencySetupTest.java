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
package com.android.tools.idea.gradle.project.sync;

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.project.facet.java.JavaFacet;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessagesStub;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.Modules;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LeakHunter;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.io.File;
import java.util.Collections;

import static com.android.tools.idea.gradle.dsl.model.dependencies.CommonConfigurationNames.COMPILE;
import static com.android.tools.idea.gradle.project.sync.LibraryDependenciesSubject.libraryDependencies;
import static com.android.tools.idea.gradle.project.sync.ModuleDependenciesSubject.moduleDependencies;
import static com.android.tools.idea.gradle.util.GradleUtil.getAndroidProject;
import static com.android.tools.idea.testing.TestProjectPaths.*;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static org.jetbrains.plugins.gradle.settings.DistributionType.DEFAULT_WRAPPED;

/**
 * Tests dependency configuration during Gradle Sync.
 */
public class DependencySetupTest extends AndroidGradleTestCase {
  private Modules myModules;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Project project = getProject();
    myModules = new Modules(project);

    GradleProjectSettings projectSettings = new GradleProjectSettings();
    projectSettings.setDistributionType(DEFAULT_WRAPPED);

    GradleSettings.getInstance(project).setLinkedProjectsSettings(Collections.singletonList(projectSettings));
  }
  @Override
  protected void tearDown() throws Exception {
    myModules = null;
    //noinspection SuperTearDownInFinally
    super.tearDown();
    LeakHunter.checkLeak(LeakHunter.allRoots(), AndroidModuleModel.class, null);
  }

  public void testWithNonExistingInterModuleDependencies() throws Exception {
    loadSimpleApplication();

    Module appModule = myModules.getAppModule();
    GradleBuildModel buildModel = GradleBuildModel.get(appModule);
    assertNotNull(buildModel);
    buildModel.dependencies().addModule(COMPILE, ":fakeLibrary");
    runWriteCommandAction(getProject(), buildModel::applyChanges);

    String failure = requestSyncAndGetExpectedFailure();
    assertThat(failure).startsWith("Project with path ':fakeLibrary' could not be found");

    // TODO verify that a message and "quick fix" has been displayed.
  }

  // Fails in Bazel PSQ because of missing dependencies in the prebuilt Android SDK.
  public void /*test*/WithUnresolvedDependencies() throws Exception {
    loadSimpleApplication();

    File buildFilePath = getBuildFilePath("app");
    VirtualFile buildFile = findFileByIoFile(buildFilePath, true);
    assertNotNull(buildFile);

    boolean versionChanged = false;

    Project project = getProject();
    GradleBuildModel buildModel = GradleBuildModel.parseBuildFile(buildFile, project);

    for (ArtifactDependencyModel artifact : buildModel.dependencies().artifacts()) {
      if ("com.android.support".equals(artifact.group().value()) && "appcompat-v7".equals(artifact.name().value())) {
        artifact.setVersion("100.0.0");
        versionChanged = true;
        break;
      }
    }
    assertTrue(versionChanged);

    runWriteCommandAction(project, buildModel::applyChanges);
    LocalFileSystem.getInstance().refresh(false /* synchronous */);

    SyncMessagesStub syncMessages = SyncMessagesStub.replaceSyncMessagesService(project);

    requestSyncAndWait();

    SyncMessage reportedMessage = syncMessages.getFirstReportedMessage();
    assertNotNull(reportedMessage);
    String[] text = reportedMessage.getText();
    assertThat(text).isNotEmpty();
    assertEquals("Failed to resolve: com.android.support:appcompat-v7:100.0.0", text[0]);
  }

  public void testWithLocalAarsAsModules() throws Exception {
    loadProject(LOCAL_AARS_AS_MODULES);

    Module localAarModule = myModules.getModule("library-debug");

    // When AAR files are exposed as artifacts, they don't have an AndroidProject model.
    AndroidFacet androidFacet = AndroidFacet.getInstance(localAarModule);
    assertNull(androidFacet);
    assertNull(getAndroidProject(localAarModule));

    // Should not expose the AAR as library, instead it should use the "exploded AAR".
    assertAbout(libraryDependencies()).that(localAarModule).isEmpty();

    Module appModule = myModules.getAppModule();
    assertAbout(libraryDependencies()).that(appModule).contains("Gradle: library-debug-unspecified");
  }

  public void testWithLocalJarsArModules() throws Exception {
    loadProject(LOCAL_JARS_AS_MODULES);

    Module localJarModule = myModules.getModule("localJarAsModule");
    // Module should be a Java module, not buildable (since it doesn't have source code).
    JavaFacet javaFacet = JavaFacet.getInstance(localJarModule);
    assertNotNull(javaFacet);
    assertFalse(javaFacet.getConfiguration().BUILDABLE);

    assertAbout(libraryDependencies()).that(localJarModule).contains("Gradle: localJarAsModule.local");
  }

  public void testWithInterModuleDependencies() throws Exception {
    loadProject(TRANSITIVE_DEPENDENCIES);

    Module appModule = myModules.getAppModule();
    assertAbout(moduleDependencies()).that(appModule).contains("library2");
  }

  // See: https://code.google.com/p/android/issues/detail?id=210172
  public void /*test*/TransitiveDependenciesFromJavaModule() throws Exception {
    loadProject(TRANSITIVE_DEPENDENCIES);
    Module appModule = myModules.getAppModule();

    // 'app' module should have 'guava' as dependency.
    // 'app' -> 'lib' -> 'guava'
    assertAbout(libraryDependencies()).that(appModule).contains("Gradle: guava-17.0");
  }

  // See: https://code.google.com/p/android/issues/detail?id=212338
  public void /*test*/TransitiveDependenciesFromAndroidModule() throws Exception {
    loadProject(TRANSITIVE_DEPENDENCIES);
    Module appModule = myModules.getAppModule();

    // 'app' module should have 'javawriter' as dependency.
    // 'app' -> 'library2' -> 'library1' -> 'javawriter'
    assertAbout(libraryDependencies()).that(appModule).contains("Gradle: javawriter-2.5.0");
  }

  // See: https://code.google.com/p/android/issues/detail?id=212557
  public void testTransitiveAndroidModuleDependency() throws Exception {
    loadProject(TRANSITIVE_DEPENDENCIES);
    Module appModule = myModules.getAppModule();

    // 'app' module should have 'library1' as module dependency.
    // 'app' -> 'library2' -> 'library1'
    assertAbout(moduleDependencies()).that(appModule).contains("library1");
  }

  public void testJavaLibraryModuleDependencies() throws Exception {
    loadProject(TRANSITIVE_DEPENDENCIES);
    Module appModule = myModules.getAppModule();

    // dependency should be set on the module not the compiled jar.
    assertAbout(moduleDependencies()).that(appModule).contains("lib");
    assertAbout(libraryDependencies()).that(appModule).doesNotContain("Gradle: lib");
  }

  public void testDependencySetUpInJavaModule() throws Exception {
    loadProject(TRANSITIVE_DEPENDENCIES);
    Module libModule = myModules.getModule("lib");
    assertAbout(libraryDependencies()).that(libModule).doesNotContain("Gradle: lib.lib");
  }

  // See: https://code.google.com/p/android/issues/detail?id=213627
  public void testJarsInLibsFolder() throws Exception {
    loadProject(TRANSITIVE_DEPENDENCIES);

    // 'fakelib' is in 'libs' directory in 'library2' module.
    Module library2Module = myModules.getModule("library2");
    assertAbout(libraryDependencies()).that(library2Module).contains("Gradle: fakelib");

    // 'app' module should have 'fakelib' as dependency.
    // 'app' -> 'library2' -> 'fakelib'
    Module appModule = myModules.getAppModule();
    assertAbout(libraryDependencies()).that(appModule).contains("Gradle: fakelib");
  }
}
