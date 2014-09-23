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
package com.android.tools.idea.avdmanager;

import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.annotation.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.AvdEditWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.AvdManagerDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import org.junit.Test;

import static org.junit.Assert.*;

public class AvdListDialogTest extends GuiTestCase {
  @Test
  @IdeGuiTest
  public void testCreateAvd() throws Exception {
    IdeFrameFixture ideFrame = openSimpleApplication();
    AvdManagerDialogFixture avdManagerDialog = ideFrame.invokeAvdManager();
    AvdEditWizardFixture avdEditWizard = avdManagerDialog.createNew();

  }
}