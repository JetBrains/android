/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.run;

import com.intellij.execution.ExecutionException;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.ui.BrowserHyperlinkListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import org.jetbrains.annotations.NotNull;

/**
 * An {@link ExecutionException} containing a clickable browser hyperlink. It attempts to navigate
 * to a URL formed from the hyperlink description verbatim.
 */
public class WithBrowserHyperlinkExecutionException extends ExecutionException
    implements HyperlinkListener, NotificationListener {

  public WithBrowserHyperlinkExecutionException(String string) {
    super(string);
  }

  @Override
  public final void hyperlinkUpdate(HyperlinkEvent e) {
    if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
      BrowserHyperlinkListener.INSTANCE.hyperlinkUpdate(e);
    }
  }

  @Override
  public final void hyperlinkUpdate(
      @NotNull Notification notification, @NotNull HyperlinkEvent event) {
    hyperlinkUpdate(event);
  }
}
