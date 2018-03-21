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
package com.android.tools.idea.gradle.structure.model;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import org.gradle.tooling.model.GradleModuleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.google.common.base.Strings.emptyToNull;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

/**
 * Similar to {@link com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpec}, with the difference that for the
 * 'Project Structure Dialog' (PSD) we don't care about a dependency's classifier and extension because the PSD matches/merges
 * dependencies obtained from different models (e.g. Gradle model, 'parsed' model and POM model,) and those dependencies can be expressed
 * differently on each model.
 */
public final class PsArtifactDependencySpec {
  @NotNull private final String myName;
  @Nullable private final String myGroup;
  @Nullable private final String myVersion;

  // Regex covering the format group:name:version:classifier@package. only name group and version are captured, and only name is required.
  // To avoid ambiguity name must not start with a digit and version must start with a digit (otherwise a:b could be parsed as group:name or
  // name:version). This requirement does not seem to be documented anywhere but is assumed elsewhere in the code and is true for all
  // examples of this format that I have seen.
  private static final Pattern ourPattern = Pattern.compile("^(?:([^:@]*):)?([^\\d+:@][^:@]*)(?::([^:@]*))?(?::[^@]*)?(?:@.*)?$");

  @Nullable
  public static PsArtifactDependencySpec create(@NotNull String notation) {
    // Example: org.gradle.test.classifiers:service:1.0 where
    //   group: org.gradle.test.classifiers
    //   name: service
    //   version: 1.0
    Matcher matcher = ourPattern.matcher(notation);
    if (!matcher.matches()) {
      return null;
    }
    return new PsArtifactDependencySpec(matcher.group(2), matcher.group(1), matcher.group(3));
  }

  @NotNull
  public static PsArtifactDependencySpec create(@NotNull ArtifactDependencyModel dependency) {
    return new PsArtifactDependencySpec(dependency.name().value(), dependency.group().value(), dependency.version().value());
  }

  @NotNull
  public static PsArtifactDependencySpec create(@NotNull GradleCoordinate coordinates) {
    return new PsArtifactDependencySpec(coordinates.getArtifactId(), coordinates.getGroupId(), coordinates.getRevision());
  }

  @NotNull
  public static PsArtifactDependencySpec create(@NotNull GradleModuleVersion moduleVersion) {
    return new PsArtifactDependencySpec(moduleVersion.getName(), moduleVersion.getGroup(), moduleVersion.getVersion());
  }

  public PsArtifactDependencySpec(@NotNull String name, @Nullable String group, @Nullable String version) {
    this.myName = name;
    this.myGroup = emptyToNull(group);
    this.myVersion = emptyToNull(version);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PsArtifactDependencySpec that = (PsArtifactDependencySpec)o;
    return Objects.equal(myName, that.myName) &&
           Objects.equal(myGroup, that.myGroup) &&
           Objects.equal(myVersion, that.myVersion);
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Nullable
  public String getGroup() {
    return myGroup;
  }

  @Nullable
  public String getVersion() {
    return myVersion;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myName, myGroup, myVersion);
  }

  @Override
  public String toString() {
    return compactNotation();
  }

  @NotNull
  public String compactNotation() {
    List<String> segments = Lists.newArrayList(myGroup, myName, myVersion);
    return Joiner.on(GRADLE_PATH_SEPARATOR).skipNulls().join(segments);
  }

  @NotNull
  public String getDisplayText() {
    boolean showGroupId = PsUISettings.getInstance().DECLARED_DEPENDENCIES_SHOW_GROUP_ID;
    StringBuilder text = new StringBuilder();
    if (showGroupId && isNotEmpty(myGroup)) {
      text.append(myGroup).append(GRADLE_PATH_SEPARATOR);
    }
    text.append(myName);
    if (isNotEmpty(myVersion)) {
      text.append(GRADLE_PATH_SEPARATOR).append(myVersion);
    }
    return text.toString();
  }
}
