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
package com.android.tools.idea.common.surface;

import static com.android.tools.idea.common.model.Coordinates.getAndroidXDip;
import static com.android.tools.idea.common.model.Coordinates.getAndroidYDip;
import static java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL;

import com.android.tools.adtui.common.AdtUiUtils;
import com.google.common.annotations.VisibleForTesting;
import com.android.tools.adtui.actions.ZoomType;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.adtui.ui.AdtUiCursors;
import com.android.tools.idea.common.api.DragType;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.DnDTransferItem;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.uibuilder.graphics.NlConstants;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintComponentUtilities;
import com.android.tools.idea.uibuilder.handlers.constraint.SecondarySelector;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.model.NlDropEvent;
import com.android.tools.idea.uibuilder.surface.DragDropInteraction;
import com.android.tools.idea.uibuilder.surface.MarqueeInteraction;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.intellij.lang.annotations.JdkConstants;
import org.intellij.lang.annotations.JdkConstants.InputEventMask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * The {@linkplain InteractionManager} is is the central manager of interactions; it is responsible for
 * recognizing when particular interactions should begin and terminate. It listens to the mouse and keyboard
 * events to find out when to start interactions and in order to update the interactions along the way.
 */
public class InteractionManager implements Disposable {
  private static final int HOVER_DELAY_MS = Registry.intValue("ide.tooltip.initialDelay");
  private static final int SCROLL_END_TIME_MS = 500;

  /**
   * The canvas which owns this {@linkplain InteractionManager}.
   */
  @NotNull
  private final DesignSurface mySurface;

  /**
   * The currently executing {@link Interaction}, or null.
   */
  @Nullable
  private Interaction myCurrentInteraction;

  /**
   * The list of overlays associated with {@link #myCurrentInteraction}. Will be
   * null before it has been initialized lazily by the buildDisplayList routine (the
   * initialized value can never be null, but it can be an empty collection).
   */
  @Nullable
  private List<Layer> myLayers;

  /**
   * Most recently seen mouse position (x coordinate). We keep a copy of this
   * value since we sometimes need to know it when we aren't told about the
   * mouse position (such as when a keystroke is received, such as an arrow
   * key in order to tweak the current drop position)
   */
  @SwingCoordinate
  protected int myLastMouseX;

  /**
   * Most recently seen mouse position (y coordinate). We keep a copy of this
   * value since we sometimes need to know it when we aren't told about the
   * mouse position (such as when a keystroke is received, such as an arrow
   * key in order to tweak the current drop position)
   */
  @SwingCoordinate
  protected int myLastMouseY;

  /**
   * Most recently seen mouse mask. We keep a copy of this since in some
   * scenarios (such as on a drag interaction) we don't get access to it.
   */
  @InputEventMask
  protected int myLastModifiersEx;

  /**
   * A timer used to control when to initiate a mouse hover action. It is active only when
   * the mouse is within the design surface. It gets reset every time the mouse is moved, and
   * fires after a certain delay once the mouse comes to rest.
   */
  private final Timer myHoverTimer;

  /**
   * A timer used to decide when we can end the scroll motion.
   */
  private final Timer myScrollEndTimer;

  private final ActionListener myScrollEndListener;

  /**
   * Listener for mouse motion, click and keyboard events.
   */
  private final Listener myListener;

  /**
   * Drop target installed by this manager
   */
  private DropTarget myDropTarget;

  /**
   * Indicates whether listeners have been registered to listen for interactions
   */
  private boolean myIsListening;

  /**
   * Flag to indicate that the user is panning the surface
   */
  private boolean myIsPanning;

  /**
   * Flag to indicate if interaction has canceled by pressing escape button.
   */
  private boolean myIsInteractionCanceled;

  /**
   * Constructs a new {@link InteractionManager} for the given {@link DesignSurface}.
   *
   * @param surface The surface which controls this {@link InteractionManager}
   */
  public InteractionManager(@NotNull DesignSurface surface) {
    mySurface = surface;
    Disposer.register(surface, this);

    myListener = new Listener();

    myHoverTimer = new Timer(HOVER_DELAY_MS, null);
    myHoverTimer.setRepeats(false);

    myScrollEndTimer = new Timer(SCROLL_END_TIME_MS, null);
    myScrollEndTimer.setRepeats(false);

    myScrollEndListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myScrollEndTimer.removeActionListener(this);
        finishInteraction(0, 0, 0, false);
      }
    };
  }

  @Override
  public void dispose() {
    myHoverTimer.stop();
    myScrollEndTimer.stop();
  }

  /**
   * Returns the canvas associated with this {@linkplain InteractionManager}.
   *
   * @return The {@link DesignSurface} associated with this {@linkplain InteractionManager}.
   * Never null.
   */
  @NotNull
  public DesignSurface getSurface() {
    return mySurface;
  }

  public boolean isPanning() {
    return myIsPanning;
  }

  /**
   * This will registers all the listeners to {@link DesignSurface} needed by the {@link InteractionManager}.<br>
   * Do nothing if it is listening already.
   * @see #stopListening()
   */
  public void startListening() {
    if (myIsListening) {
      return;
    }
    JComponent layeredPane = mySurface.getLayeredPane();
    layeredPane.addMouseMotionListener(myListener);
    layeredPane.addMouseWheelListener(myListener);
    layeredPane.addMouseListener(myListener);
    layeredPane.addKeyListener(myListener);

    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      myDropTarget = new DropTarget(mySurface.getLayeredPane(), DnDConstants.ACTION_COPY_OR_MOVE, myListener, true, null);
    }
    myHoverTimer.addActionListener(myListener);
    myIsListening = true;
  }

  /**
   * This will unregister all the listeners previously registered by from {@link DesignSurface}.<br>
   * Do nothing if it is not listening.
   * @see #startListening()
   */
  public void stopListening() {
    if (!myIsListening) {
      return;
    }
    JComponent layeredPane = mySurface.getLayeredPane();
    layeredPane.removeMouseMotionListener(myListener);
    layeredPane.removeMouseWheelListener(myListener);
    layeredPane.removeMouseListener(myListener);
    layeredPane.removeKeyListener(myListener);
    if (myDropTarget != null) {
      myDropTarget.removeDropTargetListener(myListener);
    }
    myHoverTimer.removeActionListener(myListener);
    myHoverTimer.stop();
    myIsListening = false;
  }

  /**
   * Starts the given interaction.
   */
  private void startInteraction(@SwingCoordinate int x, @SwingCoordinate int y, @Nullable Interaction interaction,
                                @InputEventMask int modifiersEx) {
    if (myCurrentInteraction != null) {
      finishInteraction(x, y, modifiersEx, true);
      assert myCurrentInteraction == null;
    }

    if (interaction != null) {
      myCurrentInteraction = interaction;
      myCurrentInteraction.begin(x, y, modifiersEx);
      myLayers = interaction.createOverlays();
    }
  }

  /**
   * Returns the currently active overlays, if any
   */
  @Nullable
  public List<Layer> getLayers() {
    return myLayers;
  }

  /**
   * Returns the most recently observed input event mask
   */
  @InputEventMask
  public int getLastModifiersEx() {
    return myLastModifiersEx;
  }

  /**
   * TODO: Remove this function when test framework use real interaction for testing.
   */
  @TestOnly
  public void setModifier(@InputEventMask int modifier) {
    myLastModifiersEx = modifier;
  }

  /**
   * Updates the current interaction, if any, for the given event.
   */
  private void updateMouse(@SwingCoordinate int x, @SwingCoordinate int y) {
    if (myCurrentInteraction != null) {
      myCurrentInteraction.update(x, y, myLastModifiersEx);
      mySurface.repaint();
    }
  }

  /**
   * Finish the given interaction, either from successful completion or from
   * cancellation.
   *
   * @param x         The most recent mouse x coordinate applicable to the new
   *                  interaction, in Swing coordinates.
   * @param y         The most recent mouse y coordinate applicable to the new
   *                  interaction, in Swing coordinates.
   * @param modifiersEx The most recent modifier key state
   * @param canceled  True if and only if the interaction was canceled.
   */
  private void finishInteraction(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask int modifiersEx, boolean canceled) {
    if (myCurrentInteraction != null) {
      myCurrentInteraction.end(x, y, modifiersEx, canceled);
      if (myLayers != null) {
        for (Layer layer : myLayers) {
          //noinspection SSBasedInspection
          layer.dispose();
        }
        myLayers = null;
      }
      myCurrentInteraction = null;
      myLastModifiersEx = 0;
      updateCursor(x, y, modifiersEx);
      mySurface.repaint();
    }
  }

  /**
   * Update the cursor to show the type of operation we expect on a mouse press:
   * <ul>
   * <li>Over a resizable region, show the resize cursor.
   * <li>Over a SceneView, show the cursor which provide by the Scene.
   * <li>Otherwise, show the default arrow cursor.
   * </ul>
   */
  void updateCursor(@SwingCoordinate int x, @SwingCoordinate int y, @JdkConstants.InputEventMask int modifier) {
    Cursor cursor = null;
    SceneView sceneView = mySurface.getSceneView(x, y);
    if (sceneView != null) {
      Dimension sceneViewSize = sceneView.getSize();
      // Check if the mouse position is at the bottom-right corner of sceneView.
      Rectangle resizeZone = new Rectangle(sceneView.getX() + sceneViewSize.width,
                                           sceneView.getY() + sceneViewSize.height,
                                           NlConstants.RESIZING_HOVERING_SIZE,
                                           NlConstants.RESIZING_HOVERING_SIZE);
      if (resizeZone.contains(x, y) && sceneView.getSurface().isResizeAvailable()) {
        cursor = Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
      }
      else {
        SceneContext context = SceneContext.get(sceneView);
        context.setMouseLocation(x, y);
        sceneView.getScene().mouseHover(context, getAndroidXDip(sceneView, x), getAndroidYDip(sceneView, y), modifier);
        cursor = sceneView.getScene().getMouseCursor();
      }
    }
    mySurface.setCursor(cursor != Cursor.getDefaultCursor() ? cursor : null);
  }

  public boolean isInteractionInProgress() {
    return myCurrentInteraction != null;
  }

  /**
   * Helper class which implements the {@link MouseMotionListener},
   * {@link MouseListener} and {@link KeyListener} interfaces.
   */
  private class Listener
    implements MouseMotionListener, MouseListener, KeyListener, DropTargetListener, ActionListener, MouseWheelListener {

    // --- Implements MouseListener ----

    @Override
    public void mouseClicked(@NotNull MouseEvent event) {
      int x = event.getX();
      int y = event.getY();
      int clickCount = event.getClickCount();

      if (clickCount == 2 && event.getButton() == MouseEvent.BUTTON1) {
        mySurface.onDoubleClick(x, y);
        return;
      }

      // No need to navigate XML when click was done holding some modifiers (e.g multi-selecting).
      if (clickCount == 1 && event.getButton() == MouseEvent.BUTTON1 && !event.isShiftDown() && !AdtUiUtils.isActionKeyDown(event)) {
        mySurface.onSingleClick(x, y);
      }

      if (event.isPopupTrigger()) {
        NlComponent component = selectComponentAt(x, y, false, true);
        mySurface.getActionManager().showPopup(event, component);
      }
    }

    @Override
    public void mousePressed(@NotNull MouseEvent event) {
      if (event.getID() == MouseEvent.MOUSE_PRESSED) {
        mySurface.getLayeredPane().requestFocusInWindow();
      }

      myIsInteractionCanceled = false;

      myLastMouseX = event.getX();
      myLastMouseY = event.getY();
      myLastModifiersEx = event.getModifiersEx();

      if (event.isPopupTrigger()) {
        NlComponent selected = selectComponentAt(event.getX(), event.getY(), false, true);
        mySurface.getActionManager().showPopup(event, selected);
        event.consume();
        return;
      }

      if (interceptPanInteraction(event)) {
        handlePanInteraction(myLastMouseX, myLastMouseY);
        return;
      }

      Interaction interaction = getSurface().createInteractionOnClick(myLastMouseX, myLastMouseY);
      if (interaction != null) {
        startInteraction(myLastMouseX, myLastMouseY, interaction, myLastModifiersEx);
      }
    }

    @Override
    public void mouseReleased(@NotNull MouseEvent event) {
      if (myIsInteractionCanceled) {
        return;
      }
      if (event.isPopupTrigger()) {
        NlComponent selected = selectComponentAt(event.getX(), event.getY(), false, true);
        mySurface.repaint();
        mySurface.getActionManager().showPopup(event, selected);
        return;
      }
      else if (interceptPanInteraction(event)) {
        setPanning(false);
        return;
      }
      else if (event.getButton() > 1 || SystemInfo.isMac && event.isControlDown()) {
        // mouse release from a popup click (the popup menu was posted on
        // the mousePressed event
        return;
      }

      int x = event.getX();
      int y = event.getY();
      int modifiersEx = event.getModifiersEx();

      if (myCurrentInteraction == null) {
        boolean allowToggle = (modifiersEx & (InputEvent.SHIFT_MASK | Toolkit.getDefaultToolkit().getMenuShortcutKeyMask())) != 0;
        selectComponentAt(x, y, allowToggle, false);
        mySurface.repaint();
      }
      if (myCurrentInteraction == null) {
        updateCursor(x, y, modifiersEx);
      }
      else {
        finishInteraction(x, y, modifiersEx, false);
        myCurrentInteraction = null;
      }
      mySurface.repaint();
    }

    /**
     * Selects the component under the given x,y coordinate, optionally
     * toggling or replacing the selection.
     *
     * @param x                       The mouse click x coordinate, in Swing coordinates.
     * @param y                       The mouse click y coordinate, in Swing coordinates.
     * @param allowToggle             If true, clicking an unselected component adds it to the selection,
     *                                and clicking a selected component removes it from the selection. If not,
     *                                the selection is replaced.
     * @param ignoreIfAlreadySelected If true, and the clicked component is already selected, leave the
     *                                selection (including possibly other selected components) alone
     */
    @Nullable
    private NlComponent selectComponentAt(@SwingCoordinate int x, @SwingCoordinate int y, boolean allowToggle,
                                          boolean ignoreIfAlreadySelected) {
      // Just a click, select
      SceneView sceneView = mySurface.getSceneView(x, y);
      if (sceneView == null) {
        return null;
      }

      SceneContext context = SceneContext.get(sceneView);
      int xDip = getAndroidXDip(sceneView, x);
      int yDip = getAndroidYDip(sceneView, y);
      Scene scene = sceneView.getScene();
      Target clickedTarget = scene.findTarget(context, xDip, yDip, myLastModifiersEx);
      SceneComponent clicked;
      if (clickedTarget != null) {
        clicked = clickedTarget.getComponent();
      }
      else {
        clicked = scene.findComponent(context, xDip, yDip);
      }
      NlComponent component = null;
      if (clicked != null) {
        component = clicked.getNlComponent();
      }

      SecondarySelector secondarySelector = Scene.getSecondarySelector(context, xDip, yDip);
      boolean useSecondarySelector = component != null && secondarySelector != null;
      if (useSecondarySelector) {
        // Change clicked component to the secondary selection.
        component = secondarySelector.getComponent();
      }

      SelectionModel selectionModel = sceneView.getSelectionModel();
      if (ignoreIfAlreadySelected && secondarySelector == null && component != null && selectionModel.isSelected(component)) {
        return component;
      }

      if (component == null) {
        selectionModel.clear();
      }
      else if (allowToggle) {
        selectionModel.toggle(component);
      }
      else if (useSecondarySelector) {
        selectionModel.setSecondarySelection(component, secondarySelector.getConstraint());
      }
      else {
        selectionModel.setSelection(Collections.singletonList(component));
      }
      return component;
    }

    @Nullable
    private NlComponent getComponentAt(@SwingCoordinate int x, @SwingCoordinate int y) {
      SceneView sceneView = mySurface.getSceneView(x, y);
      if (sceneView == null) {
        return null;
      }
      return Coordinates.findComponent(sceneView, x, y);
    }

    @Override
    public void mouseEntered(@NotNull MouseEvent event) {
      myHoverTimer.restart();
    }

    @Override
    public void mouseExited(@NotNull MouseEvent event) {
      myHoverTimer.stop();
    }

    // --- Implements MouseMotionListener ----

    @Override
    public void mouseDragged(MouseEvent event) {
      if (myIsInteractionCanceled) {
        return;
      }
      int x = event.getX();
      int y = event.getY();

      if (interceptPanInteraction(event)) {
        handlePanInteraction(x, y);
        return;
      }

      if (!SwingUtilities.isLeftMouseButton(event)) {
        // mouse drag from a popup click (the popup menu was posted on
        // the mousePressed event
        return;
      }

      int modifiersEx = event.getModifiersEx();
      if (myCurrentInteraction != null) {
        myLastMouseX = x;
        myLastMouseY = y;
        myLastModifiersEx = modifiersEx;
        myCurrentInteraction.update(myLastMouseX, myLastMouseY, myLastModifiersEx);
        updateCursor(x, y, modifiersEx);
        mySurface.getLayeredPane().scrollRectToVisible(
          new Rectangle(x - NlConstants.DEFAULT_SCREEN_OFFSET_X, y - NlConstants.DEFAULT_SCREEN_OFFSET_Y,
                        2 * NlConstants.DEFAULT_SCREEN_OFFSET_X, 2 * NlConstants.DEFAULT_SCREEN_OFFSET_Y));
        mySurface.repaint();
      }
      else {
        x = myLastMouseX; // initiate the drag from the mousePress location, not the point we've dragged to
        y = myLastMouseY;
        myLastModifiersEx = modifiersEx;
        SceneView sceneView = mySurface.getSceneView(x, y);
        if (sceneView == null) {
          return;
        }
        Scene scene = sceneView.getScene();
        SelectionModel selectionModel = sceneView.getSelectionModel();

        int xDp = getAndroidXDip(sceneView, x);
        int yDp = getAndroidYDip(sceneView, y);

        Interaction interaction;
        NlModel model = sceneView.getModel();
        SceneComponent component = null;

        // Make sure we start from root if we don't have anything selected
        if (selectionModel.isEmpty() && !model.getComponents().isEmpty()) {
          selectionModel.setSelection(ImmutableList.of(model.getComponents().get(0).getRoot()));
        }

        // See if you're dragging inside a selected parent; if so, drag the selection instead of any
        // leaf nodes inside it
        NlComponent primaryNlComponent = selectionModel.getPrimary();
        SceneComponent primary = scene.getSceneComponent(primaryNlComponent);
        if (primary != null && primary.getParent() != null && primary.containsX(xDp) && primary.containsY(yDp)) {
          component = primary;
        }
        if (component == null) {
          component = scene.findComponent(SceneContext.get(sceneView), xDp, yDp);
        }

        if (component == null || component.getParent() == null) {
          // Dragging on the background/root view: start a marquee selection
          interaction = new MarqueeInteraction(sceneView);
        }
        else {
          interaction = getSurface().createInteractionOnDrag(component, primary);
        }

        if (interaction != null) {
          startInteraction(x, y, interaction, modifiersEx);
        }
        updateCursor(x, y, modifiersEx);
      }

      myHoverTimer.restart();
    }

    @Override
    public void mouseMoved(MouseEvent event) {
      int x = event.getX();
      int y = event.getY();
      myLastMouseX = x;
      myLastMouseY = y;

      if (interceptPanInteraction(event)) {
        handlePanInteraction(x, y);
        return;
      }
      int modifier = event.getModifiersEx();
      myLastModifiersEx = modifier;

      mySurface.hover(x, y);
      if ((myLastModifiersEx & InputEvent.BUTTON1_DOWN_MASK) != 0) {
        if (myCurrentInteraction != null) {
          updateMouse(x, y);
          mySurface.repaint();
        }
      }
      else {
        updateCursor(x, y, modifier);
      }

      myHoverTimer.restart();
    }

    // --- Implements KeyListener ----

    @Override
    public void keyTyped(KeyEvent event) {
      myLastModifiersEx = event.getModifiersEx();
    }

    @Override
    public void keyPressed(KeyEvent event) {
      int modifiersEx = event.getModifiersEx();
      int keyCode = event.getKeyCode();

      myLastModifiersEx = modifiersEx;

      Scene scene = mySurface.getScene();
      if (scene != null) {
        // TODO: Make it so this step is only done when necessary. i.e Move to ConstraintSceneInteraction.keyPressed().
        // Since holding some of these keys might change some visuals, repaint.
        scene.needsRebuildList();
        scene.repaint();
      }

      // Give interactions a first chance to see and consume the key press
      if (myCurrentInteraction != null) {
        // unless it's "Escape", which cancels the interaction
        if (keyCode == KeyEvent.VK_ESCAPE) {
          finishInteraction(myLastMouseX, myLastMouseY, myLastModifiersEx, true);
          myIsInteractionCanceled = true;
          return;
        }

        if (myCurrentInteraction.keyPressed(event)) {
          return;
        }
      }

      if (keyCode == DesignSurfaceShortcut.PAN.getKeyCode()) {
        setPanning(true);
        return;
      }

      // The below shortcuts only apply without modifier keys.
      // (Zooming with "+" *may* require modifier keys, since on some keyboards you press for
      // example Shift+= to create the + key.
      if (event.isAltDown() || event.isMetaDown() || event.isShiftDown() || event.isControlDown()) {
        return;
      }

      if (keyCode == KeyEvent.VK_DELETE || keyCode == KeyEvent.VK_BACK_SPACE) {
        SceneView sceneView = mySurface.getSceneView(myLastMouseX, myLastMouseY);
        if (sceneView != null) {
          SelectionModel model = sceneView.getSelectionModel();
          if (!model.isEmpty() && !ConstraintComponentUtilities.clearSelectedConstraint(mySurface)) {
            List<NlComponent> selection = model.getSelection();
            sceneView.getModel().delete(selection);
          }
        }
      }
    }

    @Override
    public void keyReleased(KeyEvent event) {
      myLastModifiersEx = event.getModifiersEx();

      Scene scene = mySurface.getScene();
      if (scene != null) {
        // TODO: Make it so this step is only done when necessary. i.e Move to ConstraintSceneInteraction.keyReleased().
        // Since holding some of these keys might change some visuals, repaint.
        scene.needsRebuildList();
        scene.repaint();
      }
      if (myCurrentInteraction != null) {
        myCurrentInteraction.keyReleased(event);
      }

      if (event.getKeyCode() == DesignSurfaceShortcut.PAN.getKeyCode()) {
        setPanning(false);
        updateCursor(myLastMouseX, myLastMouseY, myLastModifiersEx);
      }
    }

    // ---- Implements DropTargetListener ----

    @Override
    public void dragEnter(DropTargetDragEvent dragEvent) {
      if (myCurrentInteraction == null) {
        NlDropEvent event = new NlDropEvent(dragEvent);
        Point location = event.getLocation();
        myLastMouseX = location.x;
        myLastMouseY = location.y;

        SceneView sceneView = mySurface.getSceneView(myLastMouseX, myLastMouseY);
        if (sceneView == null) {
          event.reject();
          return;
        }
        NlModel model = sceneView.getModel();
        DnDTransferItem item = DnDTransferItem.getTransferItem(event.getTransferable(), true /* allow placeholders */);
        if (item == null) {
          event.reject();
          return;
        }
        DragType dragType = event.getDropAction() == DnDConstants.ACTION_COPY ? DragType.COPY : DragType.MOVE;
        InsertType insertType = model.determineInsertType(dragType, item, true /* preview */);

        List<NlComponent> dragged;
        if (StudioFlags.NELE_DRAG_PLACEHOLDER.get() && !item.isFromPalette()) {
          // When dragging from ComponentTree, it should reuse the existing NlComponents rather than creating the new ones.
          // This impacts some Handlers, using StudioFlag to protect for now.
          // Most of Handlers should be removed once this flag is removed.
          dragged = new ArrayList<>(mySurface.getSelectionModel().getSelection());
        }
        else {
          if (item.isFromPalette()) {
            // remove selection when dragging from Palette.
            mySurface.getSelectionModel().clear();
          }
          dragged = model.createComponents(item, insertType, mySurface);
        }

        if (dragged.isEmpty()) {
          event.reject();
          return;
        }

        DragDropInteraction interaction = new DragDropInteraction(mySurface, dragged);
        interaction.setType(dragType);
        interaction.setTransferItem(item);
        startInteraction(myLastMouseX, myLastMouseY, interaction, 0);

        // This determines the icon presented to the user while dragging.
        // If we are dragging a component from the palette then use the icon for a copy, otherwise show the icon
        // that reflects the users choice i.e. controlled by the modifier key.
        event.accept(insertType);
      }
    }

    @Override
    public void dragOver(DropTargetDragEvent dragEvent) {
      NlDropEvent event = new NlDropEvent(dragEvent);
      Point location = event.getLocation();
      myLastMouseX = location.x;
      myLastMouseY = location.y;
      SceneView sceneView = mySurface.getSceneView(myLastMouseX, myLastMouseY);
      if (sceneView != null && myCurrentInteraction instanceof DragDropInteraction) {
        DragDropInteraction interaction = (DragDropInteraction)myCurrentInteraction;
        interaction.update(myLastMouseX, myLastMouseY, myLastModifiersEx);
        if (interaction.acceptsDrop()) {
          DragType dragType = event.getDropAction() == DnDConstants.ACTION_COPY ? DragType.COPY : DragType.MOVE;
          interaction.setType(dragType);
          NlModel model = sceneView.getModel();
          InsertType insertType = model.determineInsertType(dragType, interaction.getTransferItem(), true /* preview */);

          // This determines the icon presented to the user while dragging.
          // If we are dragging a component from the palette then use the icon for a copy, otherwise show the icon
          // that reflects the users choice i.e. controlled by the modifier key.
          event.accept(insertType);
        } else {
          event.reject();
        }
      }
      else {
        event.reject();
      }
    }

    @Override
    public void dropActionChanged(DropTargetDragEvent event) {
    }

    @Override
    public void dragExit(DropTargetEvent event) {
      if (myCurrentInteraction instanceof DragDropInteraction) {
        finishInteraction(myLastMouseX, myLastMouseY, myLastModifiersEx, true /* cancel interaction */);
      }
    }

    @Override
    public void drop(final DropTargetDropEvent dropEvent) {
      NlDropEvent event = new NlDropEvent(dropEvent);
      Point location = event.getLocation();
      myLastMouseX = location.x;
      myLastMouseY = location.y;
      InsertType insertType = performDrop(event.getDropAction(), event.getTransferable());
      if (insertType != null) {
        // This determines how the DnD source acts to a completed drop.
        event.accept(insertType);
        event.complete();
      }
      else {
        event.reject();
      }
    }

    @Nullable
    private InsertType performDrop(int dropAction, @Nullable Transferable transferable) {
      if (!(myCurrentInteraction instanceof DragDropInteraction)) {
        return null;
      }
      InsertType insertType = finishDropInteraction(dropAction, transferable);
      finishInteraction(myLastMouseX, myLastMouseY, myLastModifiersEx, (insertType == null));
      return insertType;
    }

    @Nullable
    private InsertType finishDropInteraction(int dropAction, @Nullable Transferable transferable) {
      if (transferable == null) {
        return null;
      }
      DnDTransferItem item = DnDTransferItem.getTransferItem(transferable, false /* no placeholders */);
      if (item == null) {
        return null;
      }
      SceneView sceneView = mySurface.getSceneView(myLastMouseX, myLastMouseY);
      if (sceneView == null) {
        return null;
      }

      NlModel model = sceneView.getModel();
      DragType dragType = dropAction == DnDConstants.ACTION_COPY ? DragType.COPY : DragType.MOVE;
      InsertType insertType = model.determineInsertType(dragType, item, false /* not for preview */);

      DragDropInteraction interaction = (DragDropInteraction)myCurrentInteraction;
      assert interaction != null;
      interaction.setType(dragType);
      interaction.setTransferItem(item);

      List<NlComponent> dragged = interaction.getDraggedComponents();
      List<NlComponent> components;
      if (insertType.isMove()) {
        components = mySurface.getSelectionModel().getSelection();
      }
      else {
        components = model.createComponents(item, insertType, mySurface);

        if (components.isEmpty()) {
          return null;  // User cancelled
        }
      }
      if (dragged.size() != components.size()) {
        throw new AssertionError(
          String.format("Problem with drop: dragged.size(%1$d) != components.size(%2$d)", dragged.size(), components.size()));
      }
      for (int index = 0; index < dragged.size(); index++) {
        NlComponentHelperKt.setX(components.get(index), NlComponentHelperKt.getX(dragged.get(index)));
        NlComponentHelperKt.setY(components.get(index), NlComponentHelperKt.getY(dragged.get(index)));
      }
      dragged.clear();
      dragged.addAll(components);
      return insertType;
    }

    // --- Implements ActionListener ----

    @Override
    public void actionPerformed(ActionEvent e) {
      if (e.getSource() != myHoverTimer) {
        return;
      }

      int x = myLastMouseX; // initiate the drag from the mousePress location, not the point we've dragged to
      int y = myLastMouseY;

      // TODO: find the correct tooltip? to show
      mySurface.hover(x, y);
    }

    // --- Implements MouseWheelListener ----

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
      int x = e.getX();
      int y = e.getY();

      int scrollAmount;
      if (e.getScrollType() == WHEEL_UNIT_SCROLL) {
        scrollAmount = e.getUnitsToScroll();
      }
      else {
        scrollAmount = (e.getWheelRotation() < 0 ? -1 : 1);
      }

      // On Touchpad handling horizontal scrolling, the horizontal scroll is
      // interpreted as a mouseWheel Event with Shift down.
      // If some scrolling imprecision happens for other scroll interaction, it might be good
      // to do the filtering at a higher level
      if (!e.isShiftDown()
          && (SystemInfo.isMac && e.isMetaDown()
              || e.isControlDown())) {
        if (scrollAmount < 0) {
          mySurface.zoom(ZoomType.IN, x, y);
        }
        else if (scrollAmount > 0) {
          mySurface.zoom(ZoomType.OUT, x, y);
        }
        return;
      }

      SceneView sceneView = mySurface.getSceneView(x, y);
      if (sceneView == null) {
        e.getComponent().getParent().dispatchEvent(e);
        return;
      }

      final NlComponent component = Coordinates.findComponent(sceneView, x, y);
      if (component == null) {
        // There is no component consuming the scroll
        e.getComponent().getParent().dispatchEvent(e);
        return;
      }

      boolean isScrollInteraction;
      if (myCurrentInteraction == null) {
        ScrollInteraction scrollInteraction = ScrollInteraction.createScrollInteraction(sceneView, component);
        if (scrollInteraction == null) {
          // There is no component consuming the scroll
          e.getComponent().getParent().dispatchEvent(e);
          return;
        }

        // Start a scroll interaction and a timer to bundle all the scroll events
        startInteraction(x, y, scrollInteraction, 0);
        isScrollInteraction = true;
        myScrollEndTimer.addActionListener(myScrollEndListener);
      }
      else {
        isScrollInteraction = myCurrentInteraction instanceof ScrollInteraction;
      }

      if (isScrollInteraction && !((ScrollInteraction)myCurrentInteraction).canScroll(scrollAmount)) {
        JScrollPane scrollPane = mySurface.getScrollPane();
        JViewport viewport = scrollPane.getViewport();
        Dimension extentSize = viewport.getExtentSize();
        Dimension viewSize = viewport.getViewSize();
        if (viewSize.width > extentSize.width || viewSize.height > extentSize.height) {
          e.getComponent().getParent().dispatchEvent(e);
          return;
        }
      }
      myCurrentInteraction.scroll(e.getX(), e.getY(), scrollAmount);

      if (isScrollInteraction) {
        myScrollEndTimer.restart();
      }
    }
  }

  void setPanning(boolean panning) {
    if (panning != myIsPanning) {
      myIsPanning = panning;
      mySurface.setCursor(panning ? AdtUiCursors.GRAB
                                  : Cursor.getDefaultCursor());
    }
  }

  /**
   * Check if the mouse wheel button is down.
   *
   * @param event {@link MouseEvent} passed by {@link MouseMotionListener#mouseDragged}
   * @return true if the event has been intercepted and handled, false otherwise.
   */
  public boolean interceptPanInteraction(@NotNull MouseEvent event) {
    if (myIsPanning || (event.getModifiersEx() & InputEvent.BUTTON2_DOWN_MASK) != 0) {
      return true;
    }
    return false;
  }

  /**
   * Scroll the {@link DesignSurface} by the same amount as the drag distance.
   *
   * @param x     x position of the cursor for the passed event
   * @param y     y position of the cursor for the passed event
   */
  void handlePanInteraction(@SwingCoordinate int x, @SwingCoordinate int y) {
    DesignSurface surface = getSurface();
    Point position = surface.getScrollPosition();
    setPanning(true);
    // position can be null in tests
    if (position != null) {
      position.translate(myLastMouseX - x, myLastMouseY - y);
      surface.setScrollPosition(position);
    }
  }

  /**
   * Cancels the current running interaction
   */
  public void cancelInteraction() {
    finishInteraction(myLastMouseX, myLastMouseY, myLastModifiersEx, true);
  }

  @VisibleForTesting
  public Object getListener() {
    return myListener;
  }
}
