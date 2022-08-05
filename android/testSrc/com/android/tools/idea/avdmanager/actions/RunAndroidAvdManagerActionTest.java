/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.avdmanager.actions;

import com.android.tools.idea.IdeInfo;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.ide.util.gotoByName.GotoActionItemProvider;
import com.intellij.ide.util.gotoByName.GotoActionModel;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.testFramework.LightPlatformTestCase;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;

public class RunAndroidAvdManagerActionTest extends LightPlatformTestCase {

  public static final String ANDROID_DEVICE_MANAGER_ID = "Android.DeviceManager";

  public void testAvdManagerActionFoundByFindAction() {
    GotoActionModel model = new GotoActionModel(null, null, null);
    GotoActionItemProvider provider = new GotoActionItemProvider(model);
    AnAction runAvdManagerAction = ActionManager.getInstance().getAction(ANDROID_DEVICE_MANAGER_ID);
    assertNotNull(runAvdManagerAction);

    List<GotoActionModel.MatchedValue> actionsFoundByWordAVD = new ArrayList<>();
    provider.filterElements("AVD", actionsFoundByWordAVD::add);

    GotoActionModel.MatchedValue firstMatched = actionsFoundByWordAVD.get(0);
    AnAction firstAction = ((GotoActionModel.ActionWrapper) firstMatched.value).getAction();
    assertEquals(runAvdManagerAction, firstAction);
  }

  public void testAvdManagerActionNotShownInIdeaMainToolbar() {
    AnAction runAvdManagerAction = ActionManager.getInstance().getAction(ANDROID_DEVICE_MANAGER_ID);

    // Preconditions
    assertFalse("There should be no Android facets added permanently to a light project",
                ProjectFacetManager.getInstance(getProject()).hasFacets(AndroidFacet.ID));

    AnActionEvent event = AnActionEvent.createFromAnAction(runAvdManagerAction, null, ActionPlaces.MAIN_TOOLBAR, DataContext.EMPTY_CONTEXT);
    runAvdManagerAction.update(event);
    assertEquals("In IDEA we don't need android-specific actions in MainToolbar in non-Android projects, " +
                 "In AndroidStudio we need them to be always visible.",
                 IdeInfo.getInstance().isAndroidStudio(), event.getPresentation().isVisible());
  }

  public void testAvdManagerActionShownInMainMenu() {
    AnAction runAvdManagerAction = ActionManager.getInstance().getAction(ANDROID_DEVICE_MANAGER_ID);

    // Preconditions
    assertFalse("There should be no Android facets added permanently to a light project",
                ProjectFacetManager.getInstance(getProject()).hasFacets(AndroidFacet.ID));

    AnActionEvent event = AnActionEvent.createFromAnAction(runAvdManagerAction, null, ActionPlaces.MAIN_MENU, DataContext.EMPTY_CONTEXT);
    runAvdManagerAction.update(event);
    assertTrue(event.getPresentation().isVisible());
  }
}