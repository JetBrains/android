/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.constraint;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.sherpa.drawing.*;
import com.android.tools.sherpa.drawing.decorator.*;
import com.android.tools.sherpa.interaction.WidgetInteractionTargets;
import com.android.tools.sherpa.structure.WidgetCompanion;
import org.jetbrains.annotations.NotNull;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.ModelListener;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.sherpa.interaction.MouseInteraction;
import com.android.tools.sherpa.interaction.WidgetMotion;
import com.android.tools.sherpa.interaction.WidgetResize;
import com.android.tools.sherpa.structure.Selection;
import com.android.tools.sherpa.structure.WidgetsScene;
import com.google.tnt.solver.widgets.*;

import java.awt.*;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Maintains a constraint model shadowing the current NlModel
 * and handles the user interactions on it
 */
public class ConstraintModel {

  public static final int DEFAULT_DENSITY = 160;

  private int myDpi = DEFAULT_DENSITY;
  private float myDpiFactor;

  static ConstraintModel ourConstraintModel = new ConstraintModel();
  static ModelListener ourModelListener = null;
  static NlModel ourCurrentModel = null;

  private ViewTransform myViewTransform = new ViewTransform();
  private WidgetsScene myWidgetsScene = new WidgetsScene();
  private Selection mySelection = new Selection(null);
  private WidgetMotion myWidgetMotion;
  private WidgetResize myWidgetResize = new WidgetResize();

  private SceneDraw mySceneDraw;
  private MouseInteraction myMouseInteraction;

  private static Lock ourLock = new ReentrantLock();
  private boolean mShowFakeUI = true;
  private ColorSet mBlueprintColorSet = new BlueprintColorSet();
  private ColorSet mAndroidColorSet = new AndroidColorSet();

  private RepaintSurface myRepaintSurface = new RepaintSurface();
  private boolean mAutoConnect = true;

  //////////////////////////////////////////////////////////////////////////////
  // Static functions
  //////////////////////////////////////////////////////////////////////////////

  /**
   * Update the current model with a new one
   *
   * @param model new model
   */
  public static void useNewModel(@NotNull NlModel model) {
    useNewModel(model, false);
  }

  /**
   * Update the current model with a new one
   *
   * @param model     new model
   * @param didChange true if the new model comes from the modelChanged callback
   */
  private static void useNewModel(@NotNull NlModel model, boolean didChange) {
    ourLock.lock();

    // First, let's make sure we have a listener
    if (ourModelListener == null) {
      ourModelListener = new ModelListener() {
        @Override
        public void modelChanged(@NotNull NlModel model) {
          // TODO: it seems that NlComponentTree / NlModel aren't calling this?..
          useNewModel(model, true);
        }

        @Override
        public void modelRendered(@NotNull NlModel model) {
        }
      };
    }

    // if the current instance of NlModel we have doesn't correspond, update it
    if (ourCurrentModel != model) {
      didChange = true;
      if (ourCurrentModel != null) {
        ourCurrentModel.removeListener(ourModelListener);
      }
      ourCurrentModel = model;
      if (ourCurrentModel != null) {
        ourCurrentModel.addListener(ourModelListener);
      }
      ourConstraintModel = null;
    }

    if (ourConstraintModel == null) {
      ourConstraintModel = new ConstraintModel();
    }

    // If we have a new model or a changed model, let's rebuild our scene
    if (ourCurrentModel != null && didChange) {
      ourConstraintModel.setDpiValue(ourCurrentModel.getConfiguration().getDensity().getDpiValue());
      boolean previousAnimationState = Animator.isAnimationEnabled();
      Animator.setAnimationEnabled(false);
      ourConstraintModel.updateNlModel(model.getComponents());
      Animator.setAnimationEnabled(previousAnimationState);
    }

    ourLock.unlock();
  }

  /**
   * Static accessor for the singleton holding the ConstraintModel
   *
   * @return the current ConstraintModel
   */
  @NotNull
  public static ConstraintModel getModel() {
    ourLock.lock();
    ConstraintModel model = ourConstraintModel;
    ourLock.unlock();
    return model;
  }

  //////////////////////////////////////////////////////////////////////////////
  // Constructor and accessors
  //////////////////////////////////////////////////////////////////////////////

  /**
   * Base constructor
   */
  public ConstraintModel() {
    mySelection.setSelectedAnchor(null);
    myWidgetsScene.setSelection(mySelection);
    myWidgetMotion = new WidgetMotion(myWidgetsScene, mySelection);
    mySceneDraw = new SceneDraw(new BlueprintColorSet(), myWidgetsScene, mySelection,
                                myWidgetMotion, myWidgetResize);
    mySceneDraw.setRepaintableSurface(myRepaintSurface);
    myMouseInteraction = new MouseInteraction(myViewTransform,
                                              myWidgetsScene, mySelection,
                                              myWidgetMotion, myWidgetResize,
                                              mySceneDraw, myMouseInteraction);
  }

  /**
   * Accessor for the WidgetsScene
   *
   * @return the current WidgetsScene
   */
  @NotNull
  public WidgetsScene getScene() {
    return myWidgetsScene;
  }

  /**
   * Accessor for the view transform
   *
   * @return the current view transform
   */
  @NotNull
  public ViewTransform getViewTransform() {
    return myViewTransform;
  }

  /**
   * Accessor for the current selection in our model
   *
   * @return the current selection
   */
  @NotNull
  public Selection getSelection() {
    return mySelection;
  }

  //////////////////////////////////////////////////////////////////////////////
  // Models synchronization
  //////////////////////////////////////////////////////////////////////////////

  /**
   * Use the new components in our model
   *
   * @param components list of components in NlModel
   */
  private void updateNlModel(@NotNull List<NlComponent> components) {
    // Initialize a list of widgets to potentially removed from the current list of widgets
    ArrayList<ConstraintWidget> widgets = new ArrayList(myWidgetsScene.getWidgets());
    if (widgets.size() > 0) {
      // Let's check for widgets to remove
      for (NlComponent component : components) {
        findComponent(component, widgets);
      }

      // After the above loop, the widgets array will only contains widget that
      // are not in the list of components, so we should remove them
      for (ConstraintWidget widget : widgets) {
        myWidgetsScene.removeWidget(widget);
      }
    }

    // Make sure the components exist
    for (NlComponent component : components) {
      createSolverWidgetFromComponent(component);
    }

    // Now update our widget from the list of components...
    for (NlComponent component : components) {
      updateSolverWidgetFromComponent(component);
    }

    // Finally, layout using our model.
    WidgetContainer root = myWidgetsScene.getRoot();
    if (root != null) {
      root = root.getRootWidgetContainer();
      if (root != null) {
        root.layout();
      }
    }
  }

  /**
   * Look up the given component in the current scene. If the widget is present,
   * we remove it from the list of widgets that are candidate to be removed.
   *
   * @param component the component to look up
   * @param widgets   a list of widgets to remove from the scene
   */
  private void findComponent(@NotNull NlComponent component, @NotNull ArrayList<ConstraintWidget> widgets) {
    ConstraintWidget widget = myWidgetsScene.getWidget(component.getTag());
    if (widget != null) {
      widgets.remove(widget);
    }
    if (component.children != null) {
      for (NlComponent child : component.children) {
        findComponent(child, widgets);
      }
    }
  }

  /**
   * Create the widget associated to a component if necessary.
   *
   * @param component the component we want to represent
   */
  private void createSolverWidgetFromComponent(@NotNull NlComponent component) {
    ConstraintWidget widget = null;
    if (component.getTag() != null) {
      widget = myWidgetsScene.getWidget(component.getTag());
    } else {
      for (ConstraintWidget w : myWidgetsScene.getWidgets()) {
        WidgetCompanion companion = (WidgetCompanion)w.getCompanionWidget();
        NlComponent c = (NlComponent)companion.getWidgetModel();
        if (component == c) {
          widget = w;
          break;
        }
      }
    }
    if (widget == null) {
      if (component.getTagName().equalsIgnoreCase(SdkConstants.CONSTRAINT_LAYOUT)) {
        widget = new ConstraintWidgetContainer();
      }
      else if (component.getTagName().equalsIgnoreCase(SdkConstants.CONSTRAINT_LAYOUT_GUIDELINE)) {
        widget = new Guideline();
        ((Guideline)widget).setOrientation(Guideline.VERTICAL);
      }
      else {
        if (component.children != null && component.children.size() > 0) {
          widget = new WidgetContainer();
        } else {
          widget = new ConstraintWidget();
        }
      }
      WidgetDecorator blueprintDecorator = createDecorator(component, widget);
      WidgetDecorator androidDecorator = createDecorator(component, widget);
      blueprintDecorator.setStyle(WidgetDecorator.BLUEPRINT_STYLE);
      androidDecorator.setStyle(WidgetDecorator.ANDROID_STYLE);
      WidgetCompanion companion = new WidgetCompanion();
      companion.addDecorator(blueprintDecorator);
      companion.addDecorator(androidDecorator);
      companion.setWidgetInteractionTargets(new WidgetInteractionTargets(widget));
      companion.setWidgetModel(component);

      widget.setCompanionWidget(companion);
      widget.setDebugName(component.getId());
      myWidgetsScene.setWidget(component.getTag(), widget);
    }

    for (NlComponent child : component.getChildren()) {
      createSolverWidgetFromComponent(child);
    }
  }

  /**
   * Return a new instance of WidgetDecorator given a component and its ConstraintWidget
   * @param component the component we want to decorate
   * @param widget the ConstraintWidget associated
   * @return a new WidgetDecorator instance
   */
  private WidgetDecorator createDecorator(NlComponent component, ConstraintWidget widget) {
    WidgetDecorator decorator = null;
    if (component.getTagName().equalsIgnoreCase(SdkConstants.TEXT_VIEW)) {
      String text = component.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_TEXT);
      decorator = new TextWidget(widget, text);
    } else if (component.getTagName().equalsIgnoreCase(SdkConstants.BUTTON)) {
      String text = component.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_TEXT);
      decorator = new ButtonWidget(widget, text);
    } else if (component.getTagName().equalsIgnoreCase(SdkConstants.RADIO_BUTTON)) {
      String text = component.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_TEXT);
      decorator = new RadiobuttonWidget(widget, text);
    } else if (component.getTagName().equalsIgnoreCase(SdkConstants.CHECK_BOX)) {
      String text = component.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_TEXT);
      decorator = new CheckboxWidget(widget, text);
    } else if (component.getTagName().equalsIgnoreCase(SdkConstants.SWITCH)) {
      String text = component.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_TEXT);
      decorator = new SwitchWidget(widget, text);
    }
    if (decorator == null) {
      decorator = new WidgetDecorator(widget);
    }
    return decorator;
  }

  /**
   * Update the widget associated to a component with the current component attributes.
   *
   * @param component the component we want to update from
   */
  private void updateSolverWidgetFromComponent(@NotNull NlComponent component) {
    ConstraintWidget widget = myWidgetsScene.getWidget(component.getTag());
    ConstraintUtilities.updateWidget(this, widget, component);
    for (NlComponent child : component.getChildren()) {
      updateSolverWidgetFromComponent(child);
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  // Interaction
  //////////////////////////////////////////////////////////////////////////////

  /**
   * Handles mouse press in the user interaction with our model
   *
   * @param x x mouse coordinate
   * @param y y mouse coordinate
   */
  public void mousePressed(@AndroidCoordinate int x, @AndroidCoordinate int y) {
    if (myMouseInteraction != null) {
      myMouseInteraction.mousePressed(pxToDp(x), pxToDp(y), false);
    }
    Animator.setAnimationEnabled(true);
  }

  /**
   * Handles mouse drag in the user interaction with our model
   *
   * @param x x mouse coordinate
   * @param y y mouse coordinate
   */
  public void mouseDragged(@AndroidCoordinate int x, @AndroidCoordinate int y) {
    if (myMouseInteraction != null) {
      myMouseInteraction.mouseDragged(pxToDp(x), pxToDp(y));
    }
  }

  /**
   * Handles mouse release in the user interaction with our model
   *
   * @param x x mouse coordinate
   * @param y y mouse coordinate
   */
  public void mouseReleased(@AndroidCoordinate int x, @AndroidCoordinate int y) {
    if (myMouseInteraction != null) {
      myMouseInteraction.mouseReleased(pxToDp(x), pxToDp(y));
    }
    Animator.setAnimationEnabled(true);
  }

  /**
   * Handles mouse move interactions
   *
   * @param x x mouse coordinate
   * @param y y mouse coordinate
   * @return true if we need to repaint
   */
  public boolean mouseMoved(@AndroidCoordinate int x, @AndroidCoordinate int y) {
    if (myMouseInteraction != null) {
      myMouseInteraction.mouseMoved(pxToDp(x), pxToDp(y));
    }
    if (mySceneDraw.getCurrentUnderneathAnchor() != null) {
      return true;
    }
    return false;
  }

  /**
   * Update the key modifiers mask
   *
   * @param modifiers new mask
   */
  public void updateModifiers(int modifiers) {
    myMouseInteraction.setIsControlDown((modifiers & InputEvent.CTRL_DOWN_MASK) != 0);
    myMouseInteraction.setIsShiftDown((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0);
    myMouseInteraction.setIsAltDown((modifiers & InputEvent.ALT_DOWN_MASK) != 0);
  }

  public void setAutoConnect(boolean autoConnect) {
    if (autoConnect != mAutoConnect) {
      mAutoConnect = autoConnect;
      myMouseInteraction.setAutoConnect(autoConnect);
    }
  }

  public void toggleAutoConnect() {
    setAutoConnect(!mAutoConnect);
  }

  public boolean isAutoConnect() { return mAutoConnect; }

  //////////////////////////////////////////////////////////////////////////////
  // Painting
  //////////////////////////////////////////////////////////////////////////////

  /**
   * Simple helper class to avoid reallocation
   */
  class RepaintSurface implements SceneDraw.Repaintable {
    ScreenView myScreenView;
    @Override
    public void repaint() {
      if (myScreenView != null) {
        myScreenView.getSurface().repaint();
      }
    }
  }

  /**
   * Paint ourselves and our children
   *
   * @param gc                  the graphic context
   * @param screenView
   *@param width              width of the canvas
   * @param height             height of the canvas
   * @param showAllConstraints flag to show or not all the existing constraints
   * @param isAndroidSurface   android surface (layoutlib)     @return true if we need to repaint
   */
  public boolean paint(@NotNull Graphics2D gc,
                       ScreenView screenView,
                       int width,
                       int height,
                       boolean showAllConstraints,
                       boolean isAndroidSurface) {
    Graphics2D g = (Graphics2D) gc.create();
    WidgetDecorator.setShowFakeUI(mShowFakeUI);
    if (isAndroidSurface) {
      mySceneDraw.setColorSet(mAndroidColorSet);
      mySceneDraw.setCurrentStyle(WidgetDecorator.ANDROID_STYLE);
    } else {
      mySceneDraw.setColorSet(mBlueprintColorSet);
      mySceneDraw.setCurrentStyle(WidgetDecorator.BLUEPRINT_STYLE);
    }
    myRepaintSurface.myScreenView = screenView;
    boolean ret = mySceneDraw.paintWidgets(width, height, myViewTransform, g, showAllConstraints, myMouseInteraction);
    g.dispose();
    return ret;
  }

  //////////////////////////////////////////////////////////////////////////////
  // Utilities
  //////////////////////////////////////////////////////////////////////////////

  /**
   * Transform android pixels into Dp
   *
   * @param px android pixels
   * @return Dp values
   */
  public int pxToDp(@AndroidCoordinate int px) {
    return (int)(px / myDpiFactor);
  }

  /**
   * Use the dpi value to set the dpi and dpi factor
   *
   * @param dpiValue the current dpi
   */
  public void setDpiValue(int dpiValue) {
    myDpi = dpiValue;
    myDpiFactor = dpiValue / (float)DEFAULT_DENSITY;
  }

  /**
   * Mark the given component as being selected
   *
   * @param component
   */
  public void selectComponent(@NotNull NlComponent component) {
    // TODO: move to NlModel's selection system
    ConstraintWidget widget = myWidgetsScene.getWidget(component.getTag());
    if (widget != null && !widget.isRoot()) {
      mySelection.add(widget);
    }
  }
}
