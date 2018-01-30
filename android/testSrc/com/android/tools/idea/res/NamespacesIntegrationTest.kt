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
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDocumentManager

class NamespacesIntegrationTest : AndroidGradleTestCase() {

  fun testNamespaceChoosing() {
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

  @Suppress("DEPRECATION") // We're calling the same method as layoutlib currently.
  fun testResolver() {
    loadProject(TestProjectPaths.NAMESPACES)
    val layout = VfsUtil.findRelativeFile(myFixture.project.baseDir, "app", "src", "main", "res", "layout", "activity_my.xml")!!
    val resourceResolver = ConfigurationManager.getOrCreateInstance(myModules.appModule).getConfiguration(layout).resourceResolver!!

    fun check(reference: String, resolvesTo: String) {
      assertEquals(
        resolvesTo,
        resourceResolver.resolveValue(null, "text", reference, false)!!.value
      )
    }

    check("@com.example.lib:string/lib_string", resolvesTo = "Hello, this is lib.")
    check("@com.example.lib:string/lib_string_indirect_full", resolvesTo = "Hello, this is lib.")
    check("@com.example.app:string/get_from_lib_full", resolvesTo = "Hello, this is lib.")

    check("@com.example.lib:string/get_from_lib_full", resolvesTo = "@com.example.lib:string/get_from_lib_full")
    check("@com.example.app:string/lib_string", resolvesTo = "@com.example.app:string/lib_string")
  }
}
