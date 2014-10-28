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
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

public class EntityModifiedImpl<T> extends AbstractProjectStructureChange<T> implements EntityModified<T> {

  @NotNull private final DataNode<T> myPreviousState;
  @NotNull private final Object myQualifier;
  @NotNull private final String myValueBeforeDescription;
  @NotNull private final String myValueAfterDescription;

  public EntityModifiedImpl(@NotNull DataNode<T> data,
                            @NotNull DataNode<T> previousState,
                            @NotNull Object qualifier,
                            @NotNull String valueBeforeDescription,
                            @NotNull String valueAfterDescription) throws IllegalArgumentException
  {
    super(data);
    myPreviousState = previousState;
    myQualifier = qualifier;
    myValueBeforeDescription = valueBeforeDescription;
    myValueAfterDescription = valueAfterDescription;
  }

  public EntityModifiedImpl(@NotNull DataNode<T> data,
                            @NotNull String dataDescription,
                            @NotNull DataNode<T> previousState,
                            @NotNull Object qualifier,
                            @NotNull String valueBeforeDescription,
                            @NotNull String valueAfterDescription)
  {
    super(data, dataDescription);
    myPreviousState = previousState;
    myQualifier = qualifier;
    myValueBeforeDescription = valueBeforeDescription;
    myValueAfterDescription = valueAfterDescription;
  }

  @NotNull
  @Override
  public DataNode<T> getPreviousState() {
    return myPreviousState;
  }

  @NotNull
  @Override
  public DataNode<T> getCurrentState() {
    return getData();
  }

  @NotNull
  @Override
  public Object getQualifier() {
    return myQualifier;
  }

  @NotNull
  @Override
  public String getDescription() {
    return AndroidBundle.message("android.gradle.project.change.modified", getDataDescription(), myQualifier, myValueBeforeDescription, myValueAfterDescription);
  }

  @Override
  public void accept(@NotNull ProjectStructureChangeVisitor visitor) {
    visitor.visit(this);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myPreviousState.getData().hashCode();
    result = 31 * result + myQualifier.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    EntityModifiedImpl that = (EntityModifiedImpl)o;
    return myQualifier.equals(that.myQualifier) && myPreviousState.getData().equals(that.myPreviousState.getData());
  }
}
