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

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.handlers.flexbox.FlexboxLayoutHandler;
import com.android.tools.idea.uibuilder.handlers.linear.LinearLayoutHandler;
import com.android.tools.idea.common.model.NlLayoutType;
import com.google.common.collect.ImmutableList;
import com.intellij.psi.PsiClass;
import com.intellij.util.CollectionQuery;
import icons.AndroidIcons;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NlPaletteModelTest extends PaletteTestCase {
  private NlPaletteModel model;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    model = NlPaletteModel.get(myFacet);

    try (Reader reader = new InputStreamReader(NlPaletteModel.class.getResourceAsStream(NlLayoutType.LAYOUT.getPaletteFileName()))) {
      model.loadPalette(reader, NlLayoutType.LAYOUT);
    }
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      model = null;
    }
    finally {
      super.tearDown();
    }
  }

  public void testPalette() throws Exception {
    Palette palette = model.getPalette(NlLayoutType.LAYOUT);
    Iterator<Palette.BaseItem> iterator = palette.getItems().iterator();
    Palette.Group widgets = assertIsGroup(iterator.next(), "Widgets");
    Palette.Group textFields = assertIsGroup(iterator.next(), "Text");
    Palette.Group layouts = assertIsGroup(iterator.next(), "Layouts");
    Palette.Group containers = assertIsGroup(iterator.next(), "Containers");
    Palette.Group images = assertIsGroup(iterator.next(), "Images");
    Palette.Group times = assertIsGroup(iterator.next(), "Date");
    Palette.Group transitions = assertIsGroup(iterator.next(), "Transitions");
    Palette.Group advanced = assertIsGroup(iterator.next(), "Advanced");
    Palette.Group google = assertIsGroup(iterator.next(), "Google");
    Palette.Group design = assertIsGroup(iterator.next(), "Design");
    Palette.Group appcompat = assertIsGroup(iterator.next(), "AppCompat");
    assertFalse(iterator.hasNext());

    iterator = widgets.getItems().iterator();
    assertButton(iterator.next());
    assertToggleButton(iterator.next());
    assertCheckBox(iterator.next());
    assertRadioButton(iterator.next());
    assertCheckedTextView(iterator.next());
    assertSpinner(iterator.next());
    assertNormalProgressBarItem(iterator.next());
    assertHorizontalProgressBarItem(iterator.next());
    assertSeekBar(iterator.next());
    assertDiscreteSeekBar(iterator.next());
    assertQuickContactBadge(iterator.next());
    assertRatingBar(iterator.next());
    assertSwitch(iterator.next());
    assertSpace(iterator.next());
    assertFalse(iterator.hasNext());

    iterator = textFields.getItems().iterator();
    assertTextViewItem(iterator.next());
    assertPlainTextEditText(iterator.next());
    assertEquals("Items in text fields group", 15, textFields.getItems().size());

    iterator = layouts.getItems().iterator();
    assertConstraintLayout(iterator.next());
    assertGridLayout(iterator.next());
    if (FlexboxLayoutHandler.FLEXBOX_ENABLE_FLAG) {
      assertFlexboxLayout(iterator.next());
    }
    assertFrameLayout(iterator.next());
    assertLinearLayoutItem(iterator.next());
    assertVerticalLinearLayoutItem(iterator.next());
    assertRelativeLayout(iterator.next());
    assertTableLayout(iterator.next());
    assertTableRow(iterator.next());
    assertFragment(iterator.next());
    assertFalse(iterator.hasNext());

    iterator = containers.getItems().iterator();
    assertRadioGroup(iterator.next());
    assertListView(iterator.next());
    assertGridView(iterator.next());
    assertExpandableListView(iterator.next());
    assertScrollView(iterator.next());
    assertHorizontalScrollView(iterator.next());
    assertTabHost(iterator.next());
    assertWebView(iterator.next());
    assertSearchView(iterator.next());
    assertViewPager(iterator.next());
    assertFalse(iterator.hasNext());

    iterator = images.getItems().iterator();
    assertImageButton(iterator.next());
    assertImageView(iterator.next());
    assertVideoView(iterator.next());
    assertFalse(iterator.hasNext());

    iterator = times.getItems().iterator();
    assertTimePicker(iterator.next());
    assertDatePicker(iterator.next());
    assertCalendarView(iterator.next());
    assertChronometer(iterator.next());
    assertTextClock(iterator.next());
    assertFalse(iterator.hasNext());

    iterator = transitions.getItems().iterator();
    assertImageSwitcher(iterator.next());
    assertAdapterViewFlipper(iterator.next());
    assertStackView(iterator.next());
    assertTextSwitcher(iterator.next());
    assertViewAnimator(iterator.next());
    assertViewFlipper(iterator.next());
    assertViewSwitcher(iterator.next());
    assertFalse(iterator.hasNext());

    iterator = advanced.getItems().iterator();
    assertIncludeItem(iterator.next());
    assertRequestFocus(iterator.next());
    assertViewTag(iterator.next());
    assertViewStub(iterator.next());
    assertTextureView(iterator.next());
    assertSurfaceView(iterator.next());
    assertNumberPicker(iterator.next());
    assertFalse(iterator.hasNext());

    iterator = google.getItems().iterator();
    assertAdView(iterator.next());
    assertMapView(iterator.next());
    assertFalse(iterator.hasNext());

    iterator = design.getItems().iterator();
    assertCoordinatorLayoutItem(iterator.next());
    assertAppBarLayoutItem(iterator.next());
    assertTabLayout(iterator.next());
    assertTabItem(iterator.next());
    assertNestedScrollViewItem(iterator.next());
    assertFloatingActionButtonItem(iterator.next());
    assertTextInputLayoutItem(iterator.next());
    assertFalse(iterator.hasNext());

    iterator = appcompat.getItems().iterator();
    assertCardView(iterator.next());
    assertGridLayoutV7(iterator.next());
    assertRecyclerView(iterator.next());
    assertToolbarV7(iterator.next());
    assertFalse(iterator.hasNext());
  }

  public void testAddIllegalThirdPartyComponent() {
    Palette palette = model.getPalette(NlLayoutType.LAYOUT);
    boolean added = model.addAdditionalComponent(NlLayoutType.LAYOUT, NlPaletteModel.THIRD_PARTY_GROUP, palette, null, null, LINEAR_LAYOUT, null, null,
                                                 SdkConstants.CONSTRAINT_LAYOUT_LIB_ARTIFACT, null, Collections.emptyList(), Collections.emptyList());
    assertThat(added).isFalse();
    assertThat(getGroupByName(NlPaletteModel.THIRD_PARTY_GROUP)).isNull();

    ViewHandler handler = ViewHandlerManager.get(myFacet).getHandler(LINEAR_LAYOUT);
    assertThat(handler).isInstanceOf(LinearLayoutHandler.class);
  }

  public void testAddThirdPartyComponent() {
    registerJavaClasses();
    registerFakeBaseViewHandler();
    Palette palette = model.getPalette(NlLayoutType.LAYOUT);
    String tag = "com.example.FakeCustomView";
    boolean added = model
      .addAdditionalComponent(NlLayoutType.LAYOUT, NlPaletteModel.THIRD_PARTY_GROUP, palette, AndroidIcons.Android, AndroidIcons.Android24, tag,
                              getXml(tag), getPreviewXml(tag), SdkConstants.CONSTRAINT_LAYOUT_LIB_ARTIFACT,
                              "family", ImmutableList.of("family", "size"), Collections.emptyList());
    Palette.Group thirdParty = getGroupByName(NlPaletteModel.THIRD_PARTY_GROUP);
    assertThat(added).isTrue();
    assertThat(thirdParty).isNotNull();
    assertThat(thirdParty.getItems().size()).isEqualTo(1);

    Palette.Item item = (Palette.Item)thirdParty.getItem(0);
    assertThat(item.getTagName()).isEqualTo(tag);
    assertThat(item.getIcon()).isEqualTo(AndroidIcons.Android);
    assertThat(item.getLargeIcon()).isEqualTo(AndroidIcons.Android24);
    assertThat(item.getTitle()).isEqualTo("FakeCustomView");
    assertThat(item.getGradleCoordinateId()).isEqualTo(SdkConstants.CONSTRAINT_LAYOUT_LIB_ARTIFACT);
    assertThat(item.getXml()).isEqualTo(getXml(tag));
    assertThat(item.getPreviewXml()).isEqualTo(getPreviewXml(tag));

    ViewHandler handler = ViewHandlerManager.get(myFacet).getHandler(tag);
    assertThat(handler).isNotNull();
    assertThat(handler.getTitle(tag)).isEqualTo("FakeCustomView");
    assertThat(handler.getIcon(tag)).isEqualTo(AndroidIcons.Android);
    assertThat(handler.getLargeIcon(tag)).isEqualTo(AndroidIcons.Android24);
    assertThat(handler.getGradleCoordinateId(tag)).isEqualTo(SdkConstants.CONSTRAINT_LAYOUT_LIB_ARTIFACT);
    assertThat(handler.getPreviewScale(tag)).isWithin(0.0).of(1.0);
    assertThat(handler.getInspectorProperties()).containsExactly("family", "size");
    assertThat(handler.getLayoutInspectorProperties()).isEmpty();
    assertThat(handler.getPreferredProperty()).isEqualTo("family");
  }

  public void testProjectComponents() {
    registerJavaClasses();
    Palette palette = model.getPalette(NlLayoutType.LAYOUT);
    Palette.Group projectComponents = getGroupByName(NlPaletteModel.PROJECT_GROUP);
    assertThat(projectComponents).isNull();

    model.loadAdditionalComponents(NlLayoutType.LAYOUT, palette, (project) -> {
      PsiClass customView = mock(PsiClass.class);
      when(customView.getName()).thenReturn("FakeCustomView");
      when(customView.getQualifiedName()).thenReturn("com.example.FakeCustomView");
      return new CollectionQuery<>(ImmutableList.of(customView));
    });
    projectComponents = getGroupByName(NlPaletteModel.PROJECT_GROUP);
    assertThat(projectComponents.getItems().size()).isEqualTo(1);

    String tag = "com.example.FakeCustomView";
    Palette.Item item = (Palette.Item)projectComponents.getItem(0);
    assertThat(item.getTagName()).isEqualTo(tag);
    assertThat(item.getIcon()).isEqualTo(AndroidIcons.Android);
    assertThat(item.getLargeIcon()).isEqualTo(AndroidIcons.Android24);
    assertThat(item.getTitle()).isEqualTo("FakeCustomView");
    assertThat(item.getGradleCoordinateId()).isEmpty();
    assertThat(item.getXml()).isEqualTo("<com.example.FakeCustomView\n" +
                                        "    android:layout_width=\"wrap_content\"\n" +
                                        "    android:layout_height=\"wrap_content\" />\n");
    assertThat(item.getPreviewXml()).isEmpty();
  }

  @Nullable
  private Palette.Group getGroupByName(@NotNull String name) {
    Palette palette = model.getPalette(NlLayoutType.LAYOUT);
    List<Palette.BaseItem> groups = palette.getItems();
    return groups.stream()
      .filter(Palette.Group.class::isInstance)
      .map(Palette.Group.class::cast)
      .filter(g -> name.equals(g.getName()))
      .findFirst()
      .orElse(null);
  }

  private void registerFakeBaseViewHandler() {
    ViewHandlerManager manager = ViewHandlerManager.get(myFacet);
    ViewHandler handler = manager.getHandler(SdkConstants.VIEW);
    assertThat(handler).isNotNull();
    manager.registerHandler("com.example.FakeView", handler);
  }

  private void registerJavaClasses() {
    myFixture.addClass("package android.view; public class View {}");
    myFixture.addClass("package com.example; public class FakeCustomView extends android.view.View {}");
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
