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
package org.jetbrains.android.uipreview

import com.android.AndroidProjectTypes
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.layoutlib.LayoutLibrary
import com.android.tools.idea.rendering.AndroidFacetRenderModelModule
import com.android.tools.idea.rendering.IRenderLogger
import com.android.tools.idea.rendering.classloading.loadClassBytes
import com.android.tools.idea.rendering.classloading.loaders.DelegatingClassLoader
import com.android.tools.idea.rendering.classloading.loaders.NameRemapperLoader
import com.android.tools.idea.rendering.classloading.loaders.StaticLoader
import com.android.tools.idea.res.ResourceIdManager.Companion.get
import com.android.tools.idea.testing.ProjectFiles
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.facet.AndroidFacet

class NonTransitiveResourcesLoaderTest : AndroidTestCase() {
  override fun configureAdditionalModules(projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture>,
                                          modules: MutableList<MyAdditionalModuleData>) {
    addModuleWithAndroidFacet(
      projectBuilder,
      modules,
      "lib",
      AndroidProjectTypes.PROJECT_TYPE_LIBRARY,
      false
    )
  }

  override fun setUp() {
    super.setUp()

    val appModule = myFixture.module
    val libModule = getAdditionalModuleByName("lib")!!
    ModuleRootModificationUtil.addDependency(appModule, libModule)

    myFixture.addFileToProject(
      "${getAdditionalModulePath("lib")}/res/values/strings.xml",
      // language=xml
      """
        <resources>
          <string name="lib_name">lib Name</string>
        </resources>
      """.trimIndent()
    )

    myFixture.addFileToProject(
      "res/values/strings.xml",
      // language=xml
      """
        <resources>
          <string name="app_name">app name</string>
        </resources>
      """.trimIndent())

    // A settings.gradle file is needed to trick the IDE into thinking this is a project built using gradle, necessary for removing
    // generated build files from search scope.
    ProjectFiles.createFileInProjectRoot(project, "settings.gradle")

    runUndoTransparentWriteAction {
      Manifest.getMainManifest(myFacet)!!.`package`.value = "com.example.app"
      Manifest.getMainManifest(AndroidFacet.getInstance(libModule)!!)!!.`package`.value = "com.example.lib"
    }
  }

  /**
   * Regression test for b/206862224.
   *
   * This test ensures that, when using non transitive R classes, the compiled R class is looked up even for dependencies and not just
   * the main module.
   * Before the fix for b/206862224, project library dependencies would be considered as regular external libraries and its ids to be
   * non-final. This is incorrect as, with non transitive R classes enabled, the library is still treated as part of the project and the
   * ids inlined (because they are final). The difference is just that the library now has its own R class, but the ids are still inlined.
   */
  fun testNonTransitiveRClassesAreInitializedCorrectly() {
    val staticLoader = StaticLoader(
      org.jetbrains.android.uipreview.nontransitive.app.R::class.java.name to loadClassBytes(
        org.jetbrains.android.uipreview.nontransitive.app.R::class.java),
      org.jetbrains.android.uipreview.nontransitive.lib.R::class.java.name to loadClassBytes(
        org.jetbrains.android.uipreview.nontransitive.lib.R::class.java)
    )
    val delegateClassLoader = DelegatingClassLoader(
      NonTransitiveResourcesLoaderTest::class.java.classLoader,
      NameRemapperLoader(
        staticLoader
      ) {
        it.replace("com.example.lib.R", org.jetbrains.android.uipreview.nontransitive.lib.R::class.java.name)
          .replace("com.example.app.R", org.jetbrains.android.uipreview.nontransitive.app.R::class.java.name)
      }
    )

    // We do not need any of the services offered by LayoutLibrary in this test so just mock it.
    val layoutlib = mock<LayoutLibrary>()
    val viewLoader = ViewLoader(layoutlib, AndroidFacetRenderModelModule(myFacet), IRenderLogger.NULL_LOGGER, null, delegateClassLoader)
    viewLoader.loadAndParseRClassSilently()
    val idManager = get(myModule)
    assertNotNull(idManager)

    assertEquals(0x7f011111,
                 idManager.getCompiledId(ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.STRING, "app_name"))!!.toInt())
    assertEquals(0x7f022222,
                 idManager.getCompiledId(ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.STRING, "lib_name"))!!.toInt())

  }
}