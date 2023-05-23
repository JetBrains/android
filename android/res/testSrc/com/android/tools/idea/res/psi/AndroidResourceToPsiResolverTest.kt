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

import com.android.AndroidProjectTypes
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.resources.configuration.DensityQualifier
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.Density
import com.android.resources.ResourceType
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.project.DefaultProjectSystem
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.ide.common.resources.Locale
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlElement
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.dom.resources.ResourceValue

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

  fun testFrameworkFileResourceJava() {
    val file = myFixture.addFileToProject(
      "src/p1/p2/MyView.java",  //language=JAVA
      """
        package p1.p2;
        public class MyTest {
            public MyTest() {
                int attribute = android.R.drawable.btn_${caret}default;
            }
        }""".trimIndent())
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val elementAtCaret = myFixture.elementAtCaret
    val fakePsiElement = ResourceReferencePsiElement.create(elementAtCaret)!!
    assertThat(fakePsiElement.resourceReference)
      .isEqualTo(ResourceReference(ResourceNamespace.ANDROID, ResourceType.DRAWABLE, "btn_default"))
    checkFileDeclarations(fakePsiElement, arrayOf("drawable/btn_default.xml"))
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
    addAndroidFacetAndSdk(dynamicFeatureModule)
    val newModuleSystem = object : AndroidModuleSystem by DefaultModuleSystem(module) {
      override fun getDynamicFeatureModules(): List<Module> = listOf(dynamicFeatureModule)
    }
    val newProjectSystem = object : AndroidProjectSystem by DefaultProjectSystem(project) {
      override fun getModuleSystem(module: Module): AndroidModuleSystem = newModuleSystem
    }
    ProjectSystemService.getInstance(project).replaceProjectSystemForTests(newProjectSystem)
  }
}

class ResourceRepositoryToPsiResolverTest : AndroidResourceToPsiResolverTest() {

  private val MODULE_WITH_DEPENDENCY = "MODULE_WITH_DEPENDENCY"
  private val MODULE_WITHOUT_DEPENDENCY = "MODULE_WITHOUT_DEPENDENCY"
  private lateinit var MAIN_MODULE_COLOR_FILE : VirtualFile
  private lateinit var MAIN_MODULE_USAGE_COLOR_FILE : VirtualFile
  private lateinit var MODULE_WITH_DEPENDENCY_COLOR_FILE : VirtualFile
  private lateinit var MODULE_WITHOUT_DEPENDENCY_COLOR_FILE : VirtualFile

  override fun setUp() {
    super.setUp()

    MAIN_MODULE_COLOR_FILE = myFixture.addFileToProject("/res/values/colors.xml", COLORS_XML).virtualFile
    MAIN_MODULE_USAGE_COLOR_FILE = myFixture.addFileToProject(
      "/res/values/morecolors.xml",
      //language=XML
      """
      <resources>
          <color name="newColor">@color/testColor</color>
      </resources>
      """.trimIndent()
    ).virtualFile
    MODULE_WITH_DEPENDENCY_COLOR_FILE = myFixture.addFileToProject(
      getAdditionalModulePath(MODULE_WITH_DEPENDENCY) + "/res/values/colors.xml",
      COLORS_XML
    ).virtualFile
    MODULE_WITHOUT_DEPENDENCY_COLOR_FILE = myFixture.addFileToProject(
      getAdditionalModulePath(MODULE_WITHOUT_DEPENDENCY) + "/res/values/colors.xml",
      COLORS_XML
    ).virtualFile
  }

  private val COLORS_XML =
    //language=XML
    """
    <resources>
        <color name="testColor">#123456</color>
    </resources>
    """.trimIndent()

  override fun configureAdditionalModules(
    projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture>,
    modules: MutableList<MyAdditionalModuleData>
  ) {
    addModuleWithAndroidFacet(projectBuilder, modules, MODULE_WITHOUT_DEPENDENCY, AndroidProjectTypes.PROJECT_TYPE_LIBRARY, false)
    addModuleWithAndroidFacet(projectBuilder, modules, MODULE_WITH_DEPENDENCY, AndroidProjectTypes.PROJECT_TYPE_LIBRARY, true)
  }

  private fun checkScopePerModuleForOpenFile(mainModule: Boolean, moduleWithDependency: Boolean, moduleWithoutDependency: Boolean) {
    val scope = ResourceRepositoryToPsiResolver.getResourceSearchScope(
      (myFixture.elementAtCaret as ResourceReferencePsiElement).resourceReference,
      myFixture.file.findElementAt(myFixture.caretOffset)!!)
    assertThat(scope.contains(MAIN_MODULE_COLOR_FILE)).isEqualTo(mainModule)
    assertThat(scope.contains(MODULE_WITH_DEPENDENCY_COLOR_FILE)).isEqualTo(moduleWithDependency)
    assertThat(scope.contains(MODULE_WITHOUT_DEPENDENCY_COLOR_FILE)).isEqualTo(moduleWithoutDependency)
  }

  fun testResourceScoping() {
    myFixture.configureFromExistingVirtualFile(MODULE_WITH_DEPENDENCY_COLOR_FILE)
    myFixture.moveCaret("name=\"testCo|lor\"")
    checkScopePerModuleForOpenFile(mainModule = true, moduleWithDependency = true, moduleWithoutDependency = false)

    myFixture.configureFromExistingVirtualFile(MODULE_WITHOUT_DEPENDENCY_COLOR_FILE)
    myFixture.moveCaret("name=\"testCo|lor\"")
    checkScopePerModuleForOpenFile(mainModule = false, moduleWithDependency = false, moduleWithoutDependency = true)

    myFixture.configureFromExistingVirtualFile(MAIN_MODULE_USAGE_COLOR_FILE)
    myFixture.moveCaret("@color/test|Color")
    checkScopePerModuleForOpenFile(mainModule = true, moduleWithDependency = false, moduleWithoutDependency = false)

    myFixture.configureFromExistingVirtualFile(MAIN_MODULE_COLOR_FILE)
    myFixture.moveCaret("name=\"testCo|lor\"")
    checkScopePerModuleForOpenFile(mainModule = true, moduleWithDependency = false, moduleWithoutDependency = false)
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

  private fun checkLocaleConfiguration(resourceReferencePsiElement: ResourceReferencePsiElement,
                                       context: PsiElement,
                                       locale: Locale,
                                       expectedFileName: String) {
    val folderConfiguration = FolderConfiguration()
    folderConfiguration.localeQualifier = locale.qualifier
    val defaultConfigurationFile = ResourceRepositoryToPsiResolver.getBestGotoDeclarationTarget(
      resourceReferencePsiElement.resourceReference,
      context,
      folderConfiguration
    )!!.containingFile
    assertThat(defaultConfigurationFile.containingDirectory.name + "/" + defaultConfigurationFile.name).isEqualTo(expectedFileName)
  }

  private fun checkDensityConfiguration(resourceReferencePsiElement: ResourceReferencePsiElement,
                                        context: PsiElement,
                                        density: Density,
                                        expectedFileName: String) {
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