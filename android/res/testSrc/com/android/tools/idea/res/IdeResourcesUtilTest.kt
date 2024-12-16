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
import com.android.tools.idea.model.MergedManifestModificationListener.Companion.ensureSubscribed
import com.android.tools.idea.model.TestAndroidModel.Companion.namespaced
import com.android.tools.idea.util.toVirtualFile
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets
import com.google.common.truth.Truth
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
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
  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    ensureSubscribed(getProject())
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
    TestCase.assertEquals("Foo", styleToTheme("Foo"))
    TestCase.assertEquals("Theme", styleToTheme("@android:style/Theme"))
    TestCase.assertEquals("LocalTheme", styleToTheme("@style/LocalTheme"))
    TestCase.assertEquals("LocalTheme", styleToTheme("@foo.bar:style/LocalTheme"))
  }

  fun testGetFolderConfiguration() {
    val file1 = myFixture.addFileToProject("res/layout-land/foo1.xml", "<LinearLayout/>")
    val file2 = myFixture.addFileToProject("res/menu-en-rUS/foo2.xml", "<menu/>")

    TestCase.assertEquals("layout-land", getFolderConfiguration(file1)!!.getFolderName(ResourceFolderType.LAYOUT))
    TestCase.assertEquals("menu-en-rUS", getFolderConfiguration(file2)!!.getFolderName(ResourceFolderType.MENU))
    TestCase.assertEquals("layout-land", getFolderConfiguration(file1.getVirtualFile())!!.getFolderName(ResourceFolderType.LAYOUT))
    TestCase.assertEquals("menu-en-rUS", getFolderConfiguration(file2.getVirtualFile())!!.getFolderName(ResourceFolderType.MENU))
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
    Truth.assertThat(stateList.disabledStates).containsExactly(disabled)

    stateList.addState(disabledPressed)
    Truth.assertThat(stateList.disabledStates).containsExactly(disabled, disabledPressed)

    stateList = StateList("stateList", "colors")
    stateList.addState(enabled)
    stateList.addState(pressed)
    stateList.addState(selected)
    stateList.addState(enabledPressed) // Not reachable
    stateList.addState(disabled)
    Truth.assertThat(stateList.disabledStates).containsExactly(pressed, selected, enabledPressed, disabled)

    stateList = StateList("stateList", "colors")
    stateList.addState(enabledPressed)
    stateList.addState(pressed)
    stateList.addState(selected)
    stateList.addState(disabled)
    Truth.assertThat(stateList.disabledStates).containsExactly(pressed, disabled)

    stateList.addState(selectedPressed)
    Truth.assertThat(stateList.disabledStates).containsExactly(pressed, disabled, selectedPressed)

    stateList = StateList("stateList", "colors")
    stateList.addState(enabledSelectedPressed)
    stateList.addState(pressed)
    stateList.addState(selected)
    stateList.addState(disabled)
    stateList.addState(selectedPressed)
    Truth.assertThat(stateList.disabledStates).containsExactly(disabled, selectedPressed)

    stateList = StateList("stateList", "colors")
    stateList.addState(enabledPressed)
    stateList.addState(notChecked)
    stateList.addState(checkedNotPressed)
    stateList.addState(selected)
    stateList.addState(notFocused)
    Truth.assertThat(stateList.disabledStates).containsExactly(selected, notFocused)
  }

  fun testResolveAsIconFromColorReference() {
    val file = myFixture.copyFileToProject("resourceHelper/values.xml", "res/values/values.xml")

    val url = ResourceUrl.parse("@color/myColor2")
    val reference = url!!.resolve(ResourceNamespace.TODO(), ResourceNamespace.Resolver.EMPTY_RESOLVER)
    val rr = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(file).getResourceResolver()
    val value = rr.getResolvedResource(reference!!)
    val icon = rr.resolveAsIcon(value, myFacet)
    assertEquals(ColorIcon(16, Color(0xEEDDCC)), icon)
  }

  fun testResolveAsIconFromColorStateList() {
    myFixture.copyFileToProject("resourceHelper/values.xml", "res/values/values.xml")
    val file = myFixture.copyFileToProject("resourceHelper/my_state_list.xml", "res/color/my_state_list.xml")

    val url = ResourceUrl.parse("@color/my_state_list")
    val reference = url!!.resolve(ResourceNamespace.TODO(), ResourceNamespace.Resolver.EMPTY_RESOLVER)
    val rr = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(file).getResourceResolver()
    val value = rr.getResolvedResource(reference!!)
    val icon = rr.resolveAsIcon(value, myFacet)
    assertEquals(ColorsIcon(16, Color(0xEEDDCC), Color(0x33123456, true)), icon)
  }

  @Throws(IOException::class)
  fun testResolveAsIconFromDrawable() {
    val file = myFixture.copyFileToProject("resourceHelper/values.xml", "res/values/values.xml")
    val url = ResourceUrl.parse("@android:drawable/ic_delete")
    val reference = url!!.resolve(ResourceNamespace.TODO(), ResourceNamespace.Resolver.EMPTY_RESOLVER)
    val rr = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(file).getResourceResolver()
    val value = rr.getResolvedResource(reference!!)
    val icon = rr.resolveAsIcon(value, myFacet)
    val image = BufferedImage(icon!!.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB)
    icon.paintIcon(null, image.getGraphics(), 0, 0)
    val goldenImage = ImageIO.read(File(getTestDataPath() + "/resourceHelper/ic_delete.png"))
    assertImageSimilar("ic_delete", goldenImage, image, ImageDiffTestUtil.DEFAULT_IMAGE_DIFF_THRESHOLD_PERCENT)
  }

  fun testResolveAsIconFromStateListDrawable() {
    myFixture.copyFileToProject("resourceHelper/ic_delete.png", "res/drawable/ic_delete.png")
    val file = myFixture.copyFileToProject("resourceHelper/icon_state_list.xml", "res/drawable/icon_state_list.xml")
    val url = ResourceUrl.parse("@drawable/icon_state_list")
    val reference = url!!.resolve(ResourceNamespace.TODO(), ResourceNamespace.Resolver.EMPTY_RESOLVER)
    val rr = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(file).getResourceResolver()
    val value = rr.getResolvedResource(reference!!)
    val iconFile = rr.resolveDrawable(value, getProject())
    Truth.assertThat(iconFile!!.getName()).isEqualTo("ic_delete.png")
    Truth.assertThat(iconFile.exists()).isTrue()
  }

  fun testResolveEmptyStatelist() {
    val file = myFixture.copyFileToProject("resourceHelper/empty_state_list.xml", "res/color/empty_state_list.xml")
    val rr = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(file).getResourceResolver()
    assertNotNull(rr)
    val rv = rr.getResolvedResource(
      ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "empty_state_list")
    )
    assertNotNull(rv)
    assertNull(rr.resolveColor(rv, myModule.getProject()))
  }

  fun testResolve() {
    val appNs = ResourceNamespace.fromPackageName("com.example.app")
    setProjectNamespace(appNs)

    val innerFileLand = myFixture.addFileToProject("res/layout-land/inner.xml", "<LinearLayout/>")
    val innerFilePort = myFixture.addFileToProject("res/layout-port/inner.xml", "<LinearLayout/>")
    val outerFileContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
      "<FrameLayout xmlns:app=\"" + appNs.getXmlNamespaceUri() + "\">\n" +
      "\n" +
      "    <include\n" +
      "        layout=\"@app:layout/inner\"\n" +
      "        android:layout_width=\"wrap_content\"\n" +
      "        android:layout_height=\"wrap_content\" />\n" +
      "\n" +
      "</FrameLayout>"
    val outerFile = myFixture.addFileToProject("layout/outer.xml", outerFileContent) as XmlFile
    val configuration: Configuration = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(innerFileLand.getVirtualFile())
    val include = outerFile.getRootTag()!!.findFirstSubTag("include")
    var resolved =
      configuration.getResourceResolver().resolve(ResourceUrl.parse(include!!.getAttribute("layout")!!.getValue()!!)!!, include)
    TestCase.assertEquals(innerFileLand.getVirtualFile().getPath(), resolved!!.getValue())
    configuration.setDeviceState(configuration.getDevice()!!.getState("Portrait"))
    resolved = configuration.getResourceResolver().resolve(ResourceUrl.parse(include.getAttribute("layout")!!.getValue()!!)!!, include)
    TestCase.assertEquals(innerFilePort.getVirtualFile().getPath(), resolved!!.getValue())
  }

  fun testGetResourceResolverFromXmlTag_namespacesEnabled() {
    setProjectNamespace(ResourceNamespace.fromPackageName("com.example.app"))

    val file = myFixture.addFileToProject("layout/simple.xml", LAYOUT_FILE) as XmlFile
    val layout = file.getRootTag()
    val textview = layout!!.findFirstSubTag("TextView")

    var resolver = getNamespaceResolver(layout)
    Truth.assertThat(resolver.uriToPrefix(SdkConstants.TOOLS_URI)).isNull()
    Truth.assertThat(resolver.uriToPrefix(SdkConstants.ANDROID_URI)).isEqualTo("framework")
    Truth.assertThat(resolver.prefixToUri("newtools")).isNull()
    Truth.assertThat(resolver.prefixToUri("framework")).isEqualTo(SdkConstants.ANDROID_URI)

    resolver = getNamespaceResolver(textview!!)
    Truth.assertThat(resolver.uriToPrefix(SdkConstants.TOOLS_URI)).isEqualTo("newtools")
    Truth.assertThat(resolver.uriToPrefix(SdkConstants.ANDROID_URI)).isEqualTo("framework")
    Truth.assertThat(resolver.prefixToUri("newtools")).isEqualTo(SdkConstants.TOOLS_URI)
    Truth.assertThat(resolver.prefixToUri("framework")).isEqualTo(SdkConstants.ANDROID_URI)
  }

  private fun setProjectNamespace(appNs: ResourceNamespace) {
    CommandProcessor.getInstance().runUndoTransparentAction(Runnable {
      ApplicationManager.getApplication().runWriteAction(Runnable {
        AndroidModel.set(myFacet, namespaced(myFacet))
        Manifest.getMainManifest(myFacet)!!.getPackage().setValue(appNs.getPackageName())
      })
    })
  }

  fun testGetResourceResolverFromXmlTag_namespacesDisabled() {
    val file = myFixture.addFileToProject("layout/simple.xml", LAYOUT_FILE) as XmlFile
    val layout = file.getRootTag()
    val textview = layout!!.findFirstSubTag("TextView")

    var resolver = getNamespaceResolver(layout)

    // "tools" is implicitly defined in non-namespaced projects.
    Truth.assertThat(resolver.uriToPrefix(SdkConstants.TOOLS_URI)).isEqualTo("tools")

    // Proper namespacing doesn't work in non-namespaced projects, so within XML attributes, only "android" works.
    // TODO(b/74426748)
    Truth.assertThat(resolver.uriToPrefix(SdkConstants.ANDROID_URI)).isNull()

    Truth.assertThat(resolver.prefixToUri("newtools")).isNull()
    Truth.assertThat(resolver.prefixToUri("framework")).isNull()

    resolver = getNamespaceResolver(textview!!)
    Truth.assertThat(resolver.uriToPrefix(SdkConstants.TOOLS_URI)).isEqualTo("tools")
    Truth.assertThat(resolver.uriToPrefix(SdkConstants.ANDROID_URI)).isNull()
    Truth.assertThat(resolver.prefixToUri("newtools")).isNull()
    Truth.assertThat(resolver.prefixToUri("framework")).isNull()
  }

  fun testPsiElementGetNamespace() {
    // Project XML:
    val layoutFile = myFixture.addFileToProject("layout/simple.xml", LAYOUT_FILE) as XmlFile
    Truth.assertThat<ResourceNamespace?>(layoutFile.resourceNamespace).isEqualTo(ResourceNamespace.RES_AUTO)
    Truth.assertThat<ResourceNamespace?>(layoutFile.getRootTag()!!.resourceNamespace).isEqualTo(ResourceNamespace.RES_AUTO)

    // Project class:
    val projectClass = myFixture.addClass("package com.example; public class Hello {}")
    Truth.assertThat<ResourceNamespace?>(projectClass.resourceNamespace).isEqualTo(ResourceNamespace.RES_AUTO)

    // Project R class:
    val rClass = myFixture.getJavaFacade().findClass("p1.p2.R", projectClass.getResolveScope())
    Truth.assertThat<ResourceNamespace?>(rClass!!.resourceNamespace).isEqualTo(ResourceNamespace.RES_AUTO)

    // Project manifest:
    val manifest = Manifest.getMainManifest(myFacet)
    Truth.assertThat<ResourceNamespace?>(manifest!!.getXmlElement()!!.resourceNamespace).isEqualTo(ResourceNamespace.RES_AUTO)
    Truth.assertThat<ResourceNamespace?>(manifest.getXmlElement()!!.getContainingFile().resourceNamespace)
      .isEqualTo(ResourceNamespace.RES_AUTO)

    // Project Manifest class:
    WriteCommandAction.runWriteCommandAction(getProject(), Runnable {
      val newPermission = manifest.addPermission()
      newPermission.getName().setValue("p1.p2.NEW_PERMISSION")
    })
    val manifestClass = myFixture.getJavaFacade().findClass("p1.p2.Manifest", projectClass.getResolveScope())
    Truth.assertThat<ResourceNamespace?>(manifestClass!!.resourceNamespace).isEqualTo(ResourceNamespace.RES_AUTO)

    // Framework class:
    val frameworkClass = myFixture.getJavaFacade().findClass("android.app.Activity")
    Truth.assertThat<ResourceNamespace?>(frameworkClass.resourceNamespace).isEqualTo(ResourceNamespace.ANDROID)

    // Framework XML: API28 has two default app icons: res/drawable-watch/sym_def_app_icon.xml and res/drawable/sym_def_app_icon.xml
    val appIconResourceItems = StudioResourceRepositoryManager.getInstance(myFacet)
      .getFrameworkResources(com.google.common.collect.ImmutableSet.of<kotlin.String>())!!
      .getResources(ResourceNamespace.ANDROID, ResourceType.DRAWABLE, "sym_def_app_icon")

    for (appIconResourceItem in appIconResourceItems) {
      val appIcon = PsiManager.getInstance(getProject()).findFile(appIconResourceItem.getSource().toVirtualFile()!!) as XmlFile?
      Truth.assertThat<ResourceNamespace?>(appIcon!!.resourceNamespace).isEqualTo(ResourceNamespace.ANDROID)
      Truth.assertThat<ResourceNamespace?>(appIcon.getRootTag()!!.resourceNamespace).isEqualTo(ResourceNamespace.ANDROID)
    }
  }

  override fun configureAdditionalModules(
    projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture?>,
    modules: MutableList<MyAdditionalModuleData?>
  ) {
    addModuleWithAndroidFacet(projectBuilder, modules, "lib", AndroidProjectTypes.PROJECT_TYPE_LIBRARY)
  }

  fun testCaseSensitivityInChangeColorResource() {
    val xmlFile = myFixture.copyFileToProject("util/colors_before.xml", "res/values/colors.xml")
    val resDir = xmlFile.getParent().getParent()
    val dirNames: MutableList<String?> = ImmutableList.of<String?>("values")
    assertTrue(
      changeValueResource(
        getProject(), resDir, "myColor", ResourceType.COLOR, "#000000", "colors.xml",
        dirNames, false
      )
    )
    assertFalse(
      changeValueResource(
        getProject(), resDir, "mycolor", ResourceType.COLOR, "#FFFFFF", "colors.xml",
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
    TestCase.assertEquals("hello", field.getName())
    TestCase.assertEquals("string", field.getContainingClass()!!.getName())
    TestCase.assertEquals("p1.p2.R", field.getContainingClass()!!.getContainingClass()!!.getQualifiedName())
  }

  fun testFindResourceFieldsWithMultipleResourceNames() {
    myFixture.copyFileToProject("util/strings.xml", "res/values/strings.xml")

    val fields = findResourceFields(
      myFacet, "string", ImmutableList.of<String>("hello", "goodbye")
    )

    val fieldNames: MutableSet<String?> = Sets.newHashSet<String?>()
    for (field in fields) {
      fieldNames.add(field.getName())
      TestCase.assertEquals("p1.p2.R", field.getContainingClass()!!.getContainingClass()!!.getQualifiedName())
    }
    assertEquals(ImmutableSet.of<String?>("hello", "goodbye"), fieldNames)
    TestCase.assertEquals(2, fields.size)
  }

  /** Tests that "inherited" resource references are found (R fields in generated in dependent modules).  */
  @Throws(Exception::class)
  fun testFindResourceFieldsWithInheritance() {
    val libModule = myAdditionalModules.get(0)
    // Remove the current manifest (has wrong package name) and copy a manifest with proper package into the lib module.
    deleteManifest(libModule)

    myFixture.copyFileToProject("util/lib/AndroidManifest.xml", "additionalModules/lib/AndroidManifest.xml")

    // Add some lib string resources.
    myFixture.copyFileToProject("util/lib/strings.xml", "additionalModules/lib/res/values/strings.xml")

    val facet = AndroidFacet.getInstance(libModule)
    Truth.assertThat(facet).isNotNull()
    val fields = findResourceFields(facet!!, "string", "lib_hello")

    val packages: MutableSet<String?> = Sets.newHashSet<String?>()
    for (field in fields) {
      TestCase.assertEquals("lib_hello", field.getName())
      packages.add(StringUtil.getPackageName(field.getContainingClass()!!.getContainingClass()!!.getQualifiedName()!!))
    }
    assertEquals(ImmutableSet.of<String?>("p1.p2", "p1.p2.lib"), packages)
    TestCase.assertEquals(2, fields.size)
  }

  /** Tests that a module without an Android Manifest can still import a lib's R class  */
  @Throws(Exception::class)
  fun testIsRJavaFileImportedNoManifest() {
    val libModule = myAdditionalModules.get(0)
    // Remove the current lib manifest (has wrong package name) and copy a manifest with proper package into the lib module.
    deleteManifest(libModule)
    myFixture.copyFileToProject("util/lib/AndroidManifest.xml", "additionalModules/lib/AndroidManifest.xml")

    // Add some lib string resources.
    myFixture.copyFileToProject("util/lib/strings.xml", "additionalModules/lib/res/values/strings.xml")
    // Remove the manifest from the main module.
    deleteManifest(myModule)

    // The main module doesn't get a generated R class and inherit fields (lack of manifest)
    val facet = AndroidFacet.getInstance(myModule)
    Truth.assertThat(facet).isNotNull()
    val mainFields = findResourceFields(facet!!, "string", "lib_hello" /* onlyInOwnPackages */)
    assertEmpty(mainFields)

    // However, if the main module happens to get a handle on the lib's R class
    // (e.g., via "import p1.p2.lib.R;"), then that R class should be recognized
    // (e.g., for goto navigation).
    val javaFile =
      myFixture.addFileToProject("src/com/example/Foo.java", "package com.example; class Foo {}")
    val libRClass =
      myFixture.getJavaFacade().findClass("p1.p2.lib.R", javaFile.getResolveScope())
    assertNotNull(libRClass)
    assertTrue(isRJavaClass(libRClass!!))
  }

  fun testEnsureNamespaceImportedAddAuto() {
    val xmlFile = ensureNamespaceImported("<LinearLayout/>", SdkConstants.AUTO_URI, null)
    Truth.assertThat(xmlFile.getText()).isEqualTo("<LinearLayout xmlns:app=\"http://schemas.android.com/apk/res-auto\" />")
  }

  fun testEnsureNamespaceImportedAddAutoWithPrefixSuggestion() {
    val xmlFile = ensureNamespaceImported("<LinearLayout/>", SdkConstants.AUTO_URI, "sherpa")
    Truth.assertThat(xmlFile.getText()).isEqualTo("<LinearLayout xmlns:sherpa=\"http://schemas.android.com/apk/res-auto\" />")
  }

  fun testEnsureNamespaceImportedDoNotAddAutoIfAlreadyThere() {
    val xmlFile =
      ensureNamespaceImported("<LinearLayout xmlns:app=\"http://schemas.android.com/apk/res-auto\" />", SdkConstants.AUTO_URI, null)
    Truth.assertThat(xmlFile.getText()).isEqualTo("<LinearLayout xmlns:app=\"http://schemas.android.com/apk/res-auto\" />")
  }

  fun testEnsureNamespaceImportedDoNotAddAutoIfAlreadyThereWithPrefixSuggestion() {
    val xmlFile =
      ensureNamespaceImported("<LinearLayout xmlns:app=\"http://schemas.android.com/apk/res-auto\" />", SdkConstants.AUTO_URI, "sherpa")
    Truth.assertThat(xmlFile.getText()).isEqualTo("<LinearLayout xmlns:app=\"http://schemas.android.com/apk/res-auto\" />")
  }

  fun testEnsureNamespaceImportedAddEmptyNamespaceForStyleAttribute() {
    val xmlFile = ensureNamespaceImported("<LinearLayout/>", "", null)
    Truth.assertThat(xmlFile.getText()).isEqualTo("<LinearLayout/>")
  }

  private fun ensureNamespaceImported(@Language("XML") text: String, namespaceUri: String, suggestedPrefix: String?): XmlFile {
    val xmlFile = myFixture.addFileToProject("res/layout/layout.xml", text) as XmlFile

    CommandProcessor.getInstance().executeCommand(getProject(), Runnable {
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
    Truth.assertThat(file.getContainingDirectory().getName()).isEqualTo(rawDirName)
    Truth.assertThat(file.getName()).isEqualTo(fileName)
    Truth.assertThat(file.getText()).isEmpty()
  }

  fun testCreateFrameLayoutFileResource() {
    val file = createXmlFileResource("linear", getResDirectory("layout"), SdkConstants.FRAME_LAYOUT, ResourceType.LAYOUT, false)
    Truth.assertThat(file.getName()).isEqualTo("linear.xml")
    Truth.assertThat(file.getText()).isEqualTo(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
        "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
        "    android:layout_width=\"match_parent\"\n" +
        "    android:layout_height=\"match_parent\">\n" +
        "\n" +
        "</FrameLayout>"
    )
  }

  fun testCreateLinearLayoutFileResource() {
    val file = createXmlFileResource("linear", getResDirectory("layout"), SdkConstants.LINEAR_LAYOUT, ResourceType.LAYOUT, false)
    Truth.assertThat(file.getName()).isEqualTo("linear.xml")
    Truth.assertThat(file.getText()).isEqualTo(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
        "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
        "    android:orientation=\"vertical\"\n" +
        "    android:layout_width=\"match_parent\"\n" +
        "    android:layout_height=\"match_parent\">\n" +
        "\n" +
        "</LinearLayout>"
    )
  }

  fun testCreateLayoutFileResource() {
    val file = createXmlFileResource("layout", getResDirectory("layout"), SdkConstants.TAG_LAYOUT, ResourceType.LAYOUT, false)
    Truth.assertThat(file.getName()).isEqualTo("layout.xml")
    Truth.assertThat(file.getText()).isEqualTo(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
        "<layout xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
        "\n" +
        "</layout>"
    )
  }

  fun testCreateMergeFileResource() {
    val file = createXmlFileResource("merge", getResDirectory("layout"), SdkConstants.VIEW_MERGE, ResourceType.LAYOUT, false)
    Truth.assertThat(file.getName()).isEqualTo("merge.xml")
    Truth.assertThat(file.getText()).isEqualTo(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
        "<merge xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
        "\n" +
        "</merge>"
    )
  }

  fun testCreateNavigationFileResource() {
    val file =
      createXmlFileResource("nav", getResDirectory("navigation"), SdkConstants.TAG_NAVIGATION, ResourceType.NAVIGATION, false)
    Truth.assertThat(file.getName()).isEqualTo("nav.xml")
    Truth.assertThat(file.getText()).isEqualTo(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
        "<navigation xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
        "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
        "    android:id=\"@+id/nav\">\n" +
        "\n" +
        "</navigation>"
    )
  }

  fun testBuildResourceNameFromStringValue_simpleName() {
    Truth.assertThat(buildResourceNameFromStringValue("Just simple string")).isEqualTo("just_simple_string")
  }

  fun testBuildResourceNameFromStringValue_nameWithSurroundingSpaces() {
    Truth.assertThat(buildResourceNameFromStringValue(" Just a simple string ")).isEqualTo("just_a_simple_string")
  }

  fun testBuildResourceNameFromStringValue_nameWithDigits() {
    Truth.assertThat(buildResourceNameFromStringValue("A string with 31337 number")).isEqualTo("a_string_with_31337_number")
  }

  fun testBuildResourceNameFromStringValue_nameShouldNotStartWithNumber() {
    Truth.assertThat(buildResourceNameFromStringValue("100 things")).isEqualTo("_100_things")
  }

  fun testBuildResourceNameFromStringValue_emptyString() {
    Truth.assertThat(buildResourceNameFromStringValue("")).isNull()
  }

  fun testBuildResourceNameFromStringValue_stringHasPunctuation() {
    Truth.assertThat(buildResourceNameFromStringValue("Hello!!#^ But why??")).isEqualTo("hello_but_why")
  }

  fun testBuildResourceNameFromStringValue_stringIsOnlyPunctuation() {
    Truth.assertThat(buildResourceNameFromStringValue("!!#^??")).isNull()
  }

  fun testBuildResourceNameFromStringValue_stringStartsAndEndsWithPunctuation() {
    Truth.assertThat(buildResourceNameFromStringValue("\"A quotation\"")).isEqualTo("a_quotation")
    Truth.assertThat(buildResourceNameFromStringValue("<tag>")).isEqualTo("tag")
  }

  private fun getResDirectory(dirName: String): PsiDirectory {
    val virtualFileDir =
      VirtualFileManager.getInstance().findFileByNioPath(Path.of(myFixture.getTempDirPath()))
    Truth.assertThat(virtualFileDir).isNotNull()
    val dir = PsiManager.getInstance(getProject()).findDirectory(virtualFileDir!!)
    Truth.assertThat(dir).isNotNull()
    return findOrCreateSubdirectory(Companion.findOrCreateSubdirectory(dir!!, "res"), dirName)
  }

  companion object {
    @Language("XML")
    private val LAYOUT_FILE = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
      "<LinearLayout xmlns:framework=\"http://schemas.android.com/apk/res/android\"\n" +
      "    framework:orientation=\"vertical\"\n" +
      "    framework:layout_width=\"fill_parent\"\n" +
      "    framework:layout_height=\"fill_parent\">\n" +
      "\n" +
      "    <TextView xmlns:newtools=\"http://schemas.android.com/tools\"\n" +
      "        framework:layout_width=\"fill_parent\"\n" +
      "        framework:layout_height=\"wrap_content\"\n" +
      "        newtools:text=\"Hello World, MyActivity\" />\n" +
      "</LinearLayout>\n"

    private fun findOrCreateSubdirectory(parent: PsiDirectory, subdirName: String): PsiDirectory {
      val sub = parent.findSubdirectory(subdirName)
      return if (sub == null) WriteAction.compute<PsiDirectory, RuntimeException?>(ThrowableComputable {
        parent.createSubdirectory(
          subdirName
        )
      }) else sub
    }
  }
}
