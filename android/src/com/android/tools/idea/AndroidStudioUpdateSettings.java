/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea;

import com.google.common.collect.Lists;
import com.intellij.openapi.updateSettings.UpdateStrategyCustomization;
import com.intellij.openapi.updateSettings.impl.ChannelStatus;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class AndroidStudioUpdateSettings extends UpdateSettings {
  @Override
  public @NotNull List<ChannelStatus> getActiveChannels() {
    //TODO b/217844216
    //workaround for UpdateSettingsConfigurable always using EAP channel when channelSelectionLockedMessage!=null
    UpdateStrategyCustomization tweaker = UpdateStrategyCustomization.getInstance();
    ChannelStatus currentChannel = tweaker.changeDefaultChannel(ChannelStatus.RELEASE);
    return Lists.newArrayList(currentChannel);
  }
}
