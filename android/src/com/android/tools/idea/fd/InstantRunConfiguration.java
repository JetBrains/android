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
package com.android.tools.idea.fd;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.Nullable;

@State(
  name = "InstantRunConfiguration",
  storages = @Storage(value = "instant-run.xml", roamingType = RoamingType.DISABLED)
)
public class InstantRunConfiguration implements PersistentStateComponent<InstantRunConfiguration> {
  public boolean INSTANT_RUN = true;
  public boolean RESTART_ACTIVITY = true;
  public boolean SHOW_TOAST = true;
  public boolean SHOW_IR_STATUS_NOTIFICATIONS = true;
  public boolean COLD_SWAP = true;
  public String COLD_SWAP_MODE;

  public static InstantRunConfiguration getInstance() {
    return ServiceManager.getService(InstantRunConfiguration.class);
  }

  public static InstantRunConfiguration getInstance(Project project) {
    return ServiceManager.getService(project, InstantRunConfiguration.class);
  }

  @Nullable
  @Override
  public InstantRunConfiguration getState() {
    return this;
  }

  @Override
  public void loadState(InstantRunConfiguration state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
