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
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.event.HyperlinkEvent;
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

    MyComponent() {
      // Set the memory settings panel
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
      return isIdeXmxModified() || (myBuildSystemComponent == null ? false : myBuildSystemComponent.isModified());
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
        if (Messages.showYesNoDialog(XmlStringUtil.wrapInHtml(AndroidBundle.message("memory.settings.restart.needed")),
                                     IdeBundle.message("title.restart.needed"),
                                     Messages.getQuestionIcon()) == Messages.YES) {

          // workaround for b/182536388
          // ApplicationImpl hides all frames, that cause deadlock in AWT
          Registry.get("ide.instant.shutdown").setValue(false);

          ((ApplicationEx)ApplicationManager.getApplication()).restart(true);
        }
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


