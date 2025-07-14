// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.updater.configure;

import com.android.tools.idea.IdeInfo;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jetbrains.android.facet.AndroidFacet;

public class RunSdkConfigActionTest extends LightPlatformTestCase {
  private final AnAction runSdkConfigAction = new RunSdkConfigAction();

  public void testRunSdkConfigActionNotShownInIdeaMainToolbar() {

    // Preconditions
    assertFalse("There should be no Android facets added permanently to a light project",
                ProjectFacetManager.getInstance(getProject()).hasFacets(AndroidFacet.ID));

    AnActionEvent event = AnActionEvent.createFromAnAction(runSdkConfigAction, null, ActionPlaces.MAIN_TOOLBAR, DataContext.EMPTY_CONTEXT);
    runSdkConfigAction.update(event);
    assertEquals("In IDEA we don't need android-specific actions in MainToolbar in non-Android projects, " +
                 "In AndroidStudio we need them to be always visible.",
                 IdeInfo.getInstance().isAndroidStudio(), event.getPresentation().isVisible());
  }

  public void testRunSdkConfigActionShownInMainMenu() {
    // Preconditions
    assertFalse("There should be no Android facets added permanently to a light project",
                ProjectFacetManager.getInstance(getProject()).hasFacets(AndroidFacet.ID));

    AnActionEvent event = AnActionEvent.createFromAnAction(runSdkConfigAction, null, ActionPlaces.MAIN_MENU, DataContext.EMPTY_CONTEXT);
    runSdkConfigAction.update(event);
    assertTrue(event.getPresentation().isVisible());
  }
}