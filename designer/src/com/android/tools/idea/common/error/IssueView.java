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

import com.android.utils.HtmlBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.RoundedLineBorder;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.StyleSheetUtil;
import com.intellij.util.ui.UIUtil;
import icons.StudioIcons;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.border.Border;
import javax.swing.text.Element;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Representation of a {@link Issue} in the {@link IssuePanel}
 */
@SuppressWarnings("unused") // Fields are used in the design form
public class IssueView extends JPanel {

  private static final int COLLAPSED_ROW_HEIGHT = 30;
  private static final int BORDER_THICKNESS = 1;
  private static final JBColor SELECTED_BG_COLOR = new JBColor(0xf2f2f2, 0x232425);

  @NotNull private final Issue myIssue;
  private final IssuePanel myContainerIssuePanel;
  private RoundedLineBorder mySelectedBorder = IdeBorderFactory.createRoundedBorder(JBUIScale.scale(BORDER_THICKNESS));
  private Border myUnselectedBorder = JBUI.Borders.empty(BORDER_THICKNESS);
  @SuppressWarnings("FieldCanBeLocal") // Used for the form
  private JPanel myContent;
  private JBLabel myExpandIcon;
  private JLabel myErrorIcon;
  private JLabel mySourceLabel;
  private JTextPane myErrorDescription;
  private JBLabel myErrorTitle;
  private JPanel myFixPanel;
  private JPanel myDetailPanel;
  private boolean myIsExpanded;
  private final int myDisplayPriority;
  private boolean myInitialized;

  @NotNull
  private String myErrorDescriptionContent;

  /**
   * Construct a new {@link IssueView} representing the provided {@link Issue}
   *
   * @param issue     The {@link Issue} to display
   * @param container The {@link IssuePanel} where this view will be shown
   */
  IssueView(@NotNull Issue issue, @NotNull IssuePanel container) {
    addMouseListener(createMouseListener());
    myIssue = issue;
    myContainerIssuePanel = container;
    myDisplayPriority = getDisplayPriority(issue);
    mySelectedBorder.setColor(UIUtil.getTreeSelectionBorderColor());
    myErrorDescription.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);

    // We cache the error description content but we do not set it into the myErrorDescription just yet.
    // myErrorDescription takes HTML and it is extremely slow to layout (it could be a second for complicated HTML).
    // myErrorDescription will not be visible by default so we will only populate it when it's about to become visible. This way, we do not
    // incur in the expensive layout cost when not needed.
    // b/162809891
    myErrorDescriptionContent = updateImageSize(new HtmlBuilder().openHtmlBody().addHtml(issue.getDescription()).closeHtmlBody().getHtml(),
                                                (int)UIUtil.getFontSize(UIUtil.FontSize.NORMAL));
    setupHeader(issue);
    setupDescriptionPanel(issue);
    setupFixPanel(issue);
    myInitialized = true;
  }

  private String updateImageSize(String html, int size) {
    return html.replaceAll("(<img .+ width=)[0-9]+( height=)[0-9]+", "$1" + size + "$2" + size);
  }

  @Override
  public void updateUI() {
    super.updateUI();
    setMaximumSize(JBUI.size(Integer.MAX_VALUE, COLLAPSED_ROW_HEIGHT));
    setBackground(UIUtil.getEditorPaneBackground());

    if (myInitialized) {
      mySelectedBorder = IdeBorderFactory.createRoundedBorder(JBUIScale.scale(BORDER_THICKNESS));
      mySelectedBorder.setColor(UIUtil.getTreeSelectionBorderColor());
      myUnselectedBorder = JBUI.Borders.empty(BORDER_THICKNESS);
      myErrorDescription.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
      myErrorDescriptionContent = updateImageSize(myErrorDescriptionContent, (int)UIUtil.getFontSize(UIUtil.FontSize.NORMAL));
      myErrorTitle.setFont(StartupUiUtil.getLabelFont().deriveFont(Font.BOLD));
      setExpanded(myIsExpanded);
    }
  }

  private void setupHeader(@NotNull Issue issue) {
    myErrorIcon.setIcon(getSeverityIcon(issue.getSeverity()));
    myExpandIcon.setIcon(UIUtil.getTreeCollapsedIcon());
    // Make sure issues aligh whether it is opened or collapsed.
    int maxWidth = Math.max(UIUtil.getTreeCollapsedIcon().getIconWidth(), UIUtil.getTreeExpandedIcon().getIconWidth());
    myExpandIcon.setMinimumSize(new Dimension(maxWidth, 0));

    myErrorTitle.setText(issue.getSummary());
    String displayText = issue.getSource().getDisplayText();
    if (!displayText.isEmpty()) {
      mySourceLabel.setText(displayText);
    }
  }

  private void setupFixPanel(@NotNull Issue issue) {
    myFixPanel.setLayout(new BoxLayout(myFixPanel, BoxLayout.Y_AXIS));
    issue.getFixes().forEach(this::createFixEntry);
    if (myFixPanel.getComponentCount() > 0) {
      myFixPanel.setVisible(true);
    }
    else {
      myFixPanel.setVisible(false);
    }
  }

  private void setupDescriptionPanel(@NotNull Issue issue) {
    myErrorDescription.setEditorKit(new IssueHTMLEditorKit());
    myErrorDescription.addHyperlinkListener(issue.getHyperlinkListener());
    myErrorDescription.setFont(UIUtil.getToolTipFont());
    myErrorDescription.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        myContainerIssuePanel.setSelectedIssue(IssueView.this);
        setFocused(true);
      }
    });
  }

  public void updateDescription(@NotNull Issue issue) {
    myErrorDescriptionContent = updateImageSize(new HtmlBuilder().openHtmlBody().addHtml(issue.getDescription()).closeHtmlBody().getHtml(),
                                                (int)UIUtil.getFontSize(UIUtil.FontSize.NORMAL));
  }

  @NotNull
  public Issue getIssue() {
    return myIssue;
  }

  /**
   * @return A int between 0 and 3 representing how high the issue should be displayed.
   * 0 is the highest priority and 3 the lower
   */
  private static int getDisplayPriority(@NotNull Issue error) {
    if (error.getSeverity().equals(HighlightSeverity.ERROR)) return 0;
    if (error.getSeverity().equals(HighlightSeverity.WARNING)) return 1;
    if (error.getSeverity().equals(HighlightSeverity.WEAK_WARNING)) return 2;
    return 3;
  }

  int getDisplayPriority() {
    return myDisplayPriority;
  }

  private void createFixEntry(@NotNull Issue.Fix fix) {
    myFixPanel.add(new FixEntry(fix.getButtonText(), fix.getDescription(), fix.getAction()));
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

    if (myIsExpanded) {
      myErrorDescription.setText(myErrorDescriptionContent);
      // After setting the content, we need the component to revalidate to re-measure. validate() does not trigger a new setSize after
      // the text has been update but, getPreferredSize() does.
      // b/168682770
      myErrorDescription.getPreferredSize();
    }
    else {
      // Remove all the HTML content since it will not be visible and this speeds up the layout.
      myErrorDescription.setText("");
    }

    myDetailPanel.setVisible(myIsExpanded);
    myExpandIcon.setIcon(myIsExpanded ? UIUtil.getTreeExpandedIcon() : UIUtil.getTreeCollapsedIcon());

    List<IssuePanel.EventListener> eventListeners = myContainerIssuePanel.getEventListeners();
    eventListeners.forEach(listener -> {
      listener.onIssueExpanded(myContainerIssuePanel.getSelectedIssue(), expanded);
    });

    // ColumnHeaderPanel from panel need layout again.
    myContainerIssuePanel.revalidate();
    myContainerIssuePanel.repaint();
  }

  /**
   * Returns the icon associated to the passed {@link HighlightSeverity}
   */
  @NotNull
  public static Icon getSeverityIcon(@Nullable HighlightSeverity severity) {
    if (severity != null) {
      if (HighlightSeverity.ERROR.equals(severity)) {
        return StudioIcons.Common.ERROR;
      }
      else if (HighlightSeverity.WARNING.equals(severity)) {
        return StudioIcons.Common.WARNING;
      }
    }
    return StudioIcons.Common.INFO;
  }

  void setSelected(boolean selected) {
    setOpaque(selected);
    setBackground(selected ? SELECTED_BG_COLOR : UIUtil.getEditorPaneBackground());
    setFocused(myContainerIssuePanel.hasFocus() && selected);
  }

  int getSourceLabelWidth() {
    return mySourceLabel.getFontMetrics(mySourceLabel.getFont()).stringWidth(mySourceLabel.getText());
  }

  /**
   * Set the size of the source {@link JLabel}
   * <p>
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
    setBorder(focused ? mySelectedBorder : myUnselectedBorder);
  }

  @VisibleForTesting
  @NotNull
  FixEntry[] getFixEntries() {
    return Arrays.copyOf(myFixPanel.getComponents(), myFixPanel.getComponentCount(), FixEntry[].class);
  }

  /**
   * Get the x coordinates of each columns of this view
   *
   * @return An array of the x coordinates for each columns
   * from left to right
   */
  @NotNull
  int[] getColumnsX() {
    return new int[]{myExpandIcon.getX(), mySourceLabel.getX()};
  }

  @SuppressWarnings("unused") // Used in the design form
  public static class FixEntry extends JComponent {
    private JButton myFixButton;
    private JBLabel myFixText;
    private JComponent myComponent;

    public FixEntry(@NotNull String buttonText, @NotNull String text, @NotNull Runnable fixRunnable) {
      myFixText.setText(text);
      myFixButton.setText(buttonText);
      myFixButton.addActionListener(e -> fixRunnable.run());
    }

    private void createUIComponents() {
      myComponent = this;
    }
  }

  private static class IssueHTMLEditorKit extends HTMLEditorKit {

    StyleSheet style = createStyleSheet();

    public StyleSheet createStyleSheet() {
      StyleSheet style = new StyleSheet();
      style.addStyleSheet(StyleSheetUtil.getDefaultStyleSheet());
      style.addRule("body { font-family: Sans-Serif; }");
      style.addRule("code { font-size: 100%; font-family: monospace; }"); // small by Swing's default
      style.addRule("small { font-size: small; }"); // x-small by Swing's default
      style.addRule("a { text-decoration: none;}");
      return style;
    }

    @Override
    public StyleSheet getStyleSheet() {
      return style;
    }

    @Override
    protected void createInputAttributes(Element element, MutableAttributeSet set) {
      // Do Nothing, the super implementation stripped out the <BR/> tags but
      // we need them
    }
  }
}
