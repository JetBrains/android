/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.intellij.android.designer.model;

import com.android.ide.common.rendering.api.MergeCookie;
import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.RenderService;
import com.google.common.collect.Maps;
import com.intellij.android.designer.AndroidDesignerEditor;
import com.intellij.android.designer.designSurface.AndroidDesignerEditorPanel;
import com.intellij.android.designer.designSurface.RootView;
import com.intellij.designer.ModuleProvider;
import com.intellij.designer.componentTree.TreeComponentDecorator;
import com.intellij.designer.model.EmptyXmlTag;
import com.intellij.designer.model.MetaManager;
import com.intellij.designer.model.MetaModel;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.VIEW_MERGE;
import static com.android.SdkConstants.VIEW_TAG;
import static com.android.tools.idea.rendering.IncludeReference.ATTR_RENDER_IN;
import static com.intellij.android.designer.designSurface.RootView.EMPTY_COMPONENT_SIZE;
import static com.intellij.android.designer.designSurface.RootView.VISUAL_EMPTY_COMPONENT_SIZE;

/**
 * Builder responsible for building up (and synchronizing) a hierarchy of {@link com.android.ide.common.rendering.api.ViewInfo}
 * objects from layoutlib with a corresponding hierarchy of {@link com.intellij.android.designer.model.RadViewComponent}
 */
public class RadModelBuilder {

  private static final String DESIGNER_KEY = "DESIGNER";

  // Special tag defined in the meta model file (views-meta-model.xml) defining the root node, shown as "Device Screen"
  public static final String ROOT_NODE_TAG = "<root>";

  private final MetaManager myMetaManager;
  private final PropertyParser myPropertyParser;
  private final Map<XmlTag,RadViewComponent> myTagToComponentMap = Maps.newIdentityHashMap();
  private final Map<XmlTag,RadViewComponent> myMergeComponentMap = Maps.newHashMap();
  private RootView myNativeComponent;
  private AndroidDesignerEditorPanel myDesigner;

  public RadModelBuilder(@NotNull AndroidDesignerEditorPanel designer, @NotNull PropertyParser propertyParser) {
    myDesigner = designer;
    myMetaManager = ViewsMetaManager.getInstance(designer.getProject());
    myPropertyParser = propertyParser;
  }

  @Nullable
  public static RadViewComponent update(@NotNull AndroidDesignerEditorPanel designer,
                                        @NotNull RenderResult result,
                                        @Nullable RadViewComponent prevRoot,
                                        @NotNull RootView nativeComponent) {
    PropertyParser propertyParser = designer.getPropertyParser(result);
    RadModelBuilder builder = new RadModelBuilder(designer, propertyParser);
    return builder.build(prevRoot, result, nativeComponent);
  }

  @NotNull
  public static AndroidDesignerEditorPanel getDesigner(@NotNull RadComponent component) {
    AndroidDesignerEditorPanel designer = component.getRoot().getClientProperty(DESIGNER_KEY);
    if (designer == null) {
      // This should not normally happen, but it has shown up in a few crash logs.
      // Perhaps it can happen if a component is queried after it has been detached from its root
      // (though it was not clear from the crash logs how that could happen). Simply
      // try to work a little harder to guess the designer from the current editor in that
      // case.
      if (component instanceof RadViewComponent) {
        Project project = ((RadViewComponent)component).getTag().getProject();
        for (FileEditor editor : FileEditorManager.getInstance(project).getSelectedEditors()) {
          if (editor instanceof AndroidDesignerEditor) {
            return (AndroidDesignerEditorPanel)((AndroidDesignerEditor)editor).getDesignerPanel();
          }
        }
      }

      assert false;
    }

    return designer;
  }

  @Nullable
  public static ModuleProvider getModuleProvider(@NotNull RadComponent component) {
    return getDesigner(component);
  }

  @Nullable
  public static Module getModule(@NotNull RadComponent component) {
    ModuleProvider provider = getModuleProvider(component);
    return provider != null ? provider.getModule() : null;
  }

  @Nullable
  public static Project getProject(@NotNull RadComponent component) {
    ModuleProvider provider = getModuleProvider(component);
    return provider != null ? provider.getProject() : null;
  }

  @NotNull
  public static XmlFile getXmlFile(@NotNull RadComponent component) {
    return getDesigner(component).getXmlFile();
  }

  @Nullable
  public static TreeComponentDecorator getTreeDecorator(@NotNull RadComponent component) {
    return getDesigner(component).getTreeDecorator();
  }

  @Nullable
  public static PropertyParser getPropertyParser(@NotNull RadComponent component) {
    return getDesigner(component).getPropertyParser(null);
  }

  @Nullable
  public RadViewComponent build(@Nullable RadViewComponent prevRoot,
                                @NotNull RenderResult result,
                                @NotNull RootView nativeComponent) {
    myNativeComponent = nativeComponent;
    RadViewComponent root = prevRoot;
    XmlTag rootTag = myDesigner.getXmlFile().getRootTag();
    boolean isMerge = rootTag != null && VIEW_MERGE.equals(rootTag.getName());
    if (root == null || isMerge != (root.getMetaModel() == myMetaManager.getModelByTag(VIEW_MERGE))) {
      try {
        root = createRoot(isMerge, rootTag);
        if (root == null) {
          return null;
        }
      } catch (Exception e) {
        return null;
      }
    }

    RenderSession session = result.getSession();
    assert session != null;

    updateClientProperties(result, nativeComponent, root);
    initTagMap(root);
    root.getChildren().clear();
    updateHierarchy(root, session);

    // I've removed any tags that are still in the map. I could call removeComponent on these, but I'm worried
    //for (RadViewComponent removed : map.values()) {
    //  myIdManager.removeComponent(removed, false);
    //}

    updateRootBounds(root, session);

    return root;
  }

  protected void updateClientProperties(@NotNull RenderResult result, RootView nativeComponent, RadViewComponent root) {
    root.setNativeComponent(nativeComponent);
    // Stash reference for the component decorator so it can show the included context
    //noinspection ConstantConditions
    root.setClientProperty(ATTR_RENDER_IN, result.getIncludedWithin());
  }

  protected void updateRootBounds(RadViewComponent root, RenderSession session) {
    // Ensure bounds for the root matches actual top level children
    BufferedImage image = session.getImage();
    Rectangle bounds = new Rectangle(0, 0, image != null ? image.getWidth() : 0, image != null ? image.getHeight() : 0);
    for (RadComponent radComponent : root.getChildren()) {
      bounds = bounds.union(radComponent.getBounds());
    }
    root.setBounds(bounds.x, bounds.y, bounds.width, bounds.height);
  }

  protected void updateHierarchy(RadViewComponent root, RenderSession session) {
    myNativeComponent.clearEmptyRegions();
    List<ViewInfo> rootViews = session.getRootViews();
    if (rootViews != null) {
      for (ViewInfo info : rootViews) {
        updateHierarchy(root, info, 0, 0);
      }
    }
  }

  protected void initTagMap(@NotNull RadViewComponent root) {
    myTagToComponentMap.clear();
    for (RadViewComponent component : RadViewComponent.getViewComponents(root.getChildren())) {
      gatherTags(myTagToComponentMap, component);
    }
  }

  @Nullable
  protected RadViewComponent createRoot(boolean isMerge, @Nullable XmlTag rootTag) throws Exception {
    RadViewComponent root;MetaModel rootModel = myMetaManager.getModelByTag(isMerge ? VIEW_MERGE : ROOT_NODE_TAG);
    assert rootModel != null;
    root = RadComponentOperations.createComponent(rootTag, rootModel);
    root.setClientProperty(DESIGNER_KEY, myDesigner);
    myPropertyParser.load(root);
    return root;
  }

  private static void gatherTags(Map<XmlTag, RadViewComponent> map, RadViewComponent component) {
    XmlTag tag = component.getTag();
    if (tag != EmptyXmlTag.INSTANCE) {
      map.put(tag, component);
    }

    for (RadComponent child : component.getChildren()) {
      if (child instanceof RadViewComponent) {
        gatherTags(map, (RadViewComponent)child);
      }
    }
  }

  @Nullable
  public RadViewComponent updateHierarchy(@Nullable RadViewComponent parent,
                                          ViewInfo view,
                                          int parentX,
                                          int parentY) {
    Object cookie = view.getCookie();
    RadViewComponent component = null;
    ViewInfo bounds = RenderService.getSafeBounds(view);

    XmlTag tag = null;
    boolean isMerge = false;
    if (cookie instanceof XmlTag) {
      tag = (XmlTag)cookie;
    } else if (cookie instanceof MergeCookie) {
      isMerge = true;
      cookie = ((MergeCookie)cookie).getCookie();
      if (cookie instanceof XmlTag) {
        tag = (XmlTag)cookie;
        if (myMergeComponentMap.containsKey(tag)) {
          // Just expand the bounds
          int left = parentX + bounds.getLeft();
          int top = parentY + bounds.getTop();
          int width = bounds.getRight() - bounds.getLeft();
          int height = bounds.getBottom() - bounds.getTop();
          RadViewComponent radViewComponent = myMergeComponentMap.get(tag);
          radViewComponent.getBounds().add(new Rectangle(left, top, width, height));
          return null;
        }
      }
    }
    if (tag != null) {
      boolean loadProperties;
      component = myTagToComponentMap.get(tag);
      if (component != null) {
        if (!tag.isValid()) {
          component = null;
        } else {
          ApplicationManager.getApplication().assertReadAccessAllowed();
          MetaModel modelByTag = myMetaManager.getModelByTag(tag.getName());
          if (modelByTag != null && modelByTag != component.getMetaModel()) {
            component = null;
          }
        }
      }
      if (component == null) {
        // TODO: Construct tag name from ViewInfo's class name so we don't have to touch the PSI data structures at all
        // (so we don't need a read lock)
        String tagName = tag.isValid() ? tag.getName() : VIEW_TAG;
        try {
          MetaModel metaModel = myMetaManager.getModelByTag(tagName);
          if (metaModel == null) {
            metaModel = myMetaManager.getModelByTag(VIEW_TAG);
            assert metaModel != null;
          }

          component = RadComponentOperations.createComponent(tag, metaModel);
          loadProperties = true;
        }
        catch (Throwable e) {
          throw new RuntimeException(e);
        }

      } else {
        component.getChildren().clear();
        myTagToComponentMap.remove(tag);
        loadProperties = component.getParent() != parent;
      }

      component.setViewInfo(view);
      component.setNativeComponent(myNativeComponent);

      int left = parentX + bounds.getLeft();
      int top = parentY + bounds.getTop();
      int width = bounds.getRight() - bounds.getLeft();
      int height = bounds.getBottom() - bounds.getTop();

      if (width < EMPTY_COMPONENT_SIZE && height < EMPTY_COMPONENT_SIZE) {
        myNativeComponent.addEmptyRegion(left, top, VISUAL_EMPTY_COMPONENT_SIZE, VISUAL_EMPTY_COMPONENT_SIZE);
      }

      component.setBounds(left, top, Math.max(width, VISUAL_EMPTY_COMPONENT_SIZE), Math.max(height, VISUAL_EMPTY_COMPONENT_SIZE));

      if (parent != null && parent != component) {
        parent.add(component, null);
        if (loadProperties) {
          // Load properties on a component *after* assigning parents, since that affects
          // the computation of available attributes (due to layout params)
          try {
            myPropertyParser.load(component);
          }
          catch (Throwable e) {
            throw new RuntimeException(e);
          }
        }
        if (isMerge) {
          myMergeComponentMap.put(tag, component);
        }
      }
    }

    if (component != null) {
      parent = component;
    }

    parentX += bounds.getLeft();
    parentY += bounds.getTop();

    for (ViewInfo child : view.getChildren()) {
      updateHierarchy(parent, child, parentX, parentY);
    }

    return component;
  }
}
