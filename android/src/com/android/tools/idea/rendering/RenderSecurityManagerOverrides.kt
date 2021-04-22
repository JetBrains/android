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
@file:JvmName("RenderPropertiesAccessUtil")
package com.android.tools.idea.rendering

import com.intellij.openapi.extensions.ExtensionPointName

/**
 * Extension point for providing build-system-specific overrides for select
 * [RenderSecurityManager] methods.
 */
interface RenderSecurityManagerOverrides {
  fun allowsPropertiesAccess(): Boolean
  fun allowsLibraryLinking(lib: String): Boolean
}

private val EP_NAME: ExtensionPointName<RenderSecurityManagerOverrides> =
  ExtensionPointName("com.android.rendering.renderSecurityManagerOverrides")

/**
 * Returns true if any registered [RenderSecurityManagerOverrides] extension allows
 * system property access during layout rendering, false otherwise.
 */
fun isPropertyAccessAllowed() = EP_NAME.extensions.any { it.allowsPropertiesAccess() }


/**
 * Returns true if any registered [RenderSecurityManagerOverrides] extension allows
 * linkage of the library [lib], false otherwise.
 */
fun isLibraryLinkingAllowed(lib: String) = EP_NAME.extensions.any { it.allowsLibraryLinking(lib) }