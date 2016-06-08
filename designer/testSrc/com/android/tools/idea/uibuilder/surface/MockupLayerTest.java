/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface;

import com.android.tools.idea.uibuilder.mockup.MockupBaseTest;
import com.android.tools.idea.uibuilder.mockup.MockupComponentAttributes;
import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.intellij.openapi.project.Project;
import org.junit.Before;
import org.mockito.Mock;

import java.awt.*;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class MockupLayerTest extends MockupBaseTest {

  public static final String MOCKUP_GRID_PNG_500x500 = getTestDataPath() + "/mockup/grid_500x500.png";
  @Mock
  private ScreenView myScreenView;
  @Mock
  private Project myMockProject;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    when(myScreenView.getSize()).thenReturn(new Dimension(500, 500));
    when(myMockProject.getBasePath()).thenReturn(getTestDataPath());
  }

  public void testCreateMockupLayerSize0x0() {
    final NlModel model = createModel1Mockup(MOCKUP_GRID_PNG_500x500, "0 0 0 0");
    DesignSurface mockSurface = mock(DesignSurface.class);
    when(mockSurface.getScale()).thenReturn(1.0);
    final ScreenView screenView = new ScreenView(mockSurface, ScreenView.ScreenViewType.BLUEPRINT, model);
    final MockupLayer mockupLayer = new MockupLayer(screenView);
    final Dimension size = screenView.getPreferredSize();
    assertTrue(size.width > 0 && size.height > 0);
    final MockupComponentAttributes mockupComponentAttributes = mockupLayer.getMockupComponentAttributes().get(0);
    assertNotNull(mockupComponentAttributes);

    Rectangle destinationRectangle;
    destinationRectangle = mockupLayer.getDestinationRectangle(new Rectangle(0,0,0,0), new Rectangle(size.width, size.height));
    assertEquals(new Rectangle(0,0,size.width,size.height), destinationRectangle);

    destinationRectangle = mockupLayer.getDestinationRectangle(mockupComponentAttributes.getPosition(), new Rectangle(size.width, size.height));
    assertEquals(new Rectangle(0,0,size.width,size.height), destinationRectangle);
  }

  public void testCreateMockupLayerSize10x10_50x50() {
    final NlModel model = createModel1Mockup(MOCKUP_GRID_PNG_500x500, "10 10 50 50");
    DesignSurface mockSurface = mock(DesignSurface.class);
    when(mockSurface.getScale()).thenReturn(1.0);
    final ScreenView screenView = new ScreenView(mockSurface, ScreenView.ScreenViewType.BLUEPRINT, model);
    int dp = screenView.getConfiguration().getDensity().getDpiValue() / Coordinates.DEFAULT_DENSITY; // Dpi factor for the screen view
    final MockupLayer mockupLayer = new MockupLayer(screenView);
    final Dimension size = screenView.getPreferredSize();
    assertTrue(size.width > 0 && size.height > 0);
    final MockupComponentAttributes mockupComponentAttributes = mockupLayer.getMockupComponentAttributes().get(0);
    assertNotNull(mockupComponentAttributes);

    Rectangle destinationRectangle;
    assertEquals(new Rectangle(10,10,50,50), mockupComponentAttributes.getPosition());
    destinationRectangle = mockupLayer.getDestinationRectangle(mockupComponentAttributes.getPosition(), new Rectangle(size.width, size.height));
    assertEquals(new Rectangle(10*dp,10*dp,50*dp ,50*dp), destinationRectangle);
  }


  public void testCreateMockupLayerEmptyPosition() {
    final NlModel model = createModel1Mockup(MOCKUP_GRID_PNG_500x500, "");
    DesignSurface mockSurface = mock(DesignSurface.class);
    when(mockSurface.getScale()).thenReturn(1.0);
    final ScreenView screenView = new ScreenView(mockSurface, ScreenView.ScreenViewType.BLUEPRINT, model);
    final MockupLayer mockupLayer = new MockupLayer(screenView);
    final Dimension size = screenView.getPreferredSize();
    final Rectangle destinationRectangle = mockupLayer.getDestinationRectangle(new Rectangle(0, 0, -1, -1), new Rectangle(size.width, size.height));
    assertEquals(new Rectangle(0,0,size.width,size.height), destinationRectangle);
  }
}