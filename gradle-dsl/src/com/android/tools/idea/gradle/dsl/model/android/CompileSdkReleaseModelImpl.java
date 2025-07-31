/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.android;

import com.android.tools.idea.gradle.dsl.api.android.CompileSdkReleaseModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslClosure;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import org.jetbrains.annotations.NotNull;

public class CompileSdkReleaseModelImpl implements CompileSdkReleaseModel {
  public static String MINOR_API_LEVEL = "minorApiLevel";
  public static String SDK_EXTENSION = "sdkExtension";
  private GradleDslMethodCall myMethodCall;
  GradleDslClosure myClosure;

  public CompileSdkReleaseModelImpl(@NotNull GradleDslMethodCall methodCall) {
    myMethodCall = methodCall;
    myClosure = methodCall.getClosureElement();
    if(myClosure == null) myClosure = new GradleDslClosure(methodCall, null, GradleNameElement.empty());
  }


  @Override
  public @NotNull ResolvedPropertyModel getMinorApiLevel() {
    return GradlePropertyModelBuilder.create(myClosure, MINOR_API_LEVEL).buildResolved();
  }

  @Override
  public @NotNull ResolvedPropertyModel getSdkExtension() {
    return GradlePropertyModelBuilder.create(myClosure, SDK_EXTENSION).buildResolved();
  }

  @Override
  public @NotNull ResolvedPropertyModel getVersion() {
    return GradlePropertyModelBuilder.create(myMethodCall.getArguments().get(0)).buildResolved();
  }
}
