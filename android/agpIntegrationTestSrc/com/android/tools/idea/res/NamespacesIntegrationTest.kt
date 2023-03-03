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

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceValueImpl
import com.android.resources.ResourceType
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.model.Namespacing
import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDocumentManager

class NamespacesIntegrationTest : AndroidGradleTestCase() {
  private val appPackageName = "com.example.app"
  private val libPackageName = "com.example.lib"
  private val otherLibPackageName = "com.example.otherlib"

  private fun getMainAndroidFacet() = project.findAppModule().getMainModule().androidFacet!!

  fun testNamespaceChoosing() {
    loadProject(TestProjectPaths.NAMESPACES)
    val resourceRepositoryManager = StudioResourceRepositoryManager.getInstance(getMainAndroidFacet())
    assertEquals(Namespacing.REQUIRED, resourceRepositoryManager.namespacing)
    assertEquals("com.example.app", resourceRepositoryManager.namespace.packageName)
    assertTrue(ProjectNamespacingStatusService.getInstance(project).namespacesUsed)

    WriteCommandAction.runWriteCommandAction(project) {
      myFixture.openFileInEditor(VfsUtil.findRelativeFile(myFixture.project.baseDir, "app", "build.gradle")!!)
      val manifest = myFixture.editor.document
      manifest.setText(manifest.text.replace(appPackageName, "com.example.change"))
      PsiDocumentManager.getInstance(project).commitDocument(manifest)
    }

    // Changing module package name now requires a sync event to propagate to Resource Repositories and R classes.
    requestSyncAndWait()
    assertEquals("com.example.change", resourceRepositoryManager.namespace.packageName)
  }

  fun testNonNamespaced() {
    loadProject(TestProjectPaths.SIMPLE_APPLICATION)
    val resourceRepositoryManager = StudioResourceRepositoryManager.getInstance(getMainAndroidFacet())
    assertEquals(Namespacing.DISABLED, resourceRepositoryManager.namespacing)
    assertSame(ResourceNamespace.RES_AUTO, resourceRepositoryManager.namespace)
    assertFalse(ProjectNamespacingStatusService.getInstance(project).namespacesUsed)
  }

  fun testResolver() {
    loadProject(TestProjectPaths.NAMESPACES)
    val layout = VfsUtil.findRelativeFile(myFixture.project.baseDir, "app", "src", "main", "res", "layout", "simple_strings.xml")!!
    val resourceResolver = ConfigurationManager.getOrCreateInstance(getMainAndroidFacet().mainModule)
      .getConfiguration(layout).resourceResolver
    val appNs = StudioResourceRepositoryManager.getInstance(getMainAndroidFacet()).namespace

    fun check(reference: String, resolvesTo: String) {
      assertEquals(
        resolvesTo,
        resourceResolver.resolveResValue(ResourceValueImpl(appNs, ResourceType.STRING, "dummy", reference))!!.value
      )
    }

    check("@$libPackageName:string/lib_string", resolvesTo = "Hello, this is lib.")
    check("@$libPackageName:string/lib_string_indirect_full", resolvesTo = "Hello, this is lib.")
    check("@$appPackageName:string/get_from_lib_full", resolvesTo = "Hello, this is lib.")

    check("@$libPackageName:string/get_from_lib_full", resolvesTo = "@$libPackageName:string/get_from_lib_full")
    check("@$appPackageName:string/lib_string", resolvesTo = "@$appPackageName:string/lib_string")
  }

  fun testAppResources() {
    loadProject(TestProjectPaths.NAMESPACES)
    val appResources = StudioResourceRepositoryManager.getInstance(getMainAndroidFacet()).appResources

    assertSameElements(
      appResources.namespaces,
      ResourceNamespace.fromPackageName(appPackageName),
      ResourceNamespace.fromPackageName(libPackageName),
      ResourceNamespace.fromPackageName(otherLibPackageName),
      ResourceNamespace.TOOLS
    )
  }
}
