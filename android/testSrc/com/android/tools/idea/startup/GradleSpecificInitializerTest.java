/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.startup;

import com.android.tools.idea.gradle.actions.AndroidTemplateProjectSettingsGroup;
import com.android.tools.idea.gradle.actions.RefreshProjectAction;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.EmptyAction;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.codeStyle.arrangement.ArrangementSettings;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementSettings;
import org.jetbrains.android.formatter.AndroidXmlPredefinedCodeStyle;

import java.util.Collections;

import static com.android.tools.idea.startup.GradleSpecificInitializer.TEMPLATE_PROJECT_SETTINGS_GROUP_ID;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Order.KEEP;
import static com.intellij.xml.arrangement.XmlRearranger.attrArrangementRule;

/**
 * Tests for {@link GradleSpecificInitializer}
 */
public class GradleSpecificInitializerTest extends AndroidGradleTestCase {

  /**
   * Verify {@link AndroidTemplateProjectSettingsGroup} is used in ActionManager and in Welcome dialog (b/37141013)
   */
  public void testAndroidTemplateProjectSettingsGroup() {
    ActionManager actionManager = ActionManager.getInstance();
    AnAction action = actionManager.getAction(TEMPLATE_PROJECT_SETTINGS_GROUP_ID);
    assertThat(action).isNotNull();
    assertThat(action).isInstanceOf(AndroidTemplateProjectSettingsGroup.class);
  }

  /**
   * Verify {@link AndroidTemplateProjectSettingsGroup} is used in Welcome dialog
   */
  public void testAndroidTemplateProjectSettingsGroupInWelcomeDialog() {
    ActionManager actionManager = ActionManager.getInstance();
    AnAction configureIdeaAction = actionManager.getAction("WelcomeScreen.Configure.IDEA");
    assertThat(configureIdeaAction).isNotNull();
    assertThat(configureIdeaAction).isInstanceOf(DefaultActionGroup.class);
    DefaultActionGroup settingsGroup = (DefaultActionGroup)configureIdeaAction;
    AnAction[] children = settingsGroup.getChildren(null);
    assertThat(children).hasLength(1);
    AnAction child = children[0];
    assertThat(child).isInstanceOf(AndroidTemplateProjectSettingsGroup.class);
  }

  public void testRefreshProjectsActionIsReplaced() {
    AnAction refreshProjectsAction = ActionManager.getInstance().getAction("ExternalSystem.RefreshAllProjects");
    assertThat(refreshProjectsAction).isInstanceOf(RefreshProjectAction.class);
  }

  public void testSelectProjectToImportActionIsHidden() {
    AnAction selectProjectToImportAction = ActionManager.getInstance().getAction("ExternalSystem.SelectProjectDataToImport");
    assertThat(selectProjectToImportAction).isInstanceOf(EmptyAction.class);
  }

  public void testModifyCodeStyleSettingsReplacesVersion1WithVersion2() {
    CodeStyleSchemes schemes = CodeStyleSchemes.getInstance();

    CodeStyleScheme scheme = schemes.createNewScheme("New Scheme", schemes.getDefaultScheme());
    scheme.getCodeStyleSettings().getCommonSettings(XMLLanguage.INSTANCE)
      .setArrangementSettings(AndroidXmlPredefinedCodeStyle.createVersion1Settings());

    schemes.setCurrentScheme(scheme);

    GradleSpecificInitializer.modifyCodeStyleSettings();

    assertThat(schemes.getCurrentScheme().getCodeStyleSettings().getCommonSettings(XMLLanguage.INSTANCE).getArrangementSettings())
      .isEqualTo(AndroidXmlPredefinedCodeStyle.createVersion2Settings());
  }

  public void testModifyCodeStyleSettingsDoesntReplaceVersion1() {
    ArrangementSettings settings = StdArrangementSettings
      .createByMatchRules(Collections.emptyList(), Collections.singletonList(attrArrangementRule("xmlns:android", "^$", KEEP)));

    CodeStyleSchemes schemes = CodeStyleSchemes.getInstance();

    CodeStyleScheme scheme = schemes.createNewScheme("New Scheme", schemes.getDefaultScheme());
    scheme.getCodeStyleSettings().getCommonSettings(XMLLanguage.INSTANCE).setArrangementSettings(settings);

    schemes.setCurrentScheme(scheme);

    GradleSpecificInitializer.modifyCodeStyleSettings();

    assertThat(schemes.getCurrentScheme().getCodeStyleSettings().getCommonSettings(XMLLanguage.INSTANCE).getArrangementSettings())
      .isEqualTo(settings);
  }
}
