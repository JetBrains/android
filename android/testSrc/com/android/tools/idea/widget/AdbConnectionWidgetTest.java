/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.widget;

import static com.google.common.truth.Truth.assertThat;

import com.intellij.openapi.actionSystem.TimerListener;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.StatusBar;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@RunWith(JUnit4.class)
public class AdbConnectionWidgetTest {
  @Test
  public void testStudioManaged() {
    StubStudioAdapter adapter = new StubStudioAdapter(false);

    // Ensure we're in the correct initial state.
    assert !adapter.myIsUserManaged;
    assert !adapter.myIsConnected;
    assert adapter.myTimerListener != null;
    assertThat(adapter.myLastIcon).isSameAs(AdbConnectionWidget.ConnectionState.STUDIO_MANAGED_DISCONNECTED.myIcon);

    // Simulate ADB is connected.
    adapter.myIsConnected = true;
    adapter.myTimerListener.run(); // "Advance" the clock.
    assertThat(adapter.myLastIcon).isSameAs(AdbConnectionWidget.ConnectionState.STUDIO_MANAGED_CONNECTED.myIcon);

    // Simulate disconnecting ADB.
    adapter.myIsConnected = false;
    adapter.myTimerListener.run(); // "Advance" the clock.
    assertThat(adapter.myLastIcon).isSameAs(AdbConnectionWidget.ConnectionState.STUDIO_MANAGED_DISCONNECTED.myIcon);

    Disposer.dispose(adapter);
  }

  @Test
  public void testUserManaged() {
    StubStudioAdapter adapter = new StubStudioAdapter(true);

    // Ensure we're in the correct initial state.
    assert !adapter.myIsConnected;
    assert adapter.myTimerListener != null;
    assertThat(adapter.myLastIcon).isSameAs(AdbConnectionWidget.ConnectionState.USER_MANAGED_DISCONNECTED.myIcon);

    // Simulate ADB is connected.
    adapter.myIsConnected = true;
    adapter.myTimerListener.run(); // "Advance" the clock.
    assertThat(adapter.myLastIcon).isSameAs(AdbConnectionWidget.ConnectionState.USER_MANAGED_CONNECTED.myIcon);

    // Simulate disconnecting ADB.
    adapter.myIsConnected = false;
    adapter.myTimerListener.run(); // "Advance" the clock.
    assertThat(adapter.myLastIcon).isSameAs(AdbConnectionWidget.ConnectionState.USER_MANAGED_DISCONNECTED.myIcon);

    Disposer.dispose(adapter);
  }

  private static final class StubStudioAdapter implements AdbConnectionWidget.StudioAdapter {
    public boolean myIsConnected;
    public boolean myIsUserManaged;
    public final StatusBar myStatusBar = Mockito.mock(StatusBar.class);
    public Icon myLastIcon;
    public TimerListener myTimerListener;

    private final AdbConnectionWidget myWidget;

    private StubStudioAdapter(boolean isUserManaged) {
      myIsUserManaged = isUserManaged;
      myWidget = new AdbConnectionWidget(this);
      Mockito.doAnswer(new Answer<Void>() {
        @Override
        @Nullable
        public Void answer(@NotNull InvocationOnMock invocation) {
          myLastIcon = myWidget.getIcon();
          return null;
        }
      }).when(myStatusBar).updateWidget(AdbConnectionWidget.ID);
      myLastIcon = myWidget.getIcon();
    }

    @Override
    public boolean isBridgeConnected() {
      return myIsConnected;
    }

    @Override
    public boolean isBridgeInUserManagedMode() {
      return myIsUserManaged;
    }

    @Nullable
    @Override
    public StatusBar getVisibleStatusBar() {
      return myStatusBar;
    }

    @Override
    public void setOnUpdate(@NotNull Runnable update) {
      myTimerListener = new TimerListener() {
        @NotNull
        @Override
        public ModalityState getModalityState() {
          return ModalityState.defaultModalityState();
        }

        @Override
        public void run() {
          update.run();
        }
      };
    }

    @Override
    public void dispose() {
      myTimerListener = null;
      Disposer.dispose(myWidget);
    }
  }
}
