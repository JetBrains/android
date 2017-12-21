/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.api.ext;

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;

/**
 * Represents the property type for a {@link GradleDslElement}.
 * <ul>
 *   <li>{@code REGULAR} - this is a Gradle property, e.g "ext.prop1 = 'value'"</li>
 *   <li>{@code VARIABLE} - this is a DSL variable, e.g "def prop1 = 'value'"</li>
 *   <li>{@code DERIVED} - this is a internal property derived from values an a map of list, e.g property "key"
 *                          in "prop1 = ["key" : 'value']"</li>
 *   <li>{@code GLOBAL}   - this is a global property defined by Gradle e.g projectDir</li>
 * </ul>
 */
public enum PropertyType {
  REGULAR,
  VARIABLE,
  DERIVED,
  GLOBAL,
}
