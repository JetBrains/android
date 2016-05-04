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
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.sherpa.drawing.*;
import com.android.tools.sherpa.drawing.decorator.*;
import com.android.tools.sherpa.interaction.WidgetInteractionTargets;
import com.android.tools.sherpa.structure.WidgetCompanion;
import com.intellij.util.containers.WeakHashMap;
import org.jetbrains.annotations.NotNull;
import com.android.tools.sherpa.structure.Selection;
import com.android.tools.sherpa.structure.WidgetsScene;
import com.google.tnt.solver.widgets.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Maintains a constraint model shadowing the current NlModel
 * and handles the user interactions on it
 */
public class ConstraintModel implements ModelListener {

  public static final int DEFAULT_DENSITY = 160;

  private WidgetsScene myWidgetsScene = new WidgetsScene();
  private Selection mySelection = new Selection(null);
  private boolean mAutoConnect = true;
  private float myDpiFactor;
  private int mNeedsAnimateConstraints = -1;
  private final NlModel myNlModel;
  private boolean mAllowsUpdate = true;

  public void setAutoConnect(boolean autoConnect) {
    if (autoConnect != mAutoConnect) {
      mAutoConnect = autoConnect;
    }
  }

  public boolean isAutoConnect() {
    return mAutoConnect;
  }

  private static Lock ourLock = new ReentrantLock();

  private long myModificationCount = -1;

  private static final WeakHashMap<ScreenView, DrawConstraintModel> ourDrawModelCache = new WeakHashMap<>();
  private static final WeakHashMap<NlModel, ConstraintModel> ourModelCache = new WeakHashMap<>();

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
    myDpiFactor = dpiValue / (float)DEFAULT_DENSITY;
  }

  //////////////////////////////////////////////////////////////////////////////
  // Static functions
  //////////////////////////////////////////////////////////////////////////////

  /**
   * Get the associated DrawConstraintModel for this ScreenView
   *
   * @param screenView
   */
  public static DrawConstraintModel getDrawConstraintModel(ScreenView screenView) {
    ConstraintModel constraintModel = getConstraintModel(screenView.getModel());
    ourLock.lock();
    DrawConstraintModel drawConstraintModel = ourDrawModelCache.get(screenView);
    if (drawConstraintModel == null) {
      if (constraintModel != null) {
        drawConstraintModel = new DrawConstraintModel(screenView, constraintModel);
        ourDrawModelCache.put(screenView, drawConstraintModel);
      }
    }
    if (drawConstraintModel != null) {
      int dpi = screenView.getConfiguration().getDensity().getDpiValue();
      drawConstraintModel.getConstraintModel().setDpiValue(dpi);
      float dpiFactor = dpi / (float)DEFAULT_DENSITY;
      ViewTransform transform = drawConstraintModel.getViewTransform();
      transform.setScale((float)(screenView.getScale() * dpiFactor));
      int swingX = screenView.getX();
      int swingY = screenView.getY();
      transform.setTranslate(swingX, swingY);
    }
    ourLock.unlock();
    return drawConstraintModel;
  }

  /**
   * Get the associated ConstraintModel instance for this model
   *
   * @param model
   * @return
   */
  public static ConstraintModel getConstraintModel(NlModel model) {
    ourLock.lock();
    ConstraintModel constraintModel = ourModelCache.get(model);
    if (constraintModel == null) {
      constraintModel = new ConstraintModel(model);
      model.addListener(constraintModel);
      ourModelCache.put(model, constraintModel);
    }
    constraintModel.modelChanged(model);
    ourLock.unlock();
    return constraintModel;
  }

  /**
   * If the model changed, let's update...
   *
   * @param model
   */
  @Override
  public void modelChanged(@NotNull NlModel model) {
    ourLock.lock();
    if (model.getModificationCount() > myModificationCount) {
      if (mAllowsUpdate) {
        int dpi = model.getConfiguration().getDensity().getDpiValue();
        setDpiValue(dpi);
        updateNlModel(model.getComponents());
      }
      myModificationCount = model.getModificationCount();
    }
    ourLock.unlock();
  }

  @Override
  public void modelRendered(@NotNull NlModel model) {
    // Do nothing here
  }

  /**
   * Allows update to the model to come in or not
   * @param value
   */
  public void allowsUpdate(boolean value) {
    ourLock.lock();
    mAllowsUpdate = value;
    ourLock.unlock();
  }

  //////////////////////////////////////////////////////////////////////////////
  // Constructor and accessors
  //////////////////////////////////////////////////////////////////////////////

  /**
   * Base constructor
   */
  public ConstraintModel(NlModel model) {
    myNlModel = model;
    mySelection.setSelectedAnchor(null);
    myWidgetsScene.setSelection(mySelection);
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
    ArrayList<ConstraintWidget> widgets = new ArrayList<>(myWidgetsScene.getWidgets());
    if (widgets.size() > 0) {
      // Let's check for widgets to remove
      for (NlComponent component : components) {
        findComponent(component, widgets);
      }

      // After the above loop, the widgets array will only contains widget that
      // are not in the list of components, so we should remove them
      if (widgets.size() > 0) {
        for (ConstraintWidget widget : widgets) {
          myWidgetsScene.removeWidget(widget);
        }
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
    ConstraintWidget widget = myWidgetsScene.getWidget(component.getTag());
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
        }
        else {
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
      companion.setWidgetTag(component.getTag());
      widget.setCompanionWidget(companion);
      widget.setDebugName(component.getId());
      myWidgetsScene.setWidget(widget);
    }

    for (NlComponent child : component.getChildren()) {
      createSolverWidgetFromComponent(child);
    }
  }

  /**
   * Return a new instance of WidgetDecorator given a component and its ConstraintWidget
   *
   * @param component the component we want to decorate
   * @param widget    the ConstraintWidget associated
   * @return a new WidgetDecorator instance
   */
  private static WidgetDecorator createDecorator(NlComponent component, ConstraintWidget widget) {
    WidgetDecorator decorator = null;
    if (component.getTagName().equalsIgnoreCase(SdkConstants.TEXT_VIEW)) {
      decorator = new TextWidget(widget, ConstraintUtilities.getResolvedText(component));
    }
    else if (component.getTagName().equalsIgnoreCase(SdkConstants.BUTTON)) {
      decorator = new ButtonWidget(widget, ConstraintUtilities.getResolvedText(component));
    }
    else if (component.getTagName().equalsIgnoreCase(SdkConstants.RADIO_BUTTON)) {
      decorator = new RadiobuttonWidget(widget, ConstraintUtilities.getResolvedText(component));
    }
    else if (component.getTagName().equalsIgnoreCase(SdkConstants.CHECK_BOX)) {
      decorator = new CheckboxWidget(widget, ConstraintUtilities.getResolvedText(component));
    }
    else if (component.getTagName().equalsIgnoreCase(SdkConstants.SWITCH)) {
      decorator = new SwitchWidget(widget, ConstraintUtilities.getResolvedText(component));
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

  /**
   * Save the model to xml
   */
  public void saveToXML() {
    ourLock.lock();
    myModificationCount ++;
    ourLock.unlock();

    ConstraintUtilities.saveModelToXML(myNlModel);
  }

  public void setNeedsAnimateConstraints(int type) {
    mNeedsAnimateConstraints = type;
  }

  public int getNeedsAnimateConstraints() {
    return mNeedsAnimateConstraints;
  }

}
