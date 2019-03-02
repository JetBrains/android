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
package com.android.tools.idea.run.editor;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.run.TargetSelectionMode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public class DeployTargetContext implements JDOMExternalizable {
  public String TARGET_SELECTION_MODE = TargetSelectionMode.SHOW_DIALOG.name();

  private final Supplier<Boolean> mySelectDeviceSnapshotComboBoxVisible;
  private final List<DeployTargetProvider> myDeployTargetProviders; // all available deploy targets
  private final Map<String, DeployTargetState> myDeployTargetStates;

  public DeployTargetContext() {
    this(() -> StudioFlags.SELECT_DEVICE_SNAPSHOT_COMBO_BOX_VISIBLE.get(), DeployTargetProvider.getProviders());
  }

  @VisibleForTesting
  DeployTargetContext(@NotNull Supplier<Boolean> selectDeviceSnapshotComboBoxVisible,
                      @NotNull List<DeployTargetProvider> deployTargetProviders) {
    mySelectDeviceSnapshotComboBoxVisible = selectDeviceSnapshotComboBoxVisible;
    myDeployTargetProviders = deployTargetProviders;

    ImmutableMap.Builder<String, DeployTargetState> builder = ImmutableMap.builder();
    for (DeployTargetProvider provider : myDeployTargetProviders) {
      builder.put(provider.getId(), provider.createState());
    }
    myDeployTargetStates = builder.build();
  }

  @NotNull
  public List<DeployTargetProvider> getApplicableDeployTargetProviders(boolean testConfiguration) {
    boolean deviceSnapshotComboBoxVisible = mySelectDeviceSnapshotComboBoxVisible.get();

    return myDeployTargetProviders.stream()
      .filter(provider -> provider.isApplicable(testConfiguration, deviceSnapshotComboBoxVisible))
      .collect(Collectors.toList());
  }

  @NotNull
  public DeployTargetProvider getCurrentDeployTargetProvider() {
    Object mode = getTargetSelectionMode().name();

    Optional<DeployTargetProvider> optionalProvider = myDeployTargetProviders.stream()
      .filter(provider -> provider.getId().equals(mode))
      .findFirst();

    return optionalProvider.orElseThrow(AssertionError::new);
  }

  @NotNull
  public Map<String, DeployTargetState> getDeployTargetStates() {
    return myDeployTargetStates;
  }

  @NotNull
  public DeployTargetState getCurrentDeployTargetState() {
    DeployTargetProvider currentTarget = getCurrentDeployTargetProvider();
    return myDeployTargetStates.get(currentTarget.getId());
  }

  @NotNull
  public DeployTargetState getDeployTargetState(@NotNull DeployTargetProvider target) {
    return myDeployTargetStates.get(target.getId());
  }

  public void setTargetSelectionMode(@NotNull TargetSelectionMode mode) {
    TARGET_SELECTION_MODE = mode.name();
  }

  public void setTargetSelectionMode(@NotNull DeployTargetProvider target) {
    TARGET_SELECTION_MODE = target.getId();
  }

  @VisibleForTesting
  void setTargetSelectionMode(@NotNull @SuppressWarnings("SameParameterValue") String mode) {
    TARGET_SELECTION_MODE = mode;
  }

  @NotNull
  public TargetSelectionMode getTargetSelectionMode() {
    try {
      TargetSelectionMode mode = TargetSelectionMode.valueOf(TARGET_SELECTION_MODE);

      if (!mySelectDeviceSnapshotComboBoxVisible.get()) {
        switch (mode) {
          case DEVICE_AND_SNAPSHOT_COMBO_BOX:
            return TargetSelectionMode.SHOW_DIALOG;
          case SHOW_DIALOG:
          case EMULATOR:
          case USB_DEVICE:
          case FIREBASE_DEVICE_MATRIX:
          case FIREBASE_DEVICE_DEBUGGING:
            return mode;
          default:
            throw new AssertionError(mode);
        }
      }

      switch (mode) {
        case DEVICE_AND_SNAPSHOT_COMBO_BOX:
          return mode;
        case SHOW_DIALOG:
        case EMULATOR:
        case USB_DEVICE:
          return TargetSelectionMode.DEVICE_AND_SNAPSHOT_COMBO_BOX;
        case FIREBASE_DEVICE_MATRIX:
        case FIREBASE_DEVICE_DEBUGGING:
          return mode;
        default:
          throw new AssertionError(mode);
      }
    }
    catch (IllegalArgumentException exception) {
      return mySelectDeviceSnapshotComboBoxVisible.get()
             ? TargetSelectionMode.DEVICE_AND_SNAPSHOT_COMBO_BOX
             : TargetSelectionMode.SHOW_DIALOG;
    }
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);

    for (DeployTargetState state : myDeployTargetStates.values()) {
      DefaultJDOMExternalizer.readExternal(state, element);
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);

    for (DeployTargetState state : myDeployTargetStates.values()) {
      DefaultJDOMExternalizer.writeExternal(state, element);
    }
  }
}
