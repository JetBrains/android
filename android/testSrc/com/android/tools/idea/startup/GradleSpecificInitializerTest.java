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

import static com.android.tools.idea.startup.GradleSpecificInitializer.TEMPLATE_PROJECT_SETTINGS_GROUP_ID;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.actions.AndroidTemplateProjectSettingsGroup;
import com.android.tools.idea.gradle.actions.AndroidTemplateProjectStructureAction;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.EmptyAction;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.codeStyle.arrangement.ArrangementSettings;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementSettings;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Order;
import java.util.Collections;
import org.jetbrains.android.formatter.AndroidXmlPredefinedCodeStyle;
import org.jetbrains.android.formatter.AndroidXmlRearranger;

/**
 * Tests for {@link GradleSpecificInitializer}
 */
public class GradleSpecificInitializerTest extends AndroidGradleTestCase {

  /**
   * Verify {@link AndroidTemplateProjectSettingsGroup} is used in ActionManager and in Welcome dialog (b/37141013)
   */
  public void testAndroidTemplateProjectSettingsGroup() {
    AnAction action = ActionManager.getInstance().getAction(TEMPLATE_PROJECT_SETTINGS_GROUP_ID);
    assertThat(action).isInstanceOf(AndroidTemplateProjectSettingsGroup.class);
  }

  /**
   * Verify {@link AndroidTemplateProjectStructureAction} is used in Welcome dialog
   */
  public void testAndroidTemplateProjectStructureActionInWelcomeDialog() {
    AnAction configureProjectStructureAction = ActionManager.getInstance().getAction("WelcomeScreen.Configure.ProjectStructure");
    assertThat(configureProjectStructureAction).isInstanceOf(AndroidTemplateProjectStructureAction.class);
  }

  public void testRefreshProjectsActionIsHidden() {
    AnAction refreshProjectsAction = ActionManager.getInstance().getAction("ExternalSystem.RefreshAllProjects");
    assertThat(refreshProjectsAction).isInstanceOf(EmptyAction.class);
  }

  public void testSelectProjectToImportActionIsHidden() {
    AnAction selectProjectToImportAction = ActionManager.getInstance().getAction("ExternalSystem.SelectProjectDataToImport");
    assertThat(selectProjectToImportAction).isInstanceOf(EmptyAction.class);
  }

  public void testModifyCodeStyleSettingsReplacesVersion1WithVersion3() {
    CodeStyleSchemes schemes = CodeStyleSchemes.getInstance();

    CodeStyleScheme scheme = schemes.createNewScheme("New Scheme", schemes.getDefaultScheme());
    scheme.getCodeStyleSettings().getCommonSettings(XMLLanguage.INSTANCE)
      .setArrangementSettings(AndroidXmlPredefinedCodeStyle.createVersion1Settings());

    schemes.setCurrentScheme(scheme);

    GradleSpecificInitializer.modifyCodeStyleSettings();

    assertThat(schemes.getCurrentScheme().getCodeStyleSettings().getCommonSettings(XMLLanguage.INSTANCE).getArrangementSettings())
      .isEqualTo(AndroidXmlPredefinedCodeStyle.createVersion3Settings());
  }

  public void testModifyCodeStyleSettingsDoesntReplaceVersion1() {
    StdArrangementMatchRule rule = AndroidXmlRearranger.newAttributeRule("xmlns:android", "^$", Order.KEEP);
    ArrangementSettings settings = StdArrangementSettings.createByMatchRules(Collections.emptyList(), Collections.singletonList(rule));

    CodeStyleSchemes schemes = CodeStyleSchemes.getInstance();

    CodeStyleScheme scheme = schemes.createNewScheme("New Scheme", schemes.getDefaultScheme());
    scheme.getCodeStyleSettings().getCommonSettings(XMLLanguage.INSTANCE).setArrangementSettings(settings);

    schemes.setCurrentScheme(scheme);

    GradleSpecificInitializer.modifyCodeStyleSettings();

    assertThat(schemes.getCurrentScheme().getCodeStyleSettings().getCommonSettings(XMLLanguage.INSTANCE).getArrangementSettings())
      .isEqualTo(settings);
  }
}
