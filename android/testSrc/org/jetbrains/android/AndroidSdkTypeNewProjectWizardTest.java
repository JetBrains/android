/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android;

import com.intellij.ide.projectWizard.NewProjectWizardTestCase;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.TestActionEvent;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.util.AndroidBundle;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 *         Date: 12/5/12
 */
public class AndroidSdkTypeNewProjectWizardTest extends NewProjectWizardTestCase {

  public void testUnsatisfied() throws Exception {
    ProjectSdksModel model = new ProjectSdksModel();
    AnAction action = getAddAction(model);
    try {
      action.actionPerformed(new TestActionEvent(action));
      fail("Exception should be thrown");
    }
    catch (Exception e) {
      e.printStackTrace();
      assertEquals(AndroidSdkType.getInstance().getUnsatisfiedDependencyMessage(), e.getMessage());
    }
  }

  public void testSatisfied() throws Exception {
    ProjectSdksModel model = new ProjectSdksModel();
    model.addSdk(IdeaTestUtil.getMockJdk17());
    final ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();
    final Sdk sdk = jdkTable.createSdk("a", AndroidSdkType.getInstance());
    mySdks.add(sdk);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        jdkTable.addJdk(sdk);
      }
    });
    AnAction action = getAddAction(model);
    try {
      action.actionPerformed(new TestActionEvent(action));
      fail("Exception should be thrown");
    }
    catch (Exception e) {
      assertEquals(AndroidBundle.message("cannot.parse.sdk.error"), e.getMessage());
    }
  }

  private static AnAction getAddAction(ProjectSdksModel model) {
    DefaultActionGroup group = new DefaultActionGroup();
    model.createAddActions(group, new JPanel(), sdk -> {}, id -> id == AndroidSdkType.getInstance());
    AnAction[] children = group.getChildren(null);
    assertEquals(1, children.length);
    return children[0];
  }
}
