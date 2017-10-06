/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.npw.assetstudio.icon;

import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.rendering.api.LayoutlibCallback;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.resources.Density;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.concurrent.FutureUtils;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.npw.assetstudio.*;
import com.android.tools.idea.npw.assetstudio.AdaptiveIconGenerator.AdaptiveIconOptions;
import com.android.tools.idea.npw.assetstudio.AdaptiveIconGenerator.ImageAssetSnapshot;
import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.npw.assetstudio.assets.ImageAsset;
import com.android.tools.idea.npw.assetstudio.assets.TextAsset;
import com.android.tools.idea.npw.assetstudio.assets.VectorAsset;
import com.android.tools.idea.observable.core.*;
import com.android.tools.idea.rendering.*;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static com.android.tools.idea.npw.assetstudio.AdaptiveIconGenerator.IMAGE_SIZE_SAFE_ZONE_DP;
import static com.android.tools.idea.npw.assetstudio.AdaptiveIconGenerator.SIZE_FULL_BLEED_DP;

/**
 * Settings when generating a launcher icon.
 *
 * Defaults from https://romannurik.github.io/AndroidAssetStudio/icons-launcher.html
 */
@SuppressWarnings("UseJBColor") // We are generating colors in our icons, no need for JBColor here
public final class AndroidAdaptiveIconGenerator extends AndroidIconGenerator {
  private final BoolProperty myUseForegroundColor = new BoolValueProperty(true);
  private final ObjectProperty<Color> myForegroundColor = new ObjectValueProperty<>(Color.BLACK);
  private final ObjectProperty<Color> myBackgroundColor = new ObjectValueProperty<>(new Color(0x26A69A));
  private final BoolProperty myGenerateLegacyIcon = new BoolValueProperty(true);
  private final BoolProperty myGenerateRoundIcon = new BoolValueProperty(true);
  private final BoolProperty myGenerateWebIcon = new BoolValueProperty(true);
  private final ObjectProperty<GraphicGenerator.Shape> myLegacyIconShape = new ObjectValueProperty<>(GraphicGenerator.Shape.SQUARE);
  private final ObjectProperty<GraphicGenerator.Shape> myWebIconShape = new ObjectValueProperty<>(GraphicGenerator.Shape.SQUARE);
  private final BoolProperty myShowGrid = new BoolValueProperty();
  private final BoolProperty myShowSafeZone = new BoolValueProperty(true);
  private final ObjectValueProperty<Density> myPreviewDensity = new ObjectValueProperty<>(Density.XHIGH);
  private final OptionalProperty<ImageAsset> myBackgroundImageAsset = new OptionalValueProperty<>();
  private final StringProperty myForegroundLayerName = new StringValueProperty();
  private final StringProperty myBackgroundLayerName = new StringValueProperty();

  /**
   * Initializes the icon generator. Every icon generator has to be disposed by calling {@link #dispose()}.
   *
   * @param facet the Android facet
   * @param minSdkVersion the minimal supported Android SDK version
   */
  public AndroidAdaptiveIconGenerator(@NotNull AndroidFacet facet, int minSdkVersion) {
    super(minSdkVersion, new AdaptiveIconGenerator(), new GraphicGeneratorContext(40, new MyDrawableRenderer(facet)));
  }

  /**
   * Whether to use the foreground color. When using images as the source asset for our icons,
   * you shouldn't apply the foreground color, which would paint over it and obscure the image.
   */
  @NotNull
  public BoolProperty useForegroundColor() {
    return myUseForegroundColor;
  }

  /**
   * A color for rendering the foreground icon.
   */
  @NotNull
  public ObjectProperty<Color> foregroundColor() {
    return myForegroundColor;
  }

  /**
   * A color for rendering the background shape.
   */
  @NotNull
  public ObjectProperty<Color> backgroundColor() {
    return myBackgroundColor;
  }

  /**
   * If {@code true}, generate the "Legacy" icon (API 24 and earlier)
   */
  @NotNull
  public BoolProperty generateLegacyIcon() {
    return myGenerateLegacyIcon;
  }

  /**
   * If {@code true}, generate the "Round" icon (API 25)
   */
  @NotNull
  public BoolProperty generateRoundIcon() {
    return myGenerateRoundIcon;
  }

  /**
   * If {@code true}, generate the "Web" icon for PlayStore
   */
  @NotNull
  public BoolProperty generateWebIcon() {
    return myGenerateWebIcon;
  }

  /**
   * A shape which will be used as the "Legacy" icon's backdrop.
   */
  @NotNull
  public ObjectProperty<GraphicGenerator.Shape> legacyIconShape() {
    return myLegacyIconShape;
  }

  /**
   * A shape which will be used as the "Web" icon's backdrop.
   */
  @NotNull
  public ObjectProperty<GraphicGenerator.Shape> webIconShape() {
    return myWebIconShape;
  }

  @NotNull
  public OptionalProperty<ImageAsset> backgroundImageAsset() {
    return myBackgroundImageAsset;
  }

  @NotNull
  public BoolProperty showGrid() {
    return myShowGrid;
  }

  @NotNull
  public BoolProperty showSafeZone() {
    return myShowSafeZone;
  }

  @NotNull
  public ObjectValueProperty<Density> previewDensity() {
    return myPreviewDensity;
  }

  @NotNull
  public StringProperty foregroundLayerName() {
    return myForegroundLayerName;
  }

  @NotNull
  public StringProperty backgroundLayerName() {
    return myBackgroundLayerName;
  }

  @Override
  @NotNull
  public GraphicGenerator.Options createOptions(boolean forPreview) {
    AdaptiveIconOptions options = new AdaptiveIconOptions();
    options.generateOutputIcons = !forPreview;
    options.generatePreviewIcons = forPreview;

    options.minSdk = getMinSdkVersion();
    options.useForegroundColor = myUseForegroundColor.get();
    options.foregroundColor = myForegroundColor.get().getRGB();
    // Set foreground image.
    BaseAsset foregroundAsset = sourceAsset().getValueOrNull();
    if (foregroundAsset != null) {
      double scaleFactor = foregroundAsset.scalingPercent().get() / 100.;
      if (foregroundAsset instanceof VectorAsset) {
        scaleFactor *= 0.58;  // Scale correction for clip art to more or less fit into the safe zone.
      }
      else if (foregroundAsset instanceof TextAsset) {
        scaleFactor *= 0.46;  // Scale correction for text to more or less fit into the safe zone.
      }
      else if (foregroundAsset.trimmed().get()) {
        // Scale correction for images to fit into the safe zone.
        // Finding the smallest circle containing the image is not trivial (see https://en.wikipedia.org/wiki/Smallest-circle_problem).
        // For simplicity we treat the safe zone as a square.
        scaleFactor *= IMAGE_SIZE_SAFE_ZONE_DP.getWidth() / SIZE_FULL_BLEED_DP.getWidth();
      }
      options.foregroundImage = new ImageAssetSnapshot(foregroundAsset, scaleFactor, getGraphicGeneratorContext());
    }
    // Set background image.
    ImageAsset backgroundAsset = myBackgroundImageAsset.getValueOrNull();
    if (backgroundAsset != null) {
      double scaleFactor = backgroundAsset.scalingPercent().get() / 100.;
      options.backgroundImage = new ImageAssetSnapshot(backgroundAsset, scaleFactor, getGraphicGeneratorContext());
    }

    options.backgroundColor = myBackgroundColor.get().getRGB();
    options.showGrid = myShowGrid.get();
    options.showSafeZone = myShowSafeZone.get();
    options.previewDensity = myPreviewDensity.get();
    options.foregroundLayerName = myForegroundLayerName.get();
    options.backgroundLayerName = myBackgroundLayerName.get();
    options.generateLegacyIcon = myGenerateLegacyIcon.get();
    options.legacyIconShape = myLegacyIconShape.get();
    options.webIconShape = myWebIconShape.get();
    options.generateRoundIcon = myGenerateRoundIcon.get();
    options.generateWebIcon = myGenerateWebIcon.get();
    return options;
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(AndroidAdaptiveIconGenerator.class);
  }

  /**
   * Creates a PsiFile with the given name and contents corresponding to the given language without storing it on disk.
   *
   * @param project the project to associate the file with
   * @param filename path relative to a source root
   * @param fileType the type of the file
   * @param contents the content of the file
   * @return the created ephemeral file
   */
  @NotNull
  private static PsiFile createEphemeralPsiFile(@NotNull Project project, @NotNull String filename, @NotNull LanguageFileType fileType,
                                                @NotNull String contents) {
    PsiManager psiManager = PsiManager.getInstance(project);
    VirtualFile virtualFile = new LightVirtualFile(filename, fileType, contents);
    SingleRootFileViewProvider viewProvider = new SingleRootFileViewProvider(psiManager, virtualFile);
    PsiFile psiFile = viewProvider.getPsi(fileType.getLanguage());
    if (psiFile == null) {
      throw new IllegalArgumentException("Unsupported language: " + fileType.getLanguage().getDisplayName());
    }
    return psiFile;
  }

  private static class MyLayoutPullParserFactory implements ILayoutPullParserFactory {
    @NotNull private final ConcurrentMap<File, String> myFileContent = new ConcurrentHashMap<>();
    @NotNull private final Project myProject;
    @NotNull private final RenderLogger myLogger;

    public MyLayoutPullParserFactory(@NotNull Project project, @NotNull RenderLogger logger) {
      myProject = project;
      myLogger = logger;
    }

    @Override
    @Nullable
    public ILayoutPullParser create(@NotNull File file, @NotNull LayoutlibCallback layoutlibCallback) {
      String content = myFileContent.remove(file); // File contents is removed upon use to avoid leaking memory.
      if (content == null) {
        return null;
      }

      XmlFile xmlFile = (XmlFile)createEphemeralPsiFile(myProject, file.getName(), StdFileTypes.XML, content);
      return LayoutPsiPullParser.create(xmlFile, myLogger);
    }

    void addFileContent(@NotNull File file, @NotNull String content) {
      myFileContent.put(file, content);
    }
  }

  private static class MyDrawableRenderer implements GraphicGeneratorContext.DrawableRenderer {
    @NotNull private final ListenableFuture<RenderTask> myRenderTaskFuture;
    @NotNull private final Object myRenderLock = new Object();
    @NotNull private final MyLayoutPullParserFactory myParserFactory;
    @NotNull private final AtomicInteger myCounter = new AtomicInteger();

    public MyDrawableRenderer(@NotNull AndroidFacet facet) {
      Module module = facet.getModule();
      RenderLogger logger = new RenderLogger(AndroidAdaptiveIconGenerator.class.getSimpleName(), module);
      myParserFactory = new MyLayoutPullParserFactory(module.getProject(), logger);
      // The ThemeEditorUtils.getConfigurationForModule and RenderService.createTask calls are pretty expensive.
      // Executing them off the UI thread.
      myRenderTaskFuture = FutureUtils.executeOnPooledThread(() -> {
        try {
          Configuration configuration = ThemeEditorUtils.getConfigurationForModule(module);
          RenderService service = RenderService.getInstance(facet);
          RenderTask renderTask = service.createTask(null, configuration, logger, null, myParserFactory);
          assert renderTask != null;
          renderTask.getLayoutlibCallback().setLogger(logger);
          if (logger.hasProblems()) {
            getLog().error(RenderProblem.format(logger.getMessages()));
          }
          return renderTask;
        } catch (RuntimeException | Error e) {
          getLog().error(e);
          return null;
        }
      });
    }

    @Override
    public void dispose() {
      synchronized (myRenderLock) {
        RenderTask renderTask = getRenderTask();
        if (renderTask != null) {
          renderTask.dispose();
        }
      }
    }

    @Override
    @NotNull
    public ListenableFuture<BufferedImage> renderDrawable(@NotNull String xmlDrawableText, @NotNull Dimension size) {
      String xmlText = VectorDrawableTransformer.resizeAndCenter(xmlDrawableText, size, 1, null);
      ResourceUrl url = ResourceUrl.create(null, ResourceType.DRAWABLE, "ic_image_preview");
      String resourceName = String.format("preview_%x.xml", myCounter.getAndIncrement());
      ResourceValue value = new ResourceValue(url, resourceName);

      RenderTask renderTask = getRenderTask();
      if (renderTask == null) {
        return Futures.immediateFuture(AssetStudioUtils.createDummyImage());
      }

      synchronized (myRenderLock) {
        myParserFactory.addFileContent(new File(resourceName), xmlText);
        renderTask.setOverrideRenderSize(size.width, size.height);
        renderTask.setMaxRenderSize(size.width, size.height);

        return renderTask.renderDrawable(value);
      }
    }

    @Nullable
    private RenderTask getRenderTask() {
      try {
        return myRenderTaskFuture.get();
      }
      catch (InterruptedException | ExecutionException e) {
        // The error was logged earlier.
        return null;
      }
    }
  }
}
