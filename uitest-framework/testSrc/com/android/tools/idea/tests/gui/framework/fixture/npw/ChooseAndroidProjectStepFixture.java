/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.npw;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowing;
import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowingAndEnabled;
import static com.android.tools.idea.tests.gui.framework.matcher.Matchers.byType;

import com.android.tools.adtui.ASGallery;
import com.android.tools.adtui.device.FormFactor;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardStepFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.ui.components.JBList;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JRootPane;
import org.fest.swing.core.matcher.JLabelMatcher;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.exception.LocationUnavailableException;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

public class ChooseAndroidProjectStepFixture<W extends AbstractWizardFixture>
  extends AbstractWizardStepFixture<ChooseAndroidProjectStepFixture, W> {

  ChooseAndroidProjectStepFixture(@NotNull W wizard, @NotNull JRootPane target) {
    super(ChooseAndroidProjectStepFixture.class, wizard, target);
  }

  public ChooseAndroidProjectStepFixture<W> chooseActivity(@NotNull String activity) {
    ASGallery<Object> list = waitUntilShowingAndEnabled(robot(), target(), byType(ASGallery.class));

    // ListFixture with ASGallery is un-reliable selecting the right activity. Select by value instead.
    GuiTask.execute(() -> {
      for (int i = 0; i < list.getModel().getSize(); i++) {
        Object value = list.getModel().getElementAt(i);
        if (activity.equals(String.valueOf(value))) {
          list.setSelectedElement(value);
          return;
        }
      }
      throw new LocationUnavailableException("Unable to select " + activity);
    });

    return this;
  }

  public List<String> listActivities() {
    ASGallery<Object> list = waitUntilShowingAndEnabled(robot(), target(), byType(ASGallery.class));
    List<String> activityNames = new ArrayList<String>();
    // ListFixture with ASGallery is un-reliable selecting the right activity. Select by value instead.
    GuiTask.execute(() -> {
      for (int i = 0; i < list.getModel().getSize(); i++) {
        Object value = list.getModel().getElementAt(i);
        activityNames.add(String.valueOf(value));
      }
    });

    return activityNames;
  }

  public ChooseAndroidProjectStepFixture<W> selectTab(@NotNull FormFactor formFactor) {
    JLabel listTitle = waitUntilShowing(robot(), target(), JLabelMatcher.withText("Templates"));
    JListFixture listFixture = new JListFixture(robot(), waitUntilShowingAndEnabled(robot(), listTitle.getParent(), byType(JBList.class)));
    listFixture.clickItem(formFactor.toString());
    return this;
  }

  public void clickCancel() {
    JButton button = GuiTests.waitUntilShowing(robot(),
                                               Matchers.byText(JButton.class, "Cancel").andIsEnabled());
    robot().click(button);
    Wait.seconds(5).expecting("dialog to disappear").until(
      () -> GuiQuery.getNonNull(() ->!target().isShowing())
    );
  }
}
