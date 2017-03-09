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
package com.android.tools.idea.run.tasks;

import com.android.tools.idea.fd.actions.HotswapAction;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.GotItMessage;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.lang.reflect.Field;
import java.util.Arrays;

public class InstantRunAdvertisement {
  private final Project myProject;

  public InstantRunAdvertisement(@NotNull Project project) {
    myProject = project;
  }

  public void show() {
    ApplicationManager.getApplication().invokeLater(() -> {
      GotItMessage message =
        GotItMessage.createMessage("Instant Run: Apply Changes", AndroidBundle.message("instant.run.notification.ir.advertisement"));
      message.setShowCallout(true);
      message.setHyperlinkListener((e) -> {
        if (HyperlinkEvent.EventType.ACTIVATED == e.getEventType() && "learnmore".equals(e.getDescription())) {
          BrowserUtil.browse(InstantRunNotificationTask.INSTANT_RUN_URL, myProject);
        }
      });

      Component applyChangesButton = getApplyChangesActionComponent();
      if (applyChangesButton != null) {
        message
          .show(
            new RelativePoint(applyChangesButton, new Point(applyChangesButton.getWidth() / 2, (applyChangesButton.getHeight() / 2) + 10)),
            Balloon.Position.below);
      }
      else {
        // If the button cannot be found, then fall back to display at bottom left.
        message.show(RelativePoint.getSouthWestOf(WindowManager.getInstance().getFrame(myProject).getRootPane()), Balloon.Position.above);
      }
    });
  }

  /**
   * Attempts to find the HotSwapAction button on the toolbar using
   * reflection, the component is used to display the IR ad which is
   * positioned below the button.
   *
   * @return the Component for the Apply Changes (lighting bolt) button on the toolbar
   */
  @VisibleForTesting
  public Component getApplyChangesActionComponent() {
    JRootPane rootPane = WindowManager.getInstance().getFrame(myProject).getRootPane();
    try {
      Field f = rootPane.getClass().getDeclaredField("myToolbar");
      f.setAccessible(true);
      ActionToolbar toolbar = (ActionToolbar)f.get(rootPane);
      Component c = Arrays.stream(toolbar.getComponent().getComponents())
        .filter((component) -> component instanceof ActionButton && ((ActionButton)component).getAction() instanceof HotswapAction)
        .findFirst().get();
      if (c != null) {
        return c;
      }
    }
    catch (IllegalAccessException | NoSuchFieldException e) {
      Logger.getInstance("InstantRunAdvertisement").debug("Error finding Apply Changes Button: ", e);
    }

    return null;
  }
}