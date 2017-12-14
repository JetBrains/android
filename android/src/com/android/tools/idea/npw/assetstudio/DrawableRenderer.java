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
package com.android.tools.idea.npw.assetstudio;

import com.android.ide.common.rendering.api.*;
import com.android.resources.ResourceType;
import com.android.tools.idea.concurrent.FutureUtils;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.rendering.*;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.Disposable;
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

/**
 * Renders XML drawables to raster images.
 */
class DrawableRenderer implements Disposable {
  @NotNull private final ListenableFuture<RenderTask> myRenderTaskFuture;
  @NotNull private final Object myRenderLock = new Object();
  @NotNull private final MyLayoutPullParserFactory myParserFactory;
  @NotNull private final AtomicInteger myCounter = new AtomicInteger();

  /**
   * Initializes the renderer. Every renderer has to be disposed by calling {@link #dispose()}.
   * Please keep in mind that each renderer instance allocates significant resources inside Layoutlib.
   *
   * @param facet the Android facet
   */
  public DrawableRenderer(@NotNull AndroidFacet facet) {
    Module module = facet.getModule();
    RenderLogger logger = new RenderLogger(LauncherIconGenerator.class.getSimpleName(), module);
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
  /**
   * Produces a raster image for the given XML drawable.
   *
   * @param xmlDrawableText the text of the XML drawable
   * @param size the size of the produced raster image
   * @return the rendering of the drawable in form of a future
   */
  @NotNull
  public ListenableFuture<BufferedImage> renderDrawable(@NotNull String xmlDrawableText, @NotNull Dimension size) {
    String xmlText = VectorDrawableTransformer.resizeAndCenter(xmlDrawableText, size, 1, null);
    String resourceName = String.format("preview_%x.xml", myCounter.getAndIncrement());
    ResourceValue value = new ResourceValue(ResourceNamespace.RES_AUTO, ResourceType.DRAWABLE, "ic_image_preview", resourceName);

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

  @Override
  public void dispose() {
    RenderTask renderTask = getRenderTask();
    if (renderTask != null) {
      synchronized (myRenderLock) {
        renderTask.dispose();
      }
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
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(DrawableRenderer.class);
  }
}
