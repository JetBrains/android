/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android;

import com.android.tools.idea.templates.TemplateManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;

/**
 * @author coyote
 */
public class AndroidPlugin implements ApplicationComponent {

  @Override
  @NotNull
  public String getComponentName() {
    return "AndroidApplicationComponent";
  }

  @Override
  public void initComponent() {
    createDynamicTemplateMenu();
  }

  public static void createDynamicTemplateMenu() {
    DefaultActionGroup newGroup = (DefaultActionGroup)ActionManager.getInstance().getAction("NewGroup");
    newGroup.addSeparator();
    final ActionGroup menu = TemplateManager.getInstance().getTemplateCreationMenu();

    if (menu != null) {
      newGroup.add(menu, new Constraints(Anchor.AFTER, "NewFromTemplate"));
    }
  }

  @Override
  public void disposeComponent() {
    AndroidSdkData.terminateDdmlib();
  }
}
