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
package com.android.tools.idea.gradle.dsl.model.android.productFlavors;


import com.android.tools.idea.gradle.dsl.api.android.productFlavors.ExternalNativeBuildOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.productFlavors.externalNativeBuild.CMakeOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.productFlavors.externalNativeBuild.NdkBuildOptionsModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.android.productFlavors.externalNativeBuild.CMakeOptionsModelImpl;
import com.android.tools.idea.gradle.dsl.model.android.productFlavors.externalNativeBuild.NdkBuildOptionsModelImpl;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.ExternalNativeBuildOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.externalNativeBuild.CMakeOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.externalNativeBuild.NdkBuildOptionsDslElement;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.dsl.parser.android.externalNativeBuild.CMakeDslElement.CMAKE_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.externalNativeBuild.NdkBuildDslElement.NDK_BUILD_BLOCK_NAME;

public class ExternalNativeBuildOptionsModelImpl extends GradleDslBlockModel implements ExternalNativeBuildOptionsModel {
  public ExternalNativeBuildOptionsModelImpl(@NotNull ExternalNativeBuildOptionsDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public CMakeOptionsModel cmake() {
    CMakeOptionsDslElement cMakeOptionsDslElement = myDslElement.getPropertyElement(CMAKE_BLOCK_NAME, CMakeOptionsDslElement.class);
    if (cMakeOptionsDslElement == null) {
      cMakeOptionsDslElement = new CMakeOptionsDslElement(myDslElement);
      myDslElement.setNewElement(CMAKE_BLOCK_NAME, cMakeOptionsDslElement);
    }
    return new CMakeOptionsModelImpl(cMakeOptionsDslElement);
  }

  @Override
  @NotNull
  public ExternalNativeBuildOptionsModel removeCMake() {
    myDslElement.removeProperty(CMAKE_BLOCK_NAME);
    return this;
  }

  @Override
  @NotNull
  public NdkBuildOptionsModel ndkBuild() {
    NdkBuildOptionsDslElement ndkBuildOptionsDslElement =
      myDslElement.getPropertyElement(NDK_BUILD_BLOCK_NAME, NdkBuildOptionsDslElement.class);
    if (ndkBuildOptionsDslElement == null) {
      ndkBuildOptionsDslElement = new NdkBuildOptionsDslElement(myDslElement);
      myDslElement.setNewElement(NDK_BUILD_BLOCK_NAME, ndkBuildOptionsDslElement);
    }
    return new NdkBuildOptionsModelImpl(ndkBuildOptionsDslElement);
  }

  @Override
  @NotNull
  public ExternalNativeBuildOptionsModel removeNdkBuild() {
    myDslElement.removeProperty(NDK_BUILD_BLOCK_NAME);
    return this;
  }
}
