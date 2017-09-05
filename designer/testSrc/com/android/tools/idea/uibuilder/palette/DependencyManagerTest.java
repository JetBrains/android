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
package com.android.tools.idea.uibuilder.palette;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.common.model.NlLayoutType;
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Disposer;
import icons.StudioIcons;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.xml.ws.Holder;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.android.SdkConstants.*;
import static com.google.common.truth.Truth.assertThat;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class DependencyManagerTest extends AndroidTestCase {
  private JComponent myPanel;
  private Disposable myDisposable;
  private Palette myPalette;
  private DependencyManager myManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    GradleDependencyManager gradleDependencyManager = mock(GradleDependencyManager.class);
    registerProjectComponent(GradleDependencyManager.class, gradleDependencyManager);
    myPanel = mock(JComponent.class);
    myDisposable = mock(Disposable.class);
    myPalette = NlPaletteModel.get(myFacet).getPalette(NlLayoutType.LAYOUT);
    //noinspection ConstantConditions
    when(gradleDependencyManager.findMissingDependencies(any(Module.class), any()))
      .thenReturn(createDependencies(DESIGN_LIB_ARTIFACT, RECYCLER_VIEW_LIB_ARTIFACT, CARD_VIEW_LIB_ARTIFACT))
      .thenReturn(createDependencies(RECYCLER_VIEW_LIB_ARTIFACT))
      .thenThrow(new RuntimeException("Unexpected call to findDependencies"));
    myManager = new DependencyManager(getProject(), myPanel, myDisposable);
    myManager.setPalette(myPalette, myModule);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Disposer.dispose(myDisposable);
      // Null out all fields, since otherwise they're retained for the lifetime of the suite (which can be long if e.g. you're running many
      // tests through IJ)
      myPalette = null;
      myPanel = null;
      myManager = null;
      myDisposable = null;
    }
    finally {
      super.tearDown();
    }
  }

  public void testNeedsLibraryLoad() {
    assertThat(myManager.needsLibraryLoad(findItem(myPalette, TEXT_VIEW))).isFalse();
    assertThat(myManager.needsLibraryLoad(findItem(myPalette, FLOATING_ACTION_BUTTON))).isTrue();
  }

  public void testCreateItemIconOfTextView() {
    Icon icon = myManager.createItemIcon(findItem(myPalette, TEXT_VIEW), myPanel);
    assertThat(icon).isSameAs(StudioIcons.LayoutEditor.Palette.TEXT_VIEW);
  }

  public void testCreateItemIconOfFloatingActionButton() {
    Icon icon = myManager.createItemIcon(findItem(myPalette, FLOATING_ACTION_BUTTON), myPanel);
    // The FloatingActionButton is in support library which is not used. Thus FloatingActionButton should show an icons with download badge.
    assertThat(icon).isNotSameAs(StudioIcons.LayoutEditor.Palette.FLOATING_ACTION_BUTTON);
  }

  public void testCreateLargeItemIconOfTextView() {
    Icon icon = myManager.createLargeItemIcon(findItem(myPalette, TEXT_VIEW), myPanel);
    assertThat(icon).isSameAs(StudioIcons.LayoutEditor.Palette.TEXT_VIEW_LARGE);
  }

  public void testGradleSynchronization() {
    assertThat(myManager.needsLibraryLoad(findItem(myPalette, FLOATING_ACTION_BUTTON))).isTrue();
    assertThat(myManager.needsLibraryLoad(findItem(myPalette, RECYCLER_VIEW))).isTrue();
    assertThat(myManager.needsLibraryLoad(findItem(myPalette, CARD_VIEW))).isTrue();

    simulateGradleSync();

    assertThat(myManager.needsLibraryLoad(findItem(myPalette, FLOATING_ACTION_BUTTON))).isFalse();
    assertThat(myManager.needsLibraryLoad(findItem(myPalette, RECYCLER_VIEW))).isTrue();
    assertThat(myManager.needsLibraryLoad(findItem(myPalette, CARD_VIEW))).isFalse();

    verify(myPanel).repaint();
  }

  public void testDisposeStopsGradleSynchronizations() {
    simulateGradleSync();
    Disposer.dispose(myDisposable);
    simulateGradleSync();
    // Expect: No exceptions from myGradleDependencyManager mock
  }

  private void simulateGradleSync() {
    // syncStarted should be called before syncEnded.
    GradleSyncState syncState = GradleSyncState.getInstance(getProject());
    syncState.syncStarted(false, TRIGGER_PROJECT_MODIFIED);
    syncState.syncEnded();
  }

  @NotNull
  private static List<GradleCoordinate> createDependencies(@NotNull String... artifacts) {
    return Arrays.stream(artifacts)
      .map(artifact -> GradleCoordinate.parseCoordinateString(artifact + ":+"))
      .collect(Collectors.toList());
  }

  @NotNull
  private static Palette.Item findItem(@NotNull Palette palette, @NotNull String tagName) {
    Holder<Palette.Item> found = new Holder<>();
    palette.accept(item -> {
      if (item.getTagName().equals(tagName)) {
        found.value = item;
      }
    });
    if (found.value == null) {
      throw new RuntimeException("The item: " + tagName + " was not found on the palette.");
    }
    return found.value;
  }
}
