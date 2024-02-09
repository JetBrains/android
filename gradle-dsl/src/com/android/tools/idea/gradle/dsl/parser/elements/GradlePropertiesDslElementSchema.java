/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.elements;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

/**
 * Goal is to have light schema for GradlePropertiesDslElement.
 * Without creating element itself, we can get possible element children - blocks and properties
 */
public abstract class GradlePropertiesDslElementSchema {
  public abstract @NotNull @Unmodifiable Map<String, PropertiesElementDescription> getBlockElementDescriptions();

  public @Nullable PropertiesElementDescription getBlockElementDescription(String name) {
    return getBlockElementDescriptions().get(name);
  }

  public abstract @NotNull ExternalToModelMap getPropertiesInfo(GradleDslNameConverter.Kind kind);
}
