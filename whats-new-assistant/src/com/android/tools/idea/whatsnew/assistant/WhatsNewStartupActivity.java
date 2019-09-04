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

import com.android.annotations.VisibleForTesting;
import com.android.repository.Revision;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.ui.GuiTestingService;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.WhatsNewAssistantEvent;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Show the "What's New" assistant the first time the app starts up with a new major.minor version.
 */
public class WhatsNewStartupActivity implements StartupActivity.Backgroundable {
  @Override
  public void runActivity(@NotNull Project project) {
    if (!(WhatsNewAssistantBundleCreator.shouldShowReleaseNotes() && StudioFlags.WHATS_NEW_ASSISTANT_AUTO_SHOW.get())) {
      return;
    }

    if (!IdeInfo.getInstance().isAndroidStudio()) {
      return;
    }

    WhatsNewService service = ServiceManager.getService(WhatsNewService.class);
    if (service == null) {
      return;
    }

    WhatsNewData data = service.getState();

    if (GuiTestingService.getInstance().isGuiTestingMode() || ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    Revision applicationRevision = Revision.parseRevision(ApplicationInfo.getInstance().getStrictVersion());

    if (shouldShowMessage(data, applicationRevision)) {
      // We don't want to show two popups, so disable the normal tip of the day if we're showing what's new.
      openWhatsNewAssistant(project);
    }
  }

  private void openWhatsNewAssistant(@NotNull Project project) {
    UsageTracker.log(AndroidStudioEvent.newBuilder()
                                                     .setKind(AndroidStudioEvent.EventKind.WHATS_NEW_ASSISTANT_EVENT)
                                                     .setWhatsNewAssistantEvent(WhatsNewAssistantEvent.newBuilder().setType(
                                                       WhatsNewAssistantEvent.WhatsNewAssistantEventType.AUTO_OPEN)));
    new WhatsNewAssistantSidePanelAction()
      .actionPerformed(AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, new DataContext() {
        @Nullable
        @Override
        public Object getData(String dataId) {
          if (dataId.equalsIgnoreCase(CommonDataKeys.PROJECT.getName())) {
            return project;
          }
          return null;
        }
      }));
  }

  @VisibleForTesting
  boolean shouldShowMessage(@NotNull WhatsNewData data, @NotNull Revision applicationRevision) {
    String seenRevisionStr = data.myRevision;
    Revision seenRevision = null;
    if (seenRevisionStr != null) {
      try {
        seenRevision = Revision.parseRevision(seenRevisionStr);
      }
      catch (NumberFormatException exception) {
        // Bad previous revision, treat as null.
      }
    }

    if (seenRevision == null || applicationRevision.compareTo(seenRevision, Revision.PreviewComparison.ASCENDING) > 0) {
      data.myRevision = applicationRevision.toString();
      return true;
    }
    return false;
  }

  @State(name = "whatsNew", storages = @Storage("androidStudioFirstRun.xml"))
  public static class WhatsNewService implements PersistentStateComponent<WhatsNewData> {
    private WhatsNewData myData;

    @NotNull
    @Override
    public WhatsNewData getState() {
      if (myData == null) {
        myData = new WhatsNewData();
      }
      return myData;
    }

    @Override
    public void loadState(WhatsNewData state) {
      myData = state;
    }
  }

  @VisibleForTesting
  public static class WhatsNewData {
    @Tag("shownVersion") public String myRevision;
  }
}
