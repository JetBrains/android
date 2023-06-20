/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.gradle;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import org.fest.swing.fixture.JComboBoxFixture;
import org.jetbrains.annotations.NotNull;

public class AGPUpgradeAssistantToolWindowFixture extends ToolWindowFixture {

  @NotNull
  private final IdeFrameFixture ideFrame;

  public AGPUpgradeAssistantToolWindowFixture(@NotNull IdeFrameFixture projectFrame) {
    super("Upgrade Assistant", projectFrame.getProject(), projectFrame.robot());
    ideFrame = projectFrame;
    activate();
  }

  @NotNull
  public void clickRunSelectedStepsButton() {
    JButton button = GuiTests.waitUntilShowing(robot(),
                                               Matchers.byText(JButton.class, "Run selected steps").andIsEnabled());
    myRobot.click(button);
  }

  @NotNull
  public boolean isRunSelectedStepsButtonEnabled() {
    JButton button = GuiTests.waitUntilShowing(robot(),
                                               Matchers.byText(JButton.class, "Run selected steps"));
    return button.isEnabled();
  }

  @NotNull
  public void clickRefreshButton() {
    JButton button = GuiTests.waitUntilShowing(robot(), Matchers.byText(JButton.class, "Refresh").andIsEnabled());
    myRobot.click(button);
  }

  @NotNull
  public boolean isRefreshButtonEnabled() {
    JButton button = GuiTests.waitUntilShowing(robot(),
                                               Matchers.byText(JButton.class, "Refresh"));
    return button.isEnabled();
  }

  @NotNull
  public void clickShowUsagesButton() {
    JButton button = GuiTests.waitUntilShowing(robot(),
                                               Matchers.byText(JButton.class, "Show Usages").andIsEnabled());
    myRobot.click(button);
  }

  @NotNull
  public boolean isShowUsagesEnabled() {
    JButton button = GuiTests.waitUntilShowing(robot(),
                                               Matchers.byText(JButton.class, "Show Usages"));
    return button.isEnabled();
  }

  @NotNull
  public void clickRevertProjectFiles() {
    JButton button = GuiTests.waitUntilShowing(robot(),
                                               Matchers.byText(JButton.class, "Revert Project Files").andIsEnabled());
    myRobot.click(button);
  }

  @NotNull
  public List<String> getAGPVersions() {
    JComboBoxFixture comboBoxFixture = new JComboBoxFixture(robot(),
                                                            robot().finder().findByType(myToolWindow.getComponent(), JComboBox.class));
    List agpVersions = Arrays.asList(comboBoxFixture.contents());
    return agpVersions;
  }

  @NotNull
  public void selectAGPVersion(@NotNull String agpVersion) {
    JComboBoxFixture comboBoxFixture = new JComboBoxFixture(robot(),
                                                            robot().finder().findByType(myToolWindow.getComponent(), JComboBox.class));
    comboBoxFixture.click();
    comboBoxFixture.selectItem(agpVersion);
  }

  public boolean getSyncStatus() {
    JEditorPane panelInfo2 = robot().finder().findByType(myToolWindow.getComponent(), JEditorPane.class);
    String text = panelInfo2.getText();
    if (text.contains("Sync succeeded")) {
      return true;
    } else {
      return false;
    }
  }

  @NotNull
  public String generateAGPVersion(@NotNull String studioVersion) {
    //This method generates the AGP version of the Android Studio Build dynamically by checking the Android Studio version, release type and build.
    String studioVersionName = studioVersion.toLowerCase();
    String agpVersion; // Check for StringBuilder
    String releaseVersion;
    String releaseType;
    String releaseNumber;
    String tipOfTree = getTipOfTree();
    HashMap<String, String> agpVersionsDict = getAgpVersionsDict();
    HashMap<String, String> agpReleaseTypeDict = getAgpReleaseTypeDict();
    if (studioVersionName.contains("dev")) {
      agpVersion = agpVersionsDict.get(tipOfTree) + "-dev";
    } else {
      Matcher matcher = Pattern
        .compile("android\\sstudio\\s(\\w.+)\\s\\|\\s\\d{4}\\.\\d{1,2}\\.\\d{1,2}?\\s*(\\w*)\\s*(\\d*)")
        .matcher(studioVersionName);
      if (matcher.find()) {
        releaseVersion = matcher.group(1);
        releaseType = matcher.group(2);
        releaseNumber = matcher.group(3);
        if (releaseType.isEmpty()) { // Final Version
          agpVersion = agpVersionsDict.get(releaseVersion);
        } else {
          if (releaseType.contains("patch")) {
            agpVersion = agpVersionsDict.get(releaseVersion).replace("0", releaseNumber);
          } else {
            agpVersion = agpVersionsDict.get(releaseVersion) + "-" + agpReleaseTypeDict.get(releaseType)
                         + String.format("%02d", Integer.parseInt(releaseNumber));
          }
        }
      } else {
        throw new AssertionError(
          "Failed to match Android Studio Version from the string: " + studioVersionName);
      }
    }
    return agpVersion;
  }

  @NotNull
  public HashMap getAgpVersionsDict() {
    // Mapping Android studio version to agp version
    HashMap<String, String> agpVersionsDict = new HashMap<>();
    agpVersionsDict.put("giraffe", "8.1.0");
    agpVersionsDict.put("flamingo", "8.0.0");
    agpVersionsDict.put("electric eel", "7.4.0");
    agpVersionsDict.put("dolphin", "7.3.0");
    agpVersionsDict.put("chipmunk", "7.2.0");
    return agpVersionsDict;
  }

  @NotNull
  public HashMap getAgpReleaseTypeDict() {
    // Mapping Android studio release type to agp release type
    HashMap<String, String> agpReleaseTypeDict = new HashMap<>();
    agpReleaseTypeDict.put("canary", "alpha");
    agpReleaseTypeDict.put("beta", "beta");
    agpReleaseTypeDict.put("rc", "rc");
    return agpReleaseTypeDict;
  }

  private String getTipOfTree() {
    // This falls under test mantainance as the TIT needs to be updated for every relase for no failures or flakiness.
    String tipOfTree = "Giraffe";
    return tipOfTree.toLowerCase();
  }
}

