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

import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.gradleModule;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.adtui.swing.FakeUi;
import com.android.tools.adtui.swing.laf.HeadlessTableUI;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.testing.TestModuleUtil;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import javax.swing.JTable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DynamicFeaturesParametersTest {
  @Parameterized.Parameter(0)
  public boolean featureOnFeatureFlagEnabled = true;
  @Rule
  public final AndroidProjectRule projectRule = AndroidProjectRule.testProject(AndroidCoreTestProject.DYNAMIC_APP);

  @Before
  public void setUp() throws Exception {
    StudioFlags.SUPPORT_FEATURE_ON_FEATURE_DEPS.override(featureOnFeatureFlagEnabled);
  }

  @After
  public void tearDown() throws Exception {
    StudioFlags.SUPPORT_FEATURE_ON_FEATURE_DEPS.clearOverride();
  }

  @Test
  public void emptyListWorks() {
    DynamicFeaturesParameters parameters = new DynamicFeaturesParameters();
    FakeUi fakeUi = new FakeUi(parameters.getComponent());
    fakeUi.layout();

    assertThat(fakeUi.getRoot().isVisible()).isFalse();
    assertThat(parameters.getDisabledDynamicFeatures()).isEmpty();
    assertThat(parameters.getUndoPanel().isVisible()).isFalse();
  }

  @Test
  public void showsFeatureListWithEnableDisableCheckboxes() throws Exception {
    DynamicFeaturesParameters parameters = loadParametersForDynamicApp();
    FakeUi fakeUi = initializeUi(parameters);
    JTable table = parameters.getTableComponent();

    assertThat(parameters.getDisabledDynamicFeatures()).isEmpty();
    assertThat(table.getRowCount()).isEqualTo(3); // base, feature1, dependsOnFeature1
    assertThat(table.getColumnCount()).isEqualTo(3);

    // These will always be sorted by name
    assertThat(table.getValueAt(0, 1)).isEqualTo(gradleModule(projectRule.getProject(), ":app").getName());
    assertThat(table.getValueAt(1, 1)).isEqualTo(gradleModule(projectRule.getProject(), ":dependsOnFeature1").getName());
    assertThat(table.getValueAt(2, 1)).isEqualTo(gradleModule(projectRule.getProject(), ":feature1").getName());

    assertThat(parameters.getUndoPanel().isVisible()).isFalse();

    // Click on first feature
    clickCheckboxInRow(1, fakeUi, table);

    // Check that the feature is now disabled
    assertThat(parameters.getDisabledDynamicFeatures()).containsExactly(
      gradleModule(projectRule.getProject(), ":dependsOnFeature1").getName());
  }

  @Test
  public void showsFeatureOnFeatureDependenciesOnlyWhenFlagEnabled() {
    DynamicFeaturesParameters parameters = loadParametersForDynamicApp();
    initializeUi(parameters);
    JTable table = parameters.getTableComponent();

    assertThat(table.getModel().getValueAt(2, 1)).isEqualTo(gradleModule(projectRule.getProject(), ":feature1").getName());
    String depLabel = (String)table.getValueAt(2, 2);
    if (featureOnFeatureFlagEnabled) {
      assertThat(depLabel).isEqualTo("Required by " + gradleModule(projectRule.getProject(), ":dependsOnFeature1").getName());
    }
    else {
      assertThat(depLabel).isNull();
    }
  }

  @Test
  public void disablesDependentFeatures() {
    DynamicFeaturesParameters parameters = loadParametersForDynamicApp();
    FakeUi fakeUi = initializeUi(parameters);
    JTable table = parameters.getTableComponent();

    // Click on feature1 to uncheck it
    clickCheckboxInRow(2, fakeUi, table);

    if (featureOnFeatureFlagEnabled) {
      // dependsOnFeature1 should have been unchecked
      assertThat(parameters.getDisabledDynamicFeatures())
        .containsExactly(gradleModule(projectRule.getProject(), ":dependsOnFeature1").getName(),
                         gradleModule(projectRule.getProject(), ":feature1").getName());

      // Check that undo works
      assertThat(parameters.getUndoPanel().isVisible()).isTrue();
      assertThat(parameters.getUndoLabel().getText())
        .isEqualTo("1 module requiring " + gradleModule(projectRule.getProject(), ":feature1").getName() + " has been deselected");

      parameters.getUndoLink().doClick();

      assertThat(parameters.getUndoPanel().isVisible()).isFalse();
      assertThat(parameters.getDisabledDynamicFeatures()).isEmpty();
    }
    else {
      assertThat(parameters.getDisabledDynamicFeatures()).containsExactly(gradleModule(projectRule.getProject(), ":feature1").getName());
      assertThat(parameters.getUndoPanel().isVisible()).isFalse();
    }
  }

  @Test
  public void enablesDependencyFeatures() {
    DynamicFeaturesParameters parameters = loadParametersForDynamicApp();
    FakeUi fakeUi = initializeUi(parameters);
    JTable table = parameters.getTableComponent();

    // Remove all checked features
    clickCheckboxInRow(1, fakeUi, table);  // Uncheck dependsOnFeature1
    clickCheckboxInRow(2, fakeUi, table);  // Uncheck feature1

    // Check dependsOnFeature1
    clickCheckboxInRow(1, fakeUi, table);

    if (featureOnFeatureFlagEnabled) {
      // feature1 should have been checked
      assertThat(parameters.getDisabledDynamicFeatures()).isEmpty();

      // Check that undo works
      assertThat(parameters.getUndoPanel().isVisible()).isTrue();
      assertThat(parameters.getUndoLabel().getText())
        .isEqualTo("1 module required by " + gradleModule(projectRule.getProject(), ":dependsOnFeature1").getName() + " has been selected");
      parameters.getUndoLink().doClick();

      assertThat(parameters.getUndoPanel().isVisible()).isFalse();
      assertThat(parameters.getDisabledDynamicFeatures())
        .containsExactly(gradleModule(projectRule.getProject(), ":feature1").getName(),
                         gradleModule(projectRule.getProject(), ":dependsOnFeature1").getName());
    }
    else {
      assertThat(parameters.getDisabledDynamicFeatures()).containsExactly(gradleModule(projectRule.getProject(), ":feature1").getName());
      assertThat(parameters.getUndoPanel().isVisible()).isFalse();
    }
  }

  private DynamicFeaturesParameters loadParametersForDynamicApp() {
    Module appModule = TestModuleUtil.findAppModule(projectRule.getProject());
    DynamicFeaturesParameters parameters = new DynamicFeaturesParameters();
    parameters.setActiveModule(appModule, DynamicFeaturesParameters.AvailableDeployTypes.INSTALLED_ONLY);

    return parameters;
  }

  private FakeUi initializeUi(DynamicFeaturesParameters parameters) {
    parameters.getTableComponent().setUI(new HeadlessTableUI());

    parameters.getComponent().setSize(new Dimension(200, 200));
    FakeUi fakeUi = new FakeUi(parameters.getComponent());
    fakeUi.layout();
    assertThat(fakeUi.getRoot().isVisible()).isTrue();

    return fakeUi;
  }

  private void clickCheckboxInRow(int row, FakeUi fakeUi, JTable featuresTable) {
    Rectangle checkbox = featuresTable.getCellRect(row, 0, true);
    Point tableLocation = fakeUi.getPosition(featuresTable);
    fakeUi.mouse.click(tableLocation.x + checkbox.x + checkbox.width / 2, tableLocation.y + checkbox.y + checkbox.height / 2);
  }

  // We need this name to be short because we can't create a gradle project in a long path on Windows (260 char limit)
  // The name (+ value) is used to create the test directory
  @Parameterized.Parameters(name = "P1={0}")
  public static List<Boolean> paramValues() {
    return ImmutableList.of(false, true);
  }
}
