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

import static com.android.SdkConstants.ATTR_PARENT_TAG;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.VIEW_MERGE;
import static com.android.tools.idea.uibuilder.model.ClassResolutionUtilsKt.findClassesForViewTag;

import com.android.annotations.concurrency.UiThread;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.actions.ViewAction;
import com.android.tools.idea.uibuilder.menu.MenuViewHandlerManager;
import com.android.tools.idea.uibuilder.model.NlComponentHelper;
import com.android.tools.idea.uibuilder.statelist.ItemHandler;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Tracks and provides {@link ViewHandler} instances in this project
 */
public class ViewHandlerManager implements Disposable {
  @VisibleForTesting
  static final ExtensionPointName<ViewHandlerProvider> EP_NAME =
    ExtensionPointName.create("com.android.tools.idea.uibuilder.handlers.viewHandlerProvider");

  /**
   * View handlers are named the same as the class for the view they represent, plus this suffix
   */
  private static final String HANDLER_CLASS_SUFFIX = "Handler";

  /**
   *  Callback used when making a handler lookup of a super class of the class we currently are finding a handler for.
   * {See #createViaIndexLookup}.
   */
  private static final Runnable SUPER_CLASS_LOOKUP = () -> {};

  private final Project myProject;
  private final Map<String, ViewHandler> myHandlers = Collections.synchronizedMap(Maps.newHashMap());
  private final Map<ViewHandler, List<ViewAction>> myToolbarActions = Maps.newHashMap();
  private final Map<ViewHandler, List<ViewAction>> myMenuActions = Maps.newHashMap();

  /**
   * A {@link ViewHandler} return when no real {@link ViewHandler} can be found.
   */
  public static final ViewHandler NONE = new ViewHandler();

  /**
   * A {@link ViewHandler} return when the lookup is delayed because an index lookup was needed.
   */
  public static final ViewHandler TEMP = new ViewHandler();

  @NotNull
  public static ViewHandlerManager get(@NotNull Project project) {
    ViewHandlerManager manager = project.getService(ViewHandlerManager.class);
    assert manager != null;
    return manager;
  }

  /**
   * Returns the ViewHandlerManager for the current project
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
   * @param handlerUpdated if a component handler lookup require an index lookup, the lookup is
   *                       performed on a background thread and <code>null</code> is returned.
   *                       This callback is called if the background finds a handler.
   * @return the corresponding view handler, or null if not found or an index lookup was delayed
   */
  @Nullable
  public ViewHandler getHandler(@NotNull NlComponent component, @NotNull Runnable handlerUpdated) {
    ViewHandler handler = getHandlerOrDefault(component, handlerUpdated);
    return handler != NONE && handler != TEMP ? handler : null;
  }

  /**
   * Gets the {@link ViewHandler} associated with a given component.
   *
   * @param component the component to find a handler for
   * @param handlerUpdated if a component handler lookup require an index lookup, the lookup is
   *                       performed on a background thread and {@link #TEMP} is returned.
   *                       This callback is called if the background finds a handler.
   * If there is no custom handler found returns an instance of {@link ViewHandler} ({@link #TEMP} if an index lookup was delayed).
   */
  @NotNull
  public ViewHandler getHandlerOrDefault(@NotNull NlComponent component, @NotNull Runnable handlerUpdated) {
    String tag = component.getTagName();

    switch (tag) {
      case TAG_ITEM:
        ViewHandler handler = MenuViewHandlerManager.getHandler(component);

        if (handler != null) {
          return handler;
        }

        return new ItemHandler();

      case VIEW_MERGE:
        String parentTag = component.getAttribute(TOOLS_URI, ATTR_PARENT_TAG);
        if (parentTag != null) {
          ViewHandler groupHandler = getHandlerOrDefault(parentTag, handlerUpdated);
          if (groupHandler instanceof ViewGroupHandler) {
            return new MergeDelegateHandler((ViewGroupHandler)groupHandler);
          }
        }
        return getHandlerOrDefault(VIEW_MERGE, handlerUpdated);

      default:
        return getHandlerOrDefault(tag, handlerUpdated);
    }
  }

  /**
   * Gets the {@link ViewHandler} associated with the given XML tag, if any
   *
   * @param viewTag the tag to look up
   * @param handlerUpdated if a component handler lookup require an index lookup, the lookup is
   *                       performed on a background thread and <code>null</code> is returned.
   *                       This callback is called if the background finds a handler.
   * @return the corresponding view handler, or null if not found or an index lookup was delayed
   */
  @Nullable
  public ViewHandler getHandler(@NotNull String viewTag, @NotNull Runnable handlerUpdated) {
    ViewHandler handler = getHandlerOrDefault(viewTag, handlerUpdated);
    return handler != NONE && handler != TEMP ? handler : null;
  }

  /**
   * Gets the {@link ViewHandler} associated with the given XML tag.
   *
   * @param viewTag the tag to look up
   * @param handlerUpdated if a component handler lookup require an index lookup, the lookup is
   *                       performed on a background thread and {@link #TEMP} is returned.
   *                       This callback is called if the background finds a handler.
   * If there is no custom handler found returns an instance of {@link ViewHandler} ({@link #TEMP} if an index lookup was delayed).
   */
  @NotNull
  public ViewHandler getHandlerOrDefault(@NotNull String viewTag, @NotNull Runnable handlerUpdated) {
    ViewHandler handler = myHandlers.get(viewTag);
    if (handler == null || (handler == TEMP && handlerUpdated == SUPER_CLASS_LOOKUP)) {
      if (viewTag.indexOf('.') != -1) {
        String tag = NlComponentHelper.INSTANCE.viewClassToTag(viewTag);
        if (!tag.equals(viewTag)) {
          handler = getHandlerOrDefault(tag, handlerUpdated);
          // Alias fully qualified widget name to tag
          myHandlers.put(viewTag, handler);
          return handler;
        }
      }

      handler = createHandler(viewTag, handlerUpdated);
      myHandlers.put(viewTag, handler);
    }
    return handler;
  }

  /**
   * Registers a {@link ViewHandler}
   *
   * @param viewTag the tag of the view
   * @param handler corresponding view handler
   */
  public void registerHandler(@NotNull String viewTag, @NotNull ViewHandler handler) {
    myHandlers.put(viewTag, handler);
  }

  /**
   * Finds the nearest layout/view group handler for the given component.
   *
   * @param component      the component to search from
   * @param strict         if true, only consider parents of the component, not the component itself
   * @param handlerUpdated if a component handler lookup require an index lookup, the lookup is
   *                       performed on a background thread and <code>null</code> is returned.
   *                       This callback is called if the background finds a handler.
   */
  @Nullable
  public ViewGroupHandler findLayoutHandler(@NotNull NlComponent component, boolean strict, @NotNull Runnable handlerUpdated) {
    NlComponent curr = component;
    if (strict) {
      curr = curr.getParent();
    }
    while (curr != null) {
      ViewHandler handler = getHandler(curr, handlerUpdated);
      if (handler instanceof ViewGroupHandler) {
        return (ViewGroupHandler)handler;
      }

      curr = curr.getParent();
    }

    return null;
  }

  @NotNull
  private ViewHandler createHandler(@NotNull String viewTag, @NotNull Runnable handlerUpdated) {
    // Check if there are any built-in handlers. Do not bother with reflection in these cases.
    ViewHandler builtInHandler = BuiltinViewHandlerProvider.INSTANCE.findHandler(viewTag);
    if (builtInHandler != null) {
      return builtInHandler;
    }

    // No built-in handler found. Allow extensions to return one
    final ViewHandler extensionHandler = EP_NAME.getExtensionList(myProject)
      .stream()
      .map(extension -> extension.findHandler(viewTag))
      .filter(Objects::nonNull)
      .limit(2)
      .reduce(null, (a, b) -> {
        if (a != null && b != null) {
          Logger.getInstance(ViewHandler.class).warn("Multiple ViewHandlers returned by extensions for tag " + viewTag);
          return a;
        }

        return a != null ? a : b;
      });
    if (extensionHandler != null) {
      return extensionHandler;
    }

    Logger.getInstance(ViewHandler.class).debug("No built-in or extension defined ViewHandlers found for " + viewTag);
    // Look for other handlers via reflection; first built into the IDE:
    try {
      String defaultHandlerPkgPrefix = "com.android.tools.idea.uibuilder.handlers.";
      String handlerClass = defaultHandlerPkgPrefix + viewTag + HANDLER_CLASS_SUFFIX;
      @SuppressWarnings("unchecked") Class<? extends ViewHandler> cls = (Class<? extends ViewHandler>)Class.forName(handlerClass);
      return cls.newInstance();
    }
    catch (Exception ignore) {
    }

    // We were not able to find built-in or extension defined handlers. We also could not get them via reflection. The last step is
    // to try searching in the users source code and try to use that one.
    if (handlerUpdated == SUPER_CLASS_LOOKUP || !ApplicationManager.getApplication().isDispatchThread()) {
      return createViaIndexLookup(viewTag, null);
    }

    // Find the handler on a background thread.
    ApplicationManager.getApplication().executeOnPooledThread(() -> createViaIndexLookup(viewTag, handlerUpdated));

    // Return a temporary ViewHandler, that will be replaced when the index lookup finishes.
    return TEMP;
  }

  @NotNull
  private ViewHandler createViaIndexLookup(@NotNull String viewTag, @Nullable Runnable handlerUpdated) {
    if (myProject.isDisposed()) {
      return NONE;
    }
    Logger.getInstance(ViewHandler.class).debug("Looking for user code defined handlers for " + viewTag);
    return ApplicationManager.getApplication().runReadAction((Computable<ViewHandler>)() -> {
      if (myProject.isDisposed()) {
        return NONE;
      }

      try {
        PsiClass[] viewClasses = findClassesForViewTag(myProject, viewTag);
        if (viewClasses.length > 0) {
          String handlerName = viewTag + HANDLER_CLASS_SUFFIX;
          PsiClass[] handlerClasses = JavaPsiFacade.getInstance(myProject).findClasses(handlerName, GlobalSearchScope.allScope(myProject));

          if (handlerClasses.length == 0) {
            // No view handler found for this class; look up the custom view and get the handler for its
            // parent view instead. For example, if you've customized a LinearLayout by subclassing it, then
            // if you don't provide a ViewHandler for the subclass, we dall back to the LinearLayout's
            // ViewHandler instead.
            for (PsiClass cls : viewClasses) {
              PsiClass superClass = cls.getSuperClass();
              if (superClass != null) {
                String fqn = superClass.getQualifiedName();
                if (fqn != null) {
                  // When making a lookup of the super class, we don't want the original caller to be notified by handlerUpdated,
                  // and we don't want to start an index lookup on another thread. Indicate that by passing the constant RECURSIVE_CALLBACK.
                  ViewHandler handler = getHandlerOrDefault(NlComponentHelper.INSTANCE.viewClassToTag(fqn), SUPER_CLASS_LOOKUP);
                  if (handler == TEMP) {
                    Logger.getInstance(ViewHandler.class).warn("Unexpectedly got: TEMP for: " + viewTag);
                    handler = NONE;
                  }
                  if (handler != NONE && handlerUpdated != null) {
                    handlerUpdated.run();
                  }
                  myHandlers.put(viewTag, handler);
                  return handler;
                }
              }
            }
          }
          else {
            for (PsiClass cls : handlerClasses) {
              // Look for bytecode and instantiate if possible, then return
              // TODO: Instantiate
              Logger.getInstance(ViewHandler.class).debug(String.format(
                "Found view handler %s  of type %s", cls.getQualifiedName(), cls.getClass().getName()));
            }
          }
        }
      }
      catch (IndexNotReadyException ignore) {
        // TODO: Fix the bug: b.android.com/210064
        myHandlers.put(viewTag, NONE);
        return NONE;
      }
      myHandlers.put(viewTag, NONE);
      return NONE;
    });
  }

  /**
   * Get the toolbar view actions for the given handler.
   * <p>
   * This method will call {@link ViewHandler#addToolbarActions(List)}
   * but will cache results across invocations.
   *
   * @param handler the handler to look up actions for
   * @return the associated view actions.
   */
  @NotNull
  @UiThread
  public List<ViewAction> getToolbarActions(@NotNull ViewHandler handler) {
    List<ViewAction> actions = myToolbarActions.get(handler);
    if (actions == null) {
      actions = new ArrayList<>();
      handler.addToolbarActions(actions);
      myToolbarActions.put(handler, actions);
    }
    return actions;
  }

  /**
   * Get the popup menu view actions for the given handler.
   * <p>
   * This method will call {@link ViewHandler#addPopupMenuActions(SceneComponent, List)} (String, List)}
   * but will cache results across invocations.
   *
   * @param component the component clicked on
   * @param handler the handler to look up actions for
   * @return the associated view actions.
   */
  @NotNull
  @UiThread
  public List<ViewAction> getPopupMenuActions(@NotNull SceneComponent component, @NotNull ViewHandler handler) {
    List<ViewAction> actions = myMenuActions.get(handler);
    if (actions == null) {
      actions = new ArrayList<>();
      if (handler.addPopupMenuActions(component, actions)) {
        myMenuActions.put(handler, actions);
      }
    }
    return actions;
  }

  @Override
  public void dispose() {
    myHandlers.clear();
  }

  /**
   * Clears the internal handler cache. Only for testing.
   */
  @TestOnly
  void clearCache() {
    myHandlers.clear();
  }
}