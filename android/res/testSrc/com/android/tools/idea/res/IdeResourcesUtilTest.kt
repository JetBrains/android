/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.AndroidProjectTypes
import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.testutils.ImageDiffUtil.assertImageSimilar
import com.android.tools.adtui.imagediff.ImageDiffTestUtil
import com.android.tools.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.model.MergedManifestModificationListener
import com.android.tools.idea.model.TestAndroidModel.Companion.namespaced
import com.android.tools.idea.util.toVirtualFile
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import com.intellij.util.IncorrectOperationException
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.ColorsIcon
import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.facet.AndroidFacet
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.nio.file.Path
import javax.imageio.ImageIO

class IdeResourcesUtilTest : AndroidTestCase() {
  override fun setUp() {
    super.setUp()
    MergedManifestModificationListener.ensureSubscribed(project)
  }

  fun testIsValueBasedResourceType() {
    assertTrue(ResourceType.STRING.isValueBased())
    assertTrue(ResourceType.DIMEN.isValueBased())
    assertTrue(ResourceType.ID.isValueBased())

    assertFalse(ResourceType.LAYOUT.isValueBased())

    // These can be both:
    assertTrue(ResourceType.DRAWABLE.isValueBased())
    assertTrue(ResourceType.COLOR.isValueBased())
  }

  fun testStyleToTheme() {
    assertEquals("Foo", styleToTheme("Foo"))
    assertEquals("Theme", styleToTheme("@android:style/Theme"))
    assertEquals("LocalTheme", styleToTheme("@style/LocalTheme"))
    assertEquals("LocalTheme", styleToTheme("@foo.bar:style/LocalTheme"))
  }

  fun testGetFolderConfiguration() {
    val file1 = myFixture.addFileToProject("res/layout-land/foo1.xml", "<LinearLayout/>")
    val file2 = myFixture.addFileToProject("res/menu-en-rUS/foo2.xml", "<menu/>")

    assertEquals("layout-land", getFolderConfiguration(file1)?.getFolderName(ResourceFolderType.LAYOUT))
    assertEquals("menu-en-rUS", getFolderConfiguration(file2)?.getFolderName(ResourceFolderType.MENU))
    assertEquals("layout-land", getFolderConfiguration(file1.virtualFile)?.getFolderName(ResourceFolderType.LAYOUT))
    assertEquals("menu-en-rUS", getFolderConfiguration(file2.virtualFile)?.getFolderName(ResourceFolderType.MENU))
  }

  fun testDisabledStateListStates() {
    val disabled = StateListState("value", ImmutableMap.of<String, Boolean>("state_enabled", false), null)
    val disabledPressed =
      StateListState("value", ImmutableMap.of<String, Boolean>("state_enabled", false, "state_pressed", true), null)
    val pressed = StateListState("value", ImmutableMap.of<String, Boolean>("state_pressed", true), null)
    val enabledPressed =
      StateListState("value", ImmutableMap.of<String, Boolean>("state_enabled", true, "state_pressed", true), null)
    val enabled = StateListState("value", ImmutableMap.of<String, Boolean>("state_enabled", true), null)
    val selected = StateListState("value", ImmutableMap.of<String, Boolean>("state_selected", true), null)
    val selectedPressed =
      StateListState("value", ImmutableMap.of<String, Boolean>("state_selected", true, "state_pressed", true), null)
    val enabledSelectedPressed =
      StateListState(
        "value", ImmutableMap.of<String, Boolean>("state_enabled", true, "state_selected", true, "state_pressed", true),
        null
      )
    val notFocused = StateListState("value", ImmutableMap.of<String, Boolean>("state_focused", false), null)
    val notChecked = StateListState("value", ImmutableMap.of<String, Boolean>("state_checked", false), null)
    val checkedNotPressed =
      StateListState("value", ImmutableMap.of<String, Boolean>("state_checked", true, "state_pressed", false), null)

    var stateList = StateList("stateList", "colors")
    stateList.addState(pressed)
    stateList.addState(disabled)
    stateList.addState(selected)
    assertThat(stateList.disabledStates).containsExactly(disabled)

    stateList.addState(disabledPressed)
    assertThat(stateList.disabledStates).containsExactly(disabled, disabledPressed)

    stateList = StateList("stateList", "colors")
    stateList.addState(enabled)
    stateList.addState(pressed)
    stateList.addState(selected)
    stateList.addState(enabledPressed) // Not reachable
    stateList.addState(disabled)
    assertThat(stateList.disabledStates).containsExactly(pressed, selected, enabledPressed, disabled)

    stateList = StateList("stateList", "colors")
    stateList.addState(enabledPressed)
    stateList.addState(pressed)
    stateList.addState(selected)
    stateList.addState(disabled)
    assertThat(stateList.disabledStates).containsExactly(pressed, disabled)

    stateList.addState(selectedPressed)
    assertThat(stateList.disabledStates).containsExactly(pressed, disabled, selectedPressed)

    stateList = StateList("stateList", "colors")
    stateList.addState(enabledSelectedPressed)
    stateList.addState(pressed)
    stateList.addState(selected)
    stateList.addState(disabled)
    stateList.addState(selectedPressed)
    assertThat(stateList.disabledStates).containsExactly(disabled, selectedPressed)

    stateList = StateList("stateList", "colors")
    stateList.addState(enabledPressed)
    stateList.addState(notChecked)
    stateList.addState(checkedNotPressed)
    stateList.addState(selected)
    stateList.addState(notFocused)
    assertThat(stateList.disabledStates).containsExactly(selected, notFocused)
  }

  fun testResolveAsIconFromColorReference() {
    val file = myFixture.copyFileToProject("resourceHelper/values.xml", "res/values/values.xml")

    val url = ResourceUrl.parse("@color/myColor2")
    val reference = url?.resolve(ResourceNamespace.TODO(), ResourceNamespace.Resolver.EMPTY_RESOLVER)
    val rr = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(file).getResourceResolver()
    val value = rr.getResolvedResource(reference!!)
    val icon = rr.resolveAsIcon(value, myFacet)
    assertEquals(ColorIcon(16, Color(0xEEDDCC)), icon)
  }

  fun testResolveAsIconFromColorStateList() {
    myFixture.copyFileToProject("resourceHelper/values.xml", "res/values/values.xml")
    val file = myFixture.copyFileToProject("resourceHelper/my_state_list.xml", "res/color/my_state_list.xml")

    val url = ResourceUrl.parse("@color/my_state_list")
    val reference = url?.resolve(ResourceNamespace.TODO(), ResourceNamespace.Resolver.EMPTY_RESOLVER)
    val rr = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(file).getResourceResolver()
    val value = rr.getResolvedResource(reference!!)
    val icon = rr.resolveAsIcon(value, myFacet)
    assertEquals(ColorsIcon(16, Color(0xEEDDCC), Color(0x33123456, true)), icon)
  }

  @Throws(IOException::class)
  fun testResolveAsIconFromDrawable() {
    val file = myFixture.copyFileToProject("resourceHelper/values.xml", "res/values/values.xml")
    val url = ResourceUrl.parse("@android:drawable/ic_delete")
    val reference = url?.resolve(ResourceNamespace.TODO(), ResourceNamespace.Resolver.EMPTY_RESOLVER)
    val rr = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(file).getResourceResolver()
    val value = rr.getResolvedResource(reference!!)
    val icon = rr.resolveAsIcon(value, myFacet)!!
    val image = BufferedImage(icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB)
    icon.paintIcon(null, image.graphics, 0, 0)
    val goldenImage = ImageIO.read(File(getTestDataPath() + "/resourceHelper/ic_delete.png"))
    assertImageSimilar("ic_delete", goldenImage, image, ImageDiffTestUtil.DEFAULT_IMAGE_DIFF_THRESHOLD_PERCENT)
  }

  fun testResolveAsIconFromStateListDrawable() {
    myFixture.copyFileToProject("resourceHelper/ic_delete.png", "res/drawable/ic_delete.png")
    val file = myFixture.copyFileToProject("resourceHelper/icon_state_list.xml", "res/drawable/icon_state_list.xml")
    val url = ResourceUrl.parse("@drawable/icon_state_list")
    val reference = url?.resolve(ResourceNamespace.TODO(), ResourceNamespace.Resolver.EMPTY_RESOLVER)
    val rr = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(file).getResourceResolver()
    val value = rr.getResolvedResource(reference!!)
    val iconFile = rr.resolveDrawable(value, project)
    assertThat(iconFile?.name).isEqualTo("ic_delete.png")
    assertThat(iconFile?.exists()).isTrue()
  }

  fun testResolveEmptyStatelist() {
    val file = myFixture.copyFileToProject("resourceHelper/empty_state_list.xml", "res/color/empty_state_list.xml")
    val rr = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(file).getResourceResolver()
    assertNotNull(rr)
    val rv = rr.getResolvedResource(
      ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "empty_state_list")
    )
    assertNotNull(rv)
    assertNull(rr.resolveColor(rv, myModule.project))
  }

  fun testResolve() {
    val appNs = ResourceNamespace.fromPackageName("com.example.app")
    setProjectNamespace(appNs)

    val innerFileLand = myFixture.addFileToProject("res/layout-land/inner.xml", "<LinearLayout/>")
    val innerFilePort = myFixture.addFileToProject("res/layout-port/inner.xml", "<LinearLayout/>")
    // language=xml
    val outerFileContent =
      """
      <?xml version="1.0" encoding="utf-8"?>
      <FrameLayout xmlns:app="${appNs.xmlNamespaceUri}">
          <include
              layout="@app:layout/inner"
              android:layout_width="wrap_content"
              android:layout_height="wrap_content" />
      </FrameLayout>
      """.trimIndent()
    val outerFile = myFixture.addFileToProject("layout/outer.xml", outerFileContent) as XmlFile
    val configuration: Configuration = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(innerFileLand.virtualFile)
    val include = outerFile.rootTag?.findFirstSubTag("include")
    var resolved =
      configuration.getResourceResolver().resolve(ResourceUrl.parse(include?.getAttribute("layout")?.value!!)!!, include)
    assertEquals(innerFileLand.virtualFile.path, resolved?.getValue())
    configuration.deviceState = configuration.getDevice()?.getState("Portrait")
    resolved = configuration.getResourceResolver().resolve(ResourceUrl.parse(include.getAttribute("layout")?.value!!)!!, include)
    assertEquals(innerFilePort.virtualFile.path, resolved?.getValue())
  }

  fun testGetResourceResolverFromXmlTag_namespacesEnabled() {
    setProjectNamespace(ResourceNamespace.fromPackageName("com.example.app"))

    val file = myFixture.addFileToProject("layout/simple.xml", LAYOUT_FILE) as XmlFile
    val layout = file.rootTag
    val textview = layout?.findFirstSubTag("TextView")!!

    var resolver = getNamespaceResolver(layout)
    assertThat(resolver.uriToPrefix(SdkConstants.TOOLS_URI)).isNull()
    assertThat(resolver.uriToPrefix(SdkConstants.ANDROID_URI)).isEqualTo("framework")
    assertThat(resolver.prefixToUri("newtools")).isNull()
    assertThat(resolver.prefixToUri("framework")).isEqualTo(SdkConstants.ANDROID_URI)

    resolver = getNamespaceResolver(textview)
    assertThat(resolver.uriToPrefix(SdkConstants.TOOLS_URI)).isEqualTo("newtools")
    assertThat(resolver.uriToPrefix(SdkConstants.ANDROID_URI)).isEqualTo("framework")
    assertThat(resolver.prefixToUri("newtools")).isEqualTo(SdkConstants.TOOLS_URI)
    assertThat(resolver.prefixToUri("framework")).isEqualTo(SdkConstants.ANDROID_URI)
  }

  private fun setProjectNamespace(appNs: ResourceNamespace) {
    CommandProcessor.getInstance().runUndoTransparentAction(Runnable {
      ApplicationManager.getApplication().runWriteAction(Runnable {
        AndroidModel.set(myFacet, namespaced(myFacet))
        Manifest.getMainManifest(myFacet)!!.getPackage().value = appNs.packageName
      })
    })
  }

  fun testGetResourceResolverFromXmlTag_namespacesDisabled() {
    val file = myFixture.addFileToProject("layout/simple.xml", LAYOUT_FILE) as XmlFile
    val layout = file.rootTag
    val textview = layout?.findFirstSubTag("TextView")!!

    var resolver = getNamespaceResolver(layout)

    // "tools" is implicitly defined in non-namespaced projects.
    assertThat(resolver.uriToPrefix(SdkConstants.TOOLS_URI)).isEqualTo("tools")

    // Proper namespacing doesn't work in non-namespaced projects, so within XML attributes, only "android" works.
    // TODO(b/74426748)
    assertThat(resolver.uriToPrefix(SdkConstants.ANDROID_URI)).isNull()

    assertThat(resolver.prefixToUri("newtools")).isNull()
    assertThat(resolver.prefixToUri("framework")).isNull()

    resolver = getNamespaceResolver(textview)
    assertThat(resolver.uriToPrefix(SdkConstants.TOOLS_URI)).isEqualTo("tools")
    assertThat(resolver.uriToPrefix(SdkConstants.ANDROID_URI)).isNull()
    assertThat(resolver.prefixToUri("newtools")).isNull()
    assertThat(resolver.prefixToUri("framework")).isNull()
  }

  fun testPsiElementGetNamespace() {
    // Project XML:
    val layoutFile = myFixture.addFileToProject("layout/simple.xml", LAYOUT_FILE) as XmlFile
    assertThat<ResourceNamespace?>(layoutFile.resourceNamespace).isEqualTo(ResourceNamespace.RES_AUTO)
    assertThat<ResourceNamespace?>(layoutFile.rootTag?.resourceNamespace).isEqualTo(ResourceNamespace.RES_AUTO)

    // Project class:
    val projectClass = myFixture.addClass("package com.example; public class Hello {}")
    assertThat<ResourceNamespace?>(projectClass.resourceNamespace).isEqualTo(ResourceNamespace.RES_AUTO)

    // Project R class:
    val rClass = myFixture.javaFacade.findClass("p1.p2.R", projectClass.resolveScope)
    assertThat<ResourceNamespace?>(rClass?.resourceNamespace).isEqualTo(ResourceNamespace.RES_AUTO)

    // Project manifest:
    val manifest = Manifest.getMainManifest(myFacet)!!
    assertThat<ResourceNamespace?>(manifest.xmlElement?.resourceNamespace).isEqualTo(ResourceNamespace.RES_AUTO)
    assertThat<ResourceNamespace?>(manifest.xmlElement?.containingFile?.resourceNamespace)
      .isEqualTo(ResourceNamespace.RES_AUTO)

    // Project Manifest class:
    WriteCommandAction.runWriteCommandAction(project, Runnable {
      val newPermission = manifest.addPermission()
      newPermission.getName().value = "p1.p2.NEW_PERMISSION"
    })
    val manifestClass = myFixture.javaFacade.findClass("p1.p2.Manifest", projectClass.resolveScope)
    assertThat<ResourceNamespace?>(manifestClass?.resourceNamespace).isEqualTo(ResourceNamespace.RES_AUTO)

    // Framework class:
    val frameworkClass = myFixture.javaFacade.findClass("android.app.Activity")
    assertThat<ResourceNamespace?>(frameworkClass.resourceNamespace).isEqualTo(ResourceNamespace.ANDROID)

    // Framework XML: API28 has two default app icons: res/drawable-watch/sym_def_app_icon.xml and res/drawable/sym_def_app_icon.xml
    val appIconResourceItems = StudioResourceRepositoryManager.getInstance(myFacet)
      .getFrameworkResources(ImmutableSet.of<String>())!!
      .getResources(ResourceNamespace.ANDROID, ResourceType.DRAWABLE, "sym_def_app_icon")

    for (appIconResourceItem in appIconResourceItems) {
      val appIcon = PsiManager.getInstance(project).findFile(appIconResourceItem.getSource().toVirtualFile()!!) as XmlFile?
      assertThat<ResourceNamespace?>(appIcon?.resourceNamespace).isEqualTo(ResourceNamespace.ANDROID)
      assertThat<ResourceNamespace?>(appIcon?.rootTag?.resourceNamespace).isEqualTo(ResourceNamespace.ANDROID)
    }
  }

  override fun configureAdditionalModules(
    projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture>,
    modules: List<MyAdditionalModuleData>
  ) {
    addModuleWithAndroidFacet(projectBuilder, modules, "lib", AndroidProjectTypes.PROJECT_TYPE_LIBRARY)
  }

  fun testCaseSensitivityInChangeColorResource() {
    val xmlFile = myFixture.copyFileToProject("util/colors_before.xml", "res/values/colors.xml")
    val resDir = xmlFile.parent.parent
    val dirNames = listOf("values")
    assertTrue(
      changeValueResource(
        project, resDir, "myColor", ResourceType.COLOR, "#000000", "colors.xml",
        dirNames, false
      )
    )
    assertFalse(
      changeValueResource(
        project, resDir, "mycolor", ResourceType.COLOR, "#FFFFFF", "colors.xml",
        dirNames, false
      )
    )
    myFixture.checkResultByFile("res/values/colors.xml", "util/colors_after.xml", true)
  }

  fun testFindResourceFields() {
    myFixture.copyFileToProject("util/strings.xml", "res/values/strings.xml")

    val fields = findResourceFields(myFacet, "string", "hello")
    TestCase.assertEquals(1, fields.size)
    val field = fields[0]
    TestCase.assertEquals("hello", field.name)
    TestCase.assertEquals("string", field.containingClass?.name)
    TestCase.assertEquals("p1.p2.R", field.containingClass?.containingClass?.qualifiedName)
  }

  fun testFindResourceFieldsWithMultipleResourceNames() {
    myFixture.copyFileToProject("util/strings.xml", "res/values/strings.xml")

    val fields = findResourceFields(
      myFacet, "string", ImmutableList.of<String>("hello", "goodbye")
    )

    val fieldNames: MutableSet<String?> = Sets.newHashSet<String?>()
    for (field in fields) {
      fieldNames.add(field.name)
      TestCase.assertEquals("p1.p2.R", field.containingClass?.containingClass?.qualifiedName)
    }
    assertEquals(ImmutableSet.of<String?>("hello", "goodbye"), fieldNames)
    TestCase.assertEquals(2, fields.size)
  }

  /** Tests that "inherited" resource references are found (R fields in generated in dependent modules).  */
  @Throws(Exception::class)
  fun testFindResourceFieldsWithInheritance() {
    val libModule = myAdditionalModules[0]
    // Remove the current manifest (has wrong package name) and copy a manifest with proper package into the lib module.
    deleteManifest(libModule)

    myFixture.copyFileToProject("util/lib/AndroidManifest.xml", "additionalModules/lib/AndroidManifest.xml")

    // Add some lib string resources.
    myFixture.copyFileToProject("util/lib/strings.xml", "additionalModules/lib/res/values/strings.xml")

    val facet = AndroidFacet.getInstance(libModule)
    assertThat(facet).isNotNull()
    val fields = findResourceFields(facet!!, "string", "lib_hello")

    val packages: MutableSet<String?> = Sets.newHashSet<String?>()
    for (field in fields) {
      TestCase.assertEquals("lib_hello", field.name)
      packages.add(StringUtil.getPackageName(field.containingClass?.containingClass?.qualifiedName!!))
    }
    assertEquals(ImmutableSet.of<String?>("p1.p2", "p1.p2.lib"), packages)
    TestCase.assertEquals(2, fields.size)
  }

  /** Tests that a module without an Android Manifest can still import a lib's R class  */
  @Throws(Exception::class)
  fun testIsRJavaFileImportedNoManifest() {
    val libModule = myAdditionalModules[0]
    // Remove the current lib manifest (has wrong package name) and copy a manifest with proper package into the lib module.
    deleteManifest(libModule)
    myFixture.copyFileToProject("util/lib/AndroidManifest.xml", "additionalModules/lib/AndroidManifest.xml")

    // Add some lib string resources.
    myFixture.copyFileToProject("util/lib/strings.xml", "additionalModules/lib/res/values/strings.xml")
    // Remove the manifest from the main module.
    deleteManifest(myModule)

    // The main module doesn't get a generated R class and inherit fields (lack of manifest)
    val facet = AndroidFacet.getInstance(myModule)
    assertThat(facet).isNotNull()
    val mainFields = findResourceFields(facet!!, "string", "lib_hello" /* onlyInOwnPackages */)
    assertEmpty(mainFields)

    // However, if the main module happens to get a handle on the lib's R class
    // (e.g., via "import p1.p2.lib.R;"), then that R class should be recognized
    // (e.g., for goto navigation).
    val javaFile =
      myFixture.addFileToProject("src/com/example/Foo.java", "package com.example; class Foo {}")
    val libRClass =
      myFixture.javaFacade.findClass("p1.p2.lib.R", javaFile.resolveScope)
    assertNotNull(libRClass)
    assertTrue(isRJavaClass(libRClass!!))
  }

  fun testEnsureNamespaceImportedAddAuto() {
    val xmlFile = ensureNamespaceImported("<LinearLayout/>", SdkConstants.AUTO_URI, null)
    assertThat(xmlFile.text).isEqualTo("<LinearLayout xmlns:app=\"http://schemas.android.com/apk/res-auto\" />")
  }

  fun testEnsureNamespaceImportedAddAutoWithPrefixSuggestion() {
    val xmlFile = ensureNamespaceImported("<LinearLayout/>", SdkConstants.AUTO_URI, "sherpa")
    assertThat(xmlFile.text).isEqualTo("<LinearLayout xmlns:sherpa=\"http://schemas.android.com/apk/res-auto\" />")
  }

  fun testEnsureNamespaceImportedDoNotAddAutoIfAlreadyThere() {
    val xmlFile =
      ensureNamespaceImported("<LinearLayout xmlns:app=\"http://schemas.android.com/apk/res-auto\" />", SdkConstants.AUTO_URI, null)
    assertThat(xmlFile.text).isEqualTo("<LinearLayout xmlns:app=\"http://schemas.android.com/apk/res-auto\" />")
  }

  fun testEnsureNamespaceImportedDoNotAddAutoIfAlreadyThereWithPrefixSuggestion() {
    val xmlFile =
      ensureNamespaceImported("<LinearLayout xmlns:app=\"http://schemas.android.com/apk/res-auto\" />", SdkConstants.AUTO_URI, "sherpa")
    assertThat(xmlFile.text).isEqualTo("<LinearLayout xmlns:app=\"http://schemas.android.com/apk/res-auto\" />")
  }

  fun testEnsureNamespaceImportedAddEmptyNamespaceForStyleAttribute() {
    val xmlFile = ensureNamespaceImported("<LinearLayout/>", "", null)
    assertThat(xmlFile.text).isEqualTo("<LinearLayout/>")
  }

  private fun ensureNamespaceImported(@Language("XML") text: String, namespaceUri: String, suggestedPrefix: String?): XmlFile {
    val xmlFile = myFixture.addFileToProject("res/layout/layout.xml", text) as XmlFile

    CommandProcessor.getInstance().executeCommand(project, Runnable {
      ApplicationManager.getApplication().runWriteAction(Runnable {
        ensureNamespaceImported(xmlFile, namespaceUri, suggestedPrefix)
      })
    }, "", "")

    return xmlFile
  }

  fun testCreateRawFileResource() {
    val fileName = "my_great_raw_file.foobar"
    val rawDirName = "raw"
    val file = createRawFileResource(fileName, getResDirectory(rawDirName))
    assertThat(file.containingDirectory.name).isEqualTo(rawDirName)
    assertThat(file.name).isEqualTo(fileName)
    assertThat(file.text).isEmpty()
  }

  fun testCreateFrameLayoutFileResource() {
    val file = createXmlFileResource("linear", getResDirectory("layout"), SdkConstants.FRAME_LAYOUT, ResourceType.LAYOUT, false)
    assertThat(file.name).isEqualTo("linear.xml")
    assertThat(file.text).isEqualTo(
      """
      <?xml version="1.0" encoding="utf-8"?>
      <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
          android:layout_width="match_parent"
          android:layout_height="match_parent">

      </FrameLayout>
      """.trimIndent()
    )
  }

  fun testCreateLinearLayoutFileResource() {
    val file = createXmlFileResource("linear", getResDirectory("layout"), SdkConstants.LINEAR_LAYOUT, ResourceType.LAYOUT, false)
    assertThat(file.name).isEqualTo("linear.xml")
    assertThat(file.text).isEqualTo(
      """
      <?xml version="1.0" encoding="utf-8"?>
      <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
          android:orientation="vertical"
          android:layout_width="match_parent"
          android:layout_height="match_parent">

      </LinearLayout>
      """.trimIndent()
    )
  }

  fun testCreateLayoutFileResource() {
    val file = createXmlFileResource("layout", getResDirectory("layout"), SdkConstants.TAG_LAYOUT, ResourceType.LAYOUT, false)
    assertThat(file.name).isEqualTo("layout.xml")
    assertThat(file.text).isEqualTo(
      """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">

      </layout>
      """.trimIndent()
    )
  }

  fun testCreateMergeFileResource() {
    val file = createXmlFileResource("merge", getResDirectory("layout"), SdkConstants.VIEW_MERGE, ResourceType.LAYOUT, false)
    assertThat(file.name).isEqualTo("merge.xml")
    assertThat(file.text).isEqualTo(
      """
      <?xml version="1.0" encoding="utf-8"?>
      <merge xmlns:android="http://schemas.android.com/apk/res/android">

      </merge>
      """.trimIndent()
    )
  }

  fun testCreateNavigationFileResource() {
    val file =
      createXmlFileResource("nav", getResDirectory("navigation"), SdkConstants.TAG_NAVIGATION, ResourceType.NAVIGATION, false)
    assertThat(file.name).isEqualTo("nav.xml")
    assertThat(file.text).isEqualTo(
      """
      <?xml version="1.0" encoding="utf-8"?>
      <navigation xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:app="http://schemas.android.com/apk/res-auto"
          android:id="@+id/nav">

      </navigation>
      """.trimIndent()
    )
  }

  fun testBuildResourceNameFromStringValue_simpleName() {
    assertThat(buildResourceNameFromStringValue("Just simple string")).isEqualTo("just_simple_string")
  }

  fun testBuildResourceNameFromStringValue_nameWithSurroundingSpaces() {
    assertThat(buildResourceNameFromStringValue(" Just a simple string ")).isEqualTo("just_a_simple_string")
  }

  fun testBuildResourceNameFromStringValue_nameWithDigits() {
    assertThat(buildResourceNameFromStringValue("A string with 31337 number")).isEqualTo("a_string_with_31337_number")
  }

  fun testBuildResourceNameFromStringValue_nameShouldNotStartWithNumber() {
    assertThat(buildResourceNameFromStringValue("100 things")).isEqualTo("_100_things")
  }

  fun testBuildResourceNameFromStringValue_emptyString() {
    assertThat(buildResourceNameFromStringValue("")).isNull()
  }

  fun testBuildResourceNameFromStringValue_stringHasPunctuation() {
    assertThat(buildResourceNameFromStringValue("Hello!!#^ But why??")).isEqualTo("hello_but_why")
  }

  fun testBuildResourceNameFromStringValue_stringIsOnlyPunctuation() {
    assertThat(buildResourceNameFromStringValue("!!#^??")).isNull()
  }

  fun testBuildResourceNameFromStringValue_stringStartsAndEndsWithPunctuation() {
    assertThat(buildResourceNameFromStringValue("\"A quotation\"")).isEqualTo("a_quotation")
    assertThat(buildResourceNameFromStringValue("<tag>")).isEqualTo("tag")
  }

  private fun getResDirectory(dirName: String): PsiDirectory {
    val virtualFileDir =
      kotlin.test.assertNotNull(VirtualFileManager.getInstance().findFileByNioPath(Path.of(myFixture.tempDirPath)))
    val dir = kotlin.test.assertNotNull(PsiManager.getInstance(project).findDirectory(virtualFileDir))
    return findOrCreateSubdirectory(findOrCreateSubdirectory(dir, "res"), dirName)
  }

  companion object {
    // language=xml
    private val LAYOUT_FILE =
      """
      <?xml version="1.0" encoding="utf-8"?>
      <LinearLayout xmlns:framework="http://schemas.android.com/apk/res/android"
          framework:orientation="vertical"
          framework:layout_width="fill_parent"
          framework:layout_height="fill_parent">

          <TextView xmlns:newtools="http://schemas.android.com/tools"
              framework:layout_width="fill_parent"
              framework:layout_height="wrap_content"
              newtools:text="Hello World, MyActivity" />
      </LinearLayout>
      """.trimIndent()

    private fun findOrCreateSubdirectory(parent: PsiDirectory, subdirName: String) =
      parent.findSubdirectory(subdirName)
      ?: WriteAction.compute<PsiDirectory, IncorrectOperationException>{
        parent.createSubdirectory(subdirName)
      }
  }
}
