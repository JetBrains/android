/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.memorysettings;

import static com.android.tools.idea.memorysettings.MemorySettingsConfigurable.MyComponent.setXmxBox;
import static com.android.tools.idea.memorysettings.MemorySettingsConfigurable.MyComponent.setXmxBoxWithOnlyCurrentValue;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.xml.util.XmlStringUtil;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.internal.daemon.DaemonsUi;

public class GradleComponent extends BuildSystemComponent {
  private JPanel myPanel;
  private ComboBox<Integer> myGradleDaemonXmxBox;
  private ComboBox<Integer> myKotlinDaemonXmxBox;
  private HyperlinkLabel myShowDaemonsLabel;
  private JBLabel myDaemonInfoLabel;

  private static final int SIZE_INCREMENT = 1024;

  private final Project myProject;
  private int myCurrentGradleXmx;
  private int myCurrentKotlinXmx;
  private int mySelectedGradleXmx;
  private int mySelectedKotlinXmx;
  private DaemonMemorySettings myDaemonMemorySettings;

  GradleComponent() {
    setupUI();
    myProject = MemorySettingsUtil.getCurrentProject();
    setUI();
  }

  @Override
  public JPanel getPanel() {
    return myPanel;
  }

  @Override
  public boolean isModified() {
    return isGradleDaemonXmxModified() || isKotlinDaemonXmxModified();
  }

  @Override
  public void reset() {
    myGradleDaemonXmxBox.setSelectedItem(myCurrentGradleXmx);
    mySelectedGradleXmx = myCurrentGradleXmx;
    myKotlinDaemonXmxBox.setSelectedItem(myCurrentKotlinXmx);
    mySelectedKotlinXmx = myCurrentKotlinXmx;
  }

  @Override
  public void apply() {
    myCurrentGradleXmx = mySelectedGradleXmx;
    myCurrentKotlinXmx = mySelectedKotlinXmx;
    myDaemonMemorySettings.saveProjectDaemonXmx(myCurrentGradleXmx, myCurrentKotlinXmx);
  }

  @Override
  public void setUI() {
    myDaemonMemorySettings = new DaemonMemorySettings(myProject);

    myCurrentGradleXmx = myDaemonMemorySettings.getProjectGradleDaemonXmx();
    mySelectedGradleXmx = myCurrentGradleXmx;
    myCurrentKotlinXmx = myDaemonMemorySettings.getProjectKotlinDaemonXmx();
    mySelectedKotlinXmx = myCurrentKotlinXmx;

    if (myDaemonMemorySettings.hasUserPropertiesPath()) {
      setXmxBoxWithOnlyCurrentValue(myGradleDaemonXmxBox, myCurrentGradleXmx);
      setXmxBoxWithOnlyCurrentValue(myKotlinDaemonXmxBox, myCurrentKotlinXmx);
      myDaemonInfoLabel
        .setText(XmlStringUtil.wrapInHtml(AndroidBundle.message("memory.settings.has.user.properties",
                                                                myDaemonMemorySettings.getUserPropertiesPath())));
      myShowDaemonsLabel.setVisible(false);
    }
    else {
      setDaemonPanelWhenNoUserGradleProperties();
    }
  }

  @Override
  public void fillCurrent(BuildSystemXmxs xmxs) {
    xmxs.gradleXmx = myCurrentGradleXmx;
    xmxs.kotlinXmx = myCurrentKotlinXmx;
  }

  @Override
  public void fillChanged(BuildSystemXmxs xmxs) {
    xmxs.gradleXmx = mySelectedGradleXmx;
    xmxs.kotlinXmx = mySelectedKotlinXmx;
  }

  private void setDaemonPanelWhenNoUserGradleProperties() {
    setXmxBox(myGradleDaemonXmxBox, myCurrentGradleXmx, -1,
              myDaemonMemorySettings.getDefaultGradleDaemonXmx(),
              DaemonMemorySettings.MAX_GRADLE_DAEMON_XMX_IN_MB,
              SIZE_INCREMENT / 2,
              event -> {
                if (event.getStateChange() == ItemEvent.SELECTED && event.getItem() != null) {
                  mySelectedGradleXmx = (int)event.getItem();
                }
              });

    setXmxBox(myKotlinDaemonXmxBox, myCurrentKotlinXmx, -1,
              myDaemonMemorySettings.getDefaultKotlinDaemonXmx(),
              DaemonMemorySettings.MAX_KOTLIN_DAEMON_XMX_IN_MB,
              SIZE_INCREMENT / 2,
              event -> {
                if (event.getStateChange() == ItemEvent.SELECTED && event.getItem() != null) {
                  mySelectedKotlinXmx = (int)event.getItem();
                }
              });

    myDaemonInfoLabel.setText(XmlStringUtil.wrapInHtml(AndroidBundle.message("memory.settings.panel.daemon.info")));
    myShowDaemonsLabel.setHyperlinkText(AndroidBundle.message("memory.settings.panel.show.daemons.info"));
    myShowDaemonsLabel.addHyperlinkListener(
      new HyperlinkAdapter() {
        DaemonsUi myUi;

        @Override
        protected void hyperlinkActivated(@NotNull HyperlinkEvent e) {
          myUi = new DaemonsUi(myProject) {
            @Override
            public void dispose() {
              myUi = null;
            }
          };
          myUi.show();
        }
      });
  }

  private boolean isGradleDaemonXmxModified() {
    return mySelectedGradleXmx != myCurrentGradleXmx;
  }

  private boolean isKotlinDaemonXmxModified() {
    return mySelectedKotlinXmx != myCurrentKotlinXmx;
  }

  private void setupUI() {
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(7, 3, new Insets(0, 0, 0, 0), -1, -1));
    panel1.add(myPanel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null,
                                            0, false));
    myPanel.setBorder(
      IdeBorderFactory.PlainSmallWithIndent.createTitledBorder(BorderFactory.createEtchedBorder(), "Daemon Heap Size Settings",
                                                               TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null,
                                                               null));
    final JBLabel jBLabel1 = new JBLabel();
    Font jBLabel1Font = getFont(null, -1, 12, jBLabel1.getFont());
    if (jBLabel1Font != null) jBLabel1.setFont(jBLabel1Font);
    jBLabel1.setText("Gradle daemon max heap size:");
    myPanel.add(jBLabel1,
                new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, new Dimension(190, -1), new Dimension(190, -1),
                                    new Dimension(190, -1), 0, false));
    myGradleDaemonXmxBox = new ComboBox();
    final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
    defaultComboBoxModel1.addElement("1024");
    defaultComboBoxModel1.addElement("1280");
    defaultComboBoxModel1.addElement("2048");
    defaultComboBoxModel1.addElement("3072");
    defaultComboBoxModel1.addElement("4096");
    myGradleDaemonXmxBox.setModel(defaultComboBoxModel1);
    myPanel.add(myGradleDaemonXmxBox, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                          GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                          new Dimension(200, -1), new Dimension(200, -1), new Dimension(200, -1), 0,
                                                          false));
    final Spacer spacer1 = new Spacer();
    myPanel.add(spacer1, new GridConstraints(4, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                             GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    final JBLabel jBLabel2 = new JBLabel();
    Font jBLabel2Font = getFont(null, -1, 12, jBLabel2.getFont());
    if (jBLabel2Font != null) jBLabel2.setFont(jBLabel2Font);
    jBLabel2.setText("Kotlin daemon max heap size:");
    myPanel.add(jBLabel2,
                new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                    GridConstraints.SIZEPOLICY_FIXED, new Dimension(190, -1), new Dimension(190, -1),
                                    new Dimension(190, -1), 0, false));
    myKotlinDaemonXmxBox = new ComboBox();
    final DefaultComboBoxModel defaultComboBoxModel2 = new DefaultComboBoxModel();
    defaultComboBoxModel2.addElement("1024");
    defaultComboBoxModel2.addElement("1280");
    defaultComboBoxModel2.addElement("2048");
    defaultComboBoxModel2.addElement("3072");
    defaultComboBoxModel2.addElement("4096");
    myKotlinDaemonXmxBox.setModel(defaultComboBoxModel2);
    myPanel.add(myKotlinDaemonXmxBox, new GridConstraints(6, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                          GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                          new Dimension(200, -1), new Dimension(200, -1), new Dimension(200, -1), 0,
                                                          false));
    final Spacer spacer2 = new Spacer();
    myPanel.add(spacer2, new GridConstraints(6, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                             GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    myShowDaemonsLabel = new HyperlinkLabel();
    myPanel.add(myShowDaemonsLabel, new GridConstraints(2, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                        null, null, 0, false));
    final Spacer spacer3 = new Spacer();
    myPanel.add(spacer3, new GridConstraints(5, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                             GridConstraints.SIZEPOLICY_FIXED, new Dimension(-1, 10), new Dimension(643, 10),
                                             new Dimension(-1, 10), 0, false));
    myDaemonInfoLabel = new JBLabel();
    myDaemonInfoLabel.setText("Label");
    myPanel.add(myDaemonInfoLabel, new GridConstraints(1, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                       GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(600, -1), null, 0, false));
    final Spacer spacer4 = new Spacer();
    myPanel.add(spacer4, new GridConstraints(0, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                             GridConstraints.SIZEPOLICY_FIXED, new Dimension(-1, 10), new Dimension(643, 10),
                                             new Dimension(-1, 10), 0, false));
    final Spacer spacer5 = new Spacer();
    myPanel.add(spacer5, new GridConstraints(3, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                             GridConstraints.SIZEPOLICY_FIXED, new Dimension(-1, 10), new Dimension(643, 10),
                                             new Dimension(-1, 10), 0, false));
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
