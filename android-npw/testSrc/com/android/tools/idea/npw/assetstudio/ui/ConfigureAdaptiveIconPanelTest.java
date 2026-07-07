/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.npw.assetstudio.ui;

import static com.android.tools.idea.testing.AndroidProjectRuleKt.onEdt;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import com.android.resources.Density;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.npw.assetstudio.icon.AndroidIconType;
import com.android.tools.idea.observable.AbstractProperty;
import com.android.tools.idea.observable.BatchInvoker;
import com.android.tools.idea.observable.TestInvokeStrategy;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObjectValueProperty;
import com.android.tools.idea.rendering.DrawableRenderer;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.testing.EdtAndroidProjectRule;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.RunsInEdt;
import java.io.File;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import org.jetbrains.android.facet.AndroidFacet;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
@RunsInEdt
public class ConfigureAdaptiveIconPanelTest {
  @Rule
  public final EdtAndroidProjectRule myProjectRule = onEdt(AndroidProjectRule.inMemory());

  private final TestInvokeStrategy myInvokeStrategy = new TestInvokeStrategy();

  @Before
  public void setUp() {
    BatchInvoker.setOverrideStrategy(myInvokeStrategy);
  }

  @After
  public void tearDown() {
    BatchInvoker.clearOverrideStrategy();
  }

  @Test
  public void testBackgroundScalingElementsDisabledWhenColorSelected() {
    AndroidFacet facet = AndroidFacet.getInstance(myProjectRule.getFixture().getModule());
    ValidatorPanel validatorPanel = new ValidatorPanel(myProjectRule.getFixture().getProjectDisposable(), new JPanel());
    DrawableRenderer renderer = mock(DrawableRenderer.class);

    BoolValueProperty showGrid = new BoolValueProperty(false);
    BoolValueProperty showSafeZone = new BoolValueProperty(true);
    AbstractProperty<Density> previewDensity = new ObjectValueProperty<>(Density.XHIGH);

    ConfigureAdaptiveIconPanel panel = new ConfigureAdaptiveIconPanel(
      myProjectRule.getFixture().getProjectDisposable(), facet, AndroidIconType.LAUNCHER,
      showGrid, showSafeZone, previewDensity, validatorPanel, renderer, true
    );
    Disposer.register(myProjectRule.getFixture().getProjectDisposable(), panel);

    // Get background asset type radio buttons
    JRadioButton colorRadioButton = panel.getBackgroundColorRadioButton();
    JRadioButton imageRadioButton = panel.getBackgroundImageRadioButton();

    // Set a path to the image asset so it becomes resizable
    panel.getBackgroundImageAssetBrowser().getAsset().imagePath().setNullableValue(new File("test.png"));

    // Select Image first (should be enabled)
    imageRadioButton.setSelected(true);
    myInvokeStrategy.updateAllSteps();

    assertThat(panel.getBackgroundResizeSlider().isEnabled()).isTrue();
    assertThat(panel.getBackgroundResizeValueTextField().isEnabled()).isTrue();

    // Select Color (should be disabled)
    colorRadioButton.setSelected(true);
    myInvokeStrategy.updateAllSteps();

    assertThat(panel.getBackgroundResizeSlider().isEnabled()).isFalse();
    assertThat(panel.getBackgroundResizeValueTextField().isEnabled()).isFalse();
    assertThat(panel.getBackgroundTrimYesRadioButton().isEnabled()).isFalse();
    assertThat(panel.getBackgroundTrimNoRadioButton().isEnabled()).isFalse();
  }

  @Test
  public void testForegroundScalingElementsDisabledWhenNotResizable() {
    AndroidFacet facet = AndroidFacet.getInstance(myProjectRule.getFixture().getModule());
    ValidatorPanel validatorPanel = new ValidatorPanel(myProjectRule.getFixture().getProjectDisposable(), new JPanel());
    DrawableRenderer renderer = mock(DrawableRenderer.class);

    BoolValueProperty showGrid = new BoolValueProperty(false);
    BoolValueProperty showSafeZone = new BoolValueProperty(true);
    AbstractProperty<Density> previewDensity = new ObjectValueProperty<>(Density.XHIGH);

    ConfigureAdaptiveIconPanel panel = new ConfigureAdaptiveIconPanel(
      myProjectRule.getFixture().getProjectDisposable(), facet, AndroidIconType.LAUNCHER,
      showGrid, showSafeZone, previewDensity, validatorPanel, renderer, true
    );
    Disposer.register(myProjectRule.getFixture().getProjectDisposable(), panel);

    // Initially image asset has no path, so it's not resizable
    myInvokeStrategy.updateAllSteps();
    assertThat(panel.getForegroundResizeSlider().isEnabled()).isFalse();
    assertThat(panel.getForegroundResizeValueTextField().isEnabled()).isFalse();

    // Set a path, it becomes resizable
    panel.getForegroundImageAssetBrowser().getAsset().imagePath().setNullableValue(new File("test.png"));
    myInvokeStrategy.updateAllSteps();
    assertThat(panel.getForegroundResizeSlider().isEnabled()).isTrue();
    assertThat(panel.getForegroundResizeValueTextField().isEnabled()).isTrue();
  }

  @Test
  public void testMonochromeNotSupportedInitializationDoesNotThrow() {
    AndroidFacet facet = AndroidFacet.getInstance(myProjectRule.getFixture().getModule());
    ValidatorPanel validatorPanel = new ValidatorPanel(myProjectRule.getFixture().getProjectDisposable(), new JPanel());
    DrawableRenderer renderer = mock(DrawableRenderer.class);

    BoolValueProperty showGrid = new BoolValueProperty(false);
    BoolValueProperty showSafeZone = new BoolValueProperty(true);
    AbstractProperty<Density> previewDensity = new ObjectValueProperty<>(Density.XHIGH);

    ConfigureAdaptiveIconPanel panel = new ConfigureAdaptiveIconPanel(
      myProjectRule.getFixture().getProjectDisposable(), facet, AndroidIconType.TV_CHANNEL,
      showGrid, showSafeZone, previewDensity, validatorPanel, renderer, false
    );
    Disposer.register(myProjectRule.getFixture().getProjectDisposable(), panel);
    myInvokeStrategy.updateAllSteps();
    assertThat(panel).isNotNull();
  }
}
