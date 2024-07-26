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
package com.android.tools.idea.actions;

import static com.android.tools.idea.actions.DesignerDataKeys.CONFIGURATIONS;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.List;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OrientationMenuActionTest extends AndroidTestCase {
  private static final String FILE_ARROW = " \u2192 ";
  private DataContext myDataContext;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myDataContext = createDataContext();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      myDataContext = null;
    }
    finally {
      super.tearDown();
    }
  }

  public void testAction() {
    OrientationMenuAction action = new OrientationMenuAction(true);
    action.updateActions(myDataContext);
    AnAction[] actions = action.getChildren(ActionManager.getInstance());
    int index = 0;
    checkAction(actions[index++], OrientationMenuAction.SetDeviceStateAction.class, "Portrait");
    checkAction(actions[index++], OrientationMenuAction.SetDeviceStateAction.class, "Landscape");
    checkAction(actions[index++], Separator.class, null);
    checkAction(actions[index++], ActionGroup.class, "UI Mode");
    assertThat(actions).hasLength(index);
  }

  public void testActionWithoutUIMode() {
    OrientationMenuAction action = new OrientationMenuAction(false);
    action.updateActions(myDataContext);
    AnAction[] actions = action.getChildren(ActionManager.getInstance());
    int index = 0;
    checkAction(actions[index++], OrientationMenuAction.SetDeviceStateAction.class, "Portrait");
    checkAction(actions[index++], OrientationMenuAction.SetDeviceStateAction.class, "Landscape");
    assertThat(actions).hasLength(index);
  }

  public void testActionWithExistingLandscapeVariation() throws Exception {
    myFixture.copyFileToProject("configurations/layout1.xml", "res/layout-land/layout1.xml");
    waitForResourceRepositoryUpdates();
    OrientationMenuAction action = new OrientationMenuAction(true);
    action.updateActions(myDataContext);
    AnAction[] actions = action.getChildren(ActionManager.getInstance());
    int index = 0;
    checkAction(actions[index++], OrientationMenuAction.SetDeviceStateAction.class, "Portrait");
    checkAction(actions[index++], OrientationMenuAction.SetDeviceStateAction.class,
                "Landscape" + FILE_ARROW + FileUtil.join("land", "layout1.xml"));
    checkAction(actions[index++], Separator.class, null);
    checkAction(actions[index++], ActionGroup.class, "UI Mode");
    assertThat(actions).hasLength(index);
  }

  public void testActionWithExistingLandscapeAndTabletVariation() throws Exception {
    myFixture.copyFileToProject("configurations/layout1.xml", "res/layout-land/layout1.xml");
    myFixture.copyFileToProject("configurations/layout1.xml", "res/layout-sw600dp/layout1.xml");
    waitForResourceRepositoryUpdates();
    OrientationMenuAction action = new OrientationMenuAction(true);
    action.updateActions(myDataContext);
    AnAction[] actions = action.getChildren(ActionManager.getInstance());
    int index = 0;
    checkAction(actions[index++], OrientationMenuAction.SetDeviceStateAction.class, "Portrait");
    checkAction(actions[index++], OrientationMenuAction.SetDeviceStateAction.class,
                "Landscape" + FILE_ARROW + FileUtil.join("land", "layout1.xml"));
    checkAction(actions[index++], Separator.class, null);
    checkAction(actions[index++], ActionGroup.class, "UI Mode");
    assertThat(actions).hasLength(index);
  }

  private static void checkAction(@NotNull AnAction action, @NotNull Class<? extends AnAction> actionClass, @Nullable String title) {
    assertThat(action.getTemplatePresentation().getText()).isEqualTo(title);
    assertThat(action).isInstanceOf(actionClass);
  }

  private @NotNull DataContext createDataContext() {
    VirtualFile file = myFixture.copyFileToProject("configurations/layout1.xml", "res/layout/layout1.xml");
    ConfigurationManager manager = ConfigurationManager.getOrCreateInstance(myModule);
    Configuration configuration = manager.getConfiguration(file);
    return new DataContext() {
      @Override
      public @Nullable Object getData(@NotNull String dataId) {
        if (CONFIGURATIONS.is(dataId)) {
          return List.of(configuration);
        }
        return null;
      }
    };
  }
}
