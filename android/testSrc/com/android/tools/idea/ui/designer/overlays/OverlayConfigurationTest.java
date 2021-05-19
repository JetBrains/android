/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.ui.designer.overlays;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.intellij.util.ui.ImageUtil;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import javax.swing.Icon;
import org.jetbrains.android.AndroidTestCase;

public class OverlayConfigurationTest extends AndroidTestCase {
  private OverlayProvider myProvider;
  private OverlayConfiguration myOverlayConfiguration;

  private static final String ID_1 = "id1";
  private static final String ID_2 = "id2";
  private static final String NAME_1 = "name1";
  private static final String NAME_2 = "name2";
  private static final BufferedImage TEST_IMG = ImageUtil.createImage(1, 1, 1);

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myProvider = mock(OverlayProvider.class);
    myOverlayConfiguration = new OverlayConfiguration();

    when(myProvider.getPluginIcon()).thenReturn(mock(Icon.class));
  }

  @Override
  public void tearDown() throws Exception {
    try {
      myOverlayConfiguration  = null;
    }
    finally {
      super.tearDown();
    }
  }

  public void testAddNewOverlay() {
    OverlayData data = new OverlayData(new OverlayEntry(ID_1, myProvider), NAME_1, TEST_IMG);
    myOverlayConfiguration.addOverlay(data);

    assert(myOverlayConfiguration.getCurrentOverlayEntry().equals(data.getOverlayEntry()));
    assert(myOverlayConfiguration.getOverlayVisibility());
    assert(myOverlayConfiguration.getOverlayImage().equals(data.getOverlayImage()));
  }

  public void testAddSameOverlay() {
    OverlayData data = new OverlayData(new OverlayEntry(ID_1, myProvider), NAME_1, TEST_IMG);
    myOverlayConfiguration.addOverlay(data);
    myOverlayConfiguration.addOverlay(data);

    assertSize(1, myOverlayConfiguration.getAllOverlays());
  }

  public void testUpdateOverlay() {
    OverlayData data = new OverlayData(new OverlayEntry(ID_1, myProvider), NAME_1, TEST_IMG);
    myOverlayConfiguration.addOverlay(data);
    data.setOverlayName(NAME_2);
    myOverlayConfiguration.addOverlay(data);

    assertSize(1, myOverlayConfiguration.getAllOverlays());
    assert(myOverlayConfiguration.getCurrentOverlayEntry().equals(data.getOverlayEntry()));
    assert(myOverlayConfiguration.getOverlayVisibility());
    assert(myOverlayConfiguration.isOverlayPresent());
    assert(myOverlayConfiguration.getAllOverlays().get(0).getOverlayName().equals(NAME_2));
  }

  public void testShowPlaceholder() {
    myOverlayConfiguration.showPlaceholder();

    assert(myOverlayConfiguration.getOverlayVisibility());
    assert(myOverlayConfiguration.isPlaceholderVisible());
    assert(myOverlayConfiguration.isOverlayPresent());
  }

  public void testHidePlaceholder() {
    myOverlayConfiguration.hidePlaceholder();

    assert(!myOverlayConfiguration.getOverlayVisibility());
    assert(!myOverlayConfiguration.isPlaceholderVisible());
    assert(!myOverlayConfiguration.isOverlayPresent());
  }

  public void testRemoveOverlay() {
    OverlayData data = new OverlayData(new OverlayEntry(ID_1, myProvider), NAME_1, TEST_IMG);
    myOverlayConfiguration.addOverlay(data);
    myOverlayConfiguration.removeOverlayFromList(data.getOverlayEntry());

    assertSize(0, myOverlayConfiguration.getAllOverlays());
  }

  public void testRemoveOverlays() {
    OverlayData data1 = new OverlayData(new OverlayEntry(ID_1, myProvider), NAME_1, TEST_IMG);
    OverlayData data2 = new OverlayData(new OverlayEntry(ID_2, myProvider), NAME_2, TEST_IMG);
    myOverlayConfiguration.addOverlay(data1);
    myOverlayConfiguration.addOverlay(data2);
    myOverlayConfiguration.removeOverlays(Arrays.asList(data1, data2));

    assertSize(0, myOverlayConfiguration.getAllOverlays());
  }

  public void testCachedOverlay() {
    OverlayData data = new OverlayData(new OverlayEntry(ID_1, myProvider), NAME_1, TEST_IMG);
    myOverlayConfiguration.addOverlay(data);

    myOverlayConfiguration.hideCachedOverlay();
    assert(myOverlayConfiguration.isOverlayPresent());
    assert(!myOverlayConfiguration.getOverlayVisibility());

    myOverlayConfiguration.showCachedOverlay();
    assert(myOverlayConfiguration.isOverlayPresent());
    assert(myOverlayConfiguration.getOverlayVisibility());

    myOverlayConfiguration.clearCurrentOverlay();
    assert(!myOverlayConfiguration.isOverlayPresent());
  }
}
