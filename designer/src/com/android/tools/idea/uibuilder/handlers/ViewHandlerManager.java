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
import com.android.tools.idea.uibuilder.api.ViewHandler;
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
  private final Project myProject;
  private final Map<String, ViewHandler> myHandlers = Maps.newHashMap();
  private static final ViewHandler NONE = new ViewHandler();

  @NonNull
  public static ViewHandlerManager get(@NonNull Project project) {
    return project.getComponent(ViewHandlerManager.class);
  }

  @NonNull
  public static ViewHandlerManager get(@NonNull AndroidFacet facet) {
    return get(facet.getModule().getProject());
  }

  public ViewHandlerManager(@NonNull Project project) {
    myProject = project;
  }

  @Nullable
  public ViewHandler getHandler(@NonNull String viewTag) {
    ViewHandler handler = myHandlers.get(viewTag);
    if (handler == null) {
      handler = createHandler(viewTag);
      myHandlers.put(viewTag, handler);
    }

    return handler != NONE ? handler : null;
  }

  private ViewHandler createHandler(@NonNull String viewTag) {
    // Builtin view. Don't bother with reflection for the common cases.
    if (LINEAR_LAYOUT.equals(viewTag) || FQCN_LINEAR_LAYOUT.equals(viewTag)) {
      return new LinearLayoutHandler();
    }
    if (RELATIVE_LAYOUT.equals(viewTag) || FQCN_RELATIVE_LAYOUT.equals(viewTag)) {
      return new RelativeLayoutHandler();
    }
    if (ABSOLUTE_LAYOUT.equals(viewTag)) {
      return new LinearLayoutHandler();
    }

    if (viewTag.indexOf('.') != -1) {
      String handlerName = viewTag + "Handler";
      JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
      PsiClass[] classes = facade.findClasses(handlerName, GlobalSearchScope.allScope(myProject));
      for (PsiClass cls : classes) {
        // Look for bytecode and instantiate if possible, then return
        // TODO: Instantiate
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
