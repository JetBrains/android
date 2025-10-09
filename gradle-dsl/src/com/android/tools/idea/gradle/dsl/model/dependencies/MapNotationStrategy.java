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

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.FakeElementTransform;
import com.android.tools.idea.gradle.dsl.parser.dependencies.FakeArtifactElement;
import com.android.tools.idea.gradle.dsl.parser.elements.FakeElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

public class MapNotationStrategy implements NotationStrategy {
  @NotNull private GradleDslExpressionMap myDslElement;

  public MapNotationStrategy(@NotNull GradleDslExpressionMap dslElement) {
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
    return StringUtils.isNotEmpty(name().valueAsString());
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
