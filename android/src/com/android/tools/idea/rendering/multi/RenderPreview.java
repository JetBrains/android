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
package com.android.tools.idea.rendering.multi;

import com.android.ide.common.rendering.HardwareConfigHelper;
import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.rendering.api.Result.Status;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Screen;
import com.android.sdklib.devices.State;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.configurations.*;
import com.android.tools.idea.ddms.screenshot.DeviceArtPainter;
import com.android.tools.idea.rendering.*;
import com.android.utils.SdkUtils;
import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.PairFunction;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.AndroidEditorSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Comparator;

import static com.android.tools.idea.configurations.ConfigurationListener.MASK_RENDERING;
import static com.android.tools.idea.rendering.ShadowPainter.SMALL_SHADOW_SIZE;
import static java.awt.RenderingHints.KEY_ANTIALIASING;
import static java.awt.RenderingHints.VALUE_ANTIALIAS_ON;

/**
 * Represents a preview rendering of a given configuration
 */
public class RenderPreview implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.android.tools.idea.rendering.RenderPreview");

  /**
   * Height of the toolbar shown over a preview during hover. Needs to be
   * large enough to accommodate icons below.
   */
  private static final int HEADER_HEIGHT = 20;

  /** Whether these previews support zooming at the individual level */
  private static final boolean ZOOM_SUPPORT = false;

  /**
   * Whether to dump out rendering failures of the previews to the log
   */
  private static final boolean DUMP_RENDER_DIAGNOSTICS = true;

  /**
   * Extra error checking in debug mode
   */
  private static final boolean DEBUG = false;

  /**
   * The configuration being previewed
   */
  private @NotNull Configuration myConfiguration;

  /**
   * Configuration to use if we have an alternate input to be rendered
   */
  private @NotNull Configuration myAlternateConfiguration;

  /**
   * The associated manager
   */
  private final @NotNull RenderPreviewManager myManager;

  private final @NotNull RenderContext myRenderContext;
  private @Nullable BufferedImage myThumbnail;
  private @Nullable String myDisplayName;
  private int myX;
  private int myY;
  private int myLayoutWidth;
  private int myLayoutHeight;
  private int myTitleHeight;
  private double myScale = 1.0;
  private double myAspectRatio;
  /** Whether the preview wants a device frame (but it may still not show it if the option isc currently off) */
  private boolean myShowFrame;
  /** Whether current thumbnail actually has a device frame */
  private boolean myThumbnailHasFrame;
  private @Nullable Rectangle myViewBounds;
  private @Nullable Runnable myPendingRendering;
  private @Nullable String myId;

  /**
   * If non null, points to a separate file containing the source
   */
  private @Nullable VirtualFile myAlternateInput;

  /**
   * If included within another layout, the name of that outer layout
   */
  private @Nullable IncludeReference myIncludedWithin;

  /**
   * Whether the mouse is actively hovering over this preview
   */
  private boolean myActive;

  /**
   * Whether this preview cannot be rendered because of a model error - such
   * as an invalid configuration, a missing resource, an error in the XML
   * markup, etc. If non null, contains the error message (or a blank string
   * if not known), and null if the render was successful.
   */
  private String myError;

  /**
   * Whether in the current layout, this preview is visible
   */
  private boolean myVisible;

  /**
   * Whether the configuration has changed and needs to be refreshed the next time
   * this preview made visible. This corresponds to the change flags in
   * {@link ConfigurationListener}.
   */
  private int myDirty;

  /**
   * TODO: Figure out something more memory efficient than storing all these images. Maybe resize it down to half size initially!
   * Or maybe only if it's made setVisible
   */
  private BufferedImage myFullImage;
  private int myFullWidth;
  private int myFullHeight;

  /**
   * Creates a new {@linkplain RenderPreview}
   *
   * @param manager       the manager
   * @param renderContext canvas where preview is painted
   * @param configuration the associated configuration
   * @param showFrame     whether device frames should be shown
   */
  @SuppressWarnings("AssertWithSideEffects")
  private RenderPreview(@NotNull RenderPreviewManager manager,
                        @NotNull RenderContext renderContext,
                        @NotNull Configuration configuration,
                        boolean showFrame) {
    myManager = manager;
    myRenderContext = renderContext;
    myConfiguration = configuration;
    myShowFrame = showFrame;

    // Should only attempt to create configurations for fully configured devices
    //noinspection AssertWithSideEffects
    assert myConfiguration.getDevice() != null;
    assert myConfiguration.getDeviceState() != null;
    assert myConfiguration.getTarget() != null;
    assert myConfiguration.getTheme() != null;
    assert myConfiguration.getFullConfig().getScreenSizeQualifier() != null : myConfiguration;

    computeInitialSize();
  }

  /**
   * Considers the device screen and orientation and computes initial values for
   * the {@link #myFullWidth}, {@link #myFullHeight}, {@link #myAspectRatio},
   * {@link #myLayoutWidth} and {@link #myLayoutHeight} fields
   */
  void computeInitialSize() {
    computeFullSize();

    if (myFullHeight > 0) {
      double scale = Math.min(1, getScale(myFullWidth, myFullHeight));
      myLayoutWidth = (int)(myFullWidth * scale);
      myLayoutHeight = (int)(myFullHeight * scale);
    } else {
      myAspectRatio = 1;
      myLayoutWidth = RenderPreviewManager.getMaxWidth();
      myLayoutHeight = RenderPreviewManager.getMaxHeight();
    }
  }

  /**
   * Considers the device screen and orientation and computes values for
   * the {@link #myFullWidth}, {@link #myFullHeight}, and {@link #myAspectRatio}.
   */
  @SuppressWarnings("SuspiciousNameCombination") // Deliberately swapping width/height orientations
  private boolean computeFullSize() {
    Device device = myConfiguration.getDevice();
    if (device == null) {
      return true;
    }
    Screen screen = device.getDefaultHardware().getScreen();
    if (screen == null) {
      return true;
    }

    State deviceState = myConfiguration.getDeviceState();
    if (deviceState == null) {
      deviceState = device.getDefaultState();
    }
    ScreenOrientation orientation = deviceState.getOrientation();
    Dimension size = device.getScreenSize(orientation);
    assert size != null;
    int screenWidth = size.width;
    int screenHeight = size.height;

    boolean changed = myFullWidth != screenWidth || myFullHeight != screenHeight;
    myFullWidth = screenWidth;
    myFullHeight = screenHeight;

    if (myShowFrame) {
      DeviceArtPainter framePainter = DeviceArtPainter.getInstance();
      double xScale = framePainter.getFrameWidthOverhead(device, orientation);
      double yScale = framePainter.getFrameHeightOverhead(device, orientation);
      myFullWidth *= xScale;
      myFullHeight *= yScale;
    }


    myAspectRatio = myFullHeight == 0 ? 1 : myFullWidth / (double)myFullHeight;
    return changed;
  }

  /** Recomputes the size */
  void updateSize() {
    boolean changed = computeFullSize();
    if (changed) {
      setMaxSize(myMaxWidth, myMaxHeight);
    }
  }

  /**
   * Sets the configuration to use for this preview
   *
   * @param configuration the new configuration
   */
  public void setConfiguration(@NotNull Configuration configuration) {
    myConfiguration = configuration;
  }

  /**
   * Gets the scale being applied to the thumbnail
   *
   * @return the scale being applied to the thumbnail
   */
  public double getScale() {
    return myScale;
  }

  /**
   * Sets the scale to apply to the thumbnail
   *
   * @param scale the factor to scale the thumbnail picture by
   */
  public void setScale(double scale) {
    if (ZOOM_SUPPORT) {
      if (scale != myScale) {
        disposeThumbnail();
        myScale = scale;
      }
    }
  }

  /**
   * Returns the aspect ratio of this render preview
   *
   * @return the aspect ratio
   */
  public double getAspectRatio() {
    return myAspectRatio;
  }

  /**
   * Returns whether the preview is actively hovered
   *
   * @return whether the mouse is hovering over the preview
   */
  public boolean isActive() {
    return myActive;
  }

  /**
   * Sets whether the preview is actively hovered
   *
   * @param active if the mouse is hovering over the preview
   */
  public void setActive(boolean active) {
    myActive = active;
  }

  /**
   * Returns whether the preview is visible. Previews that are off
   * screen are typically marked invisible during layout, which means we don't
   * have to expend effort computing preview thumbnails etc
   *
   * @return true if the preview is visible
   */
  public boolean isVisible() {
    return myVisible;
  }

  /**
   * Returns whether this preview represents a forked layout
   *
   * @return true if this preview represents a separate file
   */
  public boolean isForked() {
    return myAlternateInput != null || myIncludedWithin != null;
  }

  /**
   * Returns the file to be used for this preview, or null if this is not a
   * forked layout meaning that the file is the one used in the chooser
   *
   * @return the file or null for non-forked layouts
   */
  @Nullable
  public VirtualFile getAlternateInput() {
    if (myAlternateInput != null) {
      return myAlternateInput;
    }
    else if (myIncludedWithin != null) {
      return myIncludedWithin.getFromFile();
    }

    return null;
  }

  /**
   * Returns the area of this render preview, PRIOR to scaling
   *
   * @return the area (width times height without scaling)
   */
  int getArea() {
    return myLayoutWidth * myLayoutHeight;
  }

  /**
   * Sets whether the preview is visible. Previews that are off
   * screen are typically marked invisible during layout, which means we don't
   * have to expend effort computing preview thumbnails etc
   *
   * @param visible whether this preview is visible
   */
  public void setVisible(boolean visible) {
    if (visible != myVisible) {
      myVisible = visible;
      if (myVisible) {
        if (myDirty != 0) {
          // Just made the render preview visible:
          configurationChanged(myDirty); // schedules render
        }
        else {
          updateForkStatus();
          myManager.scheduleRender(this);
        }
      }
      else {
        dispose();
      }
    }
  }

  /**
   * Sets the layout position relative to the top left corner of the preview
   * area, in control coordinates
   */
  void setPosition(int x, int y) {
    myX = x;
    myY = y;
  }

  /**
   * Gets the layout X position relative to the top left corner of the preview
   * area, in control coordinates
   */
  int getX() {
    return myX;
  }

  /**
   * Gets the layout Y position relative to the top left corner of the preview
   * area, in control coordinates
   */
  int getY() {
    return myY;
  }

  /**
   * Determine whether this configuration has a better match in a different layout file
   */
  private void updateForkStatus() {
    FolderConfiguration config = myConfiguration.getFullConfig();
    if (myAlternateInput != null && myConfiguration.isBestMatchFor(myAlternateInput, config)) {
      return;
    }

    myAlternateInput = null;
    VirtualFile editedFile = myConfiguration.getFile();
    if (editedFile != null) {
      if (!myConfiguration.isBestMatchFor(editedFile, config)) {
        LocalResourceRepository resources = AppResourceRepository.getAppResources(myConfiguration.getModule(), true);
        if (resources != null) {
          VirtualFile best = resources.getMatchingFile(editedFile, ResourceType.LAYOUT, config);
          if (best != null) {
            myAlternateInput = best;
          }
          if (myAlternateInput != null) {
            myAlternateConfiguration = Configuration.create(myConfiguration, myAlternateInput);
          }
        }
      }
    }
  }

  /**
   * Creates a new {@linkplain RenderPreview}
   *
   * @param manager       the manager
   * @param configuration the associated configuration
   * @param showFrame     whether device frames should be shown
   * @return a new configuration
   */
  @NotNull
  public static RenderPreview create(@NotNull RenderPreviewManager manager, @NotNull Configuration configuration, boolean showFrame) {
    RenderContext context = manager.getRenderContext();
    return new RenderPreview(manager, context, configuration, showFrame);
  }

  /**
   * Throws away this preview: cancels any pending rendering jobs and disposes
   * of image resources etc
   */
  @Override
  public void dispose() {
    disposeThumbnail();
    if (this != myManager.getStashedPreview()) {
      myConfiguration.dispose();
    }
  }

  /**
   * Disposes the thumbnail rendering.
   */
  void disposeThumbnail() {
    myThumbnail = null;
    myFullImage = null;
  }

  /**
   * Returns the display name of this preview
   *
   * @return the name of the preview
   */
  @NotNull
  public String getDisplayName() {
    if (myDisplayName == null) {
      String displayName = getConfiguration().getDisplayName();
      if (displayName == null) {
        // No display name: this must be the configuration used by default
        // for the view which is originally displayed (before adding thumbnails),
        // and you've switched away to something else; now we need to display a name
        // for this original configuration. For now, just call it "Original"
        return "Original";
      }

      return displayName;
    }

    return myDisplayName;
  }

  /**
   * Sets the display name of this preview. By default, the display name is
   * the display name of the configuration, but it can be overridden by calling
   * this setter (which only sets the preview name, without editing the configuration.)
   *
   * @param displayName the new display name
   */
  public void setDisplayName(@Nullable String displayName) {
    myDisplayName = displayName;
  }

  /**
   * Sets an inclusion context to use for this layout, if any. This will render
   * the configuration preview as the outer layout with the current layout
   * embedded within.
   *
   * @param includedWithin a reference to a layout which includes this one
   */
  public void setIncludedWithin(@Nullable IncludeReference includedWithin) {
    myIncludedWithin = includedWithin;
  }

  /**
   * Render immediately (on the current thread)
   */
  void renderSync() {
    if (!tryRenderSync()) {
      disposeThumbnail();
    }
  }

  private boolean tryRenderSync() {
    final Module module = myRenderContext.getModule();
    if (module == null) {
      return false;
    }
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return false;
    }

    final Configuration configuration = myAlternateInput != null && myAlternateConfiguration != null
                                        ? myAlternateConfiguration : myConfiguration;
    PsiFile psiFile;
    if (myAlternateInput != null) {
      psiFile = AndroidPsiUtils.getPsiFileSafely(module.getProject(), myAlternateInput);
    } else {
      psiFile = myRenderContext.getXmlFile();
    }
    if (psiFile == null) {
      return false;
    }
    PreviewRenderContext renderContext = new PreviewRenderContext(myRenderContext, configuration, (XmlFile)psiFile);
    RenderService renderService = RenderService.get(facet);
    RenderLogger logger = renderService.createLogger();
    final RenderTask renderTask = renderService.createTask(psiFile, configuration, logger, renderContext);
    if (renderTask == null) {
      return false;
    }

    if (myIncludedWithin != null) {
      renderTask.setIncludedWithin(myIncludedWithin);
    }

    RenderResult result = renderTask.render();
    RenderSession session = result != null ? result.getSession() : null;
    if (session != null) {
      Result render = session.getResult();

      if (DUMP_RENDER_DIAGNOSTICS) {
        if (logger.hasProblems() || !session.getResult().isSuccess()) {
          RenderErrorPanel panel = new RenderErrorPanel();
          String html = panel.showErrors(result);
          LOG.info("Found problems rendering preview " + getDisplayName() + ": " + html);
        }
      }

      if (render.isSuccess()) {
        myError = null;
      }
      else {
        myError = render.getErrorMessage();
        if (myError == null) {
          myError = "<unknown error>";
        }
      }

      if (render.getStatus() == Status.ERROR_TIMEOUT) {
        // TODO: Special handling? schedule update again later
        return false;
      }

      disposeThumbnail();
      if (render.isSuccess()) {
        RenderedImage renderedImage = result.getImage();
        if (renderedImage != null) {
          myFullImage = renderedImage.getOriginalImage();
        }
      }

      if (myError != null) {
        createErrorThumbnail();
      }
      return true;
    } else {
      myError = "Render Failed";
      disposeThumbnail();
      createErrorThumbnail();
      return false;
    }
  }

  @Nullable
  private BufferedImage getThumbnail() {
    if (myThumbnail == null && myFullImage != null) {
      createThumbnail();
    }

    return myThumbnail;
  }

  /**
   * Sets the new image of the preview and generates a thumbnail
   */
  void createThumbnail() {
    BufferedImage image = myFullImage;
    if (image == null) {
      myThumbnail = null;
      return;
    }

    Project project = myConfiguration.getModule().getProject();
    AndroidEditorSettings.GlobalState settings = AndroidEditorSettings.getInstance().getGlobalState();

    if (UIUtil.isRetina() && ImageUtils.supportsRetina() && settings.isRetina() && createRetinaThumbnail()) {
      return;
    }

    int shadowSize = 0;
    myThumbnailHasFrame = false;
    boolean showFrame = myShowFrame;

    if (showFrame && settings.isShowDeviceFrames()) {
      DeviceArtPainter framePainter = DeviceArtPainter.getInstance();
      Device device = myConfiguration.getDevice();
      boolean showEffects = settings.isShowEffects();
      State deviceState = myConfiguration.getDeviceState();
      if (device != null && deviceState != null) {
        double scale = Math.min(1, getLayoutWidth() / (double)image.getWidth());
        //double scale = getLayoutWidth() / (double)image.getWidth();
        ScreenOrientation orientation = deviceState.getOrientation();
        double frameScale = framePainter.getFrameMaxOverhead(device, orientation);
        scale /= frameScale;
        if (myViewBounds == null) {
          myViewBounds = new Rectangle();
        }
        image = framePainter.createFrame(image, device, orientation, showEffects, scale, myViewBounds);
        myThumbnailHasFrame = true;
      } else {
        // TODO: Do drop shadow painting if frame fails?
        double scale = Math.min(1, getLayoutWidth() / (double)image.getWidth());
        image = ImageUtils.scale(image, scale, scale, 0, 0);
      }
    } else {
      boolean drawShadows = !myRenderContext.hasAlphaChannel();
      if (drawShadows && ResourceHelper.getFolderType(myRenderContext.getVirtualFile()) == ResourceFolderType.DRAWABLE) {
        drawShadows = false;
      }
      double scale = Math.min(1, getLayoutWidth() / (double)image.getWidth());
      shadowSize = drawShadows ? SMALL_SHADOW_SIZE : 0;
      if (scale < 1.0) {
        image = ImageUtils.scale(image, scale, scale, shadowSize, shadowSize);
        if (drawShadows) {
          ShadowPainter.drawSmallRectangleShadow(image, 0, 0, image.getWidth() - shadowSize, image.getHeight() - shadowSize);
        }
      }
    }

    myThumbnail = image;
    if (image != null) {
      myLayoutWidth = image.getWidth() - shadowSize;
      myLayoutHeight = image.getHeight() - shadowSize;
    }
  }

  private boolean createRetinaThumbnail() {
    BufferedImage image = myFullImage;
    if (image == null) {
      myThumbnail = null;
      return true;
    }

    myThumbnailHasFrame = false;
    boolean showFrame = myShowFrame;

    AndroidEditorSettings.GlobalState settings = AndroidEditorSettings.getInstance().getGlobalState();
    if (showFrame && settings.isShowDeviceFrames()) {
      DeviceArtPainter framePainter = DeviceArtPainter.getInstance();
      Device device = myConfiguration.getDevice();
      boolean showEffects = settings.isShowEffects();
      State deviceState = myConfiguration.getDeviceState();
      if (device != null && deviceState != null) {
        double scale = getLayoutWidth() / (double)image.getWidth();
        ScreenOrientation orientation = deviceState.getOrientation();
        double frameScale = framePainter.getFrameMaxOverhead(device, orientation);
        scale /= frameScale;
        if (myViewBounds == null) {
          myViewBounds = new Rectangle();
        }
        image = framePainter.createFrame(image, device, orientation, showEffects, 2 * scale, myViewBounds);
        myViewBounds.x /= 2;
        myViewBounds.y /= 2;
        myViewBounds.width /= 2;
        myViewBounds.height /= 2;

        myThumbnailHasFrame = true;
      } else {
        double scale = getLayoutWidth() / (double)image.getWidth();
        image = ImageUtils.scale(image, 2 * scale, 2 * scale, 0, 0);
      }

      image = ImageUtils.convertToRetina(image);
      if (image == null) {
        return false;
      }
    } else {
      boolean drawShadows = !myRenderContext.hasAlphaChannel();
      if (drawShadows && ResourceHelper.getFolderType(myRenderContext.getVirtualFile()) == ResourceFolderType.DRAWABLE) {
        drawShadows = false;
      }
      double scale = getLayoutWidth() / (double)image.getWidth();
      if (scale < 1.0) {
        image = ImageUtils.scale(image, 2 * scale, 2 * scale);

        image = ImageUtils.convertToRetina(image);
        if (image == null) {
          return false;
        }

        myLayoutWidth = image.getWidth();
        myLayoutHeight = image.getHeight();

        if (drawShadows) {
          image = ShadowPainter.createSmallRectangularDropShadow(image);
        }
        myThumbnail = image;
        return true;
      }
    }

    myThumbnail = image;
    myLayoutWidth = image.getWidth();
    myLayoutHeight = image.getHeight();

    return true;
  }

  void createErrorThumbnail() {
    int width = getLayoutWidth();
    int height = getLayoutHeight();
    @SuppressWarnings("UndesirableClassUsage")
    BufferedImage image = new BufferedImage(width + SMALL_SHADOW_SIZE, height + SMALL_SHADOW_SIZE, BufferedImage.TYPE_INT_ARGB);

    Graphics2D g = image.createGraphics();
    //noinspection UseJBColor
    g.setColor(new Color(0xfffbfcc6));
    g.fillRect(0, 0, width, height);

    g.dispose();

    boolean drawShadows = !myRenderContext.hasAlphaChannel();
    if (drawShadows) {
      ShadowPainter.drawSmallRectangleShadow(image, 0, 0, image.getWidth() - SMALL_SHADOW_SIZE, image.getHeight() - SMALL_SHADOW_SIZE);
    }

    myThumbnail = image;
  }

  private static double getScale(int width, int height) {
    int maxWidth = RenderPreviewManager.getMaxWidth();
    int maxHeight = RenderPreviewManager.getMaxHeight();
    if (width > 0 && height > 0 && (width > maxWidth || height > maxHeight)) {
      if (width >= height) { // landscape
        return maxWidth / (double)width;
      }
      else { // portrait
        return maxHeight / (double)height;
      }
    }

    return 1.0;
  }

  /**
   * Returns the width of the preview, in pixels
   *
   * @return the width in pixels
   */
  public int getWidth() {
    return (int)(myLayoutWidth * myScale * RenderPreviewManager.getScale());
  }

  /**
   * Returns the height of the preview, in pixels
   *
   * @return the height in pixels
   */
  public int getHeight() {
    return (int)(myLayoutHeight * myScale * RenderPreviewManager.getScale());
  }

  /**
   * Returns the <b>desired</b> width of this preview, in pixels.
   * Whereas {@link #getWidth()} returns the current width of the preview,
   * this method returns the desired with after the next render.
   * <p>
   * For example, let's say the orientation has just changed and an update has
   * been scheduled. During this interval, the width of the preview is the
   * old, un-rotated preview's width, whereas the layout width is the new
   * width after rotation has been applied.
   *
   * @return the layout width
   */
  public int getLayoutWidth() {
    return myLayoutWidth;
  }

  /**
   * Returns the <b>desired</b> height of this preview, in pixels.
   * See {@link #getLayoutWidth()} for details on how the layout height
   * is different from {@link #getHeight()}.
   */
  public int getLayoutHeight() {
    return myLayoutHeight;
  }

  /**
   * Handles clicks within the preview (x and y are positions relative within the
   * preview
   *
   * @param x the x coordinate within the preview where the click occurred
   * @param y the y coordinate within the preview where the click occurred
   * @return true if this preview handled (and therefore consumed) the click
   */
  public boolean click(int x, int y) {
    if (y >= myTitleHeight && y < myTitleHeight + HEADER_HEIGHT) {
      int left = 0;
      left += AllIcons.Actions.CloseHovered.getIconWidth();
      if (x <= left) {
        // Delete
        myManager.deletePreview(this);
        return true;
      }
      if (ZOOM_SUPPORT) {
        left += AndroidIcons.ZoomIn.getIconWidth();
        if (x <= left) {
          // Zoom in
          myScale *= (1 / 0.5);
          if (Math.abs(myScale - 1.0) < 0.0001) {
            myScale = 1.0;
          }

          myManager.scheduleRender(this, 0);
          myManager.layout(true);
          myManager.redraw();
          return true;
        }
        left += AndroidIcons.ZoomOut.getIconWidth();
        if (x <= left) {
          // Zoom out
          myScale *= (0.5 / 1);
          if (Math.abs(myScale - 1.0) < 0.0001) {
            myScale = 1.0;
          }
          myManager.scheduleRender(this, 0);

          myManager.layout(true);
          myManager.redraw();
          return true;
        }
      }
      left += AllIcons.Actions.Edit.getIconWidth();
      if (x <= left) {
        // Edit. For now, just rename
        Project project = myConfiguration.getConfigurationManager().getProject();
        String newName = Messages.showInputDialog(project, "Name:", "Rename Preview", null, myConfiguration.getDisplayName(), null);
        if (newName != null) {
          myConfiguration.setDisplayName(newName);
          myManager.redraw();
        }

        return true;
      }

      // Clicked anywhere else on header
      // Perhaps open Edit dialog here?
    }

    myManager.switchTo(this);
    return true;
  }

  /**
   * Paints the preview at the given x/y position
   *
   * @param gc the graphics context to paint it into
   * @param x  the x coordinate to paint the preview at
   * @param y  the y coordinate to paint the preview at
   */
  void paint(Graphics2D gc, int x, int y) {
    myTitleHeight = paintTitle(gc, x, y, true /*showFile*/);
    y += myTitleHeight;
    y += 2;

    Component component = myRenderContext.getComponent();
    gc.setFont(UIUtil.getToolTipFont());
    FontMetrics fontMetrics = gc.getFontMetrics();
    int fontHeight = fontMetrics.getHeight();
    int fontBaseline = fontHeight - fontMetrics.getDescent();

    int width = getWidth();
    int height = getHeight();
    BufferedImage thumbnail = getThumbnail();
    if (thumbnail != null && myError == null) {
      UIUtil.drawImage(gc, thumbnail, x, y, null);

      if (myActive) {
        // TODO: Can I figure out the actual frame bounds again?
        int x1 = x;
        int y1 = y;
        int w = myLayoutWidth;
        int h = myLayoutHeight;

        if (myThumbnailHasFrame && myViewBounds != null) {
          x1 = myViewBounds.x + x1;
          y1 = myViewBounds.y + y1;
          w = myViewBounds.width;
          h = myViewBounds.height;
        }

        //noinspection UseJBColor
        gc.setColor(new Color(181, 213, 255));
        Device device = myConfiguration.getDevice();
        if (device != null && device.isScreenRound()) {
          Stroke prevStroke = gc.getStroke();
          gc.setStroke(new BasicStroke(3.0f));
          Object prevAntiAlias = gc.getRenderingHint(KEY_ANTIALIASING);
          gc.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);
          Ellipse2D.Double ellipse = new Ellipse2D.Double(x1, y1, w, h);
          gc.draw(ellipse);
          gc.setStroke(prevStroke);
          gc.setRenderingHint(KEY_ANTIALIASING, prevAntiAlias);
        } else {
          gc.drawRect(x1 - 1, y1 - 1, w + 1, h + 1);
          gc.drawRect(x1 - 2, y1 - 2, w + 3, h + 3);
          gc.drawRect(x1 - 3, y1 - 3, w + 5, h + 5);
        }
      }
    }
    else if (myError != null && !myError.isEmpty()) {
      if (thumbnail != null) {
        UIUtil.drawImage(gc, thumbnail, x, y, null);
      }
      else {
        //noinspection UseJBColor
        gc.setColor(Color.DARK_GRAY);
        gc.drawRect(x, y, width, height);
      }

      Shape prevClip = gc.getClip();
      gc.setClip(x, y, width, height);
      Icon icon = AndroidIcons.RenderError;

      icon.paintIcon(component, gc, x + (width - icon.getIconWidth()) / 2, y + (height - icon.getIconHeight()) / 2);
      Composite prevComposite = gc.getComposite();
      gc.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
      //noinspection UseJBColor
      gc.setColor(Color.WHITE);
      gc.fillRect(x, y, width, height);
      gc.setComposite(prevComposite);

      String msg = myError;
      Density density = myConfiguration.getDensity();
      if (density == Density.TV || density == Density.LOW) {
        msg = "Broken rendering library; unsupported DPI. Try using the SDK manager " + "to get updated layout libraries.";
      }
      int charWidth = fontMetrics.charWidth('x');
      int charsPerLine = (width - 10) / charWidth;
      msg = SdkUtils.wrap(msg, charsPerLine, null);
      //noinspection UseJBColor
      gc.setColor(Color.BLACK);
      gc.setFont(UIUtil.getToolTipFont());
      UISettings.setupAntialiasing(gc);
      final UIUtil.TextPainter painter = new UIUtil.TextPainter().withShadow(true).withLineSpacing(1.4f);
      for (String line : msg.split("\n")) {
        painter.appendLine(line);
      }
      final int xf = x + 5;
      final int yf = y + HEADER_HEIGHT + fontBaseline;
      painter.draw(gc, new PairFunction<Integer, Integer, Couple<Integer>>() {
        @Override
        public Couple<Integer> fun(Integer width, Integer height) {
          return Couple.of(xf, yf);
        }
      });
      gc.setClip(prevClip);
    }
    else {
      //noinspection UseJBColor
      gc.setColor(Color.DARK_GRAY);
      gc.drawRect(x, y, width, height);
      Icon icon = AndroidIcons.RefreshPreview;
      Composite prevComposite = gc.getComposite();
      gc.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
      icon.paintIcon(component, gc, x + (width - icon.getIconWidth()) / 2, y + (height - icon.getIconHeight()) / 2);
      gc.setComposite(prevComposite);
    }

    if (myActive && !myShowFrame) {
      int left = x;

      Composite prevComposite = gc.getComposite();
      gc.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
      //noinspection UseJBColor
      gc.setColor(Color.WHITE);
      gc.fillRect(left, y, x + width - left, HEADER_HEIGHT);


      y += 2;

      // Paint icons
      AllIcons.Actions.CloseHovered.paintIcon(component, gc, left, y);
      left += AllIcons.Actions.CloseHovered.getIconWidth();

      if (ZOOM_SUPPORT) {
        AndroidIcons.ZoomIn.paintIcon(component, gc, left, y);
        left += AndroidIcons.ZoomIn.getIconWidth();

        AndroidIcons.ZoomOut.paintIcon(component, gc, left, y);
        left += AndroidIcons.ZoomOut.getIconWidth();
      }

      AllIcons.Actions.Edit.paintIcon(component, gc, left, y);
      left += AllIcons.Actions.Edit.getIconWidth();

      gc.setComposite(prevComposite);
    }
  }

  /**
   * Paints the preview title at the given position (and returns the required
   * height)
   *
   * @param gc the graphics context to paint into
   * @param x  the left edge of the preview rectangle
   * @param y  the top edge of the preview rectangle
   */
  private int paintTitle(Graphics2D gc, int x, int y, boolean showFile) {
    String displayName = getDisplayName();
    return paintTitle(gc, x, y, showFile, displayName);
  }

  /**
   * Paints the preview title at the given position (and returns the required
   * height)
   *
   * @param gc          the graphics context to paint into
   * @param x           the left edge of the preview rectangle
   * @param y           the top edge of the preview rectangle
   * @param displayName the title string to be used
   */
  int paintTitle(Graphics2D gc, int x, int y, boolean showFile, String displayName) {
    int titleHeight = 0;

    if (showFile && myIncludedWithin != null) {
      if (myManager.getMode() != RenderPreviewMode.INCLUDES) {
        displayName = "<include>";
      }
      else {
        // Skip: just paint footer instead
        displayName = null;
      }
    }

    int labelTop = y + 1;
    Shape prevClip = gc.getClip();
    Rectangle clipBounds = prevClip.getBounds();
    int clipWidth = myMaxWidth > 0 ? myMaxWidth : myLayoutWidth;
    gc.setClip(x, labelTop, Math.min(clipWidth, clipBounds.x + clipBounds.width - x),
               Math.min(100, clipBounds.y + clipBounds.height - labelTop));

    // Use font height rather than extent height since we want two adjacent
    // previews (which may have different display names and therefore end
    // up with slightly different extent heights) to have identical title
    // heights such that they are aligned identically
    gc.setFont(UIUtil.getToolTipFont());
    FontMetrics fontMetrics = gc.getFontMetrics();
    int fontHeight = fontMetrics.getHeight();
    int fontBaseline = fontHeight - fontMetrics.getDescent();

    if (displayName != null && displayName.length() > 0) {
      // Deliberately using Color.WHITE rather than JBColor.WHITE here: the background in the preview render
      // is always gray and does not vary by theme
      //noinspection UseJBColor
      gc.setColor(Color.WHITE);
      Rectangle2D extent = fontMetrics.getStringBounds(displayName, gc);
      int labelLeft = Math.max(x, x + (myLayoutWidth - (int)extent.getWidth()) / 2);
      Icon icon = null;
      Locale locale = myConfiguration.getLocale();
      if ((locale.hasLanguage() || locale.hasRegion()) &&
          (!(myConfiguration instanceof NestedConfiguration) || ((NestedConfiguration)myConfiguration).isOverridingLocale())) {
        icon = locale.getFlagImage();
      }

      if (icon != null) {
        int flagWidth = icon.getIconWidth();
        int flagHeight = icon.getIconHeight();
        labelLeft = Math.max(x + flagWidth / 2, labelLeft);
        icon.paintIcon(myRenderContext.getComponent(), gc, labelLeft - flagWidth / 2 - 1, labelTop + (fontHeight - flagHeight) / 2);
        labelLeft += flagWidth / 2 + 1;
        gc.drawString(displayName, labelLeft, labelTop - (fontHeight - flagHeight) / 2 + fontBaseline);
      }
      else {
        gc.drawString(displayName, labelLeft, labelTop + fontBaseline);
      }

      labelTop += (int)extent.getHeight();
      titleHeight += fontHeight;
    }

    if (showFile && (myAlternateInput != null || myIncludedWithin != null)) {
      // Draw file flag, and parent folder name
      VirtualFile file = myAlternateInput != null ? myAlternateInput : myIncludedWithin.getFromFile();
      String fileName = file.getParent().getName() + File.separator + file.getName();
      Rectangle2D extent = fontMetrics.getStringBounds(fileName, gc);
      Icon icon = AllIcons.FileTypes.Xml;
      int iconWidth = icon.getIconWidth();
      int iconHeight = icon.getIconHeight();

      int labelLeft = Math.max(x, x + (myLayoutWidth - (int)extent.getWidth() - iconWidth - 1) / 2);
      icon.paintIcon(myRenderContext.getComponent(), gc, labelLeft, labelTop);

      // Deliberately using Color.DARK_GRAY rather than JBColor.GRAY here: the background in the preview render
      // is always gray and does not vary by theme
      //noinspection UseJBColor
      gc.setColor(Color.DARK_GRAY);
      labelLeft += iconWidth + 1;
      labelTop -= ((int)extent.getHeight() - iconHeight) / 2;
      gc.drawString(fileName, labelLeft, labelTop + fontBaseline);

      titleHeight += Math.max(titleHeight, icon.getIconHeight());
    }

    gc.setClip(prevClip);

    return titleHeight;
  }

  /**
   * Notifies that the preview's configuration has changed.
   *
   * @param flags the change flags, a bitmask corresponding to the
   *              {@code CHANGE_} constants in {@link ConfigurationListener}
   */
  public void configurationChanged(int flags) {
    if (!myVisible) {
      myDirty |= flags;
      return;
    }
    if ((flags & MASK_RENDERING) != 0) {
      updateForkStatus();
    }

    // Sanity check to make sure things are working correctly
    if (DEBUG) {
      RenderPreviewMode mode = myManager.getMode();
      Configuration configuration = myRenderContext.getConfiguration();
      if (mode == RenderPreviewMode.DEFAULT) {
        assert myConfiguration instanceof VaryingConfiguration;
        VaryingConfiguration config = (VaryingConfiguration)myConfiguration;
        int alternateFlags = config.getAlternateFlags();
        switch (alternateFlags) {
          case ConfigurationListener.CFG_DEVICE_STATE: {
            State configState = config.getDeviceState();
            State chooserState = configuration.getDeviceState();
            assert configState != null && chooserState != null;
            assert !configState.getName().equals(chooserState.getName()) : configState.toString() + ':' + chooserState;

            Device configDevice = config.getDevice();
            Device chooserDevice = configuration.getDevice();
            assert configDevice != null && chooserDevice != null;
            assert configDevice == chooserDevice : configDevice.toString() + ':' + chooserDevice;

            break;
          }
          case ConfigurationListener.CFG_DEVICE: {
            Device configDevice = config.getDevice();
            Device chooserDevice = configuration.getDevice();
            assert configDevice != null && chooserDevice != null;
            assert configDevice != chooserDevice : configDevice.toString() + ':' + chooserDevice;

            State configState = config.getDeviceState();
            State chooserState = configuration.getDeviceState();
            assert configState != null && chooserState != null;
            assert configState.getName().equals(chooserState.getName()) : configState.toString() + ':' + chooserState;

            break;
          }
          case ConfigurationListener.CFG_LOCALE: {
            Locale configLocale = config.getLocale();
            Locale chooserLocale = configuration.getLocale();
            assert configLocale != null && chooserLocale != null;
            assert configLocale != chooserLocale : configLocale.toString() + ':' + chooserLocale;
            break;
          }
          default: {
            // Some other type of override I didn't anticipate
            assert false : alternateFlags;
          }
        }
      }
    }

    myDirty = 0;
    myManager.scheduleRender(this);
  }

  /**
   * Returns the configuration associated with this preview
   *
   * @return the configuration
   */
  @NotNull
  public Configuration getConfiguration() {
    return myConfiguration;
  }

  /**
   * Sets the input file to use for rendering. If not set, this will just be
   * the same file as the configuration chooser. This is used to render other
   * layouts, such as variations of the currently edited layout, which are
   * not kept in sync with the main layout.
   *
   * @param file the file to set as input
   */
  public void setAlternateInput(@Nullable VirtualFile file) {
    myAlternateInput = file;
  }

  @Override
  public String toString() {
    return getDisplayName() + ':' + myConfiguration;
  }

  /**
   * Sorts render previews into increasing aspect ratio order
   */
  static Comparator<RenderPreview> INCREASING_ASPECT_RATIO = new Comparator<RenderPreview>() {
    @Override
    public int compare(RenderPreview preview1, RenderPreview preview2) {
      return (int)Math.signum(preview1.myAspectRatio - preview2.myAspectRatio);
    }
  };

  /**
   * Sorts render previews into decreasing aspect ratio order
   */
  static Comparator<RenderPreview> DECREASING_ASPECT_RATIO = new Comparator<RenderPreview>() {
    @Override
    public int compare(RenderPreview preview1, RenderPreview preview2) {
      return (int)Math.signum(preview2.myAspectRatio - preview1.myAspectRatio);
    }
  };

  /**
   * Sorts render previews into visual order: row by row, column by column
   */
  static Comparator<RenderPreview> VISUAL_ORDER = new Comparator<RenderPreview>() {
    @Override
    public int compare(RenderPreview preview1, RenderPreview preview2) {
      int delta = preview1.myY - preview2.myY;
      if (delta == 0) {
        delta = preview1.myX - preview2.myX;
      }
      return delta;
    }
  };

  private int myMaxWidth;
  private int myMaxHeight;

  public void setMaxSize(int width, int height) {
    myMaxWidth = width;
    myMaxHeight = height;

    if (width == 0 || height == 0) {
      computeInitialSize();
    } else {
      double scale = Math.min(1, Math.min(width / (double)myFullWidth, (height - RenderPreviewManager.TITLE_HEIGHT) / (double)myFullHeight));
      myLayoutWidth = (int)(myFullWidth * scale);
      myLayoutHeight = (int)(myFullHeight * scale);
    }

    if (myThumbnail != null && (Math.abs(myLayoutWidth - myThumbnail.getWidth() /
                                                      // No, only for scalable image!
                                                        /* (ImageUtils.isRetinaImage(myThumbnail) ? 2 :*/ 1/*)*/) > 1)) {
      // Note that we null out myThumbnail, we *don't* call disposeThumbnail because we
      // want to reuse the large rendering and just scale it down again
      myThumbnail = null;
    }
  }

  @Nullable
  public String getId() {
    return myId;
  }

  public void setId(@Nullable String id) {
    myId = id;
  }

  public int getMaxWidth() {
    return myMaxWidth;
  }

  public int getMaxHeight() {
    return myMaxHeight;
  }

  /** Returns the current pending rendering request, if any */
  @Nullable
  public Runnable getPendingRendering() {
    return myPendingRendering;
  }

  /** Sets or clears the current pending rendering request */
  public void setPendingRendering(@Nullable Runnable pendingRendering) {
    myPendingRendering = pendingRendering;
  }

  public boolean isShowFrame() {
    return myShowFrame;
  }

  public void setShowFrame(boolean showFrame) {
    myShowFrame = showFrame;
  }
}
