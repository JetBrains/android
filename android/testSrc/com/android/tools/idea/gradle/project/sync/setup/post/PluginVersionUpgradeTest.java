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
package com.android.tools.idea.gradle.project.sync.setup.post;

import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.ForcedPluginVersionUpgradeStep;
import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.RecommendedPluginVersionUpgradeStep;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link PluginVersionUpgrade}.
 */
public class PluginVersionUpgradeTest extends PlatformTestCase {
  @Mock RecommendedPluginVersionUpgradeStep myUpgradeStep1;
  @Mock RecommendedPluginVersionUpgradeStep myUpgradeStep2;
  @Mock RecommendedPluginVersionUpgradeStep myUpgradeStep3;

  private PluginVersionUpgrade myVersionUpgrade;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    RecommendedPluginVersionUpgradeStep[] recommended = {myUpgradeStep1, myUpgradeStep2, myUpgradeStep3};

    myVersionUpgrade = new PluginVersionUpgrade(getProject(), new ForcedPluginVersionUpgradeStep[0], recommended);
  }

  public void testCheckAndPerformUpgradeWithoutAndroidModule() {
    assertFalse(myVersionUpgrade.checkAndPerformUpgrade());

    Project project = getProject();
    verify(myUpgradeStep1, never()).performUpgradeAndSync(eq(project), any());
    verify(myUpgradeStep2, never()).performUpgradeAndSync(eq(project), any());
    verify(myUpgradeStep3, never()).performUpgradeAndSync(eq(project), any());
  }

  public void testCheckAndPerformUpgradeWhenUpgradeIsPerformed() {
    Module module = getModule();
    simulateAndroidModule(module);


    AndroidPluginInfo pluginInfo = new AndroidPluginInfo(module, null, null);
    Project project = getProject();
    when(myUpgradeStep1.checkUpgradable(project, pluginInfo)).thenReturn(false);
    when(myUpgradeStep1.performUpgradeAndSync(project, pluginInfo)).thenReturn(false);
    when(myUpgradeStep2.checkUpgradable(project, pluginInfo)).thenReturn(true);
    when(myUpgradeStep2.performUpgradeAndSync(project, pluginInfo)).thenReturn(true);

    assertTrue(myVersionUpgrade.checkAndPerformUpgrade());

    verify(myUpgradeStep1, times(1)).checkUpgradable(project, pluginInfo);
    verify(myUpgradeStep1, never()).performUpgradeAndSync(project, pluginInfo);
    verify(myUpgradeStep2, times(1)).checkUpgradable(project, pluginInfo);
    verify(myUpgradeStep2, times(1)).performUpgradeAndSync(project, pluginInfo);
    // because myUpgradeStep2 upgraded the project, myUpgradeStep3 should not be invoked.
    verify(myUpgradeStep3, never()).checkUpgradable(project, pluginInfo);
    verify(myUpgradeStep3, never()).performUpgradeAndSync(project, pluginInfo);
  }

  public void testCheckAndPerformUpgradeWhenUpgradeIsNotPerformed() {
    Module module = getModule();
    simulateAndroidModule(module);

    AndroidPluginInfo pluginInfo = new AndroidPluginInfo(module, null, null);
    Project project = getProject();
    when(myUpgradeStep1.checkUpgradable(project, pluginInfo)).thenReturn(false);
    when(myUpgradeStep1.performUpgradeAndSync(project, pluginInfo)).thenReturn(false);
    when(myUpgradeStep2.checkUpgradable(project, pluginInfo)).thenReturn(false);
    when(myUpgradeStep2.performUpgradeAndSync(project, pluginInfo)).thenReturn(false);
    when(myUpgradeStep3.checkUpgradable(project, pluginInfo)).thenReturn(false);
    when(myUpgradeStep3.performUpgradeAndSync(project, pluginInfo)).thenReturn(false);

    assertFalse(myVersionUpgrade.checkAndPerformUpgrade());

    verify(myUpgradeStep1, times(1)).checkUpgradable(project, pluginInfo);
    verify(myUpgradeStep2, times(1)).checkUpgradable(project, pluginInfo);
    verify(myUpgradeStep3, times(1)).checkUpgradable(project, pluginInfo);
  }

  private static void simulateAndroidModule(@NotNull Module module) {
    IdeAndroidProject androidProject = mock(IdeAndroidProject.class);

    AndroidModuleModel androidModel = mock(AndroidModuleModel.class);
    when(androidModel.getAndroidProject()).thenReturn(androidProject);

    AndroidFacet facet = createAndAddAndroidFacet(module);
    facet.getConfiguration().setModel(androidModel);
  }
}
