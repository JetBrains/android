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

import com.android.resources.ResourceFolderType;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.npw.assetstudio.VectorIconGenerator;
import com.android.tools.idea.npw.assetstudio.assets.VectorAsset;
import com.android.tools.idea.npw.assetstudio.ui.VectorAssetBrowser;
import com.android.tools.idea.npw.assetstudio.ui.VectorIconButton;
import com.android.tools.idea.npw.project.AndroidPackageUtils;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.adapters.StringToIntAdapterProperty;
import com.android.tools.idea.observable.core.*;
import com.android.tools.idea.observable.expressions.Expression;
import com.android.tools.idea.observable.expressions.optional.AsOptionalExpression;
import com.android.tools.idea.observable.expressions.string.FormatExpression;
import com.android.tools.idea.observable.ui.EnabledProperty;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.observable.ui.SliderValueProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.res.IdeResourceNameValidator;
import com.android.tools.idea.ui.VectorImageComponent;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

/**
 * Step for generating Android icons from some vector source.
 */
@SuppressWarnings("UseJBColor") // Colors are used for the graphics generator, not the plugin UI.
public final class NewVectorAssetStep extends ModelWizardStep<GenerateIconsModel> {
  private static final int DEFAULT_MATERIAL_ICON_SIZE = 24;
  private static final String ICON_PREFIX = "ic_";
  private static final String VECTOR_ASSET_PATH_PROPERTY = "VectorAssetImportPath";

  private final VectorIconGenerator myIconGenerator;
  private final ObjectProperty<VectorAsset> myActiveAsset;
  private final OptionalProperty<Dimension> myOriginalSize = new OptionalValueProperty<>();

  private final BoolProperty isValidAsset = new BoolValueProperty();
  private final VectorPreviewUpdater myPreviewUpdater = new VectorPreviewUpdater();
  private final IdeResourceNameValidator myNameValidator = IdeResourceNameValidator.forFilename(ResourceFolderType.DRAWABLE);

  private final BindingsManager myGeneralBindings = new BindingsManager();
  private final BindingsManager myActiveAssetBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();
  @NotNull private final AndroidFacet myFacet;

  private final ValidatorPanel myValidatorPanel;

  private JPanel myPanel;
  private VectorImageComponent myImagePreview;
  private JLabel myImageFileLabel;
  private JTextField myOutputNameField;
  private JPanel myErrorPanel;
  private JPanel myBrowserPanel;
  private JTextField myWidthTextField;
  private JTextField myHeightTextField;
  private JCheckBox myEnableAutoMirroredCheckBox;
  private JPanel myPreviewPanel;
  private JSlider myOpacitySlider;
  private JPanel myResizePanel;
  private JLabel mySizeLabel;
  private JPanel myResourceNamePanel;
  private JRadioButton myMaterialIconRadioButton;
  private JRadioButton myLocalFileRadioButton;
  private JPanel myOpacityPanel;
  private JPanel myIconPickerPanel;
  private JCheckBox myOverrideSizeCheckBox;
  private JBLabel myOpacityValueLabel;
  private JPanel myLeftPanel;
  private JPanel myRightPanel;
  private JPanel mySourceAssetRadioButtons;
  private JPanel mySourceAssetTypePanel;
  private VectorAssetBrowser myBrowser;
  private VectorIconButton myIconButton;
  private JBScrollPane myErrorsScrollPane;
  private JTextArea myErrorsTextArea;

  public NewVectorAssetStep(@NotNull GenerateIconsModel model, @NotNull AndroidFacet facet) {
    super(model, "Configure Vector Asset");
    myFacet = facet;

    int minSdkVersion = AndroidModuleInfo.getInstance(myFacet).getMinSdkVersion().getApiLevel();
    myIconGenerator = new VectorIconGenerator(minSdkVersion);

    // Start with the icon radio button selected, because icons are easy to browse and play around
    // with right away.
    myMaterialIconRadioButton.setSelected(true);
    myActiveAsset = new ObjectValueProperty<>(myIconButton.getAsset());

    myValidatorPanel = new ValidatorPanel(this, myPanel);
  }

  @NotNull
  @Override
  protected Collection<? extends ModelWizardStep> createDependentSteps() {
    return Collections.singletonList(new ConfirmGenerateIconsStep(getModel(), AndroidPackageUtils.getModuleTemplates(myFacet, null)));
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    final Runnable onAssetModified = myPreviewUpdater::enqueueUpdate;

    loadAssetPath();

    SelectedProperty iconSelected = new SelectedProperty(myMaterialIconRadioButton);
    myListeners.receiveAndFire(iconSelected, isIconActive -> {
      myIconPickerPanel.setVisible(isIconActive);
      myBrowserPanel.setVisible(!isIconActive);
      myActiveAsset.set(isIconActive ? myIconButton.getAsset() : myBrowser.getAsset());
    });
    ActionListener assetListener = actionEvent -> {
      onAssetModified.run();
      saveAssetPath();
    };
    myIconButton.addAssetListener(assetListener);
    myBrowser.addAssetListener(assetListener);
    Disposer.register(this, myIconButton);
    Disposer.register(this, myBrowser);

    final BoolProperty overrideSize = new SelectedProperty(myOverrideSizeCheckBox);
    final IntProperty width = new IntValueProperty();
    final IntProperty height = new IntValueProperty();
    myGeneralBindings.bindTwoWay(new StringToIntAdapterProperty(new TextProperty(myWidthTextField)), width);
    myGeneralBindings.bindTwoWay(new StringToIntAdapterProperty(new TextProperty(myHeightTextField)), height);
    myGeneralBindings.bind(new EnabledProperty(myWidthTextField), overrideSize);
    myGeneralBindings.bind(new EnabledProperty(myHeightTextField), overrideSize);
    myListeners.listenAll(overrideSize, myOriginalSize).withAndFire(() -> {
      if (!overrideSize.get() || !myOriginalSize.get().isPresent()) {
        width.set(DEFAULT_MATERIAL_ICON_SIZE);
        height.set(DEFAULT_MATERIAL_ICON_SIZE);
      }
      else {
        width.set(myOriginalSize.getValue().width);
        height.set(myOriginalSize.getValue().height);
      }
    });

    final IntProperty opacityValue = new SliderValueProperty(myOpacitySlider);
    myGeneralBindings.bind(new TextProperty(myOpacityValueLabel), new FormatExpression("%d %%", opacityValue));

    final BoolProperty autoMirrored = new SelectedProperty(myEnableAutoMirroredCheckBox);

    myListeners.listenAll(myActiveAsset, overrideSize, width, height, opacityValue, autoMirrored).with(onAssetModified);

    final StringProperty name = new TextProperty(myOutputNameField);
    myListeners.listenAndFire(myActiveAsset, sender -> {
      myActiveAssetBindings.releaseAll();

      myActiveAssetBindings.bind(name, new Expression<String>(myActiveAsset.get().path()) {
        @NotNull
        @Override
        public String get() {
          File path = myActiveAsset.get().path().get();
          if (path.exists() && !path.isDirectory()) {
            String name1 = FileUtil.getNameWithoutExtension(path).toLowerCase(Locale.getDefault());
            if (!name1.startsWith(ICON_PREFIX)) {
              name1 = ICON_PREFIX + AndroidResourceUtil.getValidResourceFileName(name1);
            }
            return AndroidResourceUtil.getValidResourceFileName(name1);
          }
          else {
            return "ic_vector_name";
          }
        }
      });

      myValidatorPanel.registerValidator(name, value -> Validator.Result.fromNullableMessage(myNameValidator.getErrorText(value)));
      myValidatorPanel.registerTest(isValidAsset, "The specified asset could not be parsed. Please choose another asset.");

      myActiveAssetBindings.bind(myActiveAsset.get().opacity(), opacityValue);
      myActiveAssetBindings.bind(myActiveAsset.get().autoMirrored(), autoMirrored);
      myActiveAssetBindings.bind(myActiveAsset.get().outputWidth(), width);
      myActiveAssetBindings.bind(myActiveAsset.get().outputHeight(), height);
    });

    // Refresh the asset preview, but fire using invokeLater, as this lets the UI lay itself out,
    // which should happen before the "generate preview" logic runs.
    ApplicationManager.getApplication().invokeLater(onAssetModified, ModalityState.any());

    // Cast VectorAsset -> BaseAsset
    myGeneralBindings.bind(myIconGenerator.sourceAsset(), new AsOptionalExpression<>(myActiveAsset));
    myGeneralBindings.bind(myIconGenerator.outputName(), name);
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myValidatorPanel;
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @Override
  protected void onProceeding() {
    getModel().setIconGenerator(myIconGenerator);
  }

  @Override
  public void dispose() {
    myGeneralBindings.releaseAll();
    myActiveAssetBindings.releaseAll();
    myListeners.releaseAll();
    myIconGenerator.dispose();
  }

  private void saveAssetPath() {
    PropertiesComponent properties = PropertiesComponent.getInstance(myFacet.getModule().getProject());
    File path = myBrowser.getAsset().path().get();
    properties.setValue(VECTOR_ASSET_PATH_PROPERTY, path.getParent());
  }

  private void loadAssetPath() {
    Project project = myFacet.getModule().getProject();
    PropertiesComponent properties = PropertiesComponent.getInstance(project);
    String lastPath = properties.getValue(VECTOR_ASSET_PATH_PROPERTY);

    if (lastPath != null) {
      String defaultPath = FileUtil.toSystemDependentName(lastPath);
      myBrowser.getAsset().path().set(new File(defaultPath));
    } else {
      String projectPath = project.getBasePath();
      if (projectPath != null) {
        String defaultPath = FileUtil.toSystemDependentName(projectPath);
        myBrowser.getAsset().path().set(new File(defaultPath));
      }
    }
  }

  /**
   * Parsing and generating a vector preview is not always a lightweight operation, and if we try to
   * do it synchronously, especially with a larger vector file, it can stutter the UI. So instead, we
   * enqueue the request to run on a background thread. If several requests are made in a row while
   * an existing worker is still in progress, they will only generate a single update, run as soon
   * as the current update finishes.
   *
   * Call {@link #enqueueUpdate()} in order to kickstart the generation of a new preview.
   */
  private final class VectorPreviewUpdater {
    @Nullable private SwingWorker<Void, Void> myCurrentWorker;
    @Nullable private SwingWorker<Void, Void> myEnqueuedWorker;

    /**
     * Begin parsing the current file in {@link #myActiveAsset} and, if it's valid, update the UI
     * (particularly, the image preview and errors area). If an update is already in process, then
     * this will enqueue another request to run as soon as the current one is over.
     *
     * The width of {@link #myImagePreview} is used when calculating a preview image, so be sure
     * the layout manager has finished laying out your UI before calling this method.
     *
     * This method must be called on the dispatch thread.
     */
    public void enqueueUpdate() {
      ApplicationManager.getApplication().assertIsDispatchThread();

      if (myCurrentWorker == null) {
        myCurrentWorker = createWorker();
        myCurrentWorker.execute();
      }
      else if (myEnqueuedWorker == null) {
        myEnqueuedWorker = createWorker();
      }
    }

    private SwingWorker<Void, Void> createWorker() {
      return new SwingWorker<Void, Void>() {
        VectorAsset.ParseResult myParseResult;

        @Override
        protected Void doInBackground() {
          try {
            myParseResult = myActiveAsset.get().parse(myImagePreview.getWidth(), true);
          } catch (Throwable t) {
            Logger.getInstance(getClass()).error(t);
            myParseResult = new VectorAsset.ParseResult("Internal error parsing " + myActiveAsset.get().path().get().getName());
          }
          return null;
        }

        @Override
        protected void done() {
          assert myParseResult != null;
          // it IS possible to have invalid asset, but no error, in fact that is the initial state before a file is chosen.
          isValidAsset.set(myParseResult.isValid());
          if (myParseResult.isValid()) {
            BufferedImage image = myParseResult.getImage();
            myImagePreview.setIcon(image == null ? null : new ImageIcon(image));
            myOriginalSize.setValue(new Dimension(myParseResult.getOriginalWidth(), myParseResult.getOriginalHeight()));
          }
          else {
            myImagePreview.setIcon(null);
            myOriginalSize.clear();
          }

          String error = myParseResult.getErrors();
          myErrorPanel.setVisible(!error.isEmpty());
          myErrorsTextArea.setText(error);
          ApplicationManager.getApplication().invokeLater(() -> myErrorsScrollPane.getVerticalScrollBar().setValue(0), ModalityState.any());

          myCurrentWorker = null;
          if (myEnqueuedWorker != null) {
            myCurrentWorker = myEnqueuedWorker;
            myEnqueuedWorker = null;
            ApplicationManager.getApplication().invokeLater(() -> myCurrentWorker.execute(), ModalityState.any());
          }
        }
      };
    }
  }
}