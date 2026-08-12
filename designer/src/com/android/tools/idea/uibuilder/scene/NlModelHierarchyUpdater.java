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
import android.view.accessibility.AccessibilityNodeInfo;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.sdklib.AndroidCoordinate;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.type.DesignerEditorFileType;
import com.android.tools.idea.rendering.parsers.PsiXmlTag;
import com.android.tools.rendering.RenderResult;
import com.android.tools.rendering.RenderService;
import com.android.tools.rendering.parsers.TagSnapshot;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.type.MenuFileType;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.HashMap;
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
   */
  public static void updateHierarchy(@NotNull RenderResult result,
                                     @NotNull NlModel model) {
    XmlTag root = getRootTag(model);
    if (root != null) {
      List<ViewInfo> rootViews = getRootViews(result, model.getType());
      List<ViewInfo> systemRootViews = result.getSystemRootViews();
      updateHierarchy(root, rootViews, systemRootViews, model);
    }
  }

  /**
   * Update the hierarchy based on the inflated rootViews.
   * @param views list of views inflated that matches model file
   * @param model to be updated
   */
  public static void updateHierarchy(@NotNull List<ViewInfo> views, @NotNull NlModel model) {
    updateHierarchy(views, null, model);
  }

  /**
   * Update the hierarchy based on the inflated rootViews and optional system decor rootViews.
   * @param views list of views inflated that matches model file
   * @param systemRootViews list of system decor root views if present
   * @param model to be updated
   */
  public static void updateHierarchy(@NotNull List<ViewInfo> views,
                                     @Nullable List<ViewInfo> systemRootViews,
                                     @NotNull NlModel model) {
    XmlTag root = getRootTag(model);
    if (root != null) {
      updateHierarchy(root, views, systemRootViews, model);
    }
  }

  /**
   * Update the hierarchy based on the inflated rootViews.
   * @param rootTag xml tag of the root view from PsiFile (from model)
   * @param views list of views inflated that matches model file
   * @param model to be updated
   */
  public static void updateHierarchy(@NotNull XmlTag rootTag, @NotNull List<ViewInfo> views, @NotNull NlModel model) {
    updateHierarchy(rootTag, views, null, model);
  }

  /**
   * Update the hierarchy based on the inflated rootViews and optional system decor rootViews.
   * @param rootTag xml tag of the root view from PsiFile (from model)
   * @param views list of views inflated that matches model file
   * @param systemRootViews list of system decor root views if present
   * @param model to be updated
   */
  public static void updateHierarchy(@NotNull XmlTag rootTag,
                                     @NotNull List<ViewInfo> views,
                                     @Nullable List<ViewInfo> systemRootViews,
                                     @NotNull NlModel model) {
    model.syncWithPsi(rootTag, ContainerUtil.map(views, ViewInfoTagSnapshotNode::new));
    model.updateAccessibility(views);
    updateBounds(views, systemRootViews, model);
    ImmutableList<NlComponent> components = model.getTreeReader().getComponents();
    if (!components.isEmpty() && handleScroll(components.getFirst())) {
      // If there is scrolling involved, this will update the SceneManager to show the correct location for bounding boxes.
      model.notifyListenersModelChangedOnLayout(false);
    }
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
  private static void updateBounds(@NotNull List<ViewInfo> rootViews,
                                   @Nullable List<ViewInfo> systemRootViews,
                                   @NotNull NlModel model) {
    model.getTreeReader().flattenComponents().forEach(NlModelHierarchyUpdater::clearDerivedData);
    Map<TagSnapshot, NlComponent> snapshotToComponent =
      model.getTreeReader().flattenComponents().collect(Collectors.toMap(NlComponent::getSnapshot, Function.identity(), (n1, n2) -> n1));
    Map<XmlTag, NlComponent> tagToComponent =
      model.getTreeReader().flattenComponents().collect(Collectors.toMap(NlComponent::getTagDeprecated, Function.identity(), (n1, n2) -> n1));
    Map<Long, NlComponent> sourceIdToComponent =
      model.getTreeReader().flattenComponents().collect(Collectors.toMap(NlComponent::getAccessibilityId, Function.identity(), (n1, n2) -> n1));

    // Update the bounds. This is based on the ViewInfo instances.
    for (ViewInfo view : rootViews) {
      int initialX = 0;
      int initialY = 0;
      if (systemRootViews != null && !systemRootViews.isEmpty()) {
        Point offset = findViewOffsetInSystemRoots(systemRootViews, view);
        if (offset != null) {
          initialX = offset.x - view.getLeft();
          initialY = offset.y - view.getTop();
        }
      }
      updateBounds(view, initialX, initialY, snapshotToComponent, tagToComponent, sourceIdToComponent);
    }

    ImmutableList<NlComponent> components = model.getTreeReader().getComponents();
    if (!rootViews.isEmpty() && !components.isEmpty()) {
      // Finally, fix up bounds: ensure that all components not found in the view
      // info hierarchy inherit position from parent
      fixBounds(components.get(0));
    }
  }

  private record ViewWithOffset(ViewInfo view, int relX, int relY) {}

  @Nullable
  private static Point findViewOffsetInSystemRoots(@NotNull List<ViewInfo> systemRootViews, @NotNull ViewInfo targetRoot) {
    Map<Object, ViewWithOffset> targetMap = new HashMap<>();
    collectViewsWithOffsets(targetRoot, 0, 0, targetMap);

    for (ViewInfo systemRoot : systemRootViews) {
      Point offset = findSubtreeOffset(systemRoot, 0, 0, targetMap);
      if (offset != null) {
        return offset;
      }
    }
    return null;
  }

  private static void collectViewsWithOffsets(@NotNull ViewInfo view, int currentRelX, int currentRelY, @NotNull Map<Object, ViewWithOffset> map) {
    ViewWithOffset node = new ViewWithOffset(view, currentRelX, currentRelY);
    addKeysForView(view, node, map);
    for (ViewInfo child : view.getChildren()) {
      collectViewsWithOffsets(child, currentRelX + child.getLeft(), currentRelY + child.getTop(), map);
    }
  }

  private static void addKeysForView(@NotNull ViewInfo view, @NotNull ViewWithOffset node, @NotNull Map<Object, ViewWithOffset> map) {
    map.putIfAbsent(view, node);
    Object cookie = view.getCookie();
    if (cookie != null) {
      map.putIfAbsent(cookie, node);
      Object tag = getTag(cookie);
      if (tag != null) {
        map.putIfAbsent(tag, node);
      }
    }
    Object viewObj = view.getViewObject();
    if (viewObj != null) {
      map.putIfAbsent(viewObj, node);
    }
    Object accObj = view.getAccessibilityObject();
    if (accObj != null) {
      map.putIfAbsent(accObj, node);
    }
  }

  @Nullable
  private static Point findSubtreeOffset(@NotNull ViewInfo sysView, int sysX, int sysY, @NotNull Map<Object, ViewWithOffset> targetMap) {
    int currentSysX = sysX + sysView.getLeft();
    int currentSysY = sysY + sysView.getTop();

    ViewWithOffset matchedNode = lookupView(sysView, targetMap);
    if (matchedNode != null) {
      return new Point(currentSysX - matchedNode.relX, currentSysY - matchedNode.relY);
    }

    for (ViewInfo child : sysView.getChildren()) {
      Point offset = findSubtreeOffset(child, currentSysX, currentSysY, targetMap);
      if (offset != null) {
        return offset;
      }
    }
    return null;
  }

  @Nullable
  private static ViewWithOffset lookupView(@NotNull ViewInfo sysView, @NotNull Map<Object, ViewWithOffset> map) {
    ViewWithOffset directMatch = map.get(sysView);
    if (directMatch != null) {
      return directMatch;
    }
    Object cookie = sysView.getCookie();
    if (cookie != null) {
      ViewWithOffset node = map.get(cookie);
      if (node != null) {
        return node;
      }
      Object tag = getTag(cookie);
      if (tag != null) {
        node = map.get(tag);
        if (node != null) {
          return node;
        }
      }
    }
    Object viewObj = sysView.getViewObject();
    if (viewObj != null) {
      ViewWithOffset node = map.get(viewObj);
      if (node != null) {
        return node;
      }
    }
    Object accObj = sysView.getAccessibilityObject();
    if (accObj != null) {
      return map.get(accObj);
    }
    return null;
  }

  @Nullable
  private static Object getTag(@Nullable Object cookie) {
    if (cookie instanceof TagSnapshot snapshot) {
      if (snapshot.tag instanceof PsiXmlTag psiXmlTag) {
        XmlTag tag = psiXmlTag.getPsiXmlTag();
        if (tag != null) {
          return tag;
        }
      }
      return snapshot.tag != null ? snapshot.tag : snapshot;
    }
    if (cookie instanceof PsiXmlTag psiXmlTag) {
      XmlTag tag = psiXmlTag.getPsiXmlTag();
      if (tag != null) {
        return tag;
      }
    }
    return cookie;
  }

  /**
   * Updates the scroll in the View hierarchy from the saved scroll in the components' hierarchy. Returns whether the component
   * or its children are scrolled by a non-zero amount.
   */
  private static boolean handleScroll(@NotNull NlComponent component) {
    boolean hasNonZeroScroll = false;
    ViewInfo viewInfo = NlComponentHelperKt.getViewInfo(component);
    Object viewObject = viewInfo != null ? viewInfo.getViewObject() : null;

    if (viewObject instanceof ViewGroup) {
      ViewGroup viewGroup = (ViewGroup)viewObject;
      int savedScrollX = NlComponentHelperKt.getScrollX(component);
      int savedScrollY = NlComponentHelperKt.getScrollY(component);
      hasNonZeroScroll = savedScrollX != 0 || savedScrollY != 0;
      if (savedScrollX != viewGroup.getScrollX() || savedScrollY != viewGroup.getScrollY()) {
        viewGroup.setScrollX(savedScrollX);
        viewGroup.setScrollY(savedScrollY);
      }
    }

    List<NlComponent> children = component.getChildren();
    for (NlComponent child : children) {
      hasNonZeroScroll = hasNonZeroScroll || handleScroll(child);
    }
    return hasNonZeroScroll;
  }

  private static void updateBounds(@NotNull ViewInfo view,
                                   @AndroidCoordinate int parentX,
                                   @AndroidCoordinate int parentY,
                                   Map<TagSnapshot, NlComponent> snapshotToComponent,
                                   Map<XmlTag, NlComponent> tagToComponent,
                                   Map<Long, NlComponent> sourceIdToComponent) {
    ViewInfo bounds = RenderService.getSafeBounds(view);
    Object cookie = view.getCookie();
    NlComponent component = null;
    if (cookie instanceof TagSnapshot) {
      TagSnapshot snapshot = (TagSnapshot)cookie;
      component = snapshotToComponent.get(snapshot);
      if (component == null) {
        PsiXmlTag psiXmlTag = (PsiXmlTag)snapshot.tag;
        component = tagToComponent.get(psiXmlTag != null ? psiXmlTag.getPsiXmlTag() : null);
      }
    }
    else {
      Object accessibilityObject = view.getAccessibilityObject();
      if (accessibilityObject != null) {
        AccessibilityNodeInfo nodeInfo = (AccessibilityNodeInfo)accessibilityObject;
        component = sourceIdToComponent.get(nodeInfo.getSourceNodeId());
      }
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
    parentX += bounds.getLeft();
    parentY += bounds.getTop();

    for (ViewInfo child : view.getChildren()) {
      updateBounds(child, parentX, parentY, snapshotToComponent, tagToComponent, sourceIdToComponent);
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
