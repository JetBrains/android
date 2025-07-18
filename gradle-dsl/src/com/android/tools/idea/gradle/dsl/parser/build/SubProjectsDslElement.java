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
package com.android.tools.idea.gradle.dsl.parser.build;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.model.GradleBlockModelMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.files.GradleBuildFile;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

public class SubProjectsDslElement extends GradleDslBlockElement {
  public static final PropertiesElementDescription<SubProjectsDslElement> SUBPROJECTS =
    new PropertiesElementDescription<>("subprojects", SubProjectsDslElement.class, SubProjectsDslElement::new);

  @NotNull
  @Override
  public ImmutableMap<String, PropertiesElementDescription<?>> getChildPropertiesElementsDescriptionMap(
    @NotNull GradleDslNameConverter.Kind kind
  ) {
    return GradleBlockModelMap.getElementMap(GradleBuildFile.class, kind);
  }

  public SubProjectsDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }
}
