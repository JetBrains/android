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
package com.android.tools.idea.uibuilder.surface;

import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.idea.uibuilder.actions.TogglePanningDialogAction;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.DesignSurfaceListener;
import com.android.tools.idea.common.surface.PanZoomListener;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.uibuilder.graphics.NlConstants;
import com.android.tools.idea.common.model.ModelListener;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.sherpa.drawing.BlueprintColorSet;
import com.android.tools.sherpa.drawing.ColorSet;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.ui.popup.*;
import com.intellij.ui.AncestorListenerAdapter;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

import static com.android.tools.idea.uibuilder.surface.NlDesignSurface.ScreenMode.*;

/**
 * UI component for Navigator Panel showing a miniature representation of the NlDesignSurface
 * allowing to easily scroll inside the NlDesignSurface when the UI builder is zoomed.
 * The panel can be collapsed and expanded. The default state is collapsed
 */
public class PanZoomPanel extends JPanel
  implements DesignSurfaceListener, PanZoomListener, ModelListener, JBPopupListener {

  public static final String PROP_X_POS = "ANDROID.PANZOOM_X_POS";
  public static final String PROP_Y_POS = "ANDROID.PANZOOM_Y_POS";
  public static final String PROP_OPEN = "ANDROID.PANZOOM_OPENED";
  public static final String TITLE = "Pan and Zoom";
  public static final String HINT = "(Scroll to Zoom)";

  private static final IconButton CANCEL_BUTTON = new IconButton("Close", AllIcons.Actions.Close, AllIcons.Actions.CloseHovered);
  private static final int SCREEN_SPACE = NlConstants.SCREEN_DELTA;
  private static final Dimension PREFERRED_SIZE = new Dimension(250, 216);
  private static final BlueprintColorSet BLUEPRINT_COLOR_SET = new BlueprintColorSet();
  private static final JBColor DRAWING_SURFACE_RECTANGLE_COLOR = JBColor.red;
  private static final JBColor OVERLAY_COLOR = new JBColor(new Color(232, 232, 232, 127), new Color(80, 80, 80, 127));
  private static final JBColor NORMAL_SCREEN_VIEW_COLOR = new JBColor(Gray._255, Gray._240);
  private static final Color BLUEPRINT_SCREEN_VIEW_COLOR = BLUEPRINT_COLOR_SET.getBackground();
  private static final Color COMPONENT_STROKE_COLOR = BLUEPRINT_COLOR_SET.getFrames();
  private static final Color BACKGROUND_COLOR = BLUEPRINT_COLOR_SET.getBackground();

  private final Point myDesignSurfaceOffset;
  private final Point mySecondScreenOffset;
  private final AncestorListenerAdapter myAncestorListener;
  private final Point myCenterOffset;
  private final Point myRelativeLocation = new Point();
  private final ColorSet myColorSet;
  private final MiniMap myMiniMap;
  @Nullable private NlDesignSurface myDesignSurface;
  @Nullable private NlComponent myComponent;
  @Nullable private JBPopup myContainerPopup;
  @Nullable private Dimension myCurrentSceneViewSize;
  @Nullable private Dimension myDesignSurfaceSize;
  @Nullable private Dimension myDeviceSize;
  @Nullable private ComponentListener myApplicationComponentAdapter;
  @Nullable private ComponentListener myPopupComponentAdapter;
  private boolean myIsZoomed;
  private double mySceneViewScale;
  private double myDeviceScale;
  private int myYScreenNumber;
  private int myXScreenNumber;
  private int myScaledScreenSpace;

  public PanZoomPanel(@NotNull NlDesignSurface surface) {
    super(new BorderLayout());
    myDesignSurfaceOffset = new Point();
    mySecondScreenOffset = new Point();
    myCenterOffset = new Point();
    myMiniMap = new MiniMap();
    myAncestorListener = new MyAncestorListenerAdapter();
    myColorSet = BLUEPRINT_COLOR_SET;
    setPreferredSize(PREFERRED_SIZE);
    setOpaque(true);
    setSurface(surface);
    updateComponents(null);

    add(myMiniMap, BorderLayout.CENTER);

    // Listening to mouse event
    MouseInteractionListener listener = new MouseInteractionListener();
    addMouseListener(listener);
    addMouseMotionListener(listener);
    addMouseWheelListener(listener);
    configureUI();
  }

  /**
   * Set up the UI
   */
  public void configureUI() {
    if (myDesignSurface == null) {
      return;
    }
    computeScale(myDesignSurface.getCurrentSceneView(), myDesignSurface.getSize(),
                 myDesignSurface.getContentSize(null));

    if (myDeviceSize != null) {
      computeOffsets(myDeviceSize, myDesignSurface.getCurrentSceneView());
    }
  }

  /**
   * Set the selected component. If no component is selected, it will set the root of the previous selected component as the selected one
   *
   * @param selectedComponents
   */
  public void updateComponents(@Nullable List<NlComponent> selectedComponents) {
    if (selectedComponents != null && !selectedComponents.isEmpty()) {
      myComponent = selectedComponents.get(0);
    }
    else if (myComponent != null) {
      // If not component are selected, displays the full screen
      myComponent = myComponent.getRoot();
    }
    else if (myDesignSurface != null) {
      SceneView currentSceneView = myDesignSurface.getCurrentSceneView();
      if (currentSceneView != null) {
        final List<NlComponent> components = currentSceneView.getModel().getComponents();
        myComponent = !components.isEmpty() ? components.get(0) : null;
      }
    }
    myMiniMap.repaint();
  }

  /* implements DesignSurfaceListener */
  @Override
  public void componentSelectionChanged(@NotNull DesignSurface surface, @NotNull List<NlComponent> selectedComponents) {
    updateComponents(selectedComponents);
    configureUI();
  }

  @Override
  public void sceneChanged(@NotNull DesignSurface surface, @Nullable SceneView sceneView) {
    assert surface instanceof NlDesignSurface;
    setSurface((NlDesignSurface)surface);
    assert myDesignSurface != null;
    computeOffsets(myDeviceSize, myDesignSurface.getCurrentSceneView());
    myMiniMap.repaint();
  }

  /**
   * The model of the design surface changed
   */
  @Override
  public void modelChanged(@NotNull DesignSurface surface, @Nullable NlModel model) {
    assert surface instanceof NlDesignSurface;
    setSurface((NlDesignSurface)surface);
    if (model != null) {
      model.addListener(this);
    }

    // The model change can be triggered by a change of editor, in this case, we want to keep
    // the popup opened but we have to change the content, so we try to find the current selection
    // in the model or at least the component of the current model.
    computeOffsets(myDeviceSize, surface.getCurrentSceneView());
    if (model != null) {
      List<NlComponent> selection = model.getSelectionModel().getSelection();
      if (selection.isEmpty()) {
        selection = model.getComponents();
      }
      updateComponents(selection);
    }
    configureUI();
    myMiniMap.repaint();
  }

  @Override
  public void modelChangedOnLayout(@NotNull NlModel model, boolean animate) {
    // Do nothing
  }

  @Override
  public boolean activatePreferredEditor(@NotNull DesignSurface surface, @NotNull NlComponent component) {
    return false;
  }

  /* implements ModelListener */

  /**
   * A change occurred inside the model object
   */
  @Override
  public void modelDerivedDataChanged(@NotNull NlModel model) {
    if (myDesignSurface != null) {
      updateDeviceConfiguration(myDesignSurface.getConfiguration());
      updateComponents(model.getComponents());
      updateScreenNumber(myDesignSurface);
      myMiniMap.repaint();
    }
  }

  @Override
  public void modelRendered(@NotNull NlModel model) {
  }

  /* Implements PanZoomListener */
  @Override
  public void zoomChanged(DesignSurface designSurface) {
    assert designSurface instanceof NlDesignSurface;
    setSurface((NlDesignSurface)designSurface);
    myMiniMap.repaint();
  }

  @Override
  public void panningChanged(AdjustmentEvent adjustmentEvent) {
    if (myDesignSurface == null) {
      return;
    }
    Point scrollPosition = myDesignSurface.getScrollPosition();
    SceneView currentSceneView = myDesignSurface.getCurrentSceneView();
    if (currentSceneView != null) {
      myDesignSurfaceSize = myDesignSurface.getSize(myDesignSurfaceSize);
      Dimension contentSize = myDesignSurface.getContentSize(null);
      if (myDesignSurfaceSize != null) {
        computeScale(currentSceneView, myDesignSurfaceSize, contentSize);
      }
    }
    myDesignSurfaceOffset.setLocation(
      scrollPosition.getX() * mySceneViewScale,
      scrollPosition.getY() * mySceneViewScale
    );
    repaint();
  }

  /* Implements JBPopupListener */
  @Override
  public void beforeShown(LightweightWindowEvent event) {
    if (myDesignSurface == null) {
      return;
    }
    myContainerPopup = event.asPopup();
    registerPopupWindowComponentAdapter(myContainerPopup, myDesignSurface, myRelativeLocation);
    registerApplicationWindowComponentAdapter(myDesignSurface, myContainerPopup, myRelativeLocation);
    PropertiesComponent.getInstance().setValue(PROP_OPEN, true);
  }

  /**
   * If the close event is cancel, the {@link PanZoomPanel#PROP_OPEN} properties will be set to false
   * to notify that the popup has been closed by the user (either by clicking the cancel button or the
   * {@link TogglePanningDialogAction}).
   *
   * If the event is triggered from {@link JBPopup#closeOk(InputEvent)}, we don't persist the state so the {@link DesignSurface} knows that
   * the {@link PanZoomPanel} should be shown when it is reactivated.
   */
  @Override
  public void onClosed(LightweightWindowEvent event) {
    if (myContainerPopup != null) {
      myContainerPopup.removeListener(this);
      UIUtil.getWindow(myContainerPopup.getContent()).removeComponentListener(myPopupComponentAdapter);
      myContainerPopup = null;
    }
    if (myDesignSurface != null) {
      UIUtil.getWindow(myDesignSurface).removeComponentListener(myApplicationComponentAdapter);
    }
    if (!event.isOk()) {
      PropertiesComponent.getInstance().setValue(PROP_OPEN, false);
    }
  }

  @Override
  public boolean isVisible() {
    return myContainerPopup != null && myContainerPopup.isVisible() && super.isVisible();
  }

  /**
   * JPanel where the miniature are drown
   */
  private class MyAncestorListenerAdapter extends AncestorListenerAdapter {
    @Override
    public void ancestorRemoved(AncestorEvent event) {
      super.ancestorRemoved(event);
      if (myContainerPopup != null) {
        hidePopup();
      }
    }
  }

  /**
   * Set the DesignSurface to display the minimap from
   */
  public void setSurface(@Nullable NlDesignSurface surface) {
    updateScreenNumber(surface);
    if (surface == myDesignSurface) {
      return;
    }

    // Removing all listener for the oldSurface
    if (myDesignSurface != null) {
      myDesignSurface.removeListener(this);
      myDesignSurface.removePanZoomListener(this);
      myDesignSurface.removeAncestorListener(myAncestorListener);
      SceneView currentSceneView = myDesignSurface.getCurrentSceneView();
      if (currentSceneView != null) {
        currentSceneView.getModel().removeListener(this);
      }
    }

    myDesignSurface = surface;
    if (myDesignSurface == null) {
      return;
    }
    myDesignSurface.addListener(this);
    myDesignSurface.addPanZoomListener(this);
    myDesignSurface.addAncestorListener(myAncestorListener);

    SceneView currentSceneView = myDesignSurface.getCurrentSceneView();
    if (currentSceneView != null) {
      currentSceneView.getModel().addListener(this);
    }

    final Configuration configuration = myDesignSurface.getConfiguration();
    if (configuration != null) {
      updateDeviceConfiguration(configuration);
    }
  }

  /**
   * Update the number of screen displayed in X and Y axis
   */
  private void updateScreenNumber(@Nullable NlDesignSurface surface) {
    if (surface != null) {
      myXScreenNumber = !surface.isStackVertically() && surface.getScreenMode() == BOTH ? 2 : 1;
      myYScreenNumber = surface.isStackVertically() && surface.getScreenMode() == BOTH ? 2 : 1;
    }
  }

  /**
   * Update the screen size depending on the orientation.
   * Should be called whenever a change in the orientation occurred
   *
   * @param configuration The current configuration used by the model
   */
  private void updateDeviceConfiguration(Configuration configuration) {
    final Device device = configuration.getDevice();
    final State deviceState = configuration.getDeviceState();
    if (device != null && deviceState != null) {
      myDeviceSize = device.getScreenSize(deviceState.getOrientation());
    }
  }

  /**
   * Handle all mouse interaction onto the Minimap
   */
  private class MouseInteractionListener implements MouseListener, MouseMotionListener, MouseWheelListener {
    private final Point myMouseOrigin = new Point(0, 0);
    private final Point mySurfaceOrigin = new Point(0, 0);
    private double myNewXOffset;
    private double myNewYOffset;
    private Dimension mySceneViewSize = new Dimension();
    private SceneView myCurrentSceneView;
    private boolean myCanDrag;

    @Override
    public void mouseDragged(MouseEvent e) {
      if (myCanDrag) {
        myNewXOffset = mySurfaceOrigin.x + e.getX() - myMouseOrigin.x;
        myNewYOffset = mySurfaceOrigin.y + e.getY() - myMouseOrigin.y;

        // Since the scroll is changed, the panningChanged method will be called by the NlDesignSurface so no
        // need to manually redraw the NlDesignSurface miniature
        assert myDesignSurface != null;
        myDesignSurface.setScrollPosition(
          (int)Math.round(myNewXOffset / mySceneViewScale),
          (int)Math.round(myNewYOffset / mySceneViewScale)
        );
      }
    }

    /**
     * Check is the clicked happened in the miniature {@link NlDesignSurface} representation
     *
     * @param e The {@link MouseEvent}
     * @return True if the event happened in the miniature {@link NlDesignSurface} representation
     */
    public boolean isInDesignSurfaceRectangle(MouseEvent e) {
      assert myDesignSurface != null;
      return e.getX() > myDesignSurfaceOffset.x + myCenterOffset.x
             && e.getX() < myDesignSurfaceOffset.x + myCenterOffset.x + myDesignSurface.getWidth() * mySceneViewScale
             && e.getY() > myDesignSurfaceOffset.y + myCenterOffset.y
             && e.getY() < myDesignSurfaceOffset.y + myCenterOffset.y + myDesignSurface.getHeight() * mySceneViewScale;
    }

    @Override
    public void mousePressed(MouseEvent e) {
      if (myDesignSurface != null && isInDesignSurfaceRectangle(e)) {
        // Init the value for the drag event
        myCurrentSceneView = myDesignSurface.getCurrentSceneView();
        if (myCurrentSceneView == null) {
          return;
        }
        mySceneViewSize = myCurrentSceneView.getSize(mySceneViewSize);
        myMouseOrigin.setLocation(e.getX(), e.getY());
        mySurfaceOrigin.setLocation(myDesignSurfaceOffset.x, myDesignSurfaceOffset.y);
        myCanDrag = true;
      }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      myCanDrag = false;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      if (isInDesignSurfaceRectangle(e) && myIsZoomed) {
        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
      }
      else {
        setCursor(Cursor.getDefaultCursor());
      }
    }

    @Override
    public void mouseExited(MouseEvent e) {
      setCursor(Cursor.getDefaultCursor());
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
      if (myDesignSurface == null) {
        return;
      }

      final int wheelRotation = e.getWheelRotation();
      if (wheelRotation < 0) {
        for (int i = 0; i > wheelRotation; i--) {
          myDesignSurface.zoomIn();
        }
      }
      else if (wheelRotation > 0) {
        for (int i = 0; i < wheelRotation; i++) {
          myDesignSurface.zoomOut();
        }
      }
    }
  }

  /**
   * Set the scale ratio to display the miniature of the {@linkSceneView} and {@link NlDesignSurface} inside this panel.
   * The scale ratio is computed such as both the {@linkSceneView} and the {@link NlDesignSurface} are always totally visible
   * whatever the value of the zoom is.
   *
   * @param currentSceneView  The active {@linkSceneView}
   * @param designSurfaceSize The real size of the {@link NlDesignSurface}
   * @param contentSize       The total size of all the {@linkSceneView} in the {@link NlDesignSurface}
   */
  private void computeScale(@Nullable SceneView currentSceneView,
                            @Nullable Dimension designSurfaceSize,
                            @NotNull Dimension contentSize) {

    if (currentSceneView == null || myMiniMap == null || designSurfaceSize == null) {
      return;
    }

    myIsZoomed = designSurfaceSize.getWidth() < currentSceneView.getX() + contentSize.getWidth()
                 || designSurfaceSize.getHeight() < currentSceneView.getY() + contentSize.getHeight();

    double surfaceScale = designSurfaceSize.height <= 0 || designSurfaceSize.width <= 0
                          ? 1
                          : Math.min(PREFERRED_SIZE.height / designSurfaceSize.getHeight(),
                                     PREFERRED_SIZE.width / designSurfaceSize.getWidth());
    myScaledScreenSpace = (int)Math.round(SCREEN_SPACE * surfaceScale);

    mySceneViewScale = Math.min(PREFERRED_SIZE.height / contentSize.getHeight(),
                                PREFERRED_SIZE.width / contentSize.getWidth());

    if (myDeviceSize != null) {
      myDeviceScale = Math.min(PREFERRED_SIZE.height / myDeviceSize.getHeight() / (double)myYScreenNumber,
                               PREFERRED_SIZE.width / myDeviceSize.getWidth() / (double)myXScreenNumber);
      computeOffsets(myDeviceSize, currentSceneView);
    }
  }

  /**
   * Set the Offsets of the Screens to draw and the offset to center all the content
   *
   * @param deviceSize       Dimension of the current device
   * @param currentSceneView One of the SceneView displayed in the {@link DesignSurface}
   */
  private void computeOffsets(@Nullable Dimension deviceSize, @Nullable SceneView currentSceneView) {
    if (myDesignSurface != null && currentSceneView != null && deviceSize != null) {
      myCurrentSceneViewSize = currentSceneView.getSize(myCurrentSceneViewSize);

      // If there is twoSceneViews displayed,
      // we compute the offset of the secondSceneView
      if (myDesignSurface.getScreenMode() == BOTH) {
        if (myDesignSurface.isStackVertically()) {
          mySecondScreenOffset.setLocation(0, deviceSize.getHeight() * myDeviceScale + myScaledScreenSpace);
        }
        else {
          mySecondScreenOffset.setLocation(deviceSize.getWidth() * myDeviceScale + myScaledScreenSpace, 0);
        }
      }
      myCenterOffset.x = (int)Math.round((PREFERRED_SIZE.getWidth() - myXScreenNumber * deviceSize.getWidth() * myDeviceScale) / 2);
      myCenterOffset.y = (int)Math.round((PREFERRED_SIZE.getHeight() - myYScreenNumber * deviceSize.getHeight() * myDeviceScale) / 2);
      mySecondScreenOffset.translate(myCenterOffset.x, myCenterOffset.y);
    }
  }

  private class MiniMap extends JPanel {

    @Override
    public void paintComponent(Graphics g) {
      // Clear the graphics
      Graphics2D gc = (Graphics2D)g;
      gc.setBackground(UIUtil.getWindowColor());
      gc.clearRect(0, 0, getWidth(), getHeight());

      if (myDesignSurface != null) {

        SceneView currentSceneView = myDesignSurface.getCurrentSceneView();
        if (currentSceneView != null) {

          myDesignSurfaceSize = myDesignSurface.getSize(myDesignSurfaceSize);
          Dimension contentSize = myDesignSurface.getContentSize(null);
          computeScale(currentSceneView, myDesignSurfaceSize, contentSize);
          gc.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
          gc.setColor(BACKGROUND_COLOR);
          drawScreenViews(gc, currentSceneView);

          if (myIsZoomed) {
            drawDesignSurface(gc, currentSceneView);
          }
          gc.setColor(COMPONENT_STROKE_COLOR);
          if (myComponent != null) {
            drawAllComponents(gc, myComponent.getRoot());
          }
        }
      }
    }

    /**
     * Recursively draw all components  by finding the root of component then find all its children and grandchildren (BFS)
     *
     * @param gc        the {@link Graphics2D} to draw on
     * @param component a component in the {@link SceneView}
     */
    private void drawAllComponents(@NotNull Graphics2D gc, @NotNull NlComponent component) {

      // Save the current color to highlight the selected component then reset the color
      Color color = gc.getColor();
      if (myComponent != null && component.getId() != null
          && component.getId().equals(myComponent.getId())) {
        gc.setColor(myColorSet.getSelectedFrames());
      }
      drawComponent(gc, component);
      gc.setColor(color);

      // Recursively draw the others components
      int childCount = component.getChildCount();
      for (int i = 0; i < childCount; i++) {
        NlComponent child = component.getChild(i);
        assert child != null;
        drawAllComponents(gc, child);
      }
    }

    /**
     * Draw on component on all the miniatures {@link SceneView} available
     *
     * @param gc        the {@link Graphics2D} to draw on
     * @param component a component in the {@link SceneView}
     */
    private void drawComponent(@NotNull Graphics2D gc, @NotNull NlComponent component) {

      final double componentRatio = myDeviceScale;
      gc.drawRect(
        (int)Math.round(myCenterOffset.x + NlComponentHelperKt.getX(component) * componentRatio),
        (int)Math.round(myCenterOffset.y + NlComponentHelperKt.getY(component) * componentRatio),
        (int)Math.round(NlComponentHelperKt.getW(component) * componentRatio),
        (int)Math.round(NlComponentHelperKt.getH(component) * componentRatio));

      assert myDesignSurface != null;

      NlDesignSurface nlDesignSurface = myDesignSurface;
      if (nlDesignSurface.getScreenMode() == BOTH) {
        gc.drawRect(
          (int)Math.round(mySecondScreenOffset.x + NlComponentHelperKt.getX(component) * componentRatio),
          (int)Math.round(mySecondScreenOffset.y + NlComponentHelperKt.getY(component) * componentRatio),
          (int)Math.round(NlComponentHelperKt.getW(component) * componentRatio),
          (int)Math.round(NlComponentHelperKt.getH(component) * componentRatio)
        );
      }
    }

    /**
     * Draw the needed screen view depending on the current {@link NlDesignSurface.ScreenMode}.
     *
     * @param gc the {@link Graphics2D} to draw on
     */
    private void drawScreenViews(Graphics2D gc, @NotNull SceneView currentSceneView) {
      if (myDeviceSize == null) {
        return;
      }
      assert myDesignSurface != null;

      // Draw the first screen view
      NlDesignSurface.ScreenMode screenMode = myDesignSurface.getScreenMode();
      int scaledDeviceWidth = (int)Math.round(myDeviceSize.getWidth() * myDeviceScale);
      int scaledDeviceHeight = (int)Math.round(myDeviceSize.getHeight() * myDeviceScale);

      if (screenMode == SCREEN_ONLY) {
        drawNormalScreenView(gc, currentSceneView, myCenterOffset.x, myCenterOffset.y, scaledDeviceWidth, scaledDeviceHeight);
      }
      else if (screenMode == BLUEPRINT_ONLY) {
        drawBlueScreenView(gc, myCenterOffset.x, myCenterOffset.y, scaledDeviceWidth, scaledDeviceHeight);
      }
      else if (screenMode == BOTH) {
        drawNormalScreenView(gc, currentSceneView, myCenterOffset.x, myCenterOffset.y, scaledDeviceWidth, scaledDeviceHeight);
        drawBlueScreenView(gc, mySecondScreenOffset.x, mySecondScreenOffset.y, scaledDeviceWidth, scaledDeviceHeight
        );
      }
    }

    private void drawNormalScreenView(@NotNull Graphics2D gc,
                                      @NotNull SceneView currentSceneView,
                                      int x, int y, int scaledDeviceWidth,
                                      int scaledDeviceHeight) {
      gc.setColor(NORMAL_SCREEN_VIEW_COLOR);
      gc.fillRect(x, y, scaledDeviceWidth, scaledDeviceHeight);
      drawRenderedImage(gc, currentSceneView, myCenterOffset.x, myCenterOffset.y, scaledDeviceWidth, scaledDeviceHeight);
    }

    private void drawBlueScreenView(Graphics2D gc, int x, int y, int scaledDeviceWidth, int scaledDeviceHeight) {
      gc.setColor(BLUEPRINT_SCREEN_VIEW_COLOR);
      gc.fillRect(
        x,
        y,
        scaledDeviceWidth,
        scaledDeviceHeight
      );
    }

    private void drawRenderedImage(@NotNull Graphics2D gc,
                                   @NotNull SceneView currentSceneView,
                                   int x, int y, int scaledDeviceWidth,
                                   int scaledDeviceHeight) {
      SceneManager builder = currentSceneView.getSceneManager();
      if (builder instanceof LayoutlibSceneManager) {
        RenderResult renderResult = ((LayoutlibSceneManager)builder).getRenderResult();
        if (renderResult != null) {
          renderResult.getRenderedImage().drawImageTo(gc, x, y, scaledDeviceWidth, scaledDeviceHeight);
        }
      }
    }

    private void drawDesignSurface(@NotNull Graphics2D gc, @NotNull SceneView currentSceneView) {

      if (myDesignSurfaceSize == null) {
        return;
      }
      // Rectangle of the drawing surface
      gc.setColor(DRAWING_SURFACE_RECTANGLE_COLOR);

      int x = (int)Math.round(myCenterOffset.x + myDesignSurfaceOffset.x - (currentSceneView.getX() / 2.) * mySceneViewScale);
      int y = (int)Math.round(myCenterOffset.y + myDesignSurfaceOffset.y - (currentSceneView.getY() / 2.) * mySceneViewScale);
      int width = (int)Math.round((myDesignSurfaceSize.getWidth() - currentSceneView.getX() / 2.) * mySceneViewScale);
      int height = (int)Math.round((myDesignSurfaceSize.getHeight() - currentSceneView.getY() / 2.) * mySceneViewScale);

      final Rectangle intersection = new Rectangle(x, y, width, height).intersection(getVisibleRect());
      x = intersection.x;
      y = intersection.y;
      width = intersection.width - 1;
      height = intersection.height - 1;

      gc.drawRect(x, y, width, height);

      if (myDeviceSize == null) {
        return;
      }
      // Darken the non visible parts
      gc.setColor(OVERLAY_COLOR);

      // Left
      gc.fillRect(0, 0, x, getHeight());

      // Top
      gc.fillRect(x, 0, width, y);

      // Right
      gc.fillRect(x + width, 0, PREFERRED_SIZE.width, getHeight());

      // Bottom
      gc.fillRect(x,
                  y + height,
                  width,
                  (int)Math.round(Math.max(
                    (myDeviceSize.getHeight() * myDeviceScale) * myYScreenNumber - y - height,
                    (myDeviceSize.getWidth() * myDeviceScale) * myXScreenNumber - y - height)));
    }
  }

  /**
   * Shows a popup containing the {@link PanZoomPanel}
   *
   * @return The created popup
   */
  @NotNull
  public JBPopup showPopup() {
    if (myContainerPopup != null && !myContainerPopup.isDisposed()) {
      return myContainerPopup;
    }

    JBPopup popup = createPopup();
    if (myDesignSurface == null) {
      return popup;
    }

    getSavedScreenLocation(myRelativeLocation);
    Point screenPosition = new Point(myRelativeLocation);
    SwingUtilities.convertPointToScreen(screenPosition, myDesignSurface);
    popup.show(new RelativePoint(myDesignSurface, myRelativeLocation));
    if (!ScreenUtil.isVisible(popup.getLocationOnScreen())) {
      // If the saved popup's position is in the screen, show it
      // otherwise delete the saved location et recompute the default location
      computeDefaultLocation(myRelativeLocation, myDesignSurface);
      savePopupLocation(myRelativeLocation);
      screenPosition.setLocation(myRelativeLocation);
      SwingUtilities.convertPointToScreen(screenPosition, myDesignSurface);
      popup.setLocation(screenPosition);
    }
    PropertiesComponent.getInstance().setValue(PROP_OPEN, true);
    return popup;
  }

  private void registerPopupWindowComponentAdapter(@NotNull JBPopup popup, Component popupOwner, Point relativePosition) {
    UIUtil.getWindow(popup.getContent()).addComponentListener(getPopupWindowListener(popupOwner, relativePosition));
  }

  private void registerApplicationWindowComponentAdapter(@NotNull Component popupOwner,
                                                         @NotNull JBPopup containerPopup,
                                                         @NotNull Point relativeLocation) {
    UIUtil.getWindow(popupOwner).addComponentListener(getApplicationWindowListener(
      popupOwner, containerPopup, relativeLocation));
  }

  /**
   * Hide the popup.
   *
   * Use when to hide the popup when the associated {@link DesignSurface} is not shown.
   * It will trigger a {@link JBPopup#closeOk(InputEvent)} event on the {@link JBPopup}
   * and won't persist the closed state
   *
   * @see #onClosed(LightweightWindowEvent)
   */
  public void hidePopup() {
    if (myContainerPopup != null) {
      myContainerPopup.closeOk(null);
      myContainerPopup = null;
    }
  }

  /**
   * Use when to hide the popup when the associated {@link DesignSurface} is not shown.
   * It will trigger a {@link JBPopup#closeOk(InputEvent)} event on the {@link JBPopup}
   *
   * Close the popup and set property {@link PanZoomPanel#PROP_OPEN} to false.
   *
   * @see #onClosed(LightweightWindowEvent)
   */
  public void closePopup() {
    if (myContainerPopup != null) {
      myContainerPopup.cancel();
      myContainerPopup = null;
    }
  }

  private static void computeDefaultLocation(@NotNull Point popupLocation, @NotNull DesignSurface surface) {
    popupLocation.x = surface.getWidth() - PREFERRED_SIZE.width
                      - surface.getScrollPane().getVerticalScrollBar().getWidth();
    popupLocation.y = NlConstants.RULER_SIZE_PX;
  }

  private static void getSavedScreenLocation(@NotNull Point popupLocation) {
    popupLocation.x = PropertiesComponent.getInstance().getInt(PROP_X_POS, -1);
    popupLocation.y = PropertiesComponent.getInstance().getInt(PROP_Y_POS, -1);
  }

  /**
   * Get the instance of {@link MyPopupWindowComponentAdapter}
   *
   * @param popupOwner       The owner of the popup
   * @param relativePosition The position of the popup relative to its owner
   *                         that will be updated in the listener's callback
   */
  @NotNull
  private ComponentListener getPopupWindowListener(@NotNull Component popupOwner, @NotNull Point relativePosition) {
    if (myPopupComponentAdapter == null) {
      myPopupComponentAdapter = new MyPopupWindowComponentAdapter(popupOwner, relativePosition);
    }
    return myPopupComponentAdapter;
  }

  /**
   * Get the instance of {@link MyApplicationWindowComponentAdapter}
   *
   * @param popupOwner       The owner of the popup
   * @param relativePosition The position of the popup relative to its owner
   *                         that will be updated in the listener's callback
   */
  @NotNull
  private ComponentListener getApplicationWindowListener(@NotNull Component popupOwner,
                                                         @NotNull JBPopup containerPopup,
                                                         @NotNull Point relativePosition) {
    if (myApplicationComponentAdapter == null) {
      myApplicationComponentAdapter = new MyApplicationWindowComponentAdapter(popupOwner, containerPopup, relativePosition);
    }
    return myApplicationComponentAdapter;
  }

  private static void savePopupLocation(@NotNull Point screenLocation) {
    PropertiesComponent.getInstance().setValue(PROP_X_POS, screenLocation.x, -1);
    PropertiesComponent.getInstance().setValue(PROP_Y_POS, screenLocation.y, -1);
  }

  @NotNull
  private JBPopup createPopup() {
    return JBPopupFactory.getInstance().createComponentPopupBuilder(this, this)
      .setTitle(TITLE)
      .setMinSize(this.getSize())
      .setResizable(false)
      .setMovable(true)
      .setRequestFocus(true)
      .setLocateWithinScreenBounds(false)
      .setCancelButton(CANCEL_BUTTON)
      .setShowBorder(true)
      .setShowShadow(true)
      .setCancelOnClickOutside(false)
      .setCancelOnWindowDeactivation(false)
      .setCancelOnOtherWindowOpen(true)
      .addListener(this)
      .createPopup();
  }

  public static boolean isPropertyComponentOpen() {
    return PropertiesComponent.getInstance().getBoolean(PROP_OPEN, false);
  }

  private static class MyPopupWindowComponentAdapter extends ComponentAdapter {

    private final Component myPopupOwner;
    private final Point myRelativePosition;

    public MyPopupWindowComponentAdapter(@NotNull Component popupOwner, @NotNull Point relativePosition) {
      myPopupOwner = popupOwner;
      myRelativePosition = relativePosition;
    }

    @Override
    public void componentMoved(ComponentEvent e) {
      myRelativePosition.setLocation(e.getComponent().getLocation());
      SwingUtilities.convertPointFromScreen(myRelativePosition, myPopupOwner);
      savePopupLocation(myRelativePosition);
    }
  }

  private static class MyApplicationWindowComponentAdapter extends ComponentAdapter {

    private final Component myDesignSurface;
    private final JBPopup myContainerPopup;
    private final Point myRelativeLocation;
    private final Point myScreenLocation;

    public MyApplicationWindowComponentAdapter(@NotNull Component designSurface,
                                               @NotNull JBPopup containerPopup,
                                               @NotNull Point relativeLocation) {
      myDesignSurface = designSurface;
      myContainerPopup = containerPopup;
      myRelativeLocation = relativeLocation;
      myScreenLocation = new Point();
    }

    @Override
    public void componentResized(ComponentEvent e) {
      repositionPopup();
    }

    @Override
    public void componentMoved(ComponentEvent e) {
      repositionPopup();
    }

    private void repositionPopup() {
      myScreenLocation.setLocation(myRelativeLocation);
      SwingUtilities.convertPointToScreen(myScreenLocation, myDesignSurface);
      myContainerPopup.setLocation(myScreenLocation);
      myContainerPopup.setUiVisible(true);
    }
  }
}
