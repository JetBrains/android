/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.gradle.compiler.AndroidGradleBuildConfiguration;
import com.android.tools.idea.gradle.compiler.PostProjectBuildTasksExecutor;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.project.build.GradleBuildContext;
import com.android.tools.idea.gradle.project.build.GradleProjectBuilder;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.project.AndroidProjectBuildNotifications;
import com.android.tools.idea.tests.gui.framework.Wait;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.Tab;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.AvdManagerDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.GradleBuildModelFixture;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.GradleProjectEventListener;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.GradleToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.ndk.MiscUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.codeInspection.ui.InspectionTree;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.compiler.CompilationStatusListener;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.util.PathUtil;
import com.intellij.util.ThreeState;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.JLabelMatcher;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.android.SdkConstants.FD_GRADLE;
import static com.android.tools.idea.gradle.util.BuildMode.COMPILE_JAVA;
import static com.android.tools.idea.gradle.util.BuildMode.SOURCE_GEN;
import static com.android.tools.idea.gradle.util.GradleUtil.*;
import static com.android.tools.idea.tests.gui.framework.GuiTests.*;
import static com.android.tools.idea.tests.gui.framework.fixture.LibraryPropertiesDialogFixture.showPropertiesDialog;
import static com.intellij.ide.impl.ProjectUtil.closeAndDispose;
import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.vfs.VfsUtilCore.urlToPath;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.fail;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.fest.util.Strings.quote;
import static org.jetbrains.android.AndroidPlugin.*;
import static org.jetbrains.plugins.gradle.settings.DistributionType.LOCAL;
import static org.junit.Assert.*;

public class IdeFrameFixture extends ComponentFixture<IdeFrameFixture, IdeFrameImpl> {
  @NotNull private final File myProjectPath;
  @NotNull private final GradleProjectEventListener myGradleProjectEventListener;

  private EditorFixture myEditor;

  @NotNull
  public static IdeFrameFixture find(@NotNull final Robot robot, @NotNull final File projectPath, @Nullable final String projectName) {
    final GenericTypeMatcher<IdeFrameImpl> matcher = new GenericTypeMatcher<IdeFrameImpl>(IdeFrameImpl.class) {
      @Override
      protected boolean isMatching(@NotNull IdeFrameImpl frame) {
        Project project = frame.getProject();
        if (project != null && toSystemIndependentName(projectPath.getPath()).equals(project.getBasePath())) {
          return projectName == null || projectName.equals(project.getName());
        }
        return false;
      }
    };

    Wait.minutes(2).expecting("IdeFrame " + quote(projectPath.getPath()) + " to show up").until(new Wait.Objective() {
      @Override
      public boolean isMet() {
        Collection<IdeFrameImpl> frames = robot.finder().findAll(matcher);
        return !frames.isEmpty();
      }
    });

    IdeFrameImpl ideFrame = robot.finder().find(matcher);
    return new IdeFrameFixture(robot, ideFrame, projectPath);
  }

  public IdeFrameFixture(@NotNull Robot robot, @NotNull IdeFrameImpl target, @NotNull File projectPath) {
    super(IdeFrameFixture.class, robot, target);
    myProjectPath = projectPath;
    final Project project = getProject();

    Disposable disposable = new NoOpDisposable();
    Disposer.register(project, disposable);

    myGradleProjectEventListener = new GradleProjectEventListener();

    GradleSyncState.subscribe(project, myGradleProjectEventListener);
    PostProjectBuildTasksExecutor.subscribe(project, myGradleProjectEventListener);
  }

  @NotNull
  public File getProjectPath() {
    return myProjectPath;
  }

  @NotNull
  public List<String> getModuleNames() {
    List<String> names = Lists.newArrayList();
    for (Module module : getModuleManager().getModules()) {
      names.add(module.getName());
    }
    return names;
  }

  @NotNull
  public IdeFrameFixture requireModuleCount(int expected) {
    Module[] modules = getModuleManager().getModules();
    assertThat(modules).as("Module count in project " + quote(getProject().getName())).hasSize(expected);
    return this;
  }

  @NotNull
  public AndroidGradleModel getAndroidProjectForModule(@NotNull String name) {
    Module module = getModule(name);
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null && facet.requiresAndroidModel()) {
      // TODO: Resolve direct AndroidGradleModel dep (b/22596984)
      AndroidGradleModel androidModel = AndroidGradleModel.get(facet);
      if (androidModel != null) {
        return androidModel;
      }
    }
    throw new AssertionError("Unable to find AndroidGradleModel for module " + quote(name));
  }

  /**
   * Returns a list of system independent paths
   */
  @NotNull
  public Collection<String> getSourceFolderRelativePaths(@NotNull String moduleName, @NotNull final JpsModuleSourceRootType<?> sourceType) {
    final Set<String> paths = Sets.newHashSet();

    Module module = getModule(moduleName);
    final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);

    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        ModifiableRootModel rootModel = moduleRootManager.getModifiableModel();
        try {
          for (ContentEntry contentEntry : rootModel.getContentEntries()) {
            for (SourceFolder folder : contentEntry.getSourceFolders()) {
              JpsModuleSourceRootType<?> rootType = folder.getRootType();
              if (rootType.equals(sourceType)) {
                String path = urlToPath(folder.getUrl());
                String relativePath = getRelativePath(myProjectPath, new File(toSystemDependentName(path)));
                paths.add(PathUtil.toSystemIndependentName(relativePath));
              }
            }
          }
        }
        finally {
          rootModel.dispose();
        }
      }
    });

    return paths;
  }

  @NotNull
  public Module getModule(@NotNull String name) {
    Module module = findModule(name);
    assertNotNull("Unable to find module with name " + quote(name), module);
    return module;
  }

  @Nullable
  public Module findModule(@NotNull String name) {
    for (Module module : getModuleManager().getModules()) {
      if (name.equals(module.getName())) {
        return module;
      }
    }
    return null;
  }


  @NotNull
  private ModuleManager getModuleManager() {
    return ModuleManager.getInstance(getProject());
  }

  @NotNull
  public EditorFixture getEditor() {
    if (myEditor == null) {
      myEditor = new EditorFixture(robot(), this);
    }

    return myEditor;
  }

  @NotNull
  public AndroidGradleModel getAndroidModel(@NotNull String moduleName) {
    Module module = getModule(moduleName);
    assertNotNull("Could not find module " + moduleName, module);
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assertNotNull("Module " + moduleName + " is not an Android module", facet);
    assertTrue("Module " + moduleName + " is not a Gradle project", facet.requiresAndroidModel());
    // TODO: Resolve direct AndroidGradleModel dep (b/22596984)
    AndroidGradleModel androidModel = AndroidGradleModel.get(facet);
    assertNotNull("Module " + moduleName + " does not have a Gradle project (not synced yet or sync failed?)", androidModel);

    return androidModel;
  }

  @NotNull
  public GradleInvocationResult invokeProjectMake() {
    return invokeProjectMake(null);
  }

  @NotNull
  public GradleInvocationResult invokeProjectMake(@Nullable Runnable executeAfterInvokingMake) {
    myGradleProjectEventListener.reset();

    final AtomicReference<GradleInvocationResult> resultRef = new AtomicReference<GradleInvocationResult>();
    AndroidProjectBuildNotifications.subscribe(getProject(), new AndroidProjectBuildNotifications.AndroidProjectBuildListener() {
      @Override
      public void buildComplete(@NotNull AndroidProjectBuildNotifications.BuildContext context) {
        if (context instanceof GradleBuildContext) {
          resultRef.set(((GradleBuildContext)context).getBuildResult());
        }
      }
    });
    selectProjectMakeAction();

    if (executeAfterInvokingMake != null) {
      executeAfterInvokingMake.run();
    }

    waitForBuildToFinish(COMPILE_JAVA);

    GradleInvocationResult result = resultRef.get();
    assertNotNull(result);

    return result;
  }

  @NotNull
  public IdeFrameFixture invokeProjectMakeAndSimulateFailure(@NotNull final String failure) {
    Runnable failTask = new Runnable() {
      @Override
      public void run() {
        throw new ExternalSystemException(failure);
      }
    };
    ApplicationManager.getApplication().putUserData(EXECUTE_BEFORE_PROJECT_BUILD_IN_GUI_TEST_KEY, failTask);
    selectProjectMakeAction();
    return this;
  }

  @NotNull
  public IdeFrameFixture invokeProjectMakeWithGradleOutput(@NotNull String output) {
    ApplicationManager.getApplication().putUserData(GRADLE_BUILD_OUTPUT_IN_GUI_TEST_KEY, output);
    selectProjectMakeAction();
    return this;
  }

  @NotNull
  public IdeFrameFixture waitUntilFakeGradleOutputIsApplied() {
    final Application application = ApplicationManager.getApplication();
    if (application.getUserData(GRADLE_BUILD_OUTPUT_IN_GUI_TEST_KEY) == null) {
      fail("No fake gradle output is configured");
    }
    Wait.minutes(2).expecting("fake gradle output to be applied").until(new Wait.Objective() {
      @Override
      public boolean isMet() {
        return application.getUserData(GRADLE_BUILD_OUTPUT_IN_GUI_TEST_KEY) == null;
      }
    });
    String fakeOutput = application.getUserData(GRADLE_BUILD_OUTPUT_IN_GUI_TEST_KEY);
    if (fakeOutput != null) {
      fail(String.format("Fake gradle output (%s) is not applied in %d ms", fakeOutput, TimeUnit.MINUTES.toMillis(2)));
    }
    return this;
  }

  @NotNull
  public CompileContext invokeProjectMakeUsingJps() {
    final Project project = getProject();
    AndroidGradleBuildConfiguration buildConfiguration = AndroidGradleBuildConfiguration.getInstance(project);
    buildConfiguration.USE_EXPERIMENTAL_FASTER_BUILD = false;

    final AtomicReference<CompileContext> contextRef = new AtomicReference<CompileContext>();
    CompilerManager compilerManager = CompilerManager.getInstance(project);

    Disposable disposable = new NoOpDisposable();
    compilerManager.addCompilationStatusListener(new CompilationStatusListener() {
      @Override
      public void compilationFinished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
        contextRef.set(compileContext);
      }

      @Override
      public void fileGenerated(String outputRoot, String relativePath) {
      }
    }, disposable);

    try {
      selectProjectMakeAction();

      Wait.minutes(2).expecting("Build (" + COMPILE_JAVA + ") for project " + quote(project.getName()) + " to finish'").until(new Wait.Objective() {
        @Override
        public boolean isMet() {
          CompileContext context = contextRef.get();
          return context != null;
        }
      });

      CompileContext context = contextRef.get();
      assertNotNull(context);

      return context;
    }
    finally {
      Disposer.dispose(disposable);
    }
  }

  /**
   * Finds the Run button in the IDE interface.
   *
   * @return ActionButtonFixture for the run button.
   */
  @NotNull
  public ActionButtonFixture findRunApplicationButton() {
    return findActionButtonByActionId("Run");
  }

  public void debugApp(@NotNull String appName) throws ClassNotFoundException {
    selectApp(appName);
    findActionButtonByActionId("Debug").click();
  }

  public void runApp(@NotNull String appName) throws ClassNotFoundException {
    selectApp(appName);
    findActionButtonByActionId("Run").click();
  }

  @NotNull
  public ChooseDeviceDialogFixture findChooseDeviceDialog() {
    return ChooseDeviceDialogFixture.find(robot());
  }

  @NotNull
  public RunToolWindowFixture getRunToolWindow() {
    return new RunToolWindowFixture(this);
  }

  @NotNull
  public DebugToolWindowFixture getDebugToolWindow() {
    return new DebugToolWindowFixture(this);
  }

  protected void selectProjectMakeAction() {
    invokeMenuPath("Build", "Make Project");
  }

  /**
   * Invokes an action by menu path
   *
   * @param path the series of menu names, e.g. {@link invokeActionByMenuPath("Build", "Make Project")}
   */
  public void invokeMenuPath(@NotNull String... path) {
    getMenuFixture().invokeMenuPath(path);
  }

  /**
   * Invokes an action by menu path (where each segment is a regular expression). This is particularly
   * useful when the menu items can change dynamically, such as the labels of Undo actions, Run actions,
   * etc.
   *
   * @param path the series of menu name regular expressions, e.g. {@link invokeActionByMenuPath("Build", "Make( Project)?")}
   */
  public void invokeMenuPathRegex(@NotNull String... path) {
    getMenuFixture().invokeMenuPathRegex(path);
  }

  @NotNull
  private MenuFixture getMenuFixture() {
    return new MenuFixture(robot(), target());
  }

  @NotNull
  public IdeFrameFixture waitForBuildToFinish(@NotNull final BuildMode buildMode) {
    final Project project = getProject();
    if (buildMode == SOURCE_GEN && !GradleProjectBuilder.getInstance(project).isSourceGenerationEnabled()) {
      return this;
    }

    Wait.minutes(2).expecting("Build (" + buildMode + ") for project " + quote(project.getName()) + " to finish'").until(new Wait.Objective() {
      @Override
      public boolean isMet() {
        if (buildMode == SOURCE_GEN) {
          PostProjectBuildTasksExecutor tasksExecutor = PostProjectBuildTasksExecutor.getInstance(project);
          if (tasksExecutor.getLastBuildTimestamp() != null) {
            // This will happen when creating a new project. Source generation happens before the IDE frame is found and build listeners
            // are created. It is fairly safe to assume that source generation happened if we have a timestamp for a "last performed build".
            return true;
          }
        }
        return myGradleProjectEventListener.isBuildFinished(buildMode);
      }
    });

    waitForBackgroundTasks(robot());
    robot().waitForIdle();

    return this;
  }

  @NotNull
  public FileFixture findExistingFileByRelativePath(@NotNull String relativePath) {
    VirtualFile file = findFileByRelativePath(relativePath, true);
    return new FileFixture(getProject(), file);
  }

  @Nullable
  @Contract("_, true -> !null")
  public VirtualFile findFileByRelativePath(@NotNull String relativePath, boolean requireExists) {
    //noinspection Contract
    assertFalse("Should use '/' in test relative paths, not File.separator", relativePath.contains("\\"));
    Project project = getProject();
    VirtualFile file = project.getBaseDir().findFileByRelativePath(relativePath);
    if (requireExists) {
      //noinspection Contract
      assertNotNull("Unable to find file with relative path " + quote(relativePath), file);
    }
    return file;
  }

  @NotNull
  public IdeFrameFixture requestProjectSyncAndExpectFailure() {
    requestProjectSync();
    return waitForGradleProjectSyncToFail();
  }

  @NotNull
  public IdeFrameFixture requestProjectSyncAndSimulateFailure(@NotNull final String failure) {
    Runnable failTask = new Runnable() {
      @Override
      public void run() {
        throw new ExternalSystemException(failure);
      }
    };
    ApplicationManager.getApplication().putUserData(EXECUTE_BEFORE_PROJECT_SYNC_TASK_IN_GUI_TEST_KEY, failTask);
    // When simulating the error, we don't have to wait for sync to happen. Sync never happens because the error is thrown before it (sync)
    // is started.
    return requestProjectSync();
  }

  @NotNull
  public IdeFrameFixture requestProjectSync() {
    myGradleProjectEventListener.reset();

    // We wait until all "Run Configurations" are populated in the toolbar combo-box. Until then the "Project Sync" button is not in its
    // final position, and FEST will click the wrong button.
    Wait.minutes(2).expecting("'Run Configurations' to be populated").until(new Wait.Objective() {
      @Override
      public boolean isMet() {
        RunConfigurationComboBoxFixture runConfigurationComboBox = RunConfigurationComboBoxFixture.find(IdeFrameFixture.this);
        return isNotEmpty(runConfigurationComboBox.getText());
      }
    });

    waitForBackgroundTasks(robot());
    findGradleSyncAction().waitUntilEnabledAndShowing();
    // TODO figure out why in IDEA 15 even though an action is enabled, visible and showing, clicking it (via UI testing infrastructure)
    // does not work consistently
    GradleProjectImporter.getInstance().requestProjectSync(getProject(), null);

    return this;
  }

  @NotNull
  private ActionButtonFixture findGradleSyncAction() {
    return findActionButtonByActionId("Android.SyncProject");
  }

  @NotNull
  public IdeFrameFixture waitForGradleProjectSyncToFail() {
    try {
      waitForGradleProjectSyncToFinish(true);
      fail("Expecting project sync to fail");
    }
    catch (RuntimeException expected) {
      // expected failure.
    }
    waitForBackgroundTasks(robot());
    return this;
  }

  @NotNull
  public IdeFrameFixture waitForGradleProjectSyncToStart() {
    Project project = getProject();
    final GradleSyncState syncState = GradleSyncState.getInstance(project);
    if (!syncState.isSyncInProgress()) {
      Wait.minutes(2).expecting("Syncing project " + quote(project.getName()) + " to finish").until(new Wait.Objective() {
        @Override
        public boolean isMet() {
          return myGradleProjectEventListener.isSyncStarted();
        }
      });
    }
    return this;
  }

  @NotNull
  public IdeFrameFixture waitForGradleProjectSyncToFinish() {
    waitForGradleProjectSyncToFinish(false);
    return this;
  }

  private void waitForGradleProjectSyncToFinish(final boolean expectSyncFailure) {
    final Project project = getProject();

    // ensure GradleInvoker (in-process build) is always enabled.
    AndroidGradleBuildConfiguration buildConfiguration = AndroidGradleBuildConfiguration.getInstance(project);
    buildConfiguration.USE_EXPERIMENTAL_FASTER_BUILD = true;

    Wait.minutes(2).expecting("Syncing project " + quote(project.getName()) + " to finish").until(new Wait.Objective() {
      @Override
      public boolean isMet() {
        GradleSyncState syncState = GradleSyncState.getInstance(project);
        boolean syncFinished =
          (myGradleProjectEventListener.isSyncFinished() || syncState.isSyncNeeded() != ThreeState.YES) && !syncState.isSyncInProgress();
        if (expectSyncFailure) {
          syncFinished = syncFinished && myGradleProjectEventListener.hasSyncError();
        }
        return syncFinished;
      }
    });

    findGradleSyncAction().waitUntilEnabledAndShowing();

    if (myGradleProjectEventListener.hasSyncError()) {
      RuntimeException syncError = myGradleProjectEventListener.getSyncError();
      myGradleProjectEventListener.reset();
      throw syncError;
    }

    if (!myGradleProjectEventListener.isSyncSkipped()) {
      waitForBuildToFinish(SOURCE_GEN);
    }

    waitForBackgroundTasks(robot());
  }

  @NotNull
  private ActionButtonFixture findActionButtonByActionId(String actionId) {
    return ActionButtonFixture.findByActionId(actionId, robot(), target());
  }

  @NotNull
  public AndroidToolWindowFixture getAndroidToolWindow() {
    return new AndroidToolWindowFixture(getProject(), robot());
  }

  @NotNull
  public CapturesToolWindowFixture getCapturesToolWindow() {
    return new CapturesToolWindowFixture(getProject(), robot());
  }

  @NotNull
  public BuildVariantsToolWindowFixture getBuildVariantsWindow() {
    return new BuildVariantsToolWindowFixture(this);
  }

  @NotNull
  public MessagesToolWindowFixture getMessagesToolWindow() {
    return new MessagesToolWindowFixture(getProject(), robot());
  }

  @NotNull
  public GradleToolWindowFixture getGradleToolWindow() {
    return new GradleToolWindowFixture(getProject(), robot());
  }

  @NotNull
  public EditorNotificationPanelFixture requireEditorNotification(@NotNull final String message) {
    final Ref<EditorNotificationPanel> notificationPanelRef = new Ref<EditorNotificationPanel>();

    Wait.seconds(30).expecting("EditorNotificationPanel with message '" + message + "' to show up").until(new Wait.Objective() {
      @Override
      public boolean isMet() {
        EditorNotificationPanel notificationPanel = findNotificationPanel(message);
        notificationPanelRef.set(notificationPanel);
        return notificationPanel != null;
      }
    });

    EditorNotificationPanel notificationPanel = notificationPanelRef.get();
    assertNotNull(notificationPanel);
    return new EditorNotificationPanelFixture(robot(), notificationPanel);
  }

  public void requireNoEditorNotification() {
    assertNull(findNotificationPanel(null));
  }

  /**
   * Locates an editor notification with the given main message (unless the message is {@code null}, in which case we assert that there are
   * no visible editor notifications. Will fail if the given notification is not found.
   */
  @Nullable
  private EditorNotificationPanel findNotificationPanel(@Nullable String message) {
    Collection<EditorNotificationPanel> panels = robot().finder().findAll(target(), new GenericTypeMatcher<EditorNotificationPanel>(
      EditorNotificationPanel.class, true) {
      @Override
      protected boolean isMatching(@NotNull EditorNotificationPanel panel) {
        return panel.isShowing();
      }
    });

    if (message == null) {
      if (!panels.isEmpty()) {
        List<String> labels = Lists.newArrayList();
        for (EditorNotificationPanel panel : panels) {
          labels.addAll(getEditorNotificationLabels(panel));
        }
        fail("Found editor notifications when none were expected" + labels);
      }
      return null;
    }

    List<String> labels = Lists.newArrayList();
    for (EditorNotificationPanel panel : panels) {
      List<String> found = getEditorNotificationLabels(panel);
      labels.addAll(found);
      for (String label : found) {
        if (label.contains(message)) {
          return panel;
        }
      }
    }

    return null;
  }

  /** Looks up the main label for a given editor notification panel */
  private List<String> getEditorNotificationLabels(@NotNull EditorNotificationPanel panel) {
    final List<String> allText = Lists.newArrayList();
    final Collection<JLabel> labels = robot().finder().findAll(panel, JLabelMatcher.any().andShowing());
    for (final JLabel label : labels) {
      String text = execute(new GuiQuery<String>() {
        @Override
        @Nullable
        protected String executeInEDT() throws Throwable {
          return label.getText();
        }
      });
      if (isNotEmpty(text)) {
        allText.add(text);
      }
    }
    return allText;
  }

  @NotNull
  public IdeSettingsDialogFixture openIdeSettings() {
    // Using invokeLater because we are going to show a *modal* dialog via API (instead of clicking a button, for example.) If we use
    // GuiActionRunner the test will hang until the modal dialog is closed.
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        Project project = getProject();
        ShowSettingsUtil.getInstance().showSettingsDialog(project, ShowSettingsUtilImpl.getConfigurableGroups(project, true));
      }
    });
    return IdeSettingsDialogFixture.find(robot());
  }

  @NotNull
  public IdeFrameFixture deleteGradleWrapper() {
    deleteWrapper(getProjectPath());
    return this;
  }

  @NotNull
  public IdeFrameFixture requireGradleWrapperSet() {
    File wrapperDirPath = getGradleWrapperDirPath(getProjectPath());
    assertThat(wrapperDirPath).as("Gradle wrapper").isDirectory();
    return this;
  }

  public static void deleteWrapper(@NotNull File projectDirPath) {
    File wrapperDirPath = getGradleWrapperDirPath(projectDirPath);
    delete(wrapperDirPath);
    assertThat(wrapperDirPath).as("Gradle wrapper").doesNotExist();
  }

  @NotNull
  private static File getGradleWrapperDirPath(@NotNull File projectDirPath) {
    return new File(projectDirPath, FD_GRADLE);
  }

  @NotNull
  public IdeFrameFixture useLocalGradleDistribution(@NotNull File gradleHomePath) {
    return useLocalGradleDistribution(gradleHomePath.getPath());
  }

  @NotNull
  public IdeFrameFixture useLocalGradleDistribution(@NotNull String gradleHome) {
    GradleProjectSettings settings = getGradleSettings();
    settings.setDistributionType(LOCAL);
    settings.setGradleHome(gradleHome);
    return this;
  }

  @NotNull
  public GradleProjectSettings getGradleSettings() {
    GradleProjectSettings settings = getGradleProjectSettings(getProject());
    assertNotNull(settings);
    return settings;
  }

  @NotNull
  public AvdManagerDialogFixture invokeAvdManager() {
    ActionButtonFixture button = findActionButtonByActionId("Android.RunAndroidAvdManager");
    button.requireVisible();
    button.requireEnabled();
    button.click();
    return AvdManagerDialogFixture.find(robot());
  }

  @NotNull
  public RunConfigurationsDialogFixture invokeRunConfigurationsDialog() {
    invokeMenuPath("Run", "Edit Configurations...");
    return RunConfigurationsDialogFixture.find(robot());
  }

  @NotNull
  public InspectionsFixture inspectCode() {
    invokeMenuPath("Analyze", "Inspect Code...");

    InspectCodeDialogFixture.find(robot()).clickOk();

    final InspectionTree tree = waitUntilFound(robot(), new GenericTypeMatcher<InspectionTree>(InspectionTree.class) {
      @Override
      protected boolean isMatching(@NotNull InspectionTree component) {
        return true;
      }
    });

    return new InspectionsFixture(robot(), getProject(), tree);
  }

  @NotNull
  public ProjectViewFixture getProjectView() {
    return new ProjectViewFixture(getProject(), robot());
  }

  @NotNull
  public Project getProject() {
    Project project = target().getProject();
    assertNotNull(project);
    return project;
  }

  public void closeProject() {
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        closeAndDispose(getProject());
        RecentProjectsManager.getInstance().updateLastProjectPath();
        WelcomeFrame.showIfNoProjectOpened();
      }
    });
    Wait.seconds(30).expecting("'Welcome' page to show up").until(new Wait.Objective() {
      @Override
      public boolean isMet() {
        for (Frame frame : Frame.getFrames()) {
          if (frame == WelcomeFrame.getInstance() && frame.isShowing()) {
            return true;
          }
        }
        return false;
      }
    });
  }

  @NotNull
  public LibraryPropertiesDialogFixture showPropertiesForLibrary(@NotNull String libraryName) {
    return showPropertiesDialog(robot(), libraryName, getProject());
  }

  @NotNull
  public MessagesFixture findMessageDialog(@NotNull String title) {
    return MessagesFixture.findByTitle(robot(), target(), title);
  }

  @NotNull
  public IdeFrameFixture setGradleJvmArgs(@NotNull final String jvmArgs) {
    Project project = getProject();

    final GradleSettings settings = GradleSettings.getInstance(project);
    settings.setGradleVmOptions(jvmArgs);

    Wait.minutes(2).expecting("Gradle settings to be set").until(new Wait.Objective() {
      @Override
      public boolean isMet() {
        return jvmArgs.equals(settings.getGradleVmOptions());
      }
    });

    return this;
  }

  @NotNull
  public IdeFrameFixture updateGradleWrapperVersion(@NotNull String version) throws IOException {
    File wrapperPropertiesFile = findWrapperPropertiesFile(getProject());
    assertNotNull(wrapperPropertiesFile);
    updateGradleDistributionUrl(version, wrapperPropertiesFile);

    return this;
  }

  @NotNull
  public IdeFrameFixture updateAndroidGradlePluginVersion(@NotNull final String version) throws IOException {
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        boolean updated = updateGradlePluginVersion(getProject(), version, null);
        assertTrue("Android Gradle plugin version was not updated", updated);
      }
    });
    return this;
  }

  @NotNull
  public FindDialogFixture invokeFindInPathDialog() {
    invokeMenuPath("Edit", "Find", "Find in Path...");
    return FindDialogFixture.find(robot());
  }

  @NotNull
  public FindToolWindowFixture getFindToolWindow() {
    return new FindToolWindowFixture(this);
  }

  @NotNull
  public GradleBuildModelFixture parseBuildFileForModule(@NotNull String moduleName, boolean openInEditor) {
    Module module = getModule(moduleName);
    return parseBuildFile(module, openInEditor);
  }

  @NotNull
  public GradleBuildModelFixture parseBuildFile(@NotNull Module module, boolean openInEditor) {
    VirtualFile buildFile = getGradleBuildFile(module);
    assertNotNull(buildFile);
    return parseBuildFile(buildFile, openInEditor);
  }

  @NotNull
  public GradleBuildModelFixture parseBuildFile(@NotNull final VirtualFile buildFile, boolean openInEditor) {
    if (openInEditor) {
      getEditor().open(buildFile, Tab.DEFAULT).getCurrentFile();
    }
    final Ref<GradleBuildModel> buildModelRef = new Ref<GradleBuildModel>();
    new ReadAction() {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        buildModelRef.set(GradleBuildModel.parseBuildFile(buildFile, getProject()));
      }
    }.execute();
    GradleBuildModel buildModel = buildModelRef.get();
    assertNotNull(buildModel);
    return new GradleBuildModelFixture(buildModel);
  }

  private static class NoOpDisposable implements Disposable {
    @Override
    public void dispose() {
    }
  }

  public void selectApp(@NotNull String appName) {
    final ActionButtonFixture runButton = findRunApplicationButton();
    Container actionToolbarContainer = execute(new GuiQuery<Container>() {
      @Override
      protected Container executeInEDT() throws Throwable {
        return runButton.target().getParent();
      }
    });
    assertNotNull(actionToolbarContainer);

    ComboBoxActionFixture comboBoxActionFixture = ComboBoxActionFixture.findComboBox(robot(), actionToolbarContainer);
    comboBoxActionFixture.selectItem(appName);
    robot().pressAndReleaseKey(KeyEvent.VK_ENTER);
    robot().waitForIdle();
  }

  /////////////////////////////////////////////////////////////////
  ////     Methods to help control debugging under a test.  ///////
  /////////////////////////////////////////////////////////////////

  public void resumeProgram() {
    MiscUtils.invokeMenuPathOnRobotIdle(this, "Run", "Resume Program");
  }

  public void stepOver() {
    MiscUtils.invokeMenuPathOnRobotIdle(this, "Run", "Step Over");
  }

  public void stepInto() {
    MiscUtils.invokeMenuPathOnRobotIdle(this, "Run", "Step Into");
  }

  public void stepOut() {
    MiscUtils.invokeMenuPathOnRobotIdle(this, "Run", "Step Out");
  }

  /**
   * Toggles breakpoints at the line numbers in {@code lines} of the source file {@code fileName}.
   */
  public void openAndToggleBreakPoints(String fileName, int[] lines) {
    EditorFixture editor = getEditor().open(fileName);
    for (int line : lines) {
      editor.moveToLine(line);
      invokeMenuPath("Run", "Toggle Line Breakpoint"); // TODO: Use editor.invokeAction instead and provide the mnemonic.
    }
  }

  // Recursively prints out the debugger tree rooted at {@code node} into {@code builder} with an indent of
  // "{@code level} * {@code numIndentSpaces}" whitespaces. Each node is printed on a separate line. The indent level for every child node
  // is 1 more than their parent.
  private static void printNode(XDebuggerTreeNode node, StringBuilder builder, int level, int numIndentSpaces) {
    int numIndent = level;
    if (builder.length() > 0) {
      builder.append(System.getProperty("line.separator"));
    }
    for (int i = 0; i < level * numIndentSpaces; ++i) {
      builder.append(' ');
    }
    builder.append(node.getText().toString());
    Enumeration<XDebuggerTreeNode> children = node.children();
    while (children.hasMoreElements()) {
      printNode(children.nextElement(), builder, level + 1, numIndentSpaces);
    }
  }

  /**
   * Prints out the debugger tree rooted at {@code root}.
   */
  @NotNull
  public static String printDebuggerTree(XDebuggerTreeNode root) {
    StringBuilder builder = new StringBuilder();
    printNode(root, builder, 0, 2);
    return builder.toString();
  }

  @NotNull
  private static String[] debuggerTreeRootToChildrenTexts(XDebuggerTreeNode treeRoot) {
    List<? extends TreeNode> children = treeRoot.getChildren();
    String[] childrenTexts = new String[children.size()];
    int i = 0;
    for (TreeNode child : children) {
      childrenTexts[i] = ((XDebuggerTreeNode)child).getText().toString();
      ++i;
    }
    return childrenTexts;
  }

  /**
   * Returns the subset of {@code expectedPatterns} which do not match any of the children (just the first level children, not recursive) of
   * {@code treeRoot} .
   */
  @NotNull
  public static List<String> getUnmatchedTerminalVariableValues(String[] expectedPatterns, XDebuggerTreeNode treeRoot) {
    String[] childrenTexts = debuggerTreeRootToChildrenTexts(treeRoot);
    List<String> unmatchedPatterns = Lists.newArrayList();
    for (String expectedPattern : expectedPatterns) {
      boolean matched = false;
      for (String childText : childrenTexts) {
        if (childText.matches(expectedPattern)) {
          matched = true;
          break;
        }
      }
      if (!matched) {
        unmatchedPatterns.add(expectedPattern);
      }
    }
    return unmatchedPatterns;
  }

  /**
   * Returns the appropriate pattern to look for a variable named {@code name} with the type {@code type} and value {@code value} appearing
   * in the Variables window in Android Studio.
   */
  @NotNull
  public static String variableToSearchPattern(String name, String type, String value) {
    return String.format("%s = \\{%s\\} %s", name, type, value);
  }

  public boolean verifyVariablesAtBreakpoint(String[] expectedVariablePatterns, String debugConfigName) {
    DebugToolWindowFixture debugToolWindowFixture = new DebugToolWindowFixture(this);
    final ExecutionToolWindowFixture.ContentFixture contentFixture = debugToolWindowFixture.findContent(debugConfigName);

    contentFixture.clickDebuggerTreeRoot();
    Wait.seconds(15).expecting("debugger tree to appear").until(new Wait.Objective() {
      @Override
      public boolean isMet() {
        return contentFixture.getDebuggerTreeRoot() != null;
      }
    });

    // Get the debugger tree and print it.
    XDebuggerTreeNode debuggerTreeRoot = contentFixture.getDebuggerTreeRoot();
    if (debuggerTreeRoot == null) {
      return false;
    }

    List<String> unmatchedPatterns = getUnmatchedTerminalVariableValues(expectedVariablePatterns, debuggerTreeRoot);
    return unmatchedPatterns.isEmpty();
  }
}
