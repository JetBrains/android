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

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.NONE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.iStr;
import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR;
import static com.android.tools.idea.gradle.dsl.model.dependencies.DependencyConfigurationModelImpl.EXCLUDE;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.followElement;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.resolveElement;
import static com.android.tools.idea.gradle.dsl.parser.dependencies.FakeArtifactElement.shouldInterpolate;

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyConfigurationModel;
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.CompactToMapCatalogDependencyTransform;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.FakeElementTransform;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.PropertyTransform;
import com.android.tools.idea.gradle.dsl.parser.dependencies.FakeArtifactElement;
import com.android.tools.idea.gradle.dsl.parser.elements.FakeElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslClosure;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import com.google.common.collect.Lists;
import com.intellij.psi.PsiElement;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * <li><a href="https://docs.gradle.org/2.4/userguide/dependency_management.html">Gradle Dependency Management</a></li>
 * <li><a href="https://docs.gradle.org/current/dsl/org.gradle.api.artifacts.dsl.DependencyHandler.html">Gradle
 * DependencyHandler</a></li>
 * </ol>
 */
public abstract class ArtifactDependencyModelImpl extends DependencyModelImpl implements ArtifactDependencyModel {
  @Nullable private GradleDslClosure myConfigurationElement;
  protected boolean mySetThrough = false;
  protected boolean versionCatalogDependency = false;

  @NotNull private static final Pattern WRAPPED_VARIABLE_FORM = Pattern.compile("\\$\\{(.*)}");
  @NotNull private static final Pattern UNWRAPPED_VARIABLE_FORM = Pattern.compile("\\$(([a-zA-Z0-9_]\\w*)(\\.([a-zA-Z0-9_]\\w+))*)");


  public ArtifactDependencyModelImpl(@Nullable GradleDslClosure configurationElement,
                                     @NotNull String configurationName,
                                     @NotNull Maintainer maintainer) {
    super(configurationName, maintainer);
    myConfigurationElement = configurationElement;
  }

  @NotNull
  @Override
  public ArtifactDependencySpec getSpec() {
    String name = name().toString();
    assert name != null;
    return new ArtifactDependencySpecImpl(name, group().toString(), version().toString(), classifier().toString(), extension().toString());
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
  public boolean isVersionCatalogDependency() {
    return versionCatalogDependency;
  }

  @Override
  public void markAsVersionCatalogDependency() {
    versionCatalogDependency = true;
  }

  private static @NotNull GradleDslLiteral createLiteral(@NotNull GradlePropertiesDslElement parent, @NotNull String configurationName) {
    GradleNameElement name = GradleNameElement.create(configurationName);
    GradleDslLiteral literal = new GradleDslLiteral(parent, name);
    literal.setElementType(REGULAR);
    parent.setNewElement(literal);
    return literal;
  }

  private static void initializeLiteral(@NotNull GradleDslLiteral literal,
                                        @NotNull Object value,
                                        @NotNull List<ArtifactDependencySpec> excludes) {
    literal.setValue(value);
    addExcludes(literal, excludes);
  }

  static void createNew(@NotNull GradlePropertiesDslElement parent,
                        @NotNull String configurationName,
                        @NotNull ReferenceTo reference,
                        @NotNull List<ArtifactDependencySpec> excludes) {
    GradleDslLiteral literal = createLiteral(parent, configurationName);
    initializeLiteral(literal, reference, excludes);
  }

  static void createNew(@NotNull GradlePropertiesDslElement parent,
                        @NotNull String configurationName,
                        @NotNull ArtifactDependencySpec dependency,
                        @NotNull List<ArtifactDependencySpec> excludes) {
    GradleDslLiteral literal = createLiteral(parent, configurationName);
    initializeLiteral(literal, createCompactNotationForLiterals(literal, dependency), excludes);
  }

  private static void addExcludes(@NotNull GradleDslLiteral literal, @NotNull List<ArtifactDependencySpec> excludes) {
    if (!excludes.isEmpty()) {
      GradleDslClosure closure = new GradleDslClosure(literal.getParent(), null, literal.getNameElement());
      for (ArtifactDependencySpec exclude : excludes) {
        GradleDslExpressionMap map = new GradleDslExpressionMap(closure, GradleNameElement.create(EXCLUDE));
        String group = exclude.getGroup();
        if (group != null) {
          GradleDslLiteral groupEntry = new GradleDslLiteral(map, GradleNameElement.create("group"));
          groupEntry.setValue(shouldInterpolate(group) ? iStr(group) : group);
          map.setNewElement(groupEntry);
        }
        GradleDslLiteral moduleEntry = new GradleDslLiteral(map, GradleNameElement.create("module"));
        String module = exclude.getName();
        moduleEntry.setValue(shouldInterpolate(module) ? iStr(module) : module);
        map.setNewElement(moduleEntry);
        closure.setNewElement(map);
      }
      literal.setNewClosureElement(closure);
    }
  }

  /**
   * @return same as {@link ArtifactDependencySpec#compactNotation} but quoted if interpolation is needed.
   */
  @NotNull
  protected static String createCompactNotationForLiterals(@NotNull GradleDslElement dslElement, @NotNull ArtifactDependencySpec spec) {
    List<String> segments =
      Lists.newArrayList(spec.getGroup(), spec.getName(), spec.getVersion(), spec.getClassifier(), spec.getExtension());
    boolean shouldInterpolate = false;

    // TODO(b/148283067): this is a workaround to use the correct syntax when creating literals with interpolation.
    StringBuilder compactNotation = new StringBuilder();
    for (int currentElementIdx = 0; currentElementIdx < segments.size(); currentElementIdx++) {
      String segment = segments.get(currentElementIdx);
      if (segment != null) {
        if (currentElementIdx == 4) compactNotation.append("@");
        else if (currentElementIdx > 0) compactNotation.append(":");
        if (shouldInterpolate(segment)) {
          shouldInterpolate = true;
          Matcher wrappedValueMatcher = WRAPPED_VARIABLE_FORM.matcher(segment);
          Matcher unwrappedValueMatcher = UNWRAPPED_VARIABLE_FORM.matcher(segment);
          String interpolatedVariable = null;
          if (wrappedValueMatcher.find()) {
            interpolatedVariable = wrappedValueMatcher.group(1);
          } else if (unwrappedValueMatcher.find()) {
            interpolatedVariable = unwrappedValueMatcher.group(1);
          }

          String value = interpolatedVariable != null ?
                         dslElement.getDslFile().getParser().convertReferenceToExternalText(dslElement, interpolatedVariable, true)
                                                      : segment;
          // If we have a simple value (i.e. one word) then we don't need to use {} for the injection.
          if (Pattern.compile("([a-zA-Z0-9_]\\w*)").matcher(value).matches()) {
            compactNotation.append("$").append(value);
          } else if (WRAPPED_VARIABLE_FORM.matcher(value).matches() || UNWRAPPED_VARIABLE_FORM.matcher(value).matches()) {
            //  The value is already interpolated, should be written as is.
            compactNotation.append(value);
          } else {
            compactNotation.append("${").append(value).append("}");
          }
          continue;
        }
        compactNotation.append(segment);
      }
    }
    return shouldInterpolate ? iStr(compactNotation.toString()) : compactNotation.toString();
  }

  /**
   * Class represents artifact dependency model that
   * map or reference to map (it will use MapStrategy) or it can be
   * compact notation or reference to it (it will use CompactStrategy).
   * While class lifetime dsl can be changed from one to another.
   * Valid scenario is to substitute TOML compact notation to map notation
   * when user assigned variable as version.
   */
  static class DynamicNotation extends ArtifactDependencyModelImpl {
    private GradleDslExpression myDslExpression;

    DynamicNotation(@NotNull String configurationName,
                    @NotNull GradleDslExpression dslExpression,
                    @Nullable GradleDslClosure configurationElement,
                    @NotNull Maintainer maintainer) {
      super(configurationElement, configurationName, maintainer);
      this.myDslExpression = dslExpression;
    }

    @Nullable
    private static GradleDslExpression resolveExpression(@NotNull GradleDslExpression expression) {
      GradleDslElement element = followElement(expression);
      if (element instanceof GradleDslExpression) {
        expression = (GradleDslExpression)element;
      }
      return expression;
    }

    private NotationStrategy getStrategy() {
      GradleDslExpression resolvedExpression = resolveExpression(myDslExpression);
      if (resolvedExpression instanceof GradleDslExpressionMap) {
        return new MapNotationStrategy((GradleDslExpressionMap)resolvedExpression);
      }
      else if (myDslExpression instanceof GradleDslSimpleExpression) {
        return new CompactNotationStrategy((GradleDslSimpleExpression)myDslExpression, mySetThrough);
      }
      return null;
    }

    @NotNull
    @Override
    public ResolvedPropertyModel name() {
      return getStrategy().name();
    }

    @Nullable
    @Override
    public ResolvedPropertyModel group() {
      return getStrategy().group();
    }

    @Nullable
    @Override
    public ResolvedPropertyModel version() {
      return getStrategy().version();
    }

    @Nullable
    @Override
    public ResolvedPropertyModel classifier() {
      return getStrategy().classifier();
    }

    @Nullable
    @Override
    public ResolvedPropertyModel extension() {
      return getStrategy().extension();
    }

    @Nullable
    @Override
    public ResolvedPropertyModel completeModel() {
      return GradlePropertyModelBuilder.create(myDslExpression).buildResolved();
    }

    @Nullable
    @Override
    protected GradleDslElement getDslElement() {
      return myDslExpression;
    }

    @Override
    void setDslElement(@NotNull GradleDslElement dslElement) {
      myDslExpression = (GradleDslExpression)dslElement;
    }

    @Override
    @Nullable
    public PsiElement getPsiElement() {
      // The GradleDslElement#getPsiElement will not always be the correct literal. We correct this by getting the expression.
      return myDslExpression.getExpression();
    }

    public boolean isValidDSL() {
      return getStrategy().isValidDSL();
    }

    @Nullable
    static DynamicNotation create(@NotNull String configurationName,
                                  @NotNull GradleDslExpression dslExpression,
                                  @Nullable GradleDslClosure configurationElement,
                                  @NotNull Maintainer maintainer,
                                  @Nullable String platformMethodName) {
      DynamicNotation result;
      if (platformMethodName == null) {
        result = new DynamicNotation(configurationName, dslExpression, configurationElement, maintainer);
      }
      else {
        result = new PlatformArtifactDependencyModelImpl.DynamicNotation(
          configurationName, dslExpression, configurationElement, maintainer, platformMethodName);
      }
      return result.isValidDSL() ? result : null;
    }
  }

  interface NotationStrategy {


    boolean isValidDSL();

    ResolvedPropertyModel name();

    ResolvedPropertyModel group();

    ResolvedPropertyModel version();

    ResolvedPropertyModel classifier();

    ResolvedPropertyModel extension();
  }

  static class MapNotationStrategy implements NotationStrategy {
    @NotNull private GradleDslExpressionMap myDslElement;

    MapNotationStrategy(@NotNull GradleDslExpressionMap dslElement) {
      myDslElement = dslElement;
    }

    @Override
    @NotNull
    public ResolvedPropertyModel name() {
      GradleDslLiteral module = myDslElement.getPropertyElement("module", GradleDslLiteral.class);
      if (module != null) {
        FakeElement element = new FakeArtifactElement(myDslElement,
                                                      GradleNameElement.fake("name"),
                                                      module,
                                                      ArtifactDependencySpec::getName,
                                                      ArtifactDependencySpecImpl::setName,
                                                      false);
        return GradlePropertyModelBuilder.create(element).addTransform(new FakeElementTransform()).buildResolved();
      }
      return GradlePropertyModelBuilder.create(myDslElement, "name").buildResolved();
    }

    @Override
    public boolean isValidDSL() {
      return myDslElement.getLiteral("name", String.class) != null || myDslElement.getLiteral("module", String.class) != null;
    }

    @Override
    @NotNull
    public ResolvedPropertyModel group() {
      GradleDslLiteral module = myDslElement.getPropertyElement("module", GradleDslLiteral.class);
      if (module != null) {
        FakeElement element = new FakeArtifactElement(myDslElement,
                                                      GradleNameElement.fake("group"),
                                                      module,
                                                      ArtifactDependencySpec::getGroup,
                                                      ArtifactDependencySpecImpl::setGroup,
                                                      false);
        return GradlePropertyModelBuilder.create(element).addTransform(new FakeElementTransform()).buildResolved();
      }
      return GradlePropertyModelBuilder.create(myDslElement, "group").buildResolved();
    }

    @Override
    @NotNull
    public ResolvedPropertyModel version() {
      return GradlePropertyModelBuilder.create(myDslElement, "version").buildResolved();
    }

    @Override
    @NotNull
    public ResolvedPropertyModel classifier() {
      return GradlePropertyModelBuilder.create(myDslElement, "classifier").buildResolved();
    }

    @Override
    @NotNull
    public ResolvedPropertyModel extension() {
      return GradlePropertyModelBuilder.create(myDslElement, "ext").buildResolved();
    }
  }

  static class CompactNotationStrategy implements NotationStrategy {
    @NotNull private GradleDslSimpleExpression myDslExpression;
    private boolean mySetThrough;

    CompactNotationStrategy(@NotNull GradleDslSimpleExpression dslExpression,
                            boolean setThrough) {
      myDslExpression = dslExpression;
      mySetThrough = setThrough;
    }

    @NotNull
    public ResolvedPropertyModel createModelFor(@NotNull String name,
                                                @NotNull Function<ArtifactDependencySpec, String> getFunc,
                                                @NotNull BiConsumer<ArtifactDependencySpecImpl, String> setFunc,
                                                boolean canDelete,
                                                PropertyTransform additionalTransformer
    ) {
      GradleDslSimpleExpression element = mySetThrough ? resolveElement(myDslExpression) : myDslExpression;
      FakeElement fakeElement =
        new FakeArtifactElement(element.getParent(), GradleNameElement.fake(name), element, getFunc, setFunc, canDelete);
      GradlePropertyModelBuilder builder = GradlePropertyModelBuilder.create(fakeElement);
      if (additionalTransformer != null) {
        builder = builder.addTransform(additionalTransformer);
      }
      return builder.addTransform(new FakeElementTransform()).buildResolved();
    }

    @NotNull
    public ResolvedPropertyModel createModelFor(@NotNull String name,
                                                @NotNull Function<ArtifactDependencySpec, String> getFunc,
                                                @NotNull BiConsumer<ArtifactDependencySpecImpl, String> setFunc,
                                                boolean canDelete) {
      return createModelFor(name,getFunc,setFunc,canDelete,null);
    }


    @Override
    public boolean isValidDSL() {
      String value = myDslExpression.getValue(String.class);
      if (value == null || value.trim().isEmpty()) {
        return false;
      }
      // Check if the notation is valid i.e. it has a name
      return name().getValueType() != NONE;
    }

    @Override
    @NotNull
    public ResolvedPropertyModel name() {
      return createModelFor("name", ArtifactDependencySpec::getName, ArtifactDependencySpecImpl::setName, false);
    }

    @Override
    @NotNull
    public ResolvedPropertyModel group() {
      return createModelFor("group", ArtifactDependencySpec::getGroup, ArtifactDependencySpecImpl::setGroup, true);
    }

    @Override
    @NotNull
    public ResolvedPropertyModel version() {
      return createModelFor("version",
                            ArtifactDependencySpec::getVersion,
                            ArtifactDependencySpecImpl::setVersion,
                            true,
                            new CompactToMapCatalogDependencyTransform());
    }

    @Override
    @NotNull
    public ResolvedPropertyModel classifier() {
      return createModelFor("classifier", ArtifactDependencySpec::getClassifier, ArtifactDependencySpecImpl::setClassifier, true);
    }

    @Override
    @NotNull
    public ResolvedPropertyModel extension() {
      return createModelFor("extension", ArtifactDependencySpec::getExtension, ArtifactDependencySpecImpl::setExtension, true);
    }
  }
}
