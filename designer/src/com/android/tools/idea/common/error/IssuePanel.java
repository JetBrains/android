/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.common.error;

import com.android.tools.adtui.common.AdtSecondaryPanel;
import com.android.tools.adtui.util.ActionToolbarUtil;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import icons.StudioIcons;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Panel that displays a list of {@link Issue}.
 */
public class IssuePanel extends JPanel implements Disposable, PropertyChangeListener {
  private static final String ACTION_BAR_PLACE = "issue_panel";

  private static final String ISSUE_PANEL_NAME = "Layout Editor Error Panel";
  private static final String TITLE_NO_ISSUES = "No issues";
  private static final String TITLE_NO_IMPORTANT_ISSUE = "Issues";
  private static final String WARNING = "Warning";
  private static final String ERROR = "Error";


  /**
   * Event listeners for the issue panel
   */
  public interface EventListener {
    /**
     * Called when the panel is expanded or minimized
     */
    void onPanelExpanded(boolean isExpanded);

    /**
     * Called when the individual issue is expanded or minimized
     */
    void onIssueExpanded(@Nullable Issue issue, boolean isExpanded);
  }

  private final IssueModel myIssueModel;
  private final JPanel myErrorListPanel;
  private final JBLabel myTitleLabel;
  private final IssueModel.IssueModelListener myIssueModelListener;
  private final JBScrollPane myScrollPane;
  private final List<EventListener> myEventListeners = new ArrayList<>();
  @Nullable private Issue mySelectedIssue;
  @NotNull final private IssueListener myListener;

  /**
   * Whether the user has seen the issues or not. We consider the issues "seen" if the panel is not minimized
   */
  private boolean hasUserSeenNewErrors;
  private boolean isMinimized;
  private final boolean myInitialized;
  /**
   * If set to false, the panel will not minimize/maximaze without user interaction.
   * The default behaviour is to have the panel automatically collapse if no errors are present.
   */
  private boolean myAutoSize;

  public IssuePanel(@NotNull IssueModel issueModel, @NotNull IssueListener listener) {
    super(new BorderLayout());
    Disposer.register(issueModel, this);
    setName(ISSUE_PANEL_NAME);
    myIssueModel = issueModel;
    myListener = listener;

    myTitleLabel = createTitleLabel();
    JComponent titlePanel = createTitlePanel(myTitleLabel);
    add(titlePanel, BorderLayout.NORTH);

    myErrorListPanel = createErrorListPanel();
    myScrollPane = createListScrollPane(myErrorListPanel);
    add(myScrollPane, BorderLayout.CENTER);
    updateTitlebarStyle();

    myIssueModelListener = this::updateErrorList;
    myIssueModel.addErrorModelListener(myIssueModelListener);
    updateErrorList();

    setFocusable(true);
    setRequestFocusEnabled(true);
    setMinimized(true);
    myAutoSize = true;
    setMinimumSize(JBUI.size(200));
    UIManager.addPropertyChangeListener(this);
    myInitialized = true;
  }

  @Override
  public void updateUI() {
    super.updateUI();
    if (myInitialized) {
      updateTitlebarStyle();
      updateErrorList();
      setMinimumSize(JBUI.size(200));
    }
  }

  @NotNull
  private ActionToolbar createToolbar() {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(new MinimizeAction());
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ACTION_BAR_PLACE, actionGroup, true);
    toolbar.setTargetComponent(this);
    ActionToolbarUtil.makeToolbarNavigable(toolbar);
    toolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    return toolbar;
  }

  @NotNull
  private JComponent createTitlePanel(@NotNull JBLabel titleLabel) {
    JPanel titlePanel = new JPanel(new BorderLayout());
    titlePanel.add(titleLabel, BorderLayout.WEST);
    JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
    rightPanel.add(createToolbar().getComponent());
    titlePanel.add(rightPanel, BorderLayout.EAST);
    titlePanel.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    return titlePanel;
  }

  @NotNull
  private static JBScrollPane createListScrollPane(@NotNull JPanel content) {
    JBScrollPane pane = new JBScrollPane(content);
    pane.setBorder(null);
    pane.setAlignmentX(CENTER_ALIGNMENT);
    pane.getViewport().setBackground(content.getBackground());
    return pane;
  }

  @NotNull
  private static JPanel createErrorListPanel() {
    JPanel panel = new AdtSecondaryPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    return panel;
  }

  @NotNull
  private static JBLabel createTitleLabel() {
    JBLabel label = new JBLabel(TITLE_NO_IMPORTANT_ISSUE, SwingConstants.LEFT);
    label.setBorder(JBUI.Borders.empty(0, 5, 0, 20));
    return label;
  }

  private void updateErrorList() {
    UIUtil.invokeLaterIfNeeded(() -> {
      if (!myIssueModel.hasIssues()) {
        myTitleLabel.setText(TITLE_NO_ISSUES);
        myErrorListPanel.removeAll();
        if (myAutoSize) {
          setMinimized(true);
        }
        return;
      }
      updateTitlebarStyle();
      boolean needsRevalidate = false;
      List<Issue> issues = myIssueModel.getIssues();
      needsRevalidate = displayNewIssues(issues);
      if (needsRevalidate) {
        revalidate();
        repaint();
      }
    });
  }

  /**
   * Updates the titlebar style depending on the current panel state (whether is minimized or has new elements).
   */
  private void updateTitlebarStyle() {
    // If there are new errors and the panel is minimized, set the title to bold
    Font font = StartupUiUtil.getLabelFont();
    if (!hasUserSeenNewErrors && isMinimized()) {
      font = font.deriveFont(Font.BOLD);
    }
    myTitleLabel.setFont(font);

    int warningCount = myIssueModel.getWarningCount();
    int errorCount = myIssueModel.getErrorCount();
    if (warningCount == 0 && errorCount == 0) {
      myTitleLabel.setText(TITLE_NO_IMPORTANT_ISSUE);
    }
    else {
      StringBuilder title = new StringBuilder();
      if (warningCount > 0) {
        title.append(warningCount)
          .append(' ').append(StringUtil.pluralize(WARNING, warningCount)).append(' ');
      }
      if (errorCount > 0) {
        title.append(errorCount)
          .append(' ').append(StringUtil.pluralize(ERROR, errorCount));
      }
      myTitleLabel.setText(title.toString());
    }
  }

  private boolean displayNewIssues(@NotNull List<Issue> issues) {
    boolean needsRevalidate = false;
    for (Issue error : issues) {
    }
    return needsRevalidate;
  }

  @Override
  public void doLayout() {
    int sourceColumnSize = 0;

    super.doLayout();
  }

  public void setMinimized(boolean minimized) {
    if (minimized == isMinimized) {
      return;
    }

    if (!minimized) {
      hasUserSeenNewErrors = true;
    }

    isMinimized = minimized;
    setVisible(!isMinimized);
    revalidate();
    repaint();

    if (!myEventListeners.isEmpty()) {
      UIUtil.invokeLaterIfNeeded(() -> myEventListeners.forEach(
        it -> it.onPanelExpanded(!isMinimized)
      ));
    }
  }

  public void addEventListener(@NotNull EventListener listener) {
    myEventListeners.add(listener);
  }

  /**
   * Listener to be notified when this panel is minimized
   */
  @Override
  public void dispose() {
    myEventListeners.clear();
    mySelectedIssue = null;
    myIssueModel.removeErrorModelListener(myIssueModelListener);
    UIManager.removePropertyChangeListener(this);
  }

  public boolean isMinimized() {
    return isMinimized;
  }

  @VisibleForTesting
  public IssueModel getIssueModel() {
    return myIssueModel;
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if ("lookAndFeel".equals(evt.getPropertyName())) {
      myErrorListPanel.removeAll();
      updateErrorList();
    }
  }

  /**
   * Action invoked by the user to minimize or restore the errors panel
   */
  private class MinimizeAction extends AnAction {
    private static final String DESCRIPTION = "Hide the render errors panel";

    private MinimizeAction() {
      super(DESCRIPTION, DESCRIPTION, StudioIcons.Common.CLOSE);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      setMinimized(true);
    }
  }
}
