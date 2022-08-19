/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.common.model

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext

/**
 * This interface abstracts out the complexity of [NlModel] and provides sufficient API to associate a model with a [PreviewElement] through
 * a [DataContext].
 */
interface DataContextHolder : Disposable {
  var dataContext: DataContext
}