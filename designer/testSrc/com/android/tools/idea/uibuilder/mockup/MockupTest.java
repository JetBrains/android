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
package com.android.tools.idea.uibuilder.mockup;

import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.MockupLayer;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.project.Project;
import org.mockito.Mock;

import java.awt.*;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class MockupTest extends MockupBaseTest {

  public static final String DEFAULT_TEST_POSITION = "20 20 60 60 10 10 60 60";
  public static final String MOCKUP_PSD = "mockup/mockup.psd";

  @Mock
  Project mockProject;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    when(mockProject.getBasePath()).thenReturn(getTestDataPath());
  }

  public void testIsStringCorrectEmptySting() throws Exception {
    assertTrue(Mockup.isPositionStringCorrect(""));
  }

  public void testIsStringCorrectNullSting() throws Exception {
    assertFalse(Mockup.isPositionStringCorrect(null));
  }

  public void testIsPositionStringCorrect() throws Exception {
    assertTrue("Good string", Mockup.isPositionStringCorrect("2 13"));
    assertTrue(" 0 1", Mockup.isPositionStringCorrect("0 1"));
    assertTrue(" 0 1", Mockup.isPositionStringCorrect("0 1 "));
    assertFalse("0 ", Mockup.isPositionStringCorrect("0 "));
  }

  public void testIsStringCorrectPositionOnlyNegative() throws Exception {
    assertTrue(Mockup.isPositionStringCorrect("-2 -13"));
    assertTrue(Mockup.isPositionStringCorrect("2 -13"));
    assertTrue(Mockup.isPositionStringCorrect("-0 13"));
  }

  public void testIsStringCorrectPositionSize() throws Exception {
    assertTrue(Mockup.isPositionStringCorrect("2 13 12 23"));
    assertTrue(Mockup.isPositionStringCorrect("2 13 12 23 "));
  }

  public void testIsStringCorrectNegativePositionSize() throws Exception {
    assertTrue(Mockup.isPositionStringCorrect("-2 -13 12 23"));
  }

  public void testIsStringCorrectPositionSizeCropXY() throws Exception {
    assertTrue(Mockup.isPositionStringCorrect("-2 -13 12 23 10 10"));
  }

  public void testIsStringCorrectPositionCrop() throws Exception {
    assertTrue(Mockup.isPositionStringCorrect("0 13 12 23 23 24 231 455544"));
    assertTrue(Mockup.isPositionStringCorrect("0 0 0 0 0 0 0 0 "));
  }

  public void testIsStringCorrectPositionCropNegative() throws Exception {
    assertFalse(Mockup.isPositionStringCorrect("0 13 12 23 23 -24 -231 455544"));
  }

  public void testCreateMockupModelFromCorrectFullString() {
    final NlModel model = createModel1Mockup(MOCKUP_PSD, "1 2 3 4 5 6 7 8");
    NlComponent component = model.getComponents().get(0);
    final Mockup mockup = Mockup.create(component);//mockProject, "", "1 2 3 4 5 6 7 8");
    assertNotNull(mockup);
    assertEquals(new Rectangle(1, 2, 3, 4), mockup.getBounds());
    assertEquals(new Rectangle(5, 6, 7, 8), mockup.getCropping());
  }

  public void testCreateMockupModelFromCorrectPositionString() {
    final NlModel model = createModel1Mockup(MOCKUP_PSD, "1 2 3 4");
    NlComponent component = model.getComponents().get(0);
    final Mockup mockup = Mockup.create(component);//mockProject, "", "1 2 3 4");
    assertNotNull(mockup);
    assertEquals(new Rectangle(1, 2, 3, 4), mockup.getBounds());
    assertEquals(new Rectangle(0, 0, -1, -1), mockup.getCropping());
  }

  public void testCreateMockupModelFromCorrectXYString() {
    final NlModel model = createModel1Mockup(MOCKUP_PSD, "1 2");
    NlComponent component = model.getComponents().get(0);
    final Mockup mockup = Mockup.create(component);//mockProject, "", "1 2");
    assertNotNull(mockup);
    assertEquals(new Rectangle(1, 2, -1, -1), mockup.getBounds());
    assertEquals(new Rectangle(0, 0, -1, -1), mockup.getCropping());
  }

  public void testCreateMockupModelFromIncorrectPositionString() {
    final NlModel model = createModel1Mockup(MOCKUP_PSD, "1 2d");
    NlComponent component = model.getComponents().get(0);
    final Mockup mockup = Mockup.create(component);//mockProject, "", "1 2d");
    assertNotNull(mockup);
  }


  public void testCreateMockupModel() {
    final NlModel model = createModel1Mockup(MOCKUP_PSD, DEFAULT_TEST_POSITION, "0.4");
    NlComponent component = model.getComponents().get(0).getRoot();
    final Mockup mockup = Mockup.create(component);
    assertNotNull("Model creation", mockup);
    assertEquals(new Rectangle(20, 20, 60, 60), mockup.getBounds());
    assertEquals(new Rectangle(10, 10, 60, 60), mockup.getCropping());
    assertEquals(0.4f, mockup.getAlpha());
  }

  public void testCreateMockupLayerEmptyStringPosition() {
    final NlModel model = createModel1Mockup(MOCKUP_PSD, "");
    NlComponent component = model.getComponents().get(0).getRoot();
    final Mockup mockup = Mockup.create(component);
    assertNotNull("Model creation", mockup);
    assertEquals(new Rectangle(0, 0, -1, -1), mockup.getBounds());
    assertEquals(new Rectangle(0, 0, -1, -1), mockup.getCropping());
  }

  public void testGetAllMockup() {
    final NlModel model = createModel2Mockup(MOCKUP_PSD, DEFAULT_TEST_POSITION);
    final List<Mockup> all = Mockup.createAll(model);
    assertNotNull(all);
    assertSize(2, all);
  }

  public void testGetAllMockupNoMockup() {
    final NlModel model = createModel0Mockup();
    final List<Mockup> all = Mockup.createAll(model);
    assertNotNull(all);
    assertSize(0, all);
  }

  public void testSetAlphaString() {
    NlModel model = createModel1Mockup(MOCKUP_PSD, DEFAULT_TEST_POSITION);
    final NlComponent component = model.getComponents().get(0);
    final Mockup mockup = Mockup.create(component);
    assertNotNull(mockup);
    assertEquals(Mockup.DEFAULT_OPACITY, mockup.getAlpha());

    mockup.setAlpha("0.9");
    assertEquals(0.9f, mockup.getAlpha());

    mockup.setAlpha("2.9");
    assertEquals(Mockup.DEFAULT_OPACITY_IF_ERROR, mockup.getAlpha());

    mockup.setAlpha("-2.9");
    assertEquals(Mockup.DEFAULT_OPACITY_IF_ERROR, mockup.getAlpha());
  }

  public void testFilePathRelative() {
    final NlModel model = createModel1Mockup(MOCKUP_PSD, DEFAULT_TEST_POSITION, null);
    final NlComponent component = model.getComponents().get(0);
    final Mockup mockup = Mockup.create(component);
    assertNotNull(mockup);
    assertEquals(model.getProject().getBasePath() + "/" + MOCKUP_PSD, mockup.getFilePath());
  }

  public void testGetBounds_Normal_Position() {
    final NlModel model = createModel1Mockup(MOCKUP_PSD, "10 10 50 50");
    DesignSurface mockSurface = mock(DesignSurface.class);
    when(mockSurface.getScale()).thenReturn(1.0);
    final ScreenView screenView = new ScreenView(mockSurface, ScreenView.ScreenViewType.BLUEPRINT, model);
    int dp = screenView.getConfiguration().getDensity().getDpiValue() / Coordinates.DEFAULT_DENSITY; // Dpi factor for the screen view
    final MockupLayer mockupLayer = new MockupLayer(screenView);
    final Dimension size = screenView.getPreferredSize();
    assertTrue(size.width > 0 && size.height > 0);
    final Mockup mockup = mockupLayer.getMockups().get(0);
    assertNotNull(mockup);
    Rectangle destinationRectangle;
    assertEquals(new Rectangle(10, 10, 50, 50), mockup.getBounds());
    destinationRectangle = mockup.getBounds(screenView, new Rectangle(size.width, size.height));
    assertEquals(new Rectangle(10 * dp, 10 * dp, 40 * dp, 40 * dp), destinationRectangle);
  }

  public void testGetBounds_0000_Position() {
    final NlModel model = createModel1Mockup(MOCKUP_PSD, "0 0 0 0", null);
    final NlComponent component = model.getComponents().get(0);
    final Mockup mockup = Mockup.create(component);
    assertNotNull(mockup);
    DesignSurface mockSurface = mock(DesignSurface.class);
    when(mockSurface.getScale()).thenReturn(1.0);
    final ScreenView screenView = new ScreenView(mockSurface, ScreenView.ScreenViewType.BLUEPRINT, model);
    final Rectangle componentSwingCoordinates = new Rectangle(Coordinates.getSwingX(screenView, component.x),
                                             Coordinates.getSwingY(screenView, component.y),
                                             Coordinates.getSwingDimension(screenView, component.w),
                                             Coordinates.getSwingDimension(screenView, component.h));
    final Rectangle destinationRectangle = mockup.getBounds(screenView, componentSwingCoordinates);
    assertEquals(componentSwingCoordinates, destinationRectangle);
  }


  public void testGetBounds_nullPosition() {
    final NlModel model = createModel1Mockup(MOCKUP_PSD, null, null);
    final NlComponent component = model.getComponents().get(0);
    final Mockup mockup = Mockup.create(component);
    assertNotNull(mockup);
    DesignSurface mockSurface = mock(DesignSurface.class);
    when(mockSurface.getScale()).thenReturn(1.0);
    final ScreenView screenView = new ScreenView(mockSurface, ScreenView.ScreenViewType.BLUEPRINT, model);
    final Dimension size = screenView.getPreferredSize();
    final Rectangle destinationRectangle = mockup.getBounds(screenView, new Rectangle(0, 20, 100, 100));
    assertEquals(new Rectangle(0, 0, size.width, size.height), destinationRectangle);
  }
}

