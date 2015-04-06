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
package com.android.tools.idea.uibuilder.model;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.MergeCookie;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationListener;
import com.android.tools.idea.rendering.*;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.android.designer.model.layout.actions.ToggleRenderModeAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Alarm;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Model for an XML file
 */
public class NlModel implements Disposable, ConfigurationListener, ResourceFolderManager.ResourceFolderListener {
  @AndroidCoordinate public static final int EMPTY_COMPONENT_SIZE = 5;
  @AndroidCoordinate public static final int VISUAL_EMPTY_COMPONENT_SIZE = 14;

  private final AndroidFacet myFacet;
  private final XmlFile myFile;
  private RenderResult myRenderResult;
  private Configuration myConfiguration;
  private final List<ChangeListener> myListeners = Lists.newArrayList();
  private List<NlComponent> myComponents = Lists.newArrayList();
  private final SelectionModel mySelectionModel;
  private Disposable myParent;
  private ExternalChangeListener myPsiListener;
  private int myConfigurationDirty;
  private boolean myActive;
  private boolean myVariantChanged;

  @NonNull
  public static NlModel create(@Nullable Disposable parent, @NonNull AndroidFacet facet, @NonNull XmlFile file) {
    return new NlModel(parent, facet, file);
  }

  private NlModel(@Nullable Disposable parent, @NonNull AndroidFacet facet, @NonNull XmlFile file) {
    myParent = parent;
    myFacet = facet;
    myFile = file;
    myConfiguration = facet.getConfigurationManager().getConfiguration(myFile.getVirtualFile());
    mySelectionModel = new SelectionModel();

    if (facet.isGradleProject()) {
      // Ensure that the app resources have been initialized first, since
      // we want it to add its own variant listeners before ours (such that
      // when the variant changes, the project resources get notified and updated
      // before our own update listener attempts a re-render)
      ModuleResourceRepository.getModuleResources(facet, true /*createIfNecessary*/);
      myFacet.getResourceFolderManager().addListener(this);
    }

    myConfiguration.addListener(this);

    myPsiListener = new ExternalChangeListener(this);
    activate();
  }

  public void setParentDisposable(Disposable parent) {
    synchronized (myRenderingQueueLock) {
      myParent = parent;
    }
  }

  /** Notify model that it's active. A model is active by default. */
  public void activate() {
    if (!myActive) {
      myActive = true;
      myPsiListener.activate();

      if (myVariantChanged || (myConfigurationDirty & MASK_RENDERING) != 0) {
        myVariantChanged = false;
        requestRender();
      }
      myConfigurationDirty = 0;
    }
  }

  /** Notify model that it's not active. This means it can stop watching for events etc. It may be activated again in the future. */
  public void deactivate() {
    if (myActive) {
      myPsiListener.deactivate();
      myActive = false;
    }
  }

  public void buildProject() {
    requestRender();
  }

  public XmlFile getFile() {
    return myFile;
  }

  @NonNull
  public SelectionModel getSelectionModel() {
    return mySelectionModel;
  }

  public boolean requestRender() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    getRenderingQueue().queue(new Update("render") {
      @Override
      public void run() {
        DumbService.getInstance(myFacet.getModule().getProject()).waitForSmartMode();
        try {
          doRender();
        }
        catch (Throwable e) {
          Logger.getInstance(NlModel.class).error(e);
        }
      }

      @Override
      public boolean canEat(Update update) {
        return true;
      }
    });
    return true;
  }

  @NotNull
  private MergingUpdateQueue getRenderingQueue() {
    synchronized (myRenderingQueueLock) {
      if (myRenderingQueue == null) {
        myRenderingQueue = new MergingUpdateQueue("android.layout.rendering", 10, true, null, myParent, null,
                                                  Alarm.ThreadToUse.OWN_THREAD);
      }
      return myRenderingQueue;
    }
  }

  private final Object myRenderingQueueLock = new Object();
  private MergingUpdateQueue myRenderingQueue;
  private static final Object RENDERING_LOCK = new Object();


  private void doRender() {
    if (myFacet.getModule().getProject().isDisposed()) {
      return;
    }

    Configuration configuration = myConfiguration;
    if (configuration == null) {
      return;
    }

    // Some types of files must be saved to disk first, because layoutlib doesn't
    // delegate XML parsers for non-layout files (meaning layoutlib will read the
    // disk contents, so we have to push any edits to disk before rendering)
    LayoutPullParserFactory.saveFileIfNecessary(myFile);

    RenderResult result = null;
    synchronized (RENDERING_LOCK) {
      RenderService renderService = RenderService.get(myFacet);
      RenderLogger logger = renderService.createLogger();
      final RenderTask task = renderService.createTask(myFile, configuration, logger, null);
      if (task != null) {
        if (!ToggleRenderModeAction.isRenderViewPort()) {
          task.useDesignMode(myFile);
        }
        result = task.render();
        task.dispose();
      }
      if (result == null) {
        result = RenderResult.createBlank(myFile, logger);
      }
    }

    if (!getRenderingQueue().isEmpty()) {
      return;
    }

    myRenderResult = result;
    updateHierarchy(result);
    notifyListeners();
  }

  public void addListener(@NonNull ChangeListener listener) {
    synchronized (myListeners) {
      myListeners.remove(listener); // prevent duplicate registration
      myListeners.add(listener);
    }
  }

  public void removeListener(@NonNull ChangeListener listener) {
    synchronized (myListeners) {
      myListeners.remove(listener);
    }
  }

  private void notifyListeners() {
    synchronized (myListeners) {
      for (ChangeListener listener : myListeners) {
        listener.stateChanged(null);
      }
    }
  }

  @Nullable
  public RenderResult getRenderResult() {
    return myRenderResult;
  }

  @NonNull
  public AndroidFacet getFacet() {
    return myFacet;
  }

  @NonNull
  public Configuration getConfiguration() {
    return myConfiguration;
  }

  @NonNull
  public List<NlComponent> getComponents() {
    return myComponents;
  }

  private final Map<XmlTag,NlComponent> myTagToComponentMap = Maps.newIdentityHashMap();
  private final Map<XmlTag,NlComponent> myMergeComponentMap = Maps.newHashMap();

  void updateHierarchy(@Nullable RenderResult result) {
    if (result == null || result.getSession() == null || !result.getSession().getResult().isSuccess()) {
      myComponents.clear();
      return;
    }
    updateHierarchy(result.getSession().getRootViews());
  }

  void updateHierarchy(@Nullable List<ViewInfo> rootViews) {
    for (NlComponent component : myComponents) {
      initTagMap(component);
      component.children = null;
    }

    List<NlComponent> newRoots = Lists.newArrayList();
    if (rootViews != null) {
      for (ViewInfo info : rootViews) {
        NlComponent newRoot = updateHierarchy(null, info, 0, 0);
        newRoots.add(newRoot);
      }
    }
    myComponents = newRoots;
  }

  protected void initTagMap(@NonNull NlComponent root) {
    myTagToComponentMap.clear();
    for (NlComponent component : root.getChildren()) {
      gatherTags(myTagToComponentMap, component);
    }
  }

  private static void gatherTags(Map<XmlTag, NlComponent> map, NlComponent component) {
    XmlTag tag = component.tag;
    map.put(tag, component);

    for (NlComponent child : component.getChildren()) {
      gatherTags(map, child);
    }
  }

  @Nullable
  private NlComponent updateHierarchy(@Nullable NlComponent parent, ViewInfo view,
                                      @AndroidCoordinate int parentX, @AndroidCoordinate int parentY) {
    Object cookie = view.getCookie();
    NlComponent component = null;

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
          int left = parentX + view.getLeft();
          int top = parentY + view.getTop();
          int width = view.getRight() - view.getLeft();
          int height = view.getBottom() - view.getTop();
          NlComponent viewComponent = myMergeComponentMap.get(tag);
          Rectangle rectanglePrev = new Rectangle(viewComponent.x, viewComponent.y,
                                                  viewComponent.w, viewComponent.h);
          Rectangle rectangle = new Rectangle(left, top, width, height);
          rectangle.add(rectanglePrev);
          viewComponent.setBounds(rectanglePrev.x, rectanglePrev.y, rectanglePrev.width, rectanglePrev.height);
          return null;
        }
      }
    }
    if (tag != null) {
      component = myTagToComponentMap.get(tag);
      if (component != null) {
        // TODO: Clear out component is the tag is not valid
        //if (!tag.isValid()) {
        //  component = null;
        //}
      }
      if (component == null) {
        component = new NlComponent(tag);
      } else {
        component.children = null;
        myTagToComponentMap.remove(tag);
      }

      component.viewInfo = view;

      int left = parentX + view.getLeft();
      int top = parentY + view.getTop();
      int width = view.getRight() - view.getLeft();
      int height = view.getBottom() - view.getTop();

      component.setBounds(left, top, Math.max(width, VISUAL_EMPTY_COMPONENT_SIZE), Math.max(height, VISUAL_EMPTY_COMPONENT_SIZE));

      if (parent != null && parent != component) {
        parent.addChild(component);
        if (isMerge) {
          myMergeComponentMap.put(tag, component);
        }
      }
    }

    if (component != null) {
      parent = component;
    }

    parentX += view.getLeft();
    parentY += view.getTop();

    for (ViewInfo child : view.getChildren()) {
      updateHierarchy(parent, child, parentX, parentY);
    }

    return component;
  }

  @Nullable
  public List<NlComponent> findByOffset(int offset) {
    XmlTag tag = PsiTreeUtil.findElementOfClassAtOffset(myFile, offset, XmlTag.class, false);
    return (tag != null) ? findViewsByTag(tag) : null;
  }

  @Nullable
  public NlComponent findLeafAt(@AndroidCoordinate int x, @AndroidCoordinate int y) {
    // Search BACKWARDS such that if the children are painted on top of each
    // other (as is the case in a FrameLayout) I pick the last one which will
    // be topmost!
    for (int i = myComponents.size() - 1; i >= 0; i--) {
      NlComponent component = myComponents.get(i);
      NlComponent leaf = component.findLeafAt(x, y);
      if (leaf != null) {
        return leaf;
      }
    }

    return null;
  }

  @Nullable
  public NlComponent findViewByTag(@NonNull XmlTag tag) {
    // TODO: Consider using lookup map
    for (NlComponent component : myComponents) {
      NlComponent match = component.findViewByTag(tag);
      if (match != null) {
        return match;
      }
    }

    return null;
  }

  @Nullable
  public List<NlComponent> findViewsByTag(@NonNull XmlTag tag) {
    List<NlComponent> result = null;
    for (NlComponent view : myComponents) {
      List<NlComponent> matches = view.findViewsByTag(tag);
      if (matches != null) {
        if (result != null) {
          result.addAll(matches);
        } else {
          result = matches;
        }
      }
    }

    return result;
  }

  /**
   * Finds any components that overlap the given rectangle.
   *
   * @param x      The top left x corner defining the selection rectangle.
   * @param y      The top left y corner defining the selection rectangle.
   * @param width  The w of the selection rectangle
   * @param height The h of the selection rectangle
   */
  public List<NlComponent> findWithin(@AndroidCoordinate int x,
                                      @AndroidCoordinate int y,
                                      @AndroidCoordinate int width,
                                      @AndroidCoordinate int height) {
    List<NlComponent> within = Lists.newArrayList();
    for (NlComponent component : myComponents) {
      addWithin(within, component, x, y, width, height);
    }
    return within;
  }

  private static boolean addWithin(@NonNull List<NlComponent> result,
                                   @NonNull NlComponent component,
                                   @AndroidCoordinate int x,
                                   @AndroidCoordinate int y,
                                   @AndroidCoordinate int width,
                                   @AndroidCoordinate int height) {
    if (component.x + component.w <= x ||
        x + width <= component.x ||
        component.y + component.h <= y ||
        y + height <= component.y) {
      return false;
    }

    boolean found = false;
    for (NlComponent child : component.getChildren()) {
      found |= addWithin(result, child, x, y, width, height);
    }
    if (!found) {
      result.add(component);
    }
    return true;
  }

  public void delete() {
  }


  public void delete(final Collection<NlComponent> components) {
    // Group by parent and ask each one to participate
    WriteCommandAction<Void> action = new WriteCommandAction<Void>(myFacet.getModule().getProject(), "Delete Component", myFile) {
      @Override
      protected void run(@NotNull Result<Void> result) throws Throwable {
        handleDeletion(components);
      }
    };
    action.execute();
  }

  private void handleDeletion(@NotNull Collection<NlComponent> components) throws Exception {
    // Segment the deleted components into lists of siblings
    Map<NlComponent, List<NlComponent>> siblingLists = groupSiblings(components);

    ViewHandlerManager viewHandlerManager = ViewHandlerManager.get(myFacet);


    // Notify parent components about children getting deleted
    for (Map.Entry<NlComponent, List<NlComponent>> entry : siblingLists.entrySet()) {
      NlComponent parent = entry.getKey();
      List<NlComponent> children = entry.getValue();
      boolean finished = false;

      ViewHandler handler = viewHandlerManager.getHandler(parent.tag.getName());
      if (handler != null) {
        finished = handler.deleteChildren(parent, children);
      }

      if (!finished) {
        for (NlComponent component : children) {
          NlComponent p = component.getParent();
          if (p != null) {
            p.removeChild(component);
          }
          component.tag.delete();
        }
      }
    }
  }

  /**
   * Partitions the given list of components into a map where each value is a list of siblings,
   * in the same order as in the original list, and where the keys are the parents (or null
   * for the components that do not have a parent).
   * <p/>
   * The value lists will never be empty. The parent key will be null for components without parents.
   *
   * @param components the components to be grouped
   * @return a map from parents (or null) to a list of components with the corresponding parent
   */
  @NotNull
  public static Map<NlComponent, List<NlComponent>> groupSiblings(@NotNull Collection<? extends NlComponent> components) {
    Map<NlComponent, List<NlComponent>> siblingLists = new HashMap<NlComponent, List<NlComponent>>();

    if (components.isEmpty()) {
      return siblingLists;
    }
    if (components.size() == 1) {
      NlComponent component = components.iterator().next();
      siblingLists.put(component.getParent(), Collections.singletonList(component));
      return siblingLists;
    }

    for (NlComponent component : components) {
      NlComponent parent = component.getParent();
      List<NlComponent> children = siblingLists.get(parent);
      if (children == null) {
        children = new ArrayList<NlComponent>();
        siblingLists.put(parent, children);
      }
      children.add(component);
    }

    return siblingLists;
  }

  @Override
  public void dispose() {
    myPsiListener.deactivate();
    myConfiguration.removeListener(this);
    myFacet.getResourceFolderManager().removeListener(this);
  }

  // ---- Implements ResourceFolderManager.ResourceFolderListener ----

  @Override
  public void resourceFoldersChanged(@NotNull AndroidFacet facet,
                                     @NotNull List<VirtualFile> folders,
                                     @NotNull Collection<VirtualFile> added,
                                     @NotNull Collection<VirtualFile> removed) {
    if (facet == myFacet) {
      if (myActive) {
        // The app resources should already have been refreshed by their own variant listener
        requestRender();
      } else {
        myVariantChanged = true;
      }
    }
  }

  // ---- implements ConfigurationListener ----

  @Override
  public boolean changed(int flags) {
    if (myActive) {
      requestRender();

      //if ((flags & CFG_TARGET) != 0) {
      //  IAndroidTarget target = myConfiguration != null ? myConfiguration.getTarget() : null;
      //  if (target != null) {
      //    updatePalette(target);
      //  }
      //}
    } else {
      myConfigurationDirty |= flags;
    }

    return true;
  }
}
