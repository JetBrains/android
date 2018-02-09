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
package com.android.tools.idea.connection.assistant;

import com.android.tools.idea.assistant.AssistantBundleCreator;
import com.android.tools.idea.assistant.datamodel.TutorialBundleData;
import com.android.tools.idea.flags.StudioFlags;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;

public class ConnectionAssistantBundleCreator implements AssistantBundleCreator {
  public static final String BUNDLE_ID = "DeveloperServices.ConnectionAssistant";

  private static final String TUTORIAL_CONFIG_FILENAME = "/bundle/connection/connection_assistant_bundle.xml";

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
    return getClass().getResource(TUTORIAL_CONFIG_FILENAME);
  }

  public static boolean isAssistantEnabled() {
    return StudioFlags.CONNECTION_ASSISTANT_ENABLED.get();
  }
}
