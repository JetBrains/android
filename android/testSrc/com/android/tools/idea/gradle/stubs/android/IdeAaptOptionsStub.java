/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.stubs.android;

import com.android.builder.model.AaptOptions;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Stub class to use while testing {@link IdeAndroidProject}.
 */
public class IdeAaptOptionsStub implements AaptOptions{
  private final String myIgnoreAssets;
  private final Collection<String> myNoCompress;
  private final List<String> myAdditionalParameters;
  private final boolean myFailOnMissingConfigEntry;

  public IdeAaptOptionsStub(@NotNull String prefix) {
    myIgnoreAssets = prefix + "IgnoreAssets";
    myNoCompress = Arrays.asList(prefix + "NoCompress_0", prefix + "NoCompress_1");
    myAdditionalParameters = Arrays.asList(prefix + "Additional_0", prefix + "Additional_1");
    myFailOnMissingConfigEntry = true;
  }

  public IdeAaptOptionsStub() {
    this("");
  }

  @Override
  @NotNull
  public String getIgnoreAssets() {
    return myIgnoreAssets;
  }

  @Override
  @NotNull
  public Collection<String> getNoCompress() {
    return myNoCompress;
  }

  @Override
  @NotNull
  public List<String> getAdditionalParameters() {
    return myAdditionalParameters;
  }

  @Override
  public boolean getFailOnMissingConfigEntry() {
    return myFailOnMissingConfigEntry;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AaptOptions)) return false;

    AaptOptions stub = (AaptOptions)o;

    if (getFailOnMissingConfigEntry() != stub.getFailOnMissingConfigEntry()) return false;
    if (getIgnoreAssets() != null ? !getIgnoreAssets().equals(stub.getIgnoreAssets()) : stub.getIgnoreAssets() != null) return false;
    if (getNoCompress() != null ? !getNoCompress().equals(stub.getNoCompress()) : stub.getNoCompress() != null) return false;
    if (getAdditionalParameters() != null
        ? !getAdditionalParameters().equals(stub.getAdditionalParameters())
        : stub.getAdditionalParameters() != null) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = getIgnoreAssets() != null ? getIgnoreAssets().hashCode() : 0;
    result = 31 * result + (getNoCompress() != null ? getNoCompress().hashCode() : 0);
    result = 31 * result + (getFailOnMissingConfigEntry() ? 1 : 0);
    result = 31 * result + (getAdditionalParameters() != null ? getAdditionalParameters().hashCode() : 0);
    return result;
  }
}
