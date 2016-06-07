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

import android.support.constraint.solver.widgets.*;
import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.model.Insets;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.sherpa.drawing.ViewTransform;
import com.android.tools.sherpa.drawing.decorator.*;
import com.android.tools.sherpa.drawing.decorator.WidgetDecorator.StateModel;
import com.android.tools.sherpa.interaction.SnapCandidate;
import com.android.tools.sherpa.interaction.WidgetInteractionTargets;
import com.android.tools.sherpa.structure.Selection;
import com.android.tools.sherpa.structure.WidgetCompanion;
import com.android.tools.sherpa.structure.WidgetsScene;
import com.google.common.collect.Maps;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.WeakHashMap;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.android.SdkConstants.*;

/**
 * Maintains a constraint model shadowing the current NlModel
 * and handles the user interactions on it
 */
public class ConstraintModel implements ModelListener, SelectionListener, Selection.SelectionListener, StateModel {

  public static final int DEFAULT_DENSITY = 160;
  private static final boolean DEBUG = false;
  private static final boolean USE_GUIDELINES_DURING_DND = true;

  private WidgetsScene myWidgetsScene = new WidgetsScene();
  private Selection mySelection = new Selection(null);
  private static boolean ourAutoConnect;
  static {
    ourAutoConnect = PropertiesComponent.getInstance().getBoolean(ConstraintLayoutHandler.AUTO_CONNECT_PREF_KEY, true);
  }

  private float myDpiFactor;
  private int myNeedsAnimateConstraints = -1;
  private final NlModel myNlModel;
  private boolean myAllowsUpdate = true;
  private ConstraintWidget myDragDropWidget;
  private ArrayList<DrawConstraintModel> myDrawConstraintModels = new ArrayList<>();

  public static void setAutoConnect(boolean autoConnect) {
    if (autoConnect != ourAutoConnect) {
      ourAutoConnect = autoConnect;
      PropertiesComponent.getInstance().setValue(ConstraintLayoutHandler.AUTO_CONNECT_PREF_KEY, autoConnect, true);
    }
  }

  public static boolean isAutoConnect() {
    return ourAutoConnect;
  }

  private static Lock ourLock = new ReentrantLock();

  private long myModificationCount = -1;

  private static final WeakHashMap<ScreenView, DrawConstraintModel> ourDrawModelCache = new WeakHashMap<>();
  private static final WeakHashMap<NlModel, ConstraintModel> ourModelCache = new WeakHashMap<>();

  private SaveXMLTimer mySaveXmlTimer = new SaveXMLTimer();
  private RequestRenderTimer myRequestRenderTimer = new RequestRenderTimer();

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
   * Transform android Dp into android pixels
   *
   * @param dp Dp value
   * @return android pixels
   */
  public int dpToPx(@AndroidDpCoordinate int dp) {
    return (int)(dp * myDpiFactor);
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
    if (drawConstraintModel == null && constraintModel != null) {
      drawConstraintModel = new DrawConstraintModel(screenView, constraintModel);
      ourDrawModelCache.put(screenView, drawConstraintModel);
      constraintModel.myDrawConstraintModels.add(drawConstraintModel);
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
      constraintModel.modelChanged(model);
    }
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
    SwingUtilities.invokeLater(() -> {
      ourLock.lock();
      if (DEBUG) {
        System.out.println("*** Model Changed " + model.getResourceVersion()
                           + " vs our " + myModificationCount);
      }
      if (model.getResourceVersion() > myModificationCount) {
        if (myAllowsUpdate) {
          int dpi = model.getConfiguration().getDensity().getDpiValue();
          setDpiValue(dpi);
          updateNlModel(model.getComponents(), true);
        }
        myModificationCount = model.getResourceVersion();
        if (DEBUG) {
          System.out.println("-> updated [" + myAllowsUpdate + "] to " + myModificationCount);
        }
        for (DrawConstraintModel drawConstraintModel : getDrawConstraintModels()) {
          drawConstraintModel.repaint();
        }
      }
      else {
        if (DEBUG) {
          System.out.println("-> no update");
        }
      }
      ourLock.unlock();
    });
  }

  @Override
  public void modelRendered(@NotNull NlModel model) {
    SwingUtilities.invokeLater(() -> {
      if (DEBUG) {
        ourLock.lock();
        System.out.println("Model rendered " + model.getResourceVersion()
                           + " vs our " + myModificationCount);
        ourLock.unlock();
      }
      ourLock.lock();
      updateNlModel(model.getComponents(), false);
      ourLock.unlock();
      mySaveXmlTimer.delay();
    });
  }

  /**
   * Allows update to the model to come in or not
   *
   * @param value
   */
  public void allowsUpdate(boolean value) {
    ourLock.lock();
    myAllowsUpdate = value;
    ourLock.unlock();
    if (value) {
      mySaveXmlTimer.reset();
    }
    else {
      mySaveXmlTimer.cancel();
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  // Drag and Drop handling
  //////////////////////////////////////////////////////////////////////////////

  /**
   * Accessor to the list of DrawConstraintModel representing this ConstraintModel
   *
   * @return the list of DrawConstraintModel associated to this ConstraintModel
   */
  public ArrayList<DrawConstraintModel> getDrawConstraintModels() {
    return myDrawConstraintModels;
  }

  /**
   * Set a drop widget
   *
   * @param droppedWidget
   */
  public void setDragDropWidget(ConstraintWidget droppedWidget) {
    myDragDropWidget = droppedWidget;
  }

  /**
   * Getter for the drop widget
   */
  public ConstraintWidget getDragDropWidget() {
    return myDragDropWidget;
  }

  /**
   * Remove a drop widget
   */
  public void removeDragComponent() {
    if (myDragDropWidget != null) {
      myWidgetsScene.removeWidget(myDragDropWidget);
      myDragDropWidget = null;
    }
  }

  /**
   * Commit the drop widget
   *
   * We remove it from the scene and set its companion object to point to the newly created NlComponent.
   *
   * @param component the dropped NlComponent
   */
  public void commitDragComponent(NlComponent component) {
    if (USE_GUIDELINES_DURING_DND) {
      if (myDragDropWidget != null) {
        myWidgetsScene.removeWidget(myDragDropWidget);
        WidgetCompanion companion = (WidgetCompanion)myDragDropWidget.getCompanionWidget();
        companion.setWidgetModel(component);
        companion.setWidgetTag(component);
      }
    }
    else {
      removeDragComponent();
    }
  }

  /**
   * Do a pass at enabling connections from the existing snap candidates (used for the drag and drop),
   * if auto-connect is on.
   */
  private void connectDroppedWidget() {
    if (!ourAutoConnect) {
      // Clear the indicators
      ArrayList<DrawConstraintModel> drawConstraintModels = getDrawConstraintModels();
      if (drawConstraintModels.size() < 1) {
        return;
      }
      for (DrawConstraintModel drawConstraintModel : drawConstraintModels) {
        drawConstraintModel.getMouseInteraction().clearIndicators();
      }
      return;
    }
    ArrayList<DrawConstraintModel> drawConstraintModels = getDrawConstraintModels();
    if (drawConstraintModels.size() < 1) {
      return;
    }
    for (DrawConstraintModel drawConstraintModel : drawConstraintModels) {
      // Start the autoconnection
      for (SnapCandidate candidate : drawConstraintModel.getMouseInteraction().getSnapCandidates()) {
        int margin = candidate.margin;
        if (candidate.padding != 0) {
          margin = candidate.padding;
        }
        margin = Math.abs(margin);
        ConstraintWidget widget = candidate.source.getOwner();
        widget.connect(candidate.source, candidate.target, margin,
                       ConstraintAnchor.AUTO_CONSTRAINT_CREATOR);
      }
      drawConstraintModel.getMouseInteraction().clearIndicators();
    }
    saveToXML(true);
  }

  /**
   * Drag the current drag'n drop widget
   *
   * @param x
   * @param y
   */
  public void dragComponent(@AndroidCoordinate int x, @AndroidCoordinate int y) {
    if (myDragDropWidget == null) {
      return;
    }
    int ax = pxToDp(x);
    int ay = pxToDp(y);
    myDragDropWidget.setX(ax);
    myDragDropWidget.setY(ay);
    myDragDropWidget.forceUpdateDrawPosition();
    for (DrawConstraintModel drawConstraintModel : getDrawConstraintModels()) {
      drawConstraintModel.getMouseInteraction().dragAndDrop(myDragDropWidget, ax, ay);
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  // Selection handling
  //////////////////////////////////////////////////////////////////////////////

  /**
   * Something has changed on the selection of NlModel
   *
   * @param model     the NlModel selection model
   * @param selection the list of selected NlComponents
   */
  @Override
  public void selectionChanged(@NotNull SelectionModel model, @NotNull List<NlComponent> selection) {
    if (selection.isEmpty()) {
      return;
    }
    boolean different = selection.size() != mySelection.size();
    if (!different) {
      for (NlComponent component : model.getSelection()) {
        if (!mySelection.contains(myWidgetsScene.getWidget(component))) {
          different = true;
          break;
        }
      }
    }
    if (!different) {
      return;
    }
    mySelection.silentClear();
    for (NlComponent component : model.getSelection()) {
      ConstraintWidget widget = myWidgetsScene.getWidget(component);
      if (widget != null && !widget.isRoot() && !widget.isRootContainer()) {
        mySelection.silentAdd(widget);
      }
    }
  }

  /**
   * Something has changed in our selection
   *
   * @param selection our internal selection object
   */
  @Override
  public void onSelectionChanged(Selection selection) {
    SelectionModel selectionModel = myNlModel.getSelectionModel();
    if (selection.isEmpty()) {
      selectionModel.clear();
      return;
    }
    List<NlComponent> components = new ArrayList<>();
    for (Selection.Element selectedElement : mySelection.getElements()) {
      if (selectedElement.widget == myDragDropWidget) {
        continue;
      }
      WidgetCompanion companion = (WidgetCompanion)selectedElement.widget.getCompanionWidget();
      NlComponent component = (NlComponent)companion.getWidgetModel();
      components.add(component);
    }
    if (!components.isEmpty()) {
      selectionModel.setSelection(components);
    } else {
      selectionModel.clear();
    }
  }

  @Override
  public void save(WidgetDecorator decorator) {
    saveToXML(true);
  }

  /**
   * Schedule an XML save
   */
  public void requestSaveToXML() {
    mySaveXmlTimer.reset();
  }

  /**
   * Schedule a render
   */
  public void requestRender() {
    ourLock.lock();
    if (myAllowsUpdate) {
      myRequestRenderTimer.reset();
    };
    ourLock.unlock();
  }

  /**
   * Timer class managing the xml save behaviour
   * We only want to save if we are not doing something else...
   */
  class SaveXMLTimer implements ActionListener {
    Timer myTimer = new Timer(800, this); // 800ms delay before saving

    public SaveXMLTimer() {
      myTimer.setRepeats(false);
    }

    public void delay() {
      if (myTimer.isRunning()) {
        reset();
      }
    }

    public void reset() {
      if (DEBUG) {
        System.out.println("reset timer");
      }
      ourLock.lock();
      boolean allowsUpdate = myAllowsUpdate;
      ourLock.unlock();
      if (allowsUpdate) {
        myTimer.restart();
      }
    }

    public void cancel() {
      if (DEBUG) {
        System.out.println("cancel timer");
      }
      myTimer.stop();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      ourLock.lock();
      boolean allowsUpdate = myAllowsUpdate;
      ourLock.unlock();
      if (allowsUpdate) {
        if (DEBUG) {
          System.out.println("save xml");
        }
        saveToXML(true);
      }
    }
  }

  /**
   * Timer class managing the request render behaviour
   * We don't want to queue rendering continuously.
   */
  class RequestRenderTimer implements ActionListener {
    Timer mTimer = new Timer(400, this); // 400ms delay before render

    public RequestRenderTimer() {
      mTimer.setRepeats(false);
    }

    public void reset() {
      mTimer.restart();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      renderInLayoutLib();
    }
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
    model.getSelectionModel().addListener(this);
    mySelection.addSelectionListener(this);
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
   * @param deepUpdate do a thorough update or not
   */
  private void updateNlModel(@NotNull List<NlComponent> components, boolean deepUpdate) {
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
      updateSolverWidgetFromComponent(component, deepUpdate);
    }

    if (USE_GUIDELINES_DURING_DND) {
      // Sanity check to remove the drop widget if it has been committed already but not used.
      if (myDragDropWidget != null
          && !myWidgetsScene.getWidgets().contains(myDragDropWidget)) {
        myDragDropWidget = null;
      }
    }

    // Update the ConstraintLayout instances
    updateConstraintLayoutRoots(myWidgetsScene.getRoot());

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
    ConstraintWidget widget = myWidgetsScene.getWidget(component);
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
    ConstraintWidget widget = myWidgetsScene.getWidget(component);
    if (widget != null && isConstraintLayout(component)) {
      if (!(widget instanceof ConstraintWidgetContainer)) {
        if (widget instanceof WidgetContainer) {
          ConstraintWidgetContainer container = new ConstraintWidgetContainer();
          myWidgetsScene.transformContainerToContainer((WidgetContainer)widget, container);
          setupConstraintWidget(component, container);
          widget = container;
        }
        else {
          myWidgetsScene.removeWidget(widget);
          widget = null;
        }
      }
    }
    if (widget == null) {
      boolean dropWidget = false;
      if (USE_GUIDELINES_DURING_DND) {
        if (myDragDropWidget != null) {
          WidgetCompanion companion = (WidgetCompanion)myDragDropWidget.getCompanionWidget();
          if (companion.getWidgetModel() == component) {
            widget = myDragDropWidget;
            widget.setCompanionWidget(null);
            dropWidget = true;
          }
        }
      }
      if (widget == null) {
        if (isConstraintLayout(component)) {
          widget = new ConstraintWidgetContainer();
        }
        else if (component.getTagName().equalsIgnoreCase(SdkConstants.CONSTRAINT_LAYOUT_GUIDELINE)) {
          widget = new Guideline();
        }
        else {
          if (component.children != null && component.children.size() > 0) {
            widget = new WidgetContainer();
          }
          else {
            widget = new ConstraintWidget();
          }
        }
      }
      setupConstraintWidget(component, widget);
      myWidgetsScene.setWidget(widget);
      if (USE_GUIDELINES_DURING_DND) {
        if (dropWidget) {
          connectDroppedWidget();
          myDragDropWidget = null;
          mySelection.add(widget);
        }
      }
    }

    for (NlComponent child : component.getChildren()) {
      createSolverWidgetFromComponent(child);
    }
  }

  private static boolean isConstraintLayout(@NotNull NlComponent component) {
    return component.isOrHasSuperclass(SdkConstants.CONSTRAINT_LAYOUT);
  }

  /**
   * Set up a ConstraintWidget from a component
   *
   * @param component
   * @param widget
   */
  private void setupConstraintWidget(@NotNull NlComponent component, ConstraintWidget widget) {
    WidgetDecorator blueprintDecorator = createDecorator(component, widget);
    WidgetDecorator androidDecorator = createDecorator(component, widget);
    blueprintDecorator.setStateModel(this);
    androidDecorator.setStateModel(this);
    blueprintDecorator.setStyle(WidgetDecorator.BLUEPRINT_STYLE);
    androidDecorator.setStyle(WidgetDecorator.ANDROID_STYLE);
    WidgetCompanion companion = new WidgetCompanion();
    companion.addDecorator(blueprintDecorator);
    companion.addDecorator(androidDecorator);
    companion.setWidgetInteractionTargets(new WidgetInteractionTargets(widget));
    companion.setWidgetModel(component);
    companion.setWidgetTag(component);
    widget.setCompanionWidget(companion);
    widget.setDebugName(component.getId());
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
    else if (component.getTagName().equalsIgnoreCase(SdkConstants.IMAGE_VIEW)) {
      decorator = new ImageViewWidget(widget);
    }
    else if (component.getTagName().equalsIgnoreCase(SdkConstants.WEB_VIEW)) {
      decorator = new WebViewWidget(widget);
    }
    else if (component.getTagName().equalsIgnoreCase(SdkConstants.EDIT_TEXT)) {
      decorator = new TextWidget(widget, ConstraintUtilities.getResolvedText(component));
    }
    if (decorator == null) {
      decorator = new WidgetDecorator(widget);
    }
    return decorator;
  }

  /**
   * Update the widget associated to a component with the current component attributes.
   *
   * @param component  the component we want to update from
   * @param deepUpdate do a thorough update or not
   */
  private void updateSolverWidgetFromComponent(@NotNull NlComponent component, boolean deepUpdate) {
    ConstraintWidget widget = myWidgetsScene.getWidget(component);
    if (USE_GUIDELINES_DURING_DND) {
      if (myDragDropWidget != null) {
        // If we have a drag and drop widget candidate, let's not update from the NlComponent just yet
        WidgetCompanion companion = (WidgetCompanion)myDragDropWidget.getCompanionWidget();
        if (companion.getWidgetModel() == component) {
          saveToXML(true); // will retrigger an update
          myDragDropWidget = null;
          return;
        }
      }
    }
    ConstraintUtilities.updateWidget(this, widget, component, deepUpdate);
    for (NlComponent child : component.getChildren()) {
      updateSolverWidgetFromComponent(child, deepUpdate);
    }
  }

  /**
   * Traverse the hierarchy to find all ConstraintLayout instances
   * and update them. We set all the wrap_content sizes of the ConstraintLayout children
   * from layout lib
   *
   * @param container
   */
  private void updateConstraintLayoutRoots(WidgetContainer container) {
    if (container == null) {
      return;
    }
    Map<NlComponent, Dimension> wrapContentSizes = Maps.newHashMap();
    if (container.isRoot() || container.isRootContainer()) {
      NlComponent component = (NlComponent)((WidgetCompanion)container.getCompanionWidget()).getWidgetModel();
      Insets padding = component.getPadding(true);
      container.setDimension(pxToDp(component.w - padding.width()),
                          pxToDp(component.h - padding.height()));
      int x = pxToDp(component.x);
      int y = pxToDp(component.y);
      x += pxToDp(padding.left);
      y += pxToDp(padding.top);
      WidgetContainer parentContainer = (WidgetContainer)container.getParent();
      if (parentContainer != null) {
        x -= parentContainer.getDrawX();
        y -= parentContainer.getDrawY();
        if (!(parentContainer instanceof ConstraintWidgetContainer)) {
          container.setDimension(pxToDp(component.w),
                                 pxToDp(component.h));
        }
      }
      if (container.getX() != x || container.getY() != y) {
        container.setOrigin(x, y);
        container.forceUpdateDrawPosition();
      }
    }
    if (!(container instanceof ConstraintWidgetContainer)) {
      NlComponent component = (NlComponent)((WidgetCompanion)container.getCompanionWidget()).getWidgetModel();
      container.setDimension(pxToDp(component.w),
                             pxToDp(component.h));
    }
    if (container instanceof ConstraintWidgetContainer && container.getChildren().size() > 0) {
      NlComponent root = (NlComponent)((WidgetCompanion)container.getCompanionWidget()).getWidgetModel();
      XmlTag parentTag = root.getTag();
      if (parentTag.isValid()) {
        Map<XmlTag, NlComponent> tagToComponent = Maps.newHashMapWithExpectedSize(root.getChildCount());
        for (NlComponent child : root.getChildren()) {
          tagToComponent.put(child.getTag(), child);
        }
        XmlFile xmlFile = myNlModel.getFile();
        AndroidFacet facet = myNlModel.getFacet();
        RenderService renderService = RenderService.get(facet);
        RenderLogger logger = renderService.createLogger();
        final RenderTask task = renderService.createTask(xmlFile, myNlModel.getConfiguration(), logger, null);
        if (task != null) {
          // Measure wrap_content bounds
          Map<XmlTag, ViewInfo> map = task.measureChildren(parentTag, new RenderTask.AttributeFilter() {
            @Override
            public String getAttribute(@NotNull XmlTag n, @Nullable String namespace, @NotNull String localName) {
              // Change attributes to wrap_content
              if (ATTR_LAYOUT_WIDTH.equals(localName) && ANDROID_URI.equals(namespace)) {
                return VALUE_WRAP_CONTENT;
              }
              if (ATTR_LAYOUT_HEIGHT.equals(localName) && ANDROID_URI.equals(namespace)) {
                return VALUE_WRAP_CONTENT;
              }
              return null;
            }
          });
          task.dispose();
          if (map != null) {
            for (Map.Entry<XmlTag, ViewInfo> entry : map.entrySet()) {
              ViewInfo viewInfo = entry.getValue();
              viewInfo = RenderService.getSafeBounds(viewInfo);
              Dimension size = new Dimension(viewInfo.getRight() - viewInfo.getLeft(), viewInfo.getBottom() - viewInfo.getTop());
              NlComponent child = tagToComponent.get(entry.getKey());
              if (child != null) {
                wrapContentSizes.put(child, size);
              }
            }
          }
        }
      }
    }
    for (ConstraintWidget child : container.getChildren()) {
      NlComponent component = (NlComponent)((WidgetCompanion)child.getCompanionWidget()).getWidgetModel();
      Dimension dimension = wrapContentSizes.get(component);
      if (dimension != null) {
        child.setWrapWidth(pxToDp((int)dimension.getWidth()));
        child.setWrapHeight(pxToDp((int) dimension.getHeight()));
      }
      if (child instanceof WidgetContainer) {
        updateConstraintLayoutRoots((WidgetContainer)child);
      }
    }
  }

  /**
   * Always save the model to xml
   */
  public void saveToXML(boolean forceSave) {
    Selection selection = getSelection();
    if (forceSave || !selection.getModifiedWidgets().isEmpty()) {
      ourLock.lock();
      myModificationCount++;
      ourLock.unlock();
      if (DEBUG) {
        System.out.println("Model Saved to XML -> " + myModificationCount
                           + "(" + selection.getModifiedWidgets().size()
                           + " elements modified)");
      }
      ConstraintUtilities.saveModelToXML(myNlModel);
      selection.clearModifiedWidgets();
      requestRender();
    }
  }

  /**
   * Render the model in layoutlib
   */
  private void renderInLayoutLib() {
    ourLock.lock();
    if (DEBUG) {
      System.out.println("### Model rendered to layoutlib -> "
                         + myModificationCount + " vs "
                         + myNlModel.getResourceVersion());
    }
    ourLock.unlock();

    ConstraintUtilities.renderModel(this);
  }

  public void setNeedsAnimateConstraints(int type) {
    myNeedsAnimateConstraints = type;
  }

  public int getNeedsAnimateConstraints() {
    return myNeedsAnimateConstraints;
  }

  public NlModel getNlModel() {
    return myNlModel;
  }
}
