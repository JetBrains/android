/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.physicaltab;

import com.android.tools.idea.devicemanager.physicaltab.PhysicalDevice.ConnectionType;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalTabPersistentStateComponent.PhysicalTabState;
import com.android.tools.idea.util.xmlb.InstantConverter;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import com.intellij.util.xmlb.annotations.XCollection.Style;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "deviceManagerPhysicalTab", storages = @Storage("deviceManagerPhysicalTab.xml"))
@Service
final class PhysicalTabPersistentStateComponent implements PersistentStateComponent<@NotNull PhysicalTabState> {
  private @NotNull PhysicalTabState myState;

  @VisibleForTesting
  PhysicalTabPersistentStateComponent() {
    myState = new PhysicalTabState();
  }

  static @NotNull PhysicalTabPersistentStateComponent getInstance() {
    return ServiceManager.getService(PhysicalTabPersistentStateComponent.class);
  }

  @NotNull Collection<@NotNull PhysicalDevice> get() {
    return myState.physicalDevices.stream()
      .map(PhysicalDeviceState::asPhysicalDevice)
      .collect(Collectors.toList());
  }

  void set(@NotNull Collection<@NotNull PhysicalDevice> devices) {
    myState.physicalDevices = devices.stream()
      .map(PhysicalDeviceState::new)
      .collect(Collectors.toList());
  }

  @Override
  public @NotNull PhysicalTabState getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull PhysicalTabState state) {
    myState = state;
  }

  static final class PhysicalTabState {
    @XCollection(style = Style.v2)
    private @NotNull Collection<@NotNull PhysicalDeviceState> physicalDevices = Collections.emptyList();

    @Override
    public int hashCode() {
      return physicalDevices.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object object) {
      return object instanceof PhysicalTabState && physicalDevices.equals(((PhysicalTabState)object).physicalDevices);
    }
  }

  @Tag("PhysicalDevice")
  private static final class PhysicalDeviceState {
    @OptionTag(tag = "serialNumber", nameAttribute = "")
    private @Nullable String serialNumber;

    @OptionTag(tag = "lastOnlineTime", nameAttribute = "", converter = InstantConverter.class)
    private @Nullable Instant lastOnlineTime;

    @OptionTag(tag = "name", nameAttribute = "")
    private @Nullable String name;

    @OptionTag(tag = "target", nameAttribute = "")
    private @Nullable String target;

    @OptionTag(tag = "api", nameAttribute = "")
    private @Nullable String api;

    @OptionTag(tag = "connectionType", nameAttribute = "")
    private @Nullable ConnectionType connectionType;

    @SuppressWarnings("unused")
    private PhysicalDeviceState() {
    }

    private PhysicalDeviceState(@NotNull PhysicalDevice device) {
      serialNumber = device.getSerialNumber();
      lastOnlineTime = device.getLastOnlineTime();
      name = device.getName();
      target = device.getTarget();
      api = device.getApi();
      connectionType = device.getConnectionType();
    }

    private @NotNull PhysicalDevice asPhysicalDevice() {
      assert serialNumber != null;
      assert name != null;
      assert target != null;
      assert api != null;
      assert connectionType != null;

      return new PhysicalDevice.Builder()
        .setSerialNumber(serialNumber)
        .setLastOnlineTime(lastOnlineTime)
        .setName(name)
        .setTarget(target)
        .setApi(api)
        .setConnectionType(connectionType)
        .build();
    }

    @Override
    public int hashCode() {
      int hashCode = Objects.hashCode(serialNumber);

      hashCode = 31 * hashCode + Objects.hashCode(lastOnlineTime);
      hashCode = 31 * hashCode + Objects.hashCode(name);
      hashCode = 31 * hashCode + Objects.hashCode(target);
      hashCode = 31 * hashCode + Objects.hashCode(api);
      hashCode = 31 * hashCode + Objects.hashCode(connectionType);

      return hashCode;
    }

    @Override
    public boolean equals(@Nullable Object object) {
      if (!(object instanceof PhysicalDeviceState)) {
        return false;
      }

      PhysicalDeviceState device = (PhysicalDeviceState)object;

      return Objects.equals(serialNumber, device.serialNumber) &&
             Objects.equals(lastOnlineTime, device.lastOnlineTime) &&
             Objects.equals(name, device.name) &&
             Objects.equals(target, device.target) &&
             Objects.equals(api, device.api) &&
             Objects.equals(connectionType, device.connectionType);
    }
  }
}
