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
package com.android.tools.idea.sdk.install.patch;

import com.android.repository.api.LocalPackage;
import com.android.repository.api.PackageOperation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Common interface for the different patch {@link PackageOperation}s.
 */
interface PatchOperation extends PackageOperation {

  /**
   * The existing installed package that will be modified by this operation.
   */
  @Nullable
  LocalPackage getExisting();

  /**
   * The patcher package, which we'll use to generate (if necessary) and apply the patch.
   */
  @NotNull
  LocalPackage getPatcher();

  /**
   * The location of the files representing the result of the patch that will be generated.
   */
  @NotNull
  File getNewFilesRoot();

  /**
   * The name to show for the new version.
   */
  @NotNull
  String getNewVersionName();
}
