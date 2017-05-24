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
package com.android.tools.idea.naveditor.actions;

import com.android.tools.idea.naveditor.NavigationTestCase;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.uibuilder.SyncNlModel;
import com.android.tools.idea.uibuilder.util.NlTreeDumper;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.android.dom.navigation.NavigationSchema;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link CreateFragmentAction}
 */
public class CreateFragmentActionTest extends NavigationTestCase {

  public void testInvoked() throws Exception {
    SyncNlModel model = model("nav.xml",
                              component(NavigationSchema.TAG_NAVIGATION).unboundedChildren(
                                component(NavigationSchema.TAG_FRAGMENT).id("@id/fragment1"),
                                component(NavigationSchema.TAG_FRAGMENT).id("@id/fragment2"))).build();

    NavDesignSurface surface = (NavDesignSurface)model.getSurface();

    CreateFragmentAction action = new CreateFragmentAction(surface);
    action.actionPerformed(mock(AnActionEvent.class));

    assertEquals("NlComponent{tag=<navigation>, instance=0}\n" +
                 "    NlComponent{tag=<fragment>, instance=1}\n" +
                 "    NlComponent{tag=<fragment>, instance=2}\n" +
                 "    NlComponent{tag=<fragment>, instance=3}", new NlTreeDumper().toTree(model.getComponents()));
  }

  public void testInvokedInSubflow() throws Exception {
    SyncNlModel model = model("nav.xml",
                              component(NavigationSchema.TAG_NAVIGATION).unboundedChildren(
                                component(NavigationSchema.TAG_NAVIGATION).id("@id/subflow").
                                  unboundedChildren(component(NavigationSchema.TAG_FRAGMENT).id("@id/fragment2")),
                                component(NavigationSchema.TAG_FRAGMENT).id("@id/fragment1"))).build();

    NavDesignSurface surface = (NavDesignSurface)model.getSurface();
    when(surface.getCurrentNavigation()).thenReturn(model.find("subflow"));

    CreateFragmentAction action = new CreateFragmentAction(surface);
    action.actionPerformed(mock(AnActionEvent.class));

    assertEquals("NlComponent{tag=<navigation>, instance=0}\n" +
                 "    NlComponent{tag=<navigation>, instance=1}\n" +
                 "        NlComponent{tag=<fragment>, instance=2}\n" +
                 "        NlComponent{tag=<fragment>, instance=3}\n" +
                 "    NlComponent{tag=<fragment>, instance=4}", new NlTreeDumper().toTree(model.getComponents()));
  }
}
