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
package com.android.tools.idea.res.psi

import com.android.ide.common.resources.configuration.DensityQualifier
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.Density
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.project.DefaultProjectSystem
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.rendering.Locale
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.testing.caret
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlElement
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.dom.resources.ResourceValue
import org.jetbrains.android.dom.wrappers.LazyValueResourceElementWrapper

/**
 * Class to test aspects of [AndroidResourceToPsiResolver].
 */
abstract class AndroidResourceToPsiResolverTest : AndroidTestCase() {

  /**
   * Tests for [AndroidResourceToPsiResolver.getGotoDeclarationFileBasedTargets]
   */
  fun testMultipleDensityDrawable() {
    myFixture.copyFileToProject("dom/resources/icon.png", "res/drawable/icon.png")
    myFixture.copyFileToProject("dom/resources/icon.png", "res/drawable-hdpi/icon.png")
    myFixture.copyFileToProject("dom/resources/icon.png", "res/drawable-xhdpi/icon.png")
    val file = myFixture.addFileToProject(
      "p1/p1/ResourceClass.java",
      //language=JAVA
      """
        package p1.p2;
        public class ResourceClass {
          public void f() {
            int n = R.drawable.ic${caret}on;
          }
        }""".trimIndent())
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val elementAtCaret = myFixture.elementAtCaret
    val fakePsiElement = ResourceReferencePsiElement.create(elementAtCaret)!!
    checkFileDeclarations(fakePsiElement, arrayOf("drawable/icon.png", "drawable-hdpi/icon.png", "drawable-xhdpi/icon.png"))
  }

  fun testLayoutFileResource() {
    myFixture.copyFileToProject("dom/resources/icon.png", "res/mipmap/icon.png")
    myFixture.addFileToProject(
      "res/layout/layout.xml",
      //language=XML
      """
      <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"/>
      """.trimIndent())
    val file = myFixture.addFileToProject(
      "p1/p1/ResourceClass.java",
      //language=JAVA
      """
        package p1.p2;
        public class ResourceClass {
          public void f() {
            int n = R.layout.la${caret}yout;
          }
        }""".trimIndent())
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val elementAtCaret = myFixture.elementAtCaret
    val fakePsiElement = ResourceReferencePsiElement.create(elementAtCaret)!!
    checkFileDeclarations(fakePsiElement, arrayOf("layout/layout.xml"))
  }

  fun testAppFileResource() {
    myFixture.copyFileToProject("dom/resources/icon.png", "res/mipmap/icon.png")
    val file = myFixture.addFileToProject(
      "res/layout/layout.xml",
      //language=XML
      """
       <LinearLayout
          xmlns:android="http://schemas.android.com/apk/res/android"
          android:layout_width="match_parent"
          android:layout_height="match_parent">
          <TextView
              android:layout_width="match_parent"
              android:layout_height="match_parent"
              android:textColor="@mipmap/ic${caret}on"/>
      </LinearLayout>""".trimIndent())
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val elementAtCaret = myFixture.elementAtCaret
    val fakePsiElement = ResourceReferencePsiElement.create(elementAtCaret)!!
    checkFileDeclarations(fakePsiElement, arrayOf("mipmap/icon.png"))
  }

  private fun checkFileDeclarations(fakePsiElement: ResourceReferencePsiElement, expectedFileNames: Array<String>) {
    val context = myFixture.file.findElementAt(myFixture.caretOffset)!!
    val fileBasedResources = AndroidResourceToPsiResolver.getInstance().getGotoDeclarationFileBasedTargets(
      fakePsiElement.resourceReference,
      context
    )
    assertThat(fileBasedResources).hasLength(expectedFileNames.size)
    val fileNames = fileBasedResources.map { it.containingDirectory.name + "/" + it.name }
    assertThat(fileNames).containsExactlyElementsIn(expectedFileNames)
  }

  fun testFrameworkFileResource() {
    val file = myFixture.addFileToProject(
      "res/layout/layout.xml",
      //language=XML
      """
       <LinearLayout
          xmlns:android="http://schemas.android.com/apk/res/android"
          android:layout_width="match_parent"
          android:layout_height="match_parent">
          <TextView
              android:layout_width="match_parent"
              android:layout_height="match_parent"
              android:textColor="@android:color/secondary${caret}_text_dark"/>
      </LinearLayout>""".trimIndent())
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val elementAtCaret = myFixture.elementAtCaret
    val fakePsiElement = ResourceReferencePsiElement.create(elementAtCaret)!!
    checkFileDeclarations(fakePsiElement, arrayOf("color/secondary_text_dark.xml"))
  }

  fun setupDynamicFeatureProject(): PsiElement {
    val dynamicFeatureModuleName = "dynamicfeaturemodule"
    addDynamicFeatureModule(dynamicFeatureModuleName, myModule, myFixture)
    myFixture.addFileToProject(
      "res/values/strings.xml",
      //language=XML
      """
        <resources>
            <string name="app_name">Captures Application</string>
        </resources>
      """.trimIndent())
    myFixture.addFileToProject(
      "$dynamicFeatureModuleName/res/values/strings.xml",
      //language=XML
      """
        <resources>
            <string name="dynamic_name">Captures Application</string>
        </resources>
      """.trimIndent())

    val file = myFixture.addFileToProject(
      "res/values/other_strings.xml",
      //language=XML
      """
        <resources>
            <string name="references">context<caret></string>
        </resources>
      """.trimIndent())
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    return myFixture.elementAtCaret
  }

  private fun addDynamicFeatureModule(moduleName: String, module: Module, fixture: JavaCodeInsightTestFixture) {
    val project = module.project
    val dynamicFeatureModule = PsiTestUtil.addModule(
      project,
      JavaModuleType.getModuleType(),
      moduleName,
      fixture.tempDirFixture.findOrCreateDir(moduleName))
    addAndroidFacet(dynamicFeatureModule)
    val newModuleSystem = object : AndroidModuleSystem by DefaultModuleSystem(module) {
      override fun getDynamicFeatureModules(): List<Module> = listOf(dynamicFeatureModule)
    }
    val newProjectSystem = object : AndroidProjectSystem by DefaultProjectSystem(project) {
      override fun getModuleSystem(module: Module): AndroidModuleSystem = newModuleSystem
    }
    IdeComponents(project).replaceProjectService(ProjectSystemService::class.java, object : ProjectSystemService(project) {
      override val projectSystem = newProjectSystem
    })
  }
}

class ResourceManagerToPsiResolverTest : AndroidResourceToPsiResolverTest() {
  override fun setUp() {
    super.setUp()
    StudioFlags.RESOLVE_USING_REPOS.override(false)
    StudioFlags.NAV_DYNAMIC_SUPPORT.override(true)
  }

  override fun tearDown() {
    StudioFlags.RESOLVE_USING_REPOS.clearOverride()
    StudioFlags.NAV_DYNAMIC_SUPPORT.clearOverride()
    super.tearDown()
  }

  fun testDynamicFeatureModuleResource() {
    setupDynamicFeatureProject()
    val elementAtCaret = myFixture.elementAtCaret
    assertThat(elementAtCaret).isInstanceOf(XmlElement::class.java)
    val appNameReference = AndroidResourceToPsiResolver.getInstance().resolveReference(
      ResourceValue.referenceTo('@', null, "string", "app_name"),
      elementAtCaret as XmlElement,
      elementAtCaret.androidFacet!!)
    assertThat(appNameReference).isNotEmpty()
    with(LazyValueResourceElementWrapper.computeLazyElement(appNameReference[0].element)) {
      assertThat(this).isInstanceOf(XmlAttributeValue::class.java)
      assertThat(this!!.text).isEqualTo("\"app_name\"")
    }
    val dynamicNameReference = AndroidResourceToPsiResolver.getInstance().resolveReference(
      ResourceValue.referenceTo('@', null, "string", "dynamic_name"),
      elementAtCaret,
      elementAtCaret.androidFacet!!)
    assertThat(dynamicNameReference).isEmpty()
    val appNameReferenceIncluded = AndroidResourceToPsiResolver.getInstance()
      .resolveReferenceWithDynamicFeatureModules(
        ResourceValue.referenceTo('@', null, "string", "app_name"),
        elementAtCaret,
        elementAtCaret.androidFacet!!)
    assertThat(appNameReferenceIncluded).isNotEmpty()
    with(LazyValueResourceElementWrapper.computeLazyElement(appNameReferenceIncluded[0].element)) {
      assertThat(this!!.text).isEqualTo("\"app_name\"")
    }
    val dynamicNameReferenceIncluded = AndroidResourceToPsiResolver.getInstance()
      .resolveReferenceWithDynamicFeatureModules(
        ResourceValue.referenceTo('@', null, "string", "dynamic_name"),
        elementAtCaret,
        elementAtCaret.androidFacet!!)
    assertThat(dynamicNameReferenceIncluded).isNotEmpty()
    with(LazyValueResourceElementWrapper.computeLazyElement(dynamicNameReferenceIncluded[0].element)) {
      assertThat(this!!.text).isEqualTo("\"dynamic_name\"")
    }
  }
}

class ResourceRepositoryToPsiResolverTest : AndroidResourceToPsiResolverTest() {

  override fun setUp() {
    super.setUp()
    StudioFlags.RESOLVE_USING_REPOS.override(true)
    StudioFlags.NAV_DYNAMIC_SUPPORT.override(true)
  }

  override fun tearDown() {
    StudioFlags.RESOLVE_USING_REPOS.clearOverride()
    StudioFlags.NAV_DYNAMIC_SUPPORT.clearOverride()
    super.tearDown()
  }

  fun testDynamicFeatureModuleResource() {
    setupDynamicFeatureProject()
    val elementAtCaret = myFixture.elementAtCaret
    assertThat(elementAtCaret).isInstanceOf(XmlElement::class.java)
    val appNameReference = AndroidResourceToPsiResolver.getInstance().resolveReference(
      ResourceValue.referenceTo('@', null, "string", "app_name"),
      elementAtCaret as XmlElement,
      elementAtCaret.androidFacet!!)
    assertThat(appNameReference).isNotEmpty()
    with((appNameReference[0].element as ResourceReferencePsiElement).resourceReference) {
      assertThat(this.name).isEqualTo("app_name")
    }
    val dynamicNameReference = AndroidResourceToPsiResolver.getInstance().resolveReference(
      ResourceValue.referenceTo('@', null, "string", "dynamic_name"),
      elementAtCaret,
      elementAtCaret.androidFacet!!)
    assertThat(dynamicNameReference).isEmpty()
    val appNameReferenceIncluded = AndroidResourceToPsiResolver.getInstance()
      .resolveReferenceWithDynamicFeatureModules(
        ResourceValue.referenceTo('@', null, "string", "app_name"),
        elementAtCaret,
        elementAtCaret.androidFacet!!)
    assertThat(appNameReferenceIncluded).isNotEmpty()
    with((appNameReferenceIncluded[0].element as ResourceReferencePsiElement).resourceReference) {
      assertThat(this.name).isEqualTo("app_name")
    }
    val dynamicNameReferenceIncluded = AndroidResourceToPsiResolver.getInstance()
      .resolveReferenceWithDynamicFeatureModules(
        ResourceValue.referenceTo('@', null, "string", "dynamic_name"),
        elementAtCaret,
        elementAtCaret.androidFacet!!)
    assertThat(dynamicNameReferenceIncluded).isNotEmpty()
    with((dynamicNameReferenceIncluded[0].element as ResourceReferencePsiElement).resourceReference) {
      assertThat(this.name).isEqualTo("dynamic_name")
    }
  }

  fun testBestGotoDeclarationTargetDensity() {
    myFixture.copyFileToProject("dom/resources/icon.png", "res/drawable/icon.png")
    myFixture.copyFileToProject("dom/resources/icon.png", "res/drawable-hdpi/icon.png")
    myFixture.copyFileToProject("dom/resources/icon.png", "res/drawable-xhdpi/icon.png")
    val file = myFixture.addFileToProject(
      "p1/p1/ResourceClass.java",
      //language=JAVA
      """
        package p1.p2;
        public class ResourceClass {
          public void f() {
            int n = R.drawable.ic${caret}on;
          }
        }""".trimIndent())
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val elementAtCaret = myFixture.elementAtCaret
    val fakePsiElement = ResourceReferencePsiElement.create(elementAtCaret)!!
    val context = myFixture.file.findElementAt(myFixture.caretOffset)!!

    // Check picking the correct XHDPI density
    checkDensityConfiguration(fakePsiElement, context, Density.XHIGH, "drawable-xhdpi/icon.png")

    // Check picking the correct HDPI density
    checkDensityConfiguration(fakePsiElement, context, Density.HIGH, "drawable-hdpi/icon.png")

    // No correct XXXHDPI exists, check that it picks the next best matching DPI, which is XHDPI
    checkDensityConfiguration(fakePsiElement, context, Density.XXXHIGH, "drawable-xhdpi/icon.png")
  }

  fun testBestGotoDeclarationTargetString() {
    val stringsContent = """
      <resources>
        <string name="example">String Example</string>
      </resources>
    """.trimIndent()
    myFixture.addFileToProject("res/values/strings.xml", stringsContent)
    myFixture.addFileToProject("res/values-no/strings.xml", stringsContent)
    myFixture.addFileToProject("res/value-en/strings.xml", stringsContent)
    myFixture.addFileToProject("res/values-hdpi/strings.xml", stringsContent)
    val file = myFixture.addFileToProject(
      "p1/p1/ResourceClass.java",
      //language=JAVA
      """
        package p1.p2;
        public class ResourceClass {
          public void f() {
            int n = R.string.ex${caret}ample;
          }
        }""".trimIndent())
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val elementAtCaret = myFixture.elementAtCaret
    val fakePsiElement = ResourceReferencePsiElement.create(elementAtCaret)!!
    val context = myFixture.file.findElementAt(myFixture.caretOffset)!!

    // Check picking the hdpi density
    checkDensityConfiguration(fakePsiElement, context, Density.HIGH, "values-hdpi/strings.xml")

    // Check picking the correct Norwegian locale
    checkLocaleConfiguration(fakePsiElement, context, Locale.create("no"), "values-no/strings.xml")

    // No french localed exists for the string resource, check the default is selected instead.
    checkLocaleConfiguration(fakePsiElement, context, Locale.create("fr"), "values/strings.xml")
  }

  private fun checkLocaleConfiguration(resourceReferencePsiElement: ResourceReferencePsiElement, context: PsiElement, locale: Locale, expectedFileName: String) {
    val folderConfiguration = FolderConfiguration()
    folderConfiguration.localeQualifier = locale.qualifier
    val defaultConfigurationFile = ResourceRepositoryToPsiResolver.getBestGotoDeclarationTarget(
      resourceReferencePsiElement.resourceReference,
      context,
      folderConfiguration
    )!!.containingFile
    assertThat(defaultConfigurationFile.containingDirectory.name + "/" + defaultConfigurationFile.name).isEqualTo(expectedFileName)
  }

  private fun checkDensityConfiguration(resourceReferencePsiElement: ResourceReferencePsiElement, context: PsiElement, density: Density, expectedFileName: String) {
    val folderConfiguration = FolderConfiguration()
    folderConfiguration.densityQualifier = DensityQualifier(density)
    val defaultConfigurationFile = ResourceRepositoryToPsiResolver.getBestGotoDeclarationTarget(
      resourceReferencePsiElement.resourceReference,
      context,
      folderConfiguration
    )!!.containingFile
    assertThat(defaultConfigurationFile.containingDirectory.name + "/" + defaultConfigurationFile.name).isEqualTo(expectedFileName)
  }

}