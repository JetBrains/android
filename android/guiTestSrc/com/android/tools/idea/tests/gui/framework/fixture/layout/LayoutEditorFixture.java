/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.layout;

import com.android.tools.idea.configurations.ConfigurationToolBar;
import com.android.tools.idea.tests.gui.framework.Wait;
import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture;
import com.google.common.collect.Lists;
import com.intellij.android.designer.AndroidDesignerEditor;
import com.intellij.android.designer.designSurface.AndroidDesignerEditorPanel;
import com.intellij.android.designer.model.IdManager;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.DesignerToolWindow;
import com.intellij.designer.componentTree.AttributeWrapper;
import com.intellij.designer.componentTree.ComponentTree;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.RadComponentVisitor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.LoadingNode;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleColoredRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeModel;
import java.awt.*;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.junit.Assert.*;

/**
 * Fixture wrapping the the layout editor for a particular file
 */
public class LayoutEditorFixture extends ComponentFixture<LayoutEditorFixture, Component> implements LayoutFixture {
  private final AndroidDesignerEditorPanel myPanel;

  public LayoutEditorFixture(@NotNull Robot robot, @NotNull AndroidDesignerEditor editor) {
    super(LayoutEditorFixture.class, robot, (getDesignerPanel(editor)).getComponent());
    myPanel = getDesignerPanel(editor);
  }

  @NotNull
  private static AndroidDesignerEditorPanel getDesignerPanel(@NotNull AndroidDesignerEditor editor) {
    return (AndroidDesignerEditorPanel)editor.getDesignerPanel();
  }

  @Override
  @NotNull
  public RenderErrorPanelFixture getRenderErrors() {
    return new RenderErrorPanelFixture(robot(), this, myPanel);
  }

  @Override
  @NotNull
  public ConfigurationToolbarFixture getToolbar() {
    AndroidDesignerEditorPanel panel = myPanel;
    ConfigurationToolBar toolbar = robot().finder().findByType(panel, ConfigurationToolBar.class, true);
    assertNotNull(toolbar);
    return new ConfigurationToolbarFixture(robot(), this, panel, toolbar);
  }

  /** Returns the palette associated with this layout editor */
  @NotNull
  public LayoutPaletteFixture getPaletteFixture() {
    return new LayoutPaletteFixture(robot(), this, myPanel);
  }

  /** Returns the property sheet associated with this layout editor */
  @NotNull
  public PropertySheetFixture getPropertySheetFixture() {
    return new PropertySheetFixture(robot(), this, myPanel);
  }

  @NotNull
  @Override
  public Object waitForRenderToFinish() {
    return waitForNextRenderToFinish(null);
  }

  /** Rendering token used by {@link #waitForRenderToFinish()} */
  private Object myPreviousRender;

  @Override
  public void waitForNextRenderToFinish() {
    myPreviousRender = waitForNextRenderToFinish(myPreviousRender);
  }

  @NotNull
  @Override
  public Object waitForNextRenderToFinish(@Nullable final Object previous) {
    robot().waitForIdle();

    Wait.minutes(2).expecting("render to finish")
      .until(() -> !myPanel.isRenderPending() && myPanel.getLastResult() != null && myPanel.getLastResult() != previous);

    robot().waitForIdle();

    Object token = myPanel.getLastResult();
    assertNotNull(token);
    return token;
  }

  @Override
  public void requireRenderSuccessful() {
    waitForRenderToFinish();
    requireRenderSuccessful(false, false);
  }

  @Override
  public void requireRenderSuccessful(boolean allowErrors, boolean allowWarnings) {
    getRenderErrors().requireRenderSuccessful(allowErrors, allowWarnings);
  }

  /**
   * Searches for the nth occurrence of a given view in the layout. The ordering of widgets of the same
   * type is by visual order, first vertically, then horizontally (and finally by XML source offset, if they exactly overlap
   * as for example would happen in a {@code <merge>}
   *
   * @param tag the view tag to search for, e.g. "Button" or "TextView"
   * @occurrence the index of the occurrence of the tag, e.g. 0 for the first TextView in the layout
   */
  @NotNull
  public LayoutEditorComponentFixture findView(@NotNull final String tag, int occurrence) {
    waitForRenderToFinish();
    AndroidDesignerEditorPanel panel = myPanel;
    final List<RadViewComponent> components = Lists.newArrayList();
    final RadComponent rootComponent = panel.getRootComponent();
    assertNotNull(rootComponent);
    assertTrue(rootComponent.getClass().getName(), rootComponent instanceof RadViewComponent);
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        addComponents(tag, (RadViewComponent)rootComponent, components);
        // Sort by visual order
        Collections.sort(components, new Comparator<RadViewComponent>() {
          @Override
          public int compare(RadViewComponent component1, RadViewComponent component2) {
            Rectangle bounds1 = component1.getBounds();
            Rectangle bounds2 = component2.getBounds();
            int delta = bounds1.y - bounds2.y;
            if (delta != -1) {
              return delta;
            }
            delta = bounds1.x - bounds2.x;
            if (delta != -1) {
              return delta;
            }
            // Unlikely
            return component1.getTag().getTextOffset() - component2.getTag().getTextOffset();
          }
        });
      }
    });

    assertTrue("Only " + components.size() + " found, not enough for occurrence #" + occurrence, components.size() > occurrence);

    RadViewComponent component = components.get(occurrence);
    return createComponentFixture(component);
  }


  /**
   * Searches for the view with the given id, which should be unique and should exist in the layout.
   *
   * @param id the id to search for (should not include @id/ prefix)
   * @return the corresponding component
   */
  @NotNull
  public LayoutEditorComponentFixture findViewById(@NotNull final String id) {
    assertFalse("Should not include the resource prefix in the id name: " + id, id.startsWith("@"));

    waitForRenderToFinish();
    AndroidDesignerEditorPanel panel = myPanel;
    final RadComponent rootComponent = panel.getRootComponent();
    assertNotNull(rootComponent);
    assertTrue(rootComponent.getClass().getName(), rootComponent instanceof RadViewComponent);
    final AtomicReference<RadViewComponent> reference = new AtomicReference<RadViewComponent>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        rootComponent.accept(new RadComponentVisitor() {
          @Override
          public void endVisit(RadComponent component) {
            RadViewComponent viewComponent = (RadViewComponent)component;
            if (id.equals(IdManager.getIdName(viewComponent.getId()))) {
              assertNull("Id " + id + " occurs more than once in the layout", reference.get());
              reference.set(viewComponent);
            }
          }
        }, true);
      }
    });

    RadViewComponent component = reference.get();
    assertNotNull("No component with " + id + " was found", component);
    return createComponentFixture(component);
  }

  private LayoutEditorComponentFixture createComponentFixture(@NotNull RadViewComponent component) {
    return new LayoutEditorComponentFixture(robot(), component, this, myPanel);
  }

  /** Requires the selection to have the given number of selected widgets */
  public LayoutEditorFixture requireSelectionCount(int count) {
    assertEquals(count, getSelection().size());
    return this;
  }

  /** Requires the selection to have the given number of selected widgets */
  public LayoutEditorFixture requireSelection(@NotNull List<LayoutEditorComponentFixture> components) {
    assertEquals(components, getSelection());
    return this;
  }

  /** Returns a list of the selected views */
  @NotNull
  public List<LayoutEditorComponentFixture> getSelection() {
    List<LayoutEditorComponentFixture> selection = Lists.newArrayList();
    for (RadComponent component : myPanel.getSurfaceArea().getSelection()) {
      if (component instanceof RadViewComponent) {
        selection.add(createComponentFixture((RadViewComponent)component));
      }
    }
    return selection;
  }

  private static void addComponents(@NotNull String tag, @NotNull RadViewComponent component, @NotNull List<RadViewComponent> components) {
    if (tag.equals(component.getTag().getName())) {
      components.add(component);
    }

    for (RadComponent child : component.getChildren()) {
      if (child instanceof RadViewComponent) {
        addComponents(tag, (RadViewComponent)child, components);
      }
    }
  }

  /**
   * Checks that the component tree matches a given description.
   * Like {@link #requireComponents(String, boolean)}, but queries the
   * actual UI tree and will not include nodes not yet created (e.g.
   * because the UI node is not expanded)
   *
   * @param showSelected if true, mark selected items in the description
   */
  public void requireComponentTree(String description, boolean showSelected) {
    assertEquals(description, describeComponentTree(showSelected));
  }

  /**
   * Checks that the component hierarchy matches a given description.
   * Like {@link #requireComponentTree(String, boolean)}, but includes all
   * components regardless of whether they are included/expanded in the tree.
   *
   * @param showSelected if true, mark selected items in the description
   */
  public void requireComponents(String description, boolean showSelected) {
    assertEquals(description, describeComponents(showSelected));
  }

  /**
   * Describes the current state of the components. This is similar to
   * {@link #describeComponentTree(boolean)}, but this will unconditionally
   * show all components, whereas {@link #describeComponentTree(boolean)} will
   * not show nodes that have not been expanded/loaded.
   *
   * @param showSelected if true, mark selected items in the description
   * @return a description of the components hierarchy
   */
  public String describeComponents(final boolean showSelected) {
    final StringBuilder sb = new StringBuilder(100);
    final RadComponent root = myPanel.getRootComponent();
    if (root != null) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          SimpleColoredRenderer renderer = new SimpleColoredRenderer();
          AttributeWrapper wrapper = new AttributeWrapper() {
            @Override
            public SimpleTextAttributes getAttribute(SimpleTextAttributes attributes) {
              return SimpleTextAttributes.REGULAR_ATTRIBUTES;
            }
          };
          describe(renderer, wrapper, root, showSelected, 0);
          SimpleColoredComponent.ColoredIterator iterator = renderer.iterator();
          while (iterator.hasNext()) {
            iterator.next();
            sb.append(iterator.getFragment());
          }
        }
      });
    }

    return sb.toString();
  }

  /**
   * Describes the current state of the component tree. Like {@link #describeComponents(boolean)}, but queries the actual UI tree widget
   * rather than the internal component hierarchy. This means it won't include UI nodes that have not been created yet, such as nodes in
   * unexpanded part of the tree.
   *
   * @param showSelected if true, mark selected items in the description
   * @return a description of the component tree
   */
  public String describeComponentTree(final boolean showSelected) {
    final StringBuilder sb = new StringBuilder(100);
    DesignerToolWindow toolWindow = myPanel.getToolWindow();
    if (toolWindow != null) {
      final ComponentTree componentTree = toolWindow.getComponentTree();
      final TreeModel model = componentTree.getModel();
      final Object root = model.getRoot();
      if (root != null) {
        execute(new GuiTask() {
          @Override
          protected void executeInEDT() throws Throwable {
            SimpleColoredRenderer renderer = new SimpleColoredRenderer();
            AttributeWrapper wrapper = new AttributeWrapper() {
              @Override
              public SimpleTextAttributes getAttribute(SimpleTextAttributes attributes) {
                return SimpleTextAttributes.REGULAR_ATTRIBUTES;
              }
            };
            if (componentTree.isRootVisible()) {
              describe(renderer, wrapper, componentTree, model, root, showSelected, 0);
            }
            else {
              for (int i = 0, n = model.getChildCount(root); i < n; i++) {
                Object child = model.getChild(root, i);
                describe(renderer, wrapper, componentTree, model, child, showSelected, 0);
              }
            }
            SimpleColoredComponent.ColoredIterator iterator = renderer.iterator();
            while (iterator.hasNext()) {
              iterator.next();
              sb.append(iterator.getFragment());
            }
          }
        });
      }
    }
    return sb.toString();
  }

  private void describe(@NotNull SimpleColoredRenderer renderer,
                        @NotNull AttributeWrapper wrapper,
                        @NotNull ComponentTree componentTree,
                        @NotNull TreeModel model,
                        @NotNull Object node,
                        boolean showSelected,
                        int depth) {
    SimpleTextAttributes style = wrapper.getAttribute(SimpleTextAttributes.REGULAR_ATTRIBUTES);
    for (int i = 0; i < depth; i++) {
      renderer.append("    ", style);
    }

    if (!model.isLeaf(node) && model.getChildCount(node) > 0 &&
      model.getChild(node, 0) instanceof LoadingNode) {
      renderer.append("> ", style);
    }

    RadComponent component = componentTree.extractComponent(node);
    if (component != null) {
      if (showSelected && myPanel.getSurfaceArea().isSelected(component)) {
        renderer.append("*");
      }
      myPanel.getTreeDecorator().decorate(component, renderer, wrapper, true);
    }
    else {
      renderer.append("<missing component>", style);
    }
    renderer.append("\n", style);

    if (!model.isLeaf(node)) {
      for (int i = 0, n = model.getChildCount(node); i < n; i++) {
        Object child = model.getChild(node, i);
        if (child instanceof LoadingNode) {
          continue;
        }
        describe(renderer, wrapper, componentTree, model, child, showSelected, depth + 1);
      }
    }
  }

  private void describe(@NotNull SimpleColoredRenderer renderer,
                        @NotNull AttributeWrapper wrapper,
                        @NotNull RadComponent component,
                        boolean showSelected,
                        int depth) {
    SimpleTextAttributes style = wrapper.getAttribute(SimpleTextAttributes.REGULAR_ATTRIBUTES);
    for (int i = 0; i < depth; i++) {
      renderer.append("    ", style);
    }

    if (showSelected && myPanel.getSurfaceArea().isSelected(component)) {
      renderer.append("*");
    }
    myPanel.getTreeDecorator().decorate(component, renderer, wrapper, true);
    renderer.append("\n", style);

    for (RadComponent child : component.getChildren()) {
      describe(renderer, wrapper, child, showSelected, depth + 1);
    }
  }

  /**
   * Move the mouse to the given panel coordinates
   *
   * @param point the point to move to
   */
  public void moveMouse(@NotNull Point point) {
    robot().moveMouse(myPanel.getComponent(), point.x, point.y);
    robot().waitForIdle();
  }
}
