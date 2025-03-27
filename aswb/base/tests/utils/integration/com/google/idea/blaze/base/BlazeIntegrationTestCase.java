/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base;


import com.google.idea.blaze.base.command.buildresult.LocalFileArtifact;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.io.InputStreamProvider;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.qsync.settings.QuerySyncSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.SyncCache;
import com.google.idea.blaze.base.toolwindow.NoopTasksToolWindowService;
import com.google.idea.blaze.base.toolwindow.TasksToolWindowService;
import com.google.idea.blaze.common.artifact.BlazeArtifact;
import com.google.idea.testing.EdtRule;
import com.google.idea.testing.IntellijTestSetupRule;
import com.google.idea.testing.ServiceHelper;
import com.google.idea.testing.VerifyRequiredPluginsEnabled;
import com.google.idea.testing.runfiles.Runfiles;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.RefreshSession;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import com.intellij.util.ui.UIUtil;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/** Base test class for blaze integration tests. {@link UsefulTestCase} */
public abstract class BlazeIntegrationTestCase {

  /** Test rule that ensures tests do not run on Windows (see http://b.android.com/222904) */
  public static class IgnoreOnWindowsRule implements TestRule {
    @Override
    public Statement apply(Statement base, Description description) {
      if (SystemInfo.isWindows) {
        return new Statement() {
          @Override
          public void evaluate() throws Throwable {
            System.out.println(
                "Test \""
                    + description.getDisplayName()
                    + "\" does not run on Windows (see http://b.android.com/222904)");
          }
        };
      }
      return base;
    }
  }

  @Rule public final IgnoreOnWindowsRule rule = new IgnoreOnWindowsRule();
  @Rule public final IntellijTestSetupRule setupRule = new IntellijTestSetupRule();
  @Rule public final TestRule testRunWrapper = runTestsOnEdt() ? new EdtRule() : null;

  protected CodeInsightTestFixture testFixture;
  protected WorkspaceRoot workspaceRoot;
  protected VirtualFile projectDataDirectory;
  protected TestFileSystem fileSystem;
  protected WorkspaceFileSystem workspace;

  private String ideaPythonHelpersPath;

  @Before
  public final void setUp() throws Throwable {
    testFixture = createTestFixture();
    testFixture.setUp();
    // In 241 `setUp()` first processes events and then waits for indexing to complete. In most
    // cases indexing finishes sooner and processing events runs all startup activities, but when it
    // does not they get deferred. This seems to be a bug in the platform's test utils, which should
    // soon become irrelevant as it only affects old style `StartupActivity`es.
    EdtTestUtil.runInEdtAndWait(UIUtil::dispatchAllInvocationEvents);
    fileSystem =
        new TestFileSystem(getProject(), testFixture.getTempDirFixture(), isLightTestCase());

    runWriteAction(
        () -> {
          VirtualFile workspaceRootVirtualFile = fileSystem.createDirectory("workspace");
          workspaceRoot = new WorkspaceRoot(new File(workspaceRootVirtualFile.getPath()));
          projectDataDirectory = fileSystem.createDirectory("project-data-dir");
          workspace = new WorkspaceFileSystem(workspaceRoot, fileSystem);
        });

    BlazeImportSettingsManager.getInstance(getProject())
        .setImportSettings(
            new BlazeImportSettings(
                workspaceRoot.toString(),
                "test-project",
                projectDataDirectory.getPath(),
                workspaceRoot.fileForPath(new WorkspacePath("project-view-file")).getPath(),
                buildSystem(),
                ProjectType.ASPECT_SYNC));

    registerApplicationService(
        InputStreamProvider.class,
        new InputStreamProvider() {
          @Override
          public InputStream forFile(File file) throws IOException {
            VirtualFile vf = fileSystem.findFile(file.getPath());
            if (vf == null) {
              throw new FileNotFoundException();
            }
            return vf.getInputStream();
          }

          @Override
          public BufferedInputStream forOutputArtifact(BlazeArtifact output) throws IOException {
            if (output instanceof LocalFileArtifact) {
              return new BufferedInputStream(forFile(((LocalFileArtifact) output).getFile()));
            }
            throw new RuntimeException("Can't handle output artifact type: " + output.getClass());
          }
        });

    registerApplicationService(QuerySyncSettings.class, new QuerySyncSettings());
    registerProjectService(TasksToolWindowService.class, new NoopTasksToolWindowService());
    if (isLightTestCase()) {
      registerApplicationService(
          FileOperationProvider.class, new TestFileSystem.MockFileOperationProvider());
      registerApplicationService(
          VirtualFileSystemProvider.class, new TestFileSystem.TempVirtualFileSystemProvider());
    }

    String requiredPlugins = System.getProperty("idea.required.plugins.id");
    if (requiredPlugins != null) {
      VerifyRequiredPluginsEnabled.runCheck(requiredPlugins.split(","));
    }

    ideaPythonHelpersPath = System.getProperty("idea.python.helpers.path");
    System.setProperty(
        "idea.python.helpers.path",
        Runfiles.runfilesPath("tools/vendor/google/aswb/third_party/java/jetbrains/python/helpers")
        .toString());
  }

  @After
  public final void tearDown() throws Throwable {
    if (!isLightTestCase()) {
      // Workaround to avoid a platform race condition that occurs when we delete a VirtualDirectory
      // whose children were affected by external file system events that RefreshQueue is still
      // processing. We only need this for heavy test cases, since light test cases perform all file
      // operations synchronously through an in-memory file system.
      // See https://youtrack.jetbrains.com/issue/IDEA-218773
      RefreshSession refreshSession = RefreshQueue.getInstance().createSession(false, true, null);
      refreshSession.addFile(fileSystem.findFile(workspaceRoot.directory().getPath()));
      refreshSession.launch();
    }
    SyncCache.getInstance(getProject()).clear();
    runWriteAction(
        () -> {
          // Clear attached jdks
          ProjectJdkTable table = ProjectJdkTable.getInstance();
          for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
            table.removeJdk(sdk);
          }
          // Clear attached libraries
          LibraryTable libraryTable =
              LibraryTablesRegistrar.getInstance().getLibraryTable(getProject());
          for (Library library : libraryTable.getLibraries()) {
            libraryTable.removeLibrary(library);
          }
        });
    testFixture.tearDown();
    testFixture = null;

    if (ideaPythonHelpersPath == null) {
      System.clearProperty("idea.python.helpers.path");
    } else {
      System.setProperty("idea.python.helpers.path", ideaPythonHelpersPath);
    }
  }

  private static void runWriteAction(Runnable writeAction) throws Throwable {
    EdtTestUtil.runInEdtAndWait(
        () -> ApplicationManager.getApplication().runWriteAction(writeAction));
  }

  private CodeInsightTestFixture createTestFixture() {
    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();

    if (isLightTestCase()) {
      TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder =
          factory.createLightFixtureBuilder("test-project");
      IdeaProjectTestFixture lightFixture = fixtureBuilder.getFixture();
      return factory.createCodeInsightFixture(lightFixture, new LightTempDirTestFixtureImpl(true));
    }

    TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder =
        factory.createFixtureBuilder("test-project");
    return factory.createCodeInsightFixture(fixtureBuilder.getFixture());
  }

  /**
   * Override to back this test with a heavy test fixture, which will actually modify files on disk
   * instead of keeping everything in memory like a light test fixture does. This can hurt test
   * performance, though we aren't sure to what extent (b/117435202).
   */
  protected boolean isLightTestCase() {
    return true;
  }

  /** Override to run tests with blaze specified as the project's build system. */
  protected BuildSystemName buildSystem() {
    return BuildSystemName.Bazel;
  }

  /** Override to run tests off the EDT. */
  protected boolean runTestsOnEdt() {
    return true;
  }

  protected Project getProject() {
    return testFixture.getProject();
  }

  protected Disposable getTestRootDisposable() {
    return setupRule.testRootDisposable;
  }

  protected <T> void registerApplicationService(Class<T> key, T implementation) {
    ServiceHelper.registerApplicationService(key, implementation, getTestRootDisposable());
  }

  protected <T> void registerApplicationComponent(Class<T> key, T implementation) {
    ServiceHelper.registerApplicationComponent(key, implementation, getTestRootDisposable());
  }

  protected <T> void registerProjectService(Class<T> key, T implementation) {
    ServiceHelper.registerProjectService(
        getProject(), key, implementation, getTestRootDisposable());
  }

  public <T> void registerProjectComponent(Class<T> key, T implementation) {
    ServiceHelper.registerProjectComponent(
        getProject(), key, implementation, getTestRootDisposable());
  }

  protected <T> void registerExtension(ExtensionPointName<T> name, T instance) {
    ServiceHelper.registerExtension(name, instance, getTestRootDisposable());
  }

  protected <T> void registerExtensionFirst(ExtensionPointName<T> name, T instance) {
    ServiceHelper.registerExtensionFirst(name, instance, getTestRootDisposable());
  }
}
