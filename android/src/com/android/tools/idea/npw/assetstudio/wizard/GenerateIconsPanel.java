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

import com.android.tools.adtui.ImageComponent;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.npw.assetstudio.AssetStudioUtils;
import com.android.tools.idea.npw.assetstudio.GraphicGenerator;
import com.android.tools.idea.npw.assetstudio.NotificationIconGenerator;
import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.npw.assetstudio.icon.AndroidIconGenerator;
import com.android.tools.idea.npw.assetstudio.icon.AndroidIconType;
import com.android.tools.idea.npw.assetstudio.icon.CategoryIconMap;
import com.android.tools.idea.npw.assetstudio.ui.ConfigureIconPanel;
import com.android.tools.idea.npw.assetstudio.ui.PreviewIconsPanel;
import com.android.tools.idea.npw.project.AndroidProjectPaths;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.ObservableValue;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.android.tools.idea.observable.expressions.value.AsValueExpression;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;

/**
 * A panel which presents a UI for selecting some source asset and converting it to a target set of
 * Android Icons. See {@link AndroidIconType} for the types of icons this can generate.
 *
 * Before generating icons, you should first check {@link #hasErrors()} to make sure there won't be
 * any errors in the generation process.
 *
 * This is a Swing port of the various icon generators provided by the
 * <a href="https://romannurik.github.io/AndroidAssetStudio/index.html">Asset Studio</a>
 * web application.
 */
public final class GenerateIconsPanel extends JPanel implements Disposable {
  private static final int ASSET_PREVIEW_HEIGHT = 96;

  private final AndroidProjectPaths myDefaultPaths;
  private final ValidatorPanel myValidatorPanel;

  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();

  private final Multimap<AndroidIconType, PreviewIconsPanel> myOutputPreviewPanels;
  private final ObservableValue<AndroidIconType> myOutputIconType;
  private final StringProperty myOutputName = new StringValueProperty();

  private JPanel myRootPanel;
  private JPanel myTopRightPanel;
  private ImageComponent mySourceAssetImage;
  private JPanel myOutputPreviewPanel;
  private JPanel myTopPanel;
  private JComboBox<AndroidIconType> myIconTypeCombo;
  private JPanel myBottomPanel;
  private JPanel myConfigureIconPanels;
  private JPanel mySourceAssetPanel;
  private JPanel mySourceAssetMaxWidthPanel;

  @NotNull private AndroidProjectPaths myPaths;
  @Nullable private BufferedImage myEnqueuedImageToProcess;

  /**
   * Create a panel which can generate Android icons. The supported types passed in will be
   * presented to the user in a pulldown menu (unless there's only one supported type). If no
   * supported types are passed in, then all types will be supported by default.
   */
  public GenerateIconsPanel(@NotNull Disposable disposableParent,
                            @NotNull AndroidProjectPaths defaultPaths,
                            int minSdkVersion,
                            @NotNull AndroidIconType... supportedTypes) {
    super(new BorderLayout());

    myDefaultPaths = defaultPaths;
    myPaths = myDefaultPaths;

    if (supportedTypes.length == 0) {
      supportedTypes = AndroidIconType.values();
    }

    DefaultComboBoxModel<AndroidIconType> supportedTypesModel = new DefaultComboBoxModel<>(supportedTypes);
    myIconTypeCombo.setModel(supportedTypesModel);
    myIconTypeCombo.setVisible(supportedTypes.length > 1);

    assert myConfigureIconPanels.getLayout() instanceof CardLayout;
    for (AndroidIconType iconType : supportedTypes) {
      myConfigureIconPanels.add(new ConfigureIconPanel(this, iconType, minSdkVersion), iconType.toString());
    }

    mySourceAssetMaxWidthPanel.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        updateSourceAssetPreview(getTrimmedAndPaddedImage(getActiveIconPanel().getAsset()));
      }
    });

    // @formatter:off
    ImmutableMultimap.Builder<AndroidIconType, PreviewIconsPanel> previewPanelBuilder = ImmutableMultimap.builder();
    previewPanelBuilder.putAll(AndroidIconType.ACTIONBAR, new PreviewIconsPanel("", PreviewIconsPanel.Theme.TRANSPARENT));
    previewPanelBuilder.putAll(AndroidIconType.LAUNCHER, new PreviewIconsPanel("", PreviewIconsPanel.Theme.TRANSPARENT));

    if (minSdkVersion < 11) {
      previewPanelBuilder.putAll(AndroidIconType.NOTIFICATION,
                                 new PreviewIconsPanel("API 11+", PreviewIconsPanel.Theme.DARK,
                                                       new CategoryIconMap.NotificationFilter(NotificationIconGenerator.Version.V11)),
                                 new PreviewIconsPanel("API 9+", PreviewIconsPanel.Theme.LIGHT,
                                                       new CategoryIconMap.NotificationFilter(NotificationIconGenerator.Version.V9)));
      if (minSdkVersion < 9) {
        previewPanelBuilder.put(AndroidIconType.NOTIFICATION,
                                new PreviewIconsPanel("Older APIs", PreviewIconsPanel.Theme.GRAY,
                                                      new CategoryIconMap.NotificationFilter(NotificationIconGenerator.Version.OLDER)));
      }
    }
    else {
      // need to not have filter for minSdk < 11 as NotificationIconGenerator.generate only put the display name for minSdk < 11
      // {@link NotificationIconGenerator#generate(String, Map, GraphicGeneratorContext, GraphicGenerator.Options, String)}
      previewPanelBuilder.put(AndroidIconType.NOTIFICATION, new PreviewIconsPanel("", PreviewIconsPanel.Theme.DARK));
    }

    myOutputPreviewPanels = previewPanelBuilder.build();
    // @formatter:on

    for (PreviewIconsPanel iconsPanel : myOutputPreviewPanels.values()) {
      myOutputPreviewPanel.add(iconsPanel);
    }

    myOutputIconType = new AsValueExpression<>(new SelectedItemProperty<>(myIconTypeCombo));

    initializeListenersAndBindings();

    myValidatorPanel = new ValidatorPanel(this, myRootPanel);
    initializeValidators();

    Disposer.register(disposableParent, this);
    Disposer.register(this, myValidatorPanel);
    add(myValidatorPanel);
  }

  private void initializeListenersAndBindings() {
    ActionListener onAssetModified = actionEvent -> renderIconPreviews();

    for (Component component : myConfigureIconPanels.getComponents()) {
      ((ConfigureIconPanel)component).addAssetListener(onAssetModified);
    }

    myListeners.receiveAndFire(myOutputIconType, iconType -> {
      ((CardLayout)myConfigureIconPanels.getLayout()).show(myConfigureIconPanels, iconType.toString());

      ConfigureIconPanel iconPanel = getActiveIconPanel();
      myBindings.bind(myOutputName, iconPanel.outputName());

      for (PreviewIconsPanel previewPanel : myOutputPreviewPanels.values()) {
        previewPanel.setVisible(false);
      }
      for (PreviewIconsPanel previewPanel : myOutputPreviewPanels.get(iconType)) {
        previewPanel.setVisible(true);
      }

      renderIconPreviews();
    });
  }

  @NotNull
  private ConfigureIconPanel getActiveIconPanel() {
    for (Component component : myConfigureIconPanels.getComponents()) {
      if (component.isVisible()) {
        return (ConfigureIconPanel)component;
      }
    }

    throw new IllegalStateException("GenerateIconsPanel configured incorrectly. Please report this error.");
  }

  private void initializeValidators() {
    myValidatorPanel.registerValidator(myOutputName, outputName -> {
      String trimmedName = outputName.trim();
      if (trimmedName.isEmpty()) {
        return new Validator.Result(Validator.Severity.ERROR, "Icon name must be set");
      }
      else if (iconExists()) {
        return new Validator.Result(Validator.Severity.WARNING, "An icon with the same name already exists and will be overwritten.");
      }
      else {
        return Validator.Result.OK;
      }
    });
  }

  /**
   * Set the target project paths that this panel should use when generating assets. If not set,
   * this panel will attempt to use reasonable defaults for the project.
   */
  public void setProjectPaths(@Nullable AndroidProjectPaths projectPaths) {
    myPaths = (projectPaths != null) ? projectPaths : myDefaultPaths;
  }

  /**
   * Set the output name for the icon type currently being edited. This is exposed as some UIs may wish
   * to set this explicitly instead of relying on defaults.
   */
  public void setOutputName(@NotNull String name) {
    getActiveIconPanel().outputName().set(name);
  }

  /**
   * Return an icon generator which will create Android icons using the panel's current settings.
   */
  @NotNull
  public AndroidIconGenerator getIconGenerator() {
    return getActiveIconPanel().getIconGenerator();
  }

  /**
   * A boolean property which will be true if validation logic catches any problems with any of the
   * current icon settings, particularly the output name / path. You should probably not generate
   * icons if there are any errors.
   */
  @NotNull
  public ObservableBool hasErrors() {
    return myValidatorPanel.hasErrors();
  }

  private boolean iconExists() {
    Map<File, BufferedImage> pathImageMap = getIconGenerator().generateIntoFileMap(myPaths);
    for (File path : pathImageMap.keySet()) {
      if (path.exists()) {
        return true;
      }
    }

    return false;
  }

  private void renderIconPreviews() {
    // This method is often called as the result of a UI property changing which may also cause
    // some other properties to change. Invoke its logic later just to make sure everything gets
    // a chance to settle first.
    ApplicationManager.getApplication().invokeLater(
      () -> {
        BaseAsset asset = getActiveIconPanel().getAsset();
        // TODO: Move this call off the UI thread.
        BufferedImage image = getTrimmedAndPaddedImage(asset);
        updateSourceAssetPreview(image);
        enqueueGenerateNotificationIcons(image);
      },
      ModalityState.any());
  }

  @NotNull
  private static BufferedImage getTrimmedAndPaddedImage(@NotNull BaseAsset asset) {
    BufferedImage image = GraphicGenerator.getTrimmedAndPaddedImage(asset.toImage(), asset.trimmed().get(), asset.paddingPercent().get());
    if (image == null) {
      image = AssetStudioUtils.createDummyImage();
    }
    return image;
  }

  private void updateSourceAssetPreview(@NotNull BufferedImage assetImage) {
    // Preserve aspect ratio as much as we can (up to a reasonable maximum width)
    int myMaxAssetPreviewWidth = mySourceAssetMaxWidthPanel.getWidth();

    double aspectRatio = 1.0;
    if (assetImage.getHeight() > 0) {
      aspectRatio = (double)assetImage.getWidth() / assetImage.getHeight();
    }
    int finalWidth = (int)Math.round(ASSET_PREVIEW_HEIGHT * aspectRatio);
    finalWidth = Math.min(finalWidth, myMaxAssetPreviewWidth);
    Dimension d = new Dimension(finalWidth, ASSET_PREVIEW_HEIGHT);

    mySourceAssetPanel.setPreferredSize(d);
    mySourceAssetImage.setIcon(IconUtil.createImageIcon((Image)assetImage));
  }

  /**
   * Generating notification icons is not a lightweight process, and if we try to do it
   * synchronously, it stutters the UI. So instead we enqueue the request to run on a background
   * thread. If several requests are made in a row while an existing worker is still in progress,
   * only the most recently added will be handled, whenever the worker finishes.
   */
  private void enqueueGenerateNotificationIcons(@NotNull BufferedImage assetImage) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    boolean currentlyWorking = myEnqueuedImageToProcess != null;
    myEnqueuedImageToProcess = assetImage;

    if (currentlyWorking) {
      return;
    }

    SwingWorker<Void, Void> generateIconsWorker = new SwingWorker<Void, Void>() {
      @Nullable private CategoryIconMap myCategoryIconMap;

      @Override
      protected Void doInBackground() throws Exception {
        if (getIconGenerator().sourceAsset().get().isPresent()) {
          myCategoryIconMap = getIconGenerator().generateIntoMemory();
        }
        return null;
      }

      @Override
      protected void done() {
        if (myCategoryIconMap != null) {
          for (PreviewIconsPanel generatedIconsPanel : myOutputPreviewPanels.get(myOutputIconType.get())) {
            generatedIconsPanel.showPreviewImages(myCategoryIconMap);
          }
          myCategoryIconMap = null;
        }

        // At this point, we've finished preparing the previews. Let's see if another request to
        // render more previews was added while we were working, and if so, start on it right away.
        BufferedImage nextImage = null;
        if (myEnqueuedImageToProcess != assetImage) {
          nextImage = myEnqueuedImageToProcess;
        }
        myEnqueuedImageToProcess = null;

        if (nextImage != null) {
          final BufferedImage finalNextImage = nextImage;
          ApplicationManager.getApplication().invokeLater(() -> enqueueGenerateNotificationIcons(finalNextImage),
                                                          ModalityState.any());
        }
      }
    };

    generateIconsWorker.execute();
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }
}
