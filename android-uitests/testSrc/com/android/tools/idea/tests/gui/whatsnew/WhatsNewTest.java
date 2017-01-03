/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.whatsnew;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.welcome.whatsnew.WhatsNew;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.components.ServiceManager;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.fixture.JLabelFixture;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.swing.*;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * UI tests for {@link WhatsNew}
 */
@RunWith(GuiTestRunner.class)
public class WhatsNewTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Ignore("What's New asset doesn't exist yet")
  @Test
  public void whatsNewShown() throws Exception {
    WhatsNew.WhatsNewService service = ServiceManager.getService(WhatsNew.WhatsNewService.class);
    WhatsNew.WhatsNewData data = service.getState();
    data.myIsUnderTest = true;
    data.myRevision = null;

    // No need to wait for sync to finish
    DialogFixture dialog = guiTest.importProject("SimpleApplication").findDialog("What's New");
    JLabelFixture label = dialog.label(Matchers.byIcon(JLabel.class, null).negate(JLabel.class).andIsShowing());
    assertNotNull(label.target().getAccessibleContext().getAccessibleDescription());
    dialog.close();
  }

  @Test
  public void whatsNewNotShown() throws Exception {
    WhatsNew.WhatsNewService service = ServiceManager.getService(WhatsNew.WhatsNewService.class);
    WhatsNew.WhatsNewData data = service.getState();
    data.myIsUnderTest = true;
    data.myRevision = ApplicationInfo.getInstance().getStrictVersion();

    // Wait for sync to finish to be sure the dialog has a chance to show up
    guiTest.importSimpleApplication();
    assertTrue(guiTest.robot().finder().findAll(Matchers.byTitle(JDialog.class, "What's New")).isEmpty());
  }
}
