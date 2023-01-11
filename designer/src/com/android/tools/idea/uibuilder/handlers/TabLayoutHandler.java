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
package com.android.tools.idea.uibuilder.handlers;

import static com.android.AndroidXConstants.TAB_ITEM;
import static com.android.SdkConstants.ANDROID_MATERIAL_PKG;
import static com.android.SdkConstants.ATTR_BACKGROUND;
import static com.android.SdkConstants.ATTR_STYLE;
import static com.android.SdkConstants.ATTR_TAB_BACKGROUND;
import static com.android.SdkConstants.ATTR_TAB_CONTENT_START;
import static com.android.SdkConstants.ATTR_TAB_GRAVITY;
import static com.android.SdkConstants.ATTR_TAB_ICON_TINT;
import static com.android.SdkConstants.ATTR_TAB_ICON_TINT_MODE;
import static com.android.SdkConstants.ATTR_TAB_INDICATOR;
import static com.android.SdkConstants.ATTR_TAB_INDICATOR_ANIMATION_DURATION;
import static com.android.SdkConstants.ATTR_TAB_INDICATOR_COLOR;
import static com.android.SdkConstants.ATTR_TAB_INDICATOR_FULL_WIDTH;
import static com.android.SdkConstants.ATTR_TAB_INDICATOR_GRAVITY;
import static com.android.SdkConstants.ATTR_TAB_INDICATOR_HEIGHT;
import static com.android.SdkConstants.ATTR_TAB_INLINE_LABEL;
import static com.android.SdkConstants.ATTR_TAB_MAX_WIDTH;
import static com.android.SdkConstants.ATTR_TAB_MIN_WIDTH;
import static com.android.SdkConstants.ATTR_TAB_MODE;
import static com.android.SdkConstants.ATTR_TAB_PADDING;
import static com.android.SdkConstants.ATTR_TAB_PADDING_BOTTOM;
import static com.android.SdkConstants.ATTR_TAB_PADDING_END;
import static com.android.SdkConstants.ATTR_TAB_PADDING_START;
import static com.android.SdkConstants.ATTR_TAB_PADDING_TOP;
import static com.android.SdkConstants.ATTR_TAB_RIPPLE_COLOR;
import static com.android.SdkConstants.ATTR_TAB_SELECTED_TEXT_COLOR;
import static com.android.SdkConstants.ATTR_TAB_TEXT_APPEARANCE;
import static com.android.SdkConstants.ATTR_TAB_TEXT_COLOR;
import static com.android.SdkConstants.ATTR_TAB_UNBOUNDED_RIPPLE;
import static com.android.SdkConstants.ATTR_TEXT;
import static com.android.SdkConstants.ATTR_THEME;
import static com.android.SdkConstants.VALUE_MATCH_PARENT;
import static com.android.SdkConstants.VALUE_WRAP_CONTENT;

import com.android.tools.idea.common.api.DragType;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.Placeholder;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.TemporarySceneComponent;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.uibuilder.api.DragHandler;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.tools.idea.uibuilder.handlers.common.ViewGroupPlaceholder;
import com.android.tools.idea.uibuilder.handlers.frame.FrameDragHandler;
import com.android.tools.idea.uibuilder.handlers.frame.FrameDragTarget;
import com.android.xml.XmlBuilder;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TabLayoutHandler extends HorizontalScrollViewHandler {
  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_STYLE,
      ATTR_TAB_INDICATOR_COLOR,
      ATTR_TAB_INDICATOR_HEIGHT,
      ATTR_TAB_CONTENT_START,
      ATTR_TAB_BACKGROUND,
      ATTR_TAB_INDICATOR,
      ATTR_TAB_INDICATOR_GRAVITY,
      ATTR_TAB_INDICATOR_ANIMATION_DURATION,
      ATTR_TAB_INDICATOR_FULL_WIDTH,
      ATTR_TAB_MODE,
      ATTR_TAB_GRAVITY,
      ATTR_TAB_INLINE_LABEL,
      ATTR_TAB_MIN_WIDTH,
      ATTR_TAB_MAX_WIDTH,
      ATTR_TAB_TEXT_APPEARANCE,
      ATTR_TAB_TEXT_COLOR,
      ATTR_TAB_SELECTED_TEXT_COLOR,
      ATTR_TAB_PADDING,
      ATTR_TAB_PADDING_START,
      ATTR_TAB_PADDING_END,
      ATTR_TAB_PADDING_TOP,
      ATTR_TAB_PADDING_BOTTOM,
      ATTR_TAB_ICON_TINT,
      ATTR_TAB_ICON_TINT_MODE,
      ATTR_TAB_RIPPLE_COLOR,
      ATTR_TAB_UNBOUNDED_RIPPLE,
      ATTR_THEME,
      ATTR_BACKGROUND);
  }

  @Override
  @NotNull
  @Language("XML")
  public String getXml(@NotNull String tagName, @NotNull XmlType xmlType) {
    String tabItem = tagName.startsWith(ANDROID_MATERIAL_PKG) ? TAB_ITEM.newName() : TAB_ITEM.oldName();
    return new XmlBuilder()
      .startTag(tagName)
      .withSize(VALUE_MATCH_PARENT, VALUE_WRAP_CONTENT)
      .startTag(tabItem)
      .wrapContent()
      .androidAttribute(ATTR_TEXT, "Monday")
      .endTag(tabItem)
      .startTag(tabItem)
      .wrapContent()
      .androidAttribute(ATTR_TEXT, "Tuesday")
      .endTag(tabItem)
      .startTag(tabItem)
      .wrapContent()
      .androidAttribute(ATTR_TEXT, "Wednesday")
      .endTag(tabItem)
      .endTag(tagName)
      .toString();
  }

  @Override
  public boolean onCreate(@Nullable NlComponent parent,
                          @NotNull NlComponent node,
                          @NotNull InsertType insertType) {
    // Hide the implementation from HorizontalScrollViewHandler
    return true;
  }

  @Override
  public boolean acceptsChild(@NotNull NlComponent layout,
                              @NotNull NlComponent newChild) {
    return isTabItem(newChild);
  }

  private static boolean isTabItem(@NotNull NlComponent child) {
    return TAB_ITEM.isEquals(child.getTagName());
  }

  @Override
  public void onChildInserted(@NotNull NlComponent layout,
                              @NotNull NlComponent newChild,
                              @NotNull InsertType insertType) {
    if (newChild.getAndroidAttribute(ATTR_TEXT) == null) {
      newChild.setAndroidAttribute(ATTR_TEXT, "Tab" + (layout.getChildren().size() + 1));
    }
  }

  @Nullable
  @Override
  public DragHandler createDragHandler(@NotNull ViewEditor editor,
                                       @NotNull SceneComponent layout,
                                       @NotNull List<NlComponent> components,
                                       @NotNull DragType type) {
    return super.createDragHandler(editor, layout, components, type);
  }

  @Override
  public @NotNull List<Target> createChildTargets(@NotNull SceneComponent parentComponent,
                                                  @NotNull SceneComponent childComponent) {
    return ImmutableList.of(new FrameDragTarget());
  }

  @Override
  public List<Placeholder> getPlaceholders(@NotNull SceneComponent component, @NotNull List<SceneComponent> draggedComponents) {
    if (!draggedComponents.stream().allMatch(it -> isTabItem(it.getNlComponent()))) {
      // To drag into TabLayout, all dragged components must be TAB_ITEM.
      return Collections.emptyList();
    }

    List<Placeholder> retList = new ArrayList<>();
    List<SceneComponent> children = component.getChildren().stream()
      .filter(it -> !(it instanceof TemporarySceneComponent))
      .collect(Collectors.toList());

    if (children.size() == 0) {
      retList.add(new ViewGroupPlaceholder(component));
    }
    else {
      for (SceneComponent child : children) {
        retList.add(new TabLayoutPlaceholder(component, child));
      }
      retList.add(new TabLayoutPlaceholder(component, null));
    }
    return retList;
  }
}
