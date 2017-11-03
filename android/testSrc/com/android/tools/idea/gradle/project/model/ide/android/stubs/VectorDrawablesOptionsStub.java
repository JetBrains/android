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

import com.android.annotations.Nullable;
import com.android.builder.model.VectorDrawablesOptions;
import com.google.common.collect.Sets;

import java.util.Objects;
import java.util.Set;

public class VectorDrawablesOptionsStub extends BaseStub implements VectorDrawablesOptions {
  @Nullable private final Set<String> myGeneratedDensities;
  @Nullable private final Boolean myUseSupportLibrary;

  public VectorDrawablesOptionsStub() {
    this(Sets.newHashSet("generatedDensity"), true);
  }

  public VectorDrawablesOptionsStub(@Nullable Set<String> generatedDensities, @Nullable Boolean useSupportLibrary) {
    myGeneratedDensities = generatedDensities;
    myUseSupportLibrary = useSupportLibrary;
  }

  @Override
  @Nullable
  public Set<String> getGeneratedDensities() {
    return myGeneratedDensities;
  }

  @Override
  @Nullable
  public Boolean getUseSupportLibrary() {
    return myUseSupportLibrary;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof VectorDrawablesOptions)) {
      return false;
    }
    VectorDrawablesOptions options = (VectorDrawablesOptions)o;
    return Objects.equals(getGeneratedDensities(), options.getGeneratedDensities()) &&
           Objects.equals(getUseSupportLibrary(), options.getUseSupportLibrary());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getGeneratedDensities(), getUseSupportLibrary());
  }

  @Override
  public String toString() {
    return "VectorDrawablesOptionsStub{" +
           "myGeneratedDensities=" + myGeneratedDensities +
           ", myUseSupportLibrary=" + myUseSupportLibrary +
           "}";
  }
}
