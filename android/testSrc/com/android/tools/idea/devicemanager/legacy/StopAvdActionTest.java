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
package com.android.tools.idea.devicemanager.legacy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.idea.avdmanager.AvdUiAction.AvdInfoProvider;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.Action;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class StopAvdActionTest {
  @Test
  public void addPropertyChangeListener() {
    // Arrange
    AvdInfoProvider provider = Mockito.mock(AvdInfoProvider.class);
    Action action = new StopAvdAction(provider, false, p -> Futures.immediateFuture(false), MoreExecutors.directExecutor());

    PropertyChangeListener listener = Mockito.mock(PropertyChangeListener.class);

    // Act
    action.addPropertyChangeListener(listener);

    // Assert
    assertFalse(action.isEnabled());

    ArgumentCaptor<PropertyChangeEvent> captor = ArgumentCaptor.forClass(PropertyChangeEvent.class);

    Mockito.verify(listener).propertyChange(captor.capture());

    PropertyChangeEvent event = captor.getValue();
    assertEquals(action, event.getSource());
    assertEquals("enabled", event.getPropertyName());
    assertEquals(true, event.getOldValue());
    assertEquals(false, event.getNewValue());
  }
}
