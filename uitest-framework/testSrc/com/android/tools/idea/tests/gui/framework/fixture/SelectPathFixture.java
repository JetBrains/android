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

    GenericTypeMatcher<JTree> treeLoadedMatcher = new GenericTypeMatcher<JTree>(JTree.class) {
      @Override
      protected boolean isMatching(@NotNull JTree tree) {
        return tree.getRowCount() > 0;
      }
    };
    waitUntilShowing(ideFrameFixture.robot(), dialog, treeLoadedMatcher);

    // After the tree is shown, it will display a loading icon. Everything typed while loading is lost, when the loading is done.
    GenericTypeMatcher<AnimatedIcon> animatedIconMatcher = new GenericTypeMatcher<AnimatedIcon>(AnimatedIcon.class) {
      @Override
      protected boolean isMatching(@NotNull AnimatedIcon component) {
        return component.isRunning();
      }
    };
    waitUntilGone(ideFrameFixture.robot(), dialog, animatedIconMatcher, 60);

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
  public IdeFrameFixture clickOK() {
    JButton button = GuiTests.waitUntilShowingAndEnabled(myRobot, myDialog, Matchers.byText(JButton.class, "OK"));
    // Click the "OK" button programmatically to eliminate the flakiness due to a possible click miss.
    GuiTask.execute(button::doClick);
    Wait.seconds(5).expecting("dialog to disappear").until(() -> !target().isShowing());
    return myIdeFrameFixture;
  }
}
