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
package com.android.tools.idea.gradle.dsl.parser.plugins;

import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType.BOOLEAN;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType.STRING;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;

import com.android.tools.idea.gradle.dsl.model.PluginModelImpl;
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElementList;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElementSchema;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class PluginsDslElement extends GradleDslElementList {
  public static final PropertiesElementDescription<PluginsDslElement> PLUGINS =
    new PropertiesElementDescription<>("plugins", PluginsDslElement.class, PluginsDslElement::new, AndroidPluginsDslElementSchema::new);

  public PluginsDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  @Override
  public boolean isBlockElement() {
    return true;
  }

  public static final class AndroidPluginsDslElementSchema extends GradlePropertiesDslElementSchema {
    @Override
    public ImmutableMap<String, PropertiesElementDescription<?>> getAllBlockElementDescriptions(GradleDslNameConverter.Kind kind) {
      return ImmutableMap.of();
    }

    @NotNull
    @Override
    public ExternalToModelMap getPropertiesInfo(GradleDslNameConverter.Kind kind) {
      return Stream.of(new Object[][]{
        {"id", property, new ModelPropertyDescription(PluginModelImpl.ID, STRING), VAR},
        {"apply", property, new ModelPropertyDescription(PluginModelImpl.APPLY, BOOLEAN), VAR},
        {"version", property, new ModelPropertyDescription(PluginModelImpl.VERSION, STRING), VAR},
      }).collect(toModelMap());
    }
  }
}
