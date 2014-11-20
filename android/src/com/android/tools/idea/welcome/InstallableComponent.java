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
package com.android.tools.idea.welcome;

import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.tools.idea.wizard.DynamicWizardStep;
import com.android.tools.idea.wizard.ScopedStateStore;
import com.intellij.util.download.DownloadableFileDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Set;

/**
 * Component that may be installed by the first run wizard.
 */
public abstract class InstallableComponent {
  @NotNull private final String myName;
  private final long mySize;
  private final String myDescription;
  private final ScopedStateStore.Key<Boolean> myKey;

  public InstallableComponent(@NotNull String name, long size, @NotNull String description, ScopedStateStore.Key<Boolean> key) {
    myName = name;
    mySize = size;
    myDescription = description;
    myKey = key;
  }

  public boolean isOptional() {
    return true;
  }

  public long getSize() {
    return mySize;
  }

  public String getDescription() {
    return myDescription;
  }

  @Nullable
  public InstallableComponent getParent() {
    return null;
  }

  public ScopedStateStore.Key<Boolean> getKey() {
    return myKey;
  }

  @Override
  public String toString() {
    return myName;
  }

  public String getLabel() {
    if (mySize == 0) {
      return myName;
    }
    else {
      String sizeLabel = WelcomeUIUtils.getSizeLabel(mySize);
      return String.format("%s – (%s)", myName, sizeLabel);
    }
  }

  @NotNull
  public abstract PkgDesc.Builder[] getRequiredSdkPackages();

  public abstract void init(@NotNull ScopedStateStore state, @NotNull ProgressStep progressStep);

  public abstract DynamicWizardStep[] createSteps();

  public abstract void configure(@NotNull InstallContext installContext, @NotNull File sdk);
}
