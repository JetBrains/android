/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.upgrade;

import com.android.ide.common.repository.AgpVersion;
import com.android.tools.idea.gradle.project.upgrade.RecommendedPluginVersionUpgradeDialog.RemindMeTomorrowAction;
import com.intellij.testFramework.PlatformTestCase;
import org.mockito.Mock;

import java.awt.event.ActionEvent;

import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link RecommendedPluginVersionUpgradeDialog}.
 */
public class RecommendedPluginVersionUpgradeDialogTest extends PlatformTestCase {
  @Mock private RecommendedUpgradeReminder myUpgradeReminder;

  private RecommendedPluginVersionUpgradeDialog myUpgradeDialog;
  private RemindMeTomorrowAction myRemindMeTomorrowAction;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    when(myUpgradeReminder.getDoNotAskForProjectPropertyString()).thenReturn("test.property");
    myUpgradeDialog = new RecommendedPluginVersionUpgradeDialog(getProject(), AgpVersion.parse("2.2.0"), AgpVersion.parse("2.3.0"),
                                                                myUpgradeReminder);
    myRemindMeTomorrowAction = myUpgradeDialog.new RemindMeTomorrowAction();
  }

  public void testRemindMeLater() {
    myRemindMeTomorrowAction.doAction(mock(ActionEvent.class));
    // Verify that the timestamp was recorded, so a day later the user will be reminded about the upgrade.
    verify(myUpgradeReminder).updateLastTimestamp();
  }

  public void testCloseDialog() {
    myUpgradeDialog.doCancelAction();
    // Verify that when user close the upgrade dialog, timestamp is not recorded.
    verify(myUpgradeReminder, never()).updateLastTimestamp();
  }
}