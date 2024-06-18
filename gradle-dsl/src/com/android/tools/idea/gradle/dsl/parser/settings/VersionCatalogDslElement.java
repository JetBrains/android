/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.settings;

import static com.android.tools.idea.gradle.dsl.model.settings.VersionCatalogModelImpl.FROM;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.settings.VersionCatalogsDslElement.calculateDefaultCatalogName;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslNamedDomainElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VersionCatalogDslElement extends GradleDslBlockElement implements GradleDslNamedDomainElement {
  public static final PropertiesElementDescription<VersionCatalogDslElement> VERSION_CATALOG =
    new PropertiesElementDescription<>(null, VersionCatalogDslElement.class, VersionCatalogDslElement::new);

  private @Nullable String methodName;

  private static final ExternalToModelMap externalToModelMap = Stream.of(new Object[][] {
    {"from", exactly(1), FROM, SET},
  }).collect(toModelMap());

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return externalToModelMap;
  }

  public VersionCatalogDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  @Override
  public boolean isInsignificantIfEmpty() {
    GradleDslElement grandParent = getParent()!=null ? getParent().getParent() : null;
    String name = calculateDefaultCatalogName(grandParent);
    return getName().equals(name);
  }

  @Override
  public @Nullable String getMethodName() {
    return this.methodName;
  }

  @Override
  public void setMethodName(@Nullable String value) {
    this.methodName = value;
  }
}
