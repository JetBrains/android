/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.repositories.search;

import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.stream.Collectors;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;

public class FoundArtifact {
  @NotNull private final String myRepositoryName;
  @NotNull private final String myGroupId;
  @NotNull private final String myName;

  @NotNull private final List<String> myVersions = Lists.newArrayList();

  FoundArtifact(@NotNull String repositoryName, @NotNull String groupId, @NotNull String name, @NotNull String version) {
    this(repositoryName, groupId, name);
    myVersions.add(version);
  }

  FoundArtifact(@NotNull String repositoryName, @NotNull String groupId, @NotNull String name, @NotNull List<String> versions) {
    this(repositoryName, groupId, name);
    myVersions.addAll(versions);
  }

  private FoundArtifact(@NotNull String repositoryName, @NotNull String groupId, @NotNull String name) {
    myRepositoryName = repositoryName;
    myGroupId = groupId;
    myName = name;
  }

  @NotNull
  public String getRepositoryName() {
    return myRepositoryName;
  }

  @NotNull
  public String getGroupId() {
    return myGroupId;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public List<String> getVersions() {
    return myVersions;
  }

  @TestOnly
  @NotNull
  public List<String> getCoordinates() {
    List<String> coordinates = Lists.newArrayList();

    String groupIdAndName = myGroupId + GRADLE_PATH_SEPARATOR + myName;
    coordinates.addAll(myVersions.stream().map(version -> groupIdAndName + GRADLE_PATH_SEPARATOR + version)
                                          .collect(Collectors.toList()));

    return coordinates;
  }
}
