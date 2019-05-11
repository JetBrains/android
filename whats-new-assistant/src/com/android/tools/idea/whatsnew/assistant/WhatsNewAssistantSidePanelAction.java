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
package com.android.tools.idea.whatsnew.assistant;

import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.assistant.AssistantBundleCreator;
import com.android.tools.idea.assistant.OpenAssistSidePanelAction;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.WhatsNewAssistantEvent;
import com.intellij.ide.actions.WhatsNewAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import org.jetbrains.annotations.NotNull;

public class WhatsNewAssistantSidePanelAction extends OpenAssistSidePanelAction {
  private static WhatsNewAction action = new WhatsNewAction();

  @Override
  public void update(@NotNull AnActionEvent e) {
    // Project being null can happen when Studio first starts and doesn't have window focus
    Presentation presentation = e.getPresentation();
    if (e.getProject() == null) {
      presentation.setEnabled(false);
    }
    else if (!presentation.isEnabled()) {
      presentation.setEnabled(true);
    }

    action.update(e);
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    WhatsNewAssistantBundleCreator bundleCreator = AssistantBundleCreator.EP_NAME.findExtension(WhatsNewAssistantBundleCreator.class);
    if (bundleCreator == null || !bundleCreator.shouldShowWhatsNew()) {
      action.actionPerformed(event);
      return;
    }

    UsageTracker.log(AndroidStudioEvent.newBuilder()
                       .setKind(AndroidStudioEvent.EventKind.WHATS_NEW_ASSISTANT_EVENT)
                       .setWhatsNewAssistantEvent(WhatsNewAssistantEvent.newBuilder().setType(
                         WhatsNewAssistantEvent.WhatsNewAssistantEventType.OPEN)));
    super.openWindow(WhatsNewAssistantBundleCreator.BUNDLE_ID, event.getProject());

    if (event.getProject() != null) {
      addToolWindowListener(event.getProject());
    }
  }

  private void addToolWindowListener(@NotNull Project project) {
    ToolWindowManagerListener listener = new ToolWindowManagerListener() {
      @Override
      public void toolWindowRegistered(@NotNull String id) {
      }

      @Override
      public void stateChanged() {
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(OpenAssistSidePanelAction.TOOL_WINDOW_TITLE);
        if (window != null && !window.isVisible()) {
          ToolWindowManagerEx.getInstanceEx(project).removeToolWindowManagerListener(this);
          UsageTracker.log(AndroidStudioEvent.newBuilder()
                                                           .setKind(AndroidStudioEvent.EventKind.WHATS_NEW_ASSISTANT_EVENT)
                                                           .setWhatsNewAssistantEvent(WhatsNewAssistantEvent.newBuilder().setType(
                                                             WhatsNewAssistantEvent.WhatsNewAssistantEventType.CLOSED)));

        }
      }
    };
    ToolWindowManagerEx.getInstanceEx(project).addToolWindowManagerListener(listener);
  }
}
