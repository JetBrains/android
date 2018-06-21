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
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.FakeElementTransform;
import com.android.tools.idea.gradle.dsl.parser.dependencies.FakeArtifactElement;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.iStr;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.followElement;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.resolveElement;

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
  @Nullable private GradleDslClosure myConfigurationElement;
  @NotNull private String myConfigurationName;
  protected boolean mySetThrough = false;

  public ArtifactDependencyModelImpl(@Nullable GradleDslClosure configurationElement, @NotNull String configurationName) {
    myConfigurationElement = configurationElement;
    myConfigurationName = configurationName;
  }

  @NotNull
  protected ArtifactDependencySpec getSpec() {
    String name = name().toString();
    assert name != null;
    return new ArtifactDependencySpecImpl(name,
                                          group().toString(),
                                          version().toString(),
                                          classifier().toString(),
                                          extension().toString());
  }

  @Override
  @NotNull
  public String compactNotation() {
    return getSpec().compactNotation();
  }

  @Override
  @NotNull
  public abstract ResolvedPropertyModel name();

  @Override
  @NotNull
  public abstract ResolvedPropertyModel group();

  @Override
  @NotNull
  public abstract ResolvedPropertyModel version();

  @Override
  @NotNull
  public abstract ResolvedPropertyModel classifier();

  @Override
  @NotNull
  public abstract ResolvedPropertyModel extension();

  @Override
  @NotNull
  public abstract ResolvedPropertyModel completeModel();

  @Override
  @Nullable
  public DependencyConfigurationModel configuration() {
    if (myConfigurationElement == null) {
      return null;
    }
    return new DependencyConfigurationModelImpl(myConfigurationElement);
  }

  @Override
  public void enableSetThrough() {
    mySetThrough = true;
  }

  @Override
  public void disableSetThrough() {
    mySetThrough = false;
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
                                              @Nullable GradleDslClosure configurationElement) {
    if (configurationElement == null) {
      configurationElement = element.getClosureElement();
    }
    List<ArtifactDependencyModel> results = Lists.newArrayList();
    // We can only create ArtifactDependencyModels from expressions, if for some reason we don't have an expression here (e.g form a
    // parser bug) then don't create anything.
    if (!(element instanceof GradleDslExpression)) {
      return ImmutableList.of();
    }
    GradleDslExpression resolved = (GradleDslExpression)element;
    if (element instanceof GradleDslLiteral) {
      GradleDslElement foundElement = followElement((GradleDslLiteral)element);
      if (foundElement instanceof GradleDslExpression) {
        resolved = (GradleDslExpression)foundElement;
      }
    }

    if (resolved instanceof GradleDslExpressionMap) {
      MapNotation mapNotation =
        MapNotation.create(configurationName, (GradleDslExpressionMap)resolved, configurationElement);
      if (mapNotation != null) {
        results.add(mapNotation);
      }
    }
    else if (resolved instanceof GradleDslMethodCall) {
      String name = ((GradleDslMethodCall)resolved).getMethodName();
      if (!"project".equals(name) && !"fileTree".equals(name) && !"files".equals(name)) {
        for (GradleDslElement argument : ((GradleDslMethodCall)resolved).getArguments()) {
          results.addAll(create(configurationName, argument, configurationElement));
        }
      }
    }
    else if (resolved instanceof GradleDslExpressionList) {
      for (GradleDslSimpleExpression expression : ((GradleDslExpressionList)resolved).getSimpleExpressions()) {
        CompactNotation compactNotation = CompactNotation.create(configurationName, expression, configurationElement);
        if (compactNotation != null) {
          results.add(compactNotation);
        }
      }
    }
    else {
      CompactNotation compactNotation = CompactNotation.create(configurationName, (GradleDslSimpleExpression)element, configurationElement);
      if (compactNotation != null) {
        results.add(compactNotation);
      }
    }
    return results;
  }

  static void create(@NotNull GradlePropertiesDslElement parent,
                     @NotNull String configurationName,
                     @NotNull ArtifactDependencySpec dependency,
                     @NotNull List<ArtifactDependencySpec> excludes) {
    GradleNameElement name = GradleNameElement.create(configurationName);
    GradleDslLiteral literal = new GradleDslLiteral(parent, name);
    literal.setValue(createCompactNotationForLiterals(dependency));

    if (!excludes.isEmpty()) {
      PsiElement configBlock = parent.getDslFile().getParser().convertToExcludesBlock(excludes);
      assert configBlock != null;
      literal.setConfigBlock(configBlock);
    }

    parent.setNewElement(literal);
  }

  /**
   * @return same as {@link ArtifactDependencySpec#compactNotation} but quoted if interpolation is needed.
   */
  @NotNull
  private static String createCompactNotationForLiterals(@NotNull ArtifactDependencySpec spec) {
    List<String> segments =
      Lists.newArrayList(spec.getGroup(), spec.getName(), spec.getVersion(), spec.getClassifier(), spec.getExtension());
    boolean shouldInterpolate = segments.stream().filter(Objects::nonNull).anyMatch(FakeArtifactElement::shouldInterpolate);
    String compact = spec.compactNotation();
    return shouldInterpolate ? iStr(compact) : compact;
  }

  private static class MapNotation extends ArtifactDependencyModelImpl {
    @NotNull private GradleDslExpressionMap myDslElement;

    @Nullable
    static MapNotation create(@NotNull String configurationName,
                              @NotNull GradleDslExpressionMap dslElement,
                              @Nullable GradleDslClosure configurationElement) {
      if (dslElement.getLiteral("name", String.class) == null) {
        return null; // not a artifact dependency element.
      }

      return new MapNotation(configurationName, dslElement, configurationElement);
    }

    private MapNotation(@NotNull String configurationName,
                        @NotNull GradleDslExpressionMap dslElement,
                        @Nullable GradleDslClosure configurationElement) {
      super(configurationElement, configurationName);
      myDslElement = dslElement;
    }

    @Override
    @NotNull
    public ResolvedPropertyModel name() {
      return GradlePropertyModelBuilder.create(myDslElement, "name").asMethod(true).buildResolved();
    }

    @Override
    @NotNull
    public ResolvedPropertyModel group() {
      return GradlePropertyModelBuilder.create(myDslElement, "group").asMethod(true).buildResolved();
    }

    @Override
    @NotNull
    public ResolvedPropertyModel version() {
      return GradlePropertyModelBuilder.create(myDslElement, "version").asMethod(true).buildResolved();
    }

    @Override
    @NotNull
    public ResolvedPropertyModel classifier() {
      return GradlePropertyModelBuilder.create(myDslElement, "classifier").asMethod(true).buildResolved();
    }

    @Override
    @NotNull
    public ResolvedPropertyModel extension() {
      return GradlePropertyModelBuilder.create(myDslElement, "ext").asMethod(true).buildResolved();
    }

    @NotNull
    @Override
    public ResolvedPropertyModel completeModel() {
      return GradlePropertyModelBuilder.create(myDslElement).asMethod(true).buildResolved();
    }

    @Override
    @NotNull
    protected GradleDslElement getDslElement() {
      return myDslElement;
    }
  }

  private static class CompactNotation extends ArtifactDependencyModelImpl {
    @NotNull private GradleDslSimpleExpression myDslExpression;

    @Nullable
    static CompactNotation create(@NotNull String configurationName,
                                  @NotNull GradleDslSimpleExpression dslExpression,
                                  @Nullable GradleDslClosure configurationElement) {
      String value = dslExpression.getValue(String.class);
      if (value == null || value.trim().isEmpty()) {
        return null;
      }
      return new CompactNotation(configurationName, dslExpression, configurationElement);
    }

    private CompactNotation(@NotNull String configurationName,
                            @NotNull GradleDslSimpleExpression dslExpression,
                            @Nullable GradleDslClosure configurationElement) {
      super(configurationElement, configurationName);
      myDslExpression = dslExpression;
    }

    @NotNull
    public ResolvedPropertyModel createModelFor(@NotNull String name,
                                                @NotNull Function<ArtifactDependencySpec, String> getFunc,
                                                @NotNull BiConsumer<ArtifactDependencySpec, String> setFunc,
                                                boolean canDelete) {
      GradleDslSimpleExpression element = mySetThrough ? resolveElement(myDslExpression) : myDslExpression;
      FakeElement fakeElement =
        new FakeArtifactElement(element.getParent(), GradleNameElement.fake(name), element, getFunc, setFunc, canDelete);
      return GradlePropertyModelBuilder.create(fakeElement).addTransform(new FakeElementTransform()).asMethod(true).buildResolved();
    }

    @Override
    @NotNull
    public ResolvedPropertyModel name() {
      return createModelFor("name", ArtifactDependencySpec::getName, ArtifactDependencySpec::setName, false);
    }

    @Override
    @NotNull
    public ResolvedPropertyModel group() {
      return createModelFor("group", ArtifactDependencySpec::getGroup, ArtifactDependencySpec::setGroup, true);
    }

    @Override
    @NotNull
    public ResolvedPropertyModel version() {
      return createModelFor("version", ArtifactDependencySpec::getVersion, ArtifactDependencySpec::setVersion, true);
    }

    @Override
    @NotNull
    public ResolvedPropertyModel classifier() {
      return createModelFor("classifier", ArtifactDependencySpec::getClassifier, ArtifactDependencySpec::setClassifier, true);
    }

    @Override
    @NotNull
    public ResolvedPropertyModel extension() {
      return createModelFor("extension", ArtifactDependencySpec::getExtension, ArtifactDependencySpec::setExtension, true);
    }

    @NotNull
    @Override
    public ResolvedPropertyModel completeModel() {
      return GradlePropertyModelBuilder.create(myDslExpression).asMethod(true).buildResolved();
    }

    @Override
    @NotNull
    protected GradleDslElement getDslElement() {
      return myDslExpression;
    }

    @Override
    @Nullable
    public PsiElement getPsiElement() {
      // The GradleDslElement#getPsiElement will not always be the correct literal. We correct this by getting the expression.
      return myDslExpression.getExpression();
    }
  }
}
