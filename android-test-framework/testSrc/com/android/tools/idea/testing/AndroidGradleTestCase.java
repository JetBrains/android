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
package com.android.tools.idea.testing;

import static com.android.tools.idea.gradle.project.AndroidGradleProjectStartupActivityKt.addJUnitProducersToIgnoredList;
import static com.android.tools.idea.gradle.project.sync.snapshots.TemplateBasedTestProjectKt.migratePackageAttribute;
import static com.android.tools.idea.gradle.util.LastBuildOrSyncServiceKt.emulateStartupActivityForTest;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.prepareGradleProject;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;

import com.android.ide.common.repository.AgpVersion;
import com.android.sdklib.AndroidVersion;
import com.android.testutils.TestUtils;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildResult;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.android.tools.idea.sdk.AndroidSdkPathStore;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.startup.GradleSpecificInitializer;
import com.android.tools.idea.testing.AndroidGradleTests.SyncIssuesPresentError;
import com.google.common.base.Joiner;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.TestApplicationManager;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheImpl;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import org.jetbrains.android.AndroidTempDirTestFixture;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemDependent;
import org.jetbrains.annotations.SystemIndependent;

/**
 * Base class for unit tests that operate on Gradle projects
 * <p>
 * NOTE: Do not use this for writing tests: use JUnit4 with {@link AndroidGradleProjectRule}
 * instead. This allows you to use features introduced in JUnit4 (such as parameterization) while
 * also providing a more compositional approach - instead of your test class inheriting dozens and
 * dozens of methods you might not be familiar with, those methods will be constrained to the rule.
 */
@Deprecated
abstract class AndroidGradleTestCase extends AndroidTestBase implements GradleIntegrationTest {
  private static final Logger LOG = Logger.getInstance(AndroidGradleTestCase.class);

  private final @NotNull AgpVersionSoftwareEnvironment agpVersionSoftwareEnvironment;
  private final @NotNull @SystemIndependent String workspaceRelativeTestDataPath;

  AndroidGradleTestCase(
    @NotNull AgpVersionSoftwareEnvironment agpVersionSoftwareEnvironment,
    @NotNull @SystemIndependent String workspaceRelativeTestDataPath
  ) {
    this.agpVersionSoftwareEnvironment = agpVersionSoftwareEnvironment;
    this.workspaceRelativeTestDataPath = workspaceRelativeTestDataPath;
  }

  @Override
  public final void setUp() throws Exception {
    super.setUp();

    TestApplicationManager.getInstance();

    ensureSdkManagerAvailable(AndroidVersion.fromString(agpVersionSoftwareEnvironment.getCompileSdk()));
    AndroidTestCase.registerLongRunningThreads();
    setUpFixture();

    // This is normally done from AndroidGradleProjectStartupActivity, but that is guarded by `isBuiltWithGradle`, and that
    // gives the wrong answer (or rather, the right answer that will later turn out to have been wrong) for the default project.
    addJUnitProducersToIgnoredList(getProject());

    // To ensure that application IDs are loaded from the listing file as needed, we must register the required listeners.
    // This is normally done within an AndroidStartupActivity but these are not run in tests.
    // TODO(b/159600848)
    emulateStartupActivityForTest(getProject());

    // TODO(b/418973297): Consolidate all init logic in the different test frameworks
    WorkspaceModelCacheImpl.forceEnableCaching(getTestRootDisposable());
    GradleSpecificInitializer.initializePhasedSync();

    // Use per-project code style settings so we never modify the IDE defaults.
    CodeStyleSettingsManager.getInstance().USE_PER_PROJECT_SETTINGS = true;
    IdeSdks ideSdks = IdeSdks.getInstance();

    final var oldAndroidSdkPath = ideSdks.getAndroidSdkPath();
    Disposer.register(getTestRootDisposable(), () -> {
      WriteAction.runAndWait(() -> {
        AndroidSdkPathStore.getInstance().setAndroidSdkPath(oldAndroidSdkPath != null ? oldAndroidSdkPath.toPath() : null);
      });
    });
  }

  public final void setUpFixture() throws Exception {
    AndroidTempDirTestFixture tempDirFixture = new AndroidTempDirTestFixture(getName());
    TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder =
      IdeaTestFixtureFactory.getFixtureFactory()
        .createFixtureBuilder(getName(), tempDirFixture.getProjectDir().getParentFile().toPath(), true);
    IdeaProjectTestFixture projectFixture = projectBuilder.getFixture();
    setUpFixture(projectFixture);
  }

  public final void setUpFixture(IdeaProjectTestFixture projectFixture) throws Exception {
    JavaCodeInsightTestFixture fixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectFixture);
    fixture.setUp();
    fixture.setTestDataPath(TestUtils.resolveWorkspacePath(getTestDataDirectoryWorkspaceRelativePath()).toRealPath().toString());
    ensureSdkManagerAvailable(AndroidVersion.fromString(agpVersionSoftwareEnvironment.getCompileSdk()));

    Project project = fixture.getProject();
    FileUtil.ensureExists(new File(toSystemDependentName(project.getBasePath())));
    LocalFileSystem.getInstance().refreshAndFindFileByPath(project.getBasePath());
    AndroidGradleTests.setUpSdks(fixture, TestUtils.getSdk().toFile());
    myFixture = fixture;
  }

  public final void tearDownFixture() {
    if (myFixture != null) {
      try {
        myFixture.tearDown();
      }
      catch (Throwable e) {
        LOG.warn("Failed to tear down " + myFixture.getClass().getSimpleName(), e);
      }
      myFixture = null;
    }
  }

  @Override
  protected final void tearDown() throws Exception {
    try {
      TestDialogManager.setTestDialog(TestDialog.DEFAULT);
      tearDownFixture();

      ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
      Project[] openProjects = projectManager.getOpenProjects();
      if (openProjects.length > 0) {
        TestApplicationManager.tearDownProjectAndApp(openProjects[0]);
      }
    }
    finally {
      try {
        assertEquals(0, ProjectManager.getInstance().getOpenProjects().length);
      }
      catch (Throwable e) {
        addSuppressedException(e);
      }
      finally {
        // Added more logging because of http://b/184293946
        try {
          super.tearDown();
        } catch (DirectoryNotEmptyException ex) {
          String allPaths = Joiner.on(",").join(Files.walk(Paths.get(ex.getFile())).collect(Collectors.toList()));
          System.err.println("Failed to delete dir as it contains files: " + allPaths);
          //noinspection ThrowFromFinallyBlock
          throw ex;
        }
      }
    }
  }

  protected final File loadProject(@NotNull String relativePath,
                                   @NotNull ResolvedAgpVersionSoftwareEnvironment agpVersion,
                                   @Nullable String ndkVersion) throws Exception {
    File projectRoot = prepareProjectForImport(relativePath, agpVersion, ndkVersion, true);
    importProject(agpVersion.getJdkVersion());

    prepareProjectForTest(getProject());
    return projectRoot;
  }

  protected final void prepareProjectForTest(Project project) {
    AndroidProjectInfo androidProjectInfo = AndroidProjectInfo.getInstance(project);
    assertFalse(androidProjectInfo.isLegacyIdeaAndroidProject());
    IndexingTestUtil.waitUntilIndexesAreReady(project);
  }

  protected final void patchPreparedProject(@NotNull File projectRoot,
                                            @NotNull ResolvedAgpVersionSoftwareEnvironment agpVersion,
                                            @Nullable String ndkVersion,
                                            boolean syncReady,
                                            File... localRepos) throws IOException {
    AndroidGradleTests.defaultPatchPreparedProject(projectRoot, agpVersion, ndkVersion, syncReady, localRepos);
    AgpVersion agpVersionParsed = AgpVersion.tryParse(agpVersion.getAgpVersion());
    if (agpVersionParsed != null && agpVersionParsed.isAtLeastIncludingPreviews(8, 0, 0)) {
      migratePackageAttribute(projectRoot);
    }
  }

  /**
   * All overloads of this method should be final, please do not remove.
   */
  @NotNull
  protected final File prepareProjectForImport(@NotNull @SystemIndependent String relativePath,
                                               @NotNull File targetPath,
                                               @NotNull ResolvedAgpVersionSoftwareEnvironment agpVersion,
                                               @Nullable String ndkVersion,
                                               boolean syncReady) {
    File projectSourceRoot = resolveTestDataPath(relativePath);

    prepareGradleProject(
      projectSourceRoot,
      targetPath,
      file -> patchPreparedProject(file, agpVersion, ndkVersion, syncReady,
                                   getAdditionalRepos().toArray(new File[0])));
    return targetPath;
  }

  @NotNull
  protected final File prepareProjectForImport(@NotNull @SystemIndependent String relativePath,
                                               @NotNull ResolvedAgpVersionSoftwareEnvironment agpVersion,
                                               @Nullable String ndkVersion,
                                               boolean syncReady) {
    File projectRoot = new File(toSystemDependentName(getProject().getBasePath()));
    return prepareProjectForImport(relativePath, projectRoot, agpVersion, ndkVersion, syncReady);
  }

  @NotNull
  @Override
  @SystemIndependent
  public final String getTestDataDirectoryWorkspaceRelativePath() {
    return workspaceRelativeTestDataPath;
  }

  @NotNull
  @Override
  public final File resolveTestDataPath(@NotNull @SystemIndependent String relativePath) {
    return new File(myFixture.getTestDataPath(), toSystemDependentName(relativePath));
  }


  public final void generateSources() throws InterruptedException {
    GradleBuildResult result =
      AndroidGradleTests.invokeGradle(getProject(), invoker -> invoker.generateSources(ModuleManager.getInstance(getProject()).getModules()));
    assertTrue("Generating sources failed.", result.isBuildSuccessful());
    refreshProjectFiles();
  }

  protected final void importProject(@NotNull JavaSdkVersion jdkVersion) {
    Project project = getProject();
    AgpIntegrationTestUtil.importProject(project, jdkVersion);
  }

  @NotNull
  protected final Module getModule(@NotNull String moduleName) {
    return TestModuleUtil.findModule(getProject(), moduleName);
  }

  protected final boolean hasModule(@NotNull String moduleName) {
    return TestModuleUtil.hasModule(getProject(), moduleName);
  }

  protected final void requestSyncAndWait() throws SyncIssuesPresentError, Exception {
    requestSyncAndWait(GradleSyncInvoker.Request.testRequest());
  }

  protected final void requestSyncAndWait(@NotNull GradleSyncInvoker.Request request) throws Exception {
    refreshProjectFiles();
    AndroidGradleTests.syncProject(getProject(), request, it -> AndroidGradleTests.checkSyncStatus(getProject(), it));
    IndexingTestUtil.waitUntilIndexesAreReady(getProject());
  }

  @Override
  @SystemDependent
  @NotNull
  public final String getBaseTestPath() {
    return myFixture.getTempDirPath();
  }
}