/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.memory.adapters.instancefilters;

import com.android.tools.adtui.model.filter.FilterHandler;
import com.android.tools.profilers.memory.adapters.ClassDb;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Helper to filter and return a subset of the input InstanceObject's that match an arbitrary criteria. Note that this is different from
 * the string-based filter (see {@link FilterHandler}) that works at the class/package/callstack level but does not filter individual
 * instances within a class. This helper interface allows us to show a subset of instances that are of interest to the users.
 */
public interface CaptureObjectInstanceFilter {

  @NotNull
  String getDisplayName();

  /**
   * @return high-level summary of the instance filter's function.
   */
  @NotNull
  String getSummaryDescription();

  /**
   * @return A more detailed explanation the instance filter's function if available, null otherwise.
   */
  @Nullable
  String getDetailedDescription();

  /**
   * @return link pointing to further documentation if available, null otherwise.
   */
  @Nullable
  String getDocumentationLink();

  /**
   * @param instances     The set of instances to filter
   * @param classDatabase The class database containing all the classes that the input set of instances belong to
   * @return a subset of instances based on some arbitrary criteria.
   */
  Set<InstanceObject> filter(@NotNull Set<InstanceObject> instances, @NotNull ClassDb classDatabase);
}
