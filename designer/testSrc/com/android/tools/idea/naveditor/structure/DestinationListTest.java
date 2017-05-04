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
package com.android.tools.idea.naveditor.structure;

import com.android.tools.idea.naveditor.NavigationTestCase;
import com.android.tools.idea.uibuilder.SyncNlModel;
import com.android.tools.idea.uibuilder.fixtures.ComponentDescriptor;
import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.SelectionModel;
import com.google.common.collect.ImmutableList;
import org.jetbrains.android.dom.navigation.NavigationSchema;

/**
 * Tests for {@link DestinationList}
 */
public class DestinationListTest extends NavigationTestCase {
  public void testSelection() throws Exception {
    SyncNlModel model = model("nav.xml",
                              component(NavigationSchema.TAG_NAVIGATION).unboundedChildren(
                                component(NavigationSchema.TAG_FRAGMENT).id("@id/fragment1"),
                                component(NavigationSchema.TAG_FRAGMENT).id("@id/fragment2"))).build();

    DestinationList.DestinationListDefinition def = new DestinationList.DestinationListDefinition();
    DestinationList list = (DestinationList)def.getFactory().create();
    list.setToolContext(model.getSurface());
    ImmutableList<NlComponent> selection = ImmutableList.of(model.find("fragment1"));
    SelectionModel modelSelectionModel = model.getSelectionModel();
    modelSelectionModel.setSelection(selection);
    SelectionModel listSelectionModel = list.mySelectionModel;
    assertEquals(selection, listSelectionModel.getSelection());

    selection = ImmutableList.of(model.find("fragment2"));
    modelSelectionModel.setSelection(selection);
    assertEquals(selection, listSelectionModel.getSelection());

    selection = ImmutableList.of(model.find("fragment1"), model.find("fragment2"));
    modelSelectionModel.setSelection(selection);
    assertEquals(selection, listSelectionModel.getSelection());

    selection = ImmutableList.of();
    modelSelectionModel.setSelection(selection);
    assertEquals(selection, listSelectionModel.getSelection());

    selection = ImmutableList.of(model.find("fragment1"));
    listSelectionModel.setSelection(selection);
    assertEquals(selection, modelSelectionModel.getSelection());

    selection = ImmutableList.of(model.find("fragment2"));
    listSelectionModel.setSelection(selection);
    assertEquals(selection, modelSelectionModel.getSelection());

    selection = ImmutableList.of(model.find("fragment1"), model.find("fragment2"));
    listSelectionModel.setSelection(selection);
    assertEquals(selection, modelSelectionModel.getSelection());
  }

  public void testModifyModel() throws Exception {
    ComponentDescriptor root = component(NavigationSchema.TAG_NAVIGATION).unboundedChildren(
      component(NavigationSchema.TAG_FRAGMENT).id("@id/fragment1"),
      component(NavigationSchema.TAG_FRAGMENT).id("@id/fragment2"));
    ModelBuilder modelBuilder = model("nav.xml", root);
    SyncNlModel model = modelBuilder.build();
    DestinationList.DestinationListDefinition def = new DestinationList.DestinationListDefinition();
    DestinationList list = (DestinationList)def.getFactory().create();
    list.setToolContext(model.getSurface());

    assertEquals(ImmutableList.of(model.find("fragment1"), model.find("fragment2")), list.myComponentList);

    root.addChild(component(NavigationSchema.TAG_FRAGMENT).id("@id/fragment3"), null);
    modelBuilder.updateModel(model);

    assertEquals(ImmutableList.of(model.find("fragment1"), model.find("fragment2"), model.find("fragment3")), list.myComponentList);
  }
}
