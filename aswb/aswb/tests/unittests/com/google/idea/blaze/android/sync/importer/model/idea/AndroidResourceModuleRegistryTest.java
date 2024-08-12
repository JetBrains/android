/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.sync.importer.model.idea;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import com.google.idea.blaze.android.sync.model.AndroidResourceModule;
import com.google.idea.blaze.android.sync.model.AndroidResourceModuleRegistry;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link AndroidResourceModuleRegistry}. */
@RunWith(JUnit4.class)
public class AndroidResourceModuleRegistryTest extends BlazeTestCase {
  private AndroidResourceModuleRegistry registry;

  @Override
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);
    projectServices.register(
        AndroidResourceModuleRegistry.class, new AndroidResourceModuleRegistry());

    registry = AndroidResourceModuleRegistry.getInstance(getProject());
  }

  @Test
  public void testPutAndGet() {
    Module moduleOne = mock(Module.class);
    Module moduleTwo = mock(Module.class);
    Module moduleThree = mock(Module.class);
    TargetKey keyOne = TargetKey.forPlainTarget(Label.create("//foo/bar:one"));
    TargetKey keyTwo = TargetKey.forPlainTarget(Label.create("//foo/bar:two"));
    TargetKey keyThree = TargetKey.forPlainTarget(Label.create("//foo/bar:three"));
    AndroidResourceModule resourceModuleOne = AndroidResourceModule.builder(keyOne).build();
    AndroidResourceModule resourceModuleTwo = AndroidResourceModule.builder(keyTwo).build();
    AndroidResourceModule resourceModuleThree = AndroidResourceModule.builder(keyThree).build();

    registry.put(moduleOne, resourceModuleOne);
    registry.put(moduleTwo, resourceModuleTwo);
    registry.put(moduleThree, resourceModuleThree);

    assertThat(registry.get(moduleOne)).isEqualTo(resourceModuleOne);
    assertThat(registry.get(moduleTwo)).isEqualTo(resourceModuleTwo);
    assertThat(registry.get(moduleThree)).isEqualTo(resourceModuleThree);
    assertThat(registry.getTargetKey(moduleOne)).isEqualTo(resourceModuleOne.targetKey);
    assertThat(registry.getTargetKey(moduleTwo)).isEqualTo(resourceModuleTwo.targetKey);
    assertThat(registry.getTargetKey(moduleThree)).isEqualTo(resourceModuleThree.targetKey);
    assertThat(registry.getModuleContainingResourcesOf(keyOne)).isEqualTo(moduleOne);
    assertThat(registry.getModuleContainingResourcesOf(keyTwo)).isEqualTo(moduleTwo);
    assertThat(registry.getModuleContainingResourcesOf(keyThree)).isEqualTo(moduleThree);
  }

  @Test
  public void testPutAndGetMergedResources() {
    Module moduleOne = mock(Module.class);
    Module moduleTwo = mock(Module.class);
    TargetKey keyOne = TargetKey.forPlainTarget(Label.create("//foo/bar:one"));
    TargetKey keyTwoFirst = TargetKey.forPlainTarget(Label.create("//foo/bar:two_first"));
    TargetKey keyTwoSecond = TargetKey.forPlainTarget(Label.create("//foo/bar:two_second"));
    AndroidResourceModule resourceModuleOne = AndroidResourceModule.builder(keyOne).build();
    AndroidResourceModule resourceModuleTwo =
        AndroidResourceModule.builder(keyTwoFirst).addSourceTarget(keyTwoSecond).build();

    registry.put(moduleOne, resourceModuleOne);
    registry.put(moduleTwo, resourceModuleTwo);

    assertThat(registry.get(moduleOne)).isEqualTo(resourceModuleOne);
    assertThat(registry.get(moduleTwo)).isEqualTo(resourceModuleTwo);
    assertThat(registry.getTargetKey(moduleOne)).isEqualTo(resourceModuleOne.targetKey);
    assertThat(registry.getTargetKey(moduleTwo)).isEqualTo(resourceModuleTwo.targetKey);
    assertThat(registry.getModuleContainingResourcesOf(keyOne)).isEqualTo(moduleOne);
    assertThat(registry.getModuleContainingResourcesOf(keyTwoFirst)).isEqualTo(moduleTwo);
    assertThat(registry.getModuleContainingResourcesOf(keyTwoFirst)).isEqualTo(moduleTwo);
  }

  @Test
  public void testPutSameKeyDifferentValues() {
    Module module = mock(Module.class);
    AndroidResourceModule resourceModuleOne =
        AndroidResourceModule.builder(TargetKey.forPlainTarget(Label.create("//foo/bar:one")))
            .build();
    AndroidResourceModule resourceModuleTwo =
        AndroidResourceModule.builder(TargetKey.forPlainTarget(Label.create("//foo/bar:two")))
            .build();
    registry.put(module, resourceModuleOne);
    registry.put(module, resourceModuleTwo);
    assertThat(registry.get(module)).isEqualTo(resourceModuleTwo);
  }

  @Test
  public void testPutDifferentKeysSameValue() {
    Module moduleOne = mock(Module.class);
    Module moduleTwo = mock(Module.class);
    AndroidResourceModule resourceModule =
        AndroidResourceModule.builder(TargetKey.forPlainTarget(Label.create("//foo/bar:one")))
            .build();
    registry.put(moduleOne, resourceModule);
    try {
      registry.put(moduleTwo, resourceModule);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException ignored) {
      // ignored
    }
    assertThat(registry.get(moduleOne)).isEqualTo(resourceModule);
    assertThat(registry.get(moduleTwo)).isNull();
  }

  @Test
  public void testGetNull() {
    assertThat(registry.get(null)).isNull();
    assertThat(registry.getTargetKey(null)).isNull();
    assertThat(registry.getModuleContainingResourcesOf(null)).isNull();
  }

  @Test
  public void testGetWithoutPut() {
    assertThat(registry.get(mock(Module.class))).isNull();
    assertThat(registry.getTargetKey(mock(Module.class))).isNull();
    assertThat(
            registry.getModuleContainingResourcesOf(
                TargetKey.forPlainTarget(Label.create("//a/b:c"))))
        .isNull();
  }
}
