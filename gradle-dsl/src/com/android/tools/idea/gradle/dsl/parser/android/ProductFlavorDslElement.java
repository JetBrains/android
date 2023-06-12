/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.android;

import static com.android.tools.idea.gradle.dsl.model.android.FlavorTypeModelImpl.INIT_WITH;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.OTHER;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslNamedDomainElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ProductFlavorDslElement extends AbstractProductFlavorDslElement implements GradleDslNamedDomainElement {
  public static final PropertiesElementDescription<ProductFlavorDslElement> PRODUCT_FLAVOR =
    new PropertiesElementDescription<>(null, ProductFlavorDslElement.class, ProductFlavorDslElement::new);

  private static final ExternalToModelMap ktsToModelNameMap = Stream.of(new Object[][]{
    {"initWith", exactly(1), INIT_WITH, OTHER},
  }).collect(toModelMap(AbstractProductFlavorDslElement.ktsToModelNameMap));

  private static final ExternalToModelMap groovyToModelNameMap = Stream.of(new Object[][]{
    {"initWith", exactly(1), INIT_WITH, OTHER},
  }).collect(toModelMap(AbstractProductFlavorDslElement.groovyToModelNameMap));

  @Nullable
  private String methodName;

  public ProductFlavorDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return getExternalToModelMap(converter, groovyToModelNameMap, ktsToModelNameMap);
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    if (element.getFullName().equals("initWith") && element instanceof GradleDslLiteral) {
      GradleReferenceInjection referenceTo = ((GradleDslLiteral)element).getReferenceInjection();
      if (referenceTo != null && referenceTo.getToBeInjected() != null) {
        // Merge properties with the target
        mergePropertiesFrom((GradlePropertiesDslElement)referenceTo.getToBeInjected());
      }
    }

    super.addParsedElement(element);
  }

  @Override
  public void setMethodName(String methodName) {
    this.methodName = methodName;
  }

  @Nullable
  @Override
  public String getMethodName() {
    return methodName;
  }
}
