/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.editors.navigation;

import com.android.navigation.NavigationModel;
import com.android.navigation.State;
import com.android.navigation.Transition;
import com.android.tools.idea.rendering.RenderedView;
import com.android.tools.idea.rendering.RenderedViewHierarchy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Map;

import static com.android.tools.idea.editors.navigation.Utilities.diff;

class Selections {
  private static final Color SELECTION_COLOR = Color.BLUE;
  private static final int SELECTION_RECTANGLE_LINE_WIDTH = 4;

  public static Selection NULL = new EmptySelection();

  public static Selection create(Point mouseDownLocation,
                                  boolean shiftDown,
                                  NavigationModel navigationModel,
                                  Component component,
                                  Transition transition,
                                  Map<AndroidRootComponent, State> rootToState) {
    if (component instanceof NavigationEditorPanel2) {
      return NULL;
    }
    if (component instanceof AndroidRootComponent) {
      AndroidRootComponent androidRootComponent = (AndroidRootComponent)component;
      if (!shiftDown) {
        return new AndroidRootComponentSelection(navigationModel, androidRootComponent, mouseDownLocation, transition,
                                                           rootToState.get(androidRootComponent));
      }
      else {
        return new RelationSelection(navigationModel, androidRootComponent, mouseDownLocation);
      }
    }
    else {
      return new ComponentSelection<Component>(navigationModel, component, transition);
    }
  }

  abstract static class Selection {

    protected abstract void moveTo(Point location);

    protected abstract Selection finaliseSelectionLocation(Point location,
                                                           Component component,
                                                           Map<AndroidRootComponent, State> rootComponentToState);

    protected abstract void paint(Graphics g, boolean hasFocus);

    protected abstract void paintOver(Graphics g);

    protected abstract void remove();
  }

  private static class EmptySelection extends Selection {
    @Override
    protected void moveTo(Point location) {
    }

    @Override
    protected void paint(Graphics g, boolean hasFocus) {
    }

    @Override
    protected void paintOver(Graphics g) {
    }

    @Override
    protected Selection finaliseSelectionLocation(Point location,
                                                  Component component,
                                                  Map<AndroidRootComponent, State> rootComponentToState) {
      return this;
    }

    @Override
    protected void remove() {
    }
  }

  private static class ComponentSelection<T extends Component> extends Selection {
    protected final T myComponent;
    protected final Transition myTransition;
    protected final NavigationModel myNavigationModel;

    private ComponentSelection(NavigationModel navigationModel, T component, Transition transition) {
      myNavigationModel = navigationModel;
      myComponent = component;
      myTransition = transition;
    }

    @Override
    protected void moveTo(Point location) {
    }

    @Override
    protected void paint(Graphics g, boolean hasFocus) {
      if (hasFocus) {
        Graphics2D g2D = (Graphics2D)g.create();
        g2D.setStroke(new BasicStroke(SELECTION_RECTANGLE_LINE_WIDTH));
        g2D.setColor(SELECTION_COLOR);
        Rectangle selection = myComponent.getBounds();
        int l = SELECTION_RECTANGLE_LINE_WIDTH/2;
        selection.grow(l, l);
        g2D.drawRect(selection.x, selection.y, selection.width, selection.height);
      }
    }

    @Override
    protected void paintOver(Graphics g) {
    }

    @Override
    protected Selection finaliseSelectionLocation(Point location,
                                                  Component component,
                                                  Map<AndroidRootComponent, State> rootComponentToState) {
      return this;
    }

    @Override
    protected void remove() {
      myNavigationModel.remove(myTransition);
    }
  }

  private static class AndroidRootComponentSelection extends ComponentSelection<AndroidRootComponent> {
    protected final Point myMouseDownLocation;
    protected final Point myOrigComponentLocation;
    private final State myState;

    private AndroidRootComponentSelection(NavigationModel navigationModel, AndroidRootComponent component,
                                          Point mouseDownLocation, Transition transition, State state) {
      super(navigationModel, component, transition);
      myMouseDownLocation = mouseDownLocation;
      myOrigComponentLocation = myComponent.getLocation();
      myState = state;
    }

    private void moveTo(Point location, boolean snap) {
      Point newLocation = Utilities.add(diff(location, myMouseDownLocation),
                                        myOrigComponentLocation);
      if (snap) {
        newLocation = Utilities.snap(newLocation, NavigationEditorPanel2.MIDDLE_SNAP_GRID);
      }
      myComponent.setLocation(newLocation);
      myState.setLocation(Utilities.toNavPoint(newLocation));
      myNavigationModel.getListeners().notify(NavigationModel.Event.update(State.class));
    }

    @Override
    protected void moveTo(Point location) {
      moveTo(location, false);
    }

    @Override
    protected void remove() {
      myNavigationModel.removeState(myState);
    }

    @Override
    protected Selection finaliseSelectionLocation(Point location,
                                                  Component component,
                                                  Map<AndroidRootComponent, State> rootComponentToState) {
      moveTo(location, true);
      return this;
    }
  }

  private static class RelationSelection extends Selection {
    @NotNull private final AndroidRootComponent myComponent;
    @NotNull private Point myLocation;
    @Nullable private final RenderedView myLeaf;
    @Nullable private final RenderedView myNamedLeaf;
    private NavigationModel myNavigationModel;

    private RelationSelection(NavigationModel navigationModel, @NotNull AndroidRootComponent component, @NotNull Point mouseDownLocation) {
      myNavigationModel = navigationModel;
      myComponent = component;
      myLocation = mouseDownLocation;
      Point p = component.convertPointFromViewToModel(mouseDownLocation);
      RenderedViewHierarchy hierarchy = component.getRenderResult().getHierarchy();
      myLeaf = hierarchy != null ? hierarchy.findLeafAt(p.x, p.y) : null;
      myNamedLeaf = getNamedParent(myLeaf);
    }

    @Nullable
    private static RenderedView getNamedParent(@Nullable RenderedView view) {
      while (view != null && NavigationEditorPanel2.getViewId(view) == null) {
        view = view.getParent();
      }
      return view;
    }

    @Override
    protected void moveTo(Point location) {
      myLocation = location;
    }

    @Override
    protected void paint(Graphics g, boolean hasFocus) {
    }

    @Override
    protected void paintOver(Graphics g) {
      Graphics2D transitionGraphics = NavigationEditorPanel2.createLineGraphics(g);
      NavigationEditorPanel2.paintLeaf(transitionGraphics, myLeaf, Color.RED, myComponent);
      NavigationEditorPanel2.paintLeaf(transitionGraphics, myNamedLeaf, Color.BLUE, myComponent);
      Point start = Utilities.centre(myComponent.getBounds(myNamedLeaf));
      Utilities.drawArrow(transitionGraphics, start.x, start.y, myLocation.x, myLocation.y);
    }

    @Override
    protected Selection finaliseSelectionLocation(Point mouseUpLocation,
                                                  Component destComponent,
                                                  Map<AndroidRootComponent, State> rootComponentToState) {
      if (destComponent instanceof AndroidRootComponent) {
        if (myComponent != destComponent) {
          Transition transition = Transition.of("", rootComponentToState.get(myComponent), rootComponentToState.get(destComponent));
          transition.getSource().setViewName(NavigationEditorPanel2.getViewId(myNamedLeaf));
          {
            AndroidRootComponent destinationRoot = (AndroidRootComponent)destComponent;
            Point p = destinationRoot.convertPointFromViewToModel(mouseUpLocation);
            RenderedViewHierarchy hierarchy = destinationRoot.getRenderResult().getHierarchy();
            RenderedView endLeaf = hierarchy != null ? hierarchy.findLeafAt(p.x, p.y) : null;
            RenderedView namedEndLeaf = getNamedParent(endLeaf);
            transition.getDestination().setViewName(NavigationEditorPanel2.getViewId(namedEndLeaf));
          }
          myNavigationModel.add(transition);
        }
      }
      return NULL;
    }

    @Override
    protected void remove() {
    }
  }
}
