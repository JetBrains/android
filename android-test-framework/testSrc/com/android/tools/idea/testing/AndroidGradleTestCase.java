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

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.SdkConstants.FN_BUILD_GRADLE_KTS;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.SdkConstants.FN_SETTINGS_GRADLE_KTS;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.project.sync.snapshots.TemplateBasedTestProjectKt.migratePackageAttribute;
import static com.android.tools.idea.gradle.util.LastBuildOrSyncServiceKt.emulateStartupActivityForTest;
import static com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentUtil.resolveAgpVersionSoftwareEnvironment;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.prepareGradleProject;
import static com.android.tools.idea.testing.AndroidGradleTests.waitForSourceFolderManagerToProcessUpdates;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.android.ide.common.repository.GradleVersion;
import com.android.testutils.TestUtils;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildResult;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.android.tools.idea.testing.AndroidGradleTests.SyncIssuesPresentError;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.testFramework.TestApplicationManager;
import com.intellij.testFramework.TestApplicationManagerKt;
import com.intellij.testFramework.ThreadTracker;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.Consumer;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;
import junit.framework.AssertionFailedError;
import org.jetbrains.android.AndroidTempDirTestFixture;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemDependent;
import org.jetbrains.annotations.SystemIndependent;

/**
 * Base class for unit tests that operate on Gradle projects
 * <p>
 * TODO: After converting all tests over, check to see if there are any methods we can delete or
 * reduce visibility on.
 * <p>
 * NOTE: If you are writing a new test, consider using JUnit4 with {@link AndroidGradleProjectRule}
 * instead. This allows you to use features introduced in JUnit4 (such as parameterization) while
 * also providing a more compositional approach - instead of your test class inheriting dozens and
 * dozens of methods you might not be familiar with, those methods will be constrained to the rule.
 */
public abstract class AndroidGradleTestCase extends AndroidTestBase implements GradleIntegrationTest {
  private static final Logger LOG = Logger.getInstance(AndroidGradleTestCase.class);

  protected AndroidFacet myAndroidFacet;

  public AndroidGradleTestCase() {
  }

  protected boolean createDefaultProject() {
    return true;
  }

  @NotNull
  protected File getProjectFolderPath() {
    String projectFolderPath = getProject().getBasePath();
    assertNotNull(projectFolderPath);
    return new File(projectFolderPath);
  }

  @NotNull
  protected File getBuildFilePath(@NotNull String moduleName) {
    File buildFilePath = new File(getProjectFolderPath(), join(moduleName, FN_BUILD_GRADLE));
    if (!buildFilePath.isFile()) {
      buildFilePath = new File(getProjectFolderPath(), join(moduleName, FN_BUILD_GRADLE_KTS));
    }
    assertAbout(file()).that(buildFilePath).isFile();
    return buildFilePath;
  }

  @NotNull
  protected File getSettingsFilePath() {
    File settingsFilePath = new File(getProjectFolderPath(), FN_SETTINGS_GRADLE);
    if (!settingsFilePath.isFile()) {
      settingsFilePath = new File(getProjectFolderPath(), FN_SETTINGS_GRADLE_KTS);
    }
    assertAbout(file()).that(settingsFilePath).isFile();
    return settingsFilePath;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    TestApplicationManager.getInstance();
    ensureSdkManagerAvailable();
    // Layoutlib rendering thread will be shutdown when the app is closed so do not report it as a leak
    ThreadTracker.longRunningThreadCreated(ApplicationManager.getApplication(), "Layoutlib");

    if (createDefaultProject()) {
      setUpFixture();

      // To ensure that application IDs are loaded from the listing file as needed, we must register the required listeners.
      // This is normally done within an AndroidStartupActivity but these are not run in tests.
      // TODO(b/159600848)
      emulateStartupActivityForTest(getProject());
    }

    // Use per-project code style settings so we never modify the IDE defaults.
    CodeStyleSettingsManager.getInstance().USE_PER_PROJECT_SETTINGS = true;
  }

  public void setUpFixture() throws Exception {
    AndroidTempDirTestFixture tempDirFixture = new AndroidTempDirTestFixture(getName());
    TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder =
      IdeaTestFixtureFactory.getFixtureFactory()
        .createFixtureBuilder(getName(), tempDirFixture.getProjectDir().getParentFile().toPath(), true);
    IdeaProjectTestFixture projectFixture = projectBuilder.getFixture();
    setUpFixture(projectFixture);
  }

  public void setUpFixture(IdeaProjectTestFixture projectFixture) throws Exception {
    JavaCodeInsightTestFixture fixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectFixture);
    fixture.setUp();
    fixture.setTestDataPath(TestUtils.getWorkspaceRoot().toRealPath().resolve(getTestDataDirectoryWorkspaceRelativePath()).toString());
    ensureSdkManagerAvailable();

    Project project = fixture.getProject();
    FileUtil.ensureExists(new File(toSystemDependentName(project.getBasePath())));
    LocalFileSystem.getInstance().refreshAndFindFileByPath(project.getBasePath());
    AndroidGradleTests.setUpSdks(fixture, TestUtils.getSdk().toFile());
    myFixture = fixture;
  }

  public void tearDownFixture() {
    myAndroidFacet = null;
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
  protected void tearDown() throws Exception {
    try {
      TestDialogManager.setTestDialog(TestDialog.DEFAULT);
      tearDownFixture();

      ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
      Project[] openProjects = projectManager.getOpenProjects();
      if (openProjects.length > 0) {
        TestApplicationManagerKt.tearDownProjectAndApp(openProjects[0]);
      }
      myAndroidFacet = null;
    }
    finally {
      try {
        assertEquals(0, ProjectManager.getInstance().getOpenProjects().length);
      }
      finally {
        //noinspection ThrowFromFinallyBlock
        // Added more logging because of http://b/184293946
          try {
            super.tearDown();
          } catch (DirectoryNotEmptyException ex) {
            String allPaths = Joiner.on(",").join(Files.walk(Paths.get(ex.getFile())).collect(Collectors.toList()));
            System.err.println("Failed to delete dir as it contains files: " + allPaths);
            throw ex;
          }
      }
    }
  }

  @NotNull
  protected String loadProjectAndExpectSyncError(@NotNull String relativePath) throws Exception {
    return loadProjectAndExpectSyncError(relativePath, request -> {
    });
  }

  protected String loadProjectAndExpectSyncError(@NotNull String relativePath,
                                                 @NotNull Consumer<GradleSyncInvoker.Request> requestConfigurator) throws Exception {
    prepareProjectForImport(relativePath);
    return requestSyncAndGetExpectedFailure(requestConfigurator);
  }

  protected void loadSimpleApplication() throws Exception {
    loadProject(SIMPLE_APPLICATION);
  }

  protected final void loadProject(@NotNull String relativePath) throws Exception {
    loadProject(relativePath, null, resolveAgpVersionSoftwareEnvironment(AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT), null);
  }

  protected final void loadProject(@NotNull String relativePath,
                                   @Nullable String chosenModuleName) throws Exception {
    loadProject(relativePath, chosenModuleName, resolveAgpVersionSoftwareEnvironment(AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT), null);
  }

  protected final void loadProject(@NotNull String relativePath,
                                   @Nullable String chosenModuleName,
                                   @NotNull AgpVersionSoftwareEnvironmentDescriptor agpVersion) throws Exception {
    loadProject(relativePath, chosenModuleName, resolveAgpVersionSoftwareEnvironment(agpVersion), null);
  }

  protected final void loadProject(@NotNull String relativePath,
                                   @Nullable String chosenModuleName,
                                   @NotNull ResolvedAgpVersionSoftwareEnvironment agpVersion,
                                   @Nullable String ndkVersion) throws Exception {
    prepareProjectForImport(relativePath, agpVersion, ndkVersion);
    importProject();

    prepareProjectForTest(getProject(), chosenModuleName);
  }

  /**
   * @return a collection of absolute paths to additional local repositories required by the test.
   */
  @Override
  public Collection<File> getAdditionalRepos() {
    return ImmutableList.of();
  }

  protected void prepareProjectForTest(Project project, @Nullable String chosenModuleName) {
    AndroidProjectInfo androidProjectInfo = AndroidProjectInfo.getInstance(project);
    //assertTrue(androidProjectInfo.requiresAndroidModel());
    assertFalse(androidProjectInfo.isLegacyIdeaAndroidProject());

    Module[] modules = ModuleManager.getInstance(project).getModules();

    myAndroidFacet = AndroidGradleTests.findAndroidFacetForTests(project, modules, chosenModuleName);
  }

  protected void patchPreparedProject(@NotNull File projectRoot,
                                      @NotNull ResolvedAgpVersionSoftwareEnvironment agpVersion,
                                      @Nullable String ndkVersion,
                                      File... localRepos) throws IOException {
    AndroidGradleTests.defaultPatchPreparedProject(projectRoot, agpVersion, ndkVersion, localRepos);
    GradleVersion agpVersionParsed = GradleVersion.tryParseAndroidGradlePluginVersion(agpVersion.getAgpVersion());
    if (agpVersionParsed != null && agpVersionParsed.isAtLeastIncludingPreviews(8, 0, 0)) {
      migratePackageAttribute(projectRoot);
    }
  }

  @NotNull
  protected File prepareProjectForImport(@NotNull @SystemIndependent String relativePath) throws IOException {
    return prepareProjectForImport(relativePath, resolveAgpVersionSoftwareEnvironment(AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT),
                                   null);
  }

  @NotNull
  protected final File prepareProjectForImport(@NotNull @SystemIndependent String relativePath, @NotNull File targetPath)
    throws IOException {
    return prepareProjectForImport(relativePath, targetPath,
                                   resolveAgpVersionSoftwareEnvironment(AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT), null);
  }

  /**
   * All overloads of this method should be final, please to not remove.
   */
  @NotNull
  protected final File prepareProjectForImport(@NotNull @SystemIndependent String relativePath,
                                               @NotNull File targetPath,
                                               @NotNull ResolvedAgpVersionSoftwareEnvironment agpVersion,
                                               @Nullable String ndkVersion) throws IOException {
    File projectSourceRoot = resolveTestDataPath(relativePath);

    prepareGradleProject(
      projectSourceRoot,
      targetPath,
      file -> patchPreparedProject(file, agpVersion, ndkVersion,
                                   getAdditionalRepos().toArray(new File[0])));
    return targetPath;
  }

  @NotNull
  protected final File prepareProjectForImport(@NotNull @SystemIndependent String relativePath,
                                               @NotNull ResolvedAgpVersionSoftwareEnvironment agpVersion,
                                               @Nullable String ndkVersion) throws IOException {
    File projectRoot = new File(toSystemDependentName(getProject().getBasePath()));
    return prepareProjectForImport(relativePath, projectRoot, agpVersion, ndkVersion);
  }

  @NotNull
  @Override
  @SystemIndependent
  public String getTestDataDirectoryWorkspaceRelativePath() {
    return "tools/adt/idea/android/testData";
  }

  @NotNull
  @Override
  public File resolveTestDataPath(@NotNull @SystemIndependent String relativePath) {
    return new File(myFixture.getTestDataPath(), toSystemDependentName(relativePath));
  }


  protected void generateSources() throws InterruptedException {
    GradleBuildResult result =
      invokeGradle(getProject(), invoker -> invoker.generateSources(ModuleManager.getInstance(getProject()).getModules()));
    assertTrue("Generating sources failed.", result.isBuildSuccessful());
    refreshProjectFiles();
  }

  protected static GradleInvocationResult invokeGradleTasks(@NotNull Project project, @NotNull String... tasks)
    throws InterruptedException {
    return invokeGradleTasks(project, null, tasks);
  }

  protected static GradleInvocationResult invokeGradleTasks(@NotNull Project project, @Nullable Long timeoutMillis, @NotNull String... tasks)
    throws InterruptedException {
    assertThat(tasks).named("Gradle tasks").isNotEmpty();
    File projectDir = getBaseDirPath(project);
    // Tests should not need to access the network
    return invokeGradle(project, gradleInvoker ->
      gradleInvoker.executeTasks(
        GradleBuildInvoker.Request.builder(project, projectDir, tasks)
          .setCommandLineArguments(Lists.newArrayList("--offline"))
          .build()
      ), timeoutMillis);
  }

  @NotNull
  protected static <T extends GradleBuildResult> T invokeGradle(
    @NotNull Project project,
    @NotNull Function<GradleBuildInvoker, ListenableFuture<T>> gradleInvocationTask) {
    return invokeGradle(project, gradleInvocationTask, null);
  }

  protected static <T extends GradleBuildResult> T invokeGradle(
    @NotNull Project project,
    @NotNull Function<GradleBuildInvoker, ListenableFuture<T>> gradleInvocationTask,
    @Nullable Long sourceFolderTimeoutMillis
  ) {
    GradleBuildInvoker gradleBuildInvoker = GradleBuildInvoker.getInstance(project);

    ListenableFuture<T> future = gradleInvocationTask.apply(gradleBuildInvoker);

    T result;
    try {
      result = future.get(5, MINUTES);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }

    refreshProjectFiles();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      try {
        waitForSourceFolderManagerToProcessUpdates(project, sourceFolderTimeoutMillis);
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    });
    assert result != null;
    return result;
  }

  protected void importProject() throws Exception {
    Project project = getProject();
    AgpIntegrationTestUtil.importProject(project);
  }

  @NotNull
  protected GradleAndroidModel getModel() {
    GradleAndroidModel model = GradleAndroidModel.get(myAndroidFacet);
    assert model != null;
    return model;
  }

  @NotNull
  protected String getTextForFile(@NotNull String relativePath) {
    Project project = getProject();
    VirtualFile file = VfsUtil.findFile(Paths.get(project.getBasePath(), relativePath), false);
    if (file != null) {
      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (psiFile != null) {
        return psiFile.getText();
      }
    }

    return "";
  }

  @NotNull
  protected Module getModule(@NotNull String moduleName) {
    return TestModuleUtil.findModule(getProject(), moduleName);
  }

  protected boolean hasModule(@NotNull String moduleName) {
    return TestModuleUtil.hasModule(getProject(), moduleName);
  }

  protected void requestSyncAndWait() throws SyncIssuesPresentError, Exception {
    requestSyncAndWait(GradleSyncInvoker.Request.testRequest());
  }

  @NotNull
  protected String requestSyncAndGetExpectedFailure() throws Exception {
    return requestSyncAndGetExpectedFailure(request -> { });
  }

  @NotNull
  protected String requestSyncAndGetExpectedFailure(@NotNull Consumer<GradleSyncInvoker.Request> requestConfigurator) throws Exception {
    try {
      GradleSyncInvoker.Request request = GradleSyncInvoker.Request.testRequest();
      requestConfigurator.consume(request);
      requestSyncAndWait(request);
    } catch (AssertionFailedError error) {
      return error.getMessage();
    }

    fail("Failure was expected, but import was successful");
    return null; // Unreachable
  }

  protected void requestSyncAndWait(@NotNull GradleSyncInvoker.Request request) throws Exception {
    refreshProjectFiles();
    AndroidGradleTests.syncProject(getProject(), request, it -> AndroidGradleTests.checkSyncStatus(getProject(), it));
  }

  @Override
  @SystemDependent
  @NotNull
  public String getBaseTestPath() {
    return myFixture.getTempDirPath();
  }
}
