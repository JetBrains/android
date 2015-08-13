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

import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * {@link #getName() Named} list of related {@link GradleEditorEntity entities}.
 */
public class GradleEditorEntityGroup implements Disposable {

  @NotNull private final String myName;
  @NotNull private final List<GradleEditorEntity> myEntities;
  @NotNull private final List<GradleEditorEntity> myEntitiesView;

  public GradleEditorEntityGroup(@NotNull String name) {
    this(name, Collections.<GradleEditorEntity>emptyList());
  }

  public GradleEditorEntityGroup(@NotNull String name, @NotNull Iterable<GradleEditorEntity> entities) {
    myName = name;
    myEntities = Lists.newArrayList(entities);
    myEntitiesView = Collections.unmodifiableList(myEntities);
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public List<GradleEditorEntity> getEntities() {
    return myEntitiesView;
  }

  public void addEntity(@NotNull GradleEditorEntity entity) {
    myEntities.add(entity);
  }

  @Override
  public void dispose() {
    for (GradleEditorEntity entity : myEntities) {
      Disposer.dispose(entity);
    }
  }

  @Override
  public String toString() {
    return String.format("%s [%d entities]", myName, myEntities.size());
  }
}
