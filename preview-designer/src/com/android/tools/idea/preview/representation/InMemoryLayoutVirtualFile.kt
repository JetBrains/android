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
package com.android.tools.idea.preview.representation

import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile

private val FAKE_LAYOUT_RES_DIR = LightVirtualFile("layout")

/**
 * A [LightVirtualFile] defined to allow quickly identifying the given file as an XML that is used as adapter to be able to preview custom
 * entities.
 * The contents of the file only reside in memory and contain some XML that will be passed to Layoutlib.
 *
 * Historically, design tools were only able to preview xml layouts. Each preview corresponded to a single layout and a layout corresponded
 * to a xml file. The internal logic of [com.android.tools.idea.common.model.NlModel] and Layoutlib heavily relies on this and that is why
 * we pretend there is a xml file that backs each preview.
 *
 * TODO(b/227474522): Consider making this generic type so that clients do not need subclass but use a specified generic instead.
 */
open class InMemoryLayoutVirtualFile(
  name: String,
  content: String,
  private val originFileProvider: () -> VirtualFile?
) : LightVirtualFile(name, content), BackedVirtualFile {
  override fun getParent() = FAKE_LAYOUT_RES_DIR

  override fun getOriginFile(): VirtualFile = originFileProvider() ?: this
}