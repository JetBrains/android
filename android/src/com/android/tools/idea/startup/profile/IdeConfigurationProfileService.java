/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.startup.profile;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.startup.AndroidStudioInitializer.isAndroidStudio;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;

public class IdeConfigurationProfileService {
  @NonNls ExtensionPointName<IdeConfigurationProfile> EP_NAME = ExtensionPointName.create("com.android.ide.configurationProfile");

  private String myId;

  @NotNull
  public static IdeConfigurationProfileService getInstance() {
    return ServiceManager.getService(IdeConfigurationProfileService.class);
  }

  public void setConfigurationProfileId(@NotNull String id) {
    myId = id;
  }

  public void configureIde() {
    if (!isAndroidStudio()) {
      return;
    }

    if (isEmpty(myId)) {
      throw new IllegalStateException("Selected profile ID cannot be empty or null");
    }

    IdeConfigurationProfile selected = null;

    IdeConfigurationProfile[] profiles = EP_NAME.getExtensions();
    for (IdeConfigurationProfile profile : profiles) {
      if (profile.getProfileId().equals(myId)) {
        selected = profile;
        break;
      }
    }

    if (selected == null) {
      String msg = String.format("Failed to find an IdeConfigurationProfile with ID '%1$s'", myId);
      throw new IllegalStateException(msg);
    }

    selected.configureIde();
  }
}
