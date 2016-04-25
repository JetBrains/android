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
package com.android.tools.idea.rendering;

import com.android.annotations.NonNull;
import com.android.ide.common.rendering.LayoutLibrary;
import com.android.ide.common.rendering.api.Features;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.RenderContext;
import com.android.tools.idea.gradle.structure.editors.AndroidProjectSettingsService;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.android.utils.HtmlBuilder;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiFile;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.maven.AndroidMavenUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.uipreview.RenderingException;
import org.jetbrains.android.uipreview.UnsupportedJavaRuntimeException;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

import static com.android.SdkConstants.TAG_PREFERENCE_SCREEN;
import static com.intellij.lang.annotation.HighlightSeverity.ERROR;
import static com.intellij.lang.annotation.HighlightSeverity.WARNING;

/**
 * The {@link RenderService} provides rendering and layout information for
 * Android layouts. This is a wrapper around the layout library.
 */
public class RenderService {
  public static final boolean NELE_ENABLED = Boolean.getBoolean("nele.enabled");
  private static final Object RENDERING_LOCK = new Object();

  private static final String JDK_INSTALL_URL = "https://developer.android.com/preview/setup-sdk.html#java8";

  @NotNull
  private final AndroidFacet myFacet;

  private final Object myCredential = new Object();

  public RenderService(@NotNull AndroidFacet facet) {
    myFacet = facet;
  }

  /**
   * Returns the {@linkplain RenderService} for the given facet
   */
  @NotNull
  public static RenderService get(@NotNull AndroidFacet facet) {
    return facet.getRenderService();
  }

  @Nullable
  public static LayoutLibrary getLayoutLibrary(@Nullable final Module module, @Nullable IAndroidTarget target) {
    if (module == null || target == null) {
      return null;
    }
    Project project = module.getProject();
    AndroidPlatform platform = AndroidPlatform.getInstance(module);
    if (platform != null) {
      try {
        return platform.getSdkData().getTargetData(target).getLayoutLibrary(project);
      }
      catch (RenderingException e) {
        // Ignore.
      }
      catch (IOException e) {
        // Ditto
      }
    }
    return null;
  }

  public static boolean supportsCapability(@NotNull final Module module, @NotNull IAndroidTarget target,
                                           @MagicConstant(flagsFromClass = Features.class) int capability) {
    Project project = module.getProject();
    AndroidPlatform platform = AndroidPlatform.getInstance(module);
    if (platform != null) {
      try {
        LayoutLibrary library = platform.getSdkData().getTargetData(target).getLayoutLibrary(project);
        if (library != null) {
          return library.supports(capability);
        }
      }
      catch (RenderingException e) {
        // Ignore: if service can't be found, that capability isn't available
      }
      catch (IOException e) {
        // Ditto
      }
    }
    return false;
  }

  /** Returns true if the given file can be rendered */
  public static boolean canRender(@Nullable PsiFile file) {
    return file != null && LayoutPullParserFactory.isSupported(file);
  }

  @NotNull
  public RenderLogger createLogger() {
    Module module = getModule();
    return new RenderLogger(module.getName(), module, myCredential);
  }

  /**
   * Creates a new {@link RenderService} associated with the given editor.
   *
   * @return a {@link RenderService} which can perform rendering services
   */
  @Nullable
  public RenderTask createTask(@Nullable final PsiFile psiFile,
                               @NotNull final Configuration configuration,
                               @NotNull final RenderLogger logger,
                               @Nullable final RenderContext renderContext) {
    Module module = myFacet.getModule();
    final Project project = module.getProject();
    AndroidPlatform platform = getPlatform(module, logger);
    if (platform == null) {
      return null;
    }

    IAndroidTarget target = configuration.getTarget();
    if (target == null) {
      logger.addMessage(RenderProblem.createPlain(ERROR, "No render target was chosen"));
      return null;
    }

    warnIfObsoleteLayoutLib(module, logger, renderContext, target);

    LayoutLibrary layoutLib;
    try {
      layoutLib = platform.getSdkData().getTargetData(target).getLayoutLibrary(project);
      if (layoutLib == null) {
        String message = AndroidBundle.message("android.layout.preview.cannot.load.library.error");
        logger.addMessage(RenderProblem.createPlain(ERROR, message));
        return null;
      }
    }
    catch (UnsupportedJavaRuntimeException e) {
      RenderProblem.Html javaVersionProblem = RenderProblem.create(ERROR);
      javaVersionProblem.getHtmlBuilder()
        .add(e.getPresentableMessage())
        .newline()
        .addLink("Install a supported JDK", JDK_INSTALL_URL);
      logger.addMessage(javaVersionProblem);
      return null;
    }
    catch (RenderingException e) {
      String message = e.getPresentableMessage();
      message = message != null ? message : AndroidBundle.message("android.layout.preview.default.error.message");
      logger.addMessage(RenderProblem.createPlain(ERROR, message, module.getProject(), logger.getLinkManager(), e));
      return null;
    }
    catch (IOException e) {
      final String message = e.getMessage();
      logger.error(null, "I/O error: " + (message != null ? ": " + message : ""), e);
      return null;
    }

    if (psiFile != null && TAG_PREFERENCE_SCREEN.equals(AndroidPsiUtils.getRootTagName(psiFile)) && !layoutLib.supports(Features.PREFERENCES_RENDERING)) {
      // This means that user is using an outdated version of layoutlib. A warning to update has already been
      // presented in warnIfObsoleteLayoutLib(). Just log a plain message asking users to update.
      logger.addMessage(RenderProblem.createPlain(ERROR, "This version of the rendering library does not support rendering Preferences. " +
                                                         "Update it using the SDK Manager"));

      return null;
    }

    Device device = configuration.getDevice();
    if (device == null) {
      logger.addMessage(RenderProblem.createPlain(ERROR, "No device selected"));
      return null;
    }

    RenderTask task = new RenderTask(this, configuration, logger, layoutLib, device, myCredential);
    if (psiFile != null) {
      task.setPsiFile(psiFile);
    }
    task.setRenderContext(renderContext);

    return task;
  }

  @NotNull
  public AndroidFacet getFacet() {
    return myFacet;
  }

  public Module getModule() {
    return myFacet.getModule();
  }

  public Project getProject() {
    return getModule().getProject();
  }

  @Nullable
  public AndroidPlatform getPlatform() {
    return AndroidPlatform.getInstance(getModule());
  }

  @Nullable
  private static AndroidPlatform getPlatform(@NotNull final Module module, @Nullable RenderLogger logger) {
    AndroidPlatform platform = AndroidPlatform.getInstance(module);
    if (platform == null && logger != null) {
      if (!AndroidMavenUtil.isMavenizedModule(module)) {
        RenderProblem.Html message = RenderProblem.create(ERROR);
        logger.addMessage(message);
        message.getHtmlBuilder().addLink("No Android SDK found. Please ", "configure", " an Android SDK.",
           logger.getLinkManager().createRunnableLink(new Runnable() {
             @Override
             public void run() {
               Project project = module.getProject();
               ProjectSettingsService service = ProjectSettingsService.getInstance(project);
               if (Projects.requiresAndroidModel(project) && service instanceof AndroidProjectSettingsService) {
                 ((AndroidProjectSettingsService)service).openSdkSettings();
                 return;
               }
               AndroidSdkUtils.openModuleDependenciesConfigurable(module);
             }
           }));
      }
      else {
        String message = AndroidBundle.message("android.maven.cannot.parse.android.sdk.error", module.getName());
        logger.addMessage(RenderProblem.createPlain(ERROR, message));
      }
    }
    return platform;
  }

  private static boolean ourWarnAboutObsoleteLayoutLibVersions = true;
  protected static void warnIfObsoleteLayoutLib(@NotNull final Module module,
                                                @NotNull RenderLogger logger,
                                                @Nullable final RenderContext renderContext,
                                                @NotNull IAndroidTarget target) {
    if (!ourWarnAboutObsoleteLayoutLibVersions) {
      return;
    }

    if (target instanceof CompatibilityRenderTarget) {
      target = ((CompatibilityRenderTarget)target).getRenderTarget();
    }
    final AndroidVersion version = target.getVersion();
    final int revision;
    // Look up the current minimum required version for layoutlib for each API level. Note that these
    // are minimum revisions; if a later version is available, it will be installed.
    switch (version.getFeatureLevel()) {
      case 23: revision = 2; break;
      case 22: revision = 2; break;
      case 21: revision = 2; break;
      case 20: revision = 2; break;
      case 19: revision = 4; break;
      case 18: revision = 3; break;
      case 17: revision = 3; break;
      case 16: revision = 5; break;
      case 15: revision = 5; break;
      case 14: revision = 4; break;
      case 13: revision = 1; break;
      case 12: revision = 3; break;
      case 11: revision = 2; break;
      case 10: revision = 2; break;
      case 8: revision = 3; break;
      default: revision = -1; break;
    }

    if (revision >= 0 && target.getRevision() < revision) {
      RenderProblem.Html problem = RenderProblem.create(WARNING);
      problem.tag("obsoleteLayoutlib");
      HtmlBuilder builder = problem.getHtmlBuilder();
      builder.add("Using an obsolete version of the " + target.getVersionName() + " layout library which contains many known bugs: ");
      builder.addLink("Install Update", logger.getLinkManager().createRunnableLink(new Runnable() {
        @Override
        public void run() {
          // Don't warn again
          //noinspection AssignmentToStaticFieldFromInstanceMethod
          ourWarnAboutObsoleteLayoutLibVersions = false;

          List<String> requested = Lists.newArrayList();
          // The revision to install. Note that this will install a higher version than this if available;
          // e.g. even if we ask for version 4, if revision 7 is available it will be installed, not revision 4.
          requested.add(DetailsTypes.getPlatformPath(version));
          ModelWizardDialog dialog = SdkQuickfixUtils.createDialogForPaths(module.getProject(), requested);

          if (dialog != null && dialog.showAndGet()) {
            if (renderContext != null) {
              // Force the target to be recomputed; this will pick up the new revision object from the local sdk.
              Configuration configuration = renderContext.getConfiguration();
              if (configuration != null) {
                configuration.getConfigurationManager().setTarget(null);
              }
              renderContext.requestRender();
              // However, due to issue https://code.google.com/p/android/issues/detail?id=76096 it may not yet
              // take effect.
              Messages.showInfoMessage(module.getProject(),
                                       "Note: Due to a bug you may need to restart the IDE for the new layout library to fully take effect",
                                       "Restart Recommended");
            }
          }
        }
      }));
      builder.addLink(", ", "Ignore For Now", null, logger.getLinkManager().createRunnableLink(new Runnable() {
        @Override
        public void run() {
          //noinspection AssignmentToStaticFieldFromInstanceMethod
          ourWarnAboutObsoleteLayoutLibVersions = false;
          if (renderContext != null) {
            renderContext.requestRender();
          }
        }
      }));

      logger.addMessage(problem);
    }
  }

  /**
   * Runs a action that requires the rendering lock. Layoutlib is not thread safe so any rendering actions should be called using this
   * method.
   */
  public static void runRenderAction(@NotNull final Runnable runnable) throws Exception {
    runRenderAction(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        runnable.run();
        return null;
      }
    });
  }

  /**
   * Runs a action that requires the rendering lock. Layoutlib is not thread safe so any rendering actions should be called using this
   * method.
   */
  public static <T> T runRenderAction(@NotNull Callable<T> callable) throws Exception {
    synchronized (RENDERING_LOCK) {
      return callable.call();
    }
  }

  /**
   * Given a {@link ViewInfo} from a layoutlib rendering, checks that the view info provides
   * valid bounds. This is normally the case. However, there are known scenarios, where
   * for various reasons, the View is left in a state where some of its bounds (left, right, top
   * or bottom) are not properly resolved; they carry MeasureSpec state along, which depending
   * on whether the specification was AT_MOST or EXACTLY this will either be a very large number,
   * or a very small (negative) number. In these cases we don't want to pass on the values to
   * further UI editing processing, since it for example can lead to calling Graphics#drawLine
   * with giant coordinates which can freeze the IDE; see for example
   * https://code.google.com/p/android/issues/detail?id=178690.
   * <p/>
   * To detect this, we simply need to check to see if the MeasureSpec mode bits are set
   * in any of the four bounds fields of the {@link ViewInfo}. Note however that these
   * view bounds are sometimes manipulated (e.g. values added or subtracted if a parent view
   * bound is also invalid) so rather than simply looking for the mode mask strictly, we look
   * in the nearby range too.
   *
   * @param view the {@link ViewInfo} to check
   * @return Normally the {@link ViewInfo} itself, but a dummy 0-bound {@link ViewInfo} if
   * the view bounds are indeed invalid
   */
  @NonNull
  public static ViewInfo getSafeBounds(@NonNull ViewInfo view) {
    int left = Math.abs(view.getLeft());
    int right = Math.abs(view.getRight());
    int top = Math.abs(view.getTop());
    int bottom = Math.abs(view.getBottom());

    if (left < MAX_MAGNITUDE && right < MAX_MAGNITUDE && top < MAX_MAGNITUDE && bottom < MAX_MAGNITUDE) {
      return view;
    }
    else {
      // Not extracted as a constant; we expect this scenario to be rare
      return new ViewInfo(null, null, 0, 0, 0, 0);
    }
  }

  /** This is the View.MeasureSpec mode shift */
  private static final int MEASURE_SPEC_MODE_SHIFT = 30;

  /**
   * The maximum absolute value of bounds. This tries to identify values that carry
   * remnants of View.MeasureSpec mode bits, but accounts for the fact that sometimes arithmetic
   * is carried out on these values afterwards to bring them to lower values than they started
   * at, and we want to include those as well; there's a lot of room since the bits are shifted
   * quite a long way compared to the current relevant screen pixel ranges.
   */
  private static final int MAX_MAGNITUDE = 1 << (MEASURE_SPEC_MODE_SHIFT - 5);
}
