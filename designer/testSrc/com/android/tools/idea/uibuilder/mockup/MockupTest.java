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

import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import org.mockito.Mock;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class MockupTest extends MockupTestCase {

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

  public void testIsCropStringCorrect() throws Exception {
    assertTrue(Mockup.isPositionStringCorrect("2 13"));
    assertTrue(Mockup.isPositionStringCorrect("0 1"));
    assertTrue(Mockup.isPositionStringCorrect("0 1 "));
    assertFalse(Mockup.isPositionStringCorrect("0 "));
  }

  public void testIsStringCorrectPositionOnlyNegative() throws Exception {
    assertTrue(Mockup.isPositionStringCorrect("0 0 -1 -1 -2 -13"));
    assertTrue(Mockup.isPositionStringCorrect("0 0 -1 -1 2 -13"));
    assertTrue(Mockup.isPositionStringCorrect("0 0 -1 -1 -0 13"));
    assertFalse(Mockup.isPositionStringCorrect("-12 0 -1 -1 -0 13"));
  }

  public void testIsStringCorrectPositionSize() throws Exception {
    assertTrue(Mockup.isPositionStringCorrect("0 0 -1 -1 2 13 12 23"));
    assertTrue(Mockup.isPositionStringCorrect("0 0 -1 -1 2 13 12 -23 "));
    assertTrue(Mockup.isPositionStringCorrect("0 0 -1 -1 2 13 -12 23 "));
    assertTrue(Mockup.isPositionStringCorrect("0 0 -1 -1 2 13 -12 -23 "));
    assertTrue(Mockup.isPositionStringCorrect("0 0 -1 -1 2 13 -12 -23 "));
  }

  public void testIsStringCorrectNegativePositionSize() throws Exception {
    assertTrue(Mockup.isPositionStringCorrect("0 0 -1 -1 -2 -13 12 23"));
    assertTrue(Mockup.isPositionStringCorrect("0 0 -1 -1 -2 -13 -12 -23"));
  }

  public void testIsStringCorrectPositionSizeCropXY() throws Exception {
    assertTrue(Mockup.isPositionStringCorrect("12 23 10 10 -2 -13 "));
  }

  public void testIsStringCorrectPositionCrop() throws Exception {
    assertTrue(Mockup.isPositionStringCorrect("0 13 12 23 23 24 231 455544"));
    assertTrue(Mockup.isPositionStringCorrect("0 0 0 0 0 0 0 0 "));
  }

  public void testIsStringCorrectPositionCropNegative() throws Exception {
    assertFalse(Mockup.isPositionStringCorrect("23 -24 -231 455544 0 13 12 23"));
  }

  public void testCreateMockupModelFromCorrectFullString() {
    final NlModel model = createModel1Mockup(MOCKUP_PSD, "5 6 7 8 1 2 3 4");
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
    assertEquals(new Rectangle(1, 2, 3, 4), mockup.getCropping());
    assertEquals(new Rectangle(0, 0, -1, -1), mockup.getBounds());
  }

  public void testGetCropping_FullImage() {
    final NlModel model = createModel1Mockup(getTestDataPath() + "/" + MOCKUP_PSD, "");
    NlComponent component = model.getComponents().get(0);
    final Mockup mockup = Mockup.create(component);//mockProject, "", "1 2 3 4");
    assertNotNull(mockup);
    final BufferedImage image = mockup.getImage();
    assertNotNull(image);
    assertEquals(new Rectangle(0, 0, image.getWidth(), image.getHeight()), mockup.getCropping());

  }

  public void testCreateMockupModelFromCorrectXYString() {
    final NlModel model = createModel1Mockup(MOCKUP_PSD, "0 0 -1 -1 1 2");
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


  public void testCreateMockup() {
    final NlModel model = createModel1Mockup(MOCKUP_PSD, DEFAULT_TEST_POSITION, "0.4");
    NlComponent component = model.getComponents().get(0).getRoot();
    final Mockup mockup = Mockup.create(component);
    assertNotNull("Model creation", mockup);
    assertEquals(new Rectangle(20, 20, 60, 60), mockup.getBounds());
    assertEquals(new Rectangle(10, 10, 60, 60), mockup.getCropping());
    assertEquals(0.4f, mockup.getAlpha());
  }

  public void testCreateMockup_createWithoutAttribute() {
    final NlModel model = createModel0Mockup();
    final NlComponent component = model.getComponents().get(0);
    assertNotNull(Mockup.create(component, true));
    assertNull(Mockup.create(component, false));
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
    if (SystemInfo.isWindows) {
      // Do not run tests on Windows (see http://b.android.com/222904)
      return;
    }

    final NlModel model = createModel1Mockup(MOCKUP_PSD, DEFAULT_TEST_POSITION, null);
    final NlComponent component = model.getComponents().get(0);
    final Mockup mockup = Mockup.create(component);
    assertNotNull(mockup);
    assertEquals(model.getProject().getBasePath() + "/" + MOCKUP_PSD, mockup.getFilePath());
  }

  public void testGetBounds_Normal_Position() {
    final NlModel model = createModel1Mockup(MOCKUP_PSD, "0 0 -1 -1 10 10 120 50");
    NlDesignSurface mockSurface = mock(NlDesignSurface.class);
    when(mockSurface.getScale()).thenReturn(1.0);

    ScreenView screenView = mock(ScreenView.class);
    when(screenView.getX()).thenReturn(0);
    when(screenView.getY()).thenReturn(0);
    when(screenView.getSize()).thenReturn(new Dimension(1000, 2000));
    when(screenView.getSize(anyObject())).thenReturn(new Dimension(1000, 2000));
    when(screenView.getScale()).thenReturn(2.);
    when(screenView.getSurface()).thenReturn(mockSurface);
    final Mockup mockup = Mockup.create(model.getComponents().get(0));
    assertNotNull(mockup);
    assertEquals(new Rectangle(10, 10, 120, 50), mockup.getBounds());

    final Rectangle swingBounds = mockup.getScreenBounds(screenView);
    assertEquals(Coordinates.getSwingXDip(screenView, 10), swingBounds.x, 2.);
    assertEquals(Coordinates.getSwingXDip(screenView, 10), swingBounds.y, 2.);
    assertEquals(Coordinates.getSwingDimensionDip(screenView, 110), swingBounds.width, 2.);
    assertEquals(Coordinates.getSwingDimensionDip(screenView, 40), swingBounds.height, 2.);
  }

  public void testGetBounds_0000_Position() {
    final NlModel model = createModel1Mockup(MOCKUP_PSD, "0 0 0 0", null);
    final NlComponent component = model.getComponents().get(0);
    final Mockup mockup = Mockup.create(component);
    assertNotNull(mockup);

    NlDesignSurface surface = new NlDesignSurface(getProject(), false, getProject());
    surface.setModel(model);
    final ScreenView screenView = new ScreenView(surface, surface.getSceneManager()) {
      @Override
      public double getScale() {
        return 1.0;
      }
    };
    final Rectangle componentSwingCoordinates = new Rectangle(0, 0,
                                                              Coordinates.getSwingDimension(screenView, 1000),
                                                              // See createModel for the 1000 value
                                                              Coordinates.getSwingDimension(screenView, 1000));
    final Rectangle destinationRectangle = mockup.getScreenBounds(screenView);
    assertEquals(componentSwingCoordinates, destinationRectangle);
  }


  public void testGetBounds_nullPosition() {
    final NlModel model = createModel1Mockup(MOCKUP_PSD, null, null);
    final NlComponent component = model.getComponents().get(0);
    final Mockup mockup = Mockup.create(component);
    assertNotNull(mockup);

    NlDesignSurface surface = new NlDesignSurface(getProject(), false, getProject());
    surface.setModel(model);
    final ScreenView screenView = new ScreenView(surface, surface.getSceneManager()) {
      @Override
      public double getScale() {
        return 1.0;
      }
    };
    final Rectangle destinationRectangle = mockup.getScreenBounds(screenView);
    assertEquals(new Rectangle(0, 0, NlComponentHelperKt.getW(component), NlComponentHelperKt.getH(component)), destinationRectangle);
  }
}

