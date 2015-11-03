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

import com.android.tools.idea.gradle.dsl.dependencies.ExternalDependencySpec;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteralMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A Gradle artifact dependency. There are two notations supported for declaring a dependency on an external module. One is a string
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
 *  <li><a href="https://docs.gradle.org/2.4/userguide/dependency_management.html">Gradle Dependency Management</a></li>
 *  <li><a href="https://docs.gradle.org/current/dsl/org.gradle.api.artifacts.dsl.DependencyHandler.html">Gradle
 * DependencyHandler</a></li>
 * </ol>
 */
public abstract class ArtifactDependencyModel extends DependencyModel {
  @NotNull
  public abstract String name();

  @Nullable
  public abstract String group();

  @Nullable
  public abstract String version();

  public abstract void setVersion(@NotNull String name);

  @Nullable
  public abstract String classifier();

  @Nullable
  public abstract String extension();

  // TODO make this API to get a standard notation
  @NotNull
  public ExternalDependencySpec getDependencySpec() {
    return new ExternalDependencySpec(name(), group(), version(), classifier(), extension());
  }

  @NotNull
  public static ArtifactDependencyModel create(@NotNull GradleDslElement element) {
    if (element instanceof GradleDslLiteralMap) {
      return new MapNotation((GradleDslLiteralMap)element);
    }
    else if (element instanceof GradleDslLiteral) {
      // TODO implement CompactNotationModel
    }
    throw new IllegalArgumentException("Element used to create ArtifactDependencyModel must be GradleDslLiteralMap or GradleDslLiteral");
  }

  private static class MapNotation extends ArtifactDependencyModel {
    @NotNull private GradleDslLiteralMap myDslElement;

    MapNotation(@NotNull GradleDslLiteralMap dslElement) {
      myDslElement = dslElement;
    }

    @NotNull
    @Override
    public String name() {
      String value = myDslElement.getProperty("name", String.class);
      assert value != null;
      return value;
    }

    @Nullable
    @Override
    public String group() {
      return myDslElement.getProperty("group", String.class);
    }

    @Nullable
    @Override
    public String version() {
      return myDslElement.getProperty("version", String.class);
    }

    @Override
    public void setVersion(@NotNull String name) {
      myDslElement.setLiteralProperty("version", name);
    }

    @Nullable
    @Override
    public String classifier() {
      return myDslElement.getProperty("classifier", String.class);
    }


    @Nullable
    @Override
    public String extension() {
      return myDslElement.getProperty("ext", String.class);
    }

    @NotNull
    @Override
    protected GradleDslElement getDslElement() {
      return myDslElement;
    }
  }
}
