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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.run.TargetSelectionMode;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public class DeployTargetContext implements JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance(DeployTargetContext.class);

  public String TARGET_SELECTION_MODE = TargetSelectionMode.SHOW_DIALOG.name();

  private final Supplier<Boolean> mySelectDeviceSnapshotComboBoxVisible;
  private final List<DeployTargetProvider> myDeployTargetProviders; // all available deploy targets
  private final Map<String, DeployTargetState> myDeployTargetStates;

  public DeployTargetContext() {
    this(() -> StudioFlags.SELECT_DEVICE_SNAPSHOT_COMBO_BOX_VISIBLE.get());
  }

  @VisibleForTesting
  DeployTargetContext(@NotNull Supplier<Boolean> selectDeviceSnapshotComboBoxVisible) {
    mySelectDeviceSnapshotComboBoxVisible = selectDeviceSnapshotComboBoxVisible;
    myDeployTargetProviders = DeployTargetProvider.getProviders();

    ImmutableMap.Builder<String, DeployTargetState> builder = ImmutableMap.builder();
    for (DeployTargetProvider provider : myDeployTargetProviders) {
      builder.put(provider.getId(), provider.createState());
    }
    myDeployTargetStates = builder.build();
  }

  @NotNull
  public List<DeployTargetProvider> getDeployTargetProviders() {
    return myDeployTargetProviders;
  }

  @NotNull
  public DeployTargetProvider getCurrentDeployTargetProvider() {
    if (mySelectDeviceSnapshotComboBoxVisible.get()) {
      return getDeployTargetProvider(TargetSelectionMode.DEVICE_AND_SNAPSHOT_COMBO_BOX);
    }

    if (TARGET_SELECTION_MODE.equals(TargetSelectionMode.DEVICE_AND_SNAPSHOT_COMBO_BOX.name())) {
      return getDeployTargetProvider(TargetSelectionMode.SHOW_DIALOG);
    }

    Optional<DeployTargetProvider> provider = getDeployTargetProvider(myDeployTargetProviders, TARGET_SELECTION_MODE);
    return provider.orElse(getDeployTargetProvider(TargetSelectionMode.SHOW_DIALOG));
  }

  @NotNull
  private DeployTargetProvider getDeployTargetProvider(@NotNull TargetSelectionMode mode) {
    return getDeployTargetProvider(myDeployTargetProviders, mode.name()).orElseThrow(AssertionError::new);
  }

  @NotNull
  @VisibleForTesting
  static Optional<DeployTargetProvider> getDeployTargetProvider(@NotNull Collection<DeployTargetProvider> providers, @NotNull String id) {
    return providers.stream()
                    .filter(provider -> provider.getId().equals(id))
                    .findFirst();
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

  @NotNull
  public TargetSelectionMode getTargetSelectionMode() {
    try {
      return TargetSelectionMode.valueOf(TARGET_SELECTION_MODE);
    }
    catch (IllegalArgumentException e) {
      LOG.info(e);
      return TargetSelectionMode.EMULATOR;
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
