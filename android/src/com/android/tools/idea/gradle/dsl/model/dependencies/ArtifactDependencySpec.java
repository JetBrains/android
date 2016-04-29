/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.dependencies;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.google.common.base.Strings.emptyToNull;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class ArtifactDependencySpec {
  @NotNull public String name;

  @Nullable public String group;
  @Nullable public String version;
  @Nullable public String classifier;
  @Nullable public String extension;

  @Nullable
  public static ArtifactDependencySpec create(@NotNull String notation) {
    // Example: org.gradle.test.classifiers:service:1.0:jdk15@jar where
    //   group: org.gradle.test.classifiers
    //   name: service
    //   version: 1.0
    //   classifier: jdk15
    //   extension: jar
    List<String> segments = Splitter.on(GRADLE_PATH_SEPARATOR).trimResults().omitEmptyStrings().splitToList(notation);
    int segmentCount = segments.size();
    if (segmentCount > 0) {
      segments = Lists.newArrayList(segments);
      String lastSegment = segments.remove(segmentCount - 1);
      String extension = null;
      int indexOfAt = lastSegment.indexOf('@');
      if (indexOfAt != -1) {
        extension = lastSegment.substring(indexOfAt + 1, lastSegment.length());
        lastSegment = lastSegment.substring(0, indexOfAt);
      }
      segments.add(lastSegment);
      segmentCount = segments.size();

      String group = null;
      String name = null;
      String version = null;
      String classifier = null;

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
        if (segmentCount == 4) {
          classifier = segments.get(3);
        }
      }
      if (isNotEmpty(name)) {
        return new ArtifactDependencySpec(name, group, version, classifier, extension);
      }
    }
    return null;
  }

  public ArtifactDependencySpec(@NotNull String name, @Nullable String group, @Nullable String version) {
    this(name, group, version, null, null);
  }

  public ArtifactDependencySpec(@NotNull String name,
                                @Nullable String group,
                                @Nullable String version,
                                @Nullable String classifier,
                                @Nullable String extension) {
    this.name = name;
    this.group = emptyToNull(group);
    this.version = emptyToNull(version);
    this.classifier = emptyToNull(classifier);
    this.extension = emptyToNull(extension);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ArtifactDependencySpec that = (ArtifactDependencySpec)o;
    return Objects.equal(name, that.name) &&
           Objects.equal(group, that.group) &&
           Objects.equal(version, that.version) &&
           Objects.equal(classifier, that.classifier) &&
           Objects.equal(extension, that.extension);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name, group, version, classifier, extension);
  }

  @Override
  public String toString() {
    return compactNotation();
  }

  @NotNull
  public String compactNotation() {
    List<String> segments = Lists.newArrayList(group, name, version, classifier);
    String s = Joiner.on(':').skipNulls().join(segments);
    if (extension != null) {
      s += "@" + extension;
    }
    return s;
  }
}
