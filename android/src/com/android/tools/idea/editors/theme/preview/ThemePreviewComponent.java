/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.editors.theme.preview;

import com.android.tools.idea.configurations.*;
import com.android.tools.idea.editors.theme.ThemeEditorComponent;
import com.android.tools.idea.editors.theme.ThemeEditorContext;
import com.android.tools.idea.ui.SearchField;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Theme preview panel that includes search and configuration settings
 */
public class ThemePreviewComponent extends JPanel implements Disposable {
  private final AndroidThemePreviewPanel myPreviewPanel;
  private final ThemeEditorContext myThemeEditorContext;
  private final ScheduledExecutorService mySearchUpdateScheduler;
  private final SearchField myTextField;
  private final JPanel myToolbar;
  private final JComponent myActionToolbarComponent;
  private ScheduledFuture<?> myScheduledSearch;

  public ThemePreviewComponent(@NotNull ThemeEditorContext context) {
    super(new BorderLayout());

    myThemeEditorContext = context;
    myPreviewPanel = new AndroidThemePreviewPanel(myThemeEditorContext, ThemeEditorComponent.PREVIEW_BACKGROUND);
    myPreviewPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
    Disposer.register(this, myPreviewPanel);

    // Adds the Device selection button
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new OrientationMenuAction(myPreviewPanel));
    group.add(new DeviceMenuAction(myPreviewPanel, false));
    group.add(new TargetMenuAction(myPreviewPanel, true, false));
    group.add(new LocaleMenuAction(myPreviewPanel, false));

    ActionManager actionManager = ActionManager.getInstance();
    ActionToolbar actionToolbar = actionManager.createActionToolbar("ThemeToolbar", group, true);
    actionToolbar.setLayoutPolicy(ActionToolbar.WRAP_LAYOUT_POLICY);

    myToolbar = new JPanel(null);
    myToolbar.setLayout(new BoxLayout(myToolbar, BoxLayout.X_AXIS));
    myToolbar.setBorder(JBUI.Borders.empty(7, 14, 7, 14));

    final JPanel previewPanel = new JPanel(new BorderLayout());
    previewPanel.add(myPreviewPanel, BorderLayout.CENTER);
    previewPanel.add(myToolbar, BorderLayout.NORTH);

    myActionToolbarComponent = actionToolbar.getComponent();
    myToolbar.add(myActionToolbarComponent);

    myTextField = new SearchField(true);
    // Avoid search box stretching more than 1 line.
    myTextField.setMaximumSize(new Dimension(Integer.MAX_VALUE, myTextField.getPreferredSize().height));

    mySearchUpdateScheduler = Executors.newSingleThreadScheduledExecutor(ConcurrencyUtil.newNamedThreadFactory("Theme Editor Searcher"));
    myTextField.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        if (myScheduledSearch != null) {
          myScheduledSearch.cancel(false);
        }

        myScheduledSearch = mySearchUpdateScheduler.schedule(new Runnable() {
          @Override
          public void run() {
            myPreviewPanel.setSearchTerm(myTextField.getText());
          }
        }, 300, TimeUnit.MILLISECONDS);
      }
    });
    myToolbar.add(myTextField);

    add(myPreviewPanel, BorderLayout.CENTER);
    add(myToolbar, BorderLayout.NORTH);

    setPreviewBackground(ThemeEditorComponent.PREVIEW_BACKGROUND);
  }

  public void setPreviewBackground(@NotNull final Color bg) {
    myToolbar.setBackground(bg);
    myActionToolbarComponent.setBackground(bg);

    myTextField.setBackground(bg);
    // If the text field has icons outside of the search field, their background needs to be set correctly
    for (Component component : myTextField.getComponents()) {
      if (component instanceof JLabel) {
        component.setBackground(bg);
      }
    }

    myPreviewPanel.setBackground(bg);
  }

  /**
   * Reloads the preview contents. Call this method when the theme displayed or any of its attributes has changed in order to get the new
   * preview.
   */
  public void reloadPreviewContents() {
    myPreviewPanel.invalidateGraphicsRenderer();
    myPreviewPanel.revalidate();
  }

  @Override
  public void dispose() {
    if (myScheduledSearch != null) {
      myScheduledSearch.cancel(false);
    }
    mySearchUpdateScheduler.shutdownNow();
  }

  /**
   * Returns the theme preview component
   */
  @NotNull
  public AndroidThemePreviewPanel getPreviewPanel() {
    return myPreviewPanel;
  }
}
