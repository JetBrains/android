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
package com.android.tools.idea.gradle.structure.configurables.messages;

import com.android.tools.idea.gradle.structure.configurables.AbstractCounterDisplayConfigurable;
import com.android.tools.idea.gradle.structure.configurables.PsContext;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.intellij.util.ui.UIUtil.invokeLaterIfNeeded;

public class MessagesConfigurable extends AbstractCounterDisplayConfigurable {
  @NotNull private final MessagesForm myMessagesForm;
  private int myMessageCount;

  public MessagesConfigurable(@NotNull PsContext context) {
    super(context);

    myMessagesForm = new MessagesForm(context);
    add(myMessagesForm.getPanel(), BorderLayout.CENTER);

    myMessagesForm.renderIssues();

    getContext().getAnalyzerDaemon().add(model -> {
      fireCountChangeListener();
      invokeLaterIfNeeded(() -> {
        myMessagesForm.renderIssues();
        myMessageCount = myMessagesForm.getMessageCount();
      });
    }, this);
  }

  @Override
  public int getCount() {
    return myMessageCount;
  }

  @Override
  @Nls
  public String getDisplayName() {
    return "Messages";
  }
}
