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

import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.actions.ViewAction;
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
import com.android.tools.idea.uibuilder.handlers.menu.GroupHandler;
import com.android.tools.idea.uibuilder.handlers.menu.ItemHandler;
import com.android.tools.idea.uibuilder.handlers.menu.MenuHandler;
import com.android.tools.idea.uibuilder.handlers.preference.*;
import com.android.tools.idea.uibuilder.handlers.relative.RelativeLayoutHandler;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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
  private final Map<ViewHandler, List<ViewAction>> myToolbarActions = Maps.newHashMap();
  private final Map<ViewHandler, List<ViewAction>> myMenuActions = Maps.newHashMap();

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
      case ABS_LIST_VIEW:
      case ADAPTER_VIEW_FLIPPER:
      case ADAPTER_VIEW_ANIMATOR:
      case GRID_VIEW:
        return new ViewGroupHandler();
      case ABSOLUTE_LAYOUT:
      case WEB_VIEW:
        return new AbsoluteLayoutHandler();
      case ACTION_MENU_VIEW:
        return new ActionMenuViewHandler();
      case ADAPTER_VIEW:
      case STACK_VIEW:
        return new AdapterViewHandler();
      case AD_VIEW:
        return new AdViewHandler();
      case APP_BAR_LAYOUT:
        return new AppBarLayoutHandler();
      case AUTO_COMPLETE_TEXT_VIEW:
        return new AutoCompleteTextViewHandler();
      case EDIT_TEXT:
        return new EditTextHandler();
      case MULTI_AUTO_COMPLETE_TEXT_VIEW:
      case TEXT_VIEW:
        return TEXT_HANDLER;
      case BROWSE_FRAGMENT:
        return new BrowseFragmentHandler();
      case BUTTON:
        return new ButtonHandler();
      case CARD_VIEW:
        return new CardViewHandler();
      case CHECK_BOX:
      case RADIO_BUTTON:
        return new CheckBoxHandler();
      case CHECKED_TEXT_VIEW:
        return new CheckedTextViewHandler();
      case CHRONOMETER:
        return new ChronometerHandler();
      case QUICK_CONTACT_BADGE:
      case TEXT_CLOCK:
        return STANDARD_HANDLER;
      case COLLAPSING_TOOLBAR_LAYOUT:
        return new CollapsingToolbarLayoutHandler();
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
      case DRAWER_LAYOUT:
        return new DrawerLayoutHandler();
      case EXPANDABLE_LIST_VIEW:
        // TODO: Find out why this fails to load by class name
        return new ListViewHandler();
      case FLOATING_ACTION_BUTTON:
        return new FloatingActionButtonHandler();
      case FQCN_LINEAR_LAYOUT:
      case LINEAR_LAYOUT:
      case SEARCH_VIEW:
        return new LinearLayoutHandler();
      case FRAME_LAYOUT:
      case GESTURE_OVERLAY_VIEW:
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
      case IMAGE_SWITCHER:
        return new ImageSwitcherHandler();
      case MAP_FRAGMENT:
        return new MapFragmentHandler();
      case MAP_VIEW:
        return new MapViewHandler();
      case NAVIGATION_VIEW:
        return new NavigationViewHandler();
      case NESTED_SCROLL_VIEW:
        return new NestedScrollViewHandler();
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
        return new PreferenceScreenHandler();
      case PreferenceTags.RINGTONE_PREFERENCE:
        return new RingtonePreferenceHandler();
      case PreferenceTags.SWITCH_PREFERENCE:
        return new SwitchPreferenceHandler();
      case RATING_BAR:
        return new RatingBarHandler();
      case RECYCLER_VIEW:
        return new RecyclerViewHandler();
      case REQUEST_FOCUS:
        return new RequestFocusHandler();
      case SCROLL_VIEW:
        return new ScrollViewHandler();
      case SEARCH_FRAGMENT:
        return new SearchFragmentHandler();
      case SEEK_BAR:
        return new SeekBarHandler();
      case SPACE:
        return new SpaceHandler();
      case SURFACE_VIEW:
      case TEXTURE_VIEW:
      case VIDEO_VIEW:
        return NO_PREVIEW_HANDLER;
      case SPINNER:
        return new SpinnerHandler();
      case SWITCH:
        return new SwitchHandler();
      case TABLE_CONSTRAINT_LAYOUT:
        return new ConstraintLayoutHandler();
      case TABLE_LAYOUT:
        return new TableLayoutHandler();
      case TABLE_ROW:
        return new TableRowHandler();
      case TAB_HOST:
        return new TabHostHandler();
      case TAB_LAYOUT:
        return new TabLayoutHandler();
      case TAG_GROUP:
        return new GroupHandler();
      case TAG_ITEM:
        return new ItemHandler();
      case TAG_MENU:
        return new MenuHandler();
      case TEXT_INPUT_LAYOUT:
        return new TextInputLayoutHandler();
      case TOGGLE_BUTTON:
        return new ToggleButtonHandler();
      case TOOLBAR_V7:
        return new ToolbarHandler();
      case VIEW_FRAGMENT:
        return new FragmentHandler();
      case VIEW_INCLUDE:
        return new IncludeHandler();
      case VIEW_MERGE:
        return new MergeHandler();
      case VIEW_PAGER:
        return new ViewPagerHandler();
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

    return ApplicationManager.getApplication().runReadAction((Computable<ViewHandler>)() -> {
      String qualifiedClassName = getFullyQualifiedClassName(viewTag);
      if (qualifiedClassName != null) {
        try {
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
        catch (IndexNotReadyException ignore) {
          // TODO: Fix the bug: b.android.com/210064
          return NONE;
        }
      }
      return NONE;
    });
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

  /**
   * Get the toolbar view actions for the given handler.
   * <p>
   * This method will call {@link ViewHandler#addToolbarActionsToMenu(String, List)}
   * but will cache results across invocations.
   *
   * @param handler the handler to look up actions for
   * @return the associated view actions.
   */
  public List<ViewAction> getToolbarActions(@NotNull ViewHandler handler) {
    List<ViewAction> actions = myToolbarActions.get(handler);
    if (actions == null) {
      actions = Lists.newArrayList();
      handler.addToolbarActions(actions);
      myToolbarActions.put(handler, actions);
    }
    return actions;
  }

  /**
   * Get the popup menu view actions for the given handler.
   * <p>
   * This method will call {@link ViewHandler#addPopupMenuActions(List)} (String, List)}
   * but will cache results across invocations.
   *
   * @param handler the handler to look up actions for
   * @return the associated view actions.
   */
  public List<ViewAction> getPopupMenuActions(@NotNull ViewHandler handler) {
    List<ViewAction> actions = myMenuActions.get(handler);
    if (actions == null) {
      actions = Lists.newArrayList();
      handler.addPopupMenuActions(actions);
      myMenuActions.put(handler, actions);
    }
    return actions;
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
