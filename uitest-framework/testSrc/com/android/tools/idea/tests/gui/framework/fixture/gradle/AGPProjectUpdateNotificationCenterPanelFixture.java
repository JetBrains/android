/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.gradle;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.google.common.collect.Lists;
import java.util.List;
import javax.swing.JEditorPane;
import org.jetbrains.annotations.NotNull;

public class AGPProjectUpdateNotificationCenterPanelFixture {
  private final IdeFrameFixture myIdeFrameFixture;
  private final JEditorPane myEditorPane;
  static final String NOTIFICATIONTEXT= "Android Gradle Plugin can be";

  public static AGPProjectUpdateNotificationCenterPanelFixture find(IdeFrameFixture ideFrame) {
    List<JEditorPane> editorPanes = Lists.newArrayList(ideFrame.robot().finder().findAll(ideFrame.target(), Matchers.byType(JEditorPane.class)));
    if (editorPanes.size() > 0) {
      for (JEditorPane editorPane : editorPanes) {
        if (editorPane.getText().toLowerCase().contains(NOTIFICATIONTEXT.toLowerCase())) {
          return new AGPProjectUpdateNotificationCenterPanelFixture(ideFrame, editorPane);
        }
      }
    }
    throw new AssertionError("Unable to find the Project Update Notification center panel with text " + NOTIFICATIONTEXT);
  }

  private AGPProjectUpdateNotificationCenterPanelFixture (IdeFrameFixture ideFrame, JEditorPane editorPane) {
    myIdeFrameFixture = ideFrame;
    myEditorPane = editorPane;
  }

  public boolean isShowing() {
    return myEditorPane.isShowing();
  }

  public void clickUpgraded() {
    myIdeFrameFixture.robot().click(myEditorPane);
  }
}


