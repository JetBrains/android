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
import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.android.testutils.TestUtils.getSdk;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION_PRE30;
import static com.android.utils.TraceUtils.getCurrentStack;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static com.intellij.openapi.util.io.FileUtil.copyDir;
import static com.intellij.openapi.util.io.FileUtil.createIfDoesntExist;
import static com.intellij.openapi.util.io.FileUtil.join;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.pom.java.LanguageLevel.JDK_1_8;
import static com.intellij.testFramework.PlatformTestCase.synchronizeTempDirVfs;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.AndroidGradleProjectComponent;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.project.importing.GradleProjectImporter;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.Jdks;
import com.google.common.collect.Lists;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.idea.IdeaTestApplication;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.EmptyModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.ThreadTracker;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.Consumer;
import com.intellij.util.ThrowableRunnable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
public abstract class AndroidGradleTestCase extends AndroidTestBase {
  private static final Logger LOG = Logger.getInstance(AndroidGradleTestCase.class);

  protected AndroidFacet myAndroidFacet;
  protected Modules myModules;

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

    GradleProjectImporter.ourSkipSetupFromTest = true;

    if (createDefaultProject()) {
      TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder =
        IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
      myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
      myFixture.setUp();
      myFixture.setTestDataPath(getTestDataPath());
      ensureSdkManagerAvailable();

      Project project = getProject();
      setUpSdks(project);
      myModules = new Modules(project);
    }
    else {
      // This is necessary when we don't create a default project,
      // to ensure that the LocalFileSystemHolder is initialized.
      IdeaTestApplication.getInstance();

      ensureSdkManagerAvailable();
    }
    // Layoutlib rendering thread will be shutdown when the app is closed so do not report it as a leak
    ThreadTracker.longRunningThreadCreated(ApplicationManager.getApplication(), "Layoutlib");
  }

  protected void setUpSdks(@NotNull Project project) {
    // We seem to have two different locations where the SDK needs to be specified.
    // One is whatever is already defined in the JDK Table, and the other is the global one as defined by IdeSdks.
    // Gradle import will fail if the global one isn't set.
    File androidSdkPath = findSdkPath();

    IdeSdks ideSdks = IdeSdks.getInstance();
    runWriteCommandAction(project, () -> {
      if (IdeInfo.getInstance().isAndroidStudio()) {
        ideSdks.setUseEmbeddedJdk();
        LOG.info("Set JDK to " + ideSdks.getJdkPath());
      }

      Sdks.allowAccessToSdk(myFixture.getProjectDisposable());
      ideSdks.setAndroidSdkPath(androidSdkPath, project);
      IdeSdks.removeJdksOn(myFixture.getProjectDisposable());

      LOG.info("Set IDE Sdk Path to " + androidSdkPath);
    });

    Sdk currentJdk = ideSdks.getJdk();
    assertNotNull(currentJdk);
    assertTrue("JDK 8 is required. Found: " + currentJdk.getHomePath(), Jdks.getInstance().isApplicableJdk(currentJdk, JDK_1_8));

    // IntelliJ uses project jdk for gradle import by default, see GradleProjectSettings.myGradleJvm
    // Android Studio overrides GradleInstallationManager.getGradleJdk() using AndroidStudioGradleInstallationManager
    // so it doesn't require the Gradle JDK setting to be defined
    if (!IdeInfo.getInstance().isAndroidStudio()) {
      new WriteAction() {
        @Override
        protected void run(@NotNull Result result) {
          ProjectRootManager.getInstance(project).setProjectSdk(currentJdk);
        }
      }.execute();
    }
  }

  @NotNull
  protected File findSdkPath() {
    return getSdk();
  }

  @Override
  protected void tearDown() throws Exception {
    myModules = null;
    myAndroidFacet = null;
    try {
      Messages.setTestDialog(TestDialog.DEFAULT);
      if (myFixture != null) {
        try {
          Project project = myFixture.getProject();
          // Since we don't really open the project, but we manually register listeners in the gradle importer
          // by explicitly calling AndroidGradleProjectComponent#configureGradleProject, we need to counteract
          // that here, otherwise the testsuite will leak
          if (AndroidProjectInfo.getInstance(project).requiresAndroidModel()) {
            AndroidGradleProjectComponent projectComponent = AndroidGradleProjectComponent.getInstance(project);
            projectComponent.projectClosed();
          }
        }
        finally {
          try {
            myFixture.tearDown();
          }
          catch (Throwable e) {
            LOG.warn("Failed to tear down " + myFixture.getClass().getSimpleName(), e);
          }
          myFixture = null;
        }
      }

      GradleProjectImporter.ourSkipSetupFromTest = false;

      ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
      Project[] openProjects = projectManager.getOpenProjects();
      if (openProjects.length > 0) {
        PlatformTestCase.closeAndDisposeProjectAndCheckThatNoOpenProjects(openProjects[0]);
      }
      myAndroidFacet = null;
      myModules = null;
    }
    finally {
      try {
        assertEquals(0, ProjectManager.getInstance().getOpenProjects().length);
      }
      finally {
        //noinspection ThrowFromFinallyBlock
        super.tearDown();
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
    prepareMultipleProjectsForImport(relativePath);
    return requestSyncAndGetExpectedFailure(requestConfigurator);
  }

  protected void loadSimpleApplication() throws Exception {
    loadProject(SIMPLE_APPLICATION);
  }

  protected void loadSimpleApplication_pre3dot0() throws Exception {
    loadProject(SIMPLE_APPLICATION_PRE30);
  }

  protected void loadProject(@NotNull String relativePath) throws Exception {
    loadProject(relativePath, null);
  }

  protected void loadProject(@NotNull String relativePath,
                             @Nullable String chosenModuleName) throws Exception {
    prepareProjectForImport(relativePath);
    Project project = getProject();
    importProject(project.getName(), getBaseDirPath(project));

    AndroidProjectInfo androidProjectInfo = AndroidProjectInfo.getInstance(project);
    assertTrue(androidProjectInfo.requiresAndroidModel());
    assertFalse(androidProjectInfo.isLegacyIdeaAndroidProject());

    ModuleManager moduleManager = ModuleManager.getInstance(project);
    Module[] modules = moduleManager.getModules();

    // if module name is specified, find it
    if (chosenModuleName != null) {
      for (Module module : modules) {
        if (chosenModuleName.equals(module.getName())) {
          myAndroidFacet = AndroidFacet.getInstance(module);
          break;
        }
      }
    }

    if (myAndroidFacet == null) {
      // then try and find a non-lib facet
      for (Module module : modules) {
        AndroidFacet androidFacet = AndroidFacet.getInstance(module);
        if (androidFacet != null && androidFacet.getConfiguration().isAppProject()) {
          myAndroidFacet = androidFacet;
          break;
        }
      }
    }

    // then try and find ANY android facet
    if (myAndroidFacet == null) {
      for (Module module : modules) {
        myAndroidFacet = AndroidFacet.getInstance(module);
        if (myAndroidFacet != null) {
          break;
        }
      }
    }
    refreshProjectFiles();
  }

  /**
   * Prepares multiple projects for import.
   *
   * @param relativePath   the relative path of the projects from the the test data directory
   * @param includedBuilds names of all builds to be included (as well as the main project) these names must
   *                       all be folders within the {@param relativePath}. If empty imports a single project
   *                       with the root given by {@param relativePath}. The first path will be used as the main
   *                       project and copied to the main directory, every other path will be copied to a subfolder.
   * @return root of the imported project or projects.
   */
  @NotNull
  protected final File prepareMultipleProjectsForImport(@NotNull String relativePath, @NotNull String... includedBuilds) throws IOException {
    File root = new File(myFixture.getTestDataPath(), toSystemDependentName(relativePath));
    if (!root.exists()) {
      root = new File(PathManager.getHomePath() + "/../../external", toSystemDependentName(relativePath));
    }

    Project project = myFixture.getProject();
    File projectRoot = virtualToIoFile(project.getBaseDir());

    List<String> buildNames = new ArrayList<>(Arrays.asList(includedBuilds));
    if (includedBuilds.length == 0) {
      buildNames.add(".");
    }

    prepareProjectForImport(new File(root, buildNames.remove(0)), projectRoot);

    for (String buildName : buildNames) {
      File includedBuildRoot = new File(root, buildName);
      File projectBuildRoot = new File(projectRoot, buildName);
      prepareProjectForImport(includedBuildRoot, projectBuildRoot);
    }
    patchPreparedProject(projectRoot);
    return projectRoot;
  }

  protected void patchPreparedProject(@NotNull File projectRoot) throws IOException {
  }

  @NotNull
  protected File prepareProjectForImport(@NotNull String relativePath) throws IOException {
    return prepareMultipleProjectsForImport(relativePath);
  }

  @NotNull
  protected final File prepareProjectForImport(@NotNull File srcRoot, @NotNull File projectRoot) throws IOException {
    return prepareProjectCoreForImport(
      srcRoot, projectRoot, () -> {
        File settings = new File(srcRoot, FN_SETTINGS_GRADLE);
        File build = new File(srcRoot, FN_BUILD_GRADLE);
        File ktsSettings = new File(srcRoot, FN_SETTINGS_GRADLE_KTS);
        File ktsBuild = new File(srcRoot, FN_BUILD_GRADLE_KTS);
        assertTrue("Couldn't find build.gradle(.kts) or settings.gradle(.kts) in " + srcRoot.getPath(),
                   settings.exists() || build.exists() || ktsSettings.exists() || ktsBuild.exists());

        // We need the wrapper for import to succeed
        createGradleWrapper(projectRoot);

        // Update dependencies to latest, and possibly repository URL too if android.mavenRepoUrl is set
        updateVersionAndDependencies(projectRoot);
      });
  }

  @NotNull
  protected final File prepareProjectCoreForImport(@NotNull File srcRoot,
                                                   @NotNull File projectRoot,
                                                   @NotNull ThrowableRunnable<IOException> patcher)
    throws IOException {
    assertTrue(srcRoot.getPath(), srcRoot.exists());

    copyDir(srcRoot, projectRoot);

    // Override settings just for tests (e.g. sdk.dir)
    updateLocalProperties(projectRoot);

    patcher.run();

    // Refresh project dir to have files under of the project.getBaseDir() visible to VFS.
    // Do it in a slower but reliable way.
    VfsUtil.markDirtyAndRefresh(false, true, true, findFileByIoFile(projectRoot, true));
    return projectRoot;
  }

  protected void updateVersionAndDependencies(@NotNull File projectRoot) throws IOException {
    AndroidGradleTests.updateGradleVersions(projectRoot);
  }

  protected void generateSources() throws InterruptedException {
    GradleInvocationResult result = invokeGradle(getProject(), GradleBuildInvoker::generateSources);
    assertTrue("Generating sources failed.", result.isBuildSuccessful());
    refreshProjectFiles();
  }

  protected static GradleInvocationResult invokeGradleTasks(@NotNull Project project, @NotNull String... tasks)
    throws InterruptedException {
    assertThat(tasks).named("Gradle tasks").isNotEmpty();
    File projectDir = getBaseDirPath(project);
    return invokeGradle(project, gradleInvoker -> gradleInvoker.executeTasks(projectDir, Lists.newArrayList(tasks)));
  }

  @NotNull
  protected static GradleInvocationResult invokeGradle(@NotNull Project project, @NotNull Consumer<GradleBuildInvoker> gradleInvocationTask)
    throws InterruptedException {
    Ref<GradleInvocationResult> resultRef = new Ref<>();
    CountDownLatch latch = new CountDownLatch(1);
    GradleBuildInvoker gradleBuildInvoker = GradleBuildInvoker.getInstance(project);

    GradleBuildInvoker.AfterGradleInvocationTask task = result -> {
      resultRef.set(result);
      latch.countDown();
    };

    gradleBuildInvoker.add(task);

    try {
      gradleInvocationTask.consume(gradleBuildInvoker);
    }
    finally {
      gradleBuildInvoker.remove(task);
    }

    latch.await(5, MINUTES);
    GradleInvocationResult result = resultRef.get();
    assert result != null;
    return result;
  }

  private void updateLocalProperties(@NotNull File projectRoot) throws IOException {
    LocalProperties localProperties = new LocalProperties(projectRoot);
    File sdkPath = findSdkPath();
    assertAbout(file()).that(sdkPath).named("Android SDK path").isDirectory();
    localProperties.setAndroidSdkPath(sdkPath.getPath());
    localProperties.save();
  }

  protected void createGradleWrapper(@NotNull File projectRoot) throws IOException {
    createGradleWrapper(projectRoot, GRADLE_LATEST_VERSION);
  }

  protected void createGradleWrapper(@NotNull File projectRoot, @NotNull String gradleVersion) throws IOException {
    GradleWrapper wrapper = GradleWrapper.create(projectRoot);
    File path = EmbeddedDistributionPaths.getInstance().findEmbeddedGradleDistributionFile(gradleVersion);
    assertAbout(file()).that(path).named("Gradle distribution path").isFile();
    wrapper.updateDistributionUrl(path);
  }

  protected void importProject(@NotNull String projectName,
                               @NotNull File projectRoot) throws Exception {
    Ref<Throwable> throwableRef = new Ref<>();
    SyncListener syncListener = new SyncListener();
    Disposable subscriptionDisposable = Disposer.newDisposable();
    try {
      Project project = getProject();

      ApplicationManager.getApplication().invokeAndWait(() -> {
        try {
          // When importing project for tests we do not generate the sources as that triggers a compilation which finishes asynchronously.
          // This causes race conditions and intermittent errors. If a test needs source generation this should be handled separately.
          GradleProjectImporter.Request request = new GradleProjectImporter.Request(project);
          Project newProject = GradleProjectImporter.getInstance().importProjectNoSync(projectName, projectRoot, request);

          // It is essential to subscribe to notifications via [newProject] which may be different from the current project if
          // a new project was requested.
          GradleSyncState.subscribe(newProject, syncListener, subscriptionDisposable);

          GradleSyncInvoker.Request syncRequest = GradleSyncInvoker.Request.testRequest();
          syncRequest.generateSourcesOnSuccess = false;
          GradleSyncInvoker.getInstance().requestProjectSync(newProject, syncRequest, null);
        }
        catch (Throwable e) {
          throwableRef.set(e);
        }
      });

      Throwable throwable = throwableRef.get();
      if (throwable != null) {
        if (throwable instanceof IOException) {
          throw (IOException)throwable;
        }
        else if (throwable instanceof ConfigurationException) {
          throw (ConfigurationException)throwable;
        }
        else {
          throw new RuntimeException(throwable);
        }
      }

      syncListener.await();
    }
    finally {
      Disposer.dispose(subscriptionDisposable);
    }
    if (syncListener.failureMessage != null) {
      fail(syncListener.failureMessage);
    }
  }

  @NotNull
  protected AndroidModuleModel getModel() {
    AndroidModuleModel model = AndroidModuleModel.get(myAndroidFacet);
    assert model != null;
    return model;
  }

  @NotNull
  protected String getTextForFile(@NotNull String relativePath) {
    Project project = getProject();
    VirtualFile file = project.getBaseDir().findFileByRelativePath(relativePath);
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
    return myModules.getModule(moduleName);
  }

  protected void requestSyncAndWait(@NotNull GradleSyncInvoker.Request request) throws Exception {
    SyncListener syncListener = requestSync(request);
    checkStatus(syncListener);
  }

  protected void requestSyncAndWait() throws Exception {
    SyncListener syncListener = requestSync(request -> { });
    checkStatus(syncListener);
  }

  private static void checkStatus(@NotNull SyncListener syncListener) {
    if (!syncListener.success) {
      String cause =
        !syncListener.isSyncFinished() ? "<Timed out>" : isEmpty(syncListener.failureMessage) ? "<Unknown>" : syncListener.failureMessage;
      fail(cause);
    }
  }

  @NotNull
  protected String requestSyncAndGetExpectedFailure() throws Exception {
    return requestSyncAndGetExpectedFailure(request -> { });
  }

  @NotNull
  protected String requestSyncAndGetExpectedFailure(@NotNull Consumer<GradleSyncInvoker.Request> requestConfigurator) throws Exception {
    SyncListener syncListener = requestSync(requestConfigurator);
    assertFalse(syncListener.success);
    String message = syncListener.failureMessage;
    assertNotNull(message);
    return message;
  }

  @NotNull
  private SyncListener requestSync(@NotNull Consumer<GradleSyncInvoker.Request> requestConfigurator) throws Exception {
    GradleSyncInvoker.Request request = GradleSyncInvoker.Request.testRequest();
    request.generateSourcesOnSuccess = false;
    requestConfigurator.consume(request);
    return requestSync(request);
  }

  @NotNull
  protected SyncListener requestSync(@NotNull GradleSyncInvoker.Request request) throws Exception {
    SyncListener syncListener = new SyncListener();
    refreshProjectFiles();

    Project project = getProject();
    GradleProjectInfo.getInstance(project).setImportedProject(true);
    GradleSyncInvoker.getInstance().requestProjectSync(project, request, syncListener);

    syncListener.await();
    return syncListener;
  }

  @NotNull
  protected Module createModule(@NotNull String name) {
    return createModule(name, EmptyModuleType.getInstance());
  }

  @NotNull
  protected Module createModule(@NotNull String name, @NotNull ModuleType type) {
    VirtualFile projectRootFolder = getProject().getBaseDir();
    File moduleFile = new File(virtualToIoFile(projectRootFolder), name + ModuleFileType.DOT_DEFAULT_EXTENSION);
    createIfDoesntExist(moduleFile);

    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(moduleFile);
    return createModule(virtualFile, type);
  }

  @NotNull
  public Module createModule(@NotNull File modulePath, @NotNull ModuleType type) {
    VirtualFile moduleFolder = findFileByIoFile(modulePath, true);
    assertNotNull(moduleFolder);
    return createModule(moduleFolder, type);
  }

  @NotNull
  private Module createModule(@NotNull VirtualFile file, @NotNull ModuleType type) {
    return new WriteAction<Module>() {
      @Override
      protected void run(@NotNull Result<Module> result) {
        ModuleManager moduleManager = ModuleManager.getInstance(getProject());
        Module module = moduleManager.newModule(file.getPath(), type.getId());
        module.getModuleFile();
        result.setResult(module);
      }
    }.execute().getResultObject();
  }

  public static class SyncListener implements GradleSyncListener {
    @NotNull private final CountDownLatch myLatch;

    boolean syncSkipped;
    boolean success;
    @Nullable String failureMessage;

    SyncListener() {
      myLatch = new CountDownLatch(1);
    }

    @Override
    public void syncSkipped(@NotNull Project project) {
      syncSucceeded(project);
      syncSkipped = true;
    }

    @Override
    public void syncSucceeded(@NotNull Project project) {
      success = true;
      myLatch.countDown();
    }

    @Override
    public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
      success = false;
      failureMessage = !errorMessage.isEmpty() ? errorMessage : "No errorMessage at:\n" + getCurrentStack();
      myLatch.countDown();
    }

    void await() throws InterruptedException {
      myLatch.await(5, MINUTES);
    }

    public boolean isSyncSkipped() {
      return syncSkipped;
    }

    public boolean isSyncFinished() {
      return success || failureMessage != null;
    }
  }
}
