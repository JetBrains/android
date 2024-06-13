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

import com.android.tools.adtui.webp.WebpNativeLibHelper
import com.android.tools.rendering.RenderService
import com.android.tools.rendering.security.RenderSecurityException
import com.android.tools.rendering.security.RenderSecurityManager
import com.android.tools.rendering.security.RenderSecurityManagerDefaults
import kotlin.io.path.pathString

/** Studio-specific [RenderSecurityManager]. */
class StudioRenderSecurityManager(sdkPath: String?, projectPath: String?, restrictReads: Boolean) :
  RenderSecurityManager(
    sdkPath,
    projectPath,
    restrictReads,
    RenderSecurityManagerDefaults.getDefaultAllowedPaths(),
    RenderService::isRenderThread
  ) {
  override fun checkLink(lib: String) {
    // Allow linking with relative paths
    // Needed to for example load the "fontmanager" library from layout lib (from the
    // BiDiRenderer's layoutGlyphVector call
    if (isRelevant && (lib.indexOf('/') != -1 || lib.indexOf('\\') != -1)) {
      if (lib.startsWith(System.getProperty("java.home"))) {
        return  // Allow loading JRE libraries
      }
      // Allow loading webp library
      if (lib == WebpNativeLibHelper.getLibLocation()?.pathString) {
        return
      }
      throw RenderSecurityException.create("Link", lib)
    }
  }
}
