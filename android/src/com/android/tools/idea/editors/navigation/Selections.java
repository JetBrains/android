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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Map;

import static com.android.tools.idea.editors.navigation.Utilities.diff;

class Selections {
  private static final Color SELECTION_COLOR = Color.BLUE;
  private static final int SELECTION_RECTANGLE_LINE_WIDTH = 4;

  public static Selection NULL = new EmptySelection();

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

  static class ComponentSelection<T extends Component> extends Selection {
    protected final T myComponent;
    protected final Transition myTransition;
    protected final NavigationModel myNavigationModel;

    ComponentSelection(NavigationModel navigationModel, T component, Transition transition) {
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

  static class AndroidRootComponentSelection extends ComponentSelection<AndroidRootComponent> {
    protected final Point myMouseDownLocation;
    protected final Point myOrigComponentLocation;
    private final State myState;

    AndroidRootComponentSelection(NavigationModel navigationModel, AndroidRootComponent component,
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

  static class RelationSelection extends Selection {
    @NotNull private final AndroidRootComponent myComponent;
    @NotNull private Point myLocation;
    @Nullable private final RenderedView myLeaf;
    @Nullable private final RenderedView myNamedLeaf;
    private NavigationModel myNavigationModel;

    RelationSelection(NavigationModel navigationModel,
                      @NotNull AndroidRootComponent component,
                      @NotNull Point mouseDownLocation,
                      @Nullable RenderedView leaf, @Nullable RenderedView namedLeaf) {
      myNavigationModel = navigationModel;
      myComponent = component;
      myLocation = mouseDownLocation;
      myLeaf = leaf;
      myNamedLeaf = namedLeaf;
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
      Graphics2D lineGraphics = NavigationEditorPanel2.createLineGraphics(g);
      Point start = Utilities.centre(myComponent.getBounds(myNamedLeaf));
      Utilities.drawArrow(lineGraphics, start.x, start.y, myLocation.x, myLocation.y);
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
            RenderedView endLeaf = NavigationEditorPanel2.getRenderedView(destinationRoot, mouseUpLocation);
            RenderedView namedEndLeaf = NavigationEditorPanel2.getNamedParent(endLeaf);
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
