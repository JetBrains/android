/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.npw;

import com.android.builder.model.SourceProvider;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.npw.assetstudio.wizard.ConfirmGenerateIconsStep;
import com.android.tools.idea.ui.ApiComboBoxItem;
import com.android.tools.idea.ui.FileTreeCellRenderer;
import com.android.tools.idea.ui.FileTreeModel;
import com.android.tools.idea.wizard.template.TemplateWizardState;
import com.android.tools.idea.wizard.template.TemplateWizardStep;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * This step allows the user to select a build variant and provides a preview
 * of the assets that are about to be created.
 *
 * @deprecated Replaced by {@link ConfirmGenerateIconsStep}
 */
public class ChooseOutputResDirStep extends TemplateWizardStep {

  public static final String ATTR_TARGET_MODULE = "targetModule";
  public static final String ATTR_TARGET_VARIANT = "targetVariant";
  public static final String ATTR_OUTPUT_FOLDER = "outputFolder";
  private final VirtualFile myTargetFile;

  private JComboBox myModuleComboBox;
  private JLabel myDescription;
  private JPanel myPanel;
  private JComboBox myVariantComboBox;
  private Tree myOutputPreviewTree;
  private JLabel myResDirLabel;

  private Module mySelectedModule;
  private Module[] myModuleArray;
  private boolean myComputeNewSourceSet;

  private static final TreeModel EMPTY_MODEL = new DefaultTreeModel(null);
  private final FileTreeCellRenderer myFileTreeRenderer = new FileTreeCellRenderer();
  private FileTreeModel myTreeModel;

  private AssetStudioAssetGenerator myAssetGenerator;

  public ChooseOutputResDirStep(@NotNull TemplateWizardState state,
                                @NotNull Project project,
                                @Nullable Icon sidePanelIcon,
                                UpdateListener updateListener,
                                @Nullable Module module,
                                @Nullable VirtualFile invocationTarget) {
    super(state, project, module, sidePanelIcon, updateListener);

    myAssetGenerator = new AssetStudioAssetGenerator(state);
    myTargetFile = invocationTarget;

    init();
  }

  public void init() {
    if (myTargetFile != null) {
      VirtualFile target = myTargetFile;
      if (!target.isDirectory()) {
        // We're not interested in the source provider for an individual file (which may not be a source file).
        target = target.getParent();
      }
      AndroidFacet facet = AndroidFacet.getInstance(myModule);
      if (facet != null) {
        Iterator<SourceProvider> sourceProvidersIter = IdeaSourceProvider.getSourceProvidersForFile(facet, target, null).iterator();
        String path;
        if (sourceProvidersIter.hasNext()) {
          SourceProvider provider = sourceProvidersIter.next();
          File resDir = NewTemplateObjectWizard.findResDirectory(provider);
          if (resDir == null) {
            // No res dir exists, infer one
            path = provider.getManifestFile().getParent() + facet.getProperties().RES_FOLDER_RELATIVE_PATH;
          }
          else {
            path = resDir.getPath();
          }
        }
        else {
          // Somehow there wasn't a source provider available. Just get the default one from the facet.
          path = VfsUtil.virtualToIoFile(facet.getPrimaryResourceDir()).getPath();
        }
        myTemplateState.put(ATTR_OUTPUT_FOLDER, FileUtil.toSystemIndependentName(path));
      }
    }
    setUpUiComponents();
  }

  @NotNull
  @Override
  protected JLabel getDescription() {
    return myDescription;
  }

  @NotNull
  @Override
  protected JLabel getError() {
    return myDescription;
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public void updateStep() {
    super.updateStep();
    myComputeNewSourceSet = true;
    update();
  }

  private void setUpUiComponents() {
    // Populate the Module chooser list
    List<Module> modules = Lists.newArrayList();
    for (Module m : ModuleManager.getInstance(myProject).getModules()) {
      if (AndroidFacet.getInstance(m) != null) {
        modules.add(m);
      }
    }
    myModuleArray = new Module[modules.size()];
    modules.toArray(myModuleArray);

    populateComboBox(myModuleComboBox, myModuleArray);
    register(ATTR_TARGET_MODULE, myModuleComboBox);
    register(ATTR_TARGET_VARIANT, myVariantComboBox);

    if (myModule != null) {
      int index = -1;
      for (int i = 0; i < myModuleArray.length; ++i) {
        if (myModuleArray[i].equals(myModule)) {
          index = i;
          break;
        }
      }
      if (index != -1) {
        myModuleComboBox.setSelectedIndex(index);
      }
    }

    myOutputPreviewTree.setBorder(BorderFactory.createLoweredBevelBorder());
    // Tell the tree to ask the TreeCellRenderer for an individual height for each cell.
    myOutputPreviewTree.setRowHeight(-1);
  }

  @Override
  public void update() {
    if (myVisible) {
      super.update();
    }
  }

  @Override
  public void updateParams() {
    super.updateParams();
    mySelectedModule = myModuleArray[myModuleComboBox.getSelectedIndex()];
  }

  @Override
  public void deriveValues() {
    if (myIdsWithNewValues.contains(ATTR_TARGET_MODULE)) {
      // Populate the Build Flavor and Build Type lists
      AndroidFacet facet = AndroidFacet.getInstance(mySelectedModule);
      if (facet == null) {
        // Clear variant list
        myVariantComboBox.setModel(new DefaultComboBoxModel());
        // Remove entries from the file tree preview
        myOutputPreviewTree.setModel(EMPTY_MODEL);
        return;
      }

      AndroidModel androidModel = facet.getAndroidModel();
      if (androidModel != null) {
        show(myVariantComboBox, myResDirLabel);
        DefaultComboBoxModel comboBoxModel = new DefaultComboBoxModel();
        String moduleRoot = FileUtil.toSystemIndependentName(AndroidRootUtil.getModuleDirPath(facet.getModule()));

        File resDir = new File(FileUtil.toSystemDependentName(myTemplateState.getString(ATTR_OUTPUT_FOLDER)));
        int index = 0;
        int selectedIndex = 0;
        // Offer all source sets
        for (SourceProvider sourceProvider : IdeaSourceProvider.getAllSourceProviders(facet)) {
          for (File f : sourceProvider.getResDirectories()) {
            String resPath = FileUtil.getRelativePath(moduleRoot,
                                                      FileUtil.toSystemIndependentName(f.getPath()), '/');
            comboBoxModel.addElement(new ApiComboBoxItem(f, resPath, 1, 1));
            if (resDir != null && resDir.equals(f)) {
              selectedIndex = index;
            }
            index++;
          }
        }

        myVariantComboBox.setModel(comboBoxModel);
        myVariantComboBox.setSelectedIndex(selectedIndex);
      } else {
        hide(myVariantComboBox, myResDirLabel);
        Object path = myTemplateState.get(ATTR_OUTPUT_FOLDER);
        if (path != null) {
          myTemplateState.put(ATTR_TARGET_VARIANT, new File((String)path));
        } else {
          // Remove entries from the file tree preview
          myOutputPreviewTree.setModel(EMPTY_MODEL);
          myOutputPreviewTree.getEmptyText().setText("No Res Folder defined in project");
          return;
        }
      }
      myIdsWithNewValues.add(ATTR_TARGET_VARIANT);
    }

    if (myIdsWithNewValues.contains(ATTR_TARGET_VARIANT) || myComputeNewSourceSet) {
      File resDir = (File)myTemplateState.get(ATTR_TARGET_VARIANT);
      if (resDir == null) {
        Object selectedVariant = myVariantComboBox.getSelectedItem();
        if (selectedVariant instanceof ApiComboBoxItem) {
          resDir = (File)((ApiComboBoxItem)selectedVariant).getData();
        }
      }
      if (resDir != null) {
        // Populate the output file tree
        myTreeModel = new FileTreeModel(resDir, true);
        myTemplateState.put(ATTR_OUTPUT_FOLDER, resDir);

        try {
        Map<String, Map<String, BufferedImage>> images = myAssetGenerator.generateImages(false);
          for (String density : images.keySet()) {
            Map<String, BufferedImage> filenameMap = images.get(density);
            for (String filename : filenameMap.keySet()) {
              Image image = filenameMap.get(filename);
              Icon ic = null;
              if (image != null) {
                while (image.getHeight(null) > 200) {
                  image = image.getScaledInstance(image.getWidth(null)/2, image.getHeight(null)/2,Image.SCALE_SMOOTH);
                }
                ic = new ImageIcon(image);
              }
              myTreeModel.forceAddFile(new File(resDir, filename), ic);
            }
          }
        } catch (Exception e) {
          // pass
        }
        myOutputPreviewTree.setModel(myTreeModel);
        myOutputPreviewTree.setCellRenderer(myFileTreeRenderer);
        expandTree();
      }
    }
    myComputeNewSourceSet = false;
  }

  private void expandTree() {
    for (int i = 0; i < myOutputPreviewTree.getRowCount(); ++i) {
      myOutputPreviewTree.expandRow(i);
    }
  }

  @Override
  public boolean validate() {
    if (!super.validate()) {
      return false;
    }

    AndroidFacet facet = AndroidFacet.getInstance(mySelectedModule);
    if (facet == null) {
      setErrorHtml("The selected module does not have an Android Facet. Please choose an Android module");
      return false;
    }

    if (myTreeModel != null && myTreeModel.hasConflicts()) {
      setErrorHtml("Some existing files will be overwritten by this operation. Files which replace existing files are marked" +
                   " red in the preview above.");
    }

    return true;
  }
}
