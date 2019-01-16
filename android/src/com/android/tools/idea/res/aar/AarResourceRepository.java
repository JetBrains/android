/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.res.aar;

import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resource repository containing resources of an Android library (AAR).
 */
public interface AarResourceRepository extends SingleNamespaceResourceRepository {
  /**
   * Returns the name of the library, or null if this is a framework resource repository.
   */
  @Nullable
  String getLibraryName();

  /**
   * Returns the name of this resource repository to display in the UI.
   */
  @NotNull
  String getDisplayName();
}
