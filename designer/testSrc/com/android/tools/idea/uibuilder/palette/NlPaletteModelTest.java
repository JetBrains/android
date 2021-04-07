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
package com.android.tools.idea.uibuilder.palette;

import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.google.common.truth.Truth.assertThat;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.SdkConstants;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.handlers.linear.LinearLayoutHandler;
import com.android.tools.idea.uibuilder.type.LayoutEditorFileType;
import com.android.tools.idea.uibuilder.type.LayoutFileType;
import com.android.tools.idea.uibuilder.type.MenuFileType;
import com.android.tools.idea.uibuilder.type.PreferenceScreenFileType;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.util.CollectionQuery;
import icons.StudioIcons;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class NlPaletteModelTest {
  private static final String CUSTOM_VIEW_CLASS = "com.example.FakeCustomView";
  private static final String CUSTOM_VIEW_GROUP_CLASS = "com.example.FakeCustomViewGroup";
  private static final String CUSTOM_VIEW = StringUtil.getShortName(CUSTOM_VIEW_CLASS);
  private static final String CUSTOM_VIEW_GROUP = StringUtil.getShortName(CUSTOM_VIEW_GROUP_CLASS);

  @Rule
  public final AndroidProjectRule projectRule = AndroidProjectRule.onDisk().initAndroid(true);
  private AndroidFacet facet;
  private JavaCodeInsightTestFixture fixture;
  private NlPaletteModel model;

  @Before
  public void setUp() throws Exception {
    fixture = projectRule.getFixture(JavaCodeInsightTestFixture.class);
    facet = AndroidFacet.getInstance(projectRule.getModule());
    model = NlPaletteModel.get(facet);
  }

  @After
  public void tearDown() {
      model = null;
      facet = null;
      fixture = null;
  }

  @Test
  public void addIllegalThirdPartyComponent() {
    LayoutFileType layoutFileType = LayoutFileType.INSTANCE;
    Palette palette = model.getPalette(layoutFileType);
    boolean added = model.addAdditionalComponent(layoutFileType, NlPaletteModel.PROJECT_GROUP, palette, null, LINEAR_LAYOUT,
                                                 LINEAR_LAYOUT, null, null, SdkConstants.CONSTRAINT_LAYOUT_LIB_ARTIFACT, null,
                                                 Collections.emptyList(), Collections.emptyList());
    assertThat(added).isFalse();
    assertThat(getProjectGroup(palette)).isNull();

    ViewHandler handler = ViewHandlerManager.get(facet).getHandler(LINEAR_LAYOUT);
    assertThat(handler).isInstanceOf(LinearLayoutHandler.class);
  }

  @Test
  public void addThirdPartyComponent() throws InterruptedException {
    registerJavaClasses();
    registerFakeBaseViewHandler();
    Palette palette = getPaletteWhenAdditionalComponentsReady(model);

    Palette.Group thirdParty = getProjectGroup(palette);
    assertThat(thirdParty).isNotNull();
    List<Palette.Item> items = thirdParty.getItems().stream()
      .map(item -> (Palette.Item)item)
      .sorted(Comparator.comparing(Palette.Item::getTagName))
      .collect(Collectors.toList());
    assertThat(items.size()).isEqualTo(2);

    @Language("XML")
    String expectedViewXml = "<com.example.FakeCustomView\n" +
                             "    android:layout_width=\"wrap_content\"\n" +
                             "    android:layout_height=\"wrap_content\" />\n";
    @Language("XML")
    String expectedViewGroupXml = "<com.example.FakeCustomViewGroup\n" +
                                  "    android:layout_width=\"match_parent\"\n" +
                                  "    android:layout_height=\"match_parent\" />\n";

    Palette.Item item1 = items.get(0);
    assertThat(item1.getTagName()).isEqualTo(CUSTOM_VIEW_CLASS);
    assertThat(item1.getIcon()).isEqualTo(StudioIcons.LayoutEditor.Palette.CUSTOM_VIEW);
    assertThat(item1.getTitle()).isEqualTo(CUSTOM_VIEW);
    assertThat(item1.getGradleCoordinateId()).isEmpty();
    assertThat(item1.getXml()).isEqualTo(expectedViewXml);

    Palette.Item item2 = items.get(1);
    assertThat(item2.getTagName()).isEqualTo(CUSTOM_VIEW_GROUP_CLASS);
    assertThat(item2.getIcon()).isEqualTo(StudioIcons.LayoutEditor.Palette.CUSTOM_VIEW);
    assertThat(item2.getTitle()).isEqualTo(CUSTOM_VIEW_GROUP);
    assertThat(item2.getGradleCoordinateId()).isEmpty();
    assertThat(item2.getXml()).isEqualTo(expectedViewGroupXml);

    ViewHandler handler = ViewHandlerManager.get(facet).getHandler(CUSTOM_VIEW_CLASS);
    assertThat(handler).isNotNull();
    assertThat(handler.getTitle(CUSTOM_VIEW_CLASS)).isEqualTo(CUSTOM_VIEW);
    assertThat(handler.getIcon(CUSTOM_VIEW_CLASS)).isEqualTo(StudioIcons.LayoutEditor.Palette.CUSTOM_VIEW);
    assertThat(handler.getGradleCoordinateId(CUSTOM_VIEW_CLASS)).isEmpty();
    assertThat(handler.getPreviewScale(CUSTOM_VIEW_CLASS)).isWithin(0.0).of(1.0);
    assertThat(handler.getInspectorProperties()).isEmpty();
    assertThat(handler.getLayoutInspectorProperties()).isEmpty();
    assertThat(handler.getPreferredProperty()).isNull();
  }

  @Test
  public void addThirdPartyComponentTwice() throws InterruptedException {
    registerJavaClasses();
    registerFakeBaseViewHandler();
    Palette palette = getPaletteWhenAdditionalComponentsReady(model);
    boolean added1 = model.addAdditionalComponent(LayoutFileType.INSTANCE, NlPaletteModel.PROJECT_GROUP, palette, StudioIcons.Common.ANDROID_HEAD,
                                                  CUSTOM_VIEW_CLASS, CUSTOM_VIEW_CLASS, getXml(CUSTOM_VIEW_CLASS),
                                                  getPreviewXml(CUSTOM_VIEW_CLASS), "", "family", ImmutableList.of("family", "size"),
                                                  Collections.emptyList());
    ViewHandler handler1 = ViewHandlerManager.get(facet).getHandler(CUSTOM_VIEW_CLASS);

    boolean added2 = model.addAdditionalComponent(LayoutFileType.INSTANCE, NlPaletteModel.PROJECT_GROUP, palette, StudioIcons.Common.ANDROID_HEAD,
                                                  CUSTOM_VIEW_CLASS, CUSTOM_VIEW_CLASS, getXml(CUSTOM_VIEW_CLASS),
                                                  getPreviewXml(CUSTOM_VIEW_CLASS), "", "family", ImmutableList.of("family", "size"),
                                                  Collections.emptyList());
    ViewHandler handler2 = ViewHandlerManager.get(facet).getHandler(CUSTOM_VIEW_CLASS);
    assertThat(added1).isTrue();
    assertThat(added2).isTrue();
    assertThat(handler1).isSameAs(handler2);
  }

  @Test
  public void addThirdPartyGroupComponentTwice() throws InterruptedException {
    registerJavaClasses();
    registerFakeBaseViewHandler();
    Palette palette = getPaletteWhenAdditionalComponentsReady(model);
    boolean added1 = model.addAdditionalComponent(LayoutFileType.INSTANCE, NlPaletteModel.PROJECT_GROUP, palette, StudioIcons.Common.ANDROID_HEAD,
                                                  CUSTOM_VIEW_GROUP_CLASS, CUSTOM_VIEW_GROUP_CLASS, getXml(CUSTOM_VIEW_GROUP_CLASS),
                                                  getPreviewXml(CUSTOM_VIEW_GROUP_CLASS), "", "family", ImmutableList.of("family", "size"),
                                                  Collections.emptyList());
    ViewHandler handler1 = ViewHandlerManager.get(facet).getHandler(CUSTOM_VIEW_GROUP_CLASS);

    boolean added2 = model.addAdditionalComponent(LayoutFileType.INSTANCE, NlPaletteModel.PROJECT_GROUP, palette, StudioIcons.Common.ANDROID_HEAD,
                                                  CUSTOM_VIEW_GROUP_CLASS, CUSTOM_VIEW_GROUP_CLASS, getXml(CUSTOM_VIEW_GROUP_CLASS),
                                                  getPreviewXml(CUSTOM_VIEW_GROUP_CLASS), "", "family", ImmutableList.of("family", "size"),
                                                  Collections.emptyList());
    ViewHandler handler2 = ViewHandlerManager.get(facet).getHandler(CUSTOM_VIEW_GROUP_CLASS);
    assertThat(added1).isTrue();
    assertThat(added2).isTrue();
    assertThat(handler1).isSameAs(handler2);
  }

  @Test
  public void projectComponents() throws InterruptedException {
    //registerJavaClasses();
    Palette palette = getPaletteWhenAdditionalComponentsReady(model);
    Palette.Group projectComponents = getProjectGroup(palette);
    assertThat(projectComponents).isNull();

    CountDownLatch latch = new CountDownLatch(1);
    model.addUpdateListener((paletteModel, layoutType) -> latch.countDown());

    model.loadAdditionalComponents(LayoutFileType.INSTANCE, (project) -> {
      PsiClass customView = mock(PsiClass.class);
      when(customView.getName()).thenReturn(CUSTOM_VIEW);
      when(customView.getQualifiedName()).thenReturn(CUSTOM_VIEW_CLASS);
      return new CollectionQuery<>(ImmutableList.of(customView));
    });
    latch.await();

    palette = model.getPalette(LayoutFileType.INSTANCE);
    projectComponents = getProjectGroup(palette);
    assertThat(projectComponents.getItems().size()).isEqualTo(1);

    Palette.Item item = (Palette.Item)projectComponents.getItem(0);
    assertThat(item.getTagName()).isEqualTo(CUSTOM_VIEW_CLASS);
    assertThat(item.getIcon()).isEqualTo(StudioIcons.LayoutEditor.Palette.CUSTOM_VIEW);
    assertThat(item.getTitle()).isEqualTo(CUSTOM_VIEW);
    assertThat(item.getGradleCoordinateId()).isEmpty();
    assertThat(item.getXml()).isEqualTo("<com.example.FakeCustomView\n" +
                                        "    android:layout_width=\"wrap_content\"\n" +
                                        "    android:layout_height=\"wrap_content\" />\n");
    assertThat(item.getMetaTags()).isEmpty();
    assertThat(item.getParent()).isEqualTo(projectComponents);
  }

  @Test
  public void idsAreUnique() {
    checkIdsAreUniqueInPalette(LayoutFileType.INSTANCE);
    checkIdsAreUniqueInPalette(MenuFileType.INSTANCE);
    checkIdsAreUniqueInPalette(PreferenceScreenFileType.INSTANCE);
  }

  private void checkIdsAreUniqueInPalette(@NotNull LayoutEditorFileType layoutType) {
    Palette palette = model.getPalette(layoutType);
    Set<String> ids = new HashSet<>();
    palette.accept(item -> assertTrue("ID is not unique: " + item.getId() + " with layoutType: " + layoutType, ids.add(item.getId())));
    assertThat(ids).isNotEmpty();
  }

  @Nullable
  private static Palette.Group getProjectGroup(@NotNull Palette palette) {
    List<Palette.BaseItem> groups = palette.getItems();
    return groups.stream()
      .filter(Palette.Group.class::isInstance)
      .map(Palette.Group.class::cast)
      .filter(g -> NlPaletteModel.PROJECT_GROUP.equals(g.getName()))
      .findFirst()
      .orElse(null);
  }

  private static Palette getPaletteWhenAdditionalComponentsReady(NlPaletteModel model) throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    // We should receive one update: once the additional components are registered.
    NlPaletteModel.UpdateListener listener = (m, t) -> latch.countDown();
    model.addUpdateListener(listener);
    model.getPalette(LayoutFileType.INSTANCE);
    if (!latch.await(5, TimeUnit.SECONDS)) {
      fail("Did not receive the expected listener callbacks");
    }
    model.removeUpdateListener(listener);
    return model.getPalette(LayoutFileType.INSTANCE);
  }

  private void registerFakeBaseViewHandler() {
    ViewHandlerManager manager = ViewHandlerManager.get(facet);
    ViewHandler handler = manager.getHandler(SdkConstants.VIEW);
    assertThat(handler).isNotNull();
    manager.registerHandler("com.example.FakeView", handler);
  }

  private void registerJavaClasses() {
    fixture.addClass("package android.view; public class View {}");
    fixture.addClass("package android.view; public class ViewGroup extends View {}");
    fixture.addClass("package com.example; public class FakeCustomView extends android.view.View {}");
    fixture.addClass("package com.example; public class FakeCustomViewGroup extends android.view.ViewGroup {}");
  }

  @Language("XML")
  @NotNull
  private static String getXml(@NotNull String tag) {
    return String.format("<%1$s></%1$s>", tag);
  }

  @Language("XML")
  @NotNull
  private static String getPreviewXml(@NotNull String tag) {
    return String.format("<%1$s><TextView text=\"2\"/></%1$s>", tag);
  }
}
