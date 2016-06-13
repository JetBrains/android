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

import com.android.tools.idea.gradle.dsl.model.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.model.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
  public abstract GradleNotNullValue<String> compactNotation();

  @NotNull
  public abstract GradleNotNullValue<String> name();

  @NotNull
  public abstract GradleNullableValue<String> group();

  @NotNull
  public abstract GradleNullableValue<String> version();

  public abstract void setVersion(@NotNull String version);

  @NotNull
  public abstract GradleNullableValue<String> classifier();

  @NotNull
  public abstract GradleNullableValue<String> extension();

  @NotNull
  static List<ArtifactDependencyModel> create(@NotNull GradleDslElement element) {
    List<ArtifactDependencyModel> results = Lists.newArrayList();
    assert element instanceof GradleDslExpression || element instanceof GradleDslExpressionMap;
    if (element instanceof GradleDslExpressionMap) {
      MapNotation mapNotation = MapNotation.create((GradleDslExpressionMap)element);
      if (mapNotation != null) {
        results.add(mapNotation);
      }
    }
    else if (element instanceof GradleDslMethodCall) {
      String name = element.getName();
      if (!"project".equals(name) && !"fileTree".equals(name)) {
        for (GradleDslElement argument : ((GradleDslMethodCall)element).getArguments()) {
          results.addAll(create(argument));
        }
      }
    }
    else {
      CompactNotation compactNotation = CompactNotation.create((GradleDslExpression)element);
      if (compactNotation != null) {
          results.add(compactNotation);
      }
    }
    return results;
  }

  static void createAndAddToList(@NotNull GradleDslElementList list,
                                 @NotNull String configurationName,
                                 @NotNull ArtifactDependencySpec dependency) {
    GradleDslLiteral literal = new GradleDslLiteral(list, configurationName);
    literal.setValue(dependency.compactNotation());
    list.addNewElement(literal);
  }

  private static class MapNotation extends ArtifactDependencyModel {
    @NotNull private GradleDslExpressionMap myDslElement;

    @Nullable
    static MapNotation create(GradleDslExpressionMap dslElement) {
      if (dslElement.getPropertyValue("name", String.class).value() == null) {
        return null; // not a artifact dependency element.
      }

      return new MapNotation(dslElement);
    }

    private MapNotation(@NotNull GradleDslExpressionMap dslElement) {
      myDslElement = dslElement;
    }

    @NotNull
    @Override
    public GradleNotNullValue<String> compactNotation() {
      ArtifactDependencySpec spec = new ArtifactDependencySpec(name().value(),
                                                               group().value(),
                                                               version().value(),
                                                               classifier().value(),
                                                               extension().value());
      return new GradleNotNullValue<>(myDslElement, spec.compactNotation());
    }

    @Override
    @NotNull
    public GradleNotNullValue<String> name() {
      return GradleNotNullValue.create(myDslElement.getPropertyValue("name", String.class));
    }

    @Override
    @NotNull
    public GradleNullableValue<String> group() {
      return myDslElement.getPropertyValue("group", String.class);
    }

    @Override
    @NotNull
    public GradleNullableValue<String> version() {
      return myDslElement.getPropertyValue("version", String.class);
    }

    @Override
    public void setVersion(@NotNull String version) {
      myDslElement.setNewLiteral("version", version);
    }

    @Override
    @NotNull
    public GradleNullableValue<String> classifier() {
      return myDslElement.getPropertyValue("classifier", String.class);
    }

    @Override
    @NotNull
    public GradleNullableValue<String> extension() {
      return myDslElement.getPropertyValue("ext", String.class);
    }

    @Override
    @NotNull
    protected GradleDslElement getDslElement() {
      return myDslElement;
    }
  }

  private static class CompactNotation extends ArtifactDependencyModel {
    @NotNull private GradleDslExpression myDslExpression;
    @NotNull private ArtifactDependencySpec mySpec;

    @Nullable
    static CompactNotation create(GradleDslExpression dslExpression) {
      String value = dslExpression.getValue(String.class);
      if (value == null) {
        return null;
      }
      ArtifactDependencySpec spec = ArtifactDependencySpec.create(value);
      if (spec == null) {
        return null;
      }
      return new CompactNotation(dslExpression, spec);
    }

    private CompactNotation(@NotNull GradleDslExpression dslExpression, @NotNull ArtifactDependencySpec spec) {
      myDslExpression = dslExpression;
      mySpec = spec;
    }

    @NotNull
    @Override
    public GradleNotNullValue<String> compactNotation() {
      return new GradleNotNullValue<>(myDslExpression, mySpec.compactNotation());
    }

    @Override
    @NotNull
    public GradleNotNullValue<String> name() {
      return new GradleNotNullValue<>(myDslExpression, mySpec.name);
    }

    @Override
    @NotNull
    public GradleNullableValue<String> group() {
      return new GradleNullableValue<>(myDslExpression, mySpec.group);
    }

    @Override
    @NotNull
    public GradleNullableValue<String> version() {
      return new GradleNullableValue<>(myDslExpression, mySpec.version);
    }

    @Override
    public void setVersion(@NotNull String version) {
      mySpec.version = version;
      myDslExpression.setValue(mySpec.toString());
    }

    @Override
    @NotNull
    public GradleNullableValue<String> classifier() {
      return new GradleNullableValue<>(myDslExpression, mySpec.classifier);
    }

    @Override
    @NotNull
    public GradleNullableValue<String> extension() {
      return new GradleNullableValue<>(myDslExpression, mySpec.extension);
    }

    @Override
    @NotNull
    protected GradleDslElement getDslElement() {
      return myDslExpression;
    }
  }
}
