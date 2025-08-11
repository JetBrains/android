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
import com.android.tools.idea.projectsystem.gradle.getMainModule
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.util.androidFacet
import com.android.tools.res.ResourceNamespacing
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class NamespacesIntegrationTest {
  val projectRule = AndroidGradleProjectRule()
  @get:Rule
  val rule = RuleChain.outerRule(projectRule).around(EdtRule())
  val project by lazy { projectRule.project }
  val fixture by lazy { projectRule.fixture }

  private val appPackageName = "com.example.app"
  private val libPackageName = "com.example.lib"
  private val otherLibPackageName = "com.example.otherlib"

  private fun getMainAndroidFacet() = project.findAppModule().getMainModule().androidFacet!!

  @Test
  fun testNamespaceChoosing() {
    projectRule.loadProject(TestProjectPaths.NAMESPACES)
    val resourceRepositoryManager = StudioResourceRepositoryManager.getInstance(getMainAndroidFacet())
    assertThat(resourceRepositoryManager.namespacing).isEqualTo(ResourceNamespacing.REQUIRED)
    assertThat(resourceRepositoryManager.namespace.packageName).isEqualTo("com.example.app")
    assertThat(ProjectNamespacingStatusService.getInstance(project).namespacesUsed).isTrue()

    WriteCommandAction.runWriteCommandAction(project) {
      fixture.openFileInEditor(VfsUtil.findRelativeFile(fixture.project.baseDir, "app", "build.gradle")!!)
      val manifest = fixture.editor.document
      manifest.setText(manifest.text.replace(appPackageName, "com.example.change"))
      PsiDocumentManager.getInstance(project).commitDocument(manifest)
    }

    // Changing module package name now requires a sync event to propagate to Resource Repositories and R classes.
    projectRule.requestSyncAndWait()
    assertThat(resourceRepositoryManager.namespace.packageName).isEqualTo("com.example.change")
  }

  @Test
  fun testNonNamespaced() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION)
    val resourceRepositoryManager = StudioResourceRepositoryManager.getInstance(getMainAndroidFacet())
    assertThat(resourceRepositoryManager.namespacing).isEqualTo(ResourceNamespacing.DISABLED)
    assertThat(resourceRepositoryManager.namespace).isSameAs(ResourceNamespace.RES_AUTO)
    assertThat(ProjectNamespacingStatusService.getInstance(project).namespacesUsed).isFalse()
  }

  @Test
  fun testResolver() {
    projectRule.loadProject(TestProjectPaths.NAMESPACES)
    val layout = VfsUtil.findRelativeFile(fixture.project.baseDir, "app", "src", "main", "res", "layout", "simple_strings.xml")!!
    val resourceResolver = ConfigurationManager.getOrCreateInstance(getMainAndroidFacet().module)
      .getConfiguration(layout).resourceResolver
    val appNs = StudioResourceRepositoryManager.getInstance(getMainAndroidFacet()).namespace

    fun check(reference: String, resolvesTo: String) {
      assertThat(resourceResolver.resolveResValue(ResourceValueImpl(appNs, ResourceType.STRING, "dummy", reference))!!.value)
        .isEqualTo(resolvesTo)
    }

    check("@$libPackageName:string/lib_string", resolvesTo = "Hello, this is lib.")
    check("@$libPackageName:string/lib_string_indirect_full", resolvesTo = "Hello, this is lib.")
    check("@$appPackageName:string/get_from_lib_full", resolvesTo = "Hello, this is lib.")

    check("@$libPackageName:string/get_from_lib_full", resolvesTo = "@$libPackageName:string/get_from_lib_full")
    check("@$appPackageName:string/lib_string", resolvesTo = "@$appPackageName:string/lib_string")
  }

  @Test
  fun testAppResources() {
    projectRule.loadProject(TestProjectPaths.NAMESPACES)
    val appResources = StudioResourceRepositoryManager.getInstance(getMainAndroidFacet()).appResources

    assertThat(appResources.namespaces).containsExactly(
      ResourceNamespace.fromPackageName(appPackageName),
      ResourceNamespace.fromPackageName(libPackageName),
      ResourceNamespace.fromPackageName(otherLibPackageName),
      ResourceNamespace.TOOLS
    )
  }
}
