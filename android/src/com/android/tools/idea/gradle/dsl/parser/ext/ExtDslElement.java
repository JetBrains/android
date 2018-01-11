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
package com.android.tools.idea.gradle.dsl.parser.ext;

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the extra user-defined properties defined in the Gradle file.
 * <p>
 * For more details please read
 * <a href="https://docs.gradle.org/current/userguide/writing_build_scripts.html#sec:extra_properties">Extra Properties</a>.
 * </p>
 */
public final class ExtDslElement extends GradleDslBlockElement {
  @NonNls public static final String EXT_BLOCK_NAME = "ext";

  public ExtDslElement(@Nullable GradleDslElement parent) {
    super(parent, EXT_BLOCK_NAME);
  }

  /*
     The following method that sets values on the ExtDslElement are overwritten from the GradlePropertiesDslElement,
     this is two ensure that any properties added to the Ext block use the equals notation, "prop1 = 'value'" as apposed
     to the application notation. "prop1 'value'", the latter is not valid.
   */
  @Override
  @NotNull
  public GradleDslElement setNewLiteral(@NotNull String property, @NotNull Object object) {
    GradleDslElement element = super.setNewLiteral(property, object);
    element.setUseAssignment(true);
    return element;
  }
}
