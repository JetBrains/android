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

import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.intellij.openapi.project.Project;
import org.mockito.Mock;

import java.awt.*;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class MockupComponentAttributesTest extends MockupBaseTest {

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
    assertTrue(MockupComponentAttributes.isPositionStringCorrect(""));
  }

  public void testIsStringCorrectNullSting() throws Exception {
    assertFalse(MockupComponentAttributes.isPositionStringCorrect(null));
  }

  public void testIsPositionStringCorrect() throws Exception {
    assertTrue("Good string", MockupComponentAttributes.isPositionStringCorrect("2 13"));
    assertTrue(" 0 1", MockupComponentAttributes.isPositionStringCorrect("0 1"));
    assertTrue(" 0 1", MockupComponentAttributes.isPositionStringCorrect("0 1 "));
    assertFalse("0 ", MockupComponentAttributes.isPositionStringCorrect("0 "));
  }

  public void testIsStringCorrectPositionOnlyNegative() throws Exception {
    assertTrue(MockupComponentAttributes.isPositionStringCorrect("-2 -13"));
    assertTrue(MockupComponentAttributes.isPositionStringCorrect("2 -13"));
    assertTrue(MockupComponentAttributes.isPositionStringCorrect("-0 13"));
  }

  public void testIsStringCorrectPositionSize() throws Exception {
    assertTrue(MockupComponentAttributes.isPositionStringCorrect("2 13 12 23"));
    assertTrue(MockupComponentAttributes.isPositionStringCorrect("2 13 12 23 "));
  }

  public void testIsStringCorrectNegativePositionSize() throws Exception {
    assertTrue(MockupComponentAttributes.isPositionStringCorrect("-2 -13 12 23"));
  }

  public void testIsStringCorrectPositionSizeCropXY() throws Exception {
    assertTrue(MockupComponentAttributes.isPositionStringCorrect("-2 -13 12 23 10 10"));
  }

  public void testIsStringCorrectPositionCrop() throws Exception {
    assertTrue(MockupComponentAttributes.isPositionStringCorrect("0 13 12 23 23 24 231 455544"));
    assertTrue(MockupComponentAttributes.isPositionStringCorrect("0 0 0 0 0 0 0 0 "));
  }

  public void testIsStringCorrectPositionCropNegative() throws Exception {
    assertFalse(MockupComponentAttributes.isPositionStringCorrect("0 13 12 23 23 -24 -231 455544"));
  }

  public void testCreateMockupModelFromCorrectFullString() {
    final NlModel model = createModel1Mockup(MOCKUP_PSD, "1 2 3 4 5 6 7 8");
    NlComponent component = model.getComponents().get(0);
    final MockupComponentAttributes mockupComponentAttributes = MockupComponentAttributes.create(component);//mockProject, "", "1 2 3 4 5 6 7 8");
    assertNotNull(mockupComponentAttributes);
    assertEquals(new Rectangle(1, 2, 3, 4), mockupComponentAttributes.getPosition());
    assertEquals(new Rectangle(5, 6, 7, 8), mockupComponentAttributes.getCropping());
  }

  public void testCreateMockupModelFromCorrectPositionString() {
    final NlModel model = createModel1Mockup(MOCKUP_PSD, "1 2 3 4");
    NlComponent component = model.getComponents().get(0);
    final MockupComponentAttributes mockupComponentAttributes = MockupComponentAttributes.create(component);//mockProject, "", "1 2 3 4");
    assertNotNull(mockupComponentAttributes);
    assertEquals(new Rectangle(1, 2, 3, 4), mockupComponentAttributes.getPosition());
    assertEquals(new Rectangle(0, 0, -1, -1), mockupComponentAttributes.getCropping());
  }

  public void testCreateMockupModelFromCorrectXYString() {
    final NlModel model = createModel1Mockup(MOCKUP_PSD, "1 2");
    NlComponent component = model.getComponents().get(0);
    final MockupComponentAttributes mockupComponentAttributes = MockupComponentAttributes.create(component);//mockProject, "", "1 2");
    assertNotNull(mockupComponentAttributes);
    assertEquals(new Rectangle(1, 2, -1, -1), mockupComponentAttributes.getPosition());
    assertEquals(new Rectangle(0, 0, -1, -1), mockupComponentAttributes.getCropping());
  }

  public void testCreateMockupModelFromIncorrectPositionString() {
    final NlModel model = createModel1Mockup(MOCKUP_PSD, "1 2d");
    NlComponent component = model.getComponents().get(0);
    final MockupComponentAttributes mockupComponentAttributes = MockupComponentAttributes.create(component);//mockProject, "", "1 2d");
    assertNotNull(mockupComponentAttributes);
  }


  public void testCreateMockupModel() {
    final NlModel model = createModel1Mockup(MOCKUP_PSD, DEFAULT_TEST_POSITION, "0.4");
    NlComponent component = model.getComponents().get(0).getRoot();
    final MockupComponentAttributes mockupComponentAttributes = MockupComponentAttributes.create(component);
    assertNotNull("Model creation", mockupComponentAttributes);
    assertEquals(new Rectangle(20, 20, 60, 60), mockupComponentAttributes.getPosition());
    assertEquals(new Rectangle(10, 10, 60, 60), mockupComponentAttributes.getCropping());
    assertEquals(0.4f, mockupComponentAttributes.getAlpha());
  }

  public void testCreateMockupLayerEmptyStringPosition() {
    final NlModel model = createModel1Mockup(MOCKUP_PSD, "");
    NlComponent component = model.getComponents().get(0).getRoot();
    final MockupComponentAttributes mockupComponentAttributes = MockupComponentAttributes.create(component);
    assertNotNull("Model creation", mockupComponentAttributes);
    assertEquals(new Rectangle(0, 0, -1, -1), mockupComponentAttributes.getPosition());
    assertEquals(new Rectangle(0, 0, -1, -1), mockupComponentAttributes.getCropping());
  }

  public void testGetAllMockup() {
    final NlModel model = createModel2Mockup(MOCKUP_PSD, DEFAULT_TEST_POSITION);
    final List<MockupComponentAttributes> all = MockupComponentAttributes.createAll(model);
    assertNotNull(all);
    assertSize(2, all);
  }

  public void testGetAllMockupNoMockup() {
    final NlModel model = createModel0Mockup();
    final List<MockupComponentAttributes> all = MockupComponentAttributes.createAll(model);
    assertNotNull(all);
    assertSize(0, all);
  }

  public void testSetAlphaString() {
    NlModel model = createModel1Mockup(MOCKUP_PSD, DEFAULT_TEST_POSITION);
    final NlComponent component = model.getComponents().get(0);
    final MockupComponentAttributes mockupComponentAttributes = MockupComponentAttributes.create(component);
    assertNotNull(mockupComponentAttributes);
    assertEquals(MockupComponentAttributes.DEFAULT_OPACITY, mockupComponentAttributes.getAlpha());

    mockupComponentAttributes.setAlpha("0.9");
    assertEquals(0.9f, mockupComponentAttributes.getAlpha());

    mockupComponentAttributes.setAlpha("2.9");
    assertEquals(MockupComponentAttributes.DEFAULT_OPACITY_IF_ERROR, mockupComponentAttributes.getAlpha());

    mockupComponentAttributes.setAlpha("-2.9");
    assertEquals(MockupComponentAttributes.DEFAULT_OPACITY_IF_ERROR, mockupComponentAttributes.getAlpha());
  }

  public void testFilePathRelative() {
    final NlModel model = createModel1Mockup(MOCKUP_PSD, DEFAULT_TEST_POSITION, null);
    final NlComponent component = model.getComponents().get(0);
    final MockupComponentAttributes mockupComponentAttributes = MockupComponentAttributes.create(component);
    assertNotNull(mockupComponentAttributes);
    assertEquals(model.getProject().getBasePath() + "/" + MOCKUP_PSD, mockupComponentAttributes.getFilePath());
  }
}

