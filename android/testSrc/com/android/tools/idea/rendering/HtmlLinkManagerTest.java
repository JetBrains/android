/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.tools.idea.projectsystem.*;
import com.intellij.openapi.vfs.VirtualFile;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;

public class HtmlLinkManagerTest extends TestCase {
  public void testRunnable() {
    HtmlLinkManager manager = new HtmlLinkManager();
    final AtomicBoolean result1 = new AtomicBoolean(false);
    final AtomicBoolean result2= new AtomicBoolean(false);
    Runnable runnable1 = new Runnable() {
      @Override
      public void run() {
        result1.set(true);
      }
    };
    Runnable runnable2 = new Runnable() {
      @Override
      public void run() {
        result2.set(true);
      }
    };
    String url1 = manager.createRunnableLink(runnable1);
    String url2 = manager.createRunnableLink(runnable2);
    assertFalse(result1.get());
    manager.handleUrl(url1, null, null, null, null);
    assertTrue(result1.get());
    assertFalse(result2.get());
    result1.set(false);
    manager.handleUrl(url2, null, null, null, null);
    assertFalse(result1.get());
    assertTrue(result2.get());
  }

  public void testHandleAddDependency() {
    List<GoogleMavenArtifactId> addedArtifacts = new ArrayList<>();

    AndroidModuleSystem moduleSystem = new AndroidModuleSystem() {
      @NotNull
      @Override
      public CapabilityStatus getInstantRunSupport() {
        return new CapabilityNotSupported();
      }

      @NotNull
      @Override
      public CapabilityStatus canGeneratePngFromVectorGraphics() {
        return new CapabilityNotSupported();
      }

      @Override
      public void addDependencyWithoutSync(@NotNull GoogleMavenArtifactId artifactId, @Nullable GoogleMavenArtifactVersion version,
                                           boolean includePreview)
        throws DependencyManagementException {
        assertNull(version);
        addedArtifacts.add(artifactId);
      }

      @Nullable
      @Override
      public GoogleMavenArtifactVersion getDeclaredVersion(@NotNull GoogleMavenArtifactId artifactId) throws DependencyManagementException {
        return null;
      }

      @Nullable
      @Override
      public GoogleMavenArtifactVersion getResolvedVersion(@NotNull GoogleMavenArtifactId artifactId) throws DependencyManagementException {
        return null;
      }

      @NotNull
      @Override
      public List<NamedModuleTemplate> getModuleTemplates(@Nullable VirtualFile targetDirectory) {
        return Collections.emptyList();
      }
    };

    // try multiple invalid links
    HtmlLinkManager.handleAddDependency("addDependency:", moduleSystem);
    HtmlLinkManager.handleAddDependency("addDependency:com.android.support", moduleSystem);
    HtmlLinkManager.handleAddDependency("addDependency:com.android.support:", moduleSystem);
    HtmlLinkManager.handleAddDependency("addDependency:com.google.android.gms:palette-v7", moduleSystem);
    HtmlLinkManager.handleAddDependency("addDependency:com.android.support:palette-v7-broken", moduleSystem);
    assertEquals(0, addedArtifacts.size());

    HtmlLinkManager.handleAddDependency("addDependency:com.android.support:palette-v7", moduleSystem);
    HtmlLinkManager.handleAddDependency("addDependency:com.google.android.gms:play-services", moduleSystem);
    HtmlLinkManager.handleAddDependency("addDependency:com.android.support.constraint:constraint-layout", moduleSystem);
    assertThat(addedArtifacts.stream().map(Object::toString).collect(Collectors.toList()))
      .containsExactly("com.android.support:palette-v7",
                       "com.google.android.gms:play-services",
                       "com.android.support.constraint:constraint-layout");
  }
}
