/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.android.parser.android.testOptions.testSuites;

import static com.android.tools.idea.gradle.dsl.parser.SharedParserUtilsKt.isGradleContainerMethodName;
import static com.android.tools.idea.gradle.dsl.android.parser.android.testOptions.testSuites.TargetDslElement.TARGET;

import com.android.tools.idea.gradle.dsl.android.api.android.testOptions.testSuites.TargetModel;
import com.android.tools.idea.gradle.dsl.android.model.android.testOptions.testSuites.TargetModelImpl;
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElementMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslNamedDomainContainer;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class TargetsDslElement extends GradleDslElementMap implements GradleDslNamedDomainContainer {
  public static final PropertiesElementDescription<TargetsDslElement> TARGETS =
    new PropertiesElementDescription<>("targets", TargetsDslElement.class, TargetsDslElement::new);

  @Override
  public PropertiesElementDescription getChildPropertiesElementDescription(
    GradleDslNameConverter converter,
    String name
  ) {
    return TARGET;
  }

  @Override
  public boolean implicitlyExists(@NotNull String name) {
    return false;
  }

  public TargetsDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  @NotNull
  public List<TargetModel> get() {
    List<TargetModel> result = new ArrayList<>();
    for (TargetDslElement dslElement : getValues(TargetDslElement.class)) {
      if (!isGradleContainerMethodName(dslElement.getName())) {
        result.add(new TargetModelImpl(dslElement));
      }
    }
    return result;
  }

  @Override
  public boolean isBlockElement() {
    return true;
  }
}