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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutHandler;
import com.android.tools.idea.uibuilder.handlers.google.AdViewHandler;
import com.android.tools.idea.uibuilder.handlers.google.MapFragmentHandler;
import com.android.tools.idea.uibuilder.handlers.google.MapViewHandler;
import com.android.tools.idea.uibuilder.handlers.grid.GridLayoutHandler;
import com.android.tools.idea.uibuilder.handlers.grid.GridLayoutV7Handler;
import com.android.tools.idea.uibuilder.handlers.leanback.BrowseFragmentHandler;
import com.android.tools.idea.uibuilder.handlers.leanback.DetailsFragmentHandler;
import com.android.tools.idea.uibuilder.handlers.leanback.PlaybackOverlayFragmentHandler;
import com.android.tools.idea.uibuilder.handlers.leanback.SearchFragmentHandler;
import com.android.tools.idea.uibuilder.handlers.relative.RelativeLayoutHandler;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.android.facet.AndroidFacet;

import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.*;

/**
 * Tracks and provides {@link ViewHandler} instances in this project
 */
public class ViewHandlerManager implements ProjectComponent {
  /**
   * View handlers are named the same as the class for the view they represent, plus this suffix
   */
  private static final String HANDLER_CLASS_SUFFIX = "Handler";
  private static final Set<String> NO_PREFIX_PACKAGES = ImmutableSet
    .of(ANDROID_WIDGET_PREFIX, ANDROID_VIEW_PKG, ANDROID_WEBKIT_PKG, ANDROID_APP_PKG);

  private final Project myProject;
  private final Map<String, ViewHandler> myHandlers = Maps.newHashMap();
  public static final ViewHandler NONE = new ViewHandler();
  private static final ViewHandler STANDARD_HANDLER = new ViewHandler();
  private static final ViewHandler TEXT_HANDLER = new TextViewHandler();
  private static final ViewHandler NO_PREVIEW_HANDLER = new NoPreviewHandler();

  @NonNull
  public static ViewHandlerManager get(@NonNull Project project) {
    ViewHandlerManager manager = project.getComponent(ViewHandlerManager.class);
    assert manager != null;

    return manager;
  }

  /**
   * Returns the {@link ViewHandlerManager} for the current project
   */
  @NonNull
  public static ViewHandlerManager get(@NonNull AndroidFacet facet) {
    return get(facet.getModule().getProject());
  }

  public ViewHandlerManager(@NonNull Project project) {
    myProject = project;
  }

  /**
   * Gets the {@link ViewHandler} associated with the given component, if any
   *
   * @param component the component to find a handler for
   * @return the corresponding view handler, if any
   */
  @Nullable
  public ViewHandler getHandler(@NonNull NlComponent component) {
    return getHandler(component.getTagName());
  }

  /**
   * Gets the {@link ViewHandler} associated with a given component.
   * If there is no custom handler found returns an instance of {@link TextViewHandler}.
   */
  @NonNull
  public ViewHandler getHandlerOrDefault(@NonNull NlComponent component) {
    ViewHandler handler = getHandler(component);
    return handler != null ? handler : NONE;
  }

  /**
   * Gets the {@link ViewHandler} associated with the given XML tag.
   * If there is no custom handler found returns an instance of {@link TextViewHandler}.
   */
  @NonNull
  public ViewHandler getHandlerOrDefault(@NonNull String viewTag) {
    ViewHandler handler = getHandler(viewTag);
    return handler != null ? handler : NONE;
  }

  /**
   * Gets the {@link ViewHandler} associated with the given XML tag, if any
   *
   * @param viewTag the tag to look up
   * @return the corresponding view handler, if any
   */
  @Nullable
  public ViewHandler getHandler(@NonNull String viewTag) {
    ViewHandler handler = myHandlers.get(viewTag);
    if (handler == null) {
      if (viewTag.indexOf('.') != -1) {
        String tag = NlComponent.viewClassToTag(viewTag);
        if (!tag.equals(viewTag)) {
          handler = getHandler(tag);
          if (handler != null) {
            // Alias fully qualified widget name to tag
            myHandlers.put(viewTag, handler);
            return handler;
          }
        }
      }

      handler = createHandler(viewTag);
      myHandlers.put(viewTag, handler);
    }

    return handler != NONE ? handler : null;
  }

  /**
   * Finds the nearest layout/view group handler for the given component.
   *
   * @param component the component to search from
   * @param strict    if true, only consider parents of the component, not the component itself
   */
  @Nullable
  public ViewGroupHandler findLayoutHandler(@NonNull NlComponent component, boolean strict) {
    NlComponent curr = component;
    if (strict) {
      curr = curr.getParent();
    }
    while (curr != null) {
      ViewHandler handler = getHandler(curr);
      if (handler instanceof ViewGroupHandler) {
        return (ViewGroupHandler)handler;
      }

      curr = curr.getParent();
    }

    return null;
  }

  private ViewHandler createHandler(@NonNull String viewTag) {
    // Builtin view. Don't bother with reflection for the common cases.
    if (FRAME_LAYOUT.equals(viewTag) ||
        IMAGE_SWITCHER.equals(viewTag) ||
        TEXT_SWITCHER.equals(viewTag) ||
        VIEW_SWITCHER.equals(viewTag) ||
        VIEW_FLIPPER.equals(viewTag) ||
        VIEW_ANIMATOR.equals(viewTag) ||
        GESTURE_OVERLAY_VIEW.equals(viewTag) ||
        NESTED_SCROLL_VIEW.equals(viewTag)) {
      return new FrameLayoutHandler();
    }
    if (COORDINATOR_LAYOUT.equals(viewTag)) {
      return new CoordinatorLayoutHandler();
    }
    if (APP_BAR_LAYOUT.equals(viewTag)) {
      return new AppBarLayoutHandler();
    }
    if (LINEAR_LAYOUT.equals(viewTag) || FQCN_LINEAR_LAYOUT.equals(viewTag)) {
      return new LinearLayoutHandler();
    }
    if (TABLE_LAYOUT.equals(viewTag)) {
      return new TableLayoutHandler();
    }
    if (TABLE_ROW.equals(viewTag)) {
      return new TableRowHandler();
    }
    if (GRID_LAYOUT.equals(viewTag)) {
      return new GridLayoutHandler();
    }
    if (GRID_LAYOUT_V7.equals(viewTag)) {
      return new GridLayoutV7Handler();
    }
    if (RELATIVE_LAYOUT.equals(viewTag) || FQCN_RELATIVE_LAYOUT.equals(viewTag) || DIALER_FILTER.equals(viewTag)) {
      return new RelativeLayoutHandler();
    }
    if (CONSTRAINT_LAYOUT.equals(viewTag)) {
      return new ConstraintLayoutHandler();
    }
    if (TABLE_CONSTRAINT_LAYOUT.equals(viewTag)) {
      return new ConstraintLayoutHandler();
    }
    if (SCROLL_VIEW.equals(viewTag)) {
      return new ScrollViewHandler();
    }
    if (HORIZONTAL_SCROLL_VIEW.equals(viewTag)) {
      return new HorizontalScrollViewHandler();
    }
    if (IMAGE_BUTTON.equals(viewTag)) {
      return new ImageButtonHandler();
    }
    if (IMAGE_VIEW.equals(viewTag)) {
      return new ImageViewHandler();
    }
    if (ZOOM_BUTTON.equals(viewTag)) {
      return new ZoomButtonHandler();
    }
    if (VIEW_INCLUDE.equals(viewTag)) {
      return new IncludeHandler();
    }
    if (VIEW_FRAGMENT.equals(viewTag)) {
      return new FragmentHandler();
    }
    if (REQUEST_FOCUS.equals(viewTag)) {
      return new RequestFocusHandler();
    }
    if (VIEW_TAG.equals(viewTag)) {
      return new ViewTagHandler();
    }
    if (VIEW_STUB.equals(viewTag)) {
      return new ViewStubHandler();
    }
    if (ADAPTER_VIEW.equals(viewTag) || STACK_VIEW.equals(viewTag)) {
      return new AdapterViewHandler();
    }
    if (ABSOLUTE_LAYOUT.equals(viewTag)) {
      return new AbsoluteLayoutHandler();
    }
    if (FLOATING_ACTION_BUTTON.equals(viewTag)) {
      return new FloatingActionButtonHandler();
    }
    if (PROGRESS_BAR.equals(viewTag)) {
      return new ProgressBarHandler();
    }
    if (TEXT_INPUT_LAYOUT.equals(viewTag)) {
      return new TextInputLayoutHandler();
    }
    if (AD_VIEW.equals(viewTag)) {
      return new AdViewHandler();
    }
    if (MAP_FRAGMENT.equals(viewTag)) {
      return new MapFragmentHandler();
    }
    if (MAP_VIEW.equals(viewTag)) {
      return new MapViewHandler();
    }
    if (CARD_VIEW.equals(viewTag)) {
      return new CardViewHandler();
    }
    if (RECYCLER_VIEW.equals(viewTag)) {
      return new RecyclerViewHandler();
    }
    if (TOOLBAR_V7.equals(viewTag)) {
      return new ToolbarHandler();
    }
    if (BROWSE_FRAGMENT.equals(viewTag)) {
      return new BrowseFragmentHandler();
    }
    if (DETAILS_FRAGMENT.equals(viewTag)) {
      return new DetailsFragmentHandler();
    }
    if (PLAYBACK_OVERLAY_FRAGMENT.equals(viewTag)) {
      return new PlaybackOverlayFragmentHandler();
    }
    if (SEARCH_FRAGMENT.equals(viewTag)) {
      return new SearchFragmentHandler();
    }
    if (TAB_HOST.equals(viewTag)) {
      return new TabHostHandler();
    }
    if (EXPANDABLE_LIST_VIEW.equals(viewTag)) {
      // TODO: Find out why this fails to load by class name
      return new ListViewHandler();
    }
    if (SPINNER.equals(viewTag)) {
      return new SpinnerHandler();
    }
    if (CHRONOMETER.equals(viewTag) || TEXT_CLOCK.equals(viewTag) || QUICK_CONTACT_BADGE.equals(viewTag)) {
      return STANDARD_HANDLER;
    }
    if (TextViewHandler.hasTextAttribute(viewTag)) {
      return TEXT_HANDLER;
    }
    if (NoPreviewHandler.hasNoPreview(viewTag)) {
      return NO_PREVIEW_HANDLER;
    }

    // Look for other handlers via reflection; first built into the IDE:
    try {
      String defaultHandlerPkgPrefix = "com.android.tools.idea.uibuilder.handlers.";
      String handlerClass = defaultHandlerPkgPrefix + viewTag + HANDLER_CLASS_SUFFIX;
      @SuppressWarnings("unchecked") Class<? extends ViewHandler> cls = (Class<? extends ViewHandler>)Class.forName(handlerClass);
      return cls.newInstance();
    }
    catch (Exception ignore) {
    }

    String qualifiedClassName = getFullyQualifiedClassName(viewTag);
    if (qualifiedClassName != null) {
      String handlerName = viewTag + HANDLER_CLASS_SUFFIX;
      JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
      PsiClass[] classes = facade.findClasses(handlerName, GlobalSearchScope.allScope(myProject));

      if (classes.length == 0) {
        // No view handler found for this class; look up the custom view and get the handler for its
        // parent view instead. For example, if you've customized a LinearLayout by subclassing it, then
        // if you don't provide a ViewHandler for the subclass, we dall back to the LinearLayout's
        // ViewHandler instead.
        classes = facade.findClasses(qualifiedClassName, GlobalSearchScope.allScope(myProject));
        for (PsiClass cls : classes) {
          PsiClass superClass = cls.getSuperClass();
          if (superClass != null) {
            String fqn = superClass.getQualifiedName();
            if (fqn != null) {
              return getHandler(NlComponent.viewClassToTag(fqn));
            }
          }
        }
      }
      else {
        for (PsiClass cls : classes) {
          // Look for bytecode and instantiate if possible, then return
          // TODO: Instantiate
          // noinspection UseOfSystemOutOrSystemErr
          System.out.println("Find view handler " + cls.getQualifiedName() + " of type " + cls.getClass().getName());
        }
      }
    }

    return NONE;
  }

  @Nullable
  private String getFullyQualifiedClassName(@NonNull String viewTag) {
    if (viewTag.indexOf('.') > 0) {
      return viewTag;
    }
    JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
    for (String packageName : NO_PREFIX_PACKAGES) {
      PsiClass[] classes = facade.findClasses(packageName + viewTag, GlobalSearchScope.allScope(myProject));
      if (classes.length > 0) {
        return packageName + viewTag;
      }
    }
    return null;
  }

  @Override
  public void projectOpened() {
  }

  @Override
  public void projectClosed() {
    myHandlers.clear();
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
    myHandlers.clear();
  }

  @NonNull
  @Override
  public String getComponentName() {
    return "ViewHandlerManager";
  }
}
