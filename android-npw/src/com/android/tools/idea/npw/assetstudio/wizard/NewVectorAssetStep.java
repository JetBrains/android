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

import static com.android.tools.idea.npw.assetstudio.AssetStudioUtils.roundToInt;
import static com.android.tools.idea.npw.project.AndroidPackageUtils.getModuleTemplates;

import com.android.annotations.concurrency.UiThread;
import com.android.annotations.concurrency.WorkerThread;
import com.android.ide.common.resources.FileResourceNameValidator;
import com.android.ide.common.vectordrawable.VdOverrideInfo;
import com.android.resources.ResourceFolderType;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.model.StudioAndroidModuleInfo;
import com.android.tools.idea.npw.assetstudio.VectorIconGenerator;
import com.android.tools.idea.npw.assetstudio.assets.VectorAsset;
import com.android.tools.idea.npw.assetstudio.ui.VectorAssetBrowser;
import com.android.tools.idea.npw.assetstudio.ui.VectorIconButton;
import com.android.tools.idea.npw.assetstudio.ui.VectorImageComponent;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.ObservableValue;
import com.android.tools.idea.observable.Receiver;
import com.android.tools.idea.observable.adapters.StringToDoubleAdapterProperty;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.DoubleProperty;
import com.android.tools.idea.observable.core.DoubleValueProperty;
import com.android.tools.idea.observable.core.IntProperty;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObjectValueProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.core.OptionalValueProperty;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.expressions.Expression;
import com.android.tools.idea.observable.expressions.optional.AsOptionalExpression;
import com.android.tools.idea.observable.expressions.string.FormatExpression;
import com.android.tools.idea.observable.ui.ColorProperty;
import com.android.tools.idea.observable.ui.EnabledProperty;
import com.android.tools.idea.observable.ui.HasFocusProperty;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.observable.ui.SelectedRadioButtonProperty;
import com.android.tools.idea.observable.ui.SliderValueProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.res.IdeResourceNameValidator;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.ColorPanel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.concurrency.SwingWorker;
import com.intellij.util.ui.JBUI;
import java.awt.Color;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JTextField;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

/**
 * A wizard step for generating Android vector drawable icons.
 */
public final class NewVectorAssetStep extends ModelWizardStep<GenerateIconsModel> implements PersistentStateComponent<PersistentState> {
  private static final String DEFAULT_OUTPUT_NAME = "vector_name";
  // Start with the Clip Art radio button selected, because the clip art icons are easy to browse
  // and play around with right away.
  private static final AssetSourceType DEFAULT_ASSET_SOURCE_TYPE = AssetSourceType.CLIP_ART;
  @SuppressWarnings("UseJBColor") // Intentionally not using JBColor for Android icons.
  private static final Color DEFAULT_COLOR = Color.BLACK;

  private static final String VECTOR_ASSET_STEP_PROPERTY = "vectorAssetStep";
  private static final String OUTPUT_NAME_PROPERTY = "outputName";
  private static final String ASSET_SOURCE_TYPE_PROPERTY = "assetSourceType";
  private static final String CLIPART_ASSET_PROPERTY = "clipartAsset";
  private static final String SOURCE_FILE_PROPERTY = "sourceFile";
  private static final String COLOR_PROPERTY = "color";
  private static final String OPACITY_PERCENT_PROPERTY = "opacityPercent";
  private static final String AUTO_MIRRORED_PROPERTY = "autoMirrored";

  private final ObjectProperty<AssetSourceType> myAssetSourceType;
  private final ObjectProperty<VectorAsset> myActiveAsset;
  private final StringProperty myOutputName;
  private final ObservableBool myWidthHasFocus;
  private final ObservableBool myHeightHasFocus;
  private final DoubleProperty myWidth = new DoubleValueProperty();
  private final DoubleProperty myHeight = new DoubleValueProperty();
  private final ColorProperty myColor;
  private final IntProperty myOpacityPercent;
  private final BoolProperty myAutoMirrored;

  private final BoolValueProperty myAssetIsValid = new BoolValueProperty();
  private final ObjectValueProperty<Validator.Result> myAssetValidityState = new ObjectValueProperty<>(Validator.Result.OK);
  private final IdeResourceNameValidator myNameValidator = IdeResourceNameValidator.forFilename(ResourceFolderType.DRAWABLE);

  private final BindingsManager myGeneralBindings = new BindingsManager();
  private final BindingsManager myActiveAssetBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();

  @NotNull private final VectorIconGenerator myIconGenerator;
  @NotNull private final AndroidFacet myFacet;

  private final ValidatorPanel myValidatorPanel;

  private double myAspectRatio;
  @Nullable StringToDoubleAdapterProperty myWidthAdapter;
  @Nullable StringToDoubleAdapterProperty myHeightAdapter;

  private JPanel myPanel;
  private JPanel myImagePreviewPanel;
  private VectorImageComponent myImagePreview;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myResourceNamePanel;
  private JTextField myOutputNameTextField;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel mySourceAssetTypePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel mySourceAssetRadioButtons;
  private JRadioButton myClipartRadioButton;
  private JRadioButton myLocalFileRadioButton;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myIconPickerPanel;
  private VectorIconButton myClipartAssetButton;
  private JPanel myFileBrowserPanel;
  private VectorAssetBrowser myFileBrowser;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myResizePanel;
  private JTextField myWidthTextField;
  private JTextField myHeightTextField;
  private JPanel myColorRowPanel;
  private ColorPanel myColorPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myOpacityPanel;
  private JSlider myOpacitySlider;
  private JBLabel myOpacityValueLabel;
  private JCheckBox myEnableAutoMirroredCheckBox;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myPreviewPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myLeftPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myRightPanel;

  public NewVectorAssetStep(@NotNull GenerateIconsModel model, @NotNull AndroidFacet facet) {
    super(model, "Configure Vector Asset");
    myFacet = facet;

    int minSdkVersion = StudioAndroidModuleInfo.getInstance(myFacet).getMinSdkVersion().getApiLevel();
    myIconGenerator = new VectorIconGenerator(myFacet.getModule().getProject(), minSdkVersion);
    Disposer.register(this, myIconGenerator);

    myImagePreviewPanel.setBorder(JBUI.Borders.customLine(JBColor.border()));

    myAssetSourceType = new SelectedRadioButtonProperty<>(DEFAULT_ASSET_SOURCE_TYPE, AssetSourceType.values(),
                                                          myClipartRadioButton, myLocalFileRadioButton);
    myActiveAsset = new ObjectValueProperty<>(myClipartAssetButton.getAsset());
    myOutputName = new TextProperty(myOutputNameTextField);
    myOutputName.set(DEFAULT_OUTPUT_NAME);
    myWidthHasFocus = new HasFocusProperty(myWidthTextField);
    myHeightHasFocus = new HasFocusProperty(myHeightTextField);
    myColor = new ColorProperty(myColorPanel);
    myOpacityPercent = new SliderValueProperty(myOpacitySlider);
    myAutoMirrored = new SelectedProperty(myEnableAutoMirroredCheckBox);

    myValidatorPanel = new ValidatorPanel(this, myPanel, "Conversion Issues", "Encountered Issues:");

    ActionListener assetListener = actionEvent -> renderPreviews();
    myClipartAssetButton.addAssetListener(assetListener);
    myClipartAssetButton.setToolTipText("Select Clip Art");
    myFileBrowser.addAssetListener(assetListener);

    myListeners.listenAndFire(myAssetSourceType, sourceType -> {
      myIconPickerPanel.setVisible(sourceType == AssetSourceType.CLIP_ART);
      myColorRowPanel.setVisible(sourceType == AssetSourceType.CLIP_ART);
      myFileBrowserPanel.setVisible(sourceType == AssetSourceType.FILE);
      myActiveAsset.set(sourceType == AssetSourceType.CLIP_ART ? myClipartAssetButton.getAsset() : myFileBrowser.getAsset());
    });

    myListeners.listenAll(myWidthHasFocus, myHeightHasFocus).with(() -> {
      myGeneralBindings.release(myWidth);
      myGeneralBindings.release(myHeight);
      bindWidthAndHeight();
    });

    myGeneralBindings.bind(new TextProperty(myOpacityValueLabel), new FormatExpression("%d %%", myOpacityPercent));

    TextProperty widthText = new TextProperty(myWidthTextField);
    TextProperty heightText = new TextProperty(myHeightTextField);

    Receiver<VectorAsset.VectorDrawableInfo> drawableListener = drawableInfo -> {
      myAssetIsValid.set(drawableInfo.isValid());
      myAssetValidityState.set(drawableInfo.getValidityState());

      myGeneralBindings.release(myWidth);
      myGeneralBindings.release(myHeight);
      if (myWidthAdapter != null) {
        myGeneralBindings.release(myWidthAdapter);
        myWidthAdapter = null;
      }
      if (myHeightAdapter != null) {
        myGeneralBindings.release(myHeightAdapter);
        myHeightAdapter = null;
      }

      double width = drawableInfo.getOriginalWidth();
      double height = drawableInfo.getOriginalHeight();

      if (width > 0 && height > 0) {
        myWidth.set(width);
        myHeight.set(height);
        myAspectRatio = width / height;
        // Use fractional dimensions for drawables smaller than 100dp.
        int numDecimals = Math.max(roundToInt(Math.ceil(2 - Math.log10(Math.min(width, height)))), 0);
        myWidthAdapter = new StringToDoubleAdapterProperty(widthText, numDecimals);
        myHeightAdapter = new StringToDoubleAdapterProperty(heightText, numDecimals);
        bindWidthAndHeight();
        myWidthTextField.setEnabled(true);
        myHeightTextField.setEnabled(true);
      }
      else {
        myWidth.set(1.); // Set to a valid value.
        myHeight.set(1.);
        myWidthTextField.setText("");
        myHeightTextField.setText("");
        myWidthTextField.setEnabled(false);
        myHeightTextField.setEnabled(false);
      }

      renderPreviews();
    };

    myListeners.listenAndFire(myActiveAsset, () -> {
      myActiveAssetBindings.releaseAll();

      VectorAsset activeAsset = myActiveAsset.get();
      OptionalValueProperty<File> fileProperty = activeAsset.path();
      myActiveAssetBindings.bind(myOutputName, Expression.create(() -> {
        File file = fileProperty.getValueOrNull();
        if (file == null || !file.exists() || file.isDirectory()) {
          return DEFAULT_OUTPUT_NAME;
        }

        String name = FileUtil.getNameWithoutExtension(file).toLowerCase(Locale.getDefault());
        return FileResourceNameValidator.getValidResourceFileName(name);
      }, fileProperty));

      myListeners.release(drawableListener);
      ObjectProperty<VectorAsset.VectorDrawableInfo> vectorDrawableInfo = activeAsset.getVectorDrawableInfo();
      myListeners.listenAndFire(vectorDrawableInfo, drawableListener);

      myValidatorPanel.registerValidator(myOutputName, name -> Validator.Result.fromNullableMessage(myNameValidator.getErrorText(name)));
      myValidatorPanel.registerValidator(myAssetValidityState, validity -> validity);
      EnabledProperty widthEnabled = new EnabledProperty(myWidthTextField);
      ObservableValue<String> widthForValidation =
          Expression.create(() -> widthEnabled.get() ? widthText.get() : "24", widthText, widthEnabled);
      myValidatorPanel.registerValidator(widthForValidation, new SizeValidator("Width has to be a positive number"));
      EnabledProperty heightEnabled = new EnabledProperty(myHeightTextField);
      ObservableValue<String> heightForValidation =
          Expression.create(() -> heightEnabled.get() ? heightText.get() : "24", heightText, heightEnabled);
      myValidatorPanel.registerValidator(heightForValidation, new SizeValidator("Height has to be a positive number"));

      if (myAssetSourceType.get() == AssetSourceType.CLIP_ART) {
        myActiveAssetBindings.bind(activeAsset.color(), myColor);
      }
      myActiveAssetBindings.bind(activeAsset.opacityPercent(), myOpacityPercent);
      myActiveAssetBindings.bind(activeAsset.autoMirrored(), myAutoMirrored);
      myActiveAssetBindings.bind(activeAsset.outputWidth(), Expression.create(myWidth::get, myWidth));
      myActiveAssetBindings.bind(activeAsset.outputHeight(), Expression.create(myHeight::get, myHeight));

      myListeners.listenAll(myActiveAsset, activeAsset.outputWidth(), activeAsset.outputHeight(), activeAsset.color(),
                            activeAsset.opacityPercent(), activeAsset.autoMirrored())
          .with(this::renderPreviews);
    });

    myGeneralBindings.bind(myIconGenerator.sourceAsset(), new AsOptionalExpression<>(myActiveAsset));
    myGeneralBindings.bind(myIconGenerator.outputName(), myOutputName);

    PersistentStateUtil.load(this, getModel().getPersistentState().getChild(VECTOR_ASSET_STEP_PROPERTY));

    // Refresh the asset preview.
    renderPreviews();
  }

  private void bindWidthAndHeight() {
    if (myWidthAdapter != null && myHeightAdapter != null) {
      myGeneralBindings.bind(myWidthAdapter, myWidth);
      myGeneralBindings.bind(myHeightAdapter, myHeight);

      if (myWidthHasFocus.get()) {
        myGeneralBindings.bind(myWidth, myWidthAdapter);
        myGeneralBindings.bind(myHeight, Expression.create(() -> myWidth.get() / myAspectRatio, myWidth));
      }
      else if (myHeightHasFocus.get()) {
        myGeneralBindings.bind(myHeight, myHeightAdapter);
        myGeneralBindings.bind(myWidth, Expression.create(() -> myHeight.get() * myAspectRatio, myHeight));
      }
    }
  }

  @Override
  @NotNull
  protected Collection<? extends ModelWizardStep<?>> createDependentSteps() {
    return Collections.singletonList(new ConfirmGenerateIconsStep(getModel(), getModuleTemplates(myFacet, null)));
  }

  @Override
  @NotNull
  protected JComponent getComponent() {
    return myValidatorPanel;
  }

  @Override
  @NotNull
  protected ObservableBool canGoForward() {
    // Missing asset is treated as a warning by the validator panel, so it has to be checked separately.
    return myAssetIsValid.and(myValidatorPanel.hasErrors().not());
  }

  @Override
  protected void onProceeding() {
    getModel().setIconGenerator(myIconGenerator);
  }

  @Override
  public void onWizardFinished() {
    getModel().getPersistentState().setChild(VECTOR_ASSET_STEP_PROPERTY, getState());
  }

  @Override
  public void dispose() {
    myGeneralBindings.releaseAll();
    myActiveAssetBindings.releaseAll();
    myListeners.releaseAll();
  }

  @Override
  @NotNull
  public PersistentState getState() {
    PersistentState state = new PersistentState();
    state.set(OUTPUT_NAME_PROPERTY, myOutputName.get(), DEFAULT_OUTPUT_NAME);
    state.set(ASSET_SOURCE_TYPE_PROPERTY, myAssetSourceType.get(), DEFAULT_ASSET_SOURCE_TYPE);
    state.setChild(CLIPART_ASSET_PROPERTY, myClipartAssetButton.getState());
    File file = myFileBrowser.getAsset().path().getValueOrNull();
    state.set(SOURCE_FILE_PROPERTY, file == null ? getProjectPath() : file.getPath(), getProjectPath());
    state.set(COLOR_PROPERTY, myColor.getValueOrNull(), DEFAULT_COLOR);
    state.set(OPACITY_PERCENT_PROPERTY, myOpacityPercent.get(), 100);
    state.set(AUTO_MIRRORED_PROPERTY, myAutoMirrored.get(), false);
    return state;
  }

  @Override
  public void loadState(@NotNull PersistentState state) {
    // Load persistent state of controls after dust settles.
    ApplicationManager.getApplication().invokeLater(
      () -> {
        String name = state.get(OUTPUT_NAME_PROPERTY);
        if (name != null) {
          myOutputName.set(name);
        }
        myAssetSourceType.set(state.get(ASSET_SOURCE_TYPE_PROPERTY, DEFAULT_ASSET_SOURCE_TYPE));
        PersistentStateUtil.load(myClipartAssetButton, state.getChild(CLIPART_ASSET_PROPERTY));
        String path = state.get(SOURCE_FILE_PROPERTY, getProjectPath());
        myFileBrowser.getAsset().path().setValue(new File(path));
        myColor.setValue(state.get(COLOR_PROPERTY, DEFAULT_COLOR));
        myOpacityPercent.set(state.get(OPACITY_PERCENT_PROPERTY, 100));
        myAutoMirrored.set(state.get(AUTO_MIRRORED_PROPERTY, false));
      },
      ModalityState.any());
  }

  @SystemIndependent
  @NotNull
  private String getProjectPath() {
    String projectPath = myFacet.getModule().getProject().getBasePath();
    assert projectPath != null;
    return projectPath;
  }

  private void renderPreviews() {
    // This method is often called as the result of a UI property changing which may also cause
    // some other properties to change. Due to asynchronous nature of some property changes, it
    // is necessary to use two invokeLater calls to make sure that everything settles before
    // icons generation is attempted.
    VectorPreviewUpdater previewUpdater = new VectorPreviewUpdater();
    invokeLater(() -> invokeLater(previewUpdater::enqueueUpdate));
  }

  /**
   * Executes the given runnable asynchronously on the AWT event dispatching thread.
   */
  private void invokeLater(@NotNull Runnable runnable) {
    ApplicationManager.getApplication().invokeLater(runnable, ModalityState.any(), o -> Disposer.isDisposed(this));
  }

  /**
   * Parsing and generating a vector preview is not always a lightweight operation, and if we try to
   * do it synchronously, especially with a larger vector file, it can stutter the UI. So instead, we
   * enqueue the request to run on a background thread. If several requests are made in a row while
   * an existing worker is still in progress, they will only generate a single update, run as soon
   * as the current update finishes.
   * <p>
   * Call {@link #enqueueUpdate()} in order to kick-start the generation of a new preview.
   */
  private final class VectorPreviewUpdater {
    @Nullable private SwingWorker<VectorAsset.Preview> myCurrentWorker;
    @Nullable private SwingWorker<VectorAsset.Preview> myEnqueuedWorker;

    /**
     * Updates the image preview asynchronously. If an update is already in process, then a new update
     * scheduled to run after completion of the current one.
     */
    @UiThread
    public void enqueueUpdate() {
      ApplicationManager.getApplication().assertIsDispatchThread();

      int previewWidth = myImagePreview.getWidth();
      if (previewWidth <= 0) {
        // Delay preview update until its desired size is known.
        invokeLater(this::enqueueUpdate);
        return;
      }

      if (myCurrentWorker == null) {
        myCurrentWorker = new Worker(previewWidth);
        myCurrentWorker.start();
      }
      else if (myEnqueuedWorker == null) {
        myEnqueuedWorker = new Worker(previewWidth);
      }
    }

    private class Worker extends SwingWorker<VectorAsset.Preview> {
      @Nullable VectorAsset.Preview myPreview;
      @NotNull final VectorAsset myAsset;
      @Nullable final File myAssetFile;
      @NotNull final VectorAsset.VectorDrawableInfo myVectorDrawableInfo;
      @NotNull final VdOverrideInfo myOverrideInfo;
      private final int myPreviewWidth;

      @UiThread
      Worker(int previewWidth) {
        ApplicationManager.getApplication().assertIsDispatchThread();
        myPreviewWidth = previewWidth;
        myAsset = myActiveAsset.get();
        myAssetFile = myAsset.path().getValueOrNull();
        myVectorDrawableInfo = myAsset.getVectorDrawableInfo().get();
        myOverrideInfo = myAsset.createOverrideInfo();
      }

      @WorkerThread
      @Override
      @NotNull
      public VectorAsset.Preview construct() {
        try {
          myPreview = VectorAsset.generatePreview(myVectorDrawableInfo, myPreviewWidth, myOverrideInfo);
        } catch (Throwable t) {
          Logger.getInstance(getClass()).error(t);
          String message = myAssetFile == null ?
                           "Internal error generating preview" :
                           "Internal error generating preview for " + myAssetFile.getName();
          myPreview = new VectorAsset.Preview(message);
        }
        return myPreview;
      }

      @UiThread
      @Override
      public void finished() {
        ApplicationManager.getApplication().assertIsDispatchThread();
        assert myPreview != null;

        // Update the preview image if it corresponds to the current state.
        if (myAsset.equals(myActiveAsset.get()) &&
            myAsset.isCurrentFile(myAssetFile) &&
            myVectorDrawableInfo.equals(myAsset.getVectorDrawableInfo().get()) &&
            myOverrideInfo.equals(myAsset.createOverrideInfo())) {
          myAssetValidityState.set(myPreview.getValidityState());
          BufferedImage image = myPreview.getImage();
          myImagePreview.setIcon(image == null ? null : new ImageIcon(image));
        }

        myCurrentWorker = null;
        if (myEnqueuedWorker != null) {
          myCurrentWorker = myEnqueuedWorker;
          myEnqueuedWorker = null;
          myCurrentWorker.start();
        }
      }
    }
  }

  private static class SizeValidator implements Validator<String> {
    private static final NumberFormat myFormat = NumberFormat.getNumberInstance();
    private final Result myInvalidResult;

    SizeValidator(@NotNull String message) {
      myInvalidResult = new Result(Severity.ERROR, message);
    }

    @Override
    @NotNull
    public Result validate(@NotNull String value) {
      ParsePosition pos = new ParsePosition(0);
      Number number = myFormat.parse(value, pos);
      return number != null && pos.getIndex() == value.length() && number.doubleValue() > 0 ? Result.OK : myInvalidResult;
    }
  }

  private enum AssetSourceType {
    CLIP_ART,
    FILE,
  }
}