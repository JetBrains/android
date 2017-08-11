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
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.fixtures.ComponentDescriptor;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import com.google.common.collect.ImmutableList;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Collections;

import static org.mockito.Mockito.*;

/**
 * Tests for {@link DestinationList}
 */
public class DestinationListTest extends NavigationTestCase {

  private SyncNlModel myModel;
  private DestinationList myList;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myModel = model("nav.xml",
                    rootComponent().unboundedChildren(
                      fragmentComponent("fragment1"),
                      fragmentComponent("fragment2"),
                      navigationComponent("subnav")
                        .unboundedChildren(fragmentComponent("fragment3"))))
      .build();
    DestinationList.DestinationListDefinition def = new DestinationList.DestinationListDefinition();
    myList = (DestinationList)def.getFactory().create();
    DesignSurface surface = myModel.getSurface();
    SceneView sceneView = mock(SceneView.class);
    when(sceneView.getConfiguration()).thenReturn(myModel.getConfiguration());
    when(surface.getCurrentSceneView()).thenReturn(sceneView);
    when(sceneView.getModel()).thenReturn(myModel);
    myList.setToolContext(surface);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myModel = null;
      myList = null;
    }
    finally {
      super.tearDown();
    }
  }

  public void testSelection() throws Exception {
    DestinationList.DestinationListDefinition def = new DestinationList.DestinationListDefinition();
    DestinationList list = (DestinationList)def.getFactory().create();
    list.setToolContext(myModel.getSurface());
    ImmutableList<NlComponent> selection = ImmutableList.of(myModel.find("fragment1"));
    SelectionModel modelSelectionModel = myModel.getSelectionModel();
    modelSelectionModel.setSelection(selection);
    SelectionModel listSelectionModel = list.mySelectionModel;
    assertEquals(selection, listSelectionModel.getSelection());

    selection = ImmutableList.of(myModel.find("fragment2"));
    modelSelectionModel.setSelection(selection);
    assertEquals(selection, listSelectionModel.getSelection());

    selection = ImmutableList.of(myModel.find("fragment1"), myModel.find("fragment2"));
    modelSelectionModel.setSelection(selection);
    assertEquals(selection, listSelectionModel.getSelection());

    selection = ImmutableList.of();
    modelSelectionModel.setSelection(selection);
    assertEquals(selection, listSelectionModel.getSelection());

    selection = ImmutableList.of(myModel.find("fragment1"));
    listSelectionModel.setSelection(selection);
    assertEquals(selection, modelSelectionModel.getSelection());

    selection = ImmutableList.of(myModel.find("fragment2"));
    listSelectionModel.setSelection(selection);
    assertEquals(selection, modelSelectionModel.getSelection());

    selection = ImmutableList.of(myModel.find("fragment1"), myModel.find("fragment2"));
    listSelectionModel.setSelection(selection);
    assertEquals(selection, modelSelectionModel.getSelection());
  }

  public void testSubflow() throws Exception {

    DestinationList.DestinationListDefinition def = new DestinationList.DestinationListDefinition();
    DestinationList list = (DestinationList)def.getFactory().create();
    list.setToolContext(myModel.getSurface());
    ImmutableList<NlComponent> selection = ImmutableList.of(myModel.find("subnav"));
    SelectionModel modelSelectionModel = myModel.getSelectionModel();
    modelSelectionModel.setSelection(selection);
    SelectionModel listSelectionModel = list.mySelectionModel;
    assertEquals(selection, listSelectionModel.getSelection());

    selection = ImmutableList.of(myModel.find("subnav"));
    listSelectionModel.setSelection(selection);
    assertEquals(selection, modelSelectionModel.getSelection());
  }

  public void testModifyModel() throws Exception {
    ComponentDescriptor root = rootComponent().unboundedChildren(
      fragmentComponent("fragment1"),
      fragmentComponent("fragment2"));
    ModelBuilder modelBuilder = model("nav.xml", root);
    SyncNlModel model = modelBuilder.build();
    DestinationList.DestinationListDefinition def = new DestinationList.DestinationListDefinition();
    DestinationList list = (DestinationList)def.getFactory().create();

    SceneView sceneView = mock(SceneView.class);
    when(sceneView.getConfiguration()).thenReturn(model.getConfiguration());
    when(model.getSurface().getCurrentSceneView()).thenReturn(sceneView);
    when(sceneView.getModel()).thenReturn(myModel);
    when(sceneView.getPreferredSize()).thenReturn(new Dimension(100, 100));
    list.setToolContext(model.getSurface());

    //noinspection AssertEqualsBetweenInconvertibleTypes
    assertEquals(ImmutableList.of(model.find("fragment1"), model.find("fragment2")), Collections.list(list.myListModel.elements()));


    root.addChild(fragmentComponent("fragment3"), null);
    modelBuilder.updateModel(model);
    model.notifyModified(NlModel.ChangeType.EDIT);

    //noinspection AssertEqualsBetweenInconvertibleTypes
    assertEquals(ImmutableList.of(model.find("fragment1"), model.find("fragment2"), model.find("fragment3")),
                 Collections.list(list.myListModel.elements()));

    // Verify that modifications that don't add or remove components don't cause the selection to change
    ImmutableList<NlComponent> fragment3 = ImmutableList.of(model.find("fragment3"));
    model.getSelectionModel().setSelection(fragment3);
    assertEquals(fragment3, list.mySelectionModel.getSelection());

    model.notifyModified(NlModel.ChangeType.EDIT);
    assertEquals(fragment3, list.mySelectionModel.getSelection());
  }

  public void testDoubleClickActivity() throws Exception {
    NlComponent nlComponent = myModel.find("fragment2");
    myModel.getSelectionModel().setSelection(ImmutableList.of(nlComponent));
    myList.myList.dispatchEvent(new MouseEvent(myList.myList, MouseEvent.MOUSE_CLICKED, 1, 0, 0, 0, 2, false));
    verify((NavDesignSurface)myModel.getSurface()).notifyComponentActivate(nlComponent);
  }

  public void testBack() throws Exception {
    SyncNlModel model = model("nav.xml",
                              rootComponent().unboundedChildren(
                                navigationComponent("subnav")
                                  .withLabelAttribute("sub nav")
                                  .unboundedChildren(navigationComponent("subsubnav")
                                                       .withLabelAttribute("sub sub nav"))))
      .build();

    DestinationList.DestinationListDefinition def = new DestinationList.DestinationListDefinition();
    DestinationList list = (DestinationList)def.getFactory().create();
    NavDesignSurface surface = (NavDesignSurface)model.getSurface();
    SceneView sceneView = mock(SceneView.class);
    when(sceneView.getConfiguration()).thenReturn(model.getConfiguration());
    when(surface.getCurrentSceneView()).thenReturn(sceneView);
    when(sceneView.getModel()).thenReturn(model);
    list.setToolContext(surface);

    NlComponent root = model.getComponents().get(0);
    when(surface.getCurrentNavigation()).thenReturn(root);
    model.getSelectionModel().setSelection(ImmutableList.of(root));
    model.getSelectionModel().clear();

    assertFalse(list.myBackPanel.isVisible());

    root = root.getChild(0);
    when(surface.getCurrentNavigation()).thenReturn(root);
    model.getSelectionModel().setSelection(ImmutableList.of(root));
    model.getSelectionModel().clear();

    assertTrue(list.myBackPanel.isVisible());
    assertEquals(DestinationList.ROOT_NAME, list.myBackLabel.getText());

    list.goBack();
    verify(surface).setCurrentNavigation(root.getParent());

    root = root.getChild(0);
    when(surface.getCurrentNavigation()).thenReturn(root);
    model.getSelectionModel().setSelection(ImmutableList.of(root));
    model.getSelectionModel().clear();

    assertTrue(list.myBackPanel.isVisible());
    assertEquals("sub nav", list.myBackLabel.getText());
  }
}
