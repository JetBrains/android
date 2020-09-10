/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.ui.designer.overlays;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

@State(
  name = "OverlayPersistentState",
  storages = @Storage("overlayData.xml")
)
public final class OverlayPersistentStateService implements PersistentStateComponent<OverlayPersistentStateService> {
  public List<String> overlayIds = new ArrayList<>();
  public List<String> overlayNames = new ArrayList<>();
  public List<String> providers = new ArrayList<>();


  public static OverlayPersistentStateService getInstance() {
    return ServiceManager.getService(OverlayPersistentStateService.class);
  }

  public void addOverlayData(@NotNull OverlayEntry overlayEntry, @NotNull String name) {
    overlayIds.add(overlayEntry.getId());
    overlayNames.add(name);
    providers.add(overlayEntry.getOverlayProvider().getClass().getSimpleName());
  }

  public void deleteOverlayData(@NotNull OverlayEntry overlayEntry) {
    String provider = overlayEntry.getOverlayProvider().getClass().getSimpleName();
    for (int i = 0; i < getSize(); i++) {
      if(overlayIds.get(i).equals(overlayEntry.getId()) && providers.get(i).equals(provider)) {
        overlayIds.remove(i);
        overlayNames.remove(i);
        providers.remove(i);
        break;
      }
    }
  }

  public void updateOverlayName(@NotNull OverlayEntry overlayEntry, @NotNull String overlayName) {
    String provider = overlayEntry.getOverlayProvider().getClass().getSimpleName();
    for (int i = 0; i < getSize(); i++) {
      if (overlayIds.get(i).equals(overlayEntry.getId()) && providers.get(i).equals(provider)) {
        overlayNames.set(i, overlayName);
      }
    }
  }

  public int getSize() {
    return overlayIds.size();
  }

  @Override
  public OverlayPersistentStateService getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull OverlayPersistentStateService state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
