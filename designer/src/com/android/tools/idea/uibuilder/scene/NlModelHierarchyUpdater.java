/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.scene;

import android.view.ViewGroup;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.type.DesignerEditorFileType;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.parsers.PsiXmlTag;
import com.android.tools.idea.rendering.parsers.TagSnapshot;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.type.MenuFileType;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import java.awt.Rectangle;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class for updating NlModel hierarchy from various sources
 */
public class NlModelHierarchyUpdater {

  @AndroidCoordinate private static final int VISUAL_EMPTY_COMPONENT_SIZE = 1;

  /**
   * Update the hierarchy based on the render/inflate result.
   * @param result result after inflation. Must contain a valid ViewInfo.
   * @param model to be updated.
   * @return whether update in component hierarchy caused a reverse update in the view hierarchy
   */
  public static boolean updateHierarchy(@NotNull RenderResult result,
                                     @NotNull NlModel model) {
    return updateHierarchy(getRootViews(result, model.getType()), model);
  }

  /**
   * Update the hierarchy based on the inflated rootViews.
   * @param views list of views inflated that matches model file
   * @param model to be updated
   * @return whether update in component hierarchy caused a reverse update in the view hierarchy
   */
  public static boolean updateHierarchy(@NotNull List<ViewInfo> views, @NotNull NlModel model) {
    XmlTag root = getRootTag(model);
    if (root != null) {
      return updateHierarchy(root, views, model);
    }
    return false;
  }

  /**
   * Update the hierarchy based on the inflated rootViews.
   * @param rootTag xml tag of the root view from PsiFile (from model)
   * @param views list of views inflated that matches model file
   * @param model to be updated
   * @return whether update in component hierarchy caused a reverse update in the view hierarchy
   */
  public static boolean updateHierarchy(@NotNull XmlTag rootTag, @NotNull List<ViewInfo> views, @NotNull NlModel model) {
    model.syncWithPsi(rootTag, ContainerUtil.map(views, ViewInfoTagSnapshotNode::new));
    updateBounds(views, model);
    ImmutableList<NlComponent> components = model.getComponents();
    if (!components.isEmpty()) {
      return updateScroll(components.get(0));
    }
    return false;
  }

  /**
   * Returns the root views from result based on file type.
   */
  @NotNull
  public static List<ViewInfo> getRootViews(@NotNull RenderResult result, @NotNull DesignerEditorFileType type) {
    return type == MenuFileType.INSTANCE ? result.getSystemRootViews() : result.getRootViews();
  }

  /**
   * Get the root tag of the xml file associated with the specified model.
   * Since this code may be called on a non UI thread be extra careful about expired objects.
   */
  @Nullable
  private static XmlTag getRootTag(@NotNull NlModel model) {
    if (Disposer.isDisposed(model)) {
      return null;
    }
    return AndroidPsiUtils.getRootTagSafely(model.getFile());
  }

  // TODO: we shouldn't be going back in and modifying NlComponents here
  private static void updateBounds(@NotNull List<ViewInfo> rootViews, @NotNull NlModel model) {
    model.flattenComponents().forEach(NlModelHierarchyUpdater::clearDerivedData);
    Map<TagSnapshot, NlComponent> snapshotToComponent =
      model.flattenComponents().collect(Collectors.toMap(NlComponent::getSnapshot, Function.identity(), (n1, n2) -> n1));
    Map<XmlTag, NlComponent> tagToComponent =
      model.flattenComponents().collect(Collectors.toMap(NlComponent::getTagDeprecated, Function.identity()));

    // Update the bounds. This is based on the ViewInfo instances.
    for (ViewInfo view : rootViews) {
      updateBounds(view, 0, 0, snapshotToComponent, tagToComponent);
    }

    ImmutableList<NlComponent> components = model.getComponents();
    if (!rootViews.isEmpty() && !components.isEmpty()) {
      // Finally, fix up bounds: ensure that all components not found in the view
      // info hierarchy inherit position from parent
      fixBounds(components.get(0));
    }
  }

  /**
   * Update the scroll in the View hierarchy from the saved scroll in the components' hierarchy. Returns whether there was any scroll
   * update required or not.
   */
  private static boolean updateScroll(@NotNull NlComponent component) {
    boolean scrollHasChanged = false;
    ViewInfo viewInfo = NlComponentHelperKt.getViewInfo(component);
    Object viewObject = viewInfo != null ? viewInfo.getViewObject() : null;

    if (viewObject instanceof ViewGroup) {
      ViewGroup viewGroup = (ViewGroup)viewObject;
      int savedScrollX = NlComponentHelperKt.getScrollX(component);
      int savedScrollY = NlComponentHelperKt.getScrollY(component);
      if (savedScrollX != viewGroup.getScrollX() || savedScrollY != viewGroup.getScrollY()) {
        scrollHasChanged = true;
        viewGroup.setScrollX(savedScrollX);
        viewGroup.setScrollY(savedScrollY);
      }
    }

    List<NlComponent> children = component.getChildren();
    for (NlComponent child : children) {
      scrollHasChanged = scrollHasChanged || updateScroll(child);
    }
    return scrollHasChanged;
  }

  private static void updateBounds(@NotNull ViewInfo view,
                                   @AndroidCoordinate int parentX,
                                   @AndroidCoordinate int parentY,
                                   Map<TagSnapshot, NlComponent> snapshotToComponent,
                                   Map<XmlTag, NlComponent> tagToComponent) {
    ViewInfo bounds = RenderService.getSafeBounds(view);
    Object cookie = view.getCookie();
    NlComponent component;
    if (cookie != null) {
      if (cookie instanceof TagSnapshot) {
        TagSnapshot snapshot = (TagSnapshot)cookie;
        component = snapshotToComponent.get(snapshot);
        if (component == null) {
          PsiXmlTag psiXmlTag = (PsiXmlTag)snapshot.tag;
          component = tagToComponent.get(psiXmlTag != null ? psiXmlTag.getPsiXmlTag() : null);
        }
        if (component != null && NlComponentHelperKt.getViewInfo(component) == null) {
          NlComponentHelperKt.setViewInfo(component, view);

          int left = parentX + bounds.getLeft();
          int top = parentY + bounds.getTop();
          int width = bounds.getRight() - bounds.getLeft();
          int height = bounds.getBottom() - bounds.getTop();

          NlComponentHelperKt.setBounds(component, left, top, Math.max(width, VISUAL_EMPTY_COMPONENT_SIZE),
                                        Math.max(height, VISUAL_EMPTY_COMPONENT_SIZE));
        }
      }
    }
    parentX += bounds.getLeft();
    parentY += bounds.getTop();

    for (ViewInfo child : view.getChildren()) {
      updateBounds(child, parentX, parentY, snapshotToComponent, tagToComponent);
    }
  }

  private static void fixBounds(@NotNull NlComponent root) {
    boolean computeBounds = false;
    if (NlComponentHelperKt.getW(root) == -1 && NlComponentHelperKt.getH(root) == -1) { // -1: not initialized
      computeBounds = true;

      // Look at parent instead
      NlComponent parent = root.getParent();
      if (parent != null && NlComponentHelperKt.getW(parent) >= 0) {
        NlComponentHelperKt.setBounds(root, NlComponentHelperKt.getX(parent), NlComponentHelperKt.getY(parent), 0, 0);
      }
    }

    List<NlComponent> children = root.getChildren();
    if (!children.isEmpty()) {
      for (NlComponent child : children) {
        fixBounds(child);
      }

      if (computeBounds) {
        Rectangle rectangle = new Rectangle(NlComponentHelperKt.getX(root), NlComponentHelperKt.getY(root), NlComponentHelperKt.getW(root),
                                            NlComponentHelperKt.getH(root));
        // Grow bounds to include child bounds
        for (NlComponent child : children) {
          rectangle = rectangle.union(new Rectangle(NlComponentHelperKt.getX(child), NlComponentHelperKt.getY(child),
                                                    NlComponentHelperKt.getW(child), NlComponentHelperKt.getH(child)));
        }

        NlComponentHelperKt.setBounds(root, rectangle.x, rectangle.y, rectangle.width, rectangle.height);
      }
    }
  }

  private static void clearDerivedData(@NotNull NlComponent component) {
    NlComponentHelperKt.setBounds(component, 0, 0, -1, -1); // -1: not initialized
    NlComponentHelperKt.setViewInfo(component, null);
  }
}
