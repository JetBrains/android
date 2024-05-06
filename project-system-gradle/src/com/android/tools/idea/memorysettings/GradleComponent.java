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
import com.intellij.ui.components.JBLabel;
import com.intellij.xml.util.XmlStringUtil;
import java.awt.event.ItemEvent;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.internal.daemon.DaemonState;
import org.jetbrains.plugins.gradle.internal.daemon.DaemonsUi;
import org.jetbrains.plugins.gradle.internal.daemon.GradleDaemonServices;

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
}
