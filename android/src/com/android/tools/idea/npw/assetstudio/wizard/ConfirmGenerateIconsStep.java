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
import com.android.tools.idea.npw.assetstudio.icon.AndroidIconGenerator;
import com.android.tools.idea.npw.assetstudio.icon.CategoryIconMap;
import com.android.tools.idea.npw.project.AndroidSourceSet;
import com.android.tools.idea.ui.FileTreeCellRenderer;
import com.android.tools.idea.ui.FileTreeModel;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.ObservableValue;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.expressions.value.AsValueExpression;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.adtui.validation.validators.FalseValidator;
import com.android.tools.idea.ui.wizard.WizardUtils;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.primitives.Ints;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * This step allows the user to select a build variant and provides a preview of the assets that
 * are about to be created.
 */
public final class ConfirmGenerateIconsStep extends ModelWizardStep<GenerateIconsModel> {

  private static final DefaultTreeModel EMPTY_MODEL = new DefaultTreeModel(null);

  /**
   * In our tree of icon previews, we keep row height to a reasonable maximum to prevent super
   * large icons from taking up the whole page.
   */
  private static final int MAX_TREE_ROW_HEIGHT = 200;

  private final ValidatorPanel myValidatorPanel;
  private final ListenerManager myListeners = new ListenerManager();

  private JPanel myRootPanel;
  private JComboBox myPathsComboBox;
  private Tree myOutputPreviewTree;

  private ObservableValue<AndroidSourceSet> mySelectedSourceSet;
  private BoolProperty myFilesAlreadyExist = new BoolValueProperty();

  public ConfirmGenerateIconsStep(@NotNull GenerateIconsModel model, @NotNull List<AndroidSourceSet> sourceSets) {
    super(model, "Confirm Icon Path");
    myValidatorPanel = new ValidatorPanel(this, myRootPanel);

    DefaultComboBoxModel sourceSetsModel = new DefaultComboBoxModel();
    for (AndroidSourceSet sourceSet : sourceSets) {
      sourceSetsModel.addElement(sourceSet);
    }
    myPathsComboBox.setRenderer(new ListCellRendererWrapper<AndroidSourceSet>() {
      @Override
      public void customize(JList list, AndroidSourceSet sourceSet, int index, boolean selected, boolean hasFocus) {
        setText(sourceSet.getName());
      }
    });
    myPathsComboBox.setModel(sourceSetsModel);

    myOutputPreviewTree.setModel(EMPTY_MODEL);
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

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myValidatorPanel;
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    mySelectedSourceSet = new AsValueExpression<>(new SelectedItemProperty<>(myPathsComboBox));
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @Override
  protected void onProceeding() {
    getModel().setPaths(mySelectedSourceSet.get().getPaths());
  }

  @Override
  protected void onEntering() {
    myListeners.release(mySelectedSourceSet); // Just in case we're entering this step a second time
    myListeners.receiveAndFire(mySelectedSourceSet, sourceSet -> {
      AndroidIconGenerator iconGenerator = getModel().getIconGenerator();
      File resDir = sourceSet.getPaths().getResDirectory();
      if (iconGenerator == null || resDir == null || resDir.getParentFile() == null) {
        return;
      }

      final Map<File, BufferedImage> pathIconMap = iconGenerator.generateIntoFileMap(sourceSet.getPaths());
      myFilesAlreadyExist.set(false);

      int minHeight = Integer.MAX_VALUE;
      int maxHeight = Integer.MIN_VALUE;
      for (BufferedImage image : pathIconMap.values()) {
        minHeight = Math.min(minHeight, image.getHeight());
        maxHeight = Math.max(maxHeight, image.getHeight());
      }

      ImmutableSortedSet.Builder<File> sortedPaths = ImmutableSortedSet.orderedBy(new Comparator<File>() {
        @Override
        public int compare(File file1, File file2) {
          String path1 = file1.getAbsolutePath();
          String path2 = file2.getAbsolutePath();
          Density density1 = CategoryIconMap.pathToDensity(path1);
          Density density2 = CategoryIconMap.pathToDensity(path2);

          if (density1 != null && density2 != null && density1 != density2) {
            // Sort least dense to most dense
            return Ints.compare(density2.ordinal(), density1.ordinal());
          }
          else {
            BufferedImage image1 = pathIconMap.get(file1);
            BufferedImage image2 = pathIconMap.get(file2);
            int compareValue = Ints.compare(image2.getHeight(), image1.getHeight());
            // If heights are the same, use path as a tie breaker
            return (compareValue != 0) ? compareValue : path2.compareTo(path1);
          }

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
        if (maxHeight > MAX_TREE_ROW_HEIGHT) {
          int hCurr = image.getHeight(null);
          int wCurr = image.getWidth(null);

          double hScale;
          if (maxHeight != minHeight) {
            // From hMin <= hCurr <= hMax, interpolate to hMin <= hFinal <= MAX_TREE_ROW_HEIGHT
            double hCurrPercent = (double)(hCurr - minHeight) / (double)(maxHeight - minHeight);
            double scaledDeltaH = hCurrPercent * (MAX_TREE_ROW_HEIGHT - minHeight);
            double hCurrScaled = minHeight + scaledDeltaH;
            hScale = hCurrScaled / hCurr;
          }
          else {
            // This happens if there's only one entry in the list and it's larger than
            // MAX_TREE_ROW_HEIGHT
            hScale = MAX_TREE_ROW_HEIGHT / (double)hCurr;
          }

          int hFinal = (int)(hCurr * hScale);
          int wFinal = (int)(wCurr * hScale);
          image = image.getScaledInstance(wFinal, hFinal, Image.SCALE_SMOOTH);
        }

        treeModel.forceAddFile(path, new ImageIcon(image));
      }

      myOutputPreviewTree.setModel(treeModel);

      // The tree should be totally expanded by default
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
