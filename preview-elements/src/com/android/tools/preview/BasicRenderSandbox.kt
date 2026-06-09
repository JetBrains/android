/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.preview

import com.android.tools.rendering.security.DenyAllRenderSandbox
import com.android.tools.rendering.security.RenderSandboxDelegate
import com.android.tools.rendering.security.RenderSandboxTransformTrampoline
import com.android.tools.rendering.security.isPropertyAccessAllowed

/** A basic [RenderSandbox] implementation that blocks writes, process execution, and network, but allows reads. */
internal object BasicRenderSandbox : RenderSandboxDelegate(DenyAllRenderSandbox) {
  override fun checkPropertyAccess() {
    if (isPropertyAccessAllowed()) return
    super.checkPropertyAccess()
  }

  override fun checkPropertyRead(propertyName: String) {
    // Allow reading properties
  }

  override fun checkPropertyWrite(propertyName: String) {
    if (isPropertyWriteAllowed(propertyName)) return
    throw SecurityException("Write access not allowed ($propertyName)")
  }

  private fun isPropertyWriteAllowed(name: String): Boolean {
    // Linux sets this on fontmanager load; allow it since even if code points
    // to their own classes they don't get additional privileges, it's just like
    // using reflection
    if (name == "sun.font.fontmanager") return true
    // Toolkit initializations
    if (name.startsWith("sun.awt.") || name.startsWith("apple.awt.")) return true
    // Timezone settings are common and benign
    if (name == "user.timezone") return true
    return false
  }

  override fun checkReflectionInvoke(owner: String, name: String) {
    if (RenderSandboxTransformTrampoline.shouldIntercept(owner, name)) {
      throw SecurityException("Reflection access to restricted method: $owner#$name")
    }
  }

  override fun checkFileRead(absolutePath: String) {
    // Allow all reads for basic sandbox to avoid breaking legitimate data loading
  }

  override fun checkCreateClassLoader() {
    // Allow class loader creation
  }

  override fun checkClassLoad(classFqn: String) {
    // Allow class loading
  }

  override fun checkResourceLoad(resourceName: String) {
    // Allow resource loading
  }
}
