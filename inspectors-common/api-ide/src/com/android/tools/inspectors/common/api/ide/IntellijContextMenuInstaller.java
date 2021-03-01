/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.inspectors.common.api.ide;

import com.android.tools.adtui.stdui.ContextMenuItem;
import com.android.tools.inspectors.common.api.actions.NavigateToCodeAction;
import com.android.tools.inspectors.common.api.stacktrace.CodeLocation;
import com.android.tools.inspectors.common.api.stacktrace.CodeNavigator;
import com.android.tools.inspectors.common.ui.ContextMenuInstaller;
import com.intellij.ide.actions.CopyAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.ui.PopupHandler;
import java.awt.Component;
import java.util.Arrays;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import org.jetbrains.annotations.NotNull;

public class IntellijContextMenuInstaller implements ContextMenuInstaller {
  private static final String COMPONENT_CONTEXT_MENU = "ComponentContextMenu";

  /**
   * Cache of the X mouse coordinate where the {@link JPopupMenu} is opened.
   */
  private int myCachedX = -1;

  @Override
  public void installGenericContextMenu(@NotNull JComponent component, @NotNull ContextMenuItem contextMenuItem,
                                        @NotNull IntPredicate itemEnabled, @NotNull IntConsumer callback) {
    DefaultActionGroup popupGroup = createOrGetActionGroup(component);
    if (contextMenuItem.equals(ContextMenuItem.SEPARATOR)) {
      popupGroup.addSeparator();
      return;
    }

    // Reuses the IDE CopyAction, it makes the action component provides the data without exposing the internal implementation.
    if (contextMenuItem.equals(ContextMenuItem.COPY)) {
      popupGroup.add(new CopyAction() {
        {
          getTemplatePresentation().setText(contextMenuItem.getText());
          getTemplatePresentation().setIcon(contextMenuItem.getIcon());
          registerCustomShortcutSet(CommonShortcuts.getCopy(), component);
        }
      });
      return;
    }

    AnAction action = new AnAction() {
      @Override
      public void update(@NotNull AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        presentation.setText(contextMenuItem.getText());
        presentation.setIcon(contextMenuItem.getIcon());
        presentation.setEnabled(itemEnabled.test(myCachedX));
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        callback.accept(myCachedX);
      }
    };

    action.registerCustomShortcutSet(new ShortcutSet() {
      @NotNull
      @Override
      public Shortcut[] getShortcuts() {
        return Arrays.stream(contextMenuItem.getKeyStrokes()).filter(keyStroke -> keyStroke != null)
          .map(keyStroke -> new KeyboardShortcut(keyStroke, null)).toArray(size -> new Shortcut[size]);
      }
    }, component);
    popupGroup.add(action);
  }

  @Override
  public void installNavigationContextMenu(@NotNull JComponent component,
                                           @NotNull CodeNavigator navigator,
                                           @NotNull Supplier<CodeLocation> codeLocationSupplier) {
    DefaultActionGroup popupGroup = createOrGetActionGroup(component);
    popupGroup.add(new NavigateToCodeAction(codeLocationSupplier, navigator));
  }

  @NotNull
  private DefaultActionGroup createOrGetActionGroup(@NotNull JComponent component) {
    DefaultActionGroup actionGroup = (DefaultActionGroup)component.getClientProperty(COMPONENT_CONTEXT_MENU);
    if (actionGroup == null) {
      final DefaultActionGroup newActionGroup = new DefaultActionGroup();
      component.putClientProperty(COMPONENT_CONTEXT_MENU, newActionGroup);
      component.addMouseListener(new PopupHandler() {
        @Override
        public void invokePopup(Component comp, int x, int y) {
          myCachedX = x;
          ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, newActionGroup).getComponent().show(comp, x, y);
        }
      });
      actionGroup = newActionGroup;
    }

    return actionGroup;
  }
}
