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

import com.android.tools.idea.flags.StudioFlags;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.startup.StartupActivity;


import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.repository.Revision;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.ui.GuiTestingService;
import com.google.common.collect.ImmutableMap;
import com.intellij.ide.TipOfTheDayManager;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;

/**
 * Show the "What's New" assistant the first time the app starts up with a new major.minor version.
 */
public class WhatsNew implements StartupActivity, DumbAware {
  @Override
  public void runActivity(@NotNull Project project) {
    if (!WhatsNewAssistantBundleCreator.isAssistantEnabled() && StudioFlags.WHATS_NEW_ASSISTANT_AUTO_SHOW.get()) {
      return;
    }

    WhatsNewService service = ServiceManager.getService(WhatsNewService.class);
    if (service == null || !IdeInfo.getInstance().isAndroidStudio()) {
      return;
    }

    WhatsNewData data = service.getState();

    if (GuiTestingService.getInstance().isGuiTestingMode() && !data.myIsUnderTest) {
      return;
    }

    Revision applicationRevision = Revision.parseRevision(ApplicationInfo.getInstance().getStrictVersion());

    if (shouldShowMessage(data, applicationRevision)) {
      // We don't want to show two popups, so disable the normal tip of the day if we're showing what's new.
      disableTipOfTheDay();
      openWhatsNewAssistant(project);
    }
  }

  private void openWhatsNewAssistant(@NotNull Project project) {
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

  private static void disableTipOfTheDay() {
    TipOfTheDayManager tips = Extensions.findExtension(StartupActivity.POST_STARTUP_ACTIVITY, TipOfTheDayManager.class);
    try {
      // This is obviously a horrible hack
      Field flag = TipOfTheDayManager.class.getDeclaredField("myVeryFirstProjectOpening");
      flag.setAccessible(true);
      flag.setBoolean(tips, false);
      flag.setAccessible(false);
    }
    catch (Exception e) {
      // nothing, just give up
    }
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

    // Not persisted. Used to indicate that we're in a UI test where this should be shown (by default it will not be shown in UI tests.
    @Transient
    public boolean myIsUnderTest = false;
  }
}
