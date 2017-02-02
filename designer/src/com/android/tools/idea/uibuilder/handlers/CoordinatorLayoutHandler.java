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
package com.android.tools.idea.uibuilder.handlers;

import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.model.AndroidDpCoordinate;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.android.tools.idea.uibuilder.api.DragHandler;
import com.android.tools.idea.uibuilder.api.DragType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.graphics.NlDrawingStyle;
import com.android.tools.idea.uibuilder.graphics.NlGraphics;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.Insets;
import com.android.tools.idea.uibuilder.model.NlComponent;

import java.util.List;

import static com.android.SdkConstants.*;

/**
 * Handler for the {@code <android.support.design.widget.CoordinatorLayout>} layout
 */
public class CoordinatorLayoutHandler extends FrameLayoutHandler {
  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_CONTEXT,
      ATTR_FITS_SYSTEM_WINDOWS);
  }

  @Nullable
  @Override
  public DragHandler createDragHandler(@NotNull ViewEditor editor,
                                       @NotNull SceneComponent layout,
                                       @NotNull List<SceneComponent> components,
                                       @NotNull DragType type) {
    // The {@link CoordinatorDragHandler} handles the logic for anchoring a
    // single component to an existing component in the CoordinatorLayout.
    // If we are moving several components we probably don't want them to be
    // anchored to the same place, so instead we use the FrameLayoutHandler in
    // this case.
    if (components.size() == 1 && components.get(0) != null) {
      return new CoordinatorDragHandler(editor, layout, components, type);
    } else {
      return super.createDragHandler(editor, layout, components, type);
    }
  }

  private class CoordinatorDragHandler extends FrameDragHandler {
    private SceneComponent myAnchor;
    private SceneComponent myDragged;
    private NlComponent myDraggedNlComponent;
    private String myAnchorGravity;
    private String myGravity;
    @AndroidDpCoordinate
    private int myPreviewX;
    @AndroidDpCoordinate
    private int myPreviewY;

    public CoordinatorDragHandler(@NotNull ViewEditor editor,
                                  @NotNull SceneComponent layout,
                                  @NotNull List<SceneComponent> components,
                                  @NotNull DragType type) {
      super(editor, CoordinatorLayoutHandler.this, layout, components, type);
      assert components.size() == 1;
      myDragged = components.get(0);
      myDraggedNlComponent = myDragged.getNlComponent();
      assert myDragged != null;
    }

    @Override
    public void start(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, int modifiers) {
      super.start(x, y, modifiers);
      checkPosition();
    }

    @Nullable
    @Override
    public String update(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y, int modifiers) {
      String result = super.update(x, y, modifiers);
      checkPosition();
      return result;
    }

    @Override
    public void commit(@AndroidCoordinate int x, @AndroidCoordinate int y, int modifiers, @NotNull InsertType insertType) {
      checkPosition();
      if (myAnchor == null) {
        myDraggedNlComponent.setAttribute(AUTO_URI, ATTR_LAYOUT_ANCHOR, null);
        myDraggedNlComponent.setAttribute(AUTO_URI, ATTR_LAYOUT_ANCHOR_GRAVITY, null);
      } else {
        NlComponent root = myDraggedNlComponent.getRoot();
        root.ensureNamespace(APP_PREFIX, AUTO_URI);
        root.ensureNamespace(ANDROID_NS_NAME, ANDROID_URI);
        myAnchor.getNlComponent().ensureId();
        String id = myAnchor.getNlComponent().getAttribute(ANDROID_URI, ATTR_ID);
        myDraggedNlComponent.setAttribute(AUTO_URI, ATTR_LAYOUT_ANCHOR, id);
        myDraggedNlComponent.setAttribute(AUTO_URI, ATTR_LAYOUT_ANCHOR_GRAVITY, myAnchorGravity);
        myDraggedNlComponent.setAttribute(ANDROID_URI, ATTR_LAYOUT_GRAVITY, myGravity);
      }
      insertComponents(-1, insertType);
    }

    @Override
    public void paint(@NotNull NlGraphics gc) {
      if (myAnchor == null) {
        super.paint(gc);
      } else {
        Insets padding = myAnchor.getNlComponent().getPadding();
        @AndroidCoordinate int anchorX = editor.dpToPx(myDragged.getDrawX());
        @AndroidCoordinate int anchorY = editor.dpToPx(myDragged.getDrawY());
        @AndroidCoordinate int anchorW = editor.dpToPx(myDragged.getDrawWidth());
        @AndroidCoordinate int anchorH = editor.dpToPx(myDragged.getDrawHeight());

        // Highlight the anchor
        gc.useStyle(NlDrawingStyle.DROP_RECIPIENT);
        gc.drawRect(anchorX + padding.left, anchorY + padding.top, anchorW + padding.width(), anchorH + padding.height());

        gc.useStyle(NlDrawingStyle.DROP_ZONE);
        @AndroidCoordinate int draggedW = editor.dpToPx(myDragged.getDrawWidth());
        @AndroidCoordinate int draggedH = editor.dpToPx(myDragged.getDrawHeight());

        gc.drawRect(anchorX - draggedW, anchorY - draggedH, draggedW * 2, draggedH * 2);
        gc.drawRect(anchorX + anchorW - draggedW, anchorY - draggedH, draggedW * 2, draggedH * 2);
        gc.drawRect(anchorX - draggedW, anchorY + anchorH - draggedH, draggedW * 2, draggedH * 2);
        gc.drawRect(anchorX + anchorW - draggedW, anchorY + anchorH - draggedH, draggedW * 2, draggedH * 2);
        if (anchorW > 4 * draggedW) {
          gc.drawRect(anchorX + anchorW / 2 - draggedW, anchorY - draggedH, draggedW * 2, draggedH * 2);
          gc.drawRect(anchorX + anchorW / 2 - draggedW, anchorY + anchorH - draggedH, draggedW * 2, draggedH * 2);
        }
        if (anchorH > 4 * draggedH) {
          gc.drawRect(anchorX - draggedW, anchorY + anchorH / 2 - draggedH, draggedW * 2, draggedH * 2);
          gc.drawRect(anchorX + anchorW - draggedW, anchorY + anchorH / 2 - draggedH, draggedW * 2, draggedH * 2);
        }
        if (anchorW > 4 * draggedW && anchorH > 4 * draggedH) {
          gc.drawRect(anchorX + anchorW / 2 - draggedW, anchorY + anchorH / 2 - draggedH, draggedW * 2, draggedH * 2);
        }
        if (myAnchorGravity != null) {
          gc.useStyle(NlDrawingStyle.DROP_PREVIEW);
          gc.drawRect(myPreviewX, myPreviewY, draggedW, draggedH);
        }
      }
    }

    private void checkPosition() {
      myAnchor = findAnchor();
      myAnchorGravity = null;
      myGravity = null;
      myPreviewX = -1;
      myPreviewY = -1;

      if (myAnchor != null) {
        String anchorHgrav = null;
        String anchorVgrav = null;
        String selfHgrav = null;
        String selfVgrav = null;
        @AndroidDpCoordinate int left = -1;
        @AndroidDpCoordinate int top = -1;
        @AndroidDpCoordinate int x = -1;
        @AndroidDpCoordinate int y = -1;

        if (lastX < myAnchor.getDrawX() + myDragged.getDrawWidth()) {
          anchorHgrav = GRAVITY_VALUE_LEFT;
          left = myAnchor.getDrawX() - myDragged.getDrawWidth();
          x = lastX - myAnchor.getDrawX();
        } else if (lastX >= myAnchor.getDrawX() + myAnchor.getDrawWidth() - myDragged.getDrawWidth()) {
          anchorHgrav = GRAVITY_VALUE_RIGHT;
          left = myAnchor.getDrawX() + myAnchor.getDrawWidth() - myDragged.getDrawWidth();
          x = lastX - (myAnchor.getDrawX() + myAnchor.getDrawWidth() - myDragged.getDrawWidth());
        } else if (myAnchor.getDrawWidth() > 4 * myDragged.getDrawWidth() &&
                   myAnchor.getDrawX() + myAnchor.getDrawWidth() / 2 - myDragged.getDrawWidth() <= lastX &&
                   lastX < myAnchor.getDrawX() + myAnchor.getDrawWidth() / 2 + myDragged.getDrawWidth()) {
          anchorHgrav = GRAVITY_VALUE_CENTER_HORIZONTAL;
          left = myAnchor.getDrawX() + myAnchor.getDrawWidth() / 2 - myDragged.getDrawWidth();
          x = (lastX - (myAnchor.getDrawX() + myAnchor.getDrawWidth() / 2 - myDragged.getDrawWidth())) / 2;
        }
        if (anchorHgrav != null) {
          if (x < myDragged.getDrawWidth() / 3) {
            selfHgrav = GRAVITY_VALUE_LEFT;
          } else if (x < 2 * myDragged.getDrawWidth() / 3) {
            selfHgrav = GRAVITY_VALUE_CENTER_HORIZONTAL;
            left += myDragged.getDrawWidth() / 2;
          } else {
            selfHgrav = GRAVITY_VALUE_RIGHT;
            left += myDragged.getDrawWidth();
          }
        }

        if (lastY < myAnchor.getDrawY() + myDragged.getDrawHeight()) {
          anchorVgrav = GRAVITY_VALUE_TOP;
          top = myAnchor.getDrawY() - myDragged.getDrawHeight();
          y = lastY - myAnchor.getDrawY();
        } else if (lastY >= myAnchor.getDrawY() + myAnchor.getDrawHeight() - myDragged.getDrawHeight()) {
          anchorVgrav = GRAVITY_VALUE_BOTTOM;
          top = myAnchor.getDrawY() + myAnchor.getDrawHeight() - myDragged.getDrawHeight();
          y = lastY - (myAnchor.getDrawY() + myAnchor.getDrawHeight() - myDragged.getDrawHeight());
        } else if (myAnchor.getDrawHeight() > 4 * myDragged.getDrawHeight() &&
                   myAnchor.getDrawY() + myAnchor.getDrawHeight() / 2 - myDragged.getDrawHeight() <= lastY &&
                   lastY < myAnchor.getDrawY() + myAnchor.getDrawHeight() / 2 + myDragged.getDrawHeight()) {
          anchorVgrav = GRAVITY_VALUE_CENTER_VERTICAL;
          top = myAnchor.getDrawY() + myAnchor.getDrawHeight() / 2 - myDragged.getDrawHeight();
          y = (lastY - (myAnchor.getDrawY() + myAnchor.getDrawHeight() / 2 - myDragged.getDrawHeight())) / 2;
        }
        if (anchorVgrav != null) {
          if (y < myDragged.getDrawHeight() / 3) {
            selfVgrav = GRAVITY_VALUE_TOP;
          } else if (y < 2 * myDragged.getDrawHeight() / 3) {
            selfVgrav = GRAVITY_VALUE_CENTER_VERTICAL;
            top += myDragged.getDrawHeight() / 2;
          } else {
            selfVgrav = GRAVITY_VALUE_BOTTOM;
            top += myDragged.getDrawHeight();
          }
        }

        if (anchorHgrav != null && anchorVgrav != null) {
          myAnchorGravity = anchorVgrav + "|" + anchorHgrav;
          myGravity = selfVgrav + "|" + selfHgrav;
          myPreviewX = left;
          myPreviewY = top;
        }
      }
    }

    @Nullable
    SceneComponent findAnchor() {
      for (int i = layout.getChildCount() - 1; i >= 0; i--) {
        SceneComponent component = layout.getChild(i);
        assert component != null;
        if (component.getDrawX() < lastX && lastX < component.getDrawX() + component.getDrawWidth() &&
            component.getDrawY() < lastY && lastY < component.getDrawY() + component.getDrawHeight() &&
            component.getDrawWidth() > myDragged.getDrawWidth() * 3 && component.getDrawHeight() > myDragged.getDrawHeight() * 3) {
          return component;
        }
      }
      return null;
    }
  }
}
