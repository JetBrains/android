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
package com.android.tools.idea.npw.assetstudio.wizard;

import com.android.resources.Density;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.adtui.validation.validators.FalseValidator;
import com.android.tools.idea.npw.assetstudio.IconGenerator;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.android.tools.idea.ui.FileTreeCellRenderer;
import com.android.tools.idea.ui.FileTreeModel;
import com.android.tools.idea.ui.wizard.WizardUtils;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSortedSet;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.npw.assetstudio.IconGenerator.pathToDensity;

/**
 * This step allows the user to select a build variant and provides a preview of the assets that
 * are about to be created.
 */
public final class ConfirmGenerateIconsStep extends ModelWizardStep<GenerateIconsModel>
    implements PersistentStateComponent<PersistentState> {
  /** Limit the size of icons in the preview tree so that the tree doesn't look unnatural. */
  private static final int MAX_ICON_HEIGHT = 24;

  private static final String CONFIRMATION_STEP_PROPERTY = "confirmationStep";
  private static final String RESOURCE_DIRECTORY_PROPERTY = "resourceDirectory";

  private final List<NamedModuleTemplate> myTemplates;
  private final ValidatorPanel myValidatorPanel;
  private final ListenerManager myListeners = new ListenerManager();

  private JPanel myRootPanel;
  private JComboBox<NamedModuleTemplate> myPathsComboBox;
  private Tree myOutputPreviewTree;

  private ObjectProperty<NamedModuleTemplate> mySelectedTemplate;
  private BoolProperty myFilesAlreadyExist = new BoolValueProperty();

  public ConfirmGenerateIconsStep(@NotNull GenerateIconsModel model, @NotNull List<NamedModuleTemplate> templates) {
    super(model, "Confirm Icon Path");
    Preconditions.checkArgument(!templates.isEmpty());
    myTemplates = templates;
    myValidatorPanel = new ValidatorPanel(this, myRootPanel);

    DefaultComboBoxModel<NamedModuleTemplate> moduleTemplatesModel = new DefaultComboBoxModel<>();
    for (NamedModuleTemplate template : templates) {
      moduleTemplatesModel.addElement(template);
    }
    myPathsComboBox.setRenderer(new ListCellRendererWrapper<NamedModuleTemplate>() {
      @Override
      public void customize(JList list, NamedModuleTemplate template, int index, boolean selected, boolean hasFocus) {
        setText(template.getName());
      }
    });
    myPathsComboBox.setModel(moduleTemplatesModel);

    DefaultTreeModel emptyModel = new DefaultTreeModel(null);
    myOutputPreviewTree.setModel(emptyModel);
    myOutputPreviewTree.setCellRenderer(new FileTreeCellRenderer());
    myOutputPreviewTree.setBorder(BorderFactory.createLineBorder(UIUtil.getBoundsColor()));
    // Tell the tree to ask the TreeCellRenderer for an individual height for each cell.
    myOutputPreviewTree.setRowHeight(-1);
    myOutputPreviewTree.getEmptyText().setText("No resource folder defined in project");

    String alreadyExistsError = WizardUtils.toHtmlString(
        "Some existing files will be overwritten by this operation.<br>" +
        "Files which replace existing files are marked red in the preview above.");
    myValidatorPanel.registerValidator(myFilesAlreadyExist, new FalseValidator(Validator.Severity.WARNING, alreadyExistsError));
  }

  @Override
  @NotNull
  protected JComponent getComponent() {
    return myValidatorPanel;
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    mySelectedTemplate = ObjectProperty.wrap(new SelectedItemProperty<>(myPathsComboBox));

    PersistentStateUtil.load(this, getModel().getPersistentState().getChild(CONFIRMATION_STEP_PROPERTY));
  }

  @Override
  public void onWizardFinished() {
    getModel().getPersistentState().setChild(CONFIRMATION_STEP_PROPERTY, getState());
  }

  @Override
  @NotNull
  public PersistentState getState() {
    PersistentState state = new PersistentState();
    NamedModuleTemplate moduleTemplate = mySelectedTemplate.get();
    state.set(RESOURCE_DIRECTORY_PROPERTY, moduleTemplate.getName(), myTemplates.get(0).getName());
    return state;
  }

  @Override
  public void loadState(@NotNull PersistentState state) {
    String templateName = state.get(RESOURCE_DIRECTORY_PROPERTY);
    if (templateName != null) {
      for (NamedModuleTemplate template : myTemplates) {
        if (template.getName().equals(templateName)) {
          mySelectedTemplate.set(template);
          break;
        }
      }
    }
  }

  @Override
  @NotNull
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @Override
  protected void onProceeding() {
    getModel().setPaths(mySelectedTemplate.get().getPaths());
  }

  @Override
  protected void onEntering() {
    myListeners.release(mySelectedTemplate); // Just in case we're entering this step a second time
    myListeners.receiveAndFire(mySelectedTemplate, template -> {
      IconGenerator iconGenerator = getModel().getIconGenerator();
      File resDir = template.getPaths().getResDirectory();
      if (iconGenerator == null || resDir == null || resDir.getParentFile() == null) {
        return;
      }

      Map<File, BufferedImage> pathIconMap = iconGenerator.generateIntoFileMap(template.getPaths());
      myFilesAlreadyExist.set(false);

      int minHeight = Integer.MAX_VALUE;
      int maxHeight = Integer.MIN_VALUE;
      for (BufferedImage image : pathIconMap.values()) {
        minHeight = Math.min(minHeight, image.getHeight());
        maxHeight = Math.max(maxHeight, image.getHeight());
      }

      ImmutableSortedSet.Builder<File> sortedPaths = ImmutableSortedSet.orderedBy((file1, file2) -> {
        String path1 = file1.getAbsolutePath();
        String path2 = file2.getAbsolutePath();
        Density density1 = pathToDensity(path1);
        Density density2 = pathToDensity(path2);

        if (density1 != null && density2 != null && density1 != density2) {
          // Sort least dense to most dense
          return Integer.compare(density2.ordinal(), density1.ordinal());
        }
        else {
          BufferedImage image1 = pathIconMap.get(file1);
          BufferedImage image2 = pathIconMap.get(file2);
          int compareValue = Integer.compare(image2.getHeight(), image1.getHeight());
          // If heights are the same, use path as a tie breaker.
          return compareValue != 0 ? compareValue : path2.compareTo(path1);
        }

      });
      sortedPaths.addAll(pathIconMap.keySet());

      FileTreeModel treeModel = new FileTreeModel(resDir.getParentFile(), true);

      for (File path : sortedPaths.build()) {
        Image image = pathIconMap.get(path);

        if (path.exists()) {
          myFilesAlreadyExist.set(true);
        }

        // By default, icons grow exponentially, and if presented at scale, may take up way too
        // much real estate. Instead, let's scale down all icons proportionally so the largest
        // one fits in our maximum allowed space.
        if (maxHeight > MAX_ICON_HEIGHT) {
          int height = image.getHeight(null);
          int width = image.getWidth(null);

          double hScale;
          if (maxHeight != minHeight) {
            // From hMin <= hCurr <= hMax, interpolate to hMin <= hFinal <= MAX_ICON_HEIGHT
            double hCurrPercent = (double)(height - minHeight) / (double)(maxHeight - minHeight);
            double scaledDeltaH = hCurrPercent * (MAX_ICON_HEIGHT - minHeight);
            double hCurrScaled = minHeight + scaledDeltaH;
            hScale = hCurrScaled / height;
          }
          else {
            // This happens if there's only one entry in the list and it's larger than MAX_TREE_ROW_HEIGHT.
            hScale = MAX_ICON_HEIGHT / (double)height;
          }

          int hFinal = (int)JBUI.scale((float)(height * hScale));
          int wFinal = (int)JBUI.scale((float)(width * hScale));
          image = image.getScaledInstance(wFinal, hFinal, Image.SCALE_SMOOTH);
        }

        treeModel.forceAddFile(path, new ImageIcon(image));
      }

      myOutputPreviewTree.setModel(treeModel);

      // The tree should be totally expanded by default.
      for (int i = 0; i < myOutputPreviewTree.getRowCount(); ++i) {
        myOutputPreviewTree.expandRow(i);
      }
    });
  }

  @Override
  public void dispose() {
    myListeners.releaseAll();
  }
}
