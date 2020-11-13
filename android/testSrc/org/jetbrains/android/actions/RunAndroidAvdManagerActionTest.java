// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.actions;

import com.intellij.ide.util.gotoByName.GotoActionItemProvider;
import com.intellij.ide.util.gotoByName.GotoActionModel;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.testFramework.LightPlatformTestCase;
import java.util.ArrayList;
import java.util.List;

public class RunAndroidAvdManagerActionTest extends LightPlatformTestCase {
  public void testAvdManagerActionFoundByFindAction() {
    GotoActionModel model = new GotoActionModel(null, null, null);
    GotoActionItemProvider provider = new GotoActionItemProvider(model);
    AnAction runAvdManagerAction = ActionManager.getInstance().getAction(RunAndroidAvdManagerAction.ID);
    assertNotNull(runAvdManagerAction);

    List<GotoActionModel.MatchedValue> actionsFoundByWordAVD = new ArrayList<>();
    provider.filterElements("AVD", actionsFoundByWordAVD::add);

    GotoActionModel.MatchedValue firstMatched = actionsFoundByWordAVD.get(0);
    AnAction firstAction = ((GotoActionModel.ActionWrapper) firstMatched.value).getAction();
    assertEquals(runAvdManagerAction, firstAction);
  }
}