package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.ContainerFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.swing.*;

public class SelectPathFixture implements ContainerFixture<JDialog> {

  private final JDialog myDialog;
  private final Robot myRobot;
  private final IdeFrameFixture myIdeFrameFixture;

  private static final GenericTypeMatcher<JDialog> MATCHER = Matchers.byTitle(JDialog.class, "Select Path");

  @NotNull
  public static SelectPathFixture find(@NotNull IdeFrameFixture ideFrameFixture) {
    JDialog dialog = GuiTests.waitUntilShowing(ideFrameFixture.robot(), MATCHER);
    return new SelectPathFixture(ideFrameFixture, dialog);
  }

  private SelectPathFixture(@NotNull IdeFrameFixture ideFrameFixture, @NotNull JDialog dialog) {
    myIdeFrameFixture = ideFrameFixture;
    myRobot = ideFrameFixture.robot();
    myDialog = dialog;
  }

  @Nonnull
  @Override
  public JDialog target() {
    return myDialog;
  }

  @Nonnull
  @Override
  public Robot robot() {
    return myRobot;
  }

  @NotNull
  public IdeFrameFixture clickOK() {
    GuiTests.findAndClickOkButton(this);
    Wait.seconds(5).expecting("dialog to disappear").until(() -> !target().isShowing());
    return myIdeFrameFixture;
  }
}
