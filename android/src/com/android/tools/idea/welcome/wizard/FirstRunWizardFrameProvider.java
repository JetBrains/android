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

import com.android.tools.idea.welcome.wizard.deprecated.FirstRunWizard;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WelcomeFrameProvider;
import com.intellij.openapi.wm.WelcomeScreenProvider;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;

/**
 * {@link WelcomeFrameProvider} for the {@link FirstRunWizard}.
 */
public class FirstRunWizardFrameProvider implements WelcomeFrameProvider {
  @Override
  public IdeFrame createFrame() {
    return WelcomeScreenProvider.EP_NAME.computeSafeIfAny(provider -> {
      if (provider instanceof AndroidStudioWelcomeScreenProvider && provider.isAvailable()) {
        // If we need to show the first run wizard, return an old WelcomeFrame (which will initialize the wizard via the
        // WelcomeScreenProvider extension point).
        return new WelcomeFrame();
      }
      return null;
    });
  }
}
