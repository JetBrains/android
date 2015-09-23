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
package com.android.tools.idea.gradle.dsl.dependencies.external;

import com.android.tools.idea.gradle.dsl.dependencies.Dependencies;
import com.android.tools.idea.gradle.dsl.dependencies.Dependency;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import java.util.List;

import static com.google.common.base.Strings.emptyToNull;

/**
 * A Gradle external dependency. There are two notations supported for declaring a dependency on an external module. One is a string
 * notation formatted this way:
 * <pre>
 * configurationName "group:name:version:classifier@extension"
 * </pre>
 * The other is a map notation:
 * <pre>
 * configurationName group: group:, name: name, version: version, classifier: classifier, ext: extension
 * </pre>
 * For more details, visit:
 * <ol>
 * <li><a href="https://docs.gradle.org/2.4/userguide/dependency_management.html">Gradle Dependency Management</a></li>
 * <li><a href="https://docs.gradle.org/current/dsl/org.gradle.api.artifacts.dsl.DependencyHandler.html">Gradle
 * DependencyHandler</a></li>
 * </ol>
 */
public abstract class ExternalDependency extends Dependency {
  @NotNull protected final Spec mySpec;

  @Nullable private String myNewVersion;

  @Nullable
  public static ExternalDependency withCompactNotation(@NotNull Dependencies parent,
                                                       @NotNull String configurationName,
                                                       @NotNull GrLiteral valueLiteral) {
    return CompactNotation.parse(parent, configurationName, valueLiteral);
  }

  @Nullable
  public static ExternalDependency withMapNotation(@NotNull Dependencies parent,
                                                   @NotNull String configurationName,
                                                   @NotNull GrNamedArgument[] namedArguments) {
    return MapNotation.parse(parent, configurationName, namedArguments);
  }

  protected ExternalDependency(@NotNull Dependencies parent, @NotNull String configurationName, @NotNull Spec spec) {
    super(parent, configurationName);
    mySpec = spec;
  }

  @Nullable
  public String group() {
    return mySpec.group;
  }

  @NotNull
  public String name() {
    return mySpec.name;
  }

  @Nullable
  public String version() {
    return mySpec.version;
  }

  public void version(@NotNull String version) {
    myNewVersion = version;
    setModified(true);
  }

  @Nullable
  public String classifier() {
    return mySpec.classifier;
  }

  @Nullable
  public String extension() {
    return mySpec.extension;
  }

  @NotNull
  public String compactNotation() {
    return mySpec.toString();
  }

  @Override
  protected void apply() {
    applyVersionChange();
  }

  private void applyVersionChange() {
    if (myNewVersion == null) {
      return;
    }
    applyVersion(myNewVersion);
    reset();
  }

  protected abstract void applyVersion(@NotNull String newVersion);

  @Override
  protected void reset() {
    myNewVersion = null;
  }

  protected static class Spec {
    @NotNull public String name;

    @Nullable public String group;
    @Nullable public String version;
    @Nullable public String classifier;
    @Nullable public String extension;

    public Spec(@NotNull String name,
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
      Spec that = (Spec)o;
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
      List<String> segments = Lists.newArrayList(group, name, version, classifier);
      String s = Joiner.on(':').skipNulls().join(segments);
      if (extension != null) {
        s += "@" + extension;
      }
      return s;
    }
  }
}
