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
package com.android.tools.idea.tests.gui.framework;

import com.android.SdkConstants;
import com.android.testutils.TestUtils;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.testing.AndroidGradleTests;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.WelcomeFrameFixture;
import com.android.tools.idea.tests.gui.framework.guitestprojectsystem.GuiTestProjectSystem;
import com.android.tools.idea.tests.gui.framework.guitestsystem.CurrentGuiTestProjectSystem;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import org.fest.swing.core.Robot;
import org.fest.swing.exception.WaitTimedOutError;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AssumptionViolatedException;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.junit.runner.Description;
import org.junit.runners.model.MultipleFailureException;
import org.junit.runners.model.Statement;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.android.testutils.TestUtils.getWorkspaceFile;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.android.tools.idea.tests.gui.framework.GuiTests.refreshFiles;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.TruthJUnit.assume;
import static com.intellij.openapi.util.io.FileUtil.sanitizeFileName;
import static org.fest.reflect.core.Reflection.*;

public class GuiTestRule implements TestRule {

  /** Hack to solve focus issue when running with no window manager */
  private static final boolean HAS_EXTERNAL_WINDOW_MANAGER = Toolkit.getDefaultToolkit().isFrameStateSupported(Frame.MAXIMIZED_BOTH);

  private IdeFrameFixture myIdeFrameFixture;
  @Nullable private String myTestDirectory;

  private final RobotTestRule myRobotTestRule = new RobotTestRule();
  private final LeakCheck myLeakCheck = new LeakCheck();
  private final CurrentGuiTestProjectSystem myCurrentProjectSystem = new CurrentGuiTestProjectSystem();

  private Timeout myTimeout = new Timeout(5, TimeUnit.MINUTES);

  private final PropertyChangeListener myGlobalFocusListener = e -> {
    Object oldValue = e.getOldValue();
    if ("permanentFocusOwner".equals(e.getPropertyName()) && oldValue instanceof Component && e.getNewValue() == null) {
      Window parentWindow = oldValue instanceof Window ? (Window)oldValue : SwingUtilities.getWindowAncestor((Component)oldValue);
      if (parentWindow instanceof Dialog && ((Dialog)parentWindow).isModal()) {
        Container parent = parentWindow.getParent();
        if (parent != null && parent.isVisible()) {
          parent.requestFocus();
        }
      }
    }
  };

  public GuiTestRule withLeakCheck() {
    myLeakCheck.setEnabled(true);
    return this;
  }

  @NotNull
  @Override
  public Statement apply(final Statement base, final Description description) {
    RuleChain chain = RuleChain.emptyRuleChain()
      .around(new LogStartAndStop())
      .around(new BlockReloading())
      .around(myCurrentProjectSystem)
      .around(myRobotTestRule)
      .around(myLeakCheck)
      .around(new IdeHandling())
      .around(new ScreenshotOnFailure())
      .around(myTimeout);

    // Perf logging currently writes data to the Bazel-specific TEST_UNDECLARED_OUTPUTS_DIR. Skipp logging if running outside of Bazel.
    if (TestUtils.runningFromBazel()) {
      chain = chain.around(new GuiPerfLogger(description));
    }

    return chain.apply(base, description);
  }

  private class IdeHandling implements TestRule {
    @NotNull
    @Override
    public Statement apply(final Statement base, final Description description) {
      return new Statement() {
        @Override
        public void evaluate() throws Throwable {
          if (!TestUtils.runningFromBazel()) {
            // when state can be bad from previous tests, check and skip in that case
            assume().that(GuiTests.fatalErrorsFromIde()).named("IDE errors").isEmpty();
            assumeOnlyWelcomeFrameShowing(description);
          }
          setUp(description.getMethodName());
          List<Throwable> errors = new ArrayList<>();
          try {
            base.evaluate();
          } catch (MultipleFailureException e) {
            errors.addAll(e.getFailures());
          } catch (Throwable e) {
            errors.add(e);
          } finally {
            try {
              boolean hasTestPassed = errors.isEmpty();
              errors.addAll(tearDown());  // shouldn't throw, but called inside a try-finally for defense in depth
              if (hasTestPassed && !errors.isEmpty()) { // If we get a problem during tearDown, take a snapshot.
                new ScreenshotOnFailure().failed(errors.get(0), description);
              }
            } finally {
              //noinspection ThrowFromFinallyBlock; assertEmpty is intended to throw here
              MultipleFailureException.assertEmpty(errors);
            }
          }
        }
      };
    }
  }

  private void assumeOnlyWelcomeFrameShowing(Description description) {
    try {
      WelcomeFrameFixture.find(robot());
    } catch (WaitTimedOutError e) {
      new ScreenshotOnFailure().failed(e, description);
      throw new AssumptionViolatedException("didn't find welcome frame", e);
    }
    assume().that(GuiTests.windowsShowing()).named("windows showing").hasSize(1);
  }

  private void setUp(@Nullable String methodName) {
    myTestDirectory = methodName != null ? sanitizeFileName(methodName) : null;
    GuiTests.setUpDefaultProjectCreationLocationPath(myTestDirectory);
    GuiTests.setIdeSettings();
    GuiTests.setUpSdks();

    // Compute the workspace root before any IDE code starts messing with user.dir:
    TestUtils.getWorkspaceRoot();

    if (!HAS_EXTERNAL_WINDOW_MANAGER) {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener(myGlobalFocusListener);
    }
  }

  private static ImmutableList<Throwable> thrownFromRunning(Runnable r) {
    try {
      r.run();
      return ImmutableList.of();
    }
    catch (Throwable e) {
      return ImmutableList.of(e);
    }
  }

  protected void tearDownProject() {
    if (!robot().finder().findAll(Matchers.byType(IdeFrameImpl.class).andIsShowing()).isEmpty()) {
      ideFrame().closeProject();
    }
    myIdeFrameFixture = null;
  }

  private ImmutableList<Throwable> tearDown() {
    ImmutableList.Builder<Throwable> errors = ImmutableList.builder();
    errors.addAll(thrownFromRunning(this::waitForBackgroundTasks));
    errors.addAll(checkForPopupMenus());
    errors.addAll(checkForModalDialogs());
    errors.addAll(thrownFromRunning(this::tearDownProject));
    if (!HAS_EXTERNAL_WINDOW_MANAGER) {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener(myGlobalFocusListener);
    }
    errors.addAll(GuiTests.fatalErrorsFromIde());
    fixMemLeaks();
    return errors.build();
  }

  private List<AssertionError> checkForModalDialogs() {
    List<AssertionError> errors = new ArrayList<>();
    // We close all modal dialogs left over, because they block the AWT thread and could trigger a deadlock in the next test.
    Dialog modalDialog;

    // Loop can be infinite loop when a modal dialog opens itself again after closing.
    // Prevent infinite loop without a timeout
    long startTime = System.currentTimeMillis();
    long endTime = startTime + TimeUnit.SECONDS.toMillis(10);
    while ((modalDialog = getActiveModalDialog()) != null && System.currentTimeMillis() < endTime) {
      robot().close(modalDialog);
      errors.add(new AssertionError(
        String.format("Modal dialog showing: %s with title '%s'", modalDialog.getClass().getName(), modalDialog.getTitle())));
    }
    if (System.currentTimeMillis() >= endTime) {
      errors.add(new AssertionError("Potential modal dialog infinite loop"));
    }
    return errors;
  }

  private List<AssertionError> checkForPopupMenus() {
    List<AssertionError> errors = new ArrayList<>();

    // Close all opened popup menu items (File > New > etc) before continuing.
    Collection<JPopupMenu> popupMenus = robot().finder().findAll(Matchers.byType(JPopupMenu.class).andIsShowing());
    if (!popupMenus.isEmpty()) {
      errors.add(new AssertionError(String.format("%d Popup Menus left open", popupMenus.size())));
      for (int i = 0; i <= popupMenus.size(); i++) {
        robot().pressAndReleaseKey(KeyEvent.VK_ESCAPE);
      }
    }
    return errors;
  }

  // Note: this works with a cooperating window manager that returns focus properly. It does not work on bare Xvfb.
  private static Dialog getActiveModalDialog() {
    Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    if (activeWindow instanceof Dialog) {
      Dialog dialog = (Dialog)activeWindow;
      if (dialog.getModalityType() == Dialog.ModalityType.APPLICATION_MODAL) {
        return dialog;
      }
    }
    return null;
  }

  private void fixMemLeaks() {
    myIdeFrameFixture = null;

    // Work-around for https://youtrack.jetbrains.com/issue/IDEA-153492
    Class<?> keyboardManagerType = type("javax.swing.KeyboardManager").load();
    Object manager = method("getCurrentManager").withReturnType(Object.class).in(keyboardManagerType).invoke();
    field("componentKeyStrokeMap").ofType(Hashtable.class).in(manager).get().clear();
    field("containerMap").ofType(Hashtable.class).in(manager).get().clear();
  }

  public IdeFrameFixture importSimpleLocalApplication() throws IOException {
    return importProjectAndWaitForProjectSyncToFinish("SimpleLocalApplication");
  }

  /**
   * @deprecated use importSimpleLocalApplication that doesn't use remote repositories.
   */
  @Deprecated()
  public IdeFrameFixture importSimpleApplication() throws IOException {
    return importProjectAndWaitForProjectSyncToFinish("SimpleApplication");
  }

  public IdeFrameFixture importMultiModule() throws IOException {
    return importProjectAndWaitForProjectSyncToFinish("MultiModule");
  }

  public IdeFrameFixture importProjectAndWaitForProjectSyncToFinish(@NotNull String projectDirName) throws IOException {
    importProject(projectDirName);
    testSystem().waitForProjectSyncToFinish(ideFrame());
    return ideFrame();
  }

  public IdeFrameFixture importProject(@NotNull String projectDirName) throws IOException {
    File testProjectDir = setUpProject(projectDirName);
    testSystem().importProject(testProjectDir, robot());
    return ideFrame();
  }

  /**
   * Sets up a project before using it in a UI test:
   * <ul>
   * <li>Makes a copy of the project in testData/guiTests/newProjects (deletes any existing copy of the project first.) This copy is
   * the one the test will use.</li>
   * <li>Creates a Gradle wrapper for the test project.</li>
   * <li>Updates the version of the Android Gradle plug-in used by the project, if applicable</li>
   * <li>Creates a local.properties file pointing to the Android SDK path specified by the system property (or environment variable)
   * 'ANDROID_HOME'</li>
   * <li>Copies over missing files to the .idea directory (if the project will be opened, instead of imported.)</li>
   * <li>Deletes .idea directory, .iml files and build directories, if the project will be imported.</li>
   * <p/>
   * </ul>
   *
   * @param projectDirName             the name of the project's root directory. Tests are located in testData/guiTests.
   * @throws IOException if an unexpected I/O error occurs.
   */
  private File setUpProject(@NotNull String projectDirName) throws IOException {
    File projectPath = copyProjectBeforeOpening(projectDirName);

    testSystem().prepareTestForImport(projectPath);
    createGradleWrapper(projectPath, SdkConstants.GRADLE_LATEST_VERSION);
    updateGradleVersions(projectPath);
    updateLocalProperties(projectPath);
    cleanUpProjectForImport(projectPath);
    refreshFiles();
    return projectPath;
  }

  public File copyProjectBeforeOpening(@NotNull String projectDirName) throws IOException {
    File masterProjectPath = getMasterProjectDirPath(projectDirName);

    File projectPath = getTestProjectDirPath(projectDirName);
    if (projectPath.isDirectory()) {
      FileUtilRt.delete(projectPath);
    }
    FileUtil.copyDir(masterProjectPath, projectPath);
    return projectPath;
  }

  protected boolean createGradleWrapper(@NotNull File projectDirPath, @NotNull String gradleVersion) throws IOException {
    GradleWrapper wrapper = GradleWrapper.create(projectDirPath, gradleVersion);
    File path = getWorkspaceFile("tools/external/gradle/gradle-" + gradleVersion + "-bin.zip");
    assertAbout(file()).that(path).named("Gradle distribution path").isFile();
    wrapper.updateDistributionUrl(path);
    return wrapper != null;
  }

  protected void updateLocalProperties(File projectPath) throws IOException {
    LocalProperties localProperties = new LocalProperties(projectPath);
    localProperties.setAndroidSdkPath(IdeSdks.getInstance().getAndroidSdkPath());
    localProperties.save();
  }

  protected void updateGradleVersions(@NotNull File projectPath) throws IOException {
    AndroidGradleTests.updateGradleVersions(projectPath);
  }

  @NotNull
  protected File getMasterProjectDirPath(@NotNull String projectDirName) {
    return new File(GuiTests.getTestProjectsRootDirPath(), projectDirName);
  }

  @NotNull
  protected File getTestProjectDirPath(@NotNull String projectDirName) {
    return new File(GuiTests.getProjectCreationDirPath(myTestDirectory), projectDirName);
  }

  public void cleanUpProjectForImport(@NotNull File projectPath) {
    File dotIdeaFolderPath = new File(projectPath, Project.DIRECTORY_STORE_FOLDER);
    if (dotIdeaFolderPath.isDirectory()) {
      File modulesXmlFilePath = new File(dotIdeaFolderPath, "modules.xml");
      if (modulesXmlFilePath.isFile()) {
        SAXBuilder saxBuilder = new SAXBuilder();
        try {
          Document document = saxBuilder.build(modulesXmlFilePath);
          XPath xpath = XPath.newInstance("//*[@fileurl]");
          //noinspection unchecked
          List<Element> modules = xpath.selectNodes(document);
          int urlPrefixSize = "file://$PROJECT_DIR$/".length();
          for (Element module : modules) {
            String fileUrl = module.getAttributeValue("fileurl");
            if (!StringUtil.isEmpty(fileUrl)) {
              String relativePath = FileUtil.toSystemDependentName(fileUrl.substring(urlPrefixSize));
              File imlFilePath = new File(projectPath, relativePath);
              if (imlFilePath.isFile()) {
                FileUtilRt.delete(imlFilePath);
              }
              // It is likely that each module has a "build" folder. Delete it as well.
              File buildFilePath = new File(imlFilePath.getParentFile(), "build");
              if (buildFilePath.isDirectory()) {
                FileUtilRt.delete(buildFilePath);
              }
            }
          }
        }
        catch (Throwable ignored) {
          // if something goes wrong, just ignore. Most likely it won't affect project import in any way.
        }
      }
      FileUtilRt.delete(dotIdeaFolderPath);
    }
  }

  public void waitForBackgroundTasks() {
    GuiTests.waitForBackgroundTasks(robot());
  }

  public Robot robot() {
    return myRobotTestRule.getRobot();
  }

  public GuiTestProjectSystem testSystem() {
    return myCurrentProjectSystem.getTestProjectSystem();
  }

  @NotNull
  public File getProjectPath() {
    return ideFrame().getProjectPath();
  }

  @NotNull
  public WelcomeFrameFixture welcomeFrame() {
    return WelcomeFrameFixture.find(robot());
  }

  @NotNull
  public IdeFrameFixture ideFrame() {
    if (myIdeFrameFixture == null || myIdeFrameFixture.isClosed()) {
      // This call to find() creates a new IdeFrameFixture object every time. Each of these Objects creates a new gradleProjectEventListener
      // and registers it with GradleSyncState. This keeps adding more and more listeners, and the new recent listeners are only updated
      // with gradle State when that State changes. This means the listeners may have outdated info.
      myIdeFrameFixture = IdeFrameFixture.find(robot());
      myIdeFrameFixture.requestFocusIfLost();
    }
    return myIdeFrameFixture;
  }

  public GuiTestRule withTimeout(long timeout, @NotNull TimeUnit timeUnits) {
    myTimeout = new Timeout(timeout, timeUnits);
    return this;
  }
}
