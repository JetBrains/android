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

import com.android.tools.idea.npw.assetstudio.assets.VectorAsset;
import com.android.tools.idea.npw.assetstudio.icon.AndroidVectorIconGenerator;
import com.android.tools.idea.npw.assetstudio.ui.VectorAssetBrowser;
import com.android.tools.idea.npw.assetstudio.ui.VectorIconButton;
import com.android.tools.idea.ui.VectorImageComponent;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.InvalidationListener;
import com.android.tools.idea.ui.properties.ListenerManager;
import com.android.tools.idea.ui.properties.ObservableValue;
import com.android.tools.idea.ui.properties.adapters.StringToIntAdapterProperty;
import com.android.tools.idea.ui.properties.core.*;
import com.android.tools.idea.ui.properties.expressions.Expression;
import com.android.tools.idea.ui.properties.expressions.optional.AsOptionalExpression;
import com.android.tools.idea.ui.properties.expressions.string.FormatExpression;
import com.android.tools.idea.ui.properties.swing.EnabledProperty;
import com.android.tools.idea.ui.properties.swing.SelectedProperty;
import com.android.tools.idea.ui.properties.swing.SliderValueProperty;
import com.android.tools.idea.ui.properties.swing.TextProperty;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Consumer;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

/**
 * Step for generating Android icons from some vector source.
 */
@SuppressWarnings("UseJBColor") // Colors are used for the graphics generator, not the plugin UI
public final class NewVectorAssetStep extends ModelWizardStep<GenerateIconsModel> {

  // TODO: Add ModelWizard support for a help link
  private final static String HELP_LINK = "http://developer.android.com/tools/help/vector-asset-studio.html";
  private static final int DEFAULT_MATERIAL_ICON_SIZE = 24;
  private static final String ICON_PREFIX = "ic_";

  private final AndroidVectorIconGenerator myIconGenerator = new AndroidVectorIconGenerator();
  private final ObjectProperty<VectorAsset> myActiveAsset;
  private final OptionalProperty<Dimension> myOriginalSize = new OptionalValueProperty<>();

  private final BoolProperty isValidAsset = new BoolValueProperty();
  private final SvgPreviewUpdater myPreviewUpdater = new SvgPreviewUpdater();

  private final BindingsManager myGeneralBindings = new BindingsManager();
  private final BindingsManager myActiveAssetBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();

  private JPanel myRootPanel;
  private VectorImageComponent myImagePreview;
  private JLabel myImageFileLabel;
  private JTextField myOutputNameField;
  private JPanel myErrorPanel;
  private JPanel mySvgBrowserPanel;
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
  private VectorAssetBrowser mySvgBrowser;
  private VectorIconButton myIconButton;
  private JBScrollPane myErrorsScrollPane;
  private JTextArea myErrorsTextArea;

  public NewVectorAssetStep(@NotNull GenerateIconsModel model) {
    super(model, "Configure Vector Asset");

    // Start with the icon radio button selected, because icons are easy to browse and play around
    // with right away.
    myMaterialIconRadioButton.setSelected(true);
    myActiveAsset = new ObjectValueProperty<>(myIconButton.getAsset());
  }

  @NotNull
  @Override
  protected Collection<? extends ModelWizardStep> createDependentSteps() {
    return Collections.singletonList(new ConfirmGenerateIconsStep(getModel()));
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    final Runnable onAssetModified = myPreviewUpdater::enqueueUpdate;

    SelectedProperty iconSelected = new SelectedProperty(myMaterialIconRadioButton);
    myListeners.listenAndFire(iconSelected, new Consumer<Boolean>() {
      @Override
      public void consume(Boolean isIconActive) {
        myIconPickerPanel.setVisible(isIconActive);
        mySvgBrowserPanel.setVisible(!isIconActive);
        myActiveAsset.set(isIconActive ? myIconButton.getAsset() : mySvgBrowser.getAsset());
      }
    });
    ActionListener assetListener = actionEvent -> onAssetModified.run();
    myIconButton.addAssetListener(assetListener);
    mySvgBrowser.addAssetListener(assetListener);
    Disposer.register(this, myIconButton);
    Disposer.register(this, mySvgBrowser);

    final BoolProperty overrideSize = new SelectedProperty(myOverrideSizeCheckBox);
    final IntProperty width = new IntValueProperty();
    final IntProperty height = new IntValueProperty();
    myGeneralBindings.bindTwoWay(new StringToIntAdapterProperty(new TextProperty(myWidthTextField)), width);
    myGeneralBindings.bindTwoWay(new StringToIntAdapterProperty(new TextProperty(myHeightTextField)), height);
    myGeneralBindings.bind(new EnabledProperty(myWidthTextField), overrideSize);
    myGeneralBindings.bind(new EnabledProperty(myHeightTextField), overrideSize);
    myListeners.listenAll(overrideSize, myOriginalSize).withAndFire(new Runnable() {
      @Override
      public void run() {
        if (!overrideSize.get() || !myOriginalSize.get().isPresent()) {
          width.set(DEFAULT_MATERIAL_ICON_SIZE);
          height.set(DEFAULT_MATERIAL_ICON_SIZE);
        }
        else {
          width.set(myOriginalSize.getValue().width);
          height.set(myOriginalSize.getValue().height);
        }
      }
    });

    final IntProperty opacityValue = new SliderValueProperty(myOpacitySlider);
    myGeneralBindings.bind(new TextProperty(myOpacityValueLabel), new FormatExpression("%d %%", opacityValue));

    final BoolProperty autoMirrored = new SelectedProperty(myEnableAutoMirroredCheckBox);

    myListeners.listenAll(myActiveAsset, overrideSize, width, height, opacityValue, autoMirrored).with(onAssetModified);

    final StringProperty name = new TextProperty(myOutputNameField);
    myListeners.listenAndFire(myActiveAsset, new InvalidationListener() {
      @Override
      public void onInvalidated(@NotNull ObservableValue<?> sender) {
        myActiveAssetBindings.releaseAll();

        myActiveAssetBindings.bind(name, new Expression<String>(myActiveAsset.get().path()) {
          @NotNull
          @Override
          public String get() {
            File path = myActiveAsset.get().path().get();
            if (path.exists() && !path.isDirectory()) {
              String name = FileUtil.getNameWithoutExtension(path).toLowerCase(Locale.getDefault());
              if (!name.startsWith(ICON_PREFIX)) {
                name = ICON_PREFIX + AndroidResourceUtil.getValidResourceFileName(name);
              }
              return AndroidResourceUtil.getValidResourceFileName(name);
            }
            else {
              return "ic_vector_name";
            }
          }
        });
        myActiveAssetBindings.bind(myActiveAsset.get().opacity(), opacityValue);
        myActiveAssetBindings.bind(myActiveAsset.get().autoMirrored(), autoMirrored);
        myActiveAssetBindings.bind(myActiveAsset.get().outputWidth(), width);
        myActiveAssetBindings.bind(myActiveAsset.get().outputHeight(), height);
      }
    });

    // Refresh the asset preview, but fire using invokeLater, as this lets the UI lay itself out,
    // which should happen before the "generate preview" logic runs.
    ApplicationManager.getApplication().invokeLater(onAssetModified, ModalityState.any());

    // Cast VectorAsset -> BaseAsset
    myGeneralBindings.bind(myIconGenerator.sourceAsset(), new AsOptionalExpression<>(myActiveAsset));
    myGeneralBindings.bind(myIconGenerator.name(), name);
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return isValidAsset;
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
  }

  /**
   * Parsing and generating a preview SVG is not always a lightweight operation, and if we try to
   * do it synchronously, especially with a larger SVG file, it can stutter the UI. So instead, we
   * enqueue the request to run on a background thread. If several requests are made in a row while
   * an existing worker is still in progress, they will only generate a single update, run as soon
   * as the current update finishes.
   *
   * Call {@link #enqueueUpdate()} in order to kickstart the generation of a new preview.
   */
  private final class SvgPreviewUpdater {

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
        protected Void doInBackground() throws Exception {
          myParseResult = myActiveAsset.get().parseSvg(myImagePreview.getWidth());
          return null;
        }

        @Override
        protected void done() {
          assert myParseResult != null;
          isValidAsset.set(myParseResult.isValid());
          if (myParseResult.isValid()) {
            myImagePreview.setIcon(new ImageIcon(myParseResult.getImage()));
            myOriginalSize.setValue(new Dimension(myParseResult.getOriginalWidth(), myParseResult.getOriginalHeight()));
          }
          else {
            myImagePreview.setIcon(null);
            myOriginalSize.clear();
          }

          myErrorPanel.setVisible(!myParseResult.getErrors().isEmpty());
          myErrorsTextArea.setText(myParseResult.getErrors());
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              myErrorsScrollPane.getVerticalScrollBar().setValue(0);
            }
          }, ModalityState.any());

          myCurrentWorker = null;
          if (myEnqueuedWorker != null) {
            myCurrentWorker = myEnqueuedWorker;
            myEnqueuedWorker = null;
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                myCurrentWorker.execute();
              }
            }, ModalityState.any());
          }
        }
      };
    }
  }
}