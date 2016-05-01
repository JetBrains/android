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
package com.android.tools.idea.uibuilder.model;

import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.TagSnapshot;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.util.IdentityHashMap;
import java.util.Map;

final class PreferenceScreenModel {
  private final Map<XmlTag, ViewInfo> myTagToViewMap;
  private final NlModel myModel;

  /**
   * The root preference screen view. All preferences descend from this root. The root preference screen view is itself a descendant of one
   * of the root views from the render result.
   */
  private ViewInfo myRootView;
  private int myRootViewX;
  private int myRootViewY;

  /**
   * The NlComponent corresponding to the root preference screen.
   */
  private NlComponent myRootComponent;

  PreferenceScreenModel(@NotNull Iterable<ViewInfo> renderResultRootViews, @NotNull NlModel model) {
    initRootView(renderResultRootViews);

    myTagToViewMap = new IdentityHashMap<>();
    putDescendantsWithTags(myRootView);

    myModel = model;
    initRootComponent();
  }

  /**
   * Initializes the root preference screen view.
   *
   * @param renderResultRootViews the root views from the render result. The root preference screen is a descendant of one of these views.
   */
  private void initRootView(@NotNull Iterable<ViewInfo> renderResultRootViews) {
    for (ViewInfo renderResultRootView : renderResultRootViews) {
      findRootView(renderResultRootView, renderResultRootView.getLeft(), renderResultRootView.getTop());

      if (myRootView != null) {
        return;
      }
    }
  }

  /**
   * Finds the root preference screen view.
   */
  private void findRootView(@NotNull ViewInfo parent, int parentX, int parentY) {
    if (myRootView != null) {
      return;
    }

    for (ViewInfo child : parent.getChildren()) {
      int childX = parentX + child.getLeft();
      int childY = parentY + child.getTop();

      // TODO There should be a better way to do this
      if (child.toString().equals("android.widget.ListView")) {
        myRootView = child;
        myRootViewX = childX;
        myRootViewY = childY;

        return;
      }

      findRootView(child, childX, childY);
    }
  }

  private void putDescendantsWithTags(@NotNull ViewInfo parent) {
    for (ViewInfo child : parent.getChildren()) {
      Object cookie = child.getCookie();

      if (cookie instanceof XmlTag) {
        myTagToViewMap.put((XmlTag)cookie, child);
      }

      putDescendantsWithTags(child);
    }
  }

  private void initRootComponent() {
    XmlTag rootTag = myModel.getFile().getRootTag();

    if (rootTag == null) {
      myRootComponent = new NlComponent(myModel, EmptyXmlTag.INSTANCE);
    }
    else {
      // TODO Use the snapshot buried in the LayoutLib render
      TagSnapshot rootSnapshot = TagSnapshot.createTagSnapshot(rootTag);

      myRootComponent = new NlComponent(myModel, rootSnapshot);
      addDescendants(myRootComponent, rootSnapshot);
    }
  }

  private void addDescendants(@NotNull NlComponent parentComponent, @NotNull TagSnapshot parentSnapshot) {
    for (TagSnapshot childSnapshot : parentSnapshot.children) {
      ViewInfo childView = myTagToViewMap.get(childSnapshot.tag);
      ViewInfo bounds = RenderService.getSafeBounds(childView);

      int x = myRootViewX + bounds.getLeft();
      int y = myRootViewY + bounds.getTop();
      int width = bounds.getRight() - bounds.getLeft();
      int height = bounds.getBottom() - bounds.getTop();

      NlComponent childComponent = new NlComponent(myModel, childSnapshot);
      childComponent.setBounds(x, y, width, height);

      parentComponent.addChild(childComponent);
      addDescendants(childComponent, childSnapshot);
    }
  }

  @NotNull
  NlComponent getRootComponent() {
    return myRootComponent;
  }
}
