/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.api;

import com.android.tools.idea.gradle.dsl.api.ext.ExtModel;

public interface GradleVersionCatalogModel extends GradleFileModel {
  // TODO(b/200280395): these might not make sense as ExtModels, though that is the closest thing we currently have (an
  //  arbitrary-sized collection of arbitrary named Dsl values).  The ExtModel for versions in particular might be doing double
  //  duty in order to support exposing its contents as PsVariables.
  ExtModel libraries();

  ExtModel plugins();

  ExtModel versions();
}
