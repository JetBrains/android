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
package com.android.tools.idea.res

import com.android.builder.model.AaptOptions.Namespacing.DISABLED
import com.android.builder.model.AaptOptions.Namespacing.REQUIRED
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDocumentManager

class NamespacesIntegrationTest : AndroidGradleTestCase() {

  // TODO(b/72488141)
  fun ignore_testNamespaceChoosing() {
    loadProject(TestProjectPaths.NAMESPACES)
    val resourceRepositoryManager = ResourceRepositoryManager.getOrCreateInstance(myAndroidFacet)
    assertEquals(REQUIRED, resourceRepositoryManager.namespacing)
    assertEquals("com.example.app", resourceRepositoryManager.namespace.packageName)

    WriteCommandAction.runWriteCommandAction(project, {
      myFixture.openFileInEditor(VfsUtil.findRelativeFile(myFixture.project.baseDir, "app", "src", "main", "AndroidManifest.xml")!!)
      val manifest = myFixture.editor.document
      manifest.setText(manifest.text.replace("com.example.app", "com.example.change"))
      PsiDocumentManager.getInstance(project).commitDocument(manifest)
    })

    assertEquals("com.example.change", resourceRepositoryManager.namespace.packageName)
  }

  fun testNonNamespaced() {
    loadProject(TestProjectPaths.SIMPLE_APPLICATION)
    val resourceRepositoryManager = ResourceRepositoryManager.getOrCreateInstance(myAndroidFacet)
    assertEquals(DISABLED, resourceRepositoryManager.namespacing)
    assertSame(ResourceNamespace.RES_AUTO, resourceRepositoryManager.namespace)
  }
}
