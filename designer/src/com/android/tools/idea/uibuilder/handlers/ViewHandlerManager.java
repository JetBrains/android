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
import com.android.tools.idea.uibuilder.handlers.grid.GridLayoutHandler;
import com.android.tools.idea.uibuilder.handlers.relative.RelativeLayoutHandler;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.google.common.collect.Maps;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.android.facet.AndroidFacet;

import java.util.Map;

import static com.android.SdkConstants.*;

/**
 * Tracks and provides {@link ViewHandler} instances in this project
 */
public class ViewHandlerManager implements ProjectComponent {
  /**
   * View handlers are named the same as the class for the view they represent, plus this suffix
   */
  public static final String HANDLER_CLASS_SUFFIX = "Handler";

  private final Project myProject;
  private final Map<String, ViewHandler> myHandlers = Maps.newHashMap();
  private static final ViewHandler NONE = new ViewHandler();

  @NonNull
  public static ViewHandlerManager get(@NonNull Project project) {
    return project.getComponent(ViewHandlerManager.class);
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
    if (FRAME_LAYOUT.equals(viewTag)) {
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
    if (RELATIVE_LAYOUT.equals(viewTag) || FQCN_RELATIVE_LAYOUT.equals(viewTag)) {
      return new RelativeLayoutHandler();
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
    if (VIEW_INCLUDE.equals(viewTag)) {
      return new IncludeHandler();
    }
    if (VIEW_FRAGMENT.equals(viewTag)) {
      return new FragmentHandler();
    }
    if (VIEW_TAG.equals(viewTag)) {
      return new ViewTagHandler();
    }
    if (ADAPTER_VIEW.equals(viewTag)) {
      return new AdapterViewHandler();
    }
    if (ABSOLUTE_LAYOUT.equals(viewTag)) {
      return new AbsoluteLayoutHandler();
    }
    if (FLOATING_ACTION_BUTTON.equals(viewTag)) {
      return new FloatingActionButtonHandler();
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

    if (viewTag.indexOf('.') != -1) {
      String handlerName = viewTag + HANDLER_CLASS_SUFFIX;
      JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
      PsiClass[] classes = facade.findClasses(handlerName, GlobalSearchScope.allScope(myProject));

      if (classes.length == 0) {
        // No view handler found for this class; look up the custom view and get the handler for its
        // parent view instead. For example, if you've customized a LinearLayout by subclassing it, then
        // if you don't provide a ViewHandler for the subclass, we dall back to the LinearLayout's
        // ViewHandler instead.
        classes = facade.findClasses(viewTag, GlobalSearchScope.allScope(myProject));
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
