/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.logcat.output;

import com.android.tools.idea.flags.StudioFlags;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SimpleConfigurable;
import com.intellij.xdebugger.settings.DebuggerConfigurableProvider;
import com.intellij.xdebugger.settings.DebuggerSettingsCategory;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;

import static com.google.common.truth.Truth.assertThat;

public class LogcatOutputConfigurableProviderTest extends AndroidTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    StudioFlags.RUNDEBUG_LOGCAT_CONSOLE_OUTPUT_ENABLED.override(true);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      StudioFlags.RUNDEBUG_LOGCAT_CONSOLE_OUTPUT_ENABLED.clearOverride();
      LogcatOutputSettings.getInstance().reset();
    }
    finally {
      super.tearDown();
    }
  }

  public void testProviderIsRegistered() throws Exception {
    LogcatOutputConfigurableProvider provider = getLogcatProvider();
    assertThat(provider).isNotNull();
  }

  public void testProviderIsDisabledWithFeatureFlag() throws Exception {
    // Prepare
    StudioFlags.RUNDEBUG_LOGCAT_CONSOLE_OUTPUT_ENABLED.override(false);

    // Act
    LogcatOutputConfigurableProvider provider = getLogcatProvider();
    Collection<? extends Configurable> configurables =
      provider.getConfigurables(DebuggerSettingsCategory.GENERAL);

    // Assert
    assertThat(configurables).isNotNull();
    assertThat(configurables).isEmpty();
  }

  public void testProviderReturnsSingleLogcatConfigurable() throws Exception {
    SimpleConfigurable configurable = getConfigurable();

    assertThat(configurable.getDisplayName()).isEqualTo("Logcat output");
    assertThat(configurable.isModified()).isFalse();

    JComponent component = configurable.createComponent();
    assertThat(component).isNotNull();
    assertThat(component).isInstanceOf(JPanel.class);
  }

  public void testConfigurableCanResetAndApplySettings() throws Exception {
    // Prepare
    SimpleConfigurable configurable = getConfigurable();
    assertThat(LogcatOutputSettings.getInstance().isRunOutputEnabled()).isTrue();
    assertThat(LogcatOutputSettings.getInstance().isDebugOutputEnabled()).isTrue();

    // Act/Assert
    JComponent component = configurable.createComponent();
    assertThat(component).isNotNull();
    assertThat(component).isInstanceOf(JPanel.class);
    assertThat(component.getComponent(0)).isInstanceOf(JCheckBox.class);
    assertThat(component.getComponent(1)).isInstanceOf(JCheckBox.class);

    // Act/Assert
    JCheckBox runCheckbox = ((JCheckBox)component.getComponent(0));
    assertThat(runCheckbox.isSelected()).isFalse();
    JCheckBox debugCheckbox = ((JCheckBox)component.getComponent(1));
    assertThat(debugCheckbox.isSelected()).isFalse();

    // Act/Assert
    configurable.reset();
    assertThat(configurable.isModified()).isFalse();
    assertThat(runCheckbox.isSelected()).isTrue();
    assertThat(debugCheckbox.isSelected()).isTrue();

    // Act/Assert
    runCheckbox.setSelected(false);
    assertThat(configurable.isModified()).isTrue();
    assertThat(LogcatOutputSettings.getInstance().isRunOutputEnabled()).isTrue();
    assertThat(LogcatOutputSettings.getInstance().isDebugOutputEnabled()).isTrue();

    // Act/Assert
    configurable.apply();
    assertThat(configurable.isModified()).isFalse();
    assertThat(LogcatOutputSettings.getInstance().isRunOutputEnabled()).isFalse();
    assertThat(LogcatOutputSettings.getInstance().isDebugOutputEnabled()).isTrue();

    // Act/Assert
    debugCheckbox.setSelected(false);
    assertThat(configurable.isModified()).isTrue();
    assertThat(LogcatOutputSettings.getInstance().isRunOutputEnabled()).isFalse();
    assertThat(LogcatOutputSettings.getInstance().isDebugOutputEnabled()).isTrue();

    // Act/Assert
    configurable.apply();
    assertThat(configurable.isModified()).isFalse();
    assertThat(LogcatOutputSettings.getInstance().isRunOutputEnabled()).isFalse();
    assertThat(LogcatOutputSettings.getInstance().isDebugOutputEnabled()).isFalse();

    // Act/Assert
    runCheckbox.setSelected(true);
    runCheckbox.setSelected(true);
    assertThat(configurable.isModified()).isTrue();
    assertThat(LogcatOutputSettings.getInstance().isRunOutputEnabled()).isFalse();
    assertThat(LogcatOutputSettings.getInstance().isDebugOutputEnabled()).isFalse();

    // Act/Assert
    configurable.reset();
    assertThat(configurable.isModified()).isFalse();
    assertThat(runCheckbox.isSelected()).isFalse();
    assertThat(debugCheckbox.isSelected()).isFalse();
  }

  @NotNull
  private static SimpleConfigurable getConfigurable() {
    LogcatOutputConfigurableProvider provider = getLogcatProvider();
    Collection<? extends Configurable> configurables =
      provider.getConfigurables(DebuggerSettingsCategory.GENERAL);

    // Assert
    assertThat(configurables).isNotNull();
    assertThat(configurables).hasSize(1);

    Configurable target = configurables.stream().findFirst().orElse(null);
    assertThat(target).isNotNull();
    assertThat(target).isInstanceOf(SimpleConfigurable.class);

    return (SimpleConfigurable)target;
  }

  private static LogcatOutputConfigurableProvider getLogcatProvider() {
    return (LogcatOutputConfigurableProvider)
      DebuggerConfigurableProvider.EXTENSION_POINT.getExtensionList().stream().filter(x -> x instanceof LogcatOutputConfigurableProvider).findAny().orElse(null);
  }
}
