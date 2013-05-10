/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.android.uipreview;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
@State(
  name = "AndroidLayoutPreviewToolWindow",
  storages = {
    @Storage(id = "AndroidLayoutPreviewToolWindow", file = StoragePathMacros.WORKSPACE_FILE)
  }
)
public class AndroidLayoutPreviewToolWindowSettings implements PersistentStateComponent<AndroidLayoutPreviewToolWindowSettings.MyState> {
  private GlobalState myGlobalState = new GlobalState();

  public static AndroidLayoutPreviewToolWindowSettings getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, AndroidLayoutPreviewToolWindowSettings.class);
  }
  
  @NotNull
  public GlobalState getGlobalState() {
    return myGlobalState;
  }
  
  @Override
  public MyState getState() {
    final MyState state = new MyState();
    state.setState(myGlobalState);
    return state;
  }

  @Override
  public void loadState(MyState state) {
    myGlobalState = state.getState();
  }

  public static class MyState {
    private GlobalState myGlobalState = new GlobalState();

    public GlobalState getState() {
      return myGlobalState;
    }

    public void setState(GlobalState state) {
      myGlobalState = state;
    }
  }

  public static class GlobalState {
    private boolean myVisible = true;
    private boolean myHideForNonLayoutFiles = true;
    private boolean myShowDeviceFrames = true;
    private boolean myShowEffects = true;

    public boolean isVisible() {
      return myVisible;
    }

    public void setVisible(boolean visible) {
      myVisible = visible;
    }

    public boolean isHideForNonLayoutFiles() {
      return myHideForNonLayoutFiles;
    }

    public void setHideForNonLayoutFiles(boolean hideForNonLayoutFiles) {
      myHideForNonLayoutFiles = hideForNonLayoutFiles;
    }

    public boolean isShowDeviceFrames() {
      return myShowDeviceFrames;
    }

    public void setShowDeviceFrames(boolean showDeviceFrames) {
      myShowDeviceFrames = showDeviceFrames;
    }

    public boolean isShowEffects() {
      return myShowEffects;
    }

    public void setShowEffects(boolean showEffects) {
      myShowEffects = showEffects;
    }
  }
}
