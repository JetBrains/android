/*
 * Copyright (C) 2025 The Android Open Source Project
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
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.resolveElement;

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.CompactToMapCatalogDependencyTransform;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.FakeElementTransform;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.PropertyTransform;
import com.android.tools.idea.gradle.dsl.parser.dependencies.FakeArtifactElement;
import com.android.tools.idea.gradle.dsl.parser.elements.FakeElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

public class CompactNotationStrategy implements NotationStrategy {
  @NotNull private GradleDslSimpleExpression myDslExpression;
  private boolean mySetThrough;

  public CompactNotationStrategy(@NotNull GradleDslSimpleExpression dslExpression,
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
    return createModelFor(name, getFunc, setFunc, canDelete, null);
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
