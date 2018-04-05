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

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyConfigurationModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.model.values.GradleNotNullValueImpl;
import com.android.tools.idea.gradle.dsl.model.values.GradleNullableValueImpl;
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependencyConfigurationDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;

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
 * <li><a href="https://docs.gradle.org/2.4/userguide/dependency_management.html">Gradle Dependency Management</a></li>
 * <li><a href="https://docs.gradle.org/current/dsl/org.gradle.api.artifacts.dsl.DependencyHandler.html">Gradle
 * DependencyHandler</a></li>
 * </ol>
 */
public abstract class ArtifactDependencyModelImpl extends DependencyModelImpl implements
                                                                              ArtifactDependencyModel {
  @Nullable private DependencyConfigurationDslElement myConfigurationElement;
  @NotNull private String myConfigurationName;

  public ArtifactDependencyModelImpl(@Nullable DependencyConfigurationDslElement configurationElement, @NotNull String configurationName) {
    myConfigurationElement = configurationElement;
    myConfigurationName = configurationName;
  }

  @NotNull
  @Override
  public abstract GradleNotNullValue<String> compactNotation();

  @NotNull
  @Override
  public abstract GradleNotNullValue<String> name();

  @NotNull
  @Override
  public abstract GradleNullableValue<String> group();

  @NotNull
  @Override
  public abstract GradleNullableValue<String> version();

  @Override
  public abstract void setVersion(@NotNull String version);

  @NotNull
  @Override
  public abstract GradleNullableValue<String> classifier();

  @NotNull
  @Override
  public abstract GradleNullableValue<String> extension();

  @Override
  @Nullable
  public DependencyConfigurationModel configuration() {
    if (myConfigurationElement == null) {
      return null;
    }
    return new DependencyConfigurationModelImpl(myConfigurationElement);
  }

  @Override
  @NotNull
  public String configurationName() {
    return myConfigurationName;
  }

  @NotNull
  static List<ArtifactDependencyModel> create(@NotNull String configurationName, @NotNull GradleDslElement element) {
    return create(configurationName, element, null);
  }

  @NotNull
  static List<ArtifactDependencyModel> create(@NotNull String configurationName,
                                              @NotNull GradleDslElement element,
                                              @Nullable DependencyConfigurationDslElement configurationElement) {
    if (configurationElement == null) {
      GradleDslClosure closureElement = element.getClosureElement();
      if (closureElement instanceof DependencyConfigurationDslElement) {
        configurationElement = (DependencyConfigurationDslElement)closureElement;
      }
    }
    List<ArtifactDependencyModel> results = Lists.newArrayList();
    assert element instanceof GradleDslExpression || element instanceof GradleDslExpressionMap;
    if (element instanceof GradleDslExpressionMap) {
      MapNotation mapNotation = MapNotation.create(configurationName, (GradleDslExpressionMap)element, configurationElement);
      if (mapNotation != null) {
        results.add(mapNotation);
      }
    }
    else if (element instanceof GradleDslMethodCall) {
      String name = ((GradleDslMethodCall)element).getMethodName();
      if (!"project".equals(name) && !"fileTree".equals(name) && !"files".equals(name)) {
        for (GradleDslElement argument : ((GradleDslMethodCall)element).getArguments()) {
          results.addAll(create(configurationName, argument, configurationElement));
        }
      }
    }
    else {
      CompactNotation compactNotation = CompactNotation.create(configurationName, (GradleDslExpression)element, configurationElement);
      if (compactNotation != null) {
        results.add(compactNotation);
      }
    }
    return results;
  }

  static void createAndAddToList(@NotNull GradleDslElementList list,
                                 @NotNull String configurationName,
                                 @NotNull ArtifactDependencySpec dependency,
                                 @NotNull List<ArtifactDependencySpec> excludes) {
    GradleNameElement name = GradleNameElement.create(configurationName);
    GradleDslLiteral literal = new GradleDslLiteral(list, name);
    literal.setValue(dependency.compactNotation());

    if (!excludes.isEmpty()) {
      GrClosableBlock configBlock = buildExcludesBlock(excludes, list.getDslFile().getProject());
      literal.setConfigBlock(configBlock);
    }

    list.addNewElement(literal);
  }

  private static GrClosableBlock buildExcludesBlock(@NotNull List<ArtifactDependencySpec> excludes, @NotNull Project project) {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
    GrClosableBlock block = factory.createClosureFromText("{\n}");
    for (ArtifactDependencySpec spec : excludes) {
      String text = String.format("exclude group: '%s', module: '%s'", spec.getGroup(), spec.getName());
      block.addBefore(factory.createStatementFromText(text), block.getLastChild());
      PsiElement lineTerminator = factory.createLineTerminator(1);
      block.addBefore(lineTerminator, block.getLastChild());
    }
    return block;
  }

  private static class MapNotation extends ArtifactDependencyModelImpl {
    @NotNull private GradleDslExpressionMap myDslElement;

    @Nullable
    static MapNotation create(@NotNull String configurationName,
                              @NotNull GradleDslExpressionMap dslElement,
                              @Nullable DependencyConfigurationDslElement configurationElement) {
      if (dslElement.getLiteralProperty("name", String.class).value() == null) {
        return null; // not a artifact dependency element.
      }

      return new MapNotation(configurationName, dslElement, configurationElement);
    }

    private MapNotation(@NotNull String configurationName,
                        @NotNull GradleDslExpressionMap dslElement,
                        @Nullable DependencyConfigurationDslElement configurationElement) {
      super(configurationElement, configurationName);
      myDslElement = dslElement;
    }

    @NotNull
    @Override
    public GradleNotNullValue<String> compactNotation() {
      ArtifactDependencySpecImpl spec = new ArtifactDependencySpecImpl(name().value(),
                                                                       group().value(),
                                                                       version().value(),
                                                                       classifier().value(),
                                                                       extension().value());
      return new GradleNotNullValueImpl<>(myDslElement, spec.compactNotation());
    }

    @Override
    @NotNull
    public GradleNotNullValue<String> name() {
      GradleNullableValue<String> name = myDslElement.getLiteralProperty("name", String.class);
      assert name instanceof GradleNotNullValueImpl;
      return (GradleNotNullValueImpl<String>)name;
    }

    @Override
    @NotNull
    public GradleNullableValue<String> group() {
      return myDslElement.getLiteralProperty("group", String.class);
    }

    @Override
    @NotNull
    public GradleNullableValue<String> version() {
      return myDslElement.getLiteralProperty("version", String.class);
    }

    @Override
    public void setVersion(@NotNull String version) {
      myDslElement.setNewLiteral("version", version);
    }

    @Override
    @NotNull
    public GradleNullableValue<String> classifier() {
      return myDslElement.getLiteralProperty("classifier", String.class);
    }

    @Override
    @NotNull
    public GradleNullableValue<String> extension() {
      return myDslElement.getLiteralProperty("ext", String.class);
    }

    @Override
    @NotNull
    protected GradleDslElement getDslElement() {
      return myDslElement;
    }
  }

  private static class CompactNotation extends ArtifactDependencyModelImpl {
    @NotNull private GradleDslExpression myDslExpression;
    @NotNull private ArtifactDependencySpec mySpec;

    @Nullable
    static CompactNotation create(@NotNull String configurationName,
                                  @NotNull GradleDslExpression dslExpression,
                                  @Nullable DependencyConfigurationDslElement configurationElement) {
      String value = dslExpression.getValue(String.class);
      if (value == null) {
        return null;
      }
      ArtifactDependencySpec spec = ArtifactDependencySpecImpl.create(value);
      if (spec == null) {
        return null;
      }
      return new CompactNotation(configurationName, dslExpression, spec, configurationElement);
    }

    private CompactNotation(@NotNull String configurationName,
                            @NotNull GradleDslExpression dslExpression,
                            @NotNull ArtifactDependencySpec spec,
                            @Nullable DependencyConfigurationDslElement configurationElement) {
      super(configurationElement, configurationName);
      myDslExpression = dslExpression;
      mySpec = spec;
    }

    @NotNull
    @Override
    public GradleNotNullValue<String> compactNotation() {
      return new GradleNotNullValueImpl<>(myDslExpression, mySpec.compactNotation());
    }

    @Override
    @NotNull
    public GradleNotNullValue<String> name() {
      return new GradleNotNullValueImpl<>(myDslExpression, mySpec.getName());
    }

    @Override
    @NotNull
    public GradleNullableValue<String> group() {
      return new GradleNullableValueImpl<>(myDslExpression, mySpec.getGroup());
    }

    @Override
    @NotNull
    public GradleNullableValue<String> version() {
      return new GradleNullableValueImpl<>(myDslExpression, mySpec.getVersion());
    }

    @Override
    public void setVersion(@NotNull String version) {
      mySpec.setVersion(version);
      myDslExpression.setValue(mySpec.toString());
    }

    @Override
    @NotNull
    public GradleNullableValue<String> classifier() {
      return new GradleNullableValueImpl<>(myDslExpression, mySpec.getClassifier());
    }

    @Override
    @NotNull
    public GradleNullableValue<String> extension() {
      return new GradleNullableValueImpl<>(myDslExpression, mySpec.getExtension());
    }

    @Override
    @NotNull
    protected GradleDslElement getDslElement() {
      return myDslExpression;
    }
  }
}
