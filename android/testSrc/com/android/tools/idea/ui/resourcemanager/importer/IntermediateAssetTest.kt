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
package com.android.tools.idea.ui.resourcemanager.importer

import com.android.tools.idea.ui.resourcemanager.getPNGFile
import com.google.common.truth.Truth
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.ApplicationRule
import org.junit.Rule
import org.junit.Test
import java.io.File

class IntermediateAssetTest {

  @get:Rule
  val rule = ApplicationRule()

  @Test
  fun intermediateFileHasCorrectData() {
    val file = VfsUtil.findFileByIoFile(getPNGFile(), true)!!
    val intermediateAssetFile = IntermediateAssetFile(file, File("res/dir/path.png"))
    with(intermediateAssetFile) {
      Truth.assertThat(source).isEqualTo(file)
      Truth.assertThat(target).isEqualTo(File("res/dir/path.png"))
      Truth.assertThat(path).isEqualTo("res/dir/path.png")
      Truth.assertThat(String(contentsToByteArray())).isEqualTo(String(file.contentsToByteArray()))
      Truth.assertThat(parent).isEqualTo(null) // The parent does not exist on disk so null is returned
      Truth.assertThat(inputStream.read()).isEqualTo(file.inputStream.read()) // The parent does not exist on disk so null is returned
    }
  }

  @Test
  fun parentNotNullIfExist() {
    val file = VfsUtil.findFileByIoFile(getPNGFile(), true)!!
    val intermediateAssetFile = IntermediateAssetFile(file, File(file.parent.path, "path.png"))
    Truth.assertThat(intermediateAssetFile.parent).isEqualTo(file.parent)
  }
}