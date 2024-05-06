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
package com.android.tools.idea.projectsystem

import com.intellij.openapi.extensions.ExtensionPointName

/**
 * This interface is to be extended by concrete project systems with a suitable implementation of
 * [isApplicable], and also extended by IDE feature modules defining interface methods to modify
 * the project (or query it, but usually this should not be necessary for queries).  These
 * interfaces should be implemented in build-system-specific modules corresponding for the respective
 * IDE feature.
 */
interface Token {
  fun isApplicable(projectSystem: AndroidProjectSystem): Boolean
}

/**
 * A marker interface for project system tokens.
 *
 * The purpose of this interface is to distinguish tokens that are supposed to be implemented for various project systems from tokens
 * representing project systems.
 */
interface ProjectSystemToken : Token

/** Returns an instance of token [T] such that it is suitable for [this] project system. */
inline fun <reified T : Token> AndroidProjectSystem.getToken(extensionPointName: ExtensionPointName<T>): T {
  return getTokenOrNull(extensionPointName) ?: error("${T::class.java} token is not available for $this")
}

/** Returns an instance of token [T] such that it is suitable for [this] project system. */
inline fun <reified T : Token> AndroidProjectSystem.getTokenOrNull(extensionPointName: ExtensionPointName<T>): T? {
  return extensionPointName.getExtensions(project).singleOrNull { it.isApplicable(this) }
}
