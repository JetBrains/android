/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.testing

import com.android.tools.idea.model.MergedManifestManager
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.facet.AndroidFacet

/**
 * Applies [writeCommandActionBody] to the primary manifest of the given [androidFacet],
 * and then forces and blocks on a refresh of the [androidFacet]'s merged manifest.
 */
inline fun updatePrimaryManifest(androidFacet: AndroidFacet, crossinline writeCommandActionBody: Manifest.() -> Unit) {
  runWriteCommandAction(androidFacet.module.project) {
    Manifest.getMainManifest(androidFacet)!!.writeCommandActionBody()
  }
  FileDocumentManager.getInstance().saveAllDocuments()
  MergedManifestManager.getMergedManifest(androidFacet.module).get()
  runInEdtAndWait {
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }
}