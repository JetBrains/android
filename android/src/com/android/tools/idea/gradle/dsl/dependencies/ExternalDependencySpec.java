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
package com.android.tools.idea.gradle.dsl.dependencies;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.google.common.base.Strings.emptyToNull;

public class ExternalDependencySpec {
  @NotNull public String name;

  @Nullable public String group;
  @Nullable public String version;
  @Nullable public String classifier;
  @Nullable public String extension;

  public ExternalDependencySpec(@NotNull String name, @Nullable String group, @Nullable String version) {
    this(name, group, version, null, null);
  }

  public ExternalDependencySpec(@NotNull String name,
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
    ExternalDependencySpec that = (ExternalDependencySpec)o;
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
