/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.common.command;

import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;

// TODO: Refactor this into part of transaction for NlComponent
public final class NlWriteCommandActionUtil {

  // Prevent instantiation...
  private NlWriteCommandActionUtil() {}

  public static void run(@NotNull NlComponent component, @NotNull String name, @NotNull Runnable runnable) {
    run(Collections.singletonList(component), name, runnable);
  }

  public static void run(@NotNull List<NlComponent> components, @NotNull String name, @NotNull Runnable runnable) {
    NlModel model = checkComponents(components);
    Runnable withCleanUp = () -> {
      runnable.run();
      cleanUp(components);
    };
    WriteCommandAction.runWriteCommandAction(model.getProject(), name, null, withCleanUp, model.getFile());
  }

  public static <T> T compute(@NotNull NlComponent component, @NotNull String name, @NotNull Computable<T> computable) {
    return compute(Collections.singletonList(component), name, computable);
  }

  public static <T> T compute(@NotNull List<NlComponent> components, @NotNull String name, @NotNull Computable<T> computable) {
    NlModel model = checkComponents(components);
    Computable<T> withCleanUp = () -> {
      T result = computable.compute();
      cleanUp(components);
      return result;
    };
    return WriteCommandAction.writeCommandAction(model.getProject(), model.getFile()).withName(name).compute(() -> withCleanUp.compute());
  }

  @NotNull
  private static NlModel checkComponents(@NotNull List<NlComponent> components) {
    if (components.isEmpty()) {
      throw new IllegalArgumentException();
    }
    NlModel model = components.get(0).getModel();

    for (NlComponent component : components.subList(1, components.size())) {
      if (component.getModel() != model) {
        throw new IllegalArgumentException();
      }
    }
    return model;
  }

  private static void cleanUp(@NotNull List<NlComponent> components) {
    components.forEach(component -> {
      cleanUpAttributes(component);
      component.getBackend().reformatAndRearrange();
    });
  }

  private static void cleanUpAttributes(@NotNull NlComponent component) {
    Project project = component.getModel().getProject();
    ViewGroupHandler handler = ViewHandlerManager.get(project).findLayoutHandler(component, true);

    if (handler == null) {
      return;
    }

    AttributesTransaction transaction = component.startAttributeTransaction();
    handler.cleanUpAttributes(component, transaction);
    transaction.commit();
  }
}
