package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.ContainerFixture;
import org.fest.swing.fixture.JTableFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowing;

public class SelectModuleFixture implements ContainerFixture<JDialog> {

  private final JDialog myDialog;
  private final Robot myRobot;
  private final IdeFrameFixture myIdeFrameFixture;

  private static final GenericTypeMatcher<JDialog> MATCHER = Matchers.byTitle(JDialog.class, "Choose Modules");

  @NotNull
  public static SelectModuleFixture find(@NotNull IdeFrameFixture ideFrameFixture) {
    return new SelectModuleFixture(ideFrameFixture, waitUntilShowing(ideFrameFixture.robot(), MATCHER));
  }

  private SelectModuleFixture(@NotNull IdeFrameFixture ideFrameFixture, @NotNull JDialog dialog) {
    myIdeFrameFixture = ideFrameFixture;
    myRobot = ideFrameFixture.robot();
    myDialog = dialog;
  }

  @NotNull
  @Override
  public JDialog target() {
    return myDialog;
  }

  @NotNull
  @Override
  public Robot robot() {
    return myRobot;
  }

  @NotNull
  public SelectModuleFixture selectModule(@NotNull String moduleName) {
    JTableFixture moduleTable = new JTableFixture(robot(), robot().finder().findByType(target(), JTable.class, true));
    moduleTable.cell(moduleName).click();
    return this;
  }

  @NotNull
  public IdeFrameFixture clickOK() {
    JButton button = GuiTests.waitUntilShowingAndEnabled(robot(), target(), Matchers.byText(JButton.class, "OK"));
    // Click the "OK" button pragmatically to eliminate the flakiness due to a possible click miss.
    GuiTask.execute(() -> button.doClick());
    Wait.seconds(5).expecting("dialog to disappear").until(() -> !target().isShowing());
    return myIdeFrameFixture;
  }
}
