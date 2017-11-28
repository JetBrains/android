/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.idea.gradle.dsl.api.android.BuildTypeModel;
import com.android.tools.idea.gradle.dsl.model.android.BuildTypeModelImpl;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElementMap;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class BuildTypesDslElement extends GradleDslElementMap {
  @NonNls public static final String BUILD_TYPES_BLOCK_NAME = "buildTypes";

  public BuildTypesDslElement(@NotNull GradleDslElement parent) {
    super(parent, BUILD_TYPES_BLOCK_NAME);
  }

  @Override
  protected boolean isBlockElement() {
    return true;
  }

  @NotNull
  public List<BuildTypeModel> get() {
    List<BuildTypeModel> result = Lists.newArrayList();
    for (BuildTypeDslElement dslElement : getValues(BuildTypeDslElement.class)) {
      result.add(new BuildTypeModelImpl(dslElement));
    }
    return result;
  }
}
