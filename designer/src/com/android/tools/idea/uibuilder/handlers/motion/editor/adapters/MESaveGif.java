/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.editor.adapters;

import com.android.tools.idea.common.model.ModelListener;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.common.surface.SceneViewPanel;
import com.android.tools.idea.uibuilder.handlers.motion.MotionLayoutComponentHelper;
import com.android.tools.idea.uibuilder.handlers.motion.editor.utils.GifWriter;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.WaitFor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Locale;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

/**
 * handle Save a selected transition as a GIF
 */
public class MESaveGif {

  GifWriter mWriter;
  private AtomicBoolean isRenderCompleted = new AtomicBoolean(false);

  private final ModelListener myModelListener = new ModelListener() {
    @Override
    public void modelChangedOnLayout(@NotNull NlModel model, boolean animate) {
      isRenderCompleted.set(true);
    }
  };

  public MESaveGif(File file, int timeBetweenFramesMS, boolean loopContinuously, String comment) {
    mWriter = new GifWriter(file, timeBetweenFramesMS, loopContinuously, comment);
  }

  /**
   * Capture a series of images that can represent a selected transition and save them into a gif file.
   * yoyoMode == 0: forward only
   * yoyoMode == 1: backward only
   * yoyoMode == 2: both forward and backward
   * @param designSurface associated designSurface
   * @param numSavedImages number of images to be saved
   * @param yoyoMode type of yoyoMode
   * @param helper MotionLayoutComponentHelper
   * @param myProject An object representing an IntelliJ project.
   * @param waitForTime timeout for WaitFor
   */
  public void saveGif(NlDesignSurface designSurface, int numSavedImages, int yoyoMode, MotionLayoutComponentHelper helper,
                             Project myProject, int waitForTime) {

    designSurface.getModels().get(0).addListener(myModelListener);
    new Task.Modal(myProject, "Saving Gif", false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setText(String.format(Locale.US, "saving %d / %d frames", 0, numSavedImages));
        indicator.setIndeterminate(false);

        JComponent pane = designSurface.getInteractionPane();
        if (pane instanceof SceneViewPanel) {
          int size = yoyoMode == 2 ? numSavedImages / 2 : numSavedImages;
          int margin = 50;
          SceneViewPanel mySceneViewPanel = (SceneViewPanel)pane;
          int panelWidth = mySceneViewPanel.getWidth();
          int panelHeight = mySceneViewPanel.getHeight();

          SceneView view = designSurface.getFocusedSceneView();
          Dimension dim = view.getContentSize(null);
          double viewScale = view.getScale();
          // limit the maximum scale to 100% for now (tentative)
          double maxScale = 1.0;
          boolean shouldTranslate = maxScale >= viewScale;
          if (viewScale < maxScale) {
            maxScale = viewScale;
          }

          int width = (int)(dim.getWidth() * maxScale) + margin; // the width of the image to be captured
          int height = (int)(dim.getHeight() * maxScale) + margin; // the height of the image to be captured
          double dScale = maxScale / viewScale;
          int translateX = -(int)(panelWidth - width)  / 2;
          int translateY = -(int)(panelHeight - height) / 2;

          try {
            String progressText = "Saving %d / %d frames";
            float step = 1f / size;
            boolean isFirstPass = true;
            for (int i = 0; i < size; i++) {
              int frame = i;

              saveImage(indicator, size, mySceneViewPanel, shouldTranslate, width, height, dScale, translateX, translateY, progressText,
                        step, frame, numSavedImages, yoyoMode, helper, waitForTime, isFirstPass);
            }
            isFirstPass = false;
            if (yoyoMode == 2) {
              for (int j = size; j < numSavedImages; j++) {
                int frame = j;

                saveImage(indicator, size, mySceneViewPanel, shouldTranslate, width, height, dScale, translateX, translateY, progressText,
                          step, frame, numSavedImages, yoyoMode, helper, waitForTime, isFirstPass);
              }
            }
            indicator.setText("Saving Gif file...");
            mWriter.close();
            // resume the status to the original state
            helper.setProgress(0);
          }
          catch (IOException e) {
            e.printStackTrace();
            ApplicationManager.getApplication().invokeLater(
              () -> Messages.showErrorDialog(e.getMessage(), "Failed to save gif file")
            );
          }
        }
      }
    }.queue();
  }

  private void saveImage(@NotNull ProgressIndicator indicator,
                         int size,
                         SceneViewPanel mySceneViewPanel,
                         boolean shouldTranslate,
                         int width,
                         int height,
                         double dScale,
                         int translateX,
                         int translateY,
                         String progressText,
                         float step,
                         int frame,
                         int numSavedImages,
                         int yoyoMode,
                         MotionLayoutComponentHelper helper,
                         int waitForTime,
                         boolean isFirstPass) {
    isRenderCompleted.set(false);

    ApplicationManager.getApplication().invokeAndWait(() -> {
      indicator.setText(String.format(Locale.US, progressText, (frame + 1), numSavedImages));
      indicator.setFraction(1f * (frame + 1) / numSavedImages);
      BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      Graphics2D g2d = img.createGraphics();
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      if (shouldTranslate) {
        g2d.translate(translateX, translateY);
      }
      g2d.scale(dScale, dScale);

      if (yoyoMode == 0 || (isFirstPass && yoyoMode == 2)) {
        // set progress forward
        helper.setProgress(step * frame);
      } else if (yoyoMode == 1) {
        // set progress backward
        helper.setProgress(step * (size - frame));
      } else {
        // set progres backward after the forward pass
        helper.setProgress(step * (size - (frame + 1 - size)));
      }

      // wait for the rendering to be completed
      new WaitFor(waitForTime) {
        @Override
        protected boolean condition() {
          return isRenderCompleted.get();
        }
      };

      mySceneViewPanel.paint(g2d);
      try {
        mWriter.addImage(img);
      }
      catch (IOException e) {
        e.printStackTrace();
      }
    });
  }
}
