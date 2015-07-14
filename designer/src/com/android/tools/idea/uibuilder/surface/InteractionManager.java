/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.uibuilder.model.*;
import com.android.utils.XmlUtils;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PsiNavigateUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.awt.event.*;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.uibuilder.model.SelectionHandle.PIXEL_MARGIN;
import static com.android.tools.idea.uibuilder.model.SelectionHandle.PIXEL_RADIUS;

/**
 * The {@linkplain InteractionManager} is is the central manager of interactions; it is responsible
 * for recognizing when particular interactions should begin and terminate. It
 * listens to the drag, mouse and keyboard systems to find out when to start
 * interactions and in order to update the interactions along the way.
 */
public class InteractionManager {
  /** The canvas which owns this {@linkplain InteractionManager}. */
  @NonNull
  private final DesignSurface mySurface;

  /** The currently executing {@link Interaction}, or null. */
  @Nullable
  private Interaction myCurrentInteraction;

  /**
   * The list of overlays associated with {@link #myCurrentInteraction}. Will be
   * null before it has been initialized lazily by the paint routine (the
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
  protected int myLastStateMask;

  /**
   * Listener for mouse motion, click and keyboard events.
   */
  private Listener myListener;

  /** Drop target installed by this manager */
  private DropTarget myDropTarget;

  /**
   * Constructs a new {@link InteractionManager} for the given
   * {@link DesignSurface}.
   *
   * @param surface The surface which controls this {@link InteractionManager}
   */
  public InteractionManager(@NonNull DesignSurface surface) {
    mySurface = surface;
  }

  /**
   * Returns the canvas associated with this {@linkplain InteractionManager}.
   *
   * @return The {@link DesignSurface} associated with this {@linkplain InteractionManager}.
   *         Never null.
   */
  @NonNull
  public DesignSurface getSurface() {
    return mySurface;
  }

  /**
   * Returns the current {@link Interaction}, if one is in progress, and otherwise returns
   * null.
   *
   * @return The current interaction or null.
   */
  @Nullable
  public Interaction getCurrentInteraction() {
    return myCurrentInteraction;
  }

  /**
   * Registers all the listeners needed by the {@link InteractionManager}.
   */
  public void registerListeners() {
    assert myListener == null;
    myListener = new Listener();
    JComponent layeredPane = mySurface.getLayeredPane();
    layeredPane.addMouseMotionListener(myListener);
    layeredPane.addMouseListener(myListener);
    layeredPane.addKeyListener(myListener);

    myDropTarget = new DropTarget(mySurface.getLayeredPane(), DnDConstants.ACTION_COPY_OR_MOVE, myListener, true, null);
  }

  /**
   * Unregisters all the listeners previously registered by
   * {@link #registerListeners}.
   */
  public void unregisterListeners() {
    myDropTarget.removeDropTargetListener(myListener);
  }

  /**
   * Starts the given interaction.
   */
  private void startInteraction(@SwingCoordinate int x, @SwingCoordinate int y, @Nullable Interaction interaction,
                                int mask) {
    if (myCurrentInteraction != null) {
      finishInteraction(x, y, true);
      assert myCurrentInteraction == null;
    }

    if (interaction != null) {
      myCurrentInteraction = interaction;
      myCurrentInteraction.begin(x, y, mask);
     myLayers = interaction.createOverlays();
    }
  }

  /** Returns the currently active overlays, if any */
  @Nullable
  public List<Layer> getLayers() {
    return myLayers;
  }

  /**
   * Updates the current interaction, if any, for the given event.
   */
  private void updateMouse(@SwingCoordinate int x, @SwingCoordinate int y) {
    if (myCurrentInteraction != null) {
      myCurrentInteraction.update(x, y);
    }
  }

  /**
   * Finish the given interaction, either from successful completion or from
   * cancellation.
   *
   * @param x        The most recent mouse x coordinate applicable to the new
   *                 interaction, in Swing coordinates.
   * @param y        The most recent mouse y coordinate applicable to the new
   *                 interaction, in Swing coordinates.
   * @param canceled True if and only if the interaction was canceled.
   */
  private void finishInteraction(@SwingCoordinate int x, @SwingCoordinate int y, boolean canceled) {
    if (myCurrentInteraction != null) {
      myCurrentInteraction.end(x, y, canceled);
      if (myLayers != null) {
        for (Layer layer : myLayers) {
          //noinspection SSBasedInspection
          layer.dispose();
        }
        myLayers = null;
      }
      myCurrentInteraction = null;
      myLastStateMask = 0;
      updateCursor(x, y);
      mySurface.repaint();
    }
  }

  /**
   * Update the cursor to show the type of operation we expect on a mouse press:
   * <ul>
   * <li>Over a selection handle, show a directional cursor depending on the position of
   * the selection handle
   * <li>Over a widget, show a move (hand) cursor
   * <li>Otherwise, show the default arrow cursor
   * </ul>
   */
  void updateCursor(@SwingCoordinate int x, @SwingCoordinate int y) {
    // We don't hover on the root since it's not a widget per see and it is always there.
    ScreenView screenView = mySurface.getScreenView(x, y);
    SelectionModel selectionModel = screenView.getSelectionModel();
    if (!selectionModel.isEmpty()) {
      int mx = Coordinates.getAndroidX(screenView, x);
      int my = Coordinates.getAndroidY(screenView, y);
      int max = Coordinates.getAndroidDimension(screenView, PIXEL_RADIUS + PIXEL_MARGIN);
      SelectionHandle handle = selectionModel.findHandle(mx, my, max);
      if (handle != null) {
        Cursor cursor = handle.getCursor();
        if (cursor != mySurface.getCursor()) {
          mySurface.setCursor(cursor);
        }
        return;
      }

      // See if it's over a selected view
      NlComponent component = selectionModel.findComponent(mx, my);
      if (component != null && !component.isRoot()) {
          Cursor cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
          if (cursor != mySurface.getCursor()) {
            mySurface.setCursor(cursor);
          }
          return;
      }
    }

    mySurface.setCursor(null);
  }

  /**
   * Helper class which implements the {@link MouseMotionListener},
   * {@link MouseListener} and {@link KeyListener} interfaces.
   */
  private class Listener implements MouseMotionListener, MouseListener, KeyListener, DropTargetListener {

    // --- Implements MouseListener ----

    @Override
    public void mouseClicked(@NonNull MouseEvent event) {
      if (event.getClickCount() == 2 && event.getButton() == MouseEvent.BUTTON1) {
        // Warp to the text editor and show the corresponding XML for the
        // double-clicked widget
        int x = event.getX();
        int y = event.getY();
        ScreenView myScreenView = mySurface.getScreenView(x, y);
        NlComponent component = Coordinates.findComponent(myScreenView, x, y);
        if (component != null) {
          PsiNavigateUtil.navigate(component.tag);
        }
      }
    }

    @Override
    public void mousePressed(@NonNull MouseEvent event) {
      if(event.getID() == MouseEvent.MOUSE_PRESSED){
        mySurface.getLayeredPane().requestFocusInWindow();
      }
      Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();

      myLastMouseX = event.getX();
      myLastMouseY = event.getY();
      myLastStateMask = event.getModifiersEx();

      // Not yet used. Should be, for Mac and Linux.
    }

    @Override
    public void mouseReleased(@NonNull MouseEvent event) {
      //ControlPoint mousePos = ControlPoint.create(mySurface, e);

      int x = event.getX();
      int y = event.getY();
      if (myCurrentInteraction == null) {
        // Just a click, select
        ScreenView screenView = mySurface.getScreenView(x, y);
        SelectionModel selectionModel = screenView.getSelectionModel();
        NlComponent component = Coordinates.findComponent(screenView, x, y);

        if (component == null) {
          // Clicked component resize handle?
          int mx = Coordinates.getAndroidX(screenView, x);
          int my = Coordinates.getAndroidY(screenView, y);
          int max = Coordinates.getAndroidDimension(screenView, PIXEL_RADIUS + PIXEL_MARGIN);
          SelectionHandle handle = selectionModel.findHandle(mx, my, max);
          if (handle != null) {
            component = handle.component;
          }
        }

        List<NlComponent> components = component != null ? Collections.singletonList(component) : Collections.<NlComponent>emptyList();
        screenView.getSelectionModel().setSelection(components);
        mySurface.repaint();
      }
      if (myCurrentInteraction == null) {
        updateCursor(x, y);
      } else if (myCurrentInteraction instanceof DropInteraction) {
        // Mouse Up shouldn't be delivered in the middle of a drag & drop -
        // but this can happen on some versions of Linux
        // (see http://code.google.com/p/android/issues/detail?id=19057 )
        // and if we process the mouseUp it will abort the remainder of
        // the drag & drop operation, so ignore this event!
      } else {
        finishInteraction(x, y, false);
      }
      mySurface.repaint();
    }

    @Override
    public void mouseEntered(@NonNull MouseEvent event) {
    }

    @Override
    public void mouseExited(@NonNull MouseEvent event) {
    }

    // --- Implements MouseMotionListener ----

    @Override
    public void mouseDragged(MouseEvent event) {
      int x = event.getX();
      int y = event.getY();
      if (myCurrentInteraction != null) {
        myCurrentInteraction.update(x, y);
      } else {
        int modifiers = event.getModifiersEx();
        boolean toggle = (modifiers & (InputEvent.SHIFT_MASK | InputEvent.CTRL_MASK)) != 0;
        ScreenView screenView = mySurface.getScreenView(x, y);
        NlComponent component = Coordinates.findComponent(screenView, x, y);
        Interaction interaction;
        if (component == null || component.isRoot()) {
          interaction = new MarqueeInteraction(screenView, toggle);
        } else {
          interaction = new MoveInteraction();
        }
        startInteraction(x, y, interaction, modifiers);
      }
    }

    @Override
    public void mouseMoved(MouseEvent event) {
      int x = event.getX();
      int y = event.getY();
      myLastMouseX = x;
      myLastMouseY = y;
      myLastStateMask = event.getModifiersEx();

      if ((myLastStateMask & InputEvent.BUTTON1_DOWN_MASK) != 0) {
        if (myCurrentInteraction != null) {
          updateMouse(x, y);
          mySurface.repaint();
        }
      } else {
        updateCursor(x, y);
      }
    }

    // --- Implements KeyListener ----

    @Override
    public void keyTyped(KeyEvent event) {
    }

    @Override
    public void keyPressed(KeyEvent event) {
      myLastStateMask = event.getModifiersEx();
      // Workaround for the fact that in keyPressed the current state
      // mask is not yet updated
      int keyCode = event.getKeyCode();
      char keyChar = event.getKeyChar();
      if (keyCode == KeyEvent.VK_SHIFT) {
        myLastStateMask |= InputEvent.SHIFT_MASK;
      }
      if (keyCode == KeyEvent.VK_META) {
        myLastStateMask |= InputEvent.META_MASK;
      }
      if (keyCode == KeyEvent.VK_CONTROL) {
        myLastStateMask |= InputEvent.CTRL_MASK;
      }

      // Give interactions a first chance to see and consume the key press
      if (myCurrentInteraction != null) {
        // unless it's "Escape", which cancels the interaction
        if (keyCode == KeyEvent.VK_ESCAPE) {
          finishInteraction(myLastMouseX, myLastMouseY, true);
          return;
        }

        if (myCurrentInteraction.keyPressed(event)) {
          return;
        }
      }

      // Fall back to canvas actions for the key press
      //mySurface.handleKeyPressed(e);

      if (keyChar == '1') {
        mySurface.zoomActual();
      } else if (keyChar == '+') {
        mySurface.zoomIn();
      } else if (keyChar == '-') {
        mySurface.zoomOut();
      } else if (keyChar == '0') {
        mySurface.zoomToFit();
      } else if (keyChar == 'd') {
        ScreenView screenView = mySurface.getScreenView(myLastMouseX, myLastMouseY);
        screenView.switchDevice();
      } else if (keyChar == 'o') {
        ScreenView screenView = mySurface.getScreenView(myLastMouseX, myLastMouseY);
        screenView.toggleOrientation();
      } else if (keyChar == 'f') {
        mySurface.toggleDeviceFrames();
      } else if (keyCode == KeyEvent.VK_DELETE || keyCode == KeyEvent.VK_BACK_SPACE) {
        ScreenView screenView = mySurface.getScreenView(myLastMouseX, myLastMouseY);
        SelectionModel model = screenView.getSelectionModel();
        if (!model.isEmpty()) {
          Iterable<NlComponent> selection = model.getSelection();
          screenView.getModel().delete(Lists.newArrayList(selection));
        }
      }
    }

    @Override
    public void keyReleased(KeyEvent event) {
      myLastStateMask = event.getModifiersEx();
      // Workaround for the fact that in keyPressed the current state
      // mask is not yet updated
      if (event.getKeyCode() == KeyEvent.VK_SHIFT) {
        myLastStateMask |= InputEvent.SHIFT_MASK;
      }
      if (event.getKeyCode() == KeyEvent.VK_META) {
        myLastStateMask |= InputEvent.META_MASK;
      }
      if (event.getKeyCode() == KeyEvent.VK_CONTROL) {
        myLastStateMask |= InputEvent.CTRL_MASK;
      }

      if (myCurrentInteraction != null) {
        myCurrentInteraction.keyReleased(event);
      }
    }

    // ---- Implements DropTargetListener ----

    @Override
    public void dragEnter(DropTargetDragEvent event) {
    }

    @Override
    public void dragOver(DropTargetDragEvent event) {
      Point location = event.getLocation();
      myLastMouseX = location.x;
      myLastMouseY = location.y;
      for (DataFlavor flavor : event.getCurrentDataFlavors()) {
        if (String.class == flavor.getRepresentationClass()) {
          event.acceptDrag(DnDConstants.ACTION_COPY);
          return;
        }
      }
      event.rejectDrag();
    }

    @Override
    public void dropActionChanged(DropTargetDragEvent event) {
    }

    @Override
    public void dragExit(DropTargetEvent event) {
    }

    @Override
    public void drop(DropTargetDropEvent event) {
      Point location = event.getLocation();
      ScreenView screenView = mySurface.getScreenView(location.x, location.y);

      // TODO: Use proper model APIs to do this
      try {
        Transferable transferable = event.getTransferable();
        if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
          event.acceptDrop(DnDConstants.ACTION_MOVE);
          final String xml = (String)transferable.getTransferData(DataFlavor.stringFlavor);
          final NlModel model = screenView.getModel();
          final Project project = model.getFacet().getModule().getProject();
          final XmlFile file = model.getFile();
          WriteCommandAction<Void> action = new WriteCommandAction<Void>(project, "Drop", file) {
            @Override
            protected void run(@NotNull Result<Void> result) throws Throwable {
              XmlElementFactory elementFactory = XmlElementFactory.getInstance(project);
              XmlTag tag;
              try {
                tag = elementFactory.createTagFromText(xml);
              }
              catch (IncorrectOperationException ignore) {
                // Thrown by XmlElementFactory if you try to parse non-valid XML. User might have tried
                // to drop something like plain text -- insert this as a text view instead.
                tag = elementFactory.createTagFromText("<TextView android:text=\"" + XmlUtils.toXmlAttributeValue(xml) + "\"" +
                                                       " android:layout_width=\"wrap_content\"" +
                                                       " android:layout_height=\"wrap_content\"" +
                                                       "/>");
              }
              XmlDocument document = file.getDocument();
              if (document != null) {
                XmlTag rootTag = document.getRootTag();
                if (rootTag != null) {
                  rootTag.addSubTag(tag, false);
                  model.requestRender();
                }
              }
            }
          };
          action.execute();
          event.getDropTargetContext().dropComplete(true); // or just event.dropComplete() ?
        }
        else {
          event.rejectDrop();
        }
      }
      catch (Exception ignore) {
        event.rejectDrop();
      }
    }
  }

  @VisibleForTesting
  public Object getListener() {
    return myListener;
  }
}
