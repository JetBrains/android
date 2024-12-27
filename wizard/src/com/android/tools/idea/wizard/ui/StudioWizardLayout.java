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
package com.android.tools.idea.wizard.ui;

import com.android.tools.adtui.ImageComponent;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ui.IconProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.JBUI;
import icons.StudioIllustrations;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import org.jetbrains.annotations.NotNull;

/**
 * The general look and feel for all Studio-specific wizards.
 */
public final class StudioWizardLayout implements ModelWizardDialog.CustomLayout {
  private static final JBColor STUDIO_LAYOUT_HEADER_COLOR = new JBColor(0x616161, 0x4B4B4B);
  public static final Dimension DEFAULT_MIN_SIZE = JBUI.size(600, 350);
  public static final Dimension DEFAULT_PREFERRED_SIZE = JBUI.size(900, 650);

  private final BindingsManager myBindings = new BindingsManager();

  private JPanel myRootPanel;
  private JPanel myHeaderPanel;
  private JBLabel myTitleLabel;
  private ImageComponent myIcon;
  private JPanel myCenterPanel;
  private JLabel myStepIcon;

  public StudioWizardLayout() {
    setupUI();
    Icon icon = StudioIllustrations.Common.PRODUCT_ICON;
    myIcon.setPreferredSize(new Dimension(icon.getIconWidth(), icon.getIconHeight()));
    myIcon.setIcon(icon);

    myHeaderPanel.setBackground(STUDIO_LAYOUT_HEADER_COLOR);
  }

  @NotNull
  @Override
  public JPanel decorate(@NotNull ModelWizard.TitleHeader titleHeader, @NotNull JPanel innerPanel) {
    myBindings.bind(new TextProperty(myTitleLabel), titleHeader.title());
    myBindings.bind(new IconProperty(myStepIcon), titleHeader.stepIcon());
    myCenterPanel.add(innerPanel);
    return myRootPanel;
  }

  @Override
  public Dimension getDefaultPreferredSize() {
    return DEFAULT_PREFERRED_SIZE;
  }

  @Override
  public Dimension getDefaultMinSize() {
    return DEFAULT_MIN_SIZE;
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
  }

  private void setupUI() {
    myRootPanel = new JPanel();
    myRootPanel.setLayout(new BorderLayout(0, 0));
    myHeaderPanel = new JPanel();
    myHeaderPanel.setLayout(new GridLayoutManager(1, 3, new Insets(0, 8, 8, 8), 16, -1, false, true));
    myRootPanel.add(myHeaderPanel, BorderLayout.NORTH);
    myHeaderPanel.setBorder(
      BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(20, 10, 20, 10), null, TitledBorder.DEFAULT_JUSTIFICATION,
                                       TitledBorder.DEFAULT_POSITION, null, null));
    myIcon = new ImageComponent();
    myHeaderPanel.add(myIcon, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                  GridConstraints.SIZEPOLICY_FIXED,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                  null, 0, false));
    myTitleLabel = new JBLabel();
    Font myTitleLabelFont = getFont(null, -1, 24, myTitleLabel.getFont());
    if (myTitleLabelFont != null) myTitleLabel.setFont(myTitleLabelFont);
    myTitleLabel.setForeground(new Color(-1));
    myTitleLabel.setText("(Wizard Title)");
    myHeaderPanel.add(myTitleLabel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                        GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, null,
                                                        null, null, 0, false));
    myStepIcon = new JLabel();
    myStepIcon.setAlignmentX(0.5f);
    myStepIcon.setHorizontalAlignment(0);
    myStepIcon.setHorizontalTextPosition(0);
    myStepIcon.setName("right_icon");
    myStepIcon.setText("");
    myHeaderPanel.add(myStepIcon, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE,
                                                      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                      new Dimension(60, 60), new Dimension(60, 60), new Dimension(60, 60), 0, false));
    myCenterPanel = new JPanel();
    myCenterPanel.setLayout(new BorderLayout(0, 0));
    myRootPanel.add(myCenterPanel, BorderLayout.CENTER);
    myCenterPanel.setBorder(
      BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24), null, TitledBorder.DEFAULT_JUSTIFICATION,
                                       TitledBorder.DEFAULT_POSITION, null, null));
  }

  private Font getFont(String fontName, int style, int size, Font currentFont) {
    if (currentFont == null) return null;
    String resultName;
    if (fontName == null) {
      resultName = currentFont.getName();
    }
    else {
      Font testFont = new Font(fontName, Font.PLAIN, 10);
      if (testFont.canDisplay('a') && testFont.canDisplay('1')) {
        resultName = fontName;
      }
      else {
        resultName = currentFont.getName();
      }
    }
    Font font = new Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());
    boolean isMac = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).startsWith("mac");
    Font fontWithFallback = isMac
                            ? new Font(font.getFamily(), font.getStyle(), font.getSize())
                            : new StyleContext().getFont(font.getFamily(), font.getStyle(), font.getSize());
    return fontWithFallback instanceof FontUIResource ? fontWithFallback : new FontUIResource(fontWithFallback);
  }
}
