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

import static com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter.Kind;

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElementSchema;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DefaultConfigDslElement extends AbstractProductFlavorDslElement {
  public static final PropertiesElementDescription<DefaultConfigDslElement> DEFAULT_CONFIG =
    new PropertiesElementDescription<>("defaultConfig",
                                       DefaultConfigDslElement.class,
                                       DefaultConfigDslElement::new,
                                       DefaultConfigDslElementSchema::new);

  public DefaultConfigDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  public static final class DefaultConfigDslElementSchema extends GradlePropertiesDslElementSchema {
    @NotNull
    @Override
    protected ImmutableMap<String, PropertiesElementDescription> getAllBlockElementDescriptions() {
      return CHILD_PROPERTIES_ELEMENTS_MAP;
    }

    @Override
    @NotNull
    public ExternalToModelMap getPropertiesInfo(Kind kind) {
      return getExternalProperties(kind, groovyToModelNameMap, ktsToModelNameMap, declarativeToModelNameMap);
    }

    @Nullable
    @Override
    public String getAgpDocClass() {
      return "com.android.build.api.dsl.DefaultConfig";
    }
  }
}
