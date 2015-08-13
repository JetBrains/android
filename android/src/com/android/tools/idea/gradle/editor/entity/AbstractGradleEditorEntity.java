/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.editor.entity;

import com.android.tools.idea.gradle.editor.metadata.GradleEditorEntityMetaData;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Utility {@link GradleEditorEntity} base class which contains functionality common for all implementations.
 */
public abstract class AbstractGradleEditorEntity implements GradleEditorEntity {

  @NotNull private final Set<GradleEditorEntityMetaData> myMetaData;
  @NotNull private final GradleEditorSourceBinding myEntityLocation;
  @Nullable private final String myHelpUrl;

  protected AbstractGradleEditorEntity(@NotNull GradleEditorSourceBinding entityLocation,
                                       @NotNull Set<GradleEditorEntityMetaData> metaData,
                                       @Nullable String helpUrl) {
    myEntityLocation = entityLocation;
    myMetaData = ImmutableSet.copyOf(metaData);
    myHelpUrl = helpUrl;
  }

  @NotNull
  @Override
  public GradleEditorSourceBinding getEntityLocation() {
    return myEntityLocation;
  }

  @NotNull
  @Override
  public Set<GradleEditorEntityMetaData> getMetaData() {
    return myMetaData;
  }

  @Nullable
  @Override
  public String getHelpId() {
    return myHelpUrl;
  }

  @Override
  public void dispose() {
    Disposer.dispose(myEntityLocation);
  }
}
