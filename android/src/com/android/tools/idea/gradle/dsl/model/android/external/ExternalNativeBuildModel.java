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
package com.android.tools.idea.gradle.dsl.model.android.external;

import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.android.external.CMakeDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.external.NdkBuildDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.dsl.parser.android.external.CMakeDslElement.CMAKE_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.external.NdkBuildDslElement.NDK_BUILD_BLOCK_NAME;

public class ExternalNativeBuildModel extends GradleDslBlockModel {
  public ExternalNativeBuildModel(@NotNull GradlePropertiesDslElement dslElement) {
    super(dslElement);
  }

  @NotNull
  public CMakeModel cmake() {
    CMakeDslElement cMakeDslElement = myDslElement.getProperty(CMAKE_BLOCK_NAME, CMakeDslElement.class);
    if (cMakeDslElement == null) {
      cMakeDslElement = new CMakeDslElement(myDslElement);
      myDslElement.setNewElement(CMAKE_BLOCK_NAME, cMakeDslElement);
    }
    return new CMakeModel(cMakeDslElement);
  }

  @NotNull
  public ExternalNativeBuildModel removeCMake() {
    myDslElement.removeProperty(CMAKE_BLOCK_NAME);
    return this;
  }

  @NotNull
  public NdkBuildModel ndkBuild() {
    NdkBuildDslElement ndkBuildDslElement = myDslElement.getProperty(NDK_BUILD_BLOCK_NAME, NdkBuildDslElement.class);
    if (ndkBuildDslElement == null) {
      ndkBuildDslElement = new NdkBuildDslElement(myDslElement);
      myDslElement.setNewElement(NDK_BUILD_BLOCK_NAME, ndkBuildDslElement);
    }
    return new NdkBuildModel(ndkBuildDslElement);
  }

  @NotNull
  public ExternalNativeBuildModel removeNdkBuild() {
    myDslElement.removeProperty(NDK_BUILD_BLOCK_NAME);
    return this;
  }

}
