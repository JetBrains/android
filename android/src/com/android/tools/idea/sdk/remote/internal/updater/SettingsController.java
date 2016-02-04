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

package com.android.tools.idea.sdk.remote.internal.updater;

import com.intellij.openapi.components.*;
import org.jetbrains.annotations.Nullable;

/**
 * Controller class to get settings values using intellij persistent data mechanism.
 * Compare to {@link com.android.sdklib.internal.repository.updater.SettingsController}
 * which uses a file maintained separately.
 */
@State(name = "SettingsController", storages = @Storage(value = "remotesdk.xml", roamingType = RoamingType.DISABLED))
public class SettingsController implements PersistentStateComponent<SettingsController.PersistentState> {

  private PersistentState myState = new PersistentState();

  public boolean getForceHttp() {
    return myState.myForceHttp;
  }

  public boolean getAskBeforeAdbRestart() {
    return myState.myAskBeforeAdbRestart;
  }

  public boolean getUseDownloadCache() {
    return myState.myAskBeforeAdbRestart;
  }

  public void setForceHttp(boolean forceHttp) {
    myState.myForceHttp = forceHttp;
  }

  @Nullable
  @Override
  public PersistentState getState() {
    return myState;
  }

  @Override
  public void loadState(PersistentState state) {
    myState = state;
  }

  public static SettingsController getInstance() {
    return ServiceManager.getService(SettingsController.class);
  }

  public static class PersistentState {
    public boolean myForceHttp;
    public boolean myAskBeforeAdbRestart;
    public boolean myUseDownloadCache;
  }

  private SettingsController() {}
}
