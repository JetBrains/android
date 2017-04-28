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
package com.android.tools.idea.uibuilder.error;

import com.android.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Panel that displays a list of rendering errors.
 *
 * {@see RenderErrorModel}
 */
public class IssuePanel extends JPanel implements Disposable {
  public static final String ISSUE_PANEL_NAME = "Layout Editor Error Panel";
  private static final String PROPERTY_MINIMIZED = IssuePanel.class.getCanonicalName() + ".minimized";
  private static final String TITLE_NO_ISSUES = "No issues";
  private static final String TITLE_NO_IMPORTANT_ISSUE = "Issues";
  private static final String WARNING = "Warning";
  private static final String ERROR = "Error";

  private final HashMap<NlIssue, IssueView> myDisplayedError = new HashMap<>();
  private final IssueModel myIssueModel;
  private final JPanel myErrorListPanel;
  private final JBLabel myTitleLabel;
  private final IssueModel.IssueModelListener myIssueModelListener;
  @Nullable private MinimizeListener myMinimizeListener;
  @Nullable private IssueView mySelectedIssueView;

  /**
   * Whether the user has seen the issues or not. We consider the issues "seen" if the panel is not minimized
   */
  private boolean hasUserSeenNewErrors;
  private boolean isMinimized;

  public IssuePanel(IssueModel issueModel) {
    super(new BorderLayout());
    setName(ISSUE_PANEL_NAME);
    myIssueModel = issueModel;

    myTitleLabel = createTitleLabel();
    JComponent titlePanel = createTitlePanel(createToolbar(), myTitleLabel);
    add(titlePanel, BorderLayout.NORTH);

    myErrorListPanel = createErrorListPanel();
    add(createListScrollPane(myErrorListPanel), BorderLayout.CENTER);
    setMinimized(PropertiesComponent.getInstance().getBoolean(PROPERTY_MINIMIZED, false));
    updateTitlebarStyle();

    myIssueModelListener = this::updateErrorList;
    myIssueModel.addErrorModelListener(myIssueModelListener);
    updateErrorList();
  }

  @NotNull
  private ActionToolbar createToolbar() {
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, createActionGroup(), true);
    toolbar.setMiniMode(true);
    return toolbar;
  }

  @NotNull
  private static JComponent createTitlePanel(@NotNull ActionToolbar toolbar, @NotNull JBLabel label) {
    JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
    titlePanel.add(toolbar.getComponent());
    titlePanel.add(label);
    titlePanel.setAlignmentX(CENTER_ALIGNMENT);
    return titlePanel;
  }

  @NotNull
  private static JBScrollPane createListScrollPane(@NotNull JPanel content) {
    JBScrollPane pane = new JBScrollPane(content);
    pane.setBorder(null);
    pane.setAlignmentX(CENTER_ALIGNMENT);
    return pane;
  }

  @NotNull
  private static JPanel createErrorListPanel() {
    JPanel panel = new JPanel(null, true);
    panel.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP));
    panel.setBackground(UIUtil.getEditorPaneBackground());
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    return panel;
  }

  @NotNull
  private static JBLabel createTitleLabel() {
    JBLabel label = new JBLabel(TITLE_NO_IMPORTANT_ISSUE, SwingConstants.LEFT);
    label.setBorder(JBUI.Borders.empty(0, 5, 0, 20));
    return label;
  }

  @NotNull
  private ActionGroup createActionGroup() {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(new MinimizeAction());
    return actionGroup;
  }

  private void updateErrorList() {
    UIUtil.invokeLaterIfNeeded(() -> {
      if (!myIssueModel.hasIssues()) {
        myTitleLabel.setText(TITLE_NO_ISSUES);
        myDisplayedError.clear();
        myErrorListPanel.removeAll();
        setMinimized(true);
        return;
      }
      updateTitlebarStyle();
      boolean needsRevalidate = false;
      List<NlIssue> nlIssues = myIssueModel.getNlErrors();
      if (myDisplayedError.isEmpty()) {
        nlIssues.forEach(this::addErrorEntry);
      }
      else {
        removeOldIssues(nlIssues);
        needsRevalidate = displayNewIssues(nlIssues);
      }
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
    myTitleLabel.setFont(myTitleLabel.getFont().deriveFont(!isMinimized() || hasUserSeenNewErrors ? Font.PLAIN : Font.BOLD));

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

  private boolean displayNewIssues(@NotNull List<NlIssue> nlIssues) {
    boolean needsRevalidate = false;
    for (NlIssue error : nlIssues) {
      if (!myDisplayedError.containsKey(error)) {
        addErrorEntry(error);
        needsRevalidate = true;
      }
    }
    return needsRevalidate;
  }

  private void removeOldIssues(@NotNull List<NlIssue> nlIssues) {
    Iterator<Map.Entry<NlIssue, IssueView>> iterator = myDisplayedError.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<NlIssue, IssueView> entry = iterator.next();
      if (!nlIssues.contains(entry.getKey())) {
        myErrorListPanel.remove(entry.getValue().getComponent());
        iterator.remove();
      }
    }
  }

  private void addErrorEntry(@NotNull NlIssue error) {
    if (myErrorListPanel.getComponentCount() == 0) {
      myErrorListPanel.add(Box.createVerticalGlue(), -1);
    }
    IssueView issueView = IssueView.createFromNlError(error, this);
    myDisplayedError.put(error, issueView);
    myErrorListPanel.add(issueView.getComponent(), myErrorListPanel.getComponentCount() - 1); // We want to keep the glue at the end
    if (myDisplayedError.size() == 1) {
      setSelectedIssue(issueView);
    }
  }

  @Nullable
  public IssueView getSelectedIssueView() {
    return mySelectedIssueView;
  }

  @Override
  public void doLayout() {
    // Compute the Category and source column size so they take the minimum space
    int categoryColumnSize = 0;
    int sourceColumnSize = 0;
    Collection<IssueView> values = myDisplayedError.values();
    for (IssueView view : values) {
      categoryColumnSize = Math.max(categoryColumnSize, view.getCategoryLabelWidth());
      sourceColumnSize = Math.max(sourceColumnSize, view.getSourceLabelWidth());
    }
    for (IssueView view : values) {
      view.setCategoryLabelSize(categoryColumnSize);
      view.setSourceLabelSize(sourceColumnSize);
    }
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

    if (myMinimizeListener != null) {
      UIUtil.invokeLaterIfNeeded(() -> myMinimizeListener.onMinimizeChanged(isMinimized));
    }
  }

  public void setMinimizeListener(@Nullable MinimizeListener listener) {
    myMinimizeListener = listener;
  }

  /**
   * Listener to be notified when this panel is minimized
   */
  @Override
  public void dispose() {
    myMinimizeListener = null;
    myIssueModel.removeErrorModelListener(myIssueModelListener);
  }

  public boolean isMinimized() {
    return isMinimized;
  }

  public void setSelectedIssue(@Nullable IssueView selectedIssue) {
    if (mySelectedIssueView != selectedIssue) {
      if (mySelectedIssueView != null) {
        mySelectedIssueView.setSelected(false);
      }
      mySelectedIssueView = selectedIssue;
      if (mySelectedIssueView != null) {
        mySelectedIssueView.setSelected(true);
      }
    }
  }

  @VisibleForTesting
  public String getTitleText() {
    return myTitleLabel.getText();
  }

  @VisibleForTesting
  public IssueModel getIssueModel() {
    return myIssueModel;
  }

  @VisibleForTesting
  public boolean containsErrorWithText(@NotNull String text) {
    return myDisplayedError.values()
      .stream()
      .anyMatch(view -> view.getIssueTitle().contains(text) || view.getIssueDescription().contains(text));
  }

  public interface MinimizeListener {
    void onMinimizeChanged(boolean isMinimized);
  }

  /**
   * Action invoked by the user to minimize or restore the errors panel
   */
  private class MinimizeAction extends AnAction {
    private static final String DESCRIPTION = "Hide the render errors panel";

    private MinimizeAction() {
      super(DESCRIPTION, DESCRIPTION, AllIcons.Ide.Notification.Close);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      setMinimized(true);
      // If the user acts on the minimize button, save the preference
      PropertiesComponent.getInstance().setValue(PROPERTY_MINIMIZED, true, false);
    }
  }
}
