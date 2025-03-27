/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.idea.projectsystem.AndroidProjectSystem;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.google.wireless.android.sdk.stats.MemorySettingsEvent;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

/**
 * A class to provide a memory settings configurable dialog.
 */
public class MemorySettingsConfigurable implements SearchableConfigurable {
  private MyComponent myComponent;

  public MemorySettingsConfigurable() {
    myComponent = new MyComponent();
  }

  @Override
  @NotNull
  public String getHelpTopic() {
    return "memory.settings";
  }

  @Override
  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  @Override
  public String getDisplayName() {
    return "Memory Settings";
  }

  @Override
  public void apply() throws ConfigurationException {
    myComponent.apply();
  }

  @Override
  public boolean isModified() {
    return myComponent.isModified();
  }

  @Override
  public JComponent createComponent() {
    if (myComponent == null) {
      myComponent = new MyComponent();
    }
    return myComponent.myPanel;
  }

  @Override
  public void reset() {
    myComponent.reset();
  }

  @Override
  public void disposeUIResources() {
    if (myComponent.myNeedsRestart) {
      if (Messages.showYesNoDialog(XmlStringUtil.wrapInHtml(AndroidBundle.message("memory.settings.restart.needed")),
                                   IdeBundle.message("title.restart.needed"),
                                   Messages.getQuestionIcon()) == Messages.YES) {

        // workaround for b/182536388
        // ApplicationImpl hides all frames, that cause deadlock in AWT
        Registry.get("ide.instant.shutdown").setValue(false);

        ((ApplicationEx)ApplicationManager.getApplication()).restart(true);
      }
    }
    myComponent = null;
  }

  static class MyComponent {
    private static final int MIN_IDE_XMX = 1024;
    private static final int DEFAULT_IDE_XMX = 2048;
    private static final int SIZE_INCREMENT = 1024;
    private static final float MAX_PERCENT_OF_AVAILABLE_RAM = 0.33f;

    private JPanel myPanel;
    private ComboBox<Integer> myIdeXmxBox;
    private JBLabel myInfoLabel;
    private HyperlinkLabel myApplyRecommendationLabel;
    private JPanel myBuildSystemPanel;

    private BuildSystemComponent myBuildSystemComponent;
    private JBLabel myIdeBottomLabel;
    private JBLabel myIdeInfoLabel;
    private Project myProject;
    private int myCurrentIdeXmx;
    private final int myRecommendedIdeXmx;
    private int mySelectedIdeXmx;

    private boolean myNeedsRestart;

    MyComponent() {
      // Set the memory settings panel
      setupUI();
      myCurrentIdeXmx = MemorySettingsUtil.getCurrentXmx();
      mySelectedIdeXmx = myCurrentIdeXmx;
      myRecommendedIdeXmx = MemorySettingsRecommendation.getRecommended(myProject, myCurrentIdeXmx);

      setUI();
      BuildSystemComponent.BuildSystemXmxs currentXmxs = new BuildSystemComponent.BuildSystemXmxs();
      if (myBuildSystemComponent != null) {
        myBuildSystemComponent.fillCurrent(currentXmxs);
      }
      MemorySettingsUtil.log(MemorySettingsEvent.EventKind.SHOW_CONFIG_DIALOG,
                             myCurrentIdeXmx, currentXmxs.gradleXmx, currentXmxs.kotlinXmx,
                             myRecommendedIdeXmx, -1, -1,
                             -1, -1, -1);
    }

    private void setUI() {
      myInfoLabel.setText(XmlStringUtil.wrapInHtml("<body>" + AndroidBundle.message("memory.settings.panel.top.message") + "</body>"));
      myIdeBottomLabel.setText(XmlStringUtil.wrapInHtml(AndroidBundle.message("memory.settings.ide.bottom.message")));
      myIdeBottomLabel.setFontColor(UIUtil.FontColor.BRIGHTER);

      if (myRecommendedIdeXmx > myCurrentIdeXmx) {
        myIdeInfoLabel.setText(XmlStringUtil.wrapInHtml(
          AndroidBundle.message("memory.settings.panel.ide.info", memSizeText(myRecommendedIdeXmx))));

        myApplyRecommendationLabel.setHyperlinkText(AndroidBundle.message("memory.settings.panel.use.recommended.values"));
        myApplyRecommendationLabel.addHyperlinkListener(new HyperlinkAdapter() {
          @Override
          protected void hyperlinkActivated(@NotNull HyperlinkEvent e) {
            myIdeXmxBox.setSelectedItem(myRecommendedIdeXmx);
            mySelectedIdeXmx = myRecommendedIdeXmx;
            BuildSystemComponent.BuildSystemXmxs currentXmxs = new BuildSystemComponent.BuildSystemXmxs();
            if (myBuildSystemComponent != null) {
              myBuildSystemComponent.fillCurrent(currentXmxs);
            }
            MemorySettingsUtil.log(MemorySettingsEvent.EventKind.APPLY_RECOMMENDATION_BUTTON_CLICKED,
                                   myCurrentIdeXmx, currentXmxs.gradleXmx, currentXmxs.kotlinXmx,
                                   myRecommendedIdeXmx, -1, -1,
                                   myRecommendedIdeXmx, -1, -1);
          }
        });
      }
      else {
        myIdeInfoLabel.setVisible(false);
        myApplyRecommendationLabel.setVisible(false);
      }

      int machineMem = MemorySettingsUtil.getMachineMem();
      int maxXmx = getMaxXmxInMB(machineMem);
      setXmxBox(myIdeXmxBox, myCurrentIdeXmx, myRecommendedIdeXmx, DEFAULT_IDE_XMX, maxXmx, SIZE_INCREMENT,
                event -> {
                  if (event.getStateChange() == ItemEvent.SELECTED && event.getItem() != null) {
                    mySelectedIdeXmx = (int)event.getItem();
                  }
                });
      if (myBuildSystemComponent != null) {
        myBuildSystemComponent.setUI();
      }
      else {
        myBuildSystemPanel.setVisible(false);
      }
    }

    private void setupUI() {
      createUIComponents();
      myPanel = new JPanel();
      myPanel.setLayout(new GridLayoutManager(6, 1, new Insets(0, 0, 0, 0), -1, -1));
      final JPanel panel1 = new JPanel();
      panel1.setLayout(new GridLayoutManager(6, 3, new Insets(0, 0, 0, 0), -1, -1));
      myPanel.add(panel1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null,
                                              0, false));
      panel1.setBorder(
        IdeBorderFactory.PlainSmallWithIndent.createTitledBorder(BorderFactory.createEtchedBorder(), "IDE Heap Size Settings",
                                                                 TitledBorder.DEFAULT_JUSTIFICATION,
                                                                 TitledBorder.DEFAULT_POSITION, null, null));
      final JBLabel jBLabel1 = new JBLabel();
      Font jBLabel1Font = getFont(null, -1, 12, jBLabel1.getFont());
      if (jBLabel1Font != null) jBLabel1.setFont(jBLabel1Font);
      jBLabel1.setText("IDE max heap size:");
      panel1.add(jBLabel1,
                 new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                     GridConstraints.SIZEPOLICY_FIXED, new Dimension(190, -1), new Dimension(184, 15),
                                     new Dimension(190, -1),
                                     0, false));
      myIdeXmxBox = new ComboBox();
      final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
      defaultComboBoxModel1.addElement("1024");
      defaultComboBoxModel1.addElement("1280");
      defaultComboBoxModel1.addElement("2048");
      defaultComboBoxModel1.addElement("3072");
      defaultComboBoxModel1.addElement("4096");
      myIdeXmxBox.setModel(defaultComboBoxModel1);
      panel1.add(myIdeXmxBox, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                  GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                  new Dimension(200, -1), new Dimension(200, -1), new Dimension(200, -1), 0, false));
      myIdeBottomLabel = new JBLabel();
      Font myIdeBottomLabelFont = getFont(null, -1, 10, myIdeBottomLabel.getFont());
      if (myIdeBottomLabelFont != null) myIdeBottomLabel.setFont(myIdeBottomLabelFont);
      myIdeBottomLabel.setFontColor(UIUtil.FontColor.BRIGHTER);
      myIdeBottomLabel.setOpaque(false);
      myIdeBottomLabel.setText("Label");
      panel1.add(myIdeBottomLabel,
                 new GridConstraints(5, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                     GridConstraints.SIZEPOLICY_WANT_GROW,
                                     GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(400, -1), null, 0, false));
      myIdeInfoLabel = new JBLabel();
      myIdeInfoLabel.setText("Label");
      panel1.add(myIdeInfoLabel, new GridConstraints(1, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                     GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(600, -1), null, 0, false));
      myApplyRecommendationLabel = new HyperlinkLabel();
      panel1.add(myApplyRecommendationLabel, new GridConstraints(2, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                 GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                 GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                 null, new Dimension(643, 15), null, 0, false));
      final Spacer spacer1 = new Spacer();
      panel1.add(spacer1, new GridConstraints(3, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                              GridConstraints.SIZEPOLICY_FIXED, new Dimension(-1, 10), new Dimension(643, 10),
                                              new Dimension(-1, 10), 0, false));
      final Spacer spacer2 = new Spacer();
      panel1.add(spacer2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                              GridConstraints.SIZEPOLICY_FIXED, new Dimension(-1, 10), new Dimension(184, 10),
                                              new Dimension(-1, 10), 0, false));
      final Spacer spacer3 = new Spacer();
      panel1.add(spacer3, new GridConstraints(4, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                              GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
      myPanel.add(myBuildSystemPanel, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                          null, null, 0, false));
      myBuildSystemPanel.setBorder(
        IdeBorderFactory.PlainSmallWithIndent.createTitledBorder(BorderFactory.createEtchedBorder(), "Daemon Heap Size Settings",
                                                                 TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null,
                                                                 null));
      myInfoLabel = new JBLabel();
      myInfoLabel.setText("Label");
      myPanel.add(myInfoLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                   new Dimension(600, -1), null, 0, false));
      final Spacer spacer4 = new Spacer();
      myPanel.add(spacer4, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                               GridConstraints.SIZEPOLICY_FIXED, new Dimension(-1, 10), new Dimension(643, 10),
                                               new Dimension(-1, 10), 0, false));
      final Spacer spacer5 = new Spacer();
      myPanel.add(spacer5, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                               GridConstraints.SIZEPOLICY_FIXED, new Dimension(-1, 15), new Dimension(643, 15),
                                               new Dimension(-1, 15), 0, false));
      final Spacer spacer6 = new Spacer();
      myPanel.add(spacer6, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                               GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
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

    /**
     * @noinspection ALL
     */
    public JComponent getRootComponent() { return myPanel; }


    public static void setXmxBoxWithOnlyCurrentValue(JComboBox<Integer> box, int current) {
    box.setEditable(false);
    box.removeAllItems();
    box.addItem(current);
    box.setSelectedItem(current);
    box.setRenderer(new ColoredListCellRenderer<>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends Integer> list,
                                           Integer value,
                                           int index,
                                           boolean selected,
                                           boolean hasFocus) {
        append(String.format(Locale.US, "%s - current", memSizeText(current)),
               SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES);
      }
    });
  }

  public static void setXmxBox(JComboBox<Integer> box, int current, int recommended,
                               int defaultSize, int max, int increment, ItemListener listener) {
    box.setEditable(false);
    box.removeAllItems();

    ArrayList<Integer> items = new ArrayList<>();

    items.add(current);
    if (recommended > 0 && recommended != current) {
      items.add(recommended);
    }

    if (defaultSize > 0 && defaultSize != current && defaultSize != recommended) {
      items.add(defaultSize);
    }

    for (int size = MIN_IDE_XMX; size <= max; size += increment) {
      if (size != current && size != recommended && size != defaultSize) {
        items.add(size);
      }
    }

    Collections.sort(items);
    for (int item : items) {
      box.addItem(item);
    }
    box.setSelectedItem(current);
    box.addItemListener(listener);

    box.setRenderer(new ColoredListCellRenderer<>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends Integer> list,
                                           Integer value,
                                           int index,
                                           boolean selected,
                                           boolean hasFocus) {
        if (value.equals(current)) {
          append(String.format(Locale.US, "%s - current", memSizeText(current)),
                 SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES);
        }
        else if (value.equals(defaultSize)) {
          append(String.format(Locale.US, "%s - default", memSizeText(defaultSize)),
                 SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES);
        }
        else if (value.equals(recommended)) {
          append(String.format(Locale.US, "%s - recommended", memSizeText(recommended)),
                 new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, null));
        }
        else {
          append(memSizeText(value), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      }
    });
  }

  private boolean isModified() {
    return isIdeXmxModified() || (myBuildSystemComponent != null && myBuildSystemComponent.isModified());
  }

  private boolean isIdeXmxModified() {
    return mySelectedIdeXmx != myCurrentIdeXmx;
  }

  private void reset() {
    myIdeXmxBox.setSelectedItem(myCurrentIdeXmx);
    mySelectedIdeXmx = myCurrentIdeXmx;
    if (myBuildSystemComponent != null) {
      myBuildSystemComponent.reset();
    }
  }

  private void apply() {
    BuildSystemComponent.BuildSystemXmxs currentXmxs = new BuildSystemComponent.BuildSystemXmxs();
    BuildSystemComponent.BuildSystemXmxs selectedXmxs = new BuildSystemComponent.BuildSystemXmxs();
    if (myBuildSystemComponent != null) {
      myBuildSystemComponent.fillCurrent(currentXmxs);
      myBuildSystemComponent.fillChanged(selectedXmxs);
    }
    MemorySettingsUtil.log(MemorySettingsEvent.EventKind.SETTINGS_CHANGE_SAVED,
                           myCurrentIdeXmx, currentXmxs.gradleXmx, currentXmxs.kotlinXmx,
                           myRecommendedIdeXmx, -1, -1,
                           mySelectedIdeXmx, selectedXmxs.gradleXmx, selectedXmxs.kotlinXmx);

    boolean needsUpdate = isModified();
    if (myBuildSystemComponent != null && myBuildSystemComponent.isModified()) {
      myBuildSystemComponent.apply();
    }

    if (isIdeXmxModified()) {
      MemorySettingsUtil.saveXmx(mySelectedIdeXmx);
      myCurrentIdeXmx = mySelectedIdeXmx;
      myNeedsRestart = true;
    }
    if (needsUpdate) {
      // repaint
      setUI();
    }
  }

  private void createUIComponents() {
    myProject = MemorySettingsUtil.getCurrentProject();
    if (myProject == null) {
      myBuildSystemPanel = new JPanel();
      return;
    }
    AndroidProjectSystem projectSystem = ProjectSystemUtil.getProjectSystem(myProject);
    Optional<MemorySettingsToken<AndroidProjectSystem>> maybeToken =
      MemorySettingsToken.EP_NAME.getExtensionList().stream()
        .filter((it) -> it.isApplicable(projectSystem))
        .findFirst();
    if (maybeToken.isPresent()) {
      myBuildSystemComponent = maybeToken.get().createBuildSystemComponent(projectSystem);
      if (myBuildSystemComponent != null) {
        myBuildSystemPanel = myBuildSystemComponent.getPanel();
      }
      else {
        myBuildSystemPanel = new JPanel();
      }
    }
    else {
      myBuildSystemPanel = new JPanel();
    }
  }

    /**
     * Returns the minimum of MemorySettingsRecommendation#XLARGE_HEAP_SIZE_RECOMMENDATION_IN_MB and
     * the user's machine memory * MAX_PERCENT_OF_AVAILABLE_RAM rounded to the nearest 256 MB
   */
  private static int getMaxXmxInMB(int machineMemInMB) {
    int ideXmxCap = MemorySettingsRecommendation.XLARGE_HEAP_SIZE_RECOMMENDATION_IN_MB;
    return Math.min((Math.round(machineMemInMB * MAX_PERCENT_OF_AVAILABLE_RAM) >> 8) << 8, ideXmxCap);
  }

  private static String memSizeText(int size) {
    return size < 0 ? "unknown" : size + " MB";
  }
}
}


