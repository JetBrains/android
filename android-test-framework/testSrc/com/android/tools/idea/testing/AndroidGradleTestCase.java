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
package com.android.tools.idea.testing;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.project.AndroidGradleProjectComponent;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.project.importing.GradleProjectImporter;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.project.AndroidProjectInfo;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.Jdks;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.idea.IdeaTestApplication;
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
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.Consumer;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.RegEx;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.*;
import static com.android.testutils.TestUtils.getSdk;
import static com.android.testutils.TestUtils.getWorkspaceFile;
import static com.android.tools.idea.gradle.util.Projects.isLegacyIdeaAndroidProject;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.io.Files.write;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.pom.java.LanguageLevel.JDK_1_8;
import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * Base class for unit tests that operate on Gradle projects
 *
 * TODO: After converting all tests over, check to see if there are any methods we can delete or
 * reduce visibility on.
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
    assertAbout(file()).that(buildFilePath).isFile();
    return buildFilePath;
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
      setUpSdks();

      Project project = getProject();
      myModules = new Modules(project);
    }
    else {
      // This is necessary when we don't create a default project,
      // to ensure that the LocalFileSystemHolder is initialized.
      IdeaTestApplication.getInstance();

      ensureSdkManagerAvailable();
    }
  }

  private void setUpSdks() {
    // We seem to have two different locations where the SDK needs to be specified.
    // One is whatever is already defined in the JDK Table, and the other is the global one as defined by IdeSdks.
    // Gradle import will fail if the global one isn't set.
    File androidSdkPath = getSdk();

    IdeSdks ideSdks = IdeSdks.getInstance();
    runWriteCommandAction(getProject(), () -> {
      if (IdeInfo.getInstance().isAndroidStudio()) {
        ideSdks.setUseEmbeddedJdk();
        LOG.info("Set JDK to " + ideSdks.getJdkPath());
      }

      ideSdks.setAndroidSdkPath(androidSdkPath, getProject());
      LOG.info("Set IDE Sdk Path to " + androidSdkPath);
    });

    Sdk currentJdk = ideSdks.getJdk();
    assertNotNull(currentJdk);
    assertTrue("JDK 8 is required. Found: " + currentJdk.getHomePath(), Jdks.getInstance().isApplicableJdk(currentJdk, JDK_1_8));
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
          catch (Exception e) {
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
    prepareProjectForImport(relativePath);
    return requestSyncAndGetExpectedFailure();
  }

  protected void loadSimpleApplication() throws Exception {
    loadProject(SIMPLE_APPLICATION);
  }

  protected void loadProject(@NotNull String relativePath) throws Exception {
    loadProject(relativePath, null, null);
  }

  protected void loadProject(@NotNull String relativePath, @NotNull String chosenModuleName) throws Exception {

    loadProject(relativePath, null, chosenModuleName);
  }

  protected void loadProject(@NotNull String relativePath, @Nullable GradleSyncListener listener)
    throws Exception {
    loadProject(relativePath, listener, null);
  }

  protected void loadProject(@NotNull String relativePath,
                             @Nullable GradleSyncListener listener,
                             @Nullable String chosenModuleName) throws Exception {
    prepareProjectForImport(relativePath);
    Project project = getProject();
    File projectRoot = virtualToIoFile(project.getBaseDir());

    importProject(project.getName(), projectRoot, listener);

    assertTrue(AndroidProjectInfo.getInstance(project).requiresAndroidModel());
    assertFalse(isLegacyIdeaAndroidProject(project));

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
        if (androidFacet != null && androidFacet.isAppProject()) {
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

  protected void prepareProjectForImport(@NotNull String relativePath) throws IOException {
    File root = new File(getTestDataPath(), toSystemDependentName(relativePath));
    assertTrue(root.getPath(), root.exists());

    File build = new File(root, FN_BUILD_GRADLE);
    File settings = new File(root, FN_SETTINGS_GRADLE);
    assertTrue("Couldn't find build.gradle or settings.gradle in " + root.getPath(), build.exists() || settings.exists());

    // Sync the model
    Project project = myFixture.getProject();
    File projectRoot = virtualToIoFile(project.getBaseDir());
    copyDir(root, projectRoot);

    // We need the wrapper for import to succeed
    createGradleWrapper(projectRoot);

    // Override settings just for tests (e.g. sdk.dir)
    updateLocalProperties();

    // Update dependencies to latest, and possibly repository URL too if android.mavenRepoUrl is set
    updateGradleVersions(projectRoot);
  }

  @NotNull
  protected GradleInvocationResult generateSources() throws InterruptedException {
    return invokeGradle(getProject(), gradleInvoker -> gradleInvoker.generateSources(false));
  }

  protected static GradleInvocationResult invokeGradleTasks(@NotNull Project project, @NotNull String... tasks)
    throws InterruptedException {
    assertThat(tasks).named("Gradle tasks").isNotEmpty();
    return invokeGradle(project, gradleInvoker -> gradleInvoker.executeTasks(Lists.newArrayList(tasks)));
  }

  @NotNull
  private static GradleInvocationResult invokeGradle(@NotNull Project project, @NotNull Consumer<GradleBuildInvoker> gradleInvocationTask)
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

    latch.await();
    GradleInvocationResult result = resultRef.get();
    assert result != null;
    return result;
  }

  public static void updateGradleVersions(@NotNull File file) throws IOException {
    updateGradleVersions(file, getLocalRepositories());
  }

  private static void updateGradleVersions(@NotNull File file, @NotNull String localRepositories) throws IOException {
    if (file.isDirectory()) {
      File[] files = file.listFiles();
      if (files != null) {
        for (File child : files) {
          updateGradleVersions(child, localRepositories);
        }
      }
    }
    else if (file.getPath().endsWith(DOT_GRADLE) && file.isFile()) {
      String contentsOrig = Files.toString(file, Charsets.UTF_8);
      String contents = contentsOrig;

      BuildEnvironment buildEnvironment = BuildEnvironment.getInstance();

      contents = replaceRegexGroup(contents, "classpath ['\"]com.android.tools.build:gradle:(.+)['\"]",
                                   buildEnvironment.getGradlePluginVersion());
      contents = replaceRegexGroup(contents, "classpath ['\"]com.android.tools.build:gradle-experimental:(.+)['\"]",
                                   buildEnvironment.getExperimentalPluginVersion());

      contents = replaceRegexGroup(contents, "buildToolsVersion ['\"](.+)['\"]", buildEnvironment.getBuildToolsVersion());
      contents = replaceRegexGroup(contents, "compileSdkVersion ([0-9]+)", buildEnvironment.getCompileSdkVersion());
      contents = replaceRegexGroup(contents, "targetSdkVersion ([0-9]+)", buildEnvironment.getTargetSdkVersion());
      contents = contents.replaceAll("repositories[ ]+\\{", "repositories {\n" + localRepositories);

      if (!contents.equals(contentsOrig)) {
        write(contents, file, Charsets.UTF_8);
      }
    }
  }

  @NotNull
  protected static String getLocalRepositories() {
    return getLocalRepository("prebuilts/tools/common/m2/repository") + getLocalRepository("prebuilts/tools/common/offline-m2");
  }

  @NotNull
  protected static String getLocalRepository(@NotNull String dir) {
    String uri = getWorkspaceFile(dir).toURI().toString();
    return "maven { url \"" + uri + "\" }\n";
  }

  /**
   * Take a regex pattern with a single group in it and replace the contents of that group with a
   * new value.
   *
   * For example, the pattern "Version: (.+)" with value "Test" would take the input string
   * "Version: Production" and change it to "Version: Test"
   *
   * The reason such a special-case pattern substitution utility method exists is this class is
   * responsible for loading read-only gradle test files and copying them over into a mutable
   * version for tests to load. When doing so, it updates obsolete values (like old android
   * platforms) to more current versions. This lets tests continue to run whenever we update our
   * tools to the latest versions, without having to go back and change a bunch of broken tests
   * each time.
   *
   * If a regex is passed in with more than one group, later groups will be ignored; and if no
   * groups are present, this will throw an exception. It is up to the caller to ensure that the
   * regex is well formed and only includes a single group.
   *
   * @return The {@code contents} string, modified by the replacement {@code value}, (unless no
   * {@code regex} match was found).
   */
  @NotNull
  private static String replaceRegexGroup(String contents, @RegEx String regex, String value) {
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(contents);
    if (matcher.find()) {
      contents = contents.substring(0, matcher.start(1)) + value + contents.substring(matcher.end(1));
    }
    return contents;
  }

  private void updateLocalProperties() throws IOException {
    LocalProperties localProperties = new LocalProperties(getProject());
    File sdkPath = getSdk();
    assertAbout(file()).that(sdkPath).named("Android SDK path").isDirectory();
    localProperties.setAndroidSdkPath(sdkPath.getPath());
    localProperties.save();
  }

  protected static void createGradleWrapper(File projectRoot) throws IOException {
    GradleWrapper wrapper = GradleWrapper.create(projectRoot);
    File path = getWorkspaceFile("tools/external/gradle/gradle-" + GRADLE_LATEST_VERSION + "-bin.zip");
    assertAbout(file()).that(path).named("Gradle distribution path").isFile();
    wrapper.updateDistributionUrl(path);
  }

  protected void importProject(@NotNull String projectName,
                               @NotNull File projectRoot,
                               @Nullable GradleSyncListener listener) throws Exception {
    Ref<Throwable> throwableRef = new Ref<>();
    SyncListener syncListener = new SyncListener();

    Project project = getProject();
    GradleSyncState.subscribe(project, syncListener);

    runWriteCommandAction(project, () -> {
      try {
        // When importing project for tests we do not generate the sources as that triggers a compilation which finishes asynchronously.
        // This causes race conditions and intermittent errors. If a test needs source generation this should be handled separately.
        GradleProjectImporter.Request request = new GradleProjectImporter.Request();
        request.setProject(project).setGenerateSourcesOnSuccess(false);
        GradleProjectImporter.getInstance().importProject(projectName, projectRoot, request, listener);
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
    if (syncListener.failureMessage != null && listener == null) {
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

  protected void requestSyncAndWait(@NotNull GradleSyncInvoker.Request request) throws Exception {
    SyncListener syncListener = requestSync(request);
    checkStatus(syncListener);
  }

  protected void requestSyncAndWait() throws Exception {
    SyncListener syncListener = requestSync();
    checkStatus(syncListener);
  }

  private static void checkStatus(@NotNull SyncListener syncListener) {
    if (!syncListener.success) {
      String cause = syncListener.failureMessage;
      if (isEmpty(cause)) {
        cause = "<Unknown>";
      }
      fail(cause);
    }
  }

  @NotNull
  protected String requestSyncAndGetExpectedFailure() throws Exception {
    SyncListener syncListener = requestSync();
    assertFalse(syncListener.success);
    String message = syncListener.failureMessage;
    assertNotNull(message);
    return message;
  }

  @NotNull
  private SyncListener requestSync() throws Exception {
    GradleSyncInvoker.Request request = new GradleSyncInvoker.Request().setGenerateSourcesOnSuccess(false);
    return requestSync(request);
  }

  @NotNull
  private SyncListener requestSync(@NotNull GradleSyncInvoker.Request request) throws Exception {
    SyncListener syncListener = new SyncListener();
    refreshProjectFiles();

    GradleSyncInvoker.getInstance().requestProjectSync(getProject(), request, syncListener);

    syncListener.await();
    return syncListener;
  }

  private static void refreshProjectFiles() {
    // With IJ14 code base, we run tests with NO_FS_ROOTS_ACCESS_CHECK turned on. I'm not sure if that
    // is the cause of the issue, but not all files inside a project are seen while running unit tests.
    // This explicit refresh of the entire project fix such issues (e.g. AndroidProjectViewTest).
    // This refresh must be synchronous and recursive so it is completed before continuing the test and clean everything so indexes are
    // properly updated. Apparently this solves outdated indexes and stubs problems
    LocalFileSystem.getInstance().refresh(false /* synchronous */);
  }

  @NotNull
  protected Module createModule(@NotNull String name) {
    return createModule(name, EmptyModuleType.getInstance());
  }

  @NotNull
  protected Module createModule(@NotNull String name, @NotNull ModuleType type) {
    VirtualFile projectRootFolder = getProject().getBaseDir();
    assertNotNull(projectRootFolder);
    final File moduleFile = new File(virtualToIoFile(projectRootFolder), name + ModuleFileType.DOT_DEFAULT_EXTENSION);
    createIfDoesntExist(moduleFile);
    return new WriteAction<Module>() {
      @Override
      protected void run(@NotNull Result<Module> result) throws Throwable {
        VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(moduleFile);
        assertNotNull(virtualFile);
        Module module = ModuleManager.getInstance(getProject()).newModule(virtualFile.getPath(), type.getId());
        module.getModuleFile();
        result.setResult(module);
      }
    }.execute().getResultObject();
  }

  private static class SyncListener extends GradleSyncListener.Adapter {
    @NotNull private final CountDownLatch myLatch;

    boolean success;
    String failureMessage;

    SyncListener() {
      myLatch = new CountDownLatch(1);
    }

    @Override
    public void syncSucceeded(@NotNull Project project) {
      success = true;
      myLatch.countDown();
    }

    @Override
    public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
      failureMessage = errorMessage;
      myLatch.countDown();
    }

    void await() throws InterruptedException {
      myLatch.await(5, MINUTES);
    }
  }
}
