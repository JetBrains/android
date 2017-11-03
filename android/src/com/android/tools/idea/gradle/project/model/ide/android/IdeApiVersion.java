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
package com.android.tools.idea.gradle.project.model.ide.android;

import com.android.builder.model.ApiVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Creates a deep copy of an {@link ApiVersion}.
 */
public final class IdeApiVersion extends IdeModel implements ApiVersion {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;

  @NotNull private final String myApiString;
  @Nullable private final String myCodename;
  private final int myApiLevel;
  private final int myHashCode;

  public IdeApiVersion(@NotNull ApiVersion version, @NotNull ModelCache modelCache) {
    super(version, modelCache);
    myApiString = version.getApiString();
    myCodename = version.getCodename();
    myApiLevel = version.getApiLevel();

    myHashCode = calculateHashCode();
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
    if (this == o) {
      return true;
    }
    if (!(o instanceof IdeApiVersion)) {
      return false;
    }
    IdeApiVersion version = (IdeApiVersion)o;
    return myApiLevel == version.myApiLevel &&
           Objects.equals(myApiString, version.myApiString) &&
           Objects.equals(myCodename, version.myCodename);
  }

  @Override
  public int hashCode() {
    return myHashCode;
  }

  private int calculateHashCode() {
    return Objects.hash(myApiString, myCodename, myApiLevel);
  }

  @Override
  public String toString() {
    return "IdeApiVersion{" +
           "myApiString='" + myApiString + '\'' +
           ", myCodename='" + myCodename + '\'' +
           ", myApiLevel=" + myApiLevel +
           '}';
  }
}
