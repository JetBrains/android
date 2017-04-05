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
package com.android.tools.idea.model;

import com.android.builder.model.AaptOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Creates a deep copy of {@link AaptOptions}.
 *
 * @see IdeAndroidProject
 */
public class IdeAaptOptions implements AaptOptions, Serializable {
  @Nullable private final String myIgnoreAssets;
  @Nullable private final Collection<String> myNoCompress;
  @Nullable private final List<String> myAdditionalParameters;
  private final boolean myFailOnMissingConfigEntry;

  public IdeAaptOptions(@NotNull AaptOptions options) {
    myIgnoreAssets = options.getIgnoreAssets();

    Collection<String> opNoCompress = options.getNoCompress();
    myNoCompress = opNoCompress == null ? null : new ArrayList<>(opNoCompress);

    List<String> opAdditionalParameters = options.getAdditionalParameters();
    myAdditionalParameters = opAdditionalParameters == null ? null : new ArrayList<>(opAdditionalParameters);

    myFailOnMissingConfigEntry = options.getFailOnMissingConfigEntry();
  }

  @Override
  @Nullable
  public String getIgnoreAssets() {
    return myIgnoreAssets;
  }

  @Override
  @Nullable
  public Collection<String> getNoCompress() {
    return myNoCompress;
  }

  @Override
  @Nullable
  public List<String> getAdditionalParameters() {
    return myAdditionalParameters;
  }

  @Override
  public boolean getFailOnMissingConfigEntry() {
    return myFailOnMissingConfigEntry;
  }
}
