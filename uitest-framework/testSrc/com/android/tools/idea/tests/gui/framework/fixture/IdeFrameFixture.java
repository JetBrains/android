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

import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.android.tools.idea.tests.gui.framework.GuiTests.waitForBackgroundTasks;
import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowingAndEnabled;
import static com.android.tools.idea.tests.gui.framework.UiTestUtilsKt.waitForIdle;
import static com.android.tools.idea.ui.GuiTestingService.EXECUTE_BEFORE_PROJECT_BUILD_IN_GUI_TEST_KEY;
import static com.google.common.base.Preconditions.checkArgument;
import static java.awt.event.InputEvent.CTRL_MASK;
import static java.awt.event.InputEvent.META_MASK;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.project.build.BuildStatus;
import com.android.tools.idea.gradle.project.build.GradleBuildState;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.run.ui.ApplyChangesAction;
import com.android.tools.idea.run.ui.CodeSwapAction;
import com.android.tools.idea.testing.TestModuleUtil;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.AvdManagerDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.DeviceManagerToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.AGPUpgradeAssistantToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.GradleBuildModelFixture;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.GradleProjectEventListener;
import com.android.tools.idea.tests.gui.framework.fixture.gradle.GradleToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.run.deployment.DeviceSelectorFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.actions.RunConfigurationsComboBoxAction;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.toolWindow.StripeButton;
import com.intellij.toolWindow.ToolWindowPane;
import com.intellij.ui.BalloonImpl;
import com.intellij.util.ThreeState;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.io.File;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.core.WindowAncestorFinder;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.fixture.JToggleButtonFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IdeFrameFixture extends ComponentFixture<IdeFrameFixture, IdeFrameImpl> {
  private EditorFixture myEditor;
  private boolean myIsClosed;

  @NotNull
  public static IdeFrameFixture find(@NotNull final Robot robot) {
    return new IdeFrameFixture(robot, GuiTests.waitUntilShowing(robot, Matchers.byType(IdeFrameImpl.class)));
  }

  private IdeFrameFixture(@NotNull Robot robot, @NotNull IdeFrameImpl target) {
    super(IdeFrameFixture.class, robot, target);
  }

  @NotNull
  public File getProjectPath() {
    return new File(target().getProject().getBasePath());
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
  public AndroidModuleModel getAndroidProjectForModule(@NotNull String name) {
    Module module = getModule(name);
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null && AndroidModel.isRequired(facet)) {
      // TODO: Resolve direct AndroidGradleModel dep (b/22596984)
      AndroidModuleModel androidModel = AndroidModuleModel.get(facet);
      if (androidModel != null) {
        return androidModel;
      }
    }
    throw new AssertionError("Unable to find AndroidGradleModel for module '" + name + "'");
  }

  @NotNull
  public Module getModule(@NotNull String name) {
    return TestModuleUtil.findModule(getProject(), name);
  }

  public boolean hasModule(@NotNull String name) {
    return TestModuleUtil.hasModule(getProject(), name);
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
  public BuildStatus invokeProjectMake() {
    return invokeProjectMake(null);
  }

  @NotNull
  public BuildStatus invokeProjectMake(@Nullable Wait wait) {
    return actAndWaitForBuildToFinish(wait, it -> it.waitAndInvokeMenuPath("Build", "Make Project"));
  }

  @NotNull
  public IdeFrameFixture invokeProjectMakeAndSimulateFailure(@NotNull String failure) {
    Runnable failTask = () -> {
      throw new ExternalSystemException(failure);
    };
    ApplicationManager.getApplication().putUserData(EXECUTE_BEFORE_PROJECT_BUILD_IN_GUI_TEST_KEY, failTask);
    waitAndInvokeMenuPath("Build", "Make Project");
    return this;
  }

  @NotNull
  public ThreeComponentsSplitterFixture findToolWindowSplitter() {
    ToolWindowPane toolWindowPane = GuiTests.waitUntilFound(robot(), target(), Matchers.byType(ToolWindowPane.class));
    ThreeComponentsSplitter splitter = (ThreeComponentsSplitter) toolWindowPane.getLayeredPane().getComponent(0);
    return new ThreeComponentsSplitterFixture(robot(), splitter);
  }

  /**
   * Finds the Run button in the IDE interface.
   *
   * @return ActionButtonFixture for the run button.
   */
  @NotNull
  public ActionButtonFixture findDebugApplicationButton() {
    return findActionButtonWithRefresh("Debug");
  }

  @NotNull
  public ActionButtonFixture findRunApplicationButton() {
    return findActionButtonWithRefresh("Run");
  }

  @NotNull
  public ActionButtonFixture findStopButton() {
    return findActionButtonWithRefresh(ExecutionBundle.message("run.configuration.stop.action.name"));
  }

  @NotNull
  public ActionButtonFixture findApplyChangesButton(boolean enabled) {
    return findActionButtonWithRefresh(ApplyChangesAction.ID, enabled);
  }

  @NotNull
  public ActionButtonFixture findApplyCodeChangesButton(boolean enabled) {
    return findActionButtonWithRefresh(CodeSwapAction.ID, enabled);
  }

  @NotNull
  public ActionButtonFixture findAttachDebuggerToAndroidProcessButton() {
    GenericTypeMatcher<ActionButton> matcher = Matchers.byText(ActionButton.class, "Attach Debugger to Android Process")
      .andIsShowing();
    return ActionButtonFixture.findByMatcher(matcher, robot(), target());
  }

  @NotNull
  public IdeFrameFixture selectDevice(@NotNull String device) {
    new DeviceSelectorFixture(robot(), this).selectItem(device);
    return this;
  }

  public void troubleshootDeviceConnections(@NotNull String appName) {
    new DeviceSelectorFixture(robot(), this).troubleshootDeviceConnections(appName);
  }

  @NotNull
  public IdeFrameFixture recordEspressoTest(@NotNull String device) {
    new DeviceSelectorFixture(robot(), this).recordEspressoTest(device);
    return this;
  }

  public void debugApp(@NotNull String appName, @NotNull String deviceName) {
    debugApp(appName, deviceName, null);
  }

  public void debugApp(@NotNull String appName, @NotNull String deviceName, @Nullable Wait wait) {
    new DeviceSelectorFixture(robot(), this).debugApp(appName, deviceName);
    waitForIdle();
    waitForBackgroundTasks(robot(), wait);
    waitForIdle();
  }

  public void runApp(@NotNull String appName, @NotNull String deviceName) {
    runApp(appName, deviceName, null);
  }

  public void runApp(@NotNull String appName, @NotNull String deviceName, @Nullable Wait wait) {
    new DeviceSelectorFixture(robot(), this).runApp(appName, deviceName);
    waitForIdle();
    waitForBackgroundTasks(robot(), wait);
    waitForIdle();
  }

  @NotNull
  public IdeFrameFixture stopApp() {
    return invokeMenuPath("Run", "Stop \'app\'");
  }

  @NotNull
  public IdeFrameFixture stopAll() {
    invokeMenuPath("Run", "Stop...");
    robot().pressAndReleaseKey(KeyEvent.VK_F2, CTRL_MASK); // Stop All (Ctrl + F2)
    return this;
  }

  @NotNull
  public IdeFrameFixture stepOver() {
    return invokeMenuPath("Run", "Step Over");
  }

  @NotNull
  public IdeFrameFixture smartStepInto() {
    return invokeMenuPath("Run", "Smart Step Into");
  }

  @NotNull
  public IdeFrameFixture resumeProgram() {
    return invokeMenuPath("Run", "Resume Program");
  }

  @NotNull
  public RunToolWindowFixture getRunToolWindow() {
    return new RunToolWindowFixture(this);
  }

  @NotNull
  public DebugToolWindowFixture getDebugToolWindow() {
    return new DebugToolWindowFixture(this);
  }

  /**
   * Selects the item at {@code menuPath} and returns the result of
   * {@code fixtureFunction} applied to this {@link IdeFrameFixture}.
   */
  public <T> T openFromMenu(Function<IdeFrameFixture, T> fixtureFunction, @NotNull String... menuPath) {
    getMenuFixture().invokeMenuPath(10, menuPath);
    return fixtureFunction.apply(this);
  }

  /**
   * Selects the item at {@code menuPath} in a contextual menu
   * and returns the result of {@code fixtureFunction} applied to this
   * {@link IdeFrameFixture}.
   */
  public <T> T openFromContextualMenu(Function<IdeFrameFixture, T> fixtureFunction, @NotNull String... menuPath) {
    getMenuFixture().invokeContextualMenuPath(menuPath);
    return fixtureFunction.apply(this);
  }

  /**
   * Invokes an action by menu path
   *
   * @param path the series of menu names, e.g.
   *             {@link invokeActionByMenuPath("Build", "Make Project")}
   */
  public IdeFrameFixture invokeMenuPath(@NotNull String... path) {
    getMenuFixture().invokeMenuPath(10, path);
    return this;
  }

  /**
   * Invokes an action by menu path in a contextual menu
   *
   * @param path the series of menu names
   */
  public IdeFrameFixture invokeContextualMenuPath(@NotNull String... path) {
    getMenuFixture().invokeContextualMenuPath(path);
    return this;
  }

  /**
   * Wait till an path is enabled then invokes the action. Used for menu options
   * that might be disabled or not available at first
   *
   * @param path the series of menu names, e.g.
   *             {@link invokeActionByMenuPath("Build", "Make Project")}
   */
  public IdeFrameFixture waitAndInvokeMenuPath(@NotNull String... path) {
    waitAndInvokeMenuPath(20, path);
    return this;
  }

  private IdeFrameFixture waitAndInvokeMenuPath(int timeToWait, @NotNull String... path) {
    getMenuFixture().invokeMenuPath(timeToWait, path);
    return this;
  }

  @NotNull
  private MenuFixture getMenuFixture() {
    return new MenuFixture(robot(), target());
  }

  @NotNull
  public IdeFrameFixture invokeAndWaitForBuildAction(@NotNull String... menuPath) {
    return invokeAndWaitForBuildAction(null, menuPath);
  }

  @NotNull
  public IdeFrameFixture invokeAndWaitForBuildAction(@Nullable Wait wait, @NotNull String... menuPath) {
    assertTrue("Build failed", actAndWaitForBuildToFinish(wait, it -> it.waitAndInvokeMenuPath(menuPath)).isBuildSuccessful());
    return this;
  }

  public BuildStatus actAndWaitForBuildToFinish(@NotNull Consumer<IdeFrameFixture> actions) {
    return actAndWaitForBuildToFinish(null, actions);
  }

  public BuildStatus actAndWaitForBuildToFinish(@Nullable Wait wait, @NotNull Consumer<IdeFrameFixture> actions) {
    GradleProjectEventListener gradleProjectEventListener = new GradleProjectEventListener();
    Disposable disposable = Disposer.newDisposable();
    try {
      GradleBuildState.subscribe(getProject(), gradleProjectEventListener, disposable);
      long beforeStartedTimeStamp = System.currentTimeMillis();
      Project project = getProject();
      actions.accept(this);

      (wait != null ? wait : Wait.seconds(90))
        .expecting("build '" + project.getName() + "' to finish")
        .until(() -> gradleProjectEventListener.getLastBuildTimestamp() > beforeStartedTimeStamp);

      GuiTests.waitForProjectIndexingToFinish(getProject());
      GuiTests.waitForBackgroundTasks(robot());
      waitForIdle();
      return gradleProjectEventListener.getBuildStatus();
    } finally {
      Disposer.dispose(disposable);
    }
  }

  /**
   * Returns the virtual file corresponding to the given path. The path must be
   * relative to the project root directory
   * (the top-level directory containing all source files associated with the
   * project).
   *
   * @param relativePath a file path relative to the project root directory
   * @return the virtual file corresponding to {@code relativePath}, or
   *         {@code null} if no such file exists
   */
  @Nullable
  public VirtualFile findFileByRelativePath(@NotNull String relativePath) {
    checkArgument(!relativePath.contains("\\"), "Should use '/' in test relative paths, not File.separator");

    VirtualFile projectRootDir = getProject().getBaseDir();
    projectRootDir.refresh(false, true);
    return projectRootDir.findFileByRelativePath(relativePath);
  }

  public void requestProjectSync() {
    GuiTests.waitForBackgroundTasks(robot(), null);
    waitAndInvokeMenuPath(20, "File", "Sync Project with Gradle Files");
  }

  @NotNull
  public IdeFrameFixture requestProjectSyncAndWaitForSyncToFinish() {
    return actAndWaitForGradleProjectSyncToFinish(it -> it.requestProjectSync());
  }

  @NotNull
  public IdeFrameFixture requestProjectSyncAndWaitForSyncToFinish(@NotNull Wait waitSync) {
    return actAndWaitForGradleProjectSyncToFinish(waitSync, it -> it.requestProjectSync());
  }

  public boolean isGradleSyncNotNeeded() {
    return GradleSyncState.getInstance(getProject()).isSyncNeeded() == ThreeState.NO;
  }

  @NotNull
  public IdeFrameFixture actAndWaitForGradleProjectSyncToFinish(@NotNull Consumer<IdeFrameFixture> actions) {
    return actAndWaitForGradleProjectSyncToFinish(null, actions);
  }

  @NotNull
  public IdeFrameFixture actAndWaitForGradleProjectSyncToFinish(@Nullable Wait waitForSync,
                                                                @NotNull Consumer<IdeFrameFixture> actions) {
    return actAndWaitForGradleProjectSyncToFinish(
      waitForSync,
      () -> {
        actions.accept(this);
        return this;
      });
  }

  @NotNull
  public static IdeFrameFixture actAndWaitForGradleProjectSyncToFinish(
    @NotNull Supplier<? extends IdeFrameFixture> actions) {
    return actAndWaitForGradleProjectSyncToFinish(null, actions);
  }

  public static IdeFrameFixture actAndWaitForGradleProjectSyncToFinish(@Nullable Wait waitForSync,
                                                                       @NotNull Supplier<? extends IdeFrameFixture> ideFrame) {
    long beforeStartedTimeStamp = System.currentTimeMillis();

    IdeFrameFixture ideFixture = ideFrame.get();
    Project project = ideFixture.getProject();

    // Wait for indexing to complete to add additional waiting time if indexing (up
    // to 120 seconds). Otherwise, sync timeout may expire
    // too soon.
    GuiTests.waitForProjectIndexingToFinish(ideFixture.getProject());

    (waitForSync != null ? waitForSync : Wait.seconds(60))
      .expecting("syncing project '" + project.getName() + "' to finish")
      .until(() -> GradleSyncState.getInstance(project).getLastSyncFinishedTimeStamp() > beforeStartedTimeStamp);

    if (GradleSyncState.getInstance(project).lastSyncFailed()) {
      fail("Sync failed. See logs.");
    }

    GuiTests.waitForProjectIndexingToFinish(ideFixture.getProject());
    waitForIdle();
    // Wait for other tasks like native sync that might have been triggered.
    GuiTests.waitForBackgroundTasks(ideFixture.robot());
    waitForIdle();
    return ideFixture;
  }

  public boolean waitForGradleSyncToFinish(@Nullable Wait waitForSync) {
    long beforeStartedTimeStamp = System.currentTimeMillis();
    Project project = this.getProject();
    (waitForSync != null ? waitForSync : Wait.seconds(60))
      .expecting("syncing project '" + project.getName() + "' to finish")
      .until(() -> GradleSyncState.getInstance(project).getLastSyncFinishedTimeStamp() > beforeStartedTimeStamp);
    if (GradleSyncState.getInstance(project).lastSyncFailed()) {
      return false;
    }
    waitForIdle();
    return true;
  }

  @NotNull
  private ActionButtonFixture locateActionButtonByActionId(@NotNull String actionId) {
    return ActionButtonFixture.locateByActionId(actionId, robot(), target(), 30);
  }

  /**
   * IJ doesn't always refresh the state of the toolbar buttons. This forces it to
   * refresh.
   */
  @NotNull
  public IdeFrameFixture updateToolbars() {
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() {
        ActionToolbarImpl.updateAllToolbarsImmediately();
      }
    });
    return this;
  }

  /**
   * ActionButtons while being recreated by IJ and queried through the Action
   * system
   * may not have a proper parent. Therefore, we can not rely on FEST's system of
   * checking the component tree, as that will cause the test to immediately fail.
   */
  private static boolean hasValidWindowAncestor(@NotNull Component target) {
    return execute(new GuiQuery<Boolean>() {
      @Nullable
      @Override
      protected Boolean executeInEDT() {
        return WindowAncestorFinder.windowAncestorOf(target) != null;
      }
    });
  }

  /**
   * Finds the button while refreshing over the toolbar.
   * <p>
   * Due to IJ refresh policy (will only refresh if it detects mouse movement over
   * its window),
   * the toolbar needs to be intermittently updated before the ActionButton moves
   * to the target
   * location and update to its final state.
   */
  @NotNull
  private ActionButtonFixture findActionButtonWithRefresh(@NotNull String actionId, boolean enabled) {
    Ref<ActionButtonFixture> fixtureRef = new Ref<>();
    Wait.seconds(30)
      .expecting("button to enable")
      .until(() -> {
        updateToolbars();
        // Actions can somehow get replaced, so we need to re-get the action when we
        // attempt to check its state.
        ActionButtonFixture fixture = locateActionButtonByActionId(actionId);
        fixtureRef.set(fixture);
        if (hasValidWindowAncestor(fixture.target())) {
          return execute(new GuiQuery<Boolean>() {
            @Nullable
            @Override
            protected Boolean executeInEDT() {
              if (WindowAncestorFinder.windowAncestorOf(fixture.target()) != null) {
                ActionButton button = fixture.target();
                AnAction action = button.getAction();
                Presentation presentation = action.getTemplatePresentation();
                return presentation.isEnabledAndVisible() &&
                       button.isEnabled() == enabled &&
                       button.isShowing() &&
                       button.isVisible();
              }
              return false;
            }
          });
        }

        return false;
      });
    return fixtureRef.get();
  }

  @NotNull
  private ActionButtonFixture findActionButtonWithRefresh(@NotNull String actionId) {
    return findActionButtonWithRefresh(actionId, true);
  }

  @NotNull
  private ActionButtonFixture findActionButtonByActionId(String actionId, long secondsToWait) {
    return ActionButtonFixture.findByActionId(actionId, robot(), target(), secondsToWait);
  }

  @NotNull
  public BuildVariantsToolWindowFixture getBuildVariantsWindow() {
    return new BuildVariantsToolWindowFixture(this);
  }

  @NotNull
  public BuildToolWindowFixture getBuildToolWindow() {
    return new BuildToolWindowFixture(getProject(), robot());
  }

  @NotNull
  public GradleToolWindowFixture getGradleToolWindow() {
    return new GradleToolWindowFixture(getProject(), robot());
  }

  @NotNull
  public AGPUpgradeAssistantToolWindowFixture getUgradeAssistantToolWindow(boolean invokeUsingMenuPath) {
    if (invokeUsingMenuPath == true) {
      waitAndInvokeMenuPath("Tools", "AGP Upgrade Assistant...");
    }
    return new AGPUpgradeAssistantToolWindowFixture(this);
  }

  @NotNull
  public IdeFrameFixture clearNotificationsPresentOnIdeFrame() {
    List<BalloonImpl.ActionButton> allNotificationActions = Lists.newArrayList(robot().finder().findAll(target(), Matchers.byType(BalloonImpl.ActionButton.class)));
    if (allNotificationActions.size() > 0) {
      for (BalloonImpl.ActionButton action : allNotificationActions) {
        if (action.isShowing() && action.getToolTipText().toLowerCase().contains("close")) {
          robot().click(action);
        }
      }
    }
    return this;
  }

  @NotNull
  public String getAndroidStudioVersion() {
    AboutAndroidStudioFixture aboutStudio = AboutAndroidStudioFixture.openAboutStudioDialog(this);
    takeScreenshot();
    String name = aboutStudio.getAndroidStudioVersion();
    aboutStudio.clickCopy();
    return name;
  }

  public void takeScreenshot() {
    Timestamp timestamp = new Timestamp(System.currentTimeMillis());
    String screenShotName = timestamp.toString();
    GuiTests.takeScreenshot(this.robot(), screenShotName);
  }

  @NotNull
  public void clickPopupMenuItem(String labelPrefix) {
    PopupMenuFixture.clickPopupMenuItem(this, labelPrefix);
  }

  @NotNull
  public IdeSettingsDialogFixture openIdeSettings() {
    // Using invokeLater because we are going to show a *modal* dialog via API
    // (instead of clicking a button, for example.) If we use
    // GuiActionRunner the test will hang until the modal dialog is closed.
    ApplicationManager.getApplication().invokeLater(
      () -> {
        Project project = getProject();
        ShowSettingsUtil.getInstance().showSettingsDialog(project, ShowSettingsUtilImpl.getConfigurableGroups(project, true));
      });
    IdeSettingsDialogFixture settings = IdeSettingsDialogFixture.find(robot());
    robot().waitForIdle();
    return settings;
  }

  @NotNull
  public AvdManagerDialogFixture invokeAvdManager() {
    // The action button is prone to move during rendering so that robot.click()
    // could miss.
    // So, we use component's click here directly.
    ActionButtonFixture actionButtonFixture = findActionButtonByActionId("Android.DeviceManager", 30);
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() {
        actionButtonFixture.target().click();
      }
    });
    return AvdManagerDialogFixture.find(robot(), this);
  }

  @NotNull
  public DeviceManagerToolWindowFixture invokeDeviceManager() {
    // The action button is prone to move during rendering so that robot.click()
    // could miss.
    // So, we use component's click here directly.
    ActionButtonFixture actionButtonFixture = findActionButtonByActionId("Android.DeviceManager", 30);
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() {
        actionButtonFixture.target().click();
      }
    });
    return new DeviceManagerToolWindowFixture(this);
  }

  @NotNull
  public IdeSettingsDialogFixture invokeSdkManager() {
    ActionButton sdkButton = waitUntilShowingAndEnabled(robot(), target(), new GenericTypeMatcher<ActionButton>(ActionButton.class) {
      @Override protected boolean isMatching(@NotNull ActionButton actionButton) {
        return "SDK Manager".equals(actionButton.getAccessibleContext().getAccessibleName());
      }
    });
    robot().click(sdkButton);
    return IdeSettingsDialogFixture.find(robot());
  }

  @NotNull
  public ProjectViewFixture getProjectView() {
    return new ProjectViewFixture(this);
  }

  @NotNull
  public Project getProject() {
    return target().getProject();
  }

  public WelcomeFrameFixture closeProject() {
    myIsClosed = true;
    requestFocusIfLost(); // "Close Project" can be disabled if no component has focus
    return openFromMenu(WelcomeFrameFixture::find, "File", "Close Project");
  }

  public boolean isClosed() {
    return myIsClosed;
  }

  @NotNull
  public MessagesFixture findMessageDialog(@NotNull String title) {
    return MessagesFixture.findByTitle(robot(), title);
  }

  @NotNull
  public DialogFixture waitForDialog(@NotNull String title) {
    return new DialogFixture(robot(), GuiTests.waitUntilShowing(robot(), Matchers.byTitle(JDialog.class, title)));
  }

  @NotNull
  public DialogFixture waitForDialog(@NotNull String title, long secondsToWait) {
    return new DialogFixture(robot(), GuiTests.waitUntilShowing(robot(), null, Matchers.byTitle(JDialog.class, title), secondsToWait));
  }

  @NotNull
  public GradleBuildModelFixture parseBuildFileForModule(@NotNull String moduleName) {
    Module module = getModule(moduleName);
    VirtualFile buildFile = getGradleBuildFile(module);
    Ref<GradleBuildModel> buildModelRef = new Ref<>();
    ReadAction.run(() -> buildModelRef.set(GradleBuildModel.parseBuildFile(buildFile, getProject())));
    return new GradleBuildModelFixture(buildModelRef.get());
  }

  private static class NoOpDisposable implements Disposable {
    @Override
    public void dispose() {
    }
  }

  public void selectApp(@NotNull String appName) {
    ActionButtonFixture runButton = findRunApplicationButton();
    Container actionToolbarContainer = GuiQuery.getNonNull(() -> runButton.target().getParent());

    ComboBoxActionFixture comboBoxActionFixture = ComboBoxActionFixture.findComboBoxByClientPropertyAndText(
      robot(),
      actionToolbarContainer,
      "styleCombo",
      RunConfigurationsComboBoxAction.class,
      appName);

    comboBoxActionFixture.selectItem(appName);
    robot().pressAndReleaseKey(KeyEvent.VK_ENTER);
    Wait.seconds(1).expecting("ComboBox to be selected")
      .until(() -> appName.equals(comboBoxActionFixture.getSelectedItemText()));
  }

  /**
   * Gets the focus back to Android Studio if it was lost
   */
  public void requestFocusIfLost() {
    KeyboardFocusManager keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    Wait.seconds(5).expecting("a component to have the focus").until(() -> {
      // Keep requesting focus until it is obtained by a component which is showing.
      // Since there is no guarantee that the request focus will
      // be granted keep asking until it is. This problem has appeared at least when
      // not using a window manager when running tests. The focus
      // can sometimes temporarily be held by a component that is not showing, when
      // closing a dialog for example. This is a transition state
      // and we want to make sure to keep going until the focus is held by a stable
      // component.
      Component focusOwner = keyboardFocusManager.getFocusOwner();
      if (focusOwner == null || !focusOwner.isShowing()) {
        if (SystemInfo.isMac) {
          robot().click(target(), new Point(1, 1)); // Simulate title bar click
        }
        GuiTask.execute(() -> target().requestFocus());
        return false;
      }
      return true;
    });
  }

  public void selectPreviousEditor() {
    robot().pressAndReleaseKey(KeyEvent.VK_E, SystemInfo.isMac ? META_MASK : CTRL_MASK);
    GuiTests.waitUntilShowing(robot(), new GenericTypeMatcher<JLabel>(JLabel.class) {
      @Override
      protected boolean isMatching(@NotNull JLabel header) {
        return Objects.equals(header.getText(), "Recent Files");
      }
    });
    robot().pressAndReleaseKey(KeyEvent.VK_ENTER, 0);
  }

  @NotNull
  public Dimension getIdeFrameSize() {
    return target().getSize();
  }

  @NotNull
  @SuppressWarnings("UnusedReturnValue")
  public IdeFrameFixture setIdeFrameSize(@NotNull Dimension size) {
    target().setSize(size);
    return this;
  }

  @NotNull
  public IdeFrameFixture closeProjectPanel() {
    new JToggleButtonFixture(robot(),
                             GuiTests.waitUntilShowing(robot(), Matchers.byText(StripeButton.class, "Project"))).deselect();
    return this;
  }

  @NotNull
  public IdeFrameFixture closeBuildPanel() {
    new JToggleButtonFixture(robot(), GuiTests.waitUntilShowing(robot(), Matchers.byText(StripeButton.class, "Build")))
      .deselect();
    return this;
  }

  @NotNull
  public IdeFrameFixture openResourceManager() {
    new JToggleButtonFixture(robot(),
                             GuiTests.waitUntilShowing(robot(), Matchers.byText(StripeButton.class, "Resource Manager"))).select();
    return this;
  }

  @NotNull
  public IdeFrameFixture closeResourceManager() {
    new JToggleButtonFixture(robot(),
                             GuiTests.waitUntilShowing(robot(), Matchers.byText(StripeButton.class, "Resource Manager")))
      .deselect();
    return this;
  }
}
