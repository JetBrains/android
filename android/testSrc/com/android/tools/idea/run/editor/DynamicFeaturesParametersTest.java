/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run.editor;

import com.android.tools.adtui.swing.FakeUi;
import com.android.tools.adtui.swing.laf.HeadlessTableUI;
import com.intellij.openapi.module.Module;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

public class DynamicFeaturesParametersTest {
  @Mock Module myApp;
  @Mock Module myFeature1;
  @Mock Module myFeature2;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(myApp.getName()).thenReturn("app");
    when(myFeature1.getName()).thenReturn("feature1");
    when(myFeature2.getName()).thenReturn("feature2");
  }

  @Test
  public void emptyListWorks() {
    DynamicFeaturesParameters parameters = new DynamicFeaturesParameters();
    FakeUi fakeUi = new FakeUi(parameters.getComponent());
    fakeUi.layout();

    assertThat(fakeUi.getRoot().isVisible()).isFalse();
    assertThat(parameters.getDisabledDynamicFeatures()).isEmpty();
  }

  @Test
  public void featureListWorks() {
    DynamicFeaturesParameters parameters = new DynamicFeaturesParameters();
    JTable table = parameters.getComponent().getComponent();
    table.setUI(new HeadlessTableUI());

    // Initialize UI
    parameters.getComponent().setSize(new Dimension(200, 200));
    FakeUi fakeUi = new FakeUi(parameters.getComponent());
    fakeUi.layout();
    assertThat(fakeUi.getRoot().isVisible()).isFalse();

    // Add list of features
    List<Module> features = new ArrayList<>();
    features.add(myFeature1);
    features.add(myFeature2);
    parameters.setFeatureList(features);

    assertThat(fakeUi.getRoot().isVisible()).isTrue();
    assertThat(parameters.getDisabledDynamicFeatures()).isEmpty();
    assertThat(table.getRowCount()).isEqualTo(2);
    assertThat(table.getColumnCount()).isEqualTo(2);

    // Click on first feature
    Rectangle checkbox = table.getCellRect(0, 0, true);
    Point tableLocation = fakeUi.getPosition(table);
    fakeUi.mouse.click(tableLocation.x + checkbox.width / 2, tableLocation.y + checkbox.height / 2);

    // Check that the feature is now disabled
    assertThat(parameters.getDisabledDynamicFeatures()).containsExactly("feature1");
  }
}
