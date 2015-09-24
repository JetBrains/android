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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class NewExternalDependency {
  @NotNull public String configurationName;
  @NotNull public String name;
  @Nullable public String group;
  @Nullable public String version;
  @Nullable public String classifier;
  @Nullable public String extension;

  public NewExternalDependency(@NotNull String configurationName, @NotNull String name, @Nullable String group, @Nullable String version) {
    this.configurationName = configurationName;
    this.name = name;
    this.group = group;
    this.version = version;
  }

  @NotNull
  public String getCompactNotation() {
    String notation = Joiner.on(GRADLE_PATH_SEPARATOR).join(group, name, version);
    if (isNotEmpty(classifier)) {
      notation = notation + GRADLE_PATH_SEPARATOR + classifier;
    }
    if (isNotEmpty(extension)) {
      notation = notation + '@' + extension;
    }
    return notation;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    NewExternalDependency that = (NewExternalDependency)o;
    return Objects.equal(configurationName, that.configurationName) &&
           Objects.equal(group, that.group) &&
           Objects.equal(name, that.name) &&
           Objects.equal(version, that.version) &&
           Objects.equal(classifier, that.classifier) &&
           Objects.equal(extension, that.extension);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(configurationName, group, name, version, classifier, extension);
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this).add("configurationName", configurationName)
                                       .add("group", group)
                                       .add("name", name)
                                       .add("version", version)
                                       .add("classifier", classifier)
                                       .add("extension", extension)
                                       .toString();
  }
}
