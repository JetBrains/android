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

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitForPopup;
import static org.fest.reflect.core.Reflection.field;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.npw.BrowseSamplesWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewProjectWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.sdk.SdkProblemDialogFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.options.newEditor.SettingsTreeView;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.wm.impl.welcomeScreen.FlatWelcomeFrame;
import com.intellij.openapi.wm.impl.welcomeScreen.RecentProjectPanel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure;
import java.io.File;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import org.fest.swing.cell.JTreeCellReader;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.exception.LocationUnavailableException;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.fixture.JTreeFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

public class WelcomeFrameFixture extends ComponentFixture<WelcomeFrameFixture, FlatWelcomeFrame> {

  @NotNull
  public static WelcomeFrameFixture find(@NotNull Robot robot) {
    WelcomeFrameFixture fixture = new WelcomeFrameFixture(robot, GuiTests.waitUntilShowing(robot, Matchers.byType(FlatWelcomeFrame.class)));
    fixture.target().toFront();
    return fixture;
  }

  @NotNull
  public static WelcomeFrameFixture find(@NotNull IdeFrameFixture ideFrameFixture) {
    return find(ideFrameFixture.robot());
  }

  private WelcomeFrameFixture(@NotNull Robot robot, @NotNull FlatWelcomeFrame target) {
    super(WelcomeFrameFixture.class, robot, target);
  }

  public SdkProblemDialogFixture createNewProjectWhenSdkIsInvalid() {
    findAndClickButton("New Project");
    return SdkProblemDialogFixture.find(this);
  }

  @NotNull
  public NewProjectWizardFixture createNewProject() {
    findAndClickButton("New Project");
    return NewProjectWizardFixture.find(robot());
  }

  @NotNull
  public FileChooserDialogFixture profileOrDebugApk(@NotNull File apk) {
    // The file chooser is quite slow and we don't have a good way to find when loading finished (there used to be
    // a loading spinner, but was removed from the platform). To make sure we don't have to wait, we pre-inject the path.
    PropertiesComponent.getInstance().setValue("last.apk.imported.location", FileUtil.toSystemDependentName(apk.getPath()));

    clickMoreOptionsItem("Profile or Debug APK");
    return FileChooserDialogFixture.findDialog(robot(), "Select APK File");
  }

  @NotNull
  public BrowseSamplesWizardFixture importCodeSample() {
    clickMoreOptionsItem("Import an Android Code Sample");
    return BrowseSamplesWizardFixture.find(robot());
  }

  private static final JTreeCellReader TREE_NODE_CELL_READER = (jTree, modelValue) -> {
    Object userObject = ((DefaultMutableTreeNode)modelValue).getUserObject();
    if (userObject instanceof String) { // It is a String ("loading...") if the cell is not loaded yet.
      return (String)userObject;
    } else {
      return field("ProjectActionRenderer").ofType(String.class)
        .in(((FilteringTreeStructure.FilteringNode)userObject).getDelegate()).get();
    }
  };

  @NotNull
  public IdeFrameFixture openTheMostRecentProject(@NotNull GuiTestRule guiTestRule) {
    GuiTests.waitForBackgroundTasks(robot(), Wait.seconds(120));
    JBScrollPane jbScrollPane = robot().finder().findByType(JBScrollPane.class);
    JTree processTree = robot().finder().findByType(jbScrollPane, JTree.class, true);

    JTreeFixture jTreeFixture = new JTreeFixture(robot(), processTree);
    jTreeFixture.replaceCellReader(TREE_NODE_CELL_READER);
    // It takes a few seconds to load the whole tree.

    Wait.seconds(20).expecting("The desired path is loaded").until(() -> {
      try {
        jTreeFixture.clickRow(0);
        return true;
      } catch (LocationUnavailableException e) {
        return false;
      }
    });

    Wait
      .seconds(60)
      .expecting("Project to be open")
      .until(() -> ProjectManager.getInstance().getOpenProjects().length == 1);

    return guiTestRule.ideFrame();
  }

  public void openSdkManager() {
    JBList jbList = robot().finder().findByType(target(), JBList.class, true);
    new JListFixture(robot(), jbList).clickItem(1);

    findAndClickButton("All settings\u2026");

    SettingsTreeView settingsTreeView = robot().finder().findByType(SettingsTreeView.class);
    JTree settingsList = robot().finder().findByType(settingsTreeView, JTree.class, true);

    new JTreeFixture(robot(), settingsList)
      .expandPath("Appearance & Behavior")
      .expandPath("Appearance & Behavior/System Settings")
      .clickPath("Appearance & Behavior/System Settings/Android SDK");
  }

  public void openSdkManagerFromMoreOptions() {
    clickMoreOptionsItem("SDK Manager");
  }


  private void findAndClickButton(@NotNull String text) {
    JComponent buttonLabel = GuiTests.waitUntilShowingAndEnabled(robot(), target(), new GenericTypeMatcher<JComponent>(JComponent.class) {
      @Override
      protected boolean isMatching(@NotNull JComponent comp) {
        // Depending if the Welcome Wizard has recent Projects, we can have a buttons at the top or a JLabel inside a panel.
        return (comp instanceof JBOptionButton && text.equals(((JButton) comp).getText())) ||
               (comp instanceof JLabel && text.equals(((JLabel) comp).getText()));
      }
    });

    if (buttonLabel instanceof JButton) {
      robot().click(buttonLabel);
    }
    else if (buttonLabel instanceof LinkLabel) {
      robot().click(buttonLabel, ((LinkLabel<?>) buttonLabel).getTextRectangleCenter());
    }
    else {
      robot().click(buttonLabel.getParent());
    }
  }

  private void clickMoreOptionsItem(@NotNull String text) {
    JComponent moreActionsLabel = GuiTests.waitUntilShowingAndEnabled(robot(), target(), new GenericTypeMatcher<JComponent>(JComponent.class) {
      @Override
      protected boolean isMatching(@NotNull JComponent comp) {
        // Depending if the Welcome Wizard has recent Projects, we can have a buttons at the top or a JLabel inside a panel.
        return (comp instanceof JButton && "More Actions".equals(((JButton) comp).getText())) ||
               (comp instanceof ActionButton && "More Actions".equals(((ActionButton)comp).getAction().getTemplateText()));
      }
    });

    robot().click(moreActionsLabel);

    // Mouse needs to "move over" the menu item for it to be selected/focused. Call drag() to simulate that.
    new JListFixture(robot(), waitForPopup(robot())).item(text).drag().click();
  }
}
