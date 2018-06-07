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
package com.android.tools.idea.configurations;

import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.tools.idea.ui.designer.EditorDesignSurface;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

public class OrientationMenuActionTest extends AndroidTestCase {
  private static final String FILE_ARROW = " \u2192 ";
  private ConfigurationHolder myConfigurationHolder;
  private EditorDesignSurface mySurface;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySurface = mock(EditorDesignSurface.class);
    myConfigurationHolder = createConfigurationAndHolder();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      myConfigurationHolder = null;
      mySurface = null;
    }
    finally {
      super.tearDown();
    }
  }

  public void testAction() {
    OrientationMenuAction action = new OrientationMenuAction(myConfigurationHolder, mySurface);
    Presentation presentation = action.getTemplatePresentation().clone();
    action.createCustomComponent(presentation); // To force updateActions to be called...
    AnAction[] actions = action.getChildren(null);
    checkAction(actions[0], OrientationMenuAction.SetDeviceStateAction.class, "Portrait");
    checkAction(actions[1], OrientationMenuAction.SetDeviceStateAction.class, "Landscape");
    checkAction(actions[2], Separator.class, null);
    checkAction(actions[3], ActionGroup.class, "UI Mode");
    checkAction(actions[4], Separator.class, null);
    checkAction(actions[5], ActionGroup.class, "Night Mode");
    checkAction(actions[6], Separator.class, null);
    checkAction(actions[7], OrientationMenuAction.CreateVariationAction.class, "Create Landscape Variation");
    checkAction(actions[8], OrientationMenuAction.CreateVariationAction.class, "Create Tablet Variation");
    checkAction(actions[9], OrientationMenuAction.CreateVariationAction.class, "Create Other...");
    assertThat(actions).hasLength(10);
  }

  public void testActionWithExistingLandscapeVariation() {
    myFixture.copyFileToProject("configurations/layout1.xml", "res/layout-land/layout1.xml");
    OrientationMenuAction action = new OrientationMenuAction(myConfigurationHolder, mySurface);
    Presentation presentation = action.getTemplatePresentation().clone();
    action.createCustomComponent(presentation); // To force updateActions to be called...
    AnAction[] actions = action.getChildren(null);
    checkAction(actions[0], OrientationMenuAction.SetDeviceStateAction.class, "Portrait");
    checkAction(actions[1], OrientationMenuAction.SetDeviceStateAction.class, "Landscape" + FILE_ARROW + "land/layout1.xml");
    checkAction(actions[2], Separator.class, null);
    checkAction(actions[3], ActionGroup.class, "UI Mode");
    checkAction(actions[4], Separator.class, null);
    checkAction(actions[5], ActionGroup.class, "Night Mode");
    checkAction(actions[6], Separator.class, null);
    checkAction(actions[7], OrientationMenuAction.SwitchToVariationAction.class, "Switch to layout");
    checkAction(actions[8], OrientationMenuAction.SwitchToVariationAction.class, "Switch to layout-land");
    checkAction(actions[9], Separator.class, null);
    checkAction(actions[10], OrientationMenuAction.CreateVariationAction.class, "Create Tablet Variation");
    checkAction(actions[11], OrientationMenuAction.CreateVariationAction.class, "Create Other...");
    assertThat(actions).hasLength(12);
  }

  public void testActionWithExistingLandscapeAndTabletVariation() {
    myFixture.copyFileToProject("configurations/layout1.xml", "res/layout-land/layout1.xml");
    myFixture.copyFileToProject("configurations/layout1.xml", "res/layout-sw600dp/layout1.xml");
    OrientationMenuAction action = new OrientationMenuAction(myConfigurationHolder, mySurface);
    Presentation presentation = action.getTemplatePresentation().clone();
    action.createCustomComponent(presentation); // To force updateActions to be called...
    AnAction[] actions = action.getChildren(null);
    checkAction(actions[0], OrientationMenuAction.SetDeviceStateAction.class, "Portrait");
    checkAction(actions[1], OrientationMenuAction.SetDeviceStateAction.class, "Landscape" + FILE_ARROW + "land/layout1.xml");
    checkAction(actions[2], Separator.class, null);
    checkAction(actions[3], ActionGroup.class, "UI Mode");
    checkAction(actions[4], Separator.class, null);
    checkAction(actions[5], ActionGroup.class, "Night Mode");
    checkAction(actions[6], Separator.class, null);
    checkAction(actions[7], OrientationMenuAction.SwitchToVariationAction.class, "Switch to layout");
    checkAction(actions[8], OrientationMenuAction.SwitchToVariationAction.class, "Switch to layout-land");
    checkAction(actions[9], OrientationMenuAction.SwitchToVariationAction.class, "Switch to layout-sw600dp");
    checkAction(actions[10], Separator.class, null);
    checkAction(actions[11], OrientationMenuAction.CreateVariationAction.class, "Create Other...");
    assertThat(actions).hasLength(12);
  }

  private static void checkAction(@NotNull AnAction action, @NotNull Class<? extends AnAction> actionClass, @Nullable String title) {
    assertThat(action.getTemplatePresentation().getText()).isEqualTo(title);
    assertThat(action).isInstanceOf(actionClass);
  }

  private ConfigurationHolder createConfigurationAndHolder() {
    VirtualFile file = myFixture.copyFileToProject("configurations/layout1.xml", "res/layout/layout1.xml");
    ConfigurationManager manager = ConfigurationManager.getOrCreateInstance(myModule);
    Configuration configuration = Configuration.create(manager, file, new FolderConfiguration());
    return new ConfigurationHolder() {
      @NotNull
      @Override
      public Configuration getConfiguration() {
        return configuration;
      }
    };
  }
}
