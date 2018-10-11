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
import com.android.tools.idea.gradle.actions.RefreshProjectAction;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.actionSystem.*;

import java.util.Arrays;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link GradleSpecificInitializer}
 */
public class GradleSpecificInitializerTest extends AndroidGradleTestCase {
  /**
   * Verify {@link AndroidTemplateProjectSettingsGroup} is used in Welcome dialog
   */
  public void testAndroidTemplateProjectSettingsGroupInWelcomeDialog() {
    ActionManager actionManager = ActionManager.getInstance();
    AnAction[] children = ((ActionGroup)actionManager.getAction(IdeActions.GROUP_WELCOME_SCREEN_CONFIGURE)).getChildren(null);
    //noinspection OptionalGetWithoutIsPresent
    AnAction anAction = Arrays.stream(children)
      .filter(action -> action instanceof AndroidTemplateProjectSettingsGroup)
      .findFirst()
      .get();
    assertThat(anAction).isNotNull();
  }

  public void testRefreshProjectsActionIsReplaced() {
    AnAction refreshProjectsAction = ActionManager.getInstance().getAction("ExternalSystem.RefreshAllProjects");
    assertThat(refreshProjectsAction).isInstanceOf(RefreshProjectAction.class);
  }

  public void testSelectProjectToImportActionIsHidden() {
    AnAction selectProjectToImportAction = ActionManager.getInstance().getAction("ExternalSystem.SelectProjectDataToImport");
    assertThat(selectProjectToImportAction).isInstanceOf(EmptyAction.class);
  }
}
