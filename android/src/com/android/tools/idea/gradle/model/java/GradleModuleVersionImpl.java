/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.model.java;

import com.android.java.model.LibraryVersion;
import com.intellij.serialization.PropertyMapping;
import java.io.Serializable;
import java.util.Objects;
import org.gradle.tooling.model.GradleModuleVersion;
import org.jetbrains.annotations.NotNull;

public class GradleModuleVersionImpl implements GradleModuleVersion, Serializable {
  @NotNull private final String myGroup;
  @NotNull private final String myName;
  @NotNull private final String myVersion;

  @PropertyMapping({
    "myGroup",
    "myName",
    "myVersion"
  })
  public GradleModuleVersionImpl(@NotNull String group, @NotNull String name, @NotNull String version) {
    myGroup = group;
    myName = name;
    myVersion = version;
  }

  public GradleModuleVersionImpl(@NotNull LibraryVersion version) {
    myGroup = version.getGroup();
    myName = version.getName();
    myVersion = version.getVersion();
  }

  public GradleModuleVersionImpl(@NotNull GradleModuleVersion version) {
    myGroup = version.getGroup();
    myName = version.getName();
    myVersion = version.getVersion();
  }

  @Override
  @NotNull
  public String getGroup() {
    return myGroup;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public String getVersion() {
    return myVersion;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GradleModuleVersionImpl version = (GradleModuleVersionImpl)o;
    return myGroup.equals(version.myGroup) &&
           myName.equals(version.myName) &&
           myVersion.equals(version.myVersion);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myGroup, myName, myVersion);
  }
}
