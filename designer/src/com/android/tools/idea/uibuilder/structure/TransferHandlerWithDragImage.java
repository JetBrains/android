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
package com.android.tools.idea.uibuilder.structure;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.awt.event.InputEvent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

/**
 * This class will override the DragHandler to support a drag image.
 * This class can be removed when JDK1.6 is no longer supported since this functionality
 * is already in JDK1.7 of {link TransferHandler}.
 */
public class TransferHandlerWithDragImage extends TransferHandler {
  private static final Logger LOG = Logger.getInstance(TransferHandlerWithDragImage.class);

  /**
   * Image for the {@link DragGestureEvent#startDrag} method
   *
   * @see DragGestureEvent#startDrag(Cursor dragCursor, Image dragImage, Point imageOffset, Transferable transferable, DragSourceListener dsl)
   */
  private Image dragImage;

  /**
   * Anchor offset for the {@link DragGestureEvent#startDrag} method
   *
   * @see DragGestureEvent#startDrag(Cursor dragCursor, Image dragImage, Point imageOffset, Transferable transferable, DragSourceListener dsl)
   */
  private Point dragImageOffset;

  /**
   * Sets the drag image parameter. The image has to be prepared
   * for rendering by the moment of the call. The image is stored
   * by reference because of some performance reasons.
   *
   * @param img an image to drag
   */
  public void setDragImage(@Nullable Image img) {
    dragImage = img;
  }

  /**
   * Returns the drag image. If there is no image to drag,
   * the returned value is {@code null}.
   *
   * @return the reference to the drag image
   */
  @Nullable
  public Image getDragImage() {
    return dragImage;
  }

  /**
   * Sets an anchor offset for the image to drag.
   * It can not be {@code null}.
   *
   * @param p a {@code Point} object that corresponds
   * to coordinates of an anchor offset of the image
   * relative to the upper left corner of the image
   */
  public void setDragImageOffset(@NotNull Point p) {
    dragImageOffset = new Point(p);
  }

  /**
   * Returns an anchor offset for the image to drag.
   *
   * @return a {@code Point} object that corresponds
   * to coordinates of an anchor offset of the image
   * relative to the upper left corner of the image.
   * The point {@code (0,0)} returns by default.
   */
  @NotNull
  public Point getDragImageOffset() {
    if (dragImageOffset == null) {
      return new Point(0,0);
    }
    return new Point(dragImageOffset);
  }

  @Override
  public void exportAsDrag(JComponent comp, InputEvent e, int action) {
    if (isBeforeJava7()) {
      provideNewDragHandler();
    }
    super.exportAsDrag(comp, e, action);
  }

  private static boolean isBeforeJava7() {
    return StringUtil.compareVersionNumbers(SystemInfo.JAVA_VERSION, "1.7") < 0;
  }

  private void provideNewDragHandler() {
    try {
      // Hack to override the recognizer field in Transferhandler:
      Field recognizer = TransferHandler.class.getDeclaredField("recognizer");
      Class<?> gestureRecognizerClass = getDeclaredClass("SwingDragGestureRecognizer");
      Class<?> dragHandlerClass = getDeclaredClass("DragHandler");
      recognizer.setAccessible(true);
      Object value = recognizer.get(this);
      if (value == null && gestureRecognizerClass != null && dragHandlerClass != null) {
        Constructor<?> dragHandlerConstructor = dragHandlerClass.getDeclaredConstructor();
        dragHandlerConstructor.setAccessible(true);
        Object defaultDragHandler = dragHandlerConstructor.newInstance();
        Constructor<?> gestureConstructor = gestureRecognizerClass.getDeclaredConstructor(DragGestureListener.class);
        gestureConstructor.setAccessible(true);
        Object instance = gestureConstructor.newInstance(new DragHandler(defaultDragHandler));
        recognizer.set(this, instance);
      }
    }
    catch (NoSuchFieldException ex) {
      LOG.info(ex);
    }
    catch (IllegalAccessException ex) {
      LOG.info(ex);
    }
    catch (NoSuchMethodException ex) {
      LOG.info(ex);
    }
    catch (InstantiationException ex) {
      LOG.info(ex);
    }
    catch (InvocationTargetException ex) {
      LOG.info(ex);
    }
  }

  @Nullable
  private static Class<?> getDeclaredClass(@NotNull String name) {
    for (Class<?> klass : TransferHandler.class.getDeclaredClasses()) {
      if (klass.getName().endsWith(name)) {
        return klass;
      }
    }
    return null;
  }

  private static class DragHandler implements DragGestureListener, DragSourceListener {
    private boolean scrolls;
    private DragGestureListener myDragGestureListener;
    private DragSourceListener myDragSourceListener;

    private DragHandler(Object delegate) {
      this.myDragGestureListener = (DragGestureListener)delegate;
      this.myDragSourceListener = (DragSourceListener)delegate;
    }

    // --- DragGestureListener methods -----------------------------------

    /**
     * a Drag gesture has been recognized
     */
    @Override
    public void dragGestureRecognized(DragGestureEvent dge) {
      JComponent c = (JComponent) dge.getComponent();
      TransferHandler th = c.getTransferHandler();
      if (th instanceof TransferHandlerWithDragImage) {
        TransferHandlerWithDragImage thid = (TransferHandlerWithDragImage)th;
        Transferable t = thid.createTransferable(c);
        if (t != null) {
          scrolls = c.getAutoscrolls();
          c.setAutoscrolls(false);
          try {
            Image im = thid.getDragImage();
            if (im == null) {
              dge.startDrag(null, t, this);
            } else {
              dge.startDrag(null, im, thid.getDragImageOffset(), t, this);
            }
            return;
          } catch (RuntimeException re) {
            c.setAutoscrolls(scrolls);
          }
        }
        thid.exportDone(c, t, NONE);
      } else {
        myDragGestureListener.dragGestureRecognized(dge);
      }
    }

    // --- DragSourceListener methods -----------------------------------

    /**
     * as the hotspot enters a platform dependent drop site
     */
    @Override
    public void dragEnter(DragSourceDragEvent dsde) {
    }

    /**
     * as the hotspot moves over a platform dependent drop site
     */
    @Override
    public void dragOver(DragSourceDragEvent dsde) {
    }

    /**
     * as the hotspot exits a platform dependent drop site
     */
    @Override
    public void dragExit(DragSourceEvent dsde) {
    }

    /**
     * as the operation completes
     */
    @Override
    public void dragDropEnd(DragSourceDropEvent dsde) {
      DragSourceContext dsc = dsde.getDragSourceContext();
      JComponent c = (JComponent)dsc.getComponent();
      TransferHandler th = c.getTransferHandler();
      if (th instanceof TransferHandlerWithDragImage) {
        TransferHandlerWithDragImage thid = (TransferHandlerWithDragImage)th;
        if (dsde.getDropSuccess()) {
          thid.exportDone(c, dsc.getTransferable(), dsde.getDropAction());
        }
        else {
          thid.exportDone(c, dsc.getTransferable(), NONE);
        }
        c.setAutoscrolls(scrolls);
      } else {
        myDragSourceListener.dragDropEnd(dsde);
      }
    }

    @Override
    public void dropActionChanged(DragSourceDragEvent dsde) {
    }
  }
}
