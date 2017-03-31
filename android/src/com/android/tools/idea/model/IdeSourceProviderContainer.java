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

import com.android.builder.model.SourceProvider;
import com.android.builder.model.SourceProviderContainer;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * Creates a deep copy of {@link SourceProviderContainer}.
 *
 * @see IdeAndroidProject
 */
public class IdeSourceProviderContainer implements SourceProviderContainer, Serializable {
  @NotNull private final String myArtifactName;
  @NotNull private final SourceProvider mySourceProvider;

  public IdeSourceProviderContainer(@NotNull SourceProviderContainer container) {
    myArtifactName = container.getArtifactName();
    mySourceProvider = new IdeSourceProvider(container.getSourceProvider());
  }

  @Override
  @NotNull
  public String getArtifactName() {
    return myArtifactName;
  }

  @Override
  @NotNull
  public SourceProvider getSourceProvider() {
    return mySourceProvider;
  }
}
