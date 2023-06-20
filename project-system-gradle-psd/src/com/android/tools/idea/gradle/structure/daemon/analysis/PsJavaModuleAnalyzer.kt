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
package com.android.tools.idea.gradle.structure.daemon.analysis

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.gradle.structure.model.PsIssue
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule
import com.intellij.openapi.Disposable

class PsJavaModuleAnalyzer(parentDisposable: Disposable) : PsModelAnalyzer<PsJavaModule>(parentDisposable) {
  override val supportedModelType: Class<PsJavaModule> = PsJavaModule::class.java

  @UiThread
  override fun analyze(model: PsJavaModule): Sequence<PsIssue> =
    model.dependencies.libraries.asSequence().flatMap { analyzeDeclaredDependency(it) + analyzeDependencyScope(it) } +
    model.dependencies.jars.asSequence().flatMap { analyzeDependencyScope(it) } +
    model.dependencies.modules.asSequence().flatMap { analyzeDependencyScope(it) }
}
