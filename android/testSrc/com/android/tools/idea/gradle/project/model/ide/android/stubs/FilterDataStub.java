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
package com.android.tools.idea.gradle.project.model.ide.android.stubs;

import com.android.build.FilterData;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class FilterDataStub extends BaseStub implements FilterData {
  @NotNull private final String myIdentifier;
  @NotNull private final String myFilterType;

  public FilterDataStub() {
    this("identifier", "filterType");
  }

  public FilterDataStub(@NotNull String identifier, @NotNull String filterType) {
    myIdentifier = identifier;
    myFilterType = filterType;
  }

  @Override
  @NotNull
  public String getIdentifier() {
    return myIdentifier;
  }

  @Override
  @NotNull
  public String getFilterType() {
    return myFilterType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof FilterData)) {
      return false;
    }
    FilterData filterData = (FilterData)o;
    return Objects.equals(getIdentifier(), filterData.getIdentifier()) &&
           Objects.equals(getFilterType(), filterData.getFilterType());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getIdentifier(), getFilterType());
  }

  @Override
  public String toString() {
    return "FilterDataStub{" +
           "myIdentifier='" + myIdentifier + '\'' +
           ", myFilterType='" + myFilterType + '\'' +
           "}";
  }
}
