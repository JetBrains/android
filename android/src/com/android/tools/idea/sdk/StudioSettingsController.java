/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.idea.sdk;

import static org.jetbrains.android.sdk.AndroidSdkUtils.isAndroidSdkManagerEnabled;

import com.android.repository.api.Channel;
import com.android.repository.api.SettingsController;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.updateSettings.impl.ChannelStatus;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.util.proxy.CommonProxy;
import java.net.Proxy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Controller class to get settings values using intellij persistent data mechanism.
 *
 * TODO: reevaluate the need for each setting after repo adoption is complete.
 */
@State(
  name = "StudioSettingsController",
  storages = @Storage(value = "remotesdk.xml", roamingType = RoamingType.DISABLED)
)
public class StudioSettingsController implements PersistentStateComponent<StudioSettingsController.PersistentState>,
                                                 SettingsController,
                                                 Disposable {

  private PersistentState myState = new PersistentState();

  @Override
  public boolean getForceHttp() {
    return myState.myForceHttp;
  }

  @Override
  public void setForceHttp(boolean forceHttp) {
    myState.myForceHttp = forceHttp;
  }

  @Override
  @Nullable
  public Channel getChannel() {
    Channel res = null;
    ChannelStatus channelStatus = UpdateSettings.getInstance().getSelectedChannelStatus();
    switch (channelStatus) {
      case RELEASE:
        res = Channel.create(0);
        break;
      case BETA:
        res = Channel.create(1);
        break;
      case MILESTONE:
        res = Channel.create(2);
        break;
      case EAP:
        res = Channel.create(3);
        break;
      default:
        // should never happen
        return null;
    }
    res.setValue(channelStatus.getDisplayName());
    return res;
  }

  @Nullable
  @Override
  public PersistentState getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull PersistentState state) {
    myState = state;
  }

  public static SettingsController getInstance() {
    return ApplicationManager.getApplication().getService(StudioSettingsController.class);
  }

  public static class PersistentState {
    public boolean myForceHttp;
  }

  @NotNull
  @Override
  public Proxy getProxy() {
    assert false : "StudioSettingsController does not contain proxy information.\n" +
                   "Use StudioDownloader or HttpsRequests to download within Studio";
    return Proxy.NO_PROXY;
  }

  @VisibleForTesting
  StudioSettingsController() {
    if (isAndroidSdkManagerEnabled()) {
      setUpAuthenticator();
    }
  }

  private void setUpAuthenticator() {
    CommonProxy.getInstance().setCustomAuth(getClass().getName(), new AndroidAuthenticator());
  }

  @Override
  public void dispose() {
    CommonProxy.getInstance().removeCustomAuth(getClass().getName());
  }
}
