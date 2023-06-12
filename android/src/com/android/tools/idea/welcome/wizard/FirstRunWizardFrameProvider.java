/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.welcome.wizard;

import static com.intellij.util.ui.update.UiNotifyConnector.doWhenFirstShown;

import com.android.tools.idea.welcome.wizard.deprecated.FirstRunWizard;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WelcomeFrameProvider;
import com.intellij.openapi.wm.WelcomeScreenProvider;
import com.intellij.openapi.wm.impl.welcomeScreen.FlatWelcomeFrame;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.ui.ScreenUtil;
import java.awt.Rectangle;
import org.jetbrains.annotations.Nullable;

/**
 * {@link WelcomeFrameProvider} for the {@link FirstRunWizard}.
 */
final class FirstRunWizardFrameProvider implements WelcomeFrameProvider {
  @Override
  public @Nullable IdeFrame createFrame() {
    WelcomeFrame frame = WelcomeScreenProvider.EP_NAME.computeSafeIfAny(provider -> {
      if (provider instanceof AndroidStudioWelcomeScreenProvider && provider.isAvailable()) {
        // If we need to show the first run wizard, return a normal WelcomeFrame (which will initialize the wizard via the
        // WelcomeScreenProvider extension point).
        return new WelcomeFrame();
      }
      return null;
    });
    return frame != null ? frame : customizeFlatWelcomeFrame();
  }

  /**
   * Customizes the platform {@link FlatWelcomeFrame} so that it is resizable
   * and fits the screen.
   * <p>Note that this behavior is specific to Android Studio, as there are more
   * actions displayed in the middle panel, making the whole welcome frame
   * too big on low resolution screen with HiDPI. See
   * <a href="https://issuetracker.google.com/issues/68295805">bug 68295805</a>.
   */
  private @Nullable IdeFrame customizeFlatWelcomeFrame() {
    return WelcomeFrame.EP.computeSafeIfAny(provider -> {
      if (provider == this) {
        // Avoid infinite recursion, since we are one of the providers.
        return null;
      }

      IdeFrame frame = provider.createFrame();
      if (frame == null) {
        return null;
      }

      // Customize if FlatWelcomeFrame
      if (frame instanceof FlatWelcomeFrame) {
        FlatWelcomeFrame welcomeFrame = (FlatWelcomeFrame)frame;
        doWhenFirstShown(welcomeFrame, () -> {
          Logger.getInstance(this.getClass()).info("Overriding welcome frame to be resizable");
          welcomeFrame.setResizable(true);
          Rectangle newBounds = welcomeFrame.getBounds();
          ScreenUtil.fitToScreen(newBounds);
          welcomeFrame.setBounds(newBounds);
        });
      }
      // Always return the first available frame
      return frame;
    });
  }
}
