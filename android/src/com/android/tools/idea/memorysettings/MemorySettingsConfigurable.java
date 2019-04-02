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

import com.google.wireless.android.sdk.stats.MemorySettingsEvent;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.xml.util.XmlStringUtil;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

/** A class to provide a memory settings configurable dialog. */
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
    MemorySettingsUtil.log(MemorySettingsEvent.EventKind.SETTINGS_CHANGE_SAVED,
                           myComponent.myCurrentIdeXmx, myComponent.myCurrentGradleXmx,
                           myComponent.myRecommendedIdeXmx, -1,
                           myComponent.mySelectedIdeXmx, myComponent.mySelectedGradleXmx);

    if (myComponent.isGradleDaemonXmxModified()) {
      MemorySettingsUtil.saveProjectGradleDaemonXmx(myComponent.mySelectedGradleXmx);
    }
    if (myComponent.isIdeXmxModified()) {
      MemorySettingsUtil.saveXmx(myComponent.mySelectedIdeXmx);
      if (Messages.showOkCancelDialog(AndroidBundle.message("memory.settings.restart.needed"),
                                      IdeBundle.message("title.restart.needed"),
                                      Messages.getQuestionIcon()) == Messages.OK) {
        ((ApplicationEx)ApplicationManager.getApplication()).restart(true);
      }
    }
  }

  @Override
  public boolean isModified() {
    return myComponent.isMemorySettingsModified();
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
    myComponent.myIdeXmxBox.setSelectedItem(myComponent.myCurrentIdeXmx);
    myComponent.mySelectedIdeXmx = myComponent.myCurrentIdeXmx;
    myComponent.myGradleDaemonXmxBox.setSelectedItem(myComponent.myCurrentGradleXmx);
    myComponent.mySelectedGradleXmx = myComponent.myCurrentGradleXmx;
  }

  @Override
  public void disposeUIResources() {
    myComponent = null;
  }

  private static class MyComponent {
    private static final int MIN_IDE_XMX = 1024;
    private static final int SIZE_INCREMENT = 1024;
    private static final float MAX_PERCENT_OF_AVAILABLE_RAM = 0.33f;

    private JPanel myPanel;
    private ComboBox<Integer> myIdeXmxBox;
    private ComboBox<Integer> myGradleDaemonXmxBox;
    private JBLabel myInfoLabel;
    private HyperlinkLabel myApplyRecommendationLabel;
    private JPanel myGradlePanel;
    private int myCurrentIdeXmx;
    private int myRecommendedIdeXmx;
    private int myCurrentGradleXmx;
    private int mySelectedIdeXmx;
    private int mySelectedGradleXmx;

    MyComponent() {
      // Set the memory settings slider
      int machineMem =  MemorySettingsUtil.getMachineMem();
      int maxXmx = getMaxXmx(machineMem);
      myCurrentIdeXmx = MemorySettingsUtil.getCurrentXmx();
      mySelectedIdeXmx = myCurrentIdeXmx;
      Project project = MemorySettingsUtil.getCurrentProject();
      myRecommendedIdeXmx = MemorySettingsRecommendation.getRecommended(project, myCurrentIdeXmx);

      MemorySettingsUtil.log(MemorySettingsEvent.EventKind.SHOW_CONFIG_DIALOG,
                             myCurrentIdeXmx, myCurrentGradleXmx,
                             myRecommendedIdeXmx, -1,
                             -1, -1);

      if (myRecommendedIdeXmx > 0) {
        myInfoLabel.setIcon(AllIcons.General.Information);
        myInfoLabel.setText(XmlStringUtil.wrapInHtml(
          AndroidBundle.message("memory.settings.panel.info", memSizeText(myRecommendedIdeXmx))));

        myApplyRecommendationLabel.setHyperlinkText("Use recommended values");
        myApplyRecommendationLabel.addHyperlinkListener(new HyperlinkAdapter() {
           @Override
           protected void hyperlinkActivated(HyperlinkEvent e) {
             myIdeXmxBox.setSelectedItem(myRecommendedIdeXmx);
             mySelectedIdeXmx = myRecommendedIdeXmx;
             MemorySettingsUtil.log(MemorySettingsEvent.EventKind.APPLY_RECOMMENDATION_BUTTON_CLICKED,
                                    myCurrentIdeXmx, myCurrentGradleXmx,
                                    myRecommendedIdeXmx, -1,
                                    myRecommendedIdeXmx, -1);
           }
         });
       } else {
         myInfoLabel.setVisible(false);
         myApplyRecommendationLabel.setVisible(false);
       }

      setXmxBox(myIdeXmxBox, myCurrentIdeXmx, myRecommendedIdeXmx, -1, maxXmx, SIZE_INCREMENT,
                new ItemListener() {
                  @Override
                  public void itemStateChanged(ItemEvent event) {
                    if (event.getStateChange() == ItemEvent.SELECTED && event.getItem() != null) {
                      mySelectedIdeXmx = (int)event.getItem();
                    }
                  }
                });

      if (project != null) {
        myCurrentGradleXmx = MemorySettingsUtil.getProjectGradleDaemonXmx();
        mySelectedGradleXmx = myCurrentGradleXmx;
        setXmxBox(myGradleDaemonXmxBox, myCurrentGradleXmx, -1,
                  MemorySettingsUtil.getDefaultGradleDaemonXmx(),
                  MemorySettingsUtil.MAX_GRADLE_DAEMON_XMX_IN_MB,
                  SIZE_INCREMENT / 2,
                  new ItemListener() {
                    @Override
                    public void itemStateChanged(ItemEvent event) {
                      if (event.getStateChange() == ItemEvent.SELECTED && event.getItem() != null) {
                        mySelectedGradleXmx = (int)event.getItem();
                      }
                    }
                  });
      } else {
        myGradlePanel.setVisible(false);
      }
    }

    private void setXmxBox(JComboBox box, int current, int recommended,
                           int defaultSize, int max, int increment, ItemListener listener) {
      box.setEditable(false);
      box.removeAllItems();

      ArrayList<Integer> items = new ArrayList();

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

      box.setRenderer(new ColoredListCellRenderer<Integer>() {
        @Override
        protected void customizeCellRenderer(@NotNull JList<? extends Integer> list,
                                             Integer value,
                                             int index,
                                             boolean selected,
                                             boolean hasFocus) {
          if (value.equals(Integer.valueOf(current))) {
            append(String.format(Locale.US, "%s - current", memSizeText(current)),
                   SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES);
          }
          else if (value.equals(Integer.valueOf(defaultSize))) {
            append(String.format(Locale.US, "%s - default", memSizeText(defaultSize)),
                   SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES);
          }
          else if (value.equals(Integer.valueOf(recommended))) {
            append(String.format(Locale.US, "%s - recommended", memSizeText(recommended)),
                   new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, null));
          } else {
            append(memSizeText(value), SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }
        }
      });
    }

    private boolean isMemorySettingsModified() {
      return isIdeXmxModified() || isGradleDaemonXmxModified();
    }

    private boolean isIdeXmxModified() {
      return mySelectedIdeXmx != myCurrentIdeXmx;
    }

    private boolean isGradleDaemonXmxModified() {
      return mySelectedGradleXmx != myCurrentGradleXmx;
    }

    // Cap for Xmx: MAX_PERCENT_OF_AVAILABLE_RAM of machineMem, and a hard cap (4GB or 8GB)
    private static int getMaxXmx(int machineMem) {
      int ideXmxCap = MemorySettingsUtil.getIdeXmxCapInGB();
      return Math.min((Math.round(machineMem * MAX_PERCENT_OF_AVAILABLE_RAM) >> 8) << 8, ideXmxCap << 10);
    }

    private static String memSizeText(int size) {
      return size < 0 ? "unknown" : size + " MB";
    }
  }
}


