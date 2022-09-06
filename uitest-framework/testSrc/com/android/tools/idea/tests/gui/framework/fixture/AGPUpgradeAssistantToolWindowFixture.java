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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.ui.content.Content;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JButton;
import javax.swing.JComboBox;
import org.fest.swing.fixture.JComboBoxFixture;
import org.jetbrains.annotations.NotNull;


public class AGPUpgradeAssistantToolWindowFixture extends ToolWindowFixture{

  @NotNull private final IdeFrameFixture ideFrame;

  public AGPUpgradeAssistantToolWindowFixture(@NotNull IdeFrameFixture projectFrame) {
    super("Upgrade Assistant", projectFrame.getProject(), projectFrame.robot());
    ideFrame = projectFrame;
    activate();
  }

  @NotNull
  public AGPUpgradeAssistantToolWindowFixture clickRunSelectedStepsButton() {
    JButton button = GuiTests.waitUntilShowing(robot(), Matchers.byText(JButton.class, "Run selected steps").andIsEnabled());
    myRobot.click(button);
    return this;
  }

  @NotNull
  public AGPUpgradeAssistantToolWindowFixture clickRefreshButton() {
    JButton button = GuiTests.waitUntilShowing(robot(), Matchers.byText(JButton.class, "Refresh").andIsEnabled());
    myRobot.click(button);
    return this;
  }

  @NotNull
  public AGPUpgradeAssistantToolWindowFixture clickShowUsagesButton() {
    JButton button = GuiTests.waitUntilShowing(robot(), Matchers.byText(JButton.class, "Show Usages").andIsEnabled());
    myRobot.click(button);
    return this;
  }

  @NotNull
  public List <String> getAGPVersions() {
    JComboBoxFixture comboBoxFixture =
      new JComboBoxFixture(robot(), robot().finder().findByType(myToolWindow.getComponent(), JComboBox.class));
    List agpVersions = Arrays.asList(comboBoxFixture.contents());
    return agpVersions;
  }

  @NotNull
  public AGPUpgradeAssistantToolWindowFixture selectAGPVersion(@NotNull String agpVersion) {
    JComboBoxFixture comboBoxFixture =
      new JComboBoxFixture(robot(), robot().finder().findByType(myToolWindow.getComponent(), JComboBox.class));
    comboBoxFixture.click();
    comboBoxFixture.selectItem(agpVersion);
    return this;
  }
  @NotNull
  public String generateAGPVersion(@NotNull String studioVersion) {
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
        throw new AssertionError("Failed to match Android Studio Version from the string: " + studioVersionName);
      }
    }
    return agpVersion;
  }

  @NotNull
  public HashMap getAgpVersionsDict() {
    //Mapping Android studio version to agp version
    HashMap<String, String> agpVersionsDict = new HashMap<>();
    agpVersionsDict.put("flamingo", "8.0.0");
    agpVersionsDict.put("electric eel", "7.4.0");
    agpVersionsDict.put("dolphin", "7.3.0");
    agpVersionsDict.put("chipmunk", "7.2.0");
    return agpVersionsDict;
  }

  @NotNull
  public HashMap getAgpReleaseTypeDict() {
    //Mapping Android studio release type to agp release type
    HashMap<String, String> agpReleaseTypeDict = new HashMap<>();
    agpReleaseTypeDict.put("canary", "alpha");
    agpReleaseTypeDict.put("beta", "beta");
    agpReleaseTypeDict.put("rc", "rc");
    return agpReleaseTypeDict;
  }

  private String getTipOfTree() {
    String tipOfTree = "Flamingo";
    return tipOfTree.toLowerCase();
  }
}
