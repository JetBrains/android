/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome.wizard.deprecated;

import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.lang.ref.WeakReference;

/**
 * Substitutes Idea listener that terminates the app when window closes.
 * @deprecated See {@link com.android.tools.idea.welcome.wizard.StudioFirstRunWelcomeScreen.DelegatingListener}
 */
@Deprecated
public final class WelcomeScreenWindowListener {
  public static WindowListener install(@NotNull JFrame frame, @NotNull FirstRunWizardHost host) {
    WindowListener ideaListener = removeCloseListener(frame);
    // This code is a hack to replace IntelliJ window listener with ours.
    // That listener is an instance of anonymous class and our hack will stop working if
    // that class is removed or renamed. "DirectListener" is a fallback in case we don't
    // find an original listener.
    WindowListener ourListener = ideaListener != null ? new DelegatingListener(host, ideaListener) : new DirectListener(host);
    frame.addWindowListener(ourListener);
    return ourListener;
  }

  /**
   * Remove the listener that causes application to quit if the user closes the welcome frame.
   * <p/>
   * I was unable to find proper API in IntelliJ to do this without forking quite a few classes.
   */
  @Nullable
  private static WindowListener removeCloseListener(@NotNull JFrame frame) {
    WindowListener[] listeners = frame.getListeners(WindowListener.class);
    for (WindowListener listener : listeners) {
      // The listener in question is an anonymous class nested in WelcomeFrame
      if (listener.getClass().getName().startsWith(WelcomeFrame.class.getName())) {
        frame.removeWindowListener(listener);
        return listener;
      }
    }
    return null;
  }

  @Contract("null->false")
  private static boolean handleClose(@Nullable FirstRunWizardHost host) {
    if (host != null && host.isActive()) {
      host.cancel();
      return true;
    } else {
      return false;
    }
  }

  /**
   * This code is needed to avoid breaking IntelliJ native event processing.
   */
  private static class DelegatingListener extends DirectListener {
    @NotNull private final WindowListener myIdeaListener;

    public DelegatingListener(@NotNull FirstRunWizardHost host, @NotNull WindowListener ideaListener) {
      super(host);
      myIdeaListener = ideaListener;
    }

    @Override
    public void windowOpened(WindowEvent e) {
      myIdeaListener.windowOpened(e);
    }

    @Override
    public void windowClosed(WindowEvent e) {
      myIdeaListener.windowClosed(e);
    }

    @Override
    public void windowIconified(WindowEvent e) {
      myIdeaListener.windowIconified(e);
    }

    @Override
    public void windowClosing(WindowEvent e) {
      if (!handleClose(getHost())) {
        Window window = e.getWindow();
        window.removeWindowListener(this);
        window.addWindowListener(myIdeaListener);
        myIdeaListener.windowClosing(e);
      }
    }

    @Override
    public void windowDeiconified(WindowEvent e) {
      myIdeaListener.windowDeiconified(e);
    }

    @Override
    public void windowActivated(WindowEvent e) {
      myIdeaListener.windowActivated(e);
    }

    @Override
    public void windowDeactivated(WindowEvent e) {
      myIdeaListener.windowDeactivated(e);
    }
  }

  private static class DirectListener extends WindowAdapter {
    private final WeakReference<FirstRunWizardHost> myHostReference;

    public DirectListener(@NotNull FirstRunWizardHost host) {
      // Let the instance leave
      myHostReference = new WeakReference<FirstRunWizardHost>(host);
    }

    @Nullable
    protected final FirstRunWizardHost getHost() {
      return myHostReference.get();
    }

    @Override
    public void windowClosing(WindowEvent e) {
      if (!handleClose(getHost())) {
        e.getWindow().removeWindowListener(this);
      }
    }
  }
}
