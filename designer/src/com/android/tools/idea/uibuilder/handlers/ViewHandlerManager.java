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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

  @NotNull
  public static ViewHandlerManager get(@NotNull Project project) {
    ViewHandlerManager manager = project.getComponent(ViewHandlerManager.class);
    assert manager != null;

    return manager;
  }

  /**
   * Returns the {@link ViewHandlerManager} for the current project
   */
  @NotNull
  public static ViewHandlerManager get(@NotNull AndroidFacet facet) {
    return get(facet.getModule().getProject());
  }

  public ViewHandlerManager(@NotNull Project project) {
    myProject = project;
  }

  /**
   * Gets the {@link ViewHandler} associated with the given component, if any
   *
   * @param component the component to find a handler for
   * @return the corresponding view handler, if any
   */
  @Nullable
  public ViewHandler getHandler(@NotNull NlComponent component) {
    return getHandler(component.getTagName());
  }

  /**
   * Gets the {@link ViewHandler} associated with a given component.
   * If there is no custom handler found returns an instance of {@link TextViewHandler}.
   */
  @NotNull
  public ViewHandler getHandlerOrDefault(@NotNull NlComponent component) {
    ViewHandler handler = getHandler(component);
    return handler != null ? handler : NONE;
  }

  /**
   * Gets the {@link ViewHandler} associated with the given XML tag.
   * If there is no custom handler found returns an instance of {@link TextViewHandler}.
   */
  @NotNull
  public ViewHandler getHandlerOrDefault(@NotNull String viewTag) {
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
  public ViewHandler getHandler(@NotNull String viewTag) {
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
  public ViewGroupHandler findLayoutHandler(@NotNull NlComponent component, boolean strict) {
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

  private ViewHandler createHandler(@NotNull String viewTag) {
    // Builtin view. Don't bother with reflection for the common cases.
    switch (viewTag) {
      case ABSOLUTE_LAYOUT:
        return new AbsoluteLayoutHandler();
      case ADAPTER_VIEW:
      case STACK_VIEW:
        return new AdapterViewHandler();
      case AD_VIEW:
        return new AdViewHandler();
      case APP_BAR_LAYOUT:
        return new AppBarLayoutHandler();
      case AUTO_COMPLETE_TEXT_VIEW:
      case BUTTON:
      case CHECKED_TEXT_VIEW:
      case CHECK_BOX:
      case EDIT_TEXT:
      case MULTI_AUTO_COMPLETE_TEXT_VIEW:
      case RADIO_BUTTON:
      case SWITCH:
      case TEXT_VIEW:
      case TOGGLE_BUTTON:
        return TEXT_HANDLER;
      case BROWSE_FRAGMENT:
        return new BrowseFragmentHandler();
      case CARD_VIEW:
        return new CardViewHandler();
      case CHRONOMETER:
      case QUICK_CONTACT_BADGE:
      case TEXT_CLOCK:
        return STANDARD_HANDLER;
      case CONSTRAINT_LAYOUT:
        return new ConstraintLayoutHandler();
      case COORDINATOR_LAYOUT:
        return new CoordinatorLayoutHandler();
      case DETAILS_FRAGMENT:
        return new DetailsFragmentHandler();
      case DIALER_FILTER:
      case FQCN_RELATIVE_LAYOUT:
      case RELATIVE_LAYOUT:
        return new RelativeLayoutHandler();
      case EXPANDABLE_LIST_VIEW:
        // TODO: Find out why this fails to load by class name
        return new ListViewHandler();
      case FLOATING_ACTION_BUTTON:
        return new FloatingActionButtonHandler();
      case FQCN_LINEAR_LAYOUT:
      case LINEAR_LAYOUT:
        return new LinearLayoutHandler();
      case FRAME_LAYOUT:
      case GESTURE_OVERLAY_VIEW:
      case IMAGE_SWITCHER:
      case NESTED_SCROLL_VIEW:
      case TEXT_SWITCHER:
      case VIEW_ANIMATOR:
      case VIEW_FLIPPER:
      case VIEW_SWITCHER:
        return new FrameLayoutHandler();
      case GRID_LAYOUT:
        return new GridLayoutHandler();
      case GRID_LAYOUT_V7:
        return new GridLayoutV7Handler();
      case HORIZONTAL_SCROLL_VIEW:
        return new HorizontalScrollViewHandler();
      case IMAGE_BUTTON:
        return new ImageButtonHandler();
      case IMAGE_VIEW:
        return new ImageViewHandler();
      case MAP_FRAGMENT:
        return new MapFragmentHandler();
      case MAP_VIEW:
        return new MapViewHandler();
      case PLAYBACK_OVERLAY_FRAGMENT:
        return new PlaybackOverlayFragmentHandler();
      case PROGRESS_BAR:
        return new ProgressBarHandler();
      case PreferenceTags.CHECK_BOX_PREFERENCE:
        return new CheckBoxPreferenceHandler();
      case PreferenceTags.EDIT_TEXT_PREFERENCE:
        return new EditTextPreferenceHandler();
      case PreferenceTags.LIST_PREFERENCE:
        return new ListPreferenceHandler();
      case PreferenceTags.MULTI_SELECT_LIST_PREFERENCE:
        return new MultiSelectListPreferenceHandler();
      case PreferenceTags.PREFERENCE_CATEGORY:
        return new PreferenceCategoryHandler();
      case PreferenceTags.PREFERENCE_SCREEN:
        return new ViewGroupHandler();
      case PreferenceTags.RINGTONE_PREFERENCE:
        return new RingtonePreferenceHandler();
      case PreferenceTags.SWITCH_PREFERENCE:
        return new SwitchPreferenceHandler();
      case RECYCLER_VIEW:
        return new RecyclerViewHandler();
      case REQUEST_FOCUS:
        return new RequestFocusHandler();
      case SCROLL_VIEW:
        return new ScrollViewHandler();
      case SEARCH_FRAGMENT:
        return new SearchFragmentHandler();
      case SPACE:
      case SURFACE_VIEW:
      case TEXTURE_VIEW:
        return NO_PREVIEW_HANDLER;
      case SPINNER:
        return new SpinnerHandler();
      case TABLE_CONSTRAINT_LAYOUT:
        return new ConstraintLayoutHandler();
      case TABLE_LAYOUT:
        return new TableLayoutHandler();
      case TABLE_ROW:
        return new TableRowHandler();
      case TAB_HOST:
        return new TabHostHandler();
      case TEXT_INPUT_LAYOUT:
        return new TextInputLayoutHandler();
      case TOOLBAR_V7:
        return new ToolbarHandler();
      case VIEW_FRAGMENT:
        return new FragmentHandler();
      case VIEW_INCLUDE:
        return new IncludeHandler();
      case VIEW_STUB:
        return new ViewStubHandler();
      case VIEW_TAG:
        return new ViewTagHandler();
      case ZOOM_BUTTON:
        return new ZoomButtonHandler();
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
  private String getFullyQualifiedClassName(@NotNull String viewTag) {
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

  @NotNull
  @Override
  public String getComponentName() {
    return "ViewHandlerManager";
  }
}
