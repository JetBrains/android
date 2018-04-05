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
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependencyConfigurationDslElement;
import com.android.tools.idea.gradle.dsl.parser.dependencies.FakeArtifactElement;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.google.common.collect.Lists;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

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
      PsiElement configBlock = list.getDslFile().getParser().convertToExcludesBlock(excludes);
      literal.setConfigBlock(configBlock);
    }

    list.addNewElement(literal);
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
    @NotNull private GradleDslExpression myDslExpression;

    @Nullable
    static CompactNotation create(@NotNull String configurationName,
                                  @NotNull GradleDslExpression dslExpression,
                                  @Nullable DependencyConfigurationDslElement configurationElement) {
      String value = dslExpression.getValue(String.class);
      if (value == null) {
        return null;
      }
      return new CompactNotation(configurationName, dslExpression, configurationElement);
    }

    private CompactNotation(@NotNull String configurationName,
                            @NotNull GradleDslExpression dslExpression,
                            @Nullable DependencyConfigurationDslElement configurationElement) {
      super(configurationElement, configurationName);
      myDslExpression = dslExpression;
    }

    @NotNull
    public ResolvedPropertyModel createModelFor(@NotNull String name,
                                                @NotNull Function<ArtifactDependencySpec, String> getFunc,
                                                @NotNull BiConsumer<ArtifactDependencySpec, String> setFunc,
                                                boolean canDelete) {
      FakeElement fakeElement =
        new FakeArtifactElement(myDslExpression.getParent(), GradleNameElement.fake(name), myDslExpression, getFunc, setFunc, canDelete);
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
