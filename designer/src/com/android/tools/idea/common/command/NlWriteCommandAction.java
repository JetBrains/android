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

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.templates.TemplateUtils;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public final class NlWriteCommandAction implements Runnable {
  private final List<NlComponent> myComponents;
  private final String myName;
  private final Runnable myRunnable;

  private final NlModel myModel;

  public NlWriteCommandAction(@NotNull List<NlComponent> components, @NotNull String name, @NotNull Runnable runnable) {
    checkComponents(components);

    myComponents = components;
    myName = name;
    myRunnable = runnable;

    myModel = myComponents.get(0).getModel();
  }

  private static void checkComponents(@NotNull List<NlComponent> components) {
    int size = components.size();

    switch (size) {
      case 0:
        throw new IllegalArgumentException();
      case 1:
        break;
      default:
        Object model = components.get(0).getModel();

        for (NlComponent component : components.subList(1, size)) {
          if (component.getModel() != model) {
            throw new IllegalArgumentException();
          }
        }

        break;
    }
  }

  public static void run(@NotNull NlComponent component, @NotNull String name, @NotNull Runnable runnable) {
    new NlWriteCommandAction(Collections.singletonList(component), name, runnable).run();
  }

  public static void run(@NotNull List<NlComponent> components, @NotNull String name, @NotNull Runnable runnable) {
    new NlWriteCommandAction(components, name, runnable).run();
  }

  @Override
  public void run() {
    new WriteCommandActionImpl().execute();
  }

  private final class WriteCommandActionImpl extends WriteCommandAction.Simple<Void> {
    private WriteCommandActionImpl() {
      super(myModel.getProject(), myName, myModel.getFile());
    }

    @Override
    protected void run() throws Throwable {
      myRunnable.run();

      // TODO caen is refactoring NlModel in a way that will likely break this. Revisit this when he finishes his refactor.
      ViewEditor editor = myModel.getSurface().getViewEditor();

      myComponents.forEach(component -> {
        cleanUpAttributes(editor, component);
        reformatAndRearrange(component);
      });
    }

    private void cleanUpAttributes(@Nullable ViewEditor editor, @NotNull NlComponent component) {
      if (editor == null) {
        return;
      }

      ViewGroupHandler handler = ViewHandlerManager.get(getProject()).findLayoutHandler(component, true);

      if (handler == null) {
        return;
      }

      handler.cleanUpAttributes(editor, component);
    }

    private void reformatAndRearrange(@NotNull NlComponent component) {
      PsiElement tag = component.getTag();

      // noinspection ConstantConditions
      if (tag == null) {
        Logger.getInstance(NlWriteCommandAction.class).warn("Not reformatting " + component + " because its tag is null");
        return;
      }

      if (tag.getContainingFile().getVirtualFile() == null) {
        Logger.getInstance(NlWriteCommandAction.class).warn("Not reformatting " + component + " because its virtual file is null");
        return;
      }

      TemplateUtils.reformatAndRearrange(getProject(), tag);
    }
  }
}