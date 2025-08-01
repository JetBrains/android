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

import com.android.tools.idea.gradle.dsl.api.android.CompileSdkAddonModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CompileSdkAddonModelImpl implements CompileSdkAddonModel {
  private final GradleDslMethodCall myMethodCall;

  public CompileSdkAddonModelImpl(@NotNull GradleDslMethodCall methodCall) {
    myMethodCall = methodCall;
  }

  @Override
  public @NotNull ResolvedPropertyModel getVersion() {
    return GradlePropertyModelBuilder.create(myMethodCall.getArguments().get(2)).buildResolved();
  }

  @Override
  public void delete() {
    myMethodCall.delete();
  }

  @Override
  public @Nullable String toHash() {
    String vendorName = getVendorName().valueAsString();
    String addonName = getAddonName().valueAsString();
    Integer apiLevel = getVersion().toInt();
    return vendorName + ":" + addonName + ":" + apiLevel;
  }

  @Override
  public @Nullable Integer toInt() {
    return null;
  }

  @Override
  public @NotNull ResolvedPropertyModel getVendorName() {
    return GradlePropertyModelBuilder.create(myMethodCall.getArguments().get(0)).buildResolved();
  }

  @Override
  public @NotNull ResolvedPropertyModel getAddonName() {
    return GradlePropertyModelBuilder.create(myMethodCall.getArguments().get(1)).buildResolved();

  }
}
