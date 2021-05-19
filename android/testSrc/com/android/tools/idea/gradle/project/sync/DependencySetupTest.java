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
import static com.android.tools.idea.testing.AndroidGradleTests.SyncIssuesPresentError;
import static com.android.tools.idea.testing.TestProjectPaths.LOCAL_AARS_AS_MODULES;
import static com.android.tools.idea.testing.TestProjectPaths.LOCAL_JARS_AS_MODULES;
import static com.android.tools.idea.testing.TestProjectPaths.TRANSITIVE_DEPENDENCIES;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static com.intellij.openapi.externalSystem.service.notification.NotificationCategory.WARNING;
import static com.intellij.openapi.roots.DependencyScope.COMPILE;
import static com.intellij.openapi.roots.DependencyScope.PROVIDED;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static org.jetbrains.plugins.gradle.settings.DistributionType.DEFAULT_WRAPPED;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.project.facet.java.JavaFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.testing.TestModuleUtil;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LeakHunter;
import java.io.File;
import java.util.Collections;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

/**
 * Tests dependency configuration during Gradle Sync.
 */
public class DependencySetupTest extends GradleSyncIntegrationTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();

    GradleProjectSettings projectSettings = new GradleProjectSettings();
    projectSettings.setDistributionType(DEFAULT_WRAPPED);

    GradleSettings.getInstance(getProject()).setLinkedProjectsSettings(Collections.singletonList(projectSettings));
  }

  @Override
  protected void tearDown() throws Exception {
    //noinspection SuperTearDownInFinally
    super.tearDown();
    LeakHunter.checkLeak(LeakHunter.allRoots(), AndroidModuleModel.class, null);
  }

  public void testWithNonExistingInterModuleDependencies() throws Exception {
    loadSimpleApplication();

    Module appModule = TestModuleUtil.findAppModule(getProject());
    GradleBuildModel buildModel = GradleBuildModel.get(appModule);
    assertNotNull(buildModel);
    buildModel.dependencies().addModule("api", ":fakeLibrary");
    runWriteCommandAction(getProject(), buildModel::applyChanges);

    String failure = requestSyncAndGetExpectedFailure();
    assertThat(failure).contains("Project with path ':fakeLibrary' could not be found");

    // TODO verify that a message and "quick fix" has been displayed.
  }

  public void testWithUnresolvedDependencies() throws Exception {
    loadSimpleApplication();

    File buildFilePath = getBuildFilePath("app");
    VirtualFile buildFile = findFileByIoFile(buildFilePath, true);
    assertNotNull(buildFile);

    boolean versionChanged = false;

    Project project = getProject();
    GradleSyncMessagesStub syncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(project, getTestRootDisposable());
    GradleBuildModel buildModel = GradleBuildModel.parseBuildFile(buildFile, project);

    for (ArtifactDependencyModel artifact : buildModel.dependencies().artifacts()) {
      if ("com.android.support".equals(artifact.group().toString()) && "appcompat-v7".equals(artifact.name().forceString())) {
        artifact.version().setValue("100.0.0");
        versionChanged = true;
        break;
      }
    }
    assertTrue(versionChanged);

    runWriteCommandAction(project, buildModel::applyChanges);
    refreshProjectFiles();

    try {
      requestSyncAndWait();
    } catch (SyncIssuesPresentError expected) {
      // Sync issues are expected.
    }

    List<NotificationData> messages = syncMessages.getNotifications();
    assertThat(messages).hasSize(1);

    NotificationData notification = messages.get(0);

    assertEquals(WARNING, notification.getNotificationCategory());
    assertEquals("Unresolved dependencies", notification.getTitle());
    assertThat(notification.getMessage()).contains("Failed to resolve: com.android.support:appcompat-v7:100.0.0\nAffected Modules:");
  }

  public void testWithLocalAarsAsModules() throws Exception {
    loadProject(LOCAL_AARS_AS_MODULES);

    Module localAarModule = TestModuleUtil.findModule(getProject(), "library-debug");

    // When AAR files are exposed as artifacts, they don't have an AndroidProject model.
    AndroidFacet androidFacet = AndroidFacet.getInstance(localAarModule);
    assertNull(androidFacet);
    AndroidModuleModel gradleModel = AndroidModuleModel.get(localAarModule);
    assertNull(gradleModel);

    // Should not expose the AAR as library, instead it should use the "exploded AAR".
    assertAbout(libraryDependencies()).that(localAarModule).doesNotHaveDependencies();

    Module appModule = TestModuleUtil.findAppModule(getProject());
    assertAbout(libraryDependencies()).that(appModule).containsMatching(
      false, "Gradle: artifacts:library\\-debug:unspecified$", COMPILE);
  }

  public void testWithLocalJarsAsModules() throws Exception {
    loadProject(LOCAL_JARS_AS_MODULES);

    Module localJarModule = TestModuleUtil.findModule(getProject(), "localJarAsModule");
    // Module should be a Java module, not buildable (since it doesn't have source code).
    JavaFacet javaFacet = JavaFacet.getInstance(localJarModule);
    assertNotNull(javaFacet);
    assertFalse(javaFacet.getConfiguration().BUILDABLE);

    String localJarName = "Gradle: " + localJarModule.getName() + ".local";
    assertAbout(libraryDependencies()).that(localJarModule).hasDependency(localJarName, COMPILE, true);
  }

  public void testWithInterModuleDependencies() throws Exception {
    loadProject(TRANSITIVE_DEPENDENCIES);

    Module appModule = TestModuleUtil.findAppModule(getProject());
    String library2Name = TestModuleUtil.findModule(getProject(), "library2").getName();
    assertAbout(moduleDependencies()).that(appModule).hasDependency(library2Name, COMPILE, false);
  }

  // See: https://code.google.com/p/android/issues/detail?id=210172
  public void testTransitiveDependenciesFromJavaModule() throws Exception {
    loadProject(TRANSITIVE_DEPENDENCIES);
    Module appModule = TestModuleUtil.findAppModule(getProject());

    // 'app' module should have 'guava' as dependency.
    // 'app' -> 'javalib1' -> 'guava'
    assertAbout(libraryDependencies()).that(appModule).containsMatching(false, "Gradle: .*guava.*$", COMPILE, PROVIDED);
  }

  // See: https://code.google.com/p/android/issues/detail?id=212338
  public void testTransitiveDependenciesFromAndroidModule() throws Exception {
    loadProject(TRANSITIVE_DEPENDENCIES);
    Module appModule = TestModuleUtil.findAppModule(getProject());

    // 'app' module should have 'commons-io' as dependency.
    // 'app' -> 'library2' -> 'library1' -> 'commons-io'
    assertAbout(libraryDependencies()).that(appModule).containsMatching(false, "Gradle: .*commons\\-io.*$", COMPILE);
  }

  // See: https://code.google.com/p/android/issues/detail?id=212557
  public void testTransitiveAndroidModuleDependency() throws Exception {
    loadProject(TRANSITIVE_DEPENDENCIES);
    Module appModule = TestModuleUtil.findAppModule(getProject());

    // 'app' module should have 'library1' as module dependency.
    // 'app' -> 'library2' -> 'library1'
    String lib1Name = TestModuleUtil.findModule(getProject(), "library1").getName();
    assertAbout(moduleDependencies()).that(appModule).hasDependency(lib1Name, COMPILE, false);
  }

  public void testJavaLibraryModuleDependencies() throws Exception {
    loadProject(TRANSITIVE_DEPENDENCIES);
    Module appModule = TestModuleUtil.findAppModule(getProject());

    // dependency should be set on the module not the compiled jar.
    // 'app' -> 'javalib1' -> 'javalib2'
    String javalib1Name = TestModuleUtil.findModule(getProject(), getMainSourceSet("javalib1")).getName();
    String javalib2Name = TestModuleUtil.findModule(getProject(), getMainSourceSet("javalib2")).getName();
    assertAbout(moduleDependencies()).that(appModule).hasDependency(javalib1Name, COMPILE, false);
    assertAbout(moduleDependencies()).that(appModule).hasDependency(javalib2Name, COMPILE, false);
    assertAbout(libraryDependencies()).that(appModule).doesNotContain("Gradle: " + javalib1Name, COMPILE);
  }

  public void testDependencySetUpInJavaModule() throws Exception {
    loadProject(TRANSITIVE_DEPENDENCIES);
    Module libModule = TestModuleUtil.findModule(getProject(), getMainSourceSet("javalib1"));
    String javalib2Name = TestModuleUtil.findModule(getProject(), getMainSourceSet("javalib2")).getName();
    assertAbout(moduleDependencies()).that(libModule).hasDependency(javalib2Name, COMPILE, false);
    assertAbout(libraryDependencies()).that(libModule).doesNotContain("Gradle: " + javalib2Name, COMPILE);
  }

  // See: https://code.google.com/p/android/issues/detail?id=213627
  public void testJarsInLibsFolder() throws Exception {
    loadProject(TRANSITIVE_DEPENDENCIES);

    // 'fakelib' is in 'libs' directory in 'library2' module.
    Module library2Module = TestModuleUtil.findModule(getProject(), "library2");
    assertAbout(libraryDependencies()).that(library2Module).containsMatching(false, "Gradle: .*fakelib.*", COMPILE);

    // 'app' module should have 'fakelib' as dependency.
    // 'app' -> 'library2' -> 'fakelib'
    Module appModule = TestModuleUtil.findAppModule(getProject());
    assertAbout(libraryDependencies()).that(appModule).containsMatching(false, "Gradle: .*fakelib.*", COMPILE);
  }
}
