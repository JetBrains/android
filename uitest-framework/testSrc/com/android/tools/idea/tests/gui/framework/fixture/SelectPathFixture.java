package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.util.ui.AnimatedIcon;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.ContainerFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilGone;
import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowing;

public class SelectPathFixture implements ContainerFixture<JDialog> {

  private final JDialog myDialog;
  private final Robot myRobot;
  private final IdeFrameFixture myIdeFrameFixture;

  private static final GenericTypeMatcher<JDialog> MATCHER = Matchers.byTitle(JDialog.class, "Select Path");

  @NotNull
  public static SelectPathFixture find(@NotNull IdeFrameFixture ideFrameFixture) {
    JDialog dialog = waitUntilShowing(ideFrameFixture.robot(), MATCHER);

    // After the dialog is shown, it will display a loading icon. Everything typed while loading is lost, when the loading is done.
    GenericTypeMatcher<AnimatedIcon> animatedIconMatcher = new GenericTypeMatcher<AnimatedIcon>(AnimatedIcon.class) {
      @Override
      protected boolean isMatching(@NotNull AnimatedIcon component) {
        return component.isRunning();
      }
    };

    waitUntilShowing(ideFrameFixture.robot(), dialog, animatedIconMatcher);
    waitUntilGone(ideFrameFixture.robot(), dialog, animatedIconMatcher, 30);

    return new SelectPathFixture(ideFrameFixture, dialog);
  }

  private SelectPathFixture(@NotNull IdeFrameFixture ideFrameFixture, @NotNull JDialog dialog) {
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
  public SelectPathFixture selectPath(@NotNull File path) {
    new JTextComponentFixture(myRobot, myRobot.finder().findByType(myDialog, JTextField.class, true)).enterText(path.getPath());
    return this;
  }

  @NotNull
  public IdeFrameFixture clickOK() {
    JButton button = GuiTests.waitUntilShowingAndEnabled(myRobot, myDialog, Matchers.byText(JButton.class, "OK"));
    // Click the "OK" button pragmatically to eliminate the flakiness due to a possible click miss.
    GuiTask.execute(() -> button.doClick());
    Wait.seconds(5).expecting("dialog to disappear").until(() -> !target().isShowing());
    return myIdeFrameFixture;
  }
}
