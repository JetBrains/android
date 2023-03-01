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
package com.android.tools.idea.uibuilder.scene

import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.idea.rendering.RenderMergedManifest
import com.android.tools.idea.rendering.RenderModelManifest
import com.android.tools.idea.rendering.RenderModelModule

/** Studio specific [RenderModelModule] implementation for testing. */
internal class TestRenderModelModule(
  private val delegate: RenderModelModule
) : RenderModelModule by delegate {
  // For testing, we do not need to wait for the full merged manifest
  override val manifest: RenderModelManifest?
    get() = MergedManifestManager.getMergedManifestSupplier(delegate.ideaModule).now?.let { RenderMergedManifest(it) }
}