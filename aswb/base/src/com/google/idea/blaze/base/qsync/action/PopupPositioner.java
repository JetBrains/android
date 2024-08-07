/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.qsync.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.ui.awt.RelativePoint;
import java.awt.event.MouseEvent;

/** Encapsulates a relative position to show a popup. */
public interface PopupPositioner {
  void showInCorrectPosition(JBPopup popup);

  /**
   * Shows the popup at the location of the mount event, or centered it the screen if the event is
   * not a mouse event (e.g. keyboard shortcut).
   *
   * <p>This is used e.g. when selecting "build dependencies" from a context menu.
   */
  static PopupPositioner showAtMousePointerOrCentered(AnActionEvent e) {
    return popup -> {
      if (e.getInputEvent() instanceof MouseEvent) {
        popup.show(
            RelativePoint.fromScreen(((MouseEvent) e.getInputEvent()).getLocationOnScreen()));
      } else {
        popup.showCenteredInCurrentWindow(e.getProject());
      }
    };
  }

  /**
   * Shows the popup underneath the clicked UI component, or centered in tge screen if the event is
   * not a mouse event.
   *
   * <p>This is used to show the popup underneath the inspection widget action.
   */
  static PopupPositioner showUnderneathClickedComponentOrCentered(AnActionEvent event) {
    return popup -> {
      if (event.getInputEvent() instanceof MouseEvent
          && event.getInputEvent().getComponent() != null) {
        // if the user clicked the action button, show underneath that
        popup.showUnderneathOf(event.getInputEvent().getComponent());
      } else {
        popup.showCenteredInCurrentWindow(event.getProject());
      }
    };
  }
}
