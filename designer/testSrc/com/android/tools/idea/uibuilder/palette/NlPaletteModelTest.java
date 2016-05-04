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

import com.android.tools.idea.uibuilder.model.NlLayoutType;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Iterator;

public class NlPaletteModelTest extends PaletteTestCase {
  private NlPaletteModel model;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    model = NlPaletteModel.get(getProject());
    Reader reader = new InputStreamReader(NlPaletteModel.class.getResourceAsStream(NlLayoutType.LAYOUT.getPaletteFileName()));
    try {
      model.loadPalette(reader, NlLayoutType.LAYOUT);
    }
    finally {
      reader.close();
    }
  }

  public void testPalette() throws Exception {
    Palette palette = model.getPalette(NlLayoutType.LAYOUT);
    Iterator<Palette.BaseItem> iterator = palette.getItems().iterator();
    Palette.Group widgets = assertIsGroup(iterator.next(), "Widgets");
    Palette.Group textFields = assertIsGroup(iterator.next(), "Text Fields");
    Palette.Group layouts = assertIsGroup(iterator.next(), "Layouts");
    Palette.Group containers = assertIsGroup(iterator.next(), "Containers");
    Palette.Group images = assertIsGroup(iterator.next(), "Images & Media");
    Palette.Group times = assertIsGroup(iterator.next(), "Date & Time");
    Palette.Group transitions = assertIsGroup(iterator.next(), "Transitions");
    Palette.Group advanced = assertIsGroup(iterator.next(), "Advanced");
    Palette.Group google = assertIsGroup(iterator.next(), "Custom - Google");
    Palette.Group design = assertIsGroup(iterator.next(), "Custom - Design");
    Palette.Group appcompat = assertIsGroup(iterator.next(), "Custom - AppCompat");
    Palette.Group leanback = assertIsGroup(iterator.next(), "Custom - Leanback");
    assertFalse(iterator.hasNext());

    iterator = widgets.getItems().iterator();
    assertTextViewItem(iterator.next());
    assertButton(iterator.next());
    assertToggleButton(iterator.next());
    assertCheckBox(iterator.next());
    assertRadioButton(iterator.next());
    assertCheckedTextView(iterator.next());
    assertSpinner(iterator.next());
    assertLargeProgressBarItem(iterator.next());
    assertNormalProgressBarItem(iterator.next());
    assertSmallProgressBarItem(iterator.next());
    assertHorizontalProgressBarItem(iterator.next());
    assertSeekBar(iterator.next());
    assertQuickContactBadge(iterator.next());
    assertRatingBar(iterator.next());
    assertSwitch(iterator.next());
    assertSpace(iterator.next());
    assertFalse(iterator.hasNext());

    assertPlainTextEditText(textFields.getItem(0));
    assertEquals("Items in text fields group", 14, textFields.getItems().size());

    iterator = layouts.getItems().iterator();
    assertGridLayout(iterator.next());
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
    assertAnalogClock(iterator.next());
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
    assertGestureOverlayView(iterator.next());
    assertTextureView(iterator.next());
    assertSurfaceView(iterator.next());
    assertNumberPicker(iterator.next());
    assertZoomButton(iterator.next());
    assertZoomControls(iterator.next());
    assertDialerFilter(iterator.next());
    assertFalse(iterator.hasNext());

    iterator = google.getItems().iterator();
    assertAdView(iterator.next());
    assertMapFragment(iterator.next());
    assertMapView(iterator.next());
    assertFalse(iterator.hasNext());

    iterator = design.getItems().iterator();
    assertCoordinatorLayoutItem(iterator.next());
    assertAppBarLayoutItem(iterator.next());
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

    iterator = leanback.getItems().iterator();
    assertBrowseFragment(iterator.next());
    assertDetailsFragment(iterator.next());
    assertPlaybackOverlayFragment(iterator.next());
    assertSearchFragment(iterator.next());
    assertFalse(iterator.hasNext());
  }
}
