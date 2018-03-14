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
package com.android.tools.idea.navigator.nodes.ndk.includes.view;

import com.android.builder.model.NativeArtifact;
import com.android.builder.model.NativeSettings;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.function.Function;

/**
 * Data carriage class for IncludesView.
 */
public class NativeIncludes {
  @NotNull
  public final ImmutableList<NativeArtifact> myArtifacts;

  @NotNull
  private final Function<String, NativeSettings> myFindNativeSettingsFunction;

  public NativeIncludes(@NotNull Function<String, NativeSettings> nativeSettings, @NotNull Collection<NativeArtifact> artifacts) {
    this.myFindNativeSettingsFunction = nativeSettings;
    this.myArtifacts = ImmutableList.copyOf(artifacts);
  }

  @NotNull
  public NativeSettings findExpectedSettings(@NotNull String name) {
    return myFindNativeSettingsFunction.apply(name);
  }
}
