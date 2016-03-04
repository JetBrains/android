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
package org.jetbrains.android.intentions;

import com.android.SdkConstants;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.android.dom.manifest.Application;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.manifest.Receiver;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * For inheritor of android.content.BroadcastReceiver which isn't registered in the AndroidManifest.xml file,
 * add an intention action to register it.
 */
public class AddBroadcastReceiverToManifest extends AbstractRegisterComponentAction {
  @Nls
  @NotNull
  @Override
  public String getText() {
    return "Add broadcast receiver to manifest";
  }

  @Override
  boolean isAvailable(@NotNull PsiClass psiClass, @NotNull Manifest manifest) {
    // Check whether the class is a subclass of BroadcastReceiver
    if (!(InheritanceUtil.isInheritor(psiClass, true, SdkConstants.CLASS_BROADCASTRECEIVER))) {
      return false;
    }

    // Check whether this Activity is already registered
    return !isRegisteredBroadcastReceiver(psiClass, manifest.getApplication());
  }

  @Override
  void invoke(@NotNull PsiClass psiClass, @NotNull Manifest manifest) {
    final Application application = manifest.getApplication();
    application.addReceiver().getReceiverClass().setValue(psiClass);
  }

  private static boolean isRegisteredBroadcastReceiver(PsiClass psiClass, Application application) {
    for (Receiver receiver : application.getReceivers()) {
      if (psiClass.isEquivalentTo(receiver.getReceiverClass().getValue())) {
        // Found existing broadcast receiver with the same class
        return true;
      }
    }
    return false;
  }
}
