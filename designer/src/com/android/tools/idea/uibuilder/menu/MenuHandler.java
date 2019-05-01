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
package com.android.tools.idea.uibuilder.menu;

import com.android.SdkConstants;
import com.android.tools.idea.common.api.DragType;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.command.NlWriteCommandActionUtil;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.Placeholder;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.TemporarySceneComponent;
import com.android.tools.idea.common.scene.target.CommonDragTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.api.actions.ViewAction;
import com.android.tools.idea.uibuilder.handlers.linear.LinearPlaceholderFactory;
import com.google.common.collect.ImmutableList;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;

public class MenuHandler extends ViewGroupHandler {
  @Nullable
  @Override
  public DragHandler createDragHandler(@NotNull ViewEditor editor,
                                       @NotNull SceneComponent group,
                                       @NotNull List<NlComponent> items,
                                       @NotNull DragType type) {
    return new GroupDragHandler(editor, this, group, items, type);
  }

  @Override
  public void onChildInserted(@NotNull ViewEditor editor,
                              @NotNull NlComponent parent,
                              @NotNull NlComponent newChild,
                              @NotNull InsertType type) {
    if (SearchItemHandler.handles(newChild)) {
      SearchItemHandler.onChildInserted(editor);
    }
  }

  @Override
  public boolean onCreate(@NotNull ViewEditor editor,
                          @Nullable NlComponent parent,
                          @NotNull NlComponent newChild,
                          @NotNull InsertType type) {
    return NlWriteCommandActionUtil.compute(newChild, "Create Menu",
      () -> {
        newChild.removeAndroidAttribute(ATTR_LAYOUT_WIDTH);
        newChild.removeAndroidAttribute(ATTR_LAYOUT_HEIGHT);
        return true;
    });
  }

  @NotNull
  @Override
  public List<Target> createTargets(@NotNull SceneComponent sceneComponent) {
    return ImmutableList.of(new CommonDragTarget(sceneComponent));
  }

  @Override
  public List<Placeholder> getPlaceholders(@NotNull SceneComponent component) {
    ImmutableList.Builder<Placeholder> builder = new ImmutableList.Builder<>();
    if (component.getParent() == null && !component.getChildren().isEmpty()) {
      // The non-empty root <menu> tag has same behaviour as vertical LinearLayout.
      List<SceneComponent> nonActionItems = getNonActionItems(component);

      int bottomOfChildren = component.getDrawY();
      int left = component.getDrawX();
      int right = component.getDrawX() + component.getDrawWidth();
      for (SceneComponent item : nonActionItems) {
        builder.add(LinearPlaceholderFactory.createVerticalPlaceholder(component, item, item.getDrawY(), left, right));
        bottomOfChildren = Math.max(bottomOfChildren, item.getDrawY() + item.getDrawHeight());
      }
      builder.add(LinearPlaceholderFactory.createVerticalPlaceholder(component, null, bottomOfChildren, left, right));
    }
    // Add the Placeholder for root <menu>
    return builder.add(new MenuPlaceholder(component)).build();
  }

  /**
   * All items which have app:showAsAction="always" are displayed in Actionbar.<br>
   * For items which have app:showAsAction="ifRoom", there are only 2 slots for them.<br>
   * <br>
   * According the above rule, the filter logic is:
   * (1) Filter all item which have app:showAsAction="always"
   * (2) If there are less than 2 items are filtered, filter the first or first two items which have app:showAsAction="ifRoom".
   * (3) Return all remaining items.
   */
  private static List<SceneComponent> getNonActionItems(@NotNull SceneComponent menu) {
    List<SceneComponent> children = menu.getChildren();
    // Ignore TemporarySceneComponent, which happens when dragging from Palette.
    children = children.stream().filter(it -> !(it instanceof TemporarySceneComponent)).collect(Collectors.toList());
    List<SceneComponent> childrenWithoutActions = children.stream()
      .filter(it -> {
        String v = it.getAuthoritativeNlComponent().getAttribute(SdkConstants.AUTO_URI, SdkConstants.ATTR_SHOW_AS_ACTION);
        return !SdkConstants.VALUE_ALWAYS.equals(v);
      }).collect(Collectors.toList());

    int currentActionNumber = children.size() - childrenWithoutActions.size();

    while (currentActionNumber < 2) {
      Optional<SceneComponent> roomAction = childrenWithoutActions.stream().filter(it -> {
        String v = it.getAuthoritativeNlComponent().getAttribute(SdkConstants.AUTO_URI, SdkConstants.ATTR_SHOW_AS_ACTION);
        return SdkConstants.VALUE_IF_ROOM.equals(v);
      }).findFirst();

      if (roomAction.isPresent()) {
        childrenWithoutActions.remove(roomAction.get());
        currentActionNumber++;
      }
      else {
        break;
      }
    }
    return childrenWithoutActions;
  }

  @Override
  public void addToolbarActions(@NotNull List<ViewAction> actions) {
    // The default behaviour of a ViewHandler is to add the "Expand horizontally" and "Expand vertically" actions.
    // This does not make sense for state lists, so instead no action is added to the toolbar
  }
}
