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
package com.android.tools.idea.run.editor.binaryxml;

import com.android.tools.idea.run.activity.DefaultActivityLocator;
import com.android.tools.idea.run.activity.manifest.ManifestActivityInfo;
import com.android.tools.idea.run.activity.manifest.NodeActivity;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;

public class ManifestActivityInfoParserTest {

  // This test can be run alone with command:
  // bazel test //tools/adt/idea/android:intellij.android.core.tests_tests__all --test_filter=run.editor.binaryxml
  private static final String BASE = "binary.manifest/";

  @Test
  public void binaryManifestTest() throws IOException {
    URL url = ManifestActivityInfoParserTest.class.getClassLoader().getResource(BASE + "manifestWithActivity.bxml");
    Assert.assertTrue(" ", url != null);
    try (InputStream input = url.openStream()) {
      ManifestActivityInfo manifest = ManifestActivityInfo.parseBinaryFromStream(input);

      Assert.assertEquals("Application id", "com.example.activityapplication", manifest.packageName());

      List<NodeActivity> activities = manifest.activities();
      Assert.assertEquals("Num activities", 5, activities.size());

      DefaultActivityLocator.ActivityWrapper activity = getActivityByQName("com.example.activityapplication.MainActivity", activities);
      Assert.assertEquals("Intent Action 0", true, activity.hasAction("android.intent.action.MAIN"));
      Assert.assertEquals("Intent Catego 0", true, activity.hasCategory("android.intent.category.LAUNCHER"));
      Assert.assertEquals("Exported 0", true, activity.getExported());
      Assert.assertEquals("Enabled 0", true, activity.isEnabled());

      // Activity alias
      activity = getActivityByQName("com.example.activityapplication.foo", activities);
      Assert.assertNotEquals("Aliased Activity",null, activity);


      activity = getActivityByQName("com.example.activityapplication.MissingActivity", activities);
      Assert.assertEquals("Intent Action 1", false, activity.hasAction("android.intent.action.MAIN"));
      Assert.assertEquals("Intent Catego 1", false, activity.hasCategory("android.intent.category.LAUNCHER"));
      Assert.assertEquals("Exported 1", false, activity.getExported());
      Assert.assertEquals("Enabled 1", false, activity.isEnabled());

      activity = getActivityByQName("com.example.activityapplication.MissingActivity2", activities);
      Assert.assertEquals("Intent Action 2.0", true, activity.hasAction("android.intent.action.MAIN"));
      Assert.assertEquals("Intent Catego 2.0", true, activity.hasCategory("android.intent.category.LAUNCHER"));
      Assert.assertEquals("Intent Action 2.1", true, activity.hasAction("foo"));
      Assert.assertEquals("Intent Catego 2.1", true, activity.hasCategory("bar"));
      Assert.assertEquals("Exported 2", true, activity.getExported());
      Assert.assertEquals("Enabled 2", true, activity.isEnabled());

      // Test multiAction, multiCategory activity
      activity = getActivityByQName("com.example.activityapplication.MultiActivity", activities);
      Assert.assertEquals("Multi Action 1", true, activity.hasAction("android.intent.action.MAIN"));
      Assert.assertEquals("Multi Action 2", true, activity.hasAction("android.intent.action.MAIN2"));
      Assert.assertEquals("Multi Catego 1", true, activity.hasCategory("android.intent.category.LAUNCHER"));
      Assert.assertEquals("Multi Catego 2", true, activity.hasCategory("android.intent.category.LAUNCHER2"));
    }
  }

  @Nullable
  private static NodeActivity getActivityByQName(@NotNull String qname, @NotNull List<NodeActivity> activities) {
      for(NodeActivity activity : activities) {
        if (qname.equals(activity.getQualifiedName())) {
          return activity;
        }
      }
      return null;
  }
}
