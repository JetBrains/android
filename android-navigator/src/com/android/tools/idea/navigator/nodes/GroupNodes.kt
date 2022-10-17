/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.navigator.nodes

import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile

/**
 * A node that represents a group of files in a merged form.
 */
interface FileGroupNode {
  /**
   * A list of files represented by the node.
   */
  val files: List<PsiFile>

}

/**
 * A node whose children are selected from multiple folders.
 */
interface FolderGroupNode {
  /**
   * A list of folders from which its children are/can be selected.
   */
  val folders: List<PsiDirectory>

}

object GroupNodes {

  @JvmStatic
  fun canRepresent(node: FolderGroupNode, element: Any?): Boolean {
    return node.folders.any { it == element || it.virtualFile == element }
  }

  @JvmStatic
  fun canRepresent(node: FileGroupNode, element: Any?): Boolean {
    return node.files.any { it == element || it.virtualFile == element }
  }

}