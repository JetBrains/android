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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.actions.AndroidTemplateProjectStructureAction;
import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.EmptyAction;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.plugins.gradle.service.project.CommonGradleProjectResolverExtension;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for {@link GradleSpecificInitializer}
 */
public class GradleSpecificInitializerTest {
  @Rule
  public AndroidGradleProjectRule projectRule = new AndroidGradleProjectRule();

  /**
   * Verify {@link AndroidTemplateProjectStructureAction} is used in Welcome dialog
   */
  @Test
  public void testAndroidTemplateProjectStructureActionInWelcomeDialog() {
    AnAction configureProjectStructureAction = ActionManager.getInstance().getAction("WelcomeScreen.Configure.ProjectStructure");
    assertThat(configureProjectStructureAction).isInstanceOf(AndroidTemplateProjectStructureAction.class);
  }

  @Test
  public void testRefreshProjectsActionIsHidden() {
    AnAction refreshProjectsAction = ActionManager.getInstance().getAction("ExternalSystem.RefreshAllProjects");
    assertThat(refreshProjectsAction).isInstanceOf(EmptyAction.class);
  }

  @Test
  public void testSelectProjectToImportActionIsHidden() {
    AnAction selectProjectToImportAction = ActionManager.getInstance().getAction("ExternalSystem.SelectProjectDataToImport");
    assertThat(selectProjectToImportAction).isInstanceOf(EmptyAction.class);
  }

  @Test
  public void testGroovyResourceActionsAreHidden() {
    AnAction rebuildResourcesAction = ActionManager.getInstance().getAction("Groovy.CheckResources.Rebuild");
    assertThat(rebuildResourcesAction).isInstanceOf(EmptyAction.class);

    AnAction buildResourcesAction = ActionManager.getInstance().getAction("Groovy.CheckResources.Make");
    assertThat(buildResourcesAction).isInstanceOf(EmptyAction.class);
  }

  @Test
  public void testJetBrainsVersionCatalogActivation() {
    assertThat(Registry.get(CommonGradleProjectResolverExtension.GRADLE_VERSION_CATALOGS_DYNAMIC_SUPPORT).asBoolean()).isTrue();
  }
}
