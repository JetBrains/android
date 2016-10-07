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

import com.android.ide.common.repository.GradleVersion;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.android.tools.idea.gradle.structure.model.repositories.search.AndroidSdkRepositories.ANDROID_REPOSITORY_NAME;
import static com.android.tools.idea.gradle.structure.model.repositories.search.AndroidSdkRepositories.GOOGLE_REPOSITORY_NAME;

public class FoundArtifact implements Comparable<FoundArtifact> {
  @NotNull private final String myRepositoryName;
  @NotNull private final String myGroupId;
  @NotNull private final String myName;

  @NotNull private final List<GradleVersion> myVersions = Lists.newArrayList();

  FoundArtifact(@NotNull String repositoryName, @NotNull String groupId, @NotNull String name, @NotNull GradleVersion version) {
    this(repositoryName, groupId, name);
    myVersions.add(version);
    sortVersionsFromNewestToOldest();
  }

  FoundArtifact(@NotNull String repositoryName, @NotNull String groupId, @NotNull String name, @NotNull List<GradleVersion> versions) {
    this(repositoryName, groupId, name);
    myVersions.addAll(versions);
    sortVersionsFromNewestToOldest();
  }

  private void sortVersionsFromNewestToOldest() {
    if (myVersions.size() > 1) {
      Collections.sort(myVersions, Collections.reverseOrder());
    }
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
  public List<GradleVersion> getVersions() {
    return myVersions;
  }

  @Override
  public int compareTo(FoundArtifact other) {
    int compare = compareRepositoryNames(myRepositoryName, other.myRepositoryName);
    if (compare != 0) {
      return compare;
    }

    compare = compareGroupIds(myGroupId, other.myGroupId);
    if (compare != 0) {
      return compare;
    }

    return myName.compareTo(other.myName);
  }

  private static int compareRepositoryNames(@NotNull String s1, @NotNull String s2) {
    if (s1.equals(s2)) {
      return 0;
    }
    if (s1.equals(ANDROID_REPOSITORY_NAME)) {
      return -1;
    }
    if (s1.equals(GOOGLE_REPOSITORY_NAME) && !s2.equals(ANDROID_REPOSITORY_NAME)) {
      return -1;
    }
    return s1.compareTo(s2);
  }

  private static int compareGroupIds(@NotNull String s1, @NotNull String s2) {
    if (s1.equals(s2)) {
      return 0;
    }
    // Give preference to package 'com.android' an 'com.google'
    String androidPackage = "com.android";
    if (s1.startsWith(androidPackage)) {
      if (s2.startsWith(androidPackage)) {
        return s1.compareTo(s2);
      }
      return -1;
    }
    String googlePackage = "com.google";
    if (s1.startsWith(googlePackage)) {
      if (s2.startsWith(googlePackage)) {
        return s1.compareTo(s2);
      }
      if (!s2.startsWith(androidPackage)) {
        return -1;
      }
    }

    return s1.compareTo(s2);
  }

  @TestOnly
  @NotNull
  public List<String> getCoordinates() {
    String groupIdAndName = myGroupId + GRADLE_PATH_SEPARATOR + myName;
    return myVersions.stream().map(version -> groupIdAndName + GRADLE_PATH_SEPARATOR + version).collect(Collectors.toList());
  }

  @Override
  public String toString() {
    return "{repository='" + myRepositoryName + '\'' +
           ", group='" + myGroupId + '\'' +
           ", name='" + myName + '\'' +
           ", versions=" + myVersions +
           '}';
  }
}
