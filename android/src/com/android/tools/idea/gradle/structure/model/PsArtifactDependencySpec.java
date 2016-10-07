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

import com.android.builder.model.MavenCoordinates;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.gradle.tooling.model.GradleModuleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.google.common.base.Strings.emptyToNull;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

/**
 * Similar to {@link com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpec}, with the difference that for the
 * 'Project Structure Dialog' (PSD) we don't care about a dependency's classifier and extension because the PSD matches/merges
 * dependencies obtained from different models (e.g. Gradle model, 'parsed' model and POM model,) and those dependencies can be expressed
 * differently on each model.
 */
public class PsArtifactDependencySpec {
  @NotNull public String name;

  @Nullable public String group;
  @Nullable public String version;

  @Nullable
  public static PsArtifactDependencySpec create(@NotNull String notation) {
    // Example: org.gradle.test.classifiers:service:1.0 where
    //   group: org.gradle.test.classifiers
    //   name: service
    //   version: 1.0
    List<String> segments = Splitter.on(GRADLE_PATH_SEPARATOR).trimResults().omitEmptyStrings().splitToList(notation);

    // TODO unify notation parsing with ArtifactDependencySpec parsing.
    int segmentCount = segments.size();
    if (segmentCount > 0) {
      segments = Lists.newArrayList(segments);
      String lastSegment = segments.remove(segmentCount - 1);
      int indexOfAt = lastSegment.indexOf('@');
      if (indexOfAt != -1) {
        lastSegment = lastSegment.substring(0, indexOfAt);
      }
      segments.add(lastSegment);
      segmentCount = segments.size();

      String group = null;
      String name = null;
      String version = null;

      if (segmentCount == 1) {
        name = segments.get(0);
      }
      else if (segmentCount == 2) {
        if (!lastSegment.isEmpty() && Character.isDigit(lastSegment.charAt(0))) {
          name = segments.get(0);
          version = lastSegment;
        }
        else {
          group = segments.get(0);
          name = segments.get(1);
        }
      }
      else if (segmentCount == 3 || segmentCount == 4) {
        group = segments.get(0);
        name = segments.get(1);
        version = segments.get(2);
      }
      if (isNotEmpty(name)) {
        return new PsArtifactDependencySpec(name, group, version);
      }
    }
    return null;
  }

  @NotNull
  public static PsArtifactDependencySpec create(@NotNull ArtifactDependencyModel dependency) {
    return new PsArtifactDependencySpec(dependency.name().value(), dependency.group().value(), dependency.version().value());
  }

  @NotNull
  public static PsArtifactDependencySpec create(@NotNull MavenCoordinates coordinates) {
    return new PsArtifactDependencySpec(coordinates.getArtifactId(), coordinates.getGroupId(), coordinates.getVersion());
  }

  @NotNull
  public static PsArtifactDependencySpec create(@NotNull GradleModuleVersion moduleVersion) {
    return new PsArtifactDependencySpec(moduleVersion.getName(), moduleVersion.getGroup(), moduleVersion.getVersion());
  }

  public PsArtifactDependencySpec(@NotNull String name, @Nullable String group, @Nullable String version) {
    this.name = name;
    this.group = emptyToNull(group);
    this.version = emptyToNull(version);
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
    return Objects.equal(name, that.name) &&
           Objects.equal(group, that.group) &&
           Objects.equal(version, that.version);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name, group, version);
  }

  @Override
  public String toString() {
    return compactNotation();
  }

  @NotNull
  public String compactNotation() {
    List<String> segments = Lists.newArrayList(group, name, version);
    return Joiner.on(GRADLE_PATH_SEPARATOR).skipNulls().join(segments);
  }

  @NotNull
  public String getDisplayText() {
    boolean showGroupId = PsUISettings.getInstance().DECLARED_DEPENDENCIES_SHOW_GROUP_ID;
    StringBuilder text = new StringBuilder();
    if (showGroupId && isNotEmpty(group)) {
      text.append(group).append(GRADLE_PATH_SEPARATOR);
    }
    text.append(name);
    if (isNotEmpty(version)) {
      text.append(GRADLE_PATH_SEPARATOR).append(version);
    }
    return text.toString();
  }
}
