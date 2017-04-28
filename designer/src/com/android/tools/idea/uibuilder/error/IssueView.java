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
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.utils.HtmlBuilder;
import com.android.utils.Pair;
import com.intellij.icons.AllIcons;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.FontUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.regex.Pattern;

/**
 * Representation of a row in the {@link IssuePanel}
 */
public class IssueView extends JPanel {

  private static final Dimension COLLAPSED_ROW_SIZE = JBUI.size(Integer.MAX_VALUE, 30);
  private static final String SUGGESTED_FIXES = "Suggested Fixes";

  private final IssuePanel myContainerIssuePanel;
  private JBLabel myExpandIcon;
  private JLabel myErrorIcon;
  private JBLabel myCategoryLabel;
  private JLabel mySourceLabel;
  private JTextPane myErrorDescription;
  private JPanel myContent;
  private JBLabel myErrorTitle;
  private JPanel myFixPanel;
  private JPanel myDetailPanel;
  private JBLabel mySuggestedFixLabel;
  private boolean myIsExpanded;

  private IssueView(@NotNull NlIssue error, @NotNull IssuePanel container) {
    MouseAdapter mouseAdapter = createMouseListener();
    myContainerIssuePanel = container;

    myErrorIcon.setIcon(getSeverityIcon(error.getSeverity()));
    myExpandIcon.setIcon(AllIcons.Nodes.TreeRightArrow);
    myExpandIcon.addMouseListener(mouseAdapter);

    myErrorTitle.setText(error.getSummary());
    myErrorTitle.addMouseListener(mouseAdapter);

    myErrorDescription.setContentType(UIUtil.HTML_MIME);
    String description = error.getDescription();
    String formattedText = new HtmlBuilder().openHtmlBody().addHtml(description).closeHtmlBody().getHtml();
    myErrorDescription.setText(formattedText);
    setErrorDescriptionStyle(myErrorDescription);

    myCategoryLabel.setText(error.getCategory());
    NlComponent source = error.getSource();
    if (source != null) {
      String id = source.getId();
      String tag = source.getTagName();
      mySourceLabel.setText((id != null ? id + " " : "") + "<" + tag + ">");
    }

    myContent.setMaximumSize(COLLAPSED_ROW_SIZE);
    myContent.setBackground(UIUtil.getEditorPaneBackground());

    myFixPanel.setLayout(new BoxLayout(myFixPanel, BoxLayout.Y_AXIS));
    error.getFixes().forEach(this::createFixEntry);
    if (myFixPanel.getComponentCount() > 0) {
      myFixPanel.setVisible(true);
      mySuggestedFixLabel.setVisible(true);
      if (myFixPanel.getComponentCount() > 1) {
        mySuggestedFixLabel.setText(SUGGESTED_FIXES);
      }
    }
  }

  private void createFixEntry(Pair<String, Runnable> pair) {
    myFixPanel.add(new FixEntry(pair.getFirst(), pair.getSecond()).getComponent());
  }

  @NotNull
  private MouseAdapter createMouseListener() {
    return new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getSource() != myErrorTitle || e.getClickCount() >= 2) {
          toggleExpand();
        }
        if (e.getSource() != myExpandIcon) {
          setSelected(true);
        }
      }
    };
  }

  private void toggleExpand() {
    myIsExpanded = !myIsExpanded;
    myDetailPanel.setVisible(myIsExpanded);
    myExpandIcon.setIcon(myIsExpanded ? AllIcons.Nodes.TreeDownArrow : AllIcons.Nodes.TreeRightArrow);
  }

  /**
   * Returns the icon associated to the passed {@link HighlightSeverity}
   */
  @Nullable
  public static Icon getSeverityIcon(@Nullable HighlightSeverity severity) {
    if (severity != null) {
      if (HighlightSeverity.ERROR.equals(severity)) {
        return AllIcons.General.Error;
      }
      else if (HighlightSeverity.WARNING.equals(severity)) {
        return AllIcons.General.BalloonWarning;
      }
    }
    return AllIcons.General.Information;
  }

  @NotNull
  public static IssueView createFromNlError(@NotNull NlIssue error, IssuePanel panel) {
    return new IssueView(error, panel);
  }

  public void setSelected(boolean selected) {
    myContent.setOpaque(selected);
    myContent.setBackground(selected ? UIUtil.getPanelBackground() : UIUtil.getEditorPaneBackground());
    myContainerIssuePanel.setSelectedIssue(this);
  }

  public JComponent getComponent() {
    return myContent;
  }

  public int getCategoryLabelWidth() {
    return myCategoryLabel.getFontMetrics(myCategoryLabel.getFont()).stringWidth(myCategoryLabel.getText());
  }

  public int getSourceLabelWidth() {
    return mySourceLabel.getFontMetrics(mySourceLabel.getFont()).stringWidth(mySourceLabel.getText());
  }

  public void setSourceLabelSize(int sourceLabelSize) {
    Dimension size = mySourceLabel.getPreferredSize();
    size.width = sourceLabelSize;
    mySourceLabel.setPreferredSize(size);
  }

  public void setCategoryLabelSize(int CategoryLabelSize) {
    Dimension size = myCategoryLabel.getPreferredSize();
    size.width = CategoryLabelSize;
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

  private void createUIComponents() {
    myContent = this;
  }

  public static void setErrorDescriptionStyle(JTextPane textPane) {
    Font font = UIUtil.getTreeFont();
    MutableAttributeSet attrs = textPane.getInputAttributes();

    StyleConstants.setFontFamily(attrs, font.getFamily());
    StyleConstants.setFontSize(attrs, font.getSize());
    StyleConstants.setItalic(attrs, (font.getStyle() & Font.ITALIC) != 0);
    StyleConstants.setBold(attrs, (font.getStyle() & Font.BOLD) != 0);

    textPane.getStyledDocument()
      .setCharacterAttributes(0, textPane.getStyledDocument().getLength() + 1, attrs, false);
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
