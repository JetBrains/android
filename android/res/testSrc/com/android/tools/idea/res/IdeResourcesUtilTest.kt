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
import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.adtui.imagediff.ImageDiffTestUtil
import com.android.tools.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.model.MergedManifestModificationListener
import com.android.tools.idea.model.TestAndroidModel.Companion.namespaced
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.util.androidFacet
import com.android.tools.idea.util.toVirtualFile
import com.google.common.collect.ImmutableMap
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiField
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import com.intellij.util.IncorrectOperationException
import com.intellij.util.application
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.ColorsIcon
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.assertNotNull
import org.intellij.lang.annotations.Language
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private const val ANDROID_MANIFEST = "AndroidManifest.xml"

@RunWith(JUnit4::class)
class IdeResourcesUtilTest {
  @get:Rule val projectRule = AndroidProjectRule.withSdk().onEdt()

  private val project by lazy { projectRule.project }
  private val fixture by lazy { projectRule.fixture as JavaCodeInsightTestFixture }
  private val module by lazy { projectRule.projectRule.module }
  private val facet by lazy { assertNotNull(module.androidFacet) }

  @Before
  fun setUp() {
    projectRule.fixture.testDataPath =
      resolveWorkspacePath("tools/adt/idea/android/testData").toString()
    fixture.copyFileToProject(ANDROID_MANIFEST, ANDROID_MANIFEST)
    MergedManifestModificationListener.ensureSubscribed(project)
  }

  @Test
  fun isValueBasedResourceType() {
    assertThat(ResourceType.STRING.isValueBased()).isTrue()
    assertThat(ResourceType.DIMEN.isValueBased()).isTrue()
    assertThat(ResourceType.ID.isValueBased()).isTrue()

    assertThat(ResourceType.LAYOUT.isValueBased()).isFalse()

    // These can be both:
    assertThat(ResourceType.DRAWABLE.isValueBased()).isTrue()
    assertThat(ResourceType.COLOR.isValueBased()).isTrue()
  }

  @Test
  fun styleToTheme() {
    assertThat(styleToTheme("Foo")).isEqualTo("Foo")
    assertThat(styleToTheme("@android:style/Theme")).isEqualTo("Theme")
    assertThat(styleToTheme("@style/LocalTheme")).isEqualTo("LocalTheme")
    assertThat(styleToTheme("@foo.bar:style/LocalTheme")).isEqualTo("LocalTheme")
  }

  @Test
  fun getFolderConfiguration() {
    val file1 = fixture.addFileToProject("res/layout-land/foo1.xml", "<LinearLayout/>")
    val file2 = fixture.addFileToProject("res/menu-en-rUS/foo2.xml", "<menu/>")

    assertThat(getFolderConfiguration(file1)?.getFolderName(ResourceFolderType.LAYOUT))
      .isEqualTo("layout-land")
    assertThat(getFolderConfiguration(file2)?.getFolderName(ResourceFolderType.MENU))
      .isEqualTo("menu-en-rUS")
    assertThat(getFolderConfiguration(file1.virtualFile)?.getFolderName(ResourceFolderType.LAYOUT))
      .isEqualTo("layout-land")
    assertThat(getFolderConfiguration(file2.virtualFile)?.getFolderName(ResourceFolderType.MENU))
      .isEqualTo("menu-en-rUS")
  }

  @Test
  fun disabledStateListStates() {
    val disabled =
      StateListState("value", ImmutableMap.of<String, Boolean>("state_enabled", false), null)
    val disabledPressed =
      StateListState(
        "value",
        ImmutableMap.of<String, Boolean>("state_enabled", false, "state_pressed", true),
        null,
      )
    val pressed =
      StateListState("value", ImmutableMap.of<String, Boolean>("state_pressed", true), null)
    val enabledPressed =
      StateListState(
        "value",
        ImmutableMap.of<String, Boolean>("state_enabled", true, "state_pressed", true),
        null,
      )
    val enabled =
      StateListState("value", ImmutableMap.of<String, Boolean>("state_enabled", true), null)
    val selected =
      StateListState("value", ImmutableMap.of<String, Boolean>("state_selected", true), null)
    val selectedPressed =
      StateListState(
        "value",
        ImmutableMap.of<String, Boolean>("state_selected", true, "state_pressed", true),
        null,
      )
    val enabledSelectedPressed =
      StateListState(
        "value",
        ImmutableMap.of<String, Boolean>(
          "state_enabled",
          true,
          "state_selected",
          true,
          "state_pressed",
          true,
        ),
        null,
      )
    val notFocused =
      StateListState("value", ImmutableMap.of<String, Boolean>("state_focused", false), null)
    val notChecked =
      StateListState("value", ImmutableMap.of<String, Boolean>("state_checked", false), null)
    val checkedNotPressed =
      StateListState(
        "value",
        ImmutableMap.of<String, Boolean>("state_checked", true, "state_pressed", false),
        null,
      )

    with(StateList("stateList", "colors")) {
      addState(pressed)
      addState(disabled)
      addState(selected)
      assertThat(disabledStates).containsExactly(disabled)
      addState(disabledPressed)
      assertThat(disabledStates).containsExactly(disabled, disabledPressed)
    }

    with(StateList("stateList", "colors")) {
      addState(enabled)
      addState(pressed)
      addState(selected)
      addState(enabledPressed) // Not reachable
      addState(disabled)
      assertThat(disabledStates).containsExactly(pressed, selected, enabledPressed, disabled)
    }

    with(StateList("stateList", "colors")) {
      addState(enabledPressed)
      addState(pressed)
      addState(selected)
      addState(disabled)
      assertThat(disabledStates).containsExactly(pressed, disabled)

      addState(selectedPressed)
      assertThat(disabledStates).containsExactly(pressed, disabled, selectedPressed)
    }

    with(StateList("stateList", "colors")) {
      addState(enabledSelectedPressed)
      addState(pressed)
      addState(selected)
      addState(disabled)
      addState(selectedPressed)
      assertThat(disabledStates).containsExactly(disabled, selectedPressed)
    }

    with(StateList("stateList", "colors")) {
      addState(enabledPressed)
      addState(notChecked)
      addState(checkedNotPressed)
      addState(selected)
      addState(notFocused)
      assertThat(disabledStates).containsExactly(selected, notFocused)
    }
  }

  @Test
  fun resolveAsIconFromColorReference() {
    val file = fixture.copyFileToProject("resourceHelper/values.xml", "res/values/values.xml")

    val url = ResourceUrl.parse("@color/myColor2")
    val reference =
      assertNotNull(
        url?.resolve(ResourceNamespace.TODO(), ResourceNamespace.Resolver.EMPTY_RESOLVER)
      )
    val rr =
      ConfigurationManager.getOrCreateInstance(module).getConfiguration(file).getResourceResolver()
    val value = rr.getResolvedResource(reference)
    val icon = rr.resolveAsIcon(value, facet)
    assertThat(icon).isEqualTo(ColorIcon(16, Color(0xEEDDCC)))
  }

  @Test
  fun resolveAsIconFromColorStateList() {
    fixture.copyFileToProject("resourceHelper/values.xml", "res/values/values.xml")
    val file =
      fixture.copyFileToProject("resourceHelper/my_state_list.xml", "res/color/my_state_list.xml")

    val url = ResourceUrl.parse("@color/my_state_list")
    val reference =
      assertNotNull(
        url?.resolve(ResourceNamespace.TODO(), ResourceNamespace.Resolver.EMPTY_RESOLVER)
      )
    val rr =
      ConfigurationManager.getOrCreateInstance(module).getConfiguration(file).getResourceResolver()
    val value = rr.getResolvedResource(reference)
    val icon = rr.resolveAsIcon(value, facet)
    assertThat(icon).isEqualTo(ColorsIcon(16, Color(0xEEDDCC), Color(0x33123456, true)))
  }

  @Test
  fun resolveAsIconFromDrawable() {
    fixture.copyFileToProject("resourceHelper/values.xml", "res/values/values.xml")
    val file =
      fixture.copyFileToProject("resourceHelper/ic_delete.png", "res/drawable/ic_delete.png")
    val url = ResourceUrl.parse("@drawable/ic_delete")
    val reference =
      assertNotNull(
        url?.resolve(ResourceNamespace.TODO(), ResourceNamespace.Resolver.EMPTY_RESOLVER)
      )
    val rr =
      ConfigurationManager.getOrCreateInstance(module).getConfiguration(file).getResourceResolver()
    val value = rr.getResolvedResource(reference)
    val icon = assertNotNull(rr.resolveAsIcon(value, facet))
    val image = BufferedImage(icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB)
    icon.paintIcon(null, image.graphics, 0, 0)
    val goldenImage = ImageIO.read(File(fixture.testDataPath + "/resourceHelper/ic_delete.png"))
    assertImageSimilar(
      "ic_delete",
      goldenImage,
      image,
      ImageDiffTestUtil.DEFAULT_IMAGE_DIFF_THRESHOLD_PERCENT,
    )
  }

  @Test
  fun resolveAsIconFromStateListDrawable() {
    fixture.copyFileToProject("resourceHelper/ic_delete.png", "res/drawable/ic_delete.png")
    val file =
      fixture.copyFileToProject(
        "resourceHelper/icon_state_list.xml",
        "res/drawable/icon_state_list.xml",
      )
    val url = ResourceUrl.parse("@drawable/icon_state_list")
    val reference =
      assertNotNull(
        url?.resolve(ResourceNamespace.TODO(), ResourceNamespace.Resolver.EMPTY_RESOLVER)
      )
    val rr =
      ConfigurationManager.getOrCreateInstance(module).getConfiguration(file).getResourceResolver()
    val value = rr.getResolvedResource(reference)
    val iconFile = assertNotNull(rr.resolveDrawable(value, project))
    assertThat(iconFile.name).isEqualTo("ic_delete.png")
    assertThat(iconFile.exists()).isTrue()
  }

  @Test
  fun resolveEmptyStateList() {
    val file =
      fixture.copyFileToProject(
        "resourceHelper/empty_state_list.xml",
        "res/color/empty_state_list.xml",
      )
    val rr =
      ConfigurationManager.getOrCreateInstance(module).getConfiguration(file).getResourceResolver()
    val rv =
      rr.getResolvedResource(
        ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "empty_state_list")
      )
    assertThat(rv).isNotNull()
    assertThat(rr.resolveColor(rv, module.project)).isNull()
  }

  @Test
  fun resolve() {
    val appNs = ResourceNamespace.fromPackageName("com.example.app")
    setProjectNamespace(appNs)

    val innerFileLand = fixture.addFileToProject("res/layout-land/inner.xml", "<LinearLayout/>")
    val innerFilePort = fixture.addFileToProject("res/layout-port/inner.xml", "<LinearLayout/>")
    val outerFileContent =
      """
      <?xml version="1.0" encoding="utf-8"?>
      <FrameLayout xmlns:app="${appNs.xmlNamespaceUri}">

          <include
              layout="@app:layout/inner"
              android:layout_width="wrap_content"
              android:layout_height="wrap_content" />

      </FrameLayout>"
      """
        .trimIndent()
    val outerFile = fixture.addFileToProject("res/layout/outer.xml", outerFileContent) as XmlFile
    val configuration: Configuration =
      ConfigurationManager.getOrCreateInstance(module).getConfiguration(innerFileLand.virtualFile)
    val include =
      application.runReadAction<XmlTag, Nothing> {
        assertNotNull(outerFile.rootTag?.findFirstSubTag("include"))
      }
    fun getResolvedResourceValue() =
      application.runReadAction<String, Nothing> {
        val value = assertNotNull(include.getAttribute("layout")?.value)
        val resourceUrl = assertNotNull(ResourceUrl.parse(value))
        assertNotNull(configuration.getResourceResolver().resolve(resourceUrl, include)).value
      }
    assertThat(getResolvedResourceValue()).isEqualTo(innerFileLand.virtualFile.path)
    configuration.deviceState = configuration.device?.getState("Portrait")
    assertThat(getResolvedResourceValue()).isEqualTo(innerFilePort.virtualFile.path)
  }

  @RunsInEdt
  @Test
  fun getResourceResolverFromXmlTag_namespacesEnabled() {
    setProjectNamespace(ResourceNamespace.fromPackageName("com.example.app"))

    val file = fixture.addFileToProject("res/layout/simple.xml", LAYOUT_FILE) as XmlFile
    val layout = assertNotNull(file.rootTag)
    val textView = assertNotNull(layout.findFirstSubTag("TextView"))

    var layoutResolver = getNamespaceResolver(layout)
    assertThat(layoutResolver.uriToPrefix(SdkConstants.TOOLS_URI)).isNull()
    assertThat(layoutResolver.uriToPrefix(SdkConstants.ANDROID_URI)).isEqualTo("framework")
    assertThat(layoutResolver.prefixToUri("newtools")).isNull()
    assertThat(layoutResolver.prefixToUri("framework")).isEqualTo(SdkConstants.ANDROID_URI)

    val textViewResolver = getNamespaceResolver(textView)
    assertThat(textViewResolver.uriToPrefix(SdkConstants.TOOLS_URI)).isEqualTo("newtools")
    assertThat(textViewResolver.uriToPrefix(SdkConstants.ANDROID_URI)).isEqualTo("framework")
    assertThat(textViewResolver.prefixToUri("newtools")).isEqualTo(SdkConstants.TOOLS_URI)
    assertThat(textViewResolver.prefixToUri("framework")).isEqualTo(SdkConstants.ANDROID_URI)
  }

  private fun setProjectNamespace(appNs: ResourceNamespace) {
    application.invokeAndWait {
      CommandProcessor.getInstance().runUndoTransparentAction {
        application.runWriteAction {
          AndroidModel.set(facet, namespaced(facet))
          val manifest = assertNotNull(Manifest.getMainManifest(facet))
          manifest.getPackage().value = appNs.packageName
        }
      }
    }
  }

  @RunsInEdt
  @Test
  fun getResourceResolverFromXmlTag_namespacesDisabled() {
    val file = fixture.addFileToProject("res/layout/simple.xml", LAYOUT_FILE) as XmlFile
    val layout = file.rootTag
    val textview = assertNotNull(layout?.findFirstSubTag("TextView"))

    var resolver = getNamespaceResolver(layout)

    // "tools" is implicitly defined in non-namespaced projects.
    assertThat(resolver.uriToPrefix(SdkConstants.TOOLS_URI)).isEqualTo("tools")

    // Proper namespacing doesn't work in non-namespaced projects, so within XML attributes, only
    // "android" works.
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

  @RunsInEdt
  @Test
  fun psiElementGetNamespace() {
    // Project XML:
    val layoutFile = fixture.addFileToProject("res/layout/simple.xml", LAYOUT_FILE) as XmlFile
    assertThat(layoutFile.resourceNamespace).isEqualTo(ResourceNamespace.RES_AUTO)
    assertThat(layoutFile.rootTag?.resourceNamespace).isEqualTo(ResourceNamespace.RES_AUTO)

    // Project class:
    val projectClass = fixture.addClass("package com.example; public class Hello {}")
    assertThat(projectClass.resourceNamespace).isEqualTo(ResourceNamespace.RES_AUTO)

    // Project R class:
    val rClass = fixture.javaFacade.findClass("p1.p2.R", projectClass.resolveScope)
    assertThat(rClass?.resourceNamespace).isEqualTo(ResourceNamespace.RES_AUTO)

    // Project manifest:
    val manifest = assertNotNull(Manifest.getMainManifest(facet))
    assertThat(manifest.xmlElement?.resourceNamespace).isEqualTo(ResourceNamespace.RES_AUTO)
    assertThat(manifest.xmlElement?.containingFile?.resourceNamespace)
      .isEqualTo(ResourceNamespace.RES_AUTO)

    // Project Manifest class:
    WriteCommandAction.runWriteCommandAction(
      project,
      Runnable {
        val newPermission = manifest.addPermission()
        newPermission.getName().value = "p1.p2.NEW_PERMISSION"
      },
    )
    val manifestClass = fixture.javaFacade.findClass("p1.p2.Manifest", projectClass.resolveScope)
    assertThat(manifestClass?.resourceNamespace).isEqualTo(ResourceNamespace.RES_AUTO)

    // Framework class:
    val frameworkClass = fixture.javaFacade.findClass("android.app.Activity")
    assertThat(frameworkClass.resourceNamespace).isEqualTo(ResourceNamespace.ANDROID)

    // Framework XML: API28 has two default app icons: res/drawable-watch/sym_def_app_icon.xml and
    // res/drawable/sym_def_app_icon.xml
    val appIconResourceItems =
      StudioResourceRepositoryManager.getInstance(facet)
        .getFrameworkResources(setOf())
        ?.getResources(ResourceNamespace.ANDROID, ResourceType.DRAWABLE, "sym_def_app_icon")

    assertNotNull(appIconResourceItems)

    for (appIconResourceItem in appIconResourceItems) {
      val virtualFile = assertNotNull(appIconResourceItem.source.toVirtualFile())
      val appIcon = PsiManager.getInstance(project).findFile(virtualFile) as? XmlFile
      assertThat(appIcon?.resourceNamespace).isEqualTo(ResourceNamespace.ANDROID)
      assertThat(appIcon?.rootTag?.resourceNamespace).isEqualTo(ResourceNamespace.ANDROID)
    }
  }

  @RunsInEdt
  @Test
  fun caseSensitivityInChangeColorResource() {
    val xmlFile = fixture.copyFileToProject("util/colors_before.xml", "res/values/colors.xml")
    val resDir = xmlFile.parent.parent
    val dirNames = listOf("values")
    assertThat(
        changeValueResource(
          project,
          resDir,
          "myColor",
          ResourceType.COLOR,
          "#000000",
          "colors.xml",
          dirNames,
          false,
        )
      )
      .isTrue()
    assertThat(
        changeValueResource(
          project,
          resDir,
          "mycolor",
          ResourceType.COLOR,
          "#FFFFFF",
          "colors.xml",
          dirNames,
          false,
        )
      )
      .isFalse()
    fixture.checkResultByFile("res/values/colors.xml", "util/colors_after.xml", true)
  }

  @Test
  fun findResourceFields() {
    fixture.copyFileToProject("util/strings.xml", "res/values/strings.xml")

    val fields =
      application.runReadAction<Array<PsiField>, Nothing> {
        findResourceFields(facet, "string", "hello")
      }
    assertThat(fields.size).isEqualTo(1)
    with(fields.single()) {
      assertThat(name).isEqualTo("hello")
      assertThat(containingClass?.name).isEqualTo("string")
      assertThat(containingClass?.containingClass?.qualifiedName).isEqualTo("p1.p2.R")
    }
  }

  @Test
  fun findResourceFieldsWithMultipleResourceNames() {
    fixture.copyFileToProject("util/strings.xml", "res/values/strings.xml")

    val fields =
      application.runReadAction<Array<PsiField>, Nothing> {
        findResourceFields(facet, "string", listOf("hello", "goodbye"))
      }
    assertThat(fields.size).isEqualTo(2)
    assertThat(fields.map { it.containingClass?.containingClass?.qualifiedName }.distinct())
      .containsExactly("p1.p2.R")

    assertThat(fields.map(PsiField::getName)).containsExactly("hello", "goodbye")
  }

  @RunsInEdt
  @Test
  fun ensureNamespaceImportedAddAuto() {
    val xmlFile = ensureNamespaceImported("<LinearLayout/>", SdkConstants.AUTO_URI, null)
    assertThat(xmlFile.text)
      .isEqualTo("<LinearLayout xmlns:app=\"http://schemas.android.com/apk/res-auto\" />")
  }

  @RunsInEdt
  @Test
  fun ensureNamespaceImportedAddAutoWithPrefixSuggestion() {
    val xmlFile = ensureNamespaceImported("<LinearLayout/>", SdkConstants.AUTO_URI, "sherpa")
    assertThat(xmlFile.text)
      .isEqualTo("<LinearLayout xmlns:sherpa=\"http://schemas.android.com/apk/res-auto\" />")
  }

  @RunsInEdt
  @Test
  fun ensureNamespaceImportedDoNotAddAutoIfAlreadyThere() {
    val xmlFile =
      ensureNamespaceImported(
        "<LinearLayout xmlns:app=\"http://schemas.android.com/apk/res-auto\" />",
        SdkConstants.AUTO_URI,
        null,
      )
    assertThat(xmlFile.text)
      .isEqualTo("<LinearLayout xmlns:app=\"http://schemas.android.com/apk/res-auto\" />")
  }

  @RunsInEdt
  @Test
  fun ensureNamespaceImportedDoNotAddAutoIfAlreadyThereWithPrefixSuggestion() {
    val xmlFile =
      ensureNamespaceImported(
        "<LinearLayout xmlns:app=\"http://schemas.android.com/apk/res-auto\" />",
        SdkConstants.AUTO_URI,
        "sherpa",
      )
    assertThat(xmlFile.text)
      .isEqualTo("<LinearLayout xmlns:app=\"http://schemas.android.com/apk/res-auto\" />")
  }

  @RunsInEdt
  @Test
  fun ensureNamespaceImportedAddEmptyNamespaceForStyleAttribute() {
    val xmlFile = ensureNamespaceImported("<LinearLayout/>", "", null)
    assertThat(xmlFile.text).isEqualTo("<LinearLayout/>")
  }

  private fun ensureNamespaceImported(
    @Language("XML") text: String,
    namespaceUri: String,
    suggestedPrefix: String?,
  ): XmlFile {
    val xmlFile = fixture.addFileToProject("res/layout/layout.xml", text) as XmlFile

    CommandProcessor.getInstance()
      .executeCommand(
        project,
        Runnable {
          ApplicationManager.getApplication()
            .runWriteAction(
              Runnable { ensureNamespaceImported(xmlFile, namespaceUri, suggestedPrefix) }
            )
        },
        "",
        "",
      )

    return xmlFile
  }

  @RunsInEdt
  @Test
  fun createRawFileResource() {
    val fileName = "my_great_raw_file.foobar"
    val rawDirName = "raw"
    val file = createRawFileResource(fileName, getResDirectory(rawDirName))
    assertThat(file.containingDirectory.name).isEqualTo(rawDirName)
    assertThat(file.name).isEqualTo(fileName)
    assertThat(file.text).isEmpty()
  }

  @RunsInEdt
  @Test
  fun createFrameLayoutFileResource() {
    val file =
      createXmlFileResource(
        "linear",
        getResDirectory("layout"),
        SdkConstants.FRAME_LAYOUT,
        ResourceType.LAYOUT,
        false,
      )
    assertThat(file.name).isEqualTo("linear.xml")
    assertThat(file.text)
      .isEqualTo(
        """
      <?xml version="1.0" encoding="utf-8"?>
      <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
          android:layout_width="match_parent"
          android:layout_height="match_parent">

      </FrameLayout>
      """
          .trimIndent()
      )
  }

  @RunsInEdt
  @Test
  fun createLinearLayoutFileResource() {
    val file =
      createXmlFileResource(
        "linear",
        getResDirectory("layout"),
        SdkConstants.LINEAR_LAYOUT,
        ResourceType.LAYOUT,
        false,
      )
    assertThat(file.name).isEqualTo("linear.xml")
    assertThat(file.text)
      .isEqualTo(
        """
      <?xml version="1.0" encoding="utf-8"?>
      <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
          android:orientation="vertical"
          android:layout_width="match_parent"
          android:layout_height="match_parent">

      </LinearLayout>
      """
          .trimIndent()
      )
  }

  @RunsInEdt
  @Test
  fun createLayoutFileResource() {
    val file =
      createXmlFileResource(
        "layout",
        getResDirectory("layout"),
        SdkConstants.TAG_LAYOUT,
        ResourceType.LAYOUT,
        false,
      )
    assertThat(file.name).isEqualTo("layout.xml")
    assertThat(file.text)
      .isEqualTo(
        """
      <?xml version="1.0" encoding="utf-8"?>
      <layout xmlns:android="http://schemas.android.com/apk/res/android">

      </layout>
      """
          .trimIndent()
      )
  }

  @RunsInEdt
  @Test
  fun createMergeFileResource() {
    val file =
      createXmlFileResource(
        "merge",
        getResDirectory("layout"),
        SdkConstants.VIEW_MERGE,
        ResourceType.LAYOUT,
        false,
      )
    assertThat(file.name).isEqualTo("merge.xml")
    assertThat(file.text)
      .isEqualTo(
        """
      <?xml version="1.0" encoding="utf-8"?>
      <merge xmlns:android="http://schemas.android.com/apk/res/android">

      </merge>
      """
          .trimIndent()
      )
  }

  @RunsInEdt
  @Test
  fun createNavigationFileResource() {
    val file =
      createXmlFileResource(
        "nav",
        getResDirectory("navigation"),
        SdkConstants.TAG_NAVIGATION,
        ResourceType.NAVIGATION,
        false,
      )
    assertThat(file.name).isEqualTo("nav.xml")
    assertThat(file.text)
      .isEqualTo(
        """
      <?xml version="1.0" encoding="utf-8"?>
      <navigation xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:app="http://schemas.android.com/apk/res-auto"
          android:id="@+id/nav">

      </navigation>
      """
          .trimIndent()
      )
  }

  @Test
  fun buildResourceNameFromStringValue_simpleName() {
    assertThat(buildResourceNameFromStringValue("Just simple string"))
      .isEqualTo("just_simple_string")
  }

  @Test
  fun buildResourceNameFromStringValue_nameWithSurroundingSpaces() {
    assertThat(buildResourceNameFromStringValue(" Just a simple string "))
      .isEqualTo("just_a_simple_string")
  }

  @Test
  fun buildResourceNameFromStringValue_nameWithDigits() {
    assertThat(buildResourceNameFromStringValue("A string with 31337 number"))
      .isEqualTo("a_string_with_31337_number")
  }

  @Test
  fun buildResourceNameFromStringValue_nameShouldNotStartWithNumber() {
    assertThat(buildResourceNameFromStringValue("100 things")).isEqualTo("_100_things")
  }

  @Test
  fun buildResourceNameFromStringValue_emptyString() {
    assertThat(buildResourceNameFromStringValue("")).isNull()
  }

  @Test
  fun buildResourceNameFromStringValue_stringHasPunctuation() {
    assertThat(buildResourceNameFromStringValue("Hello!!#^ But why??")).isEqualTo("hello_but_why")
  }

  @Test
  fun buildResourceNameFromStringValue_stringIsOnlyPunctuation() {
    assertThat(buildResourceNameFromStringValue("!!#^??")).isNull()
  }

  @Test
  fun buildResourceNameFromStringValue_stringStartsAndEndsWithPunctuation() {
    assertThat(buildResourceNameFromStringValue("\"A quotation\"")).isEqualTo("a_quotation")
    assertThat(buildResourceNameFromStringValue("<tag>")).isEqualTo("tag")
  }

  private fun getResDirectory(dirName: String): PsiDirectory {
    val virtualFileDir =
      assertNotNull(
        VirtualFileManager.getInstance().findFileByNioPath(Path.of(fixture.tempDirPath))
      )
    val dir =
      application.runReadAction<PsiDirectory, Nothing> {
        assertNotNull(PsiManager.getInstance(project).findDirectory(virtualFileDir))
      }
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
      """
        .trimIndent()

    private fun findOrCreateSubdirectory(parent: PsiDirectory, subdirName: String) =
      parent.findSubdirectory(subdirName)
        ?: WriteAction.compute<PsiDirectory, IncorrectOperationException> {
          parent.createSubdirectory(subdirName)
        }
  }
}

class IdeResourcesUtilAdditionalModulesTest : AndroidTestCase() {
  override fun setUp() {
    super.setUp()
    MergedManifestModificationListener.ensureSubscribed(project)
  }

  override fun configureAdditionalModules(
    projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture>,
    modules: List<MyAdditionalModuleData>,
  ) {
    addModuleWithAndroidFacet(
      projectBuilder,
      modules,
      "lib",
      AndroidProjectTypes.PROJECT_TYPE_LIBRARY,
    )
  }

  /**
   * Tests that "inherited" resource references are found (R fields in generated in dependent
   * modules).
   */
  fun testFindResourceFieldsWithInheritance() {
    // Remove the current lib manifest (has wrong package name) and copy a manifest with proper
    // package into the lib module.
    val libModule = myAdditionalModules.single()
    deleteManifest(libModule)

    myFixture.copyFileToProject(
      "util/lib/AndroidManifest.xml",
      "additionalModules/lib/AndroidManifest.xml",
    )

    // Add some lib string resources.
    myFixture.copyFileToProject(
      "util/lib/strings.xml",
      "additionalModules/lib/res/values/strings.xml",
    )

    val facet = kotlin.test.assertNotNull(AndroidFacet.getInstance(libModule))
    val fields = findResourceFields(facet, "string", "lib_hello")
    assertThat(fields).hasLength(2)

    val packages =
      fields
        .mapNotNull { it.containingClass?.containingClass?.qualifiedName }
        .map(StringUtil::getPackageName)
    assertThat(packages).containsExactly("p1.p2", "p1.p2.lib")
  }

  /** Tests that a module without an Android Manifest can still import a lib's R class */
  fun testIsRJavaFileImportedNoManifest() {
    // Remove the current lib manifest (has wrong package name) and copy a manifest with proper
    // package into the lib module.
    deleteManifest(myAdditionalModules.single())

    myFixture.copyFileToProject(
      "util/lib/AndroidManifest.xml",
      "additionalModules/lib/AndroidManifest.xml",
    )

    // Add some lib string resources.
    myFixture.copyFileToProject(
      "util/lib/strings.xml",
      "additionalModules/lib/res/values/strings.xml",
    )
    // Remove the manifest from the main module.
    deleteManifest(myModule)

    // The main module doesn't get a generated R class and inherit fields (lack of manifest)
    val facet = kotlin.test.assertNotNull(AndroidFacet.getInstance(myModule))
    val mainFields = findResourceFields(facet, "string", "lib_hello" /* onlyInOwnPackages */)
    assertThat(mainFields).isEmpty()

    // However, if the main module happens to get a handle on the lib's R class
    // (e.g., via "import p1.p2.lib.R;"), then that R class should be recognized
    // (e.g., for goto navigation).
    val javaFile =
      myFixture.addFileToProject("src/com/example/Foo.java", "package com.example; class Foo {}")
    val libRClass = myFixture.javaFacade.findClass("p1.p2.lib.R", javaFile.resolveScope)
    kotlin.test.assertNotNull(libRClass)
    assertThat(isRJavaClass(libRClass)).isTrue()
  }
}
