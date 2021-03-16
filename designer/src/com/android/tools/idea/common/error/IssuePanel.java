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
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.surface.DesignSurface;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import icons.StudioIcons;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Panel that displays a list of {@link Issue}.
 */
public class IssuePanel extends JPanel implements Disposable, PropertyChangeListener {
  private static final String ISSUE_PANEL_NAME = "Layout Editor Error Panel";
  private static final String TITLE_NO_ISSUES = "No issues";
  private static final String TITLE_NO_IMPORTANT_ISSUE = "Issues";
  private static final String WARNING = "Warning";
  private static final String ERROR = "Error";
  private static final String ACTION_PREVIOUS = "PREVIOUS";
  private static final String ACTION_NEXT = "next";
  private static final String ACTION_EXPAND = "expand";
  private static final String ACTION_COLLAPSE = "collapse";
  private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");

  /** Event listeners for the issue panel */
  public interface EventListener {
    /** Called when the panel is expanded or minimized */
    void onPanelExpanded(boolean isExpanded);

    /** Called when the individual issue is expanded or minimized */
    void onIssueExpanded(@Nullable Issue issue, boolean isExpanded);
  }

  private final HashBiMap<Issue, IssueView> myDisplayedError = HashBiMap.create();
  private final IssueModel myIssueModel;
  private final JPanel myErrorListPanel;
  private final JBLabel myTitleLabel;
  private final IssueModel.IssueModelListener myIssueModelListener;
  private final JBScrollPane myScrollPane;
  private final DesignSurface mySurface;
  private final ColumnHeaderPanel myColumnHeaderView;
  private final List<EventListener> myEventListeners = new ArrayList<>();
  @Nullable private IssueView mySelectedIssueView;
  @Nullable private Issue mySelectedIssue;

  /**
   * Whether the user has seen the issues or not. We consider the issues "seen" if the panel is not minimized
   */
  private boolean hasUserSeenNewErrors;
  private boolean isMinimized;
  private boolean myInitialized;
  /**
   * If set to false, the panel will not minimize/maximaze without user interaction.
   * The default behaviour is to have the panel automatically collapse if no errors are present.
   */
  private boolean myAutoSize = true;

  public IssuePanel(@NotNull DesignSurface designSurface, @NotNull IssueModel issueModel) {
    super(new BorderLayout());
    setName(ISSUE_PANEL_NAME);
    myIssueModel = issueModel;
    mySurface = designSurface;

    myTitleLabel = createTitleLabel();
    JComponent titlePanel = createTitlePanel(myTitleLabel);
    add(titlePanel, BorderLayout.NORTH);

    myErrorListPanel = createErrorListPanel();
    myScrollPane = createListScrollPane(myErrorListPanel);
    myColumnHeaderView = new ColumnHeaderPanel();
    myScrollPane.setColumnHeaderView(myColumnHeaderView);
    add(myScrollPane, BorderLayout.CENTER);
    updateTitlebarStyle();

    myIssueModelListener = this::updateErrorList;
    myIssueModel.addErrorModelListener(myIssueModelListener);
    updateErrorList();

    setFocusable(true);
    setRequestFocusEnabled(true);
    registerKeyboardActions();
    addFocusListener(createFocusListener());
    setMinimized(true);
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
  private FocusListener createFocusListener() {
    return new FocusListener() {

      @Override
      public void focusGained(FocusEvent e) {
        if (mySelectedIssueView != null) {
          mySelectedIssueView.setFocused(true);
        }
      }

      @Override
      public void focusLost(FocusEvent e) {
        if (mySelectedIssueView != null) {
          mySelectedIssueView.setFocused(false);
        }
      }
    };
  }

  private void registerKeyboardActions() {
    getActionMap().put(ACTION_PREVIOUS, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        keyboardSelect(-1);
      }
    });
    getActionMap().put(ACTION_NEXT, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        keyboardSelect(1);
      }
    });
    getActionMap().put(ACTION_EXPAND, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        expandSelectedIssue(true);
      }
    });
    getActionMap().put(ACTION_COLLAPSE, new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        expandSelectedIssue(false);
      }
    });
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), ACTION_PREVIOUS);
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), ACTION_NEXT);
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), ACTION_EXPAND);
    getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), ACTION_COLLAPSE);
  }

  private void expandSelectedIssue(boolean expanded) {
    if (mySelectedIssueView != null) {
      mySelectedIssueView.setExpanded(expanded);
    }
  }

  private void keyboardSelect(int direction) {
    if (!myDisplayedError.isEmpty()) {
      if (mySelectedIssueView == null) {
        Component component = myErrorListPanel.getComponent(0);
        if (component instanceof IssueView) {
          setSelectedIssue((IssueView)component);
          return;
        }
      }
    }
    Component[] components = myErrorListPanel.getComponents();
    for (int i = 0; i < components.length; i++) {
      Component component = components[i];
      if (component == mySelectedIssueView) {
        int selectedIndex = (i + (direction >= 0 ? 1 : -1)) % myDisplayedError.size();
        if (selectedIndex < 0) selectedIndex += myDisplayedError.size();
        assert components[i] instanceof IssueView;
        setSelectedIssue(((IssueView)components[selectedIndex]));
        mySelectedIssueView.scrollRectToVisible(mySelectedIssueView.getBounds());
        myScrollPane.getViewport().setViewPosition(mySelectedIssueView.getLocation());
        return;
      }
    }
  }

  @NotNull
  private ActionToolbar createToolbar() {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(new MinimizeAction());
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actionGroup, true);
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

  /**
   * Disables the auto-sizing of the panel. This will prevent the panel from automatically collapsing if there are no errors.
   */
  public void disableAutoSize() {
    myAutoSize = false;
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
        myDisplayedError.clear();
        myErrorListPanel.removeAll();
        if (myAutoSize) {
          setMinimized(true);
        }
        return;
      }
      updateTitlebarStyle();
      boolean needsRevalidate = false;
      List<Issue> issues = myIssueModel.getIssues();
      if (myDisplayedError.isEmpty()) {
        issues.forEach(this::addErrorEntry);
      }
      else {
        removeOldIssues(issues);
        needsRevalidate = displayNewIssues(issues);
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
    Font font = UIUtil.getLabelFont();
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
      if (!myDisplayedError.containsKey(error)) {
        addErrorEntry(error);
        needsRevalidate = true;
      }
    }
    return needsRevalidate;
  }

  private void removeOldIssues(@NotNull List<Issue> newIssues) {
    Iterator<Map.Entry<Issue, IssueView>> iterator = myDisplayedError.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<Issue, IssueView> entry = iterator.next();
      Issue issue = entry.getKey();
      if (!newIssues.contains(issue)) {
        IssueView issueView = entry.getValue();
        myErrorListPanel.remove(issueView);
        iterator.remove();
      }
    }
  }

  private void addErrorEntry(@NotNull Issue error) {
    if (myErrorListPanel.getComponentCount() == 0) {
      myErrorListPanel.add(Box.createVerticalGlue(), -1);
    }
    IssueView issueView = new IssueView(error, this);
    myDisplayedError.put(error, issueView);
    myErrorListPanel.add(issueView, getInsertionIndex(issueView));
  }

  private int getInsertionIndex(IssueView issueView) {
    int insertIndex = 0;
    for (int i = 0; i < myErrorListPanel.getComponentCount(); i++) {
      Component component = myErrorListPanel.getComponent(i);
      if (component instanceof IssueView) {
        if (((IssueView)component).getDisplayPriority() <= issueView.getDisplayPriority()) {
          insertIndex++;
        }
        else {
          break;
        }
      }
    }
    return insertIndex;
  }

  @Nullable
  public IssueView getSelectedIssueView() {
    return mySelectedIssueView;
  }

  @Override
  public void doLayout() {
    int sourceColumnSize = 0;
    Collection<IssueView> issueViews = myDisplayedError.values();
    IssueView lastView = null;
    for (IssueView view : issueViews) {
      sourceColumnSize = Math.max(sourceColumnSize, view.getSourceLabelWidth());
    }
    for (IssueView view : issueViews) {
      view.setSourceLabelSize(sourceColumnSize);
      lastView = view;
    }
    super.doLayout();
    if (lastView != null) {
      myColumnHeaderView.setColumnsX(lastView.getColumnsX());
    }
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
      UIUtil.invokeLaterIfNeeded(() -> myEventListeners.forEach(it ->
        it.onPanelExpanded(!isMinimized)
      ));
    }
  }

  public void addEventListener(@NotNull EventListener listener) {
    myEventListeners.add(listener);
  }

  @NotNull
  public List<EventListener> getEventListeners() {
    return myEventListeners;
  }

  @Nullable
  public Issue getSelectedIssue() {
    return mySelectedIssue;
  }

  /**
   * Listener to be notified when this panel is minimized
   */
  @Override
  public void dispose() {
    myEventListeners.clear();
    mySelectedIssue = null;
    mySelectedIssueView = null;
    myIssueModel.removeErrorModelListener(myIssueModelListener);
    UIManager.removePropertyChangeListener(this);
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
        Issue issue = myDisplayedError.inverse().get(mySelectedIssueView);
        mySelectedIssue = issue;
        if (issue == null) {
          return;
        }
        issue.getSource().getOnIssueSelected().invoke(mySurface);
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

  /**
   * Lookup the title and description of every shown error and tries to find the provided text
   */
  @VisibleForTesting
  public boolean containsErrorWithText(@NotNull String text) {
    return myDisplayedError.values()
      .stream()
      .anyMatch(
        view -> view.getIssueTitle().contains(text) || MULTIPLE_SPACES.matcher(view.getIssueDescription()).replaceAll(" ").contains(text));
  }

  @VisibleForTesting
  public String getFullIssueText() {
    return myDisplayedError.values()
      .stream()
      .map(view -> view.getIssueTitle() + "\n" + view.getIssueDescription() + "\n\n")
      .collect(Collectors.joining());
  }

  /**
   * Select the highest severity issue related to the provided component and scroll the viewport to this issue
   *
   * @param component      The component owning the issue to show
   * @param collapseOthers if true, all other issues will be collapsed
   */
  public void showIssueForComponent(@NotNull NlComponent component, boolean collapseOthers) {
    Issue issue = myIssueModel.getHighestSeverityIssue(component);
    if (issue == null) {
      return;
    }

    IssueView issueView = myDisplayedError.get(issue);
    if (issueView != null) {
      setSelectedIssue(issueView);
      issueView.setExpanded(true);

      // Collapse all other issue
      if (collapseOthers) {
        for (IssueView other : myDisplayedError.values()) {
          if (other != issueView) {
            other.setExpanded(false);
          }
        }
      }
    }
    setMinimized(false);
    if (issueView != null) {
      JViewport viewport = myScrollPane.getViewport();
      viewport.validate();
      viewport.setViewPosition(issueView.getLocation());
    }
  }

  /**
   * @return The height that the panel should take to display the selected issue if there is one
   * or the full list of collapsed issues
   */
  public int getSuggestedHeight() {
    validate();
    int suggestedHeight = myTitleLabel.getHeight() + myColumnHeaderView.getHeight();
    if (mySelectedIssueView != null) {
      suggestedHeight += mySelectedIssueView.getHeight();
    }
    else {
      suggestedHeight += myDisplayedError.size();
    }
    return Math.max(getMinimumSize().height, suggestedHeight);
  }

  @NotNull
  @VisibleForTesting
  ImmutableList<IssueView> getIssueViews() {
    ImmutableList.Builder<IssueView> builder = ImmutableList.builder();
    for (Component component : myErrorListPanel.getComponents()) {
      if (component instanceof IssueView) {
        builder.add(((IssueView)component));
      }
    }
    return builder.build();
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if ("lookAndFeel".equals(evt.getPropertyName())) {
      myErrorListPanel.removeAll();
      myDisplayedError.clear();
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

  private static class ColumnHeaderPanel extends JPanel {

    private static final int HEIGHT = 15;
    private static final GradientPaint backgroundPaint =
      new GradientPaint(0, 0, new JBColor(0xfbfbfb, 0x53575a),
                        0, HEIGHT, new JBColor(0xe2e2e2, 0x393b3d));
    public static final int COLUMN_COUNT = 2;
    private final JLabel myMessageLabel = createLabel("Message");
    private final JLabel mySourceLabel = createLabel("Source");
    private int[] myColumnsX;
    private boolean myInitialized;

    private ColumnHeaderPanel() {
      setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border()));
      mySourceLabel.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(0, 1, 0, 0, JBColor.border()),
        BorderFactory.createEmptyBorder(0, JBUI.scale(6), 0, 0)));
      add(myMessageLabel);
      add(mySourceLabel);
      myInitialized = true;
    }

    @Override
    public void updateUI() {
      super.updateUI();
      if (myInitialized) {
        myMessageLabel.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
        mySourceLabel.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
        mySourceLabel.setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createMatteBorder(0, 1, 0, 0, JBColor.border()),
          BorderFactory.createEmptyBorder(0, JBUI.scale(6), 0, 0)));
      }
    }

    @NotNull
    private static JLabel createLabel(@Nullable String message) {
      JLabel label = new JLabel(message);
      label.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
      return label;
    }

    @Override
    public void doLayout() {
      super.doLayout();
      // Measurement from IssueView seems to be off sometimes. See b/168682770
      // ColumnsX might be just 0,0 and make the labels on top of one another.
      boolean isColumnsXValid =  myColumnsX != null
           && myColumnsX.length == COLUMN_COUNT
           && !(myColumnsX[0] == 0 && myColumnsX[1] == 0);

      if (isColumnsXValid) {
        myMessageLabel.setLocation(myColumnsX[0], 0);

        // Ensure that the source column always occupied at least 10% of the width if
        // no row has a source label
        int sourceLabelX = (int)Math.min(getWidth() * 0.9, myColumnsX[1] - mySourceLabel.getInsets().left);
        mySourceLabel.setLocation(sourceLabelX, 0);
      }
      else {
        myMessageLabel.setLocation(5, 0);
        mySourceLabel.setLocation((int)(getWidth() * 0.8), 0);
      }
    }

    @Override
    protected void paintComponent(@NotNull Graphics g) {
      super.paintComponent(g);
      Graphics2D g2d = (Graphics2D)g;
      Paint paint = g2d.getPaint();
      g2d.setPaint(backgroundPaint);
      g2d.fill(getBounds());
      g2d.setPaint(paint);
    }

    @Override
    public Dimension getPreferredSize() {
      return JBUI.size(-1, HEIGHT);
    }

    /**
     * Set the x coordinates of each columns
     *
     * @param columnsX An array of the x coordinates for each columns
     *                 from left to right
     */
    private void setColumnsX(@NotNull int[] columnsX) {
      myColumnsX = columnsX;
      revalidate();
      repaint();
    }
  }
}
