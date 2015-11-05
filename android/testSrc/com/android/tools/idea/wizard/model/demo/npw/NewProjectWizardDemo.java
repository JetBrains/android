/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.wizard.model.demo.npw;

import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.android.tools.idea.wizard.model.demo.npw.android.FormFactor;
import com.android.tools.idea.wizard.model.demo.npw.models.ActivityModel;
import com.android.tools.idea.wizard.model.demo.npw.models.ProjectModel;
import com.android.tools.idea.wizard.model.demo.npw.steps.ChooseActivityStep;
import com.android.tools.idea.wizard.model.demo.npw.steps.ChooseFormFactorsStep;
import com.android.tools.idea.wizard.model.demo.npw.steps.ConfigureProjectStep;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Mock version of our new project and new activity wizards.
 * <p/>
 * This demo provides a comprehensive example of how to put together a wizard built out of multiple
 * models, and how you can reuse models across wizards (both the project wizard and the activity
 * wizard use an activity model)
 */
public final class NewProjectWizardDemo {
  private JPanel myRootPanel;
  private JButton myNewProjectButton;
  private JButton myNewMobileActivityButton;
  private JButton myNewWearActivityButton;
  private JButton myNewTVActivityButton;

  public NewProjectWizardDemo() {
    myNewProjectButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ModelWizard projectWizard = new ModelWizard();

        ProjectModel projectModel = new ProjectModel();
        projectWizard.addStep(new ConfigureProjectStep(projectModel));
        projectWizard.addStep(new ChooseFormFactorsStep(projectModel));

        runWizard(projectWizard, myNewProjectButton.getText());
      }
    });

    myNewMobileActivityButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ModelWizard activityWizard = new ModelWizard();
        ActivityModel model = new ActivityModel(FormFactor.MOBILE);
        activityWizard.addStep(new ChooseActivityStep(model));

        runWizard(activityWizard, myNewMobileActivityButton.getText());
      }
    });

    myNewWearActivityButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ModelWizard activityWizard = new ModelWizard();
        ActivityModel model = new ActivityModel(FormFactor.WEAR);
        activityWizard.addStep(new ChooseActivityStep(model));

        runWizard(activityWizard, myNewWearActivityButton.getText());
      }
    });

    myNewTVActivityButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ModelWizard activityWizard = new ModelWizard();
        ActivityModel model = new ActivityModel(FormFactor.TV);
        activityWizard.addStep(new ChooseActivityStep(model));

        runWizard(activityWizard, myNewTVActivityButton.getText());
      }
    });
  }

  private static void runWizard(ModelWizard wizard, String title) {
    ModelWizardDialog dialog = new ModelWizardDialog(wizard, title, new DemoWizardLayout(), null);
    dialog.setSize(-1, 300);
    dialog.show();
  }

  public static void main(String[] args) {
    JFrame frame = new JFrame("Model Wizard Demo - New Project Wizards");
    frame.setContentPane(new NewProjectWizardDemo().myRootPanel);
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    frame.pack();
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);
  }
}
