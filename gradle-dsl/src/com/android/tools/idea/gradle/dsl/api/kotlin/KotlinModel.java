/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.api.kotlin;

import com.android.tools.idea.gradle.dsl.api.android.BuildTypeModel;
import com.android.tools.idea.gradle.dsl.api.android.SourceSetModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.util.GradleBlockModel;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public interface KotlinModel extends GradleBlockModel {
  @NotNull
  ResolvedPropertyModel jvmToolchain();

  @NotNull
  List<KotlinSourceSetModel> sourceSets();

  @NotNull
  KotlinSourceSetModel addSourceSet(@NotNull String name);

  void removeSourceSet(@NotNull String name);
}