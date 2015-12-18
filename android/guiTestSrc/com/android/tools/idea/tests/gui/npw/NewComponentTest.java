/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.npw;

import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewXmlValueWizardFixture;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.fixture.JLabelFixture;
import org.fest.swing.timing.Condition;
import org.junit.Test;

import java.io.IOException;

import static com.android.tools.idea.tests.gui.framework.GuiTests.SHORT_TIMEOUT;
import static org.fest.swing.timing.Pause.pause;

public class NewComponentTest extends GuiTestCase {
  @Test @IdeGuiTest
  public void testNewValueWizard() throws IOException {
    myProjectFrame = importSimpleApplication();

    myProjectFrame.getProjectView().selectAndroidPane();
    myProjectFrame.invokeMenuPath("File", "New", "XML", "Values XML File");

    final NewXmlValueWizardFixture wizardFixture = NewXmlValueWizardFixture.find(myRobot);
    final JButtonFixture finishFixture = wizardFixture.findWizardButton("Finish");
    finishFixture.requireEnabled();
    wizardFixture.getFileNameField().enterText("strings");
    final JLabelFixture errorLabel = wizardFixture.findLabel("Values File Name must be unique");

    pause(new Condition("Waiting for Finish to be disabled") {
      @Override
      public boolean test() {
        return !finishFixture.isEnabled();
      }
    }, SHORT_TIMEOUT);
    wizardFixture.getFileNameField().enterText("2");
    pause(new Condition("Waiting for Finish to be enabled") {
      @Override
      public boolean test() {
        return finishFixture.isEnabled();
      }
    }, SHORT_TIMEOUT);

    // Now test an invalid file name. Currently we have "strings2" so add an space to make it invalid.
    wizardFixture.getFileNameField().enterText(" ");
    pause(new Condition("Waiting for Finish to be disabled") {
      @Override
      public boolean test() {
        return !finishFixture.isEnabled();
      }
    }, SHORT_TIMEOUT);
    errorLabel.requireText("<html>Values File Name is not a valid resource name. ' ' is not a valid resource name character</html>");
  }
}
