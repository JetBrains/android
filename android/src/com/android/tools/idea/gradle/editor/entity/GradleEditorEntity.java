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
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Defines contract for the main model entity processed by enhanced gradle editor.
 */
public interface GradleEditorEntity extends Disposable {

  /**
   * @return    user-friendly textual representation of the current entity
   */
  @NotNull
  String getName();

  /**
   * @return    meta-data configured for the current entity (empty set in case of meta-data absence)
   */
  @NotNull
  Set<GradleEditorEntityMetaData> getMetaData();

  /**
   * @return    unique id which defines help topic for the gradle configuration element represented by the current entity (if any)
   *            Basic idea is to allow quick documentation lookup for the target entity at the UI level
   */
  @Nullable
  String getHelpId();

  /**
   * @return    an object which encapsulates entity's location at the source file (useful e.g. for removing the entity)
   */
  GradleEditorSourceBinding getEntityLocation();
}
