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
package com.android.tools.idea.startup;

import com.android.tools.idea.gradle.actions.AndroidTemplateProjectSettingsGroup;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;

import static com.android.tools.idea.startup.GradleSpecificInitializer.TEMPLATE_PROJECT_SETTINGS_GROUP_ID;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link GradleSpecificInitializer}
 */
public class GradleSpecificInitializerTest extends AndroidGradleTestCase {
  public void testDisableAll() {
    // tests cannot run because issues while loading androidstudio.xml (see ag/2695439)
  }

  /**
   * Verify {@link AndroidTemplateProjectSettingsGroup} is used in ActionManager and in Welcome dialog (b/37141013)
   */
  public void /*test*/AndroidTemplateProjectSettingsGroup() {
    ActionManager actionManager = ActionManager.getInstance();
    AnAction action = actionManager.getAction(TEMPLATE_PROJECT_SETTINGS_GROUP_ID);
    assertThat(action).isNotNull();
    assertThat(action).isInstanceOf(AndroidTemplateProjectSettingsGroup.class);
  }

  /**
   * Verify {@link AndroidTemplateProjectSettingsGroup} is used in Welcome dialog
   */
  public void /*test*/AndroidTemplateProjectSettingsGroupInWelcomeDialog() {
    ActionManager actionManager = ActionManager.getInstance();
    AnAction configureIdeaAction = actionManager.getAction("WelcomeScreen.Configure.IDEA");
    assertThat(configureIdeaAction).isNotNull();
    assertThat(configureIdeaAction).isInstanceOf(DefaultActionGroup.class);
    DefaultActionGroup settingsGroup = (DefaultActionGroup)configureIdeaAction;
    AnAction[] children = settingsGroup.getChildren(null);
    assertThat(children).hasLength(1);
    AnAction child = children[0];
    assertThat(child).isInstanceOf(AndroidTemplateProjectSettingsGroup.class);
  }
}
