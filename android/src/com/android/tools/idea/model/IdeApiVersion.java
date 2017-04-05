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

import com.android.annotations.Nullable;
import com.android.builder.model.ApiVersion;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Objects;

/**
 * Creates a deep copy of {@link ApiVersion}.
 *
 * @see IdeAndroidProject
 */
public class IdeApiVersion implements ApiVersion, Serializable {
  @NotNull private final String myApiString;
  @Nullable private final String myCodename;
  private final int myApiLevel;

  public IdeApiVersion(@NotNull ApiVersion version) {
    myApiString = version.getApiString();
    myCodename = version.getCodename();
    myApiLevel = version.getApiLevel();
  }

  public IdeApiVersion(@NotNull String apiString, @Nullable String codename, int apiLevel) {
    myApiString = apiString;
    myCodename = codename;
    myApiLevel = apiLevel;
  }

  @Override
  @NotNull
  public String getApiString() {
    return myApiString;
  }

  @Override
  @Nullable
  public String getCodename() {
    return myCodename;
  }

  @Override
  public int getApiLevel() {
    return myApiLevel;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ApiVersion)) return false;
    ApiVersion version = (ApiVersion)o;
    return Objects.equals(getApiString(), version.getApiString()) &&
           Objects.equals(getCodename(), version.getCodename()) &&
           getApiLevel() == version.getApiLevel();
  }
}
