/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.rendering

import com.android.ide.common.rendering.api.RenderResources
import com.intellij.openapi.Disposable
import com.intellij.psi.xml.XmlFile

/**
 * An interface proving access to the general environment specific functionality, primarily related to Intellij IDEA. The interface itself
 * is Intellij/Studio agnostic so that when used outside of studio this can be easily stubbed/nooped or implemented differently.
 *
 * In the future, functionality related to DumbService, read/writeAction etc. can be added here.
 */
interface EnvironmentContext {
  val parentDisposable: Disposable

  fun hasLayoutlibCrash(): Boolean

  val runnableFixFactory: RenderProblem.RunnableFixFactory

  fun createIncludeReference(xmlFile: XmlFile, resolver: RenderResources): IncludeReference
}