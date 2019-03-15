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

import com.android.tools.idea.flags.StudioFlags;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.Messages;
import java.util.Hashtable;
import java.util.Locale;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
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
    if (myComponent.isMemorySettingsModified()) {
      MemorySettingsUtil.saveXmx(myComponent.myIDEXmxSlider.getValue());
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
    int currentXmx = MemorySettingsUtil.getCurrentXmx();
    myComponent.myIDEXmxSlider.setValue(currentXmx);
    myComponent.myIDEXmxValueField.setText(MyComponent.memSizeText(currentXmx));
  }

  @Override
  public void disposeUIResources() {
    myComponent = null;
  }

  private static class MyComponent {
    private static final int MIN_IDE_XMX = 512;
    private static final int SLIDER_MAJOR_TICK_SPACING = 1024;
    private static final int SLIDER_MINOR_TICK_SPACING = 256;
    private static final float MAX_PERCENT_OF_AVAILABLE_RAM = 0.33f;

    private JPanel myPanel;
    private JSlider myIDEXmxSlider;
    private JTextField myIDEXmxValueField;

    MyComponent() {
      // Set the memory settings slider
      int machineMem =  MemorySettingsUtil.getMachineMem();
      int maxXmx = getMaxXmx(machineMem);

      myIDEXmxSlider.setMinimum(MIN_IDE_XMX);
      myIDEXmxSlider.setMaximum(maxXmx);

      int currentXmx = MemorySettingsUtil.getCurrentXmx();
      myIDEXmxSlider.setValue(currentXmx);
      myIDEXmxValueField.setText(memSizeText(currentXmx));

      Hashtable labelTable = new Hashtable();
      labelTable.put(Integer.valueOf(MIN_IDE_XMX), memLabel(MIN_IDE_XMX));
      labelTable.put(Integer.valueOf(maxXmx), memLabel(maxXmx));
      labelTable.put(Integer.valueOf(currentXmx), memLabel(currentXmx));
      myIDEXmxSlider.setLabelTable(labelTable);
      myIDEXmxSlider.setPaintLabels(true);
      myIDEXmxSlider.setMajorTickSpacing(SLIDER_MAJOR_TICK_SPACING);
      myIDEXmxSlider.setMinorTickSpacing(SLIDER_MINOR_TICK_SPACING);
      myIDEXmxSlider.setPaintTicks(true);
      myIDEXmxSlider.setSnapToTicks(true);

      ChangeListener listener = new ChangeListener() {
        @Override
        public void stateChanged(ChangeEvent event) {
          myIDEXmxValueField.setText(memSizeText(myIDEXmxSlider.getValue()));
        }
      };
      myIDEXmxSlider.addChangeListener(listener);
      myIDEXmxValueField.setEditable(false);
    }

    private JLabel memLabel(int mem) {
      return new JLabel("<html><span style='font-size:8px'>" + memSizeText(mem) + "</span></html>");
    }

    private boolean isMemorySettingsModified() {
      return myIDEXmxSlider.getValue() != MemorySettingsUtil.getCurrentXmx();
    }

    // Cap for Xmx: MAX_PERCENT_OF_AVAILABLE_RAM of machineMem, and IDE_XMX_CAP_IN_GB
    private static int getMaxXmx(int machineMem) {
      return Math.min((Math.round(machineMem * MAX_PERCENT_OF_AVAILABLE_RAM) >> 8) << 8,
                      StudioFlags.IDE_XMX_CAP_IN_GB.get() << 10);
    }

    private static String memSizeText(int size) {
      return size == -1 ? "unknown" : String.format(Locale.US, "%.1f", size / 1024.0);
    }
  }
}


