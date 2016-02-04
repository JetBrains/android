/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome.config;

import com.google.common.base.Objects;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * Store for persistent Android first run data.
 */
@State(name = "AndroidFirstRunPersistentData", storages = @Storage("androidStudioFirstRun.xml"))
public class AndroidFirstRunPersistentData implements PersistentStateComponent<AndroidFirstRunPersistentData.FirstRunData> {
  private static final int CURRENT_SDK_UPDATE_VERSION = 1;

  private FirstRunData myData = new FirstRunData();

  public static AndroidFirstRunPersistentData getInstance() {
    return ServiceManager.getService(AndroidFirstRunPersistentData.class);
  }

  public boolean isSdkUpToDate() {
    return myData.sdkUpdateVersion == CURRENT_SDK_UPDATE_VERSION;
  }

  public void markSdkUpToDate(@Nullable String handoffTimestamp) {
    myData.sdkUpdateVersion = CURRENT_SDK_UPDATE_VERSION;
    // Do not overwrite the timestamp - if it is here, means settings originally came from installer.
    if (handoffTimestamp != null) {
      myData.handoffTimestamp = handoffTimestamp;
    }
  }

  public boolean isSameTimestamp(@Nullable String handoffTimestamp) {
    return Objects.equal(myData.handoffTimestamp, handoffTimestamp);
  }

  @Nullable
  @Override
  public FirstRunData getState() {
    return myData;
  }

  @Override
  public void loadState(FirstRunData state) {
    myData = state;
  }

  public static class FirstRunData {
    @Tag("version") public int sdkUpdateVersion = -1;
    @Tag("handofftimestamp") public String handoffTimestamp = null;
  }
}
