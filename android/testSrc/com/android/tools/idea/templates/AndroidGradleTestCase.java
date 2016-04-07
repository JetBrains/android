/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.templates;

import com.android.SdkConstants;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.eclipse.GradleImport;
import com.android.tools.idea.gradle.project.AndroidGradleProjectComponent;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.npw.*;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.VersionCheck;
import com.android.tools.idea.wizard.template.TemplateWizard;
import com.android.tools.idea.wizard.template.TemplateWizardState;
import com.android.tools.lint.checks.BuiltinIssueRegistry;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.LintRequest;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.intellij.analysis.AnalysisScope;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.SmartList;
import com.intellij.util.lang.CompoundRuntimeException;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.inspections.lint.IntellijLintClient;
import org.jetbrains.android.inspections.lint.IntellijLintIssueRegistry;
import org.jetbrains.android.inspections.lint.IntellijLintRequest;
import org.jetbrains.android.inspections.lint.ProblemData;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.AndroidTestCaseHelper.getAndroidSdkPath;
import static com.android.tools.idea.AndroidTestCaseHelper.getSystemPropertyOrEnvironmentVariable;
import static com.android.tools.idea.gradle.util.Projects.*;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_BUILD_API;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_BUILD_API_STRING;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static com.intellij.openapi.projectRoots.JdkUtil.checkForJdk;
import static com.intellij.openapi.util.SystemInfo.isWindows;
import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.jetbrains.android.sdk.AndroidSdkUtils.setSdkData;
import static org.jetbrains.android.sdk.AndroidSdkUtils.tryToChooseAndroidSdk;

/**
 * Base class for unit tests that operate on Gradle projects
 */
public abstract class AndroidGradleTestCase extends AndroidTestBase {
  private static final Logger LOG = Logger.getInstance(AndroidGradleTestCase.class);

  /**
   * The name of the gradle wrapper executable associated with the current OS.
   */
  @NonNls private static final String GRADLE_WRAPPER_EXECUTABLE_NAME = isWindows ? FN_GRADLE_WRAPPER_WIN : FN_GRADLE_WRAPPER_UNIX;

  /** Environment variable pointing to the JDK to be used for tests */
  public static final String JDK_HOME_FOR_TESTS = "JDK_HOME_FOR_TESTS";

  private static AndroidSdkData ourPreviousSdkData;

  protected AndroidFacet myAndroidFacet;

  public AndroidGradleTestCase() {
  }

  protected boolean createDefaultProject() {
    return true;
  }

  /**
   * Is the bundled (incomplete) SDK install adequate or do we need to find a valid install?
   */
  @Override
  protected boolean requireRecentSdk() {
    return true;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    GradleProjectImporter.ourSkipSetupFromTest = true;

    if (createDefaultProject()) {
      final TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder =
        IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
      myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
      myFixture.setUp();
      myFixture.setTestDataPath(getTestDataPath());
    }

    ensureSdkManagerAvailable();

    setUpSdks();
  }

  private void setUpSdks() {
    // We seem to have two different locations where the SDK needs to be specified.
    // One is whatever is already defined in the JDK Table, and the other is the global one as defined by IdeSdks.
    // Gradle import will fail if the global one isn't set.
    final File androidSdkPath = getAndroidSdkPath();

    String jdkHome = getSystemPropertyOrEnvironmentVariable(JDK_HOME_FOR_TESTS);
    if (isNullOrEmpty(jdkHome) || !checkForJdk(jdkHome)) {
      fail("Please specify the path to a valid JDK using system property or environment variable " + JDK_HOME_FOR_TESTS);
    }
    final File jdkPath = new File(jdkHome);

    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        File currentAndroidSdkPath = IdeSdks.getAndroidSdkPath();
        File currentJdkPath = IdeSdks.getJdkPath();
        if (!filesEqual(androidSdkPath, currentAndroidSdkPath) || !filesEqual(jdkPath, currentJdkPath)) {
          runWriteCommandAction(getProject(), new Runnable() {
            @Override
            public void run() {
              IdeSdks.setAndroidSdkPath(androidSdkPath, getProject());
              LOG.info("Set IDE Sdk Path to " + androidSdkPath);

              IdeSdks.setJdkPath(jdkPath);
              LOG.info("Set JDK to " + jdkPath);
            }
          });
        }
      }
    });
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myFixture != null) {
        try {
          Project project = myFixture.getProject();
          // Since we don't really open the project, but we manually register listeners in the gradle importer
          // by explicitly calling AndroidGradleProjectComponent#configureGradleProject, we need to counteract
          // that here, otherwise the testsuite will leak
          if (requiresAndroidModel(project)) {
            AndroidGradleProjectComponent projectComponent = AndroidGradleProjectComponent.getInstance(project);
            projectComponent.projectClosed();
          }
        }
        finally {
          myFixture.tearDown();
          myFixture = null;
        }
      }

      GradleProjectImporter.ourSkipSetupFromTest = false;

      ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
      Project[] openProjects = projectManager.getOpenProjects();
      if (openProjects.length > 0) {
        final List<Throwable> exceptions = new SmartList<>();
        try {
          PlatformTestCase.closeAndDisposeProjectAndCheckThatNoOpenProjects(openProjects[0], exceptions);
        }
        finally {
          CompoundRuntimeException.throwIfNotEmpty(exceptions);
        }
      }
    }
    finally {
      try {
        assertEquals(0, ProjectManager.getInstance().getOpenProjects().length);
      }
      finally {
        super.tearDown();
      }

      // In case other test cases rely on the builtin (incomplete) SDK, restore
      if (ourPreviousSdkData != null) {
        setSdkData(ourPreviousSdkData);
        ourPreviousSdkData = null;
      }
    }
  }

  @Override
  protected void ensureSdkManagerAvailable() {
    if (requireRecentSdk() && ourPreviousSdkData == null) {
      ourPreviousSdkData = tryToChooseAndroidSdk();
      if (ourPreviousSdkData != null) {
        VersionCheck.VersionCheckResult check = VersionCheck.checkVersion(ourPreviousSdkData.getLocation().getPath());
        // "The sdk1.5" version of the SDK stored in the test directory isn't really a 22.0.5 version of the SDK even
        // though its sdk1.5/tools/source.properties says it is. We can't use this one for these tests.
        if (!check.isCompatibleVersion() || ourPreviousSdkData.getLocation().getPath().endsWith(File.separator + "sdk1.5")) {
          AndroidSdkData sdkData = createTestSdkManager();
          assertNotNull(sdkData);
          setSdkData(sdkData);
        }
      }
    }
    super.ensureSdkManagerAvailable();
  }

  protected void loadProject(String relativePath) throws IOException, ConfigurationException, InterruptedException {
    loadProject(relativePath, false);
  }

  protected void loadProject(String relativePath, boolean buildProject) throws IOException, ConfigurationException, InterruptedException {
    loadProject(relativePath, buildProject, null);
  }

  protected void loadProject(String relativePath, boolean buildProject, @Nullable GradleSyncListener listener)
    throws IOException, ConfigurationException, InterruptedException {
    File root = new File(getTestDataPath(), relativePath.replace('/', File.separatorChar));
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

    // Update dependencies to latest, and possibly repository URL too if android.mavenRepoUrl is set
    updateGradleVersions(projectRoot);

    if (buildProject) {
      try {
        assertBuildsCleanly(project, true);
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    importProject(project, project.getName(), projectRoot, listener);

    assertTrue(requiresAndroidModel(project));
    assertFalse(isLegacyIdeaAndroidProject(project));

    ModuleManager moduleManager = ModuleManager.getInstance(project);
    Module[] modules = moduleManager.getModules();
    // first try and find a non-lib facet
    for (Module module : modules) {
      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      if (androidFacet != null && !androidFacet.isLibraryProject()) {
        myAndroidFacet = androidFacet;
        break;
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

    // With IJ14 code base, we run tests with NO_FS_ROOTS_ACCESS_CHECK turned on. I'm not sure if that
    // is the cause of the issue, but not all files inside a project are seen while running unit tests.
    // This explicit refresh of the entire project fix such issues (e.g. AndroidProjectViewTest).
    LocalFileSystem.getInstance().refreshFiles(Collections.singletonList(project.getBaseDir()));
  }

  public static void updateGradleVersions(@NotNull File file) throws IOException {
    if (file.isDirectory()) {
      File[] files = file.listFiles();
      if (files != null) {
        for (File child : files) {
          updateGradleVersions(child);
        }
      }
    }
    else if (file.getPath().endsWith(DOT_GRADLE) && file.isFile()) {
      String contents = Files.toString(file, Charsets.UTF_8);

      boolean changed = false;
      Pattern pattern = Pattern.compile("classpath ['\"]com.android.tools.build:gradle:(.+)['\"]");
      Matcher matcher = pattern.matcher(contents);
      if (matcher.find()) {
        contents = contents.substring(0, matcher.start(1)) + SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION +
                   contents.substring(matcher.end(1));
        changed = true;
      }

      pattern = Pattern.compile("buildToolsVersion ['\"](.+)['\"]");
      matcher = pattern.matcher(contents);
      if (matcher.find()) {
        contents = contents.substring(0, matcher.start(1)) + GradleImport.CURRENT_BUILD_TOOLS_VERSION +
                   contents.substring(matcher.end(1));
        changed = true;
      }

      String repository = System.getProperty(TemplateWizard.MAVEN_URL_PROPERTY);
      if (repository != null) {
        pattern = Pattern.compile("mavenCentral\\(\\)");
        matcher = pattern.matcher(contents);
        if (matcher.find()) {
          contents = contents.substring(0, matcher.start()) + "maven { url '" + repository + "' };" +
                     contents.substring(matcher.start()); // note: start; not end; we're prepending, not replacing!
          changed = true;
        }
        pattern = Pattern.compile("jcenter\\(\\)");
        matcher = pattern.matcher(contents);
        if (matcher.find()) {
          contents = contents.substring(0, matcher.start()) + "maven { url '" + repository + "' };" +
                     contents.substring(matcher.start()); // note: start; not end; we're prepending, not replacing!
          changed = true;
        }
      }

      if (changed) {
        Files.write(contents, file, Charsets.UTF_8);
      }
    }
  }

  public void createProject(String activityName, boolean syncModel) throws Exception {
    final NewProjectWizardState projectWizardState = new NewProjectWizardState();

    configureProjectState(projectWizardState);
    TemplateWizardState activityWizardState = projectWizardState.getActivityTemplateState();
    configureActivityState(activityWizardState, activityName);

    createProject(projectWizardState, syncModel);
  }

  public void testCreateGradleWrapper() throws Exception {
    File baseDir = getBaseDirPath(getProject());
    createGradleWrapper(baseDir);

    assertFilesExist(baseDir, FN_GRADLE_WRAPPER_UNIX, FN_GRADLE_WRAPPER_WIN, FD_GRADLE, FD_GRADLE_WRAPPER,
                     join(FD_GRADLE_WRAPPER, FN_GRADLE_WRAPPER_JAR), join(FD_GRADLE_WRAPPER, FN_GRADLE_WRAPPER_PROPERTIES));
  }

  public static void createGradleWrapper(File projectRoot) throws IOException {
    GradleUtil.createGradleWrapper(projectRoot);
  }

  protected static void assertFilesExist(@Nullable File baseDir, @NotNull String... paths) {
    for (String path : paths) {
      path = toSystemDependentName(path);
      File testFile = baseDir != null ? new File(baseDir, path) : new File(path);
      assertTrue("File doesn't exist: " + testFile.getPath(), testFile.exists());
    }
  }

  protected void configureActivityState(TemplateWizardState activityWizardState, String activityName) {
    TemplateManager manager = TemplateManager.getInstance();
    List<File> templates = manager.getTemplates(Template.CATEGORY_ACTIVITIES);
    File blankActivity = null;
    for (File t : templates) {
      if (t.getName().equals(activityName)) {
        blankActivity = t;
        break;
      }
    }
    assertNotNull(blankActivity);
    activityWizardState.setTemplateLocation(blankActivity);
    Template.convertApisToInt(activityWizardState.getParameters());
    assertNotNull(activityWizardState.getTemplate());
  }

  protected void configureProjectState(NewProjectWizardState projectWizardState) {
    final Project project = myFixture.getProject();
    AndroidSdkData sdkData = tryToChooseAndroidSdk();
    assert sdkData != null;

    Template.convertApisToInt(projectWizardState.getParameters());
    projectWizardState.put(TemplateMetadata.ATTR_GRADLE_VERSION, GRADLE_LATEST_VERSION);
    projectWizardState.put(TemplateMetadata.ATTR_GRADLE_PLUGIN_VERSION, GRADLE_PLUGIN_RECOMMENDED_VERSION);
    projectWizardState.put(NewModuleWizardState.ATTR_PROJECT_LOCATION, project.getBasePath());
    projectWizardState.put(FormFactorUtils.ATTR_MODULE_NAME, "TestModule");
    projectWizardState.put(TemplateMetadata.ATTR_PACKAGE_NAME, "test.pkg");
    projectWizardState.put(TemplateMetadata.ATTR_CREATE_ICONS, false); // If not, you need to initialize additional state
    BuildToolInfo buildTool = sdkData.getLatestBuildTool();
    assertNotNull(buildTool);
    projectWizardState.put(TemplateMetadata.ATTR_BUILD_TOOLS_VERSION, buildTool.getRevision().toString());
    IAndroidTarget[] targets = sdkData.getTargets();
    AndroidVersion version = targets[targets.length - 1].getVersion();
    projectWizardState.put(ATTR_BUILD_API, version.getApiLevel());
    projectWizardState.put(ATTR_BUILD_API_STRING, TemplateMetadata.getBuildApiString(version));
  }

  public void createProject(final NewProjectWizardState projectWizardState, boolean syncModel) throws Exception {
    createProject(myFixture, projectWizardState, syncModel);
  }

  public static void createProject(final IdeaProjectTestFixture myFixture,
                                   final NewProjectWizardState projectWizardState,
                                   boolean syncModel) throws Exception {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        AssetStudioAssetGenerator assetGenerator = new AssetStudioAssetGenerator(projectWizardState);
        NewProjectWizard.createProject(projectWizardState, myFixture.getProject(), assetGenerator);
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });

    // Sync model
    if (syncModel) {
      String projectName = projectWizardState.getString(FormFactorUtils.ATTR_MODULE_NAME);
      File projectRoot = new File(projectWizardState.getString(NewModuleWizardState.ATTR_PROJECT_LOCATION));
      assertEquals(projectRoot, virtualToIoFile(myFixture.getProject().getBaseDir()));
      importProject(myFixture.getProject(), projectName, projectRoot, null);
    }
  }

  public void assertBuildsCleanly(Project project, boolean allowWarnings, String... extraArgs) throws Exception {
    File base = virtualToIoFile(project.getBaseDir());
    File gradlew = new File(base, GRADLE_WRAPPER_EXECUTABLE_NAME);
    assertTrue(gradlew.exists());
    File pwd = base.getAbsoluteFile();
    // TODO: Add in --no-daemon, anything to suppress total time?
    List<String> args = Lists.newArrayList();
    args.add(gradlew.getPath());
    args.add("assembleDebug");
    Collections.addAll(args, extraArgs);
    GradleUtil.addLocalMavenRepoInitScriptCommandLineOption(args);
    GeneralCommandLine cmdLine = new GeneralCommandLine(args).withWorkDirectory(pwd);
    CapturingProcessHandler process = new CapturingProcessHandler(cmdLine);
    // Building currently takes about 30s, so a 5min timeout should give a safe margin.
    int timeoutInMilliseconds = 5 * 60 * 1000;
    ProcessOutput processOutput = process.runProcess(timeoutInMilliseconds, true);
    if (processOutput.isTimeout()) {
      throw new TimeoutException("\"gradlew assembleDebug\" did not terminate within test timeout value.\n" +
                                 "[stdout]\n" +
                                 processOutput.getStdout() + "\n" +
                                 "[stderr]\n" +
                                 processOutput.getStderr() + "\n");
    }
    String errors = processOutput.getStderr();
    String output = processOutput.getStdout();
    int exitCode = processOutput.getExitCode();
    int expectedExitCode = 0;
    if (output.contains("BUILD FAILED") && errors.contains("Could not find any version that matches com.android.tools.build:gradle:")) {
      // We ignore this assertion. We got here because we are using a version of the Android Gradle plug-in that is not available in Maven
      // Central yet.
      expectedExitCode = 1;
    }
    else {
      assertTrue(output + "\n" + errors, output.contains("BUILD SUCCESSFUL"));
      if (!allowWarnings) {
        assertEquals(output + "\n" + errors, "", errors);
      }
    }
    assertEquals(expectedExitCode, exitCode);
  }

  public void assertLintsCleanly(Project project, Severity maxSeverity, Set<Issue> ignored) throws Exception {
    BuiltinIssueRegistry registry = new IntellijLintIssueRegistry();
    Map<Issue, Map<File, List<ProblemData>>> map = Maps.newHashMap();
    IntellijLintClient client = IntellijLintClient.forBatch(project, map, new AnalysisScope(project), registry.getIssues());
    LintDriver driver = new LintDriver(registry, client);
    List<Module> modules = Arrays.asList(ModuleManager.getInstance(project).getModules());
    LintRequest request = new IntellijLintRequest(client, project, null, modules, false);
    EnumSet<Scope> scope = EnumSet.allOf(Scope.class);
    scope.remove(Scope.CLASS_FILE);
    scope.remove(Scope.ALL_CLASS_FILES);
    scope.remove(Scope.JAVA_LIBRARIES);
    request.setScope(scope);
    driver.analyze(request);
    if (!map.isEmpty()) {
      for (Map<File, List<ProblemData>> fileListMap : map.values()) {
        for (Map.Entry<File, List<ProblemData>> entry : fileListMap.entrySet()) {
          File file = entry.getKey();
          List<ProblemData> problems = entry.getValue();
          for (ProblemData problem : problems) {
            Issue issue = problem.getIssue();
            if (ignored != null && ignored.contains(issue)) {
              continue;
            }
            if (issue.getDefaultSeverity().compareTo(maxSeverity) < 0) {
              fail("Found lint issue " +
                   issue.getId() +
                   " with severity " +
                   issue.getDefaultSeverity() +
                   " in " +
                   file +
                   " at " +
                   problem.getTextRange() +
                   ": " +
                   problem.getMessage());
            }
          }
        }
      }
    }
  }

  protected static void importProject(final @NotNull Project project, final @NotNull String projectName, final File projectRoot,
                                      final @Nullable GradleSyncListener listener)
    throws IOException, ConfigurationException, InterruptedException {

    final Ref<Throwable> throwableRef = new Ref<>();
    final CountDownLatch latch = new CountDownLatch(1);
    GradleSyncListener postSetupListener = new GradleSyncListener.Adapter() {
      @Override
      public void syncSucceeded(@NotNull Project project) {
        latch.countDown();
      }

      @Override
      public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
        latch.countDown();
        if (listener == null) {
          fail(errorMessage);
        }
      }
    };

    GradleSyncState.subscribe(project, postSetupListener);

    runWriteCommandAction(project, new Runnable() {
      @Override
      public void run() {
        try {
          // When importing project for tests we do not generate the sources as that triggers a compilation which finishes asynchronously.
          // This causes race conditions and intermittent errors. If a test needs source generation this should be handled separately.
          GradleProjectImporter.getInstance().importProject(projectName, projectRoot, false /* do not generate sources */, listener,
                                                            project, null);
        }
        catch (Throwable e) {
          throwableRef.set(e);
        }
      }
    });

    Throwable throwable = throwableRef.get();
    if (throwable != null) {
      if (throwable instanceof IOException) {
        throw (IOException)throwable;
      } else if (throwable instanceof ConfigurationException) {
        throw (ConfigurationException)throwable;
      } else {
        throw new RuntimeException(throwable);
      }
    }

    latch.await();
  }
}
