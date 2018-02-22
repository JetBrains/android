/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.performance;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.testFramework.LeakHunter;
import com.intellij.util.MemoryDumpHelper;
import gnu.trove.*;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

@RunWith(GuiTestRunner.class)
public class LayoutEditorMemoryUseTest {

  private static final int MAX_LOOP_COUNT = 3;
  private static final Set<String> ourIgnoredClasses = ImmutableSet.of(
    // Not a leak. ImagePool reuses its own objects by using PhantomReferences. This can lead to false positives.
    "com.android.tools.idea.rendering.ImagePool$2",

    // Known leak: b/73826291. Each time RenderTask is created, its myAssetRepository object is leaked.
    // The lifetime of the leak is equal to the lifetime of the Module (project close should dispose it).
    "com.android.tools.idea.res.AssetRepositoryImpl",

    // Known leak: b/73827630. DefaultNlToolbarActionGroups registers these actions, but never remove them.
    "com.android.tools.idea.common.actions.IssueNotificationAction",
    "com.android.tools.idea.common.actions.SwitchDesignModeAction",
    "com.android.tools.idea.common.actions.ToggleDeviceOrientationAction",
    "com.android.tools.idea.common.actions.SetZoomAction",
    "com.android.tools.idea.common.actions.SwitchDeviceAction",
    "com.android.tools.idea.rendering.RefreshRenderAction"
  );

  private static final Logger LOG = Logger.getInstance(LayoutEditorMemoryUseTest.class);
  private static final boolean CAPTURE_HEAP_DUMPS = false;

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @NotNull
  private static TObjectIntHashMap<String> copyMapWithSizeOnly(THashMap<String, THashSet<Object>> map) {
    TObjectIntHashMap<String> result;
    result = new TObjectIntHashMap<>();
    for (Map.Entry<String, THashSet<Object>> entry : map.entrySet()) {
      result.put(entry.getKey(), entry.getValue().size());
    }
    return result;
  }

  @Test
  public void navigateAndEdit() throws Exception {
    IdeFrameFixture fixture = guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutLocalTest");

    warmUp(fixture);

    if (CAPTURE_HEAP_DUMPS) {
      MemoryDumpHelper.captureMemoryDumpZipped("/tmp/LayoutEditorMemoryUseTest-before.hprof");
    }

    LeakedInstancesTracker instancesTracker = LeakedInstancesTracker.createWithInitialSnapshot();
    // Stop tracking classes that are ignored
    for (String className : ourIgnoredClasses) {
      instancesTracker.doNotTrack(className);
    }

    TObjectIntHashMap<String> previousCountsInstances = null;
    TObjectIntHashMap<String> currentCountsInstances = null;

    for (int i = 0; i < MAX_LOOP_COUNT && !instancesTracker.isEmpty(); i++) {
      runScenario(fixture);

      instancesTracker.snapshot();

      previousCountsInstances = currentCountsInstances;
      currentCountsInstances = copyMapWithSizeOnly(instancesTracker.getCurrentLeakCounts());
    }

    if (!currentCountsInstances.isEmpty()) {
      // Leaks have been found. Create a report.

      if (CAPTURE_HEAP_DUMPS) {
        MemoryDumpHelper.captureMemoryDumpZipped("/tmp/LayoutEditorMemoryUseTest-after.hprof");
      }

      if (!currentCountsInstances.isEmpty()) {
        reportCollectionsDelta(previousCountsInstances, currentCountsInstances);
      }

      Assert.fail("Found leaked objects.");
    }
  }

  public void reportCollectionsDelta(TObjectIntHashMap<String> previousCounts,
                                     TObjectIntHashMap<String> currentCounts) {
    StringBuilder sb = new StringBuilder();
    sb.append("Instance count delta:\n");
    TObjectIntIterator<String> iterator = currentCounts.iterator();
    while (iterator.hasNext()) {
      iterator.advance();
      int previousCount = previousCounts.get(iterator.key());
      if (previousCount != 0 && iterator.value() > previousCount) {
        sb.append(" Instance: ")
          .append(iterator.key()).append(" => +").append(iterator.value() - previousCount).append("\n");
      }
    }
    LOG.warn(sb.toString());
  }

  // Warm-up the scenario. Typically a single scenario run is enough if the senario works the same when the project is open and
  // when it is ran after a previous scenario run. Any additional setup should be performed in this method (show/hide IDE elements,
  // configure theme, connect app to Firebase, setup emulator, etc.)
  public void warmUp(IdeFrameFixture fixture) {
    runScenario(fixture);
  }

  // Main scenario. The scenario will be repeated multiple times during a run.
  // The state after each run should be the same as just after the warmUp() method.
  public void runScenario(IdeFrameFixture fixture) {
    String[] layoutFilePaths = {
      "app/src/main/res/layout/layout1.xml",
      "app/src/main/res/layout/layout2.xml",
      "app/src/main/res/layout/widgets.xml",
      "app/src/main/res/layout/textstyles.xml",
      "app/src/main/res/layout/constraint.xml",
    };

    // Open five layout files and switch their tabs in order.
    // First pass shows design tab, second pass - edit tab
    for (String layoutFilePath : layoutFilePaths) {
      fixture
        .getEditor()
        .open(layoutFilePath, EditorFixture.Tab.DESIGN)
        .getLayoutEditor(true)
        .waitForRenderToFinish();
    }
    for (String layoutFilePath : layoutFilePaths) {
      fixture
        .getEditor()
        .open(layoutFilePath, EditorFixture.Tab.EDITOR);
      fixture.getEditor().getLayoutPreview(true).waitForRenderToFinish();
    }
  }

  private static class LeakedInstancesTracker {
    private static final String ANDROID_CLASSES_PREFIX = "com.android.tools.";
    private THashMap<String, THashSet<Object>> myClasses;

    private LeakedInstancesTracker(THashMap<String, THashSet<Object>> classes) {
      myClasses = classes;
    }

    public static LeakedInstancesTracker createWithInitialSnapshot() {
      InstanceCounter instanceCounter = new InstanceCounter(ANDROID_CLASSES_PREFIX);
      LeakHunter.checkLeak(LeakHunter.allRoots(), Object.class, instanceCounter::registerObjectInstance);
      return new LeakedInstancesTracker(instanceCounter.getClassNameToInstancesMap());
    }

    public void snapshot() {
      InstanceCounter instanceCounter = new InstanceCounter(ANDROID_CLASSES_PREFIX);
      LeakHunter.checkLeak(LeakHunter.allRoots(), Object.class, instanceCounter::registerObjectInstance);

      // Remove all classes that have no instances
      for (Iterator<Map.Entry<String, THashSet<Object>>> it = myClasses.entrySet().iterator(); it.hasNext(); ) {
        final Map.Entry<String, THashSet<Object>> entry = it.next();
        if (!instanceCounter.getClassNameToInstancesMap().containsKey(entry.getKey())) {
          it.remove();
        }
      }
      // Remove all classes for which instance counts dropped
      for (Map.Entry<String, THashSet<Object>> entry : instanceCounter.getClassNameToInstancesMap().entrySet()) {
        if (!myClasses.containsKey(entry.getKey())) {
          continue;
        }
        if (myClasses.get(entry.getKey()).size() >= entry.getValue().size()) {
          myClasses.remove(entry.getKey());
        }
        else {
          myClasses.put(entry.getKey(), entry.getValue());
        }
      }
    }

    public boolean isEmpty() {
      return myClasses.isEmpty();
    }

    public THashMap<String, THashSet<Object>> getCurrentLeakCounts() {
      return myClasses;
    }

    public void doNotTrack(String name) {
      myClasses.remove(name);
    }

    private static class InstanceCounter {
      private String myPrefixFilter;
      private THashMap<String, THashSet<Object>> myClassNameToInstances;

      public InstanceCounter(String prefixFilter) {
        myPrefixFilter = prefixFilter;
        myClassNameToInstances = new THashMap<>();
      }

      public boolean registerObjectInstance(Object o) {
        Class clazz = o.getClass();
        final String clazzName = clazz.getName();
        if (myPrefixFilter != null && !clazzName.startsWith(myPrefixFilter)) return false;
        if (myClassNameToInstances.containsKey(clazzName)) {
          myClassNameToInstances.get(clazzName).add(o);
        }
        else {
          myClassNameToInstances.put(clazzName, new THashSet<>(TObjectHashingStrategy.IDENTITY));
        }
        return false;
      }

      public THashMap<String, THashSet<Object>> getClassNameToInstancesMap() {
        return myClassNameToInstances;
      }
    }
  }
}
