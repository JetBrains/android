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
package com.android.tools.idea.gradle;

import com.android.builder.model.NativeAndroidProject;
import com.android.tools.idea.gradle.facet.NativeAndroidGradleFacet;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;

public class NativeAndroidGradleModel implements Serializable {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;

  @NotNull
  private final String myModuleName;
  @NotNull
  private final File myRootDirPath;
  @NotNull
  private final NativeAndroidProject myNativeAndroidProject;
  // TODO: Serialize the model using the proxy objects to cache the model data properly.

  @Nullable
  public static NativeAndroidGradleModel get(@NotNull NativeAndroidGradleFacet androidFacet) {
    NativeAndroidGradleModel androidModel = androidFacet.getNativeAndroidGradleModel();
    if (androidModel == null) {
      return null;
    }
    return androidModel;
  }

  @Nullable
  public static NativeAndroidGradleModel get(@NotNull Module module) {
    NativeAndroidGradleFacet facet = NativeAndroidGradleFacet.getInstance(module);
    return facet != null ? get(facet) : null;
  }

  public NativeAndroidGradleModel(@NotNull String moduleName,
                                  @NotNull File rootDirPath,
                                  @NotNull NativeAndroidProject nativeAndroidProject) {
    myModuleName = moduleName;
    myRootDirPath = rootDirPath;
    myNativeAndroidProject = nativeAndroidProject;
  }

  @NotNull
  public String getModuleName() {
    return myModuleName;
  }

  @NotNull
  public File getRootDirPath() {
    return myRootDirPath;
  }

  @NotNull
  public NativeAndroidProject getNativeAndroidProject() {
    return myNativeAndroidProject;
  }
}
