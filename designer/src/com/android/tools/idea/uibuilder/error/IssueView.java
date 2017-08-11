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
import com.android.tools.idea.common.model.NlComponent;
import com.android.utils.HtmlBuilder;
import com.android.utils.Pair;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.RoundedLineBorder;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Representation of a {@link NlIssue} in the {@link IssuePanel}
 */
public class IssueView extends JPanel {

  private static final Dimension COLLAPSED_ROW_SIZE = JBUI.size(Integer.MAX_VALUE, 30);
  private static final String SUGGESTED_FIXES = "Suggested Fixes";
  private static final RoundedLineBorder SELECTED_BORDER = IdeBorderFactory.createRoundedBorder(1);
  private static final Border UNSELECTED_BORDER = IdeBorderFactory.createEmptyBorder(SELECTED_BORDER.getThickness());

  static {
    SELECTED_BORDER.setColor(UIUtil.getTreeSelectionBorderColor());
  }

  private final IssuePanel myContainerIssuePanel;
  @SuppressWarnings("unused") private JPanel myContent;
  private JBLabel myExpandIcon;
  private JLabel myErrorIcon;
  private JBLabel myCategoryLabel;
  private JLabel mySourceLabel;
  private JTextPane myErrorDescription;
  private JBLabel myErrorTitle;
  private JPanel myFixPanel;
  private JPanel myDetailPanel;
  private JBLabel mySuggestedFixLabel;
  private boolean myIsExpanded;
  private final int myDisplayPriority;

  /**
   * Construct a new {@link IssueView} representing the provided {@link NlIssue}
   *
   * @param issue     The {@link NlIssue} to display
   * @param container The {@link IssuePanel} where this view will be shown
   */
  IssueView(@NotNull NlIssue issue, @NotNull IssuePanel container) {
    addMouseListener(createMouseListener());
    myContainerIssuePanel = container;
    myDisplayPriority = getDisplayPriority(issue);

    setupHeader(issue);
    setupDescriptionPanel(issue);
    setupFixPanel(issue);

    setMaximumSize(COLLAPSED_ROW_SIZE);
    setBackground(UIUtil.getEditorPaneBackground());
  }

  private void setupHeader(@NotNull NlIssue issue) {
    myErrorIcon.setIcon(getSeverityIcon(issue.getSeverity()));
    myExpandIcon.setIcon(UIUtil.getTreeCollapsedIcon());
    myErrorTitle.setText(issue.getSummary());
    myCategoryLabel.setText(issue.getCategory());
    NlComponent source = issue.getSource();
    if (source != null) {
      String id = source.getId();
      String tag = source.getTagName();
      mySourceLabel.setText((id != null ? id + " " : "") + "<" + tag + ">");
    }
  }

  private void setupFixPanel(@NotNull NlIssue issue) {
    myFixPanel.setLayout(new BoxLayout(myFixPanel, BoxLayout.Y_AXIS));
    issue.getFixes().forEach(this::createFixEntry);
    if (myFixPanel.getComponentCount() > 0) {
      myFixPanel.setVisible(true);
      mySuggestedFixLabel.setVisible(true);
      if (myFixPanel.getComponentCount() > 1) {
        mySuggestedFixLabel.setText(SUGGESTED_FIXES);
      }
    }
  }

  private void setupDescriptionPanel(@NotNull NlIssue issue) {
    String description = issue.getDescription();
    String formattedText = new HtmlBuilder().openHtmlBody().addHtml(description).closeHtmlBody().getHtml();
    myErrorDescription.setEditorKit(UIUtil.getHTMLEditorKit());
    myErrorDescription.addHyperlinkListener(issue.getHyperlinkListener());
    myErrorDescription.setText(formattedText);
    myErrorDescription.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        myContainerIssuePanel.setSelectedIssue(IssueView.this);
        setFocused(true);
      }
    });
    applyIssueDescriptionStyle(myErrorDescription);
  }

  /**
   * @return A int between 0 and 3 representing how high the issue should be displayed.
   * 0 is the highest priority and 3 the lower
   */
  private static int getDisplayPriority(@NotNull NlIssue error) {
    if (error.getSeverity().equals(HighlightSeverity.ERROR)) return 0;
    if (error.getSeverity().equals(HighlightSeverity.WARNING)) return 1;
    if (error.getSeverity().equals(HighlightSeverity.WEAK_WARNING)) return 2;
    return 3;
  }

  int getDisplayPriority() {
    return myDisplayPriority;
  }

  private void createFixEntry(Pair<String, Runnable> pair) {
    myFixPanel.add(new FixEntry(pair.getFirst(), pair.getSecond()).getComponent());
  }

  @NotNull
  private MouseAdapter createMouseListener() {
    return new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        myContainerIssuePanel.requestFocusInWindow();
        if (e.getClickCount() == 1) {
          myContainerIssuePanel.setSelectedIssue(IssueView.this);
          setFocused(true);
        }
        if (e.getX() > myExpandIcon.getX() - 3 && e.getX() < myExpandIcon.getX() + myExpandIcon.getWidth() + 3
            || e.getClickCount() >= 2) {
          setExpanded(!myIsExpanded);
        }
      }
    };
  }

  void setExpanded(boolean expanded) {
    myIsExpanded = expanded;
    myDetailPanel.setVisible(myIsExpanded);
    myExpandIcon.setIcon(myIsExpanded ? UIUtil.getTreeExpandedIcon() : UIUtil.getTreeCollapsedIcon());
  }

  /**
   * Returns the icon associated to the passed {@link HighlightSeverity}
   */
  @NotNull
  public static Icon getSeverityIcon(@Nullable HighlightSeverity severity) {
    if (severity != null) {
      if (HighlightSeverity.ERROR.equals(severity)) {
        return AndroidIcons.Issue.Error;
      }
      else if (HighlightSeverity.WARNING.equals(severity)) {
        return AndroidIcons.Issue.Warning;
      }
    }
    return AndroidIcons.Issue.Info;
  }

  void setSelected(boolean selected) {
    setOpaque(selected);
    setBackground(selected ? UIUtil.getPanelBackground() : UIUtil.getEditorPaneBackground());
    setFocused(myContainerIssuePanel.hasFocus() && selected);
  }

  int getCategoryLabelWidth() {
    return myCategoryLabel.getFontMetrics(myCategoryLabel.getFont()).stringWidth(myCategoryLabel.getText());
  }

  int getSourceLabelWidth() {
    return mySourceLabel.getFontMetrics(mySourceLabel.getFont()).stringWidth(mySourceLabel.getText());
  }

  /**
   * Set the size of the source {@link JLabel}
   *
   * The method is used my the {@link IssuePanel} to ensure that every {@link IssueView}'s category
   * label has the same size.
   *
   * @param sourceLabelSize
   */
  void setSourceLabelSize(int sourceLabelSize) {
    Dimension size = mySourceLabel.getPreferredSize();
    size.width = sourceLabelSize;
    mySourceLabel.setPreferredSize(size);
  }

  /**
   * Set size of the category {@link JLabel}
   *
   * The method is used my the {@link IssuePanel} to ensure that every {@link IssueView}'s category
   * label has the same size.
   *
   * @param categoryLabelSize
   */
  void setCategoryLabelSize(int categoryLabelSize) {
    Dimension size = myCategoryLabel.getPreferredSize();
    size.width = categoryLabelSize;
    myCategoryLabel.setPreferredSize(size);
  }

  @VisibleForTesting
  public String getIssueDescription() {
    return myErrorDescription.getText();
  }

  @VisibleForTesting
  public String getIssueTitle() {
    return myErrorTitle.getText();
  }

  /**
   * Custom creation of the GUI form
   */
  private void createUIComponents() {
    myContent = this;
  }

  /**
   * Apply the styles to the provided {@link JTextPane}
   *
   * @param textPane The {@link JTextPane} to style
   */
  private static void applyIssueDescriptionStyle(@NotNull JTextPane textPane) {
    Font font = UIUtil.getLabelFont(UIUtil.FontSize.NORMAL);
    MutableAttributeSet attrs = textPane.getInputAttributes();

    StyleConstants.setFontFamily(attrs, font.getFamily());
    StyleConstants.setFontSize(attrs, font.getSize());
    StyleConstants.setItalic(attrs, (font.getStyle() & Font.ITALIC) != 0);
    StyleConstants.setBold(attrs, (font.getStyle() & Font.BOLD) != 0);

    textPane.getStyledDocument()
      .setCharacterAttributes(0, textPane.getStyledDocument().getLength() + 1, attrs, false);
  }

  /**
   * Draw a border around this issue to show the user that it is focused.
   * <p>
   * This method is intended to show when the {@link IssuePanel} has the keyboard focus but
   * won't actually give the keyboard focus to this {@link IssueView}
   *
   * @param focused
   */
  void setFocused(boolean focused) {
    setBorder(focused ? SELECTED_BORDER : UNSELECTED_BORDER);
  }

  public static class FixEntry {
    private JButton myFixButton;
    private JBLabel myFixText;
    private JComponent myComponent;

    public FixEntry(@NotNull String text, @NotNull Runnable fixRunnable) {
      myFixText.setText(text);
      myFixButton.addActionListener(e -> fixRunnable.run());
    }

    @NotNull
    public JComponent getComponent() {
      return myComponent;
    }
  }
}
