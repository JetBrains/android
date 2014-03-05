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

import com.android.navigation.Dimension;
import com.android.navigation.NavigationModel;
import com.android.navigation.State;
import com.android.navigation.Transition;
import com.android.tools.idea.rendering.RenderedView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

import static com.android.tools.idea.editors.navigation.NavigationView.Line;
import static com.android.tools.idea.editors.navigation.Utilities.diff;

class Selections {
  private static final Color SELECTION_COLOR = Color.BLUE;
  private static final int SELECTION_RECTANGLE_LINE_WIDTH = 4;

  public static Selection NULL = new EmptySelection();

  abstract static class Selection {

    protected abstract void moveTo(Point location);

    protected abstract Selection finaliseSelectionLocation(Point location);

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
    protected Selection finaliseSelectionLocation(Point location) {
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
        int l = SELECTION_RECTANGLE_LINE_WIDTH / 2;
        selection.grow(l, l);
        g2D.drawRect(selection.x, selection.y, selection.width, selection.height);
      }
    }

    @Override
    protected void paintOver(Graphics g) {
    }

    @Override
    protected Selection finaliseSelectionLocation(Point location) {
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
    private final Transform myTransform;

    AndroidRootComponentSelection(NavigationModel navigationModel,
                                  AndroidRootComponent component,
                                  Point mouseDownLocation,
                                  Transition transition,
                                  State state,
                                  Transform transform) {
      super(navigationModel, component, transition);
      myMouseDownLocation = mouseDownLocation;
      myOrigComponentLocation = myComponent.getLocation();
      myState = state;
      myTransform = transform;
    }

    private void moveTo(Point location, boolean snap) {
      Point newLocation = Utilities.add(diff(location, myMouseDownLocation), myOrigComponentLocation);
      if (snap) {
        newLocation = Utilities.snap(newLocation, myTransform.modelToView(Dimension.create(NavigationView.MIDDLE_SNAP_GRID)));
      }
      myComponent.setLocation(newLocation);
      myNavigationModel.getStateToLocation().put(myState, myTransform.viewToModel(newLocation));
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
    protected Selection finaliseSelectionLocation(Point location) {
      moveTo(location, true);
      return this;
    }
  }

  static class RelationSelection extends Selection {
    private final AndroidRootComponent mySourceComponent;
    private final NavigationView myNavigationEditor;
    private final NavigationModel myNavigationModel;
    private final RenderedView myNamedLeaf;
    @NotNull private Point myMouseLocation;

    RelationSelection(NavigationModel navigationModel,
                      @NotNull AndroidRootComponent sourceComponent,
                      @NotNull Point mouseDownLocation,
                      @Nullable RenderedView namedLeaf,
                      @NotNull NavigationView navigationEditor) {
      myNavigationModel = navigationModel;
      mySourceComponent = sourceComponent;
      myMouseLocation = mouseDownLocation;
      myNamedLeaf = namedLeaf;
      myNavigationEditor = navigationEditor;
    }

    @Override
    protected void moveTo(Point location) {
      myMouseLocation = location;
    }

    @Override
    protected void paint(Graphics g, boolean hasFocus) {
    }

    @Override
    protected void paintOver(Graphics g) {
      int lineWidth = mySourceComponent.transform.modelToViewW(NavigationView.LINE_WIDTH);
      Graphics2D lineGraphics = NavigationView.createLineGraphics(g, lineWidth);
      Rectangle sourceBounds = NavigationView.getBounds(mySourceComponent, myNamedLeaf);
      Rectangle destBounds = myNavigationEditor.getNamedLeafBoundsAt(mySourceComponent, myMouseLocation);
      Rectangle sourceComponentBounds = mySourceComponent.getBounds();
      // if the mouse hasn't left the bounds of the originating component yet, use leaf bounds instead for the midLine calculation
      Rectangle startBounds = sourceComponentBounds.contains(myMouseLocation) ? sourceBounds : sourceComponentBounds;
      Line midLine = NavigationView.getMidLine(startBounds, new Rectangle(myMouseLocation));
      Point[] controlPoints = NavigationView.getControlPoints(sourceBounds, destBounds, midLine);
      myNavigationEditor.drawTransition(lineGraphics, sourceBounds, destBounds, controlPoints);
    }

    @Override
    protected Selection finaliseSelectionLocation(Point mouseUpLocation) {
      Transition transition = myNavigationEditor.getTransition(mySourceComponent, myNamedLeaf, mouseUpLocation);
      if (transition != null) {
        myNavigationModel.add(transition);
      }
      return NULL;
    }

    @Override
    protected void remove() {
    }
  }
}
