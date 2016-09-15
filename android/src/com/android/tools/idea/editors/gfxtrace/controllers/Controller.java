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
package com.android.tools.idea.editors.gfxtrace.controllers;

import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.service.path.PathListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import sun.awt.AppContext;
import javax.swing.Action;
import java.awt.Component;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;

public abstract class Controller implements PathListener, Disposable {
  @NotNull protected final GfxTraceEditor myEditor;

  /**
   * @see javax.swing.text.JTextComponent#FOCUSED_COMPONENT
   */
  private static final Object CURRENT_COMPONENT = new StringBuilder("Navigable_CurrentAction");

  public Controller(@NotNull GfxTraceEditor editor) {
    myEditor = editor;
    myEditor.addPathListener(this);
    Disposer.register(editor, this);
  }

  @Override
  public void dispose() {
    clear();
  }

  public void clear() {
  }

  protected static void setNavigableComponentAction(Component c, Action action) {
    c.addFocusListener(new FocusAdapter() {
      /**
       * @see javax.swing.text.JTextComponent.MutableCaretEvent#focusGained(FocusEvent)
       */
      @Override
      public void focusGained(FocusEvent fe) {
        AppContext.getAppContext().put(CURRENT_COMPONENT, action);
      }
    });
    c.addHierarchyListener(new HierarchyListener() {
      /**
       * @see javax.swing.text.JTextComponent#removeNotify()
       */
      @Override
      public void hierarchyChanged(HierarchyEvent e) {
        if (!c.isShowing() && getCurrentNavigable() == action) {
          AppContext.getAppContext().remove(CURRENT_COMPONENT);
        }
      }
    });
  }

  /**
   * @see javax.swing.text.TextAction#getFocusedComponent()
   */
  public static final Action getCurrentNavigable() {
    return (Action)AppContext.getAppContext().get(CURRENT_COMPONENT);
  }
}
