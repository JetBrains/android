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
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.assistant.AssistantBundleCreator;
import com.android.tools.idea.assistant.datamodel.TutorialBundleData;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.help.StudioHelpManagerImpl;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.net.URL;
import java.util.Arrays;
import java.util.Optional;

public class WhatsNewAssistantBundleCreator implements AssistantBundleCreator {
  public static final String BUNDLE_ID = "DeveloperServices.WhatsNewAssistant";
  private static AssistantBundleCreator ourTestCreator = null;

  @NotNull
  @Override
  public String getBundleId() {
    return BUNDLE_ID;
  }

  @Nullable
  @Override
  public TutorialBundleData getBundle(@NotNull Project project) {
    return null;
  }

  @Nullable
  @Override
  public URL getConfig() {
    return getClass().getResource("/" + getVersion() + ".xml");
  }

  private static String getVersion() {
    Revision revision = Revision.parseRevision(ApplicationInfo.getInstance().getStrictVersion());
    return revision.toShortString();
  }

  public static boolean isAssistantEnabled() {
    if (!StudioFlags.WHATS_NEW_ASSISTANT_ENABLED.get() || !IdeInfo.getInstance().isAndroidStudio()) return false;

    Optional<AssistantBundleCreator> creator = getCreator();
    try {
      // getConfig will return none-null value if there is a config file for this current version.
      return creator.isPresent() && creator.get().getConfig() != null;
    }
    catch (FileNotFoundException e) {
      return false;
    }
  }

  @VisibleForTesting
  public static void setTestCreator(@Nullable AssistantBundleCreator testCreator) {
    ourTestCreator = testCreator;
  }

  private static Optional<AssistantBundleCreator> getCreator() {
    if (ourTestCreator != null) return Optional.of(ourTestCreator);

    return Arrays.stream(AssistantBundleCreator.EP_NAME.getExtensions()).filter(extension -> extension.getBundleId().equals(BUNDLE_ID))
      .findFirst();
  }
}

