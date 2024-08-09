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

import com.android.repository.Revision;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.assistant.AssistantBundleCreator;
import com.android.tools.idea.ui.GuiTestingService;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.util.xmlb.annotations.Tag;
import org.apache.http.concurrent.FutureCallback;
import org.jetbrains.annotations.NotNull;

/**
 * Show the "What's New" assistant the first time the app starts up with a new major.minor version.
 */
public class WhatsNewStartupActivity implements StartupActivity.DumbAware {
  @Override
  public void runActivity(@NotNull Project project) {
    WhatsNewBundleCreator bundleCreator = AssistantBundleCreator.EP_NAME.findExtension(WhatsNewBundleCreator.class);
    if (bundleCreator == null || bundleCreator.shouldNotShowWhatsNew()) {
      return;
    }

    if (!IdeInfo.getInstance().isAndroidStudio()) {
      return;
    }

    WhatsNewService service = ApplicationManager.getApplication().getService(WhatsNewService.class);
    if (service == null) {
      return;
    }

    WhatsNewData data = service.getState();

    if (GuiTestingService.getInstance().isGuiTestingMode() || ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    Revision applicationRevision = Revision.safeParseRevision(ApplicationInfo.getInstance().getStrictVersion());

    // If the Android Studio version is new, then always show on startup
    if (isNewStudioVersion(data, applicationRevision)) {
      openWhatsNewAssistant(project);
    }
    else {
      // But also show if the config version is newer than current, even if AS version is not higher
      // This needs to be done asynchronously because the WNABundleCreator needs to download config to check version
      WhatsNewCheckVersionTask task =
        new WhatsNewCheckVersionTask(project, new VersionCheckCallback(project));
      task.queue();
    }
  }

  private static void openWhatsNewAssistant(@NotNull Project project) {
    ((WhatsNewSidePanelAction)ActionManager.getInstance().getAction("WhatsNewAction"))
      .openWhatsNewSidePanel(project, true);
  }

  @VisibleForTesting
  boolean isNewStudioVersion(@NotNull WhatsNewData data, @NotNull Revision applicationRevision) {
    String seenRevisionStr = data.myRevision;
    Revision seenRevision = null;
    if (seenRevisionStr != null) {
      seenRevision = Revision.safeParseRevision(seenRevisionStr);
    }

    if (seenRevision == null
        || seenRevision.equals(Revision.NOT_SPECIFIED)
        || applicationRevision.equals(Revision.NOT_SPECIFIED)
        || applicationRevision.compareTo(seenRevision, Revision.PreviewComparison.ASCENDING) > 0) {
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
    public void loadState(@NotNull WhatsNewData state) {
      myData = state;
    }
  }

  @VisibleForTesting
  public static class WhatsNewData {
    @Tag("shownVersion") public String myRevision;
  }

  /**
   * Callback for when WhatsNewBundleCreator has determined whether
   * there has been an update to the config. If yes, WNA is automatically opened.
   */
  private static class VersionCheckCallback implements FutureCallback<Boolean> {
    private final Project myProject;

    private VersionCheckCallback(Project project) {
      super();
      myProject = project;
    }

    @Override
    public void cancelled() {
      // Don't auto-show
    }

    @Override
    public void completed(Boolean result) {
      // Auto-show What's New Assistant
      if (result) {
        openWhatsNewAssistant(myProject);
      }
    }

    @Override
    public void failed(Exception ex) {
      // Don't auto-show
      Logger.getInstance(WhatsNewStartupActivity.class).error(ex);
    }
  }
}
