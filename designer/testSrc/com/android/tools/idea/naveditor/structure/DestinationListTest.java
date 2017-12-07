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

import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.fixtures.ComponentDescriptor;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.naveditor.NavTestCase;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.naveditor.surface.NavView;
import com.google.common.collect.ImmutableList;
import com.intellij.ui.ColoredListCellRenderer;
import icons.StudioIcons;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.naveditor.NavModelBuilderUtil.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DestinationList}
 */
public class DestinationListTest extends NavTestCase {

  private SyncNlModel myModel;
  private DestinationList myList;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myModel = model("nav.xml",
                    rootComponent("root").unboundedChildren(
                      fragmentComponent("fragment1"),
                      fragmentComponent("fragment2"),
                      navigationComponent("subnav")
                        .unboundedChildren(fragmentComponent("fragment3"))))
      .build();
    DestinationList.DestinationListDefinition def = new DestinationList.DestinationListDefinition();
    myList = (DestinationList)def.getFactory().create();
    DesignSurface surface = myModel.getSurface();
    SceneView sceneView = new NavView((NavDesignSurface)surface, surface.getSceneManager());
    when(surface.getCurrentSceneView()).thenReturn(sceneView);
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

  public void testSelection() {
    DestinationList.DestinationListDefinition def = new DestinationList.DestinationListDefinition();
    DestinationList list = (DestinationList)def.getFactory().create();
    list.setToolContext(myModel.getSurface());
    ImmutableList<NlComponent> selection = ImmutableList.of(myModel.find("fragment1"));
    SelectionModel modelSelectionModel = myModel.getSurface().getSelectionModel();
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

  public void testSubflow() {
    DestinationList.DestinationListDefinition def = new DestinationList.DestinationListDefinition();
    DestinationList list = (DestinationList)def.getFactory().create();
    list.setToolContext(myModel.getSurface());
    ImmutableList<NlComponent> selection = ImmutableList.of(myModel.find("subnav"));
    SelectionModel modelSelectionModel = myModel.getSurface().getSelectionModel();
    modelSelectionModel.setSelection(selection);
    SelectionModel listSelectionModel = list.mySelectionModel;
    assertEquals(selection, listSelectionModel.getSelection());

    selection = ImmutableList.of(myModel.find("subnav"));
    listSelectionModel.setSelection(selection);
    assertEquals(selection, modelSelectionModel.getSelection());
  }

  public void testModifyModel() {
    ComponentDescriptor root = rootComponent("root").unboundedChildren(
      fragmentComponent("fragment1"),
      fragmentComponent("fragment2"));
    ModelBuilder modelBuilder = model("nav.xml", root);
    SyncNlModel model = modelBuilder.build();
    DestinationList.DestinationListDefinition def = new DestinationList.DestinationListDefinition();
    DestinationList list = (DestinationList)def.getFactory().create();

    SceneView sceneView = new NavView((NavDesignSurface)model.getSurface(), model.getSurface().getSceneManager());
    when(model.getSurface().getCurrentSceneView()).thenReturn(sceneView);
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
    model.getSurface().getSelectionModel().setSelection(fragment3);
    assertEquals(fragment3, list.mySelectionModel.getSelection());

    model.notifyModified(NlModel.ChangeType.EDIT);
    assertEquals(fragment3, list.mySelectionModel.getSelection());
  }

  public void testDoubleClickActivity() {
    NlComponent nlComponent = myModel.find("fragment2");
    myModel.getSurface().getSelectionModel().setSelection(ImmutableList.of(nlComponent));
    myList.myList.dispatchEvent(new MouseEvent(myList.myList, MouseEvent.MOUSE_CLICKED, 1, 0, 0, 0, 2, false));
    verify((NavDesignSurface)myModel.getSurface()).notifyComponentActivate(nlComponent);
  }

  public void testBack() {
    SyncNlModel model = model("nav.xml",
                              rootComponent("root").unboundedChildren(
                                navigationComponent("subnav")
                                  .withLabelAttribute("sub nav")
                                  .unboundedChildren(navigationComponent("subsubnav")
                                                       .withLabelAttribute("sub sub nav"))))
      .build();

    DestinationList.DestinationListDefinition def = new DestinationList.DestinationListDefinition();
    DestinationList list = (DestinationList)def.getFactory().create();
    NavDesignSurface surface = (NavDesignSurface)model.getSurface();
    SceneView sceneView = new NavView(surface, surface.getSceneManager());
    when(surface.getCurrentSceneView()).thenReturn(sceneView);
    list.setToolContext(surface);

    NlComponent root = model.getComponents().get(0);
    when(surface.getCurrentNavigation()).thenReturn(root);
    surface.getSelectionModel().setSelection(ImmutableList.of(root));
    surface.getSelectionModel().clear();

    assertFalse(list.myBackPanel.isVisible());

    root = root.getChild(0);
    when(surface.getCurrentNavigation()).thenReturn(root);
    surface.getSelectionModel().setSelection(ImmutableList.of(root));
    surface.getSelectionModel().clear();

    assertTrue(list.myBackPanel.isVisible());
    assertEquals(DestinationList.ROOT_NAME, list.myBackLabel.getText());

    list.goBack();
    verify(surface).setCurrentNavigation(root.getParent());

    root = root.getChild(0);
    when(surface.getCurrentNavigation()).thenReturn(root);
    surface.getSelectionModel().setSelection(ImmutableList.of(root));
    surface.getSelectionModel().clear();

    assertTrue(list.myBackPanel.isVisible());
    assertEquals("sub nav", list.myBackLabel.getText());
  }

  public void testRendering() {
    SyncNlModel model = model("nav.xml",
                              rootComponent("root").withStartDestinationAttribute("fragment2")
                                .unboundedChildren(
                                  fragmentComponent("fragment1").withAttribute(ANDROID_URI, ATTR_LABEL, "fragmentLabel"),
                                  fragmentComponent("fragment2"),
                                  activityComponent("activity"),
                                  navigationComponent("nav1").withAttribute(ANDROID_URI, ATTR_LABEL, "navName"),
                                  navigationComponent("nav2"),
                                  includeComponent("navigation")))
      .build();
    DestinationList.DestinationListDefinition def = new DestinationList.DestinationListDefinition();
    DestinationList list = (DestinationList)def.getFactory().create();
    DesignSurface surface = model.getSurface();
    SceneView sceneView = new NavView((NavDesignSurface)surface, surface.getSceneManager());
    when(surface.getCurrentSceneView()).thenReturn(sceneView);
    list.setToolContext(surface);

    assertEquals(6, list.myList.getItemsCount());

    @SuppressWarnings("unchecked")
    ListCellRenderer<NlComponent> renderer = (ListCellRenderer<NlComponent>)list.myList.getCellRenderer();
    Map<String, Icon> result = new HashMap<>();
    for (int i = 0; i < list.myList.getItemsCount(); i++) {
      ColoredListCellRenderer<NlComponent> component = (ColoredListCellRenderer<NlComponent>)renderer
        .getListCellRendererComponent(list.myList, list.myList.getModel().getElementAt(i), i, false, false);
      result.put(component.toString(), component.getIcon());
    }

    assertEquals(StudioIcons.NavEditor.Tree.FRAGMENT, result.get("fragmentLabel"));
    assertEquals(StudioIcons.NavEditor.Tree.FRAGMENT, result.get("fragment2 - Start"));
    assertEquals(StudioIcons.NavEditor.Tree.ACTIVITY, result.get("activity"));
    assertEquals(StudioIcons.NavEditor.Tree.NESTED_GRAPH, result.get("navName"));
    assertEquals(StudioIcons.NavEditor.Tree.NESTED_GRAPH, result.get("navName"));
    assertEquals(StudioIcons.NavEditor.Tree.INCLUDE_GRAPH, result.get("myCoolLabel"));
  }
}
