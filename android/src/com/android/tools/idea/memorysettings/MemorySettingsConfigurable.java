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

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Locale;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
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
    myComponent.myIdeXmxBox.setSelectedIndex(0);
    myComponent.myGradleDaemonXmxBox.setSelectedIndex(0);
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
    private JComboBox myIdeXmxBox;
    private JBLabel myRecommendationDescriptionLabel;
    private JLabel myDefaultGradleDaemonXmx;
    private JComboBox myGradleDaemonXmxBox;
    private int myCurrentIdeXmx;
    private int myRecommendedIdeXmx;
    private int mySelectedIdeXmx;
    private int myCurrentGradleXmx;
    private int mySelectedGradleXmx;

    MyComponent() {
      // Set the memory settings slider
      int machineMem =  MemorySettingsUtil.getMachineMem();
      int maxXmx = getMaxXmx(machineMem);
      myCurrentIdeXmx = MemorySettingsUtil.getCurrentXmx();
      mySelectedIdeXmx = myCurrentIdeXmx;
      Project project = MemorySettingsUtil.getCurrentProject();
      myRecommendedIdeXmx = MemorySettingsRecommendation.getRecommended(project, myCurrentIdeXmx);
      myRecommendationDescriptionLabel.setVisible(false);

      setXmxBox(myIdeXmxBox, myCurrentIdeXmx, myRecommendedIdeXmx, maxXmx, SIZE_INCREMENT,
                new ItemListener() {
                  @Override
                  public void itemStateChanged(ItemEvent event) {
                    if (event.getStateChange() == ItemEvent.SELECTED && event.getItem() != null) {
                      String selectedItem = (String)event.getItem();
                      mySelectedIdeXmx = parseSelected(selectedItem);
                      myRecommendationDescriptionLabel.setVisible(
                        selectedItem!= null && selectedItem.contains("recommended"));
                    }
                  }
                });


      myDefaultGradleDaemonXmx.setText(
        String.format(Locale.US, "%s MB", MemorySettingsUtil.getDefaultGradleDaemonXmx()));
      if (project != null) {
        myCurrentGradleXmx = MemorySettingsUtil.getProjectGradleDaemonXmx();
        mySelectedGradleXmx = myCurrentGradleXmx;
        setXmxBox(myGradleDaemonXmxBox, myCurrentGradleXmx, -1,
                  MemorySettingsUtil.MAX_GRADLE_DAEMON_XMX_IN_MB,
                  SIZE_INCREMENT / 2,
                  new ItemListener() {
                    @Override
                    public void itemStateChanged(ItemEvent event) {
                      if (event.getStateChange() == ItemEvent.SELECTED && event.getItem() != null) {
                        mySelectedGradleXmx = parseSelected((String)event.getItem());
                      }
                    }
                  });
      } else {
        myGradleDaemonXmxBox.setEnabled(false);
      }
    }

    private void setXmxBox(JComboBox box, int current, int recommended, int max, int increment, ItemListener listener) {
      box.setEditable(false);
      box.removeAllItems();
      box.addItem(String.format(Locale.US, "%s - current", memSizeText(current)));
      if (recommended != -1) {
        box.addItem(String.format(Locale.US, "%d - recommended", recommended));
      }

      for (int size = MIN_IDE_XMX; size <= max; size += increment) {
        if (size != current && size != recommended) {
          box.addItem(Integer.toString(size));
        }
      }
      box.addItemListener(listener);
    }

    private int parseSelected(String selected) {
      return Integer.valueOf(selected.replaceAll("[^\\d]", ""));
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
      return size == -1 ? "unknown" : Integer.toString(size);
    }
  }
}


