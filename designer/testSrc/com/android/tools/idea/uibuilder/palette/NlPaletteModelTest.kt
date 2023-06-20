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
package com.android.tools.idea.uibuilder.palette

import com.android.SdkConstants
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.projectsystem.TestProjectSystem
import com.android.tools.idea.testing.AndroidProjectRule.Companion.onDisk
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager
import com.android.tools.idea.uibuilder.handlers.linear.LinearLayoutHandler
import com.android.tools.idea.uibuilder.palette.Palette.BaseItem
import com.android.tools.idea.uibuilder.type.LayoutEditorFileType
import com.android.tools.idea.uibuilder.type.LayoutFileType
import com.android.tools.idea.uibuilder.type.MenuFileType
import com.android.tools.idea.uibuilder.type.PreferenceScreenFileType
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiClass
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.util.CollectionQuery
import icons.StudioIcons
import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

class NlPaletteModelTest {
  private var facet: AndroidFacet? = null
  private var fixture: JavaCodeInsightTestFixture? = null
  private var model: NlPaletteModel? = null
  private var projectSystem: TestProjectSystem? = null
  private val projectRule = onDisk().initAndroid(true)

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  @Before
  fun setUp() {
    fixture = projectRule.fixture
    facet = AndroidFacet.getInstance(projectRule.module)
    model = NlPaletteModel.get(facet!!)
    projectSystem = TestProjectSystem(projectRule.project)
    runInEdt {
      projectSystem!!.useInTests()
    }
  }

  @After
  fun tearDown() {
    model = null
    facet = null
    fixture = null
  }

  @Test
  fun addIllegalThirdPartyComponent() {
    val layoutFileType = LayoutFileType
    val palette = model!!.getPalette(layoutFileType)
    val added = model!!.addAdditionalComponent(
      layoutFileType, NlPaletteModel.PROJECT_GROUP, palette, null, SdkConstants.LINEAR_LAYOUT,
      SdkConstants.LINEAR_LAYOUT, null, null, SdkConstants.CONSTRAINT_LAYOUT_LIB_ARTIFACT, null, emptyList(), emptyList()
    )
    assertThat(added).isFalse()
    assertThat(getProjectGroup(palette)).isNull()
    val handler = ViewHandlerManager.get(facet!!).getHandler(SdkConstants.LINEAR_LAYOUT)
    assertThat(handler).isInstanceOf(LinearLayoutHandler::class.java)
  }

  @Test
  fun addThirdPartyComponent() {
    registerJavaClasses()
    registerFakeBaseViewHandler()
    val palette = getPaletteWhenAdditionalComponentsReady(model)
    val thirdParty = getProjectGroup(palette)
    assertThat(thirdParty).isNotNull()
    val items = thirdParty!!.items.stream()
      .map { item: BaseItem -> item as Palette.Item }
      .sorted(Comparator.comparing { it.tagName })
      .collect(Collectors.toList())
    assertThat(items.size).isEqualTo(2)

    @Language("XML")
    val expectedViewXml = """
      <com.example.FakeCustomView
          android:layout_width="wrap_content"
          android:layout_height="wrap_content" />
      """.trimIndent()

    @Language("XML")
    val expectedViewGroupXml = """
      <com.example.FakeCustomViewGroup
          android:layout_width="match_parent"
          android:layout_height="match_parent" />
      """.trimIndent()

    val item1 = items[0]
    assertThat(item1.tagName).isEqualTo(CUSTOM_VIEW_CLASS)
    assertThat(item1.icon).isEqualTo(StudioIcons.LayoutEditor.Palette.CUSTOM_VIEW)
    assertThat(item1.title).isEqualTo(CUSTOM_VIEW)
    assertThat(item1.gradleCoordinateId).isEmpty()
    assertThat(item1.xml.trim()).isEqualTo(expectedViewXml)
    val item2 = items[1]
    assertThat(item2.tagName).isEqualTo(CUSTOM_VIEW_GROUP_CLASS)
    assertThat(item2.icon).isEqualTo(StudioIcons.LayoutEditor.Palette.CUSTOM_VIEW)
    assertThat(item2.title).isEqualTo(CUSTOM_VIEW_GROUP)
    assertThat(item2.gradleCoordinateId).isEmpty()
    assertThat(item2.xml.trim()).isEqualTo(expectedViewGroupXml)
    val handler = ViewHandlerManager.get(facet!!).getHandler(CUSTOM_VIEW_CLASS)
    assertThat(handler).isNotNull()
    assertThat(handler!!.getTitle(CUSTOM_VIEW_CLASS)).isEqualTo(CUSTOM_VIEW)
    assertThat(handler.getIcon(CUSTOM_VIEW_CLASS)).isEqualTo(StudioIcons.LayoutEditor.Palette.CUSTOM_VIEW)
    assertThat(handler.getGradleCoordinateId(CUSTOM_VIEW_CLASS)).isEmpty()
    assertThat(handler.getPreviewScale(CUSTOM_VIEW_CLASS)).isWithin(0.0).of(1.0)
    assertThat(handler.inspectorProperties).isEmpty()
    assertThat(handler.layoutInspectorProperties).isEmpty()
    assertThat(handler.preferredProperty).isNull()
  }

  @Test
  fun addThirdPartyComponentTwice() {
    registerJavaClasses()
    registerFakeBaseViewHandler()
    val palette = getPaletteWhenAdditionalComponentsReady(model)
    val added1 = model!!.addAdditionalComponent(
      LayoutFileType, NlPaletteModel.PROJECT_GROUP, palette, StudioIcons.Common.ANDROID_HEAD,
      CUSTOM_VIEW_CLASS, CUSTOM_VIEW_CLASS, getXml(CUSTOM_VIEW_CLASS),
      getPreviewXml(CUSTOM_VIEW_CLASS), "", "family", ImmutableList.of("family", "size"), emptyList()
    )
    val handler1 = ViewHandlerManager.get(facet!!).getHandler(CUSTOM_VIEW_CLASS)
    val added2 = model!!.addAdditionalComponent(
      LayoutFileType, NlPaletteModel.PROJECT_GROUP, palette, StudioIcons.Common.ANDROID_HEAD,
      CUSTOM_VIEW_CLASS, CUSTOM_VIEW_CLASS, getXml(CUSTOM_VIEW_CLASS),
      getPreviewXml(CUSTOM_VIEW_CLASS), "", "family", ImmutableList.of("family", "size"), emptyList()
    )
    val handler2 = ViewHandlerManager.get(facet!!).getHandler(CUSTOM_VIEW_CLASS)
    assertThat(added1).isTrue()
    assertThat(added2).isTrue()
    assertThat(handler1).isSameAs(handler2)
  }

  @Test
  fun addThirdPartyGroupComponentTwice() {
    registerJavaClasses()
    registerFakeBaseViewHandler()
    val palette = getPaletteWhenAdditionalComponentsReady(model)
    val added1 = model!!.addAdditionalComponent(
      LayoutFileType, NlPaletteModel.PROJECT_GROUP, palette, StudioIcons.Common.ANDROID_HEAD,
      CUSTOM_VIEW_GROUP_CLASS, CUSTOM_VIEW_GROUP_CLASS, getXml(CUSTOM_VIEW_GROUP_CLASS),
      getPreviewXml(CUSTOM_VIEW_GROUP_CLASS), "", "family", ImmutableList.of("family", "size"), emptyList()
    )
    val handler1 = ViewHandlerManager.get(facet!!).getHandler(CUSTOM_VIEW_GROUP_CLASS)
    val added2 = model!!.addAdditionalComponent(
      LayoutFileType, NlPaletteModel.PROJECT_GROUP, palette, StudioIcons.Common.ANDROID_HEAD,
      CUSTOM_VIEW_GROUP_CLASS, CUSTOM_VIEW_GROUP_CLASS, getXml(CUSTOM_VIEW_GROUP_CLASS),
      getPreviewXml(CUSTOM_VIEW_GROUP_CLASS), "", "family", ImmutableList.of("family", "size"), emptyList()
    )
    val handler2 = ViewHandlerManager.get(facet!!).getHandler(CUSTOM_VIEW_GROUP_CLASS)
    assertThat(added1).isTrue()
    assertThat(added2).isTrue()
    assertThat(handler1).isSameAs(handler2)
  }

  @Test
  fun projectComponents() {
    //registerJavaClasses();
    var palette = getPaletteWhenAdditionalComponentsReady(model)
    var projectComponents = getProjectGroup(palette)
    assertThat(projectComponents).isNull()
    val latch = CountDownLatch(1)
    model!!.addUpdateListener { _, _ -> latch.countDown() }
    model!!.loadAdditionalComponents(LayoutFileType) {
      val customView: PsiClass = mock()
      whenever(customView.name).thenReturn(CUSTOM_VIEW)
      whenever(customView.qualifiedName).thenReturn(CUSTOM_VIEW_CLASS)
      CollectionQuery(ImmutableList.of(customView))
    }
    latch.await()
    palette = model!!.getPalette(LayoutFileType)
    projectComponents = getProjectGroup(palette)
    assertThat(projectComponents!!.items.size).isEqualTo(1)
    val item = projectComponents.getItem(0) as Palette.Item
    assertThat(item.tagName).isEqualTo(CUSTOM_VIEW_CLASS)
    assertThat(item.icon).isEqualTo(StudioIcons.LayoutEditor.Palette.CUSTOM_VIEW)
    assertThat(item.title).isEqualTo(CUSTOM_VIEW)
    assertThat(item.gradleCoordinateId).isEmpty()
    assertThat(item.xml.trim()).isEqualTo("""
      <com.example.FakeCustomView
          android:layout_width="wrap_content"
          android:layout_height="wrap_content" />
      """.trimIndent()
    )
    assertThat(item.metaTags).isEmpty()
    assertThat(item.parent).isEqualTo(projectComponents)
  }

  @Test
  fun idsAreUnique() {
    checkIdsAreUniqueInPalette(LayoutFileType)
    checkIdsAreUniqueInPalette(MenuFileType)
    checkIdsAreUniqueInPalette(PreferenceScreenFileType)
  }

  @Test
  fun testThirdPartyComponentsAreReloadedAfterBuild() {
    registerJavaClasses()
    registerFakeBaseViewHandler()
    var palette = getPaletteWhenAdditionalComponentsReady(model)
    var thirdParty = getProjectGroup(palette)
    assertThat(thirdParty!!.items.map { it.toString() }).containsExactly("FakeCustomView", "FakeCustomViewGroup")

    // Simulate a build where a new custom component was added:
    fixture!!.addClass("package com.example; public class AnotherFakeCustomView extends android.view.View {}")
    projectSystem!!.getBuildManager().compileProject()
    palette = getPaletteWhenAdditionalComponentsReady(model)
    thirdParty = getProjectGroup(palette)
    assertThat(thirdParty!!.items.map { it.toString() }).containsExactly("FakeCustomView", "FakeCustomViewGroup", "AnotherFakeCustomView")
  }

  private fun checkIdsAreUniqueInPalette(layoutType: LayoutEditorFileType) {
    val palette = model!!.getPalette(layoutType)
    val ids: MutableSet<String?> = HashSet()
    palette.accept { item: Palette.Item ->
      TestCase.assertTrue(
        "ID is not unique: " + item.id + " with layoutType: " + layoutType,
        ids.add(item.id)
      )
    }
    assertThat(ids).isNotEmpty()
  }

  private fun registerFakeBaseViewHandler() {
    val manager = ViewHandlerManager.get(facet!!)
    val handler = manager.getHandler(SdkConstants.VIEW)
    assertThat(handler).isNotNull()
    manager.registerHandler("com.example.FakeView", handler!!)
  }

  private fun registerJavaClasses() {
    fixture!!.addClass("package android.view; public class View {}")
    fixture!!.addClass("package android.view; public class ViewGroup extends View {}")
    fixture!!.addClass("package com.example; public class FakeCustomView extends android.view.View {}")
    fixture!!.addClass("package com.example; public class FakeCustomViewGroup extends android.view.ViewGroup {}")
  }

  companion object {
    private const val CUSTOM_VIEW_CLASS = "com.example.FakeCustomView"
    private const val CUSTOM_VIEW_GROUP_CLASS = "com.example.FakeCustomViewGroup"
    private val CUSTOM_VIEW = StringUtil.getShortName(CUSTOM_VIEW_CLASS)
    private val CUSTOM_VIEW_GROUP = StringUtil.getShortName(CUSTOM_VIEW_GROUP_CLASS)
    private fun getProjectGroup(palette: Palette): Palette.Group? {
      val groups = palette.items
      return groups.stream()
        .filter { obj: BaseItem? -> Palette.Group::class.java.isInstance(obj) }
        .map { obj: BaseItem? -> Palette.Group::class.java.cast(obj) }
        .filter { g: Palette.Group? -> NlPaletteModel.PROJECT_GROUP == g!!.name }
        .findFirst()
        .orElse(null)
    }

    private fun getPaletteWhenAdditionalComponentsReady(model: NlPaletteModel?): Palette {
      val latch = CountDownLatch(1)
      // We should receive one update: once the additional components are registered.
      val listener = NlPaletteModel.UpdateListener { _, _ -> latch.countDown() }
      model!!.addUpdateListener(listener)
      model.getPalette(LayoutFileType)
      if (!latch.await(5, TimeUnit.SECONDS)) {
        Assert.fail("Did not receive the expected listener callbacks")
      }
      model.removeUpdateListener(listener)
      return model.getPalette(LayoutFileType)
    }

    @Language("XML")
    private fun getXml(tag: String): String {
      return String.format("<%1\$s></%1\$s>", tag)
    }

    @Language("XML")
    private fun getPreviewXml(tag: String): String {
      return String.format("<%1\$s><TextView text=\"2\"/></%1\$s>", tag)
    }
  }
}
