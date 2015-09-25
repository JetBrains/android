/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.gfxtrace.actions;

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.editors.gfxtrace.GfxTracer;
import com.android.tools.idea.monitor.render.RenderMonitorView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class GfxTraceCaptureAction extends ToggleAction {
  @NotNull protected final RenderMonitorView myView;
  private GfxTracer myActive = null;

  public static class Listen extends GfxTraceCaptureAction {
    public Listen(@NotNull RenderMonitorView view) {
      super(view, "Listen", "Listen for GFX traces", AndroidIcons.Ddms.StartMethodProfiling);
    }

    @Override
    boolean isEnabled() {
      return myView.getDeviceContext().getSelectedDevice() != null;
    }

    @Override
    GfxTracer start() {
      final IDevice device = myView.getDeviceContext().getSelectedDevice();
      if (device == null) {
        return null;
      }
      GfxTracer.Options options = new GfxTracer.Options(false, false);
      return GfxTracer.listen(myView.getProject(), device, options, myView.getEvents());
    }
  }

  public static class Relaunch extends GfxTraceCaptureAction {
    public Relaunch(@NotNull RenderMonitorView view) {
      super(view, "Launch", "Launch in GFX trace mode", AndroidIcons.Ddms.Threads);
    }

    @Override
    boolean isEnabled() {
      return myView.getDeviceContext().getSelectedClient() != null;
    }

    @Override
    GfxTracer start() {
      final Client client = myView.getDeviceContext().getSelectedClient();
      if (client == null) {
        return null;
      }
      GfxTracer.Options options = new GfxTracer.Options(false, false);
      return GfxTracer.launch(myView.getProject(), client, options, myView.getEvents());
    }
  }

  public GfxTraceCaptureAction(@NotNull RenderMonitorView view,
                               @Nullable final String text,
                               @Nullable final String description,
                               @Nullable final Icon icon) {
    super(text, description, icon);
    myView = view;
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return myActive != null;
  }

  @Override
  public final void setSelected(AnActionEvent e, boolean state) {
    if (myActive == null) {
      myView.setPaused(false);
      myActive = start();
    }
    else {
      myActive.stop();
      myActive = null;
    }
  }

  @Override
  public final void update(AnActionEvent e) {
    super.update(e);
    Presentation presentation = e.getPresentation();
    if (myActive == null) {
      presentation.setEnabled(isEnabled());
      presentation.setText("Start tracing");
    }
    else {
      presentation.setEnabled(true);
      presentation.setText("Stop tracing");
    }
  }

  abstract boolean isEnabled();

  abstract GfxTracer start();
}
