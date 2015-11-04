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
package com.android.tools.idea.gradle.dsl.parser.dependencies;

import com.android.tools.idea.gradle.dsl.parser.elements.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class DependenciesDslElement extends GradlePropertiesDslElement {
  public static String NAME = "dependencies";

  public DependenciesDslElement(@Nullable GradleDslElement parent) {
    super(parent, null, NAME);
  }

  @Override
  public void addDslElement(@NotNull String configurationName, @NotNull GradleDslElement dependency) {
    // Treat all literal and literal map as dependencies
    // TODO analysis the configuration in context so we could exclude those are not dependencies
    // TODO deal with list of dependencies such as:
    //  runtime(
    //    [group: 'com.google.code.guice', name: 'guice', version: '1.0'],
    //    [group: 'com.google.guava', name: 'guava', version: '18.0'],
    //    [group: 'com.android.support', name: 'appcompat-v7', version: '22.1.1']
    //  )
    // TODO deal with element that has closable such as:
    // compile("xxx") { exclude "yyy" }

    if (dependency instanceof GradleDslLiteral || dependency instanceof GradleDslLiteralMap) {
      GradleDslElementList elementList = getProperty(configurationName, GradleDslElementList.class);
      if (elementList == null) {
        elementList = new GradleDslElementList(this, configurationName);
        super.addDslElement(configurationName, elementList);
      }
      elementList.addParsedElement(dependency);
    }
  }

  @Override
  public boolean isBlockElement() {
    return true;
  }
}
