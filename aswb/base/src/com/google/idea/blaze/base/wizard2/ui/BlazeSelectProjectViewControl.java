/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.wizard2.ui;

import com.google.idea.blaze.base.projectview.ProjectViewStorageManager;
import com.google.idea.blaze.base.wizard2.BlazeNewProjectBuilder;
import com.google.idea.blaze.base.wizard2.BlazeSelectProjectViewOption;
import com.google.idea.blaze.base.wizard2.BlazeWizardOptionProvider;
import com.intellij.openapi.options.ConfigurationException;
import java.util.Collection;
import javax.swing.JComponent;

/** UI for selecting the project view during the import process. */
public class BlazeSelectProjectViewControl {

  private BlazeSelectOptionControl<BlazeSelectProjectViewOption> selectOptionControl;

  public BlazeSelectProjectViewControl(BlazeNewProjectBuilder builder) {
    Collection<BlazeSelectProjectViewOption> options =
        BlazeWizardOptionProvider.getInstance().getSelectProjectViewOptions(builder);

    this.selectOptionControl =
        new BlazeSelectOptionControl<BlazeSelectProjectViewOption>(builder, options) {
          @Override
          protected String getTitle() {
            return BlazeSelectProjectViewControl.getTitle(builder);
          }

          @Override
          protected String getOptionKey() {
            return "select-project-view.selected-option";
          }
        };
  }

  public JComponent getUiComponent() {
    return selectOptionControl.getUiComponent();
  }

  public void validateAndUpdateModel(BlazeNewProjectBuilder builder) throws ConfigurationException {
    selectOptionControl.validateAndUpdateModel(builder);
    builder.setProjectViewOption(selectOptionControl.getSelectedOption());
  }

  public void update(BlazeNewProjectBuilder builder) {
    selectOptionControl.setTitle(getTitle(builder));
  }

  public void commit() {
    selectOptionControl.commit();
  }

  private static String getTitle(BlazeNewProjectBuilder builder) {
    String projectViewString =
        ProjectViewStorageManager.getProjectViewFileName(builder.getBuildSystem());
    return String.format("Select project view (%s file)", projectViewString);
  }
}
