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
package com.android.tools.idea.gradle.service.sync.change;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import org.jetbrains.annotations.NotNull;

/**
 * Common super-class for {@link ProjectStructureChange} implementations which need to
 * {@link #getData() hold at least one affected project structure entity}.
 *
 * @param <T>  type of the target project structure entity targeted by the current change
 */
public abstract class AbstractProjectStructureChange<T> implements ProjectStructureChange<T> {

  @NotNull private final DataNode<T> myData;
  @NotNull private final String      myDataDescription;

  /**
   * Constructs new <code>AbstractProjectStructureChange</code> object.
   *
   * @param data  target project structure entity data
   * @throws IllegalArgumentException   if {@link DefaultProjectStructureEntityDescriptionBuilder default entity description builder}
   *                                    is unable to build description for the given project structure entity
   */
  protected AbstractProjectStructureChange(@NotNull DataNode<T> data) throws IllegalArgumentException {
    this(data, DefaultProjectStructureEntityDescriptionBuilder.build(data));
  }

  /**
   * Constructs new <code>AbstractProjectStructureChange</code> object.
   *
   * @param data             target project structure entity data
   * @param dataDescription  target project structure entity's description
   */
  protected AbstractProjectStructureChange(@NotNull DataNode<T> data, @NotNull String dataDescription) {
    myData = data;
    myDataDescription = dataDescription;
  }

  @NotNull
  @Override
  public Key<T> getKey() {
    return myData.getKey();
  }

  @NotNull
  public DataNode<T> getData() {
    return myData;
  }

  /**
   * @return    human-readable description of the {@link #getData() project structure entity targeted by the current change}
   */
  @NotNull
  protected String getDataDescription() {
    return myDataDescription;
  }

  @Override
  public int hashCode() {
    int result = myData.getKey().hashCode();
    result = 31 * result + myData.getData().hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AbstractProjectStructureChange that = (AbstractProjectStructureChange)o;
    return myData.getKey().equals(that.myData.getKey()) && myData.getData().equals(that.myData.getData());
  }

  @Override
  public String toString() {
    return getDescription();
  }
}
