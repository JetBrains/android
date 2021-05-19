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

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilFound;
import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowing;
import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowingAndEnabled;
import static com.android.tools.idea.tests.gui.framework.matcher.Matchers.byType;

import com.android.tools.adtui.ASGallery;
import com.android.tools.adtui.device.FormFactor;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardStepFixture;
import com.intellij.ui.components.JBList;
import javax.swing.JLabel;
import javax.swing.JRootPane;
import javax.swing.JTabbedPane;
import org.fest.swing.core.matcher.JLabelMatcher;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.fixture.JTabbedPaneFixture;
import org.jetbrains.annotations.NotNull;

public class ChooseAndroidProjectStepFixture<W extends AbstractWizardFixture>
  extends AbstractWizardStepFixture<ChooseAndroidProjectStepFixture, W> {

  ChooseAndroidProjectStepFixture(@NotNull W wizard, @NotNull JRootPane target) {
    super(ChooseAndroidProjectStepFixture.class, wizard, target);
  }

  public ChooseAndroidProjectStepFixture<W> chooseActivity(@NotNull String activity) {
    JListFixture listFixture = new JListFixture(robot(), waitUntilShowingAndEnabled(robot(), target(), byType(ASGallery.class)));
    listFixture.replaceCellReader((jList, index) -> String.valueOf(jList.getModel().getElementAt(index)));
    listFixture.clickItem(activity);
    return this;
  }

  public ChooseAndroidProjectStepFixture<W> selectTab(@NotNull FormFactor formFactor) {
    if (StudioFlags.NPW_NEW_MODULE_WITH_SIDE_BAR.get()) {
      JLabel listTitle = waitUntilShowing(robot(), target(), JLabelMatcher.withText("Templates"));
      JListFixture listFixture = new JListFixture(robot(), waitUntilShowingAndEnabled(robot(), listTitle.getParent(), byType(JBList.class)));
      listFixture.clickItem(formFactor.toString());
      return this;
    }

    new JTabbedPaneFixture(robot(), waitUntilFound(robot(), target(), byType(JTabbedPane.class))).selectTab(formFactor.toString());
    return this;
  }
}
