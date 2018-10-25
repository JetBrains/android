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
package com.android.tools.idea.rendering;

import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;

import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.perflogger.Benchmark;
import com.android.tools.perflogger.Metric;
import com.android.tools.perflogger.Metric.MetricSample;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

public class RenderPerfgateTest extends AndroidTestCase {

  @Language("XML")
  private static final String SIMPLE_LAYOUT = "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                              "    android:layout_height=\"match_parent\"\n" +
                                              "    android:layout_width=\"match_parent\"\n" +
                                              "    android:orientation=\"vertical\">\n" +
                                              "\n" +
                                              "    <LinearLayout\n" +
                                              "        android:layout_width=\"50dp\"\n" +
                                              "        android:layout_height=\"50dp\"\n" +
                                              "        android:background=\"#F00\"/>\n" +
                                              "    <LinearLayout\n" +
                                              "        android:layout_width=\"50dp\"\n" +
                                              "        android:layout_height=\"50dp\"\n" +
                                              "        android:background=\"#0F0\"/>\n" +
                                              "    <LinearLayout\n" +
                                              "        android:layout_width=\"50dp\"\n" +
                                              "        android:layout_height=\"50dp\"\n" +
                                              "        android:background=\"#00F\"/>\n" +
                                              "    \n" +
                                              "\n" +
                                              "</LinearLayout>";
  private static final int NUMBER_OF_WARM_UP = 2;
  private static final int NUMBER_OF_SAMPLES = 20;
  private static final int MAX_PRUNED_SAMPLES = NUMBER_OF_SAMPLES / 4;

  // This is the name that appears on perfgate dashboard.
  private static final Benchmark sBenchMark = new Benchmark.Builder("DesignTools Render Time Benchmark")
    .setDescription("Base line for RenderTask inflate time (mean) after " + NUMBER_OF_SAMPLES + " samples.")
    .build();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    RenderTestUtil.beforeRenderTestCase();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      RenderTestUtil.afterRenderTestCase();
    } finally {
      super.tearDown();
    }
  }

  public void testBaseInflate() throws Exception {
    VirtualFile file = myFixture.addFileToProject("res/layout/layout.xml", SIMPLE_LAYOUT).getVirtualFile();
    Configuration configuration = RenderTestUtil.getConfiguration(myModule, file);
    RenderLogger logger = mock(RenderLogger.class);

    ThrowableComputable<MetricSample, Exception> computable = () -> {
      RenderTask task = RenderTestUtil.createRenderTask(myFacet, file, configuration, logger);
      MetricSample sample = getInflateSamples(task);
      task.dispose().get(5, TimeUnit.SECONDS);
      return sample;
    };

    computeAndRecordMetric("inflate_time_base", computable);
  }

  public void testBaseRender() throws Exception {
    VirtualFile file = myFixture.addFileToProject("res/layout/layout.xml", SIMPLE_LAYOUT).getVirtualFile();
    Configuration configuration = RenderTestUtil.getConfiguration(myModule, file);
    RenderLogger logger = mock(RenderLogger.class);

    ThrowableComputable<MetricSample, Exception> computable = () -> {
      RenderTask task = RenderTestUtil.createRenderTask(myFacet, file, configuration, logger);
      MetricSample sample = getRenderSamples(task);
      task.dispose().get(5, TimeUnit.SECONDS);
      return sample;
    };

    computeAndRecordMetric("render_time_base", computable);
  }

  private static void computeAndRecordMetric(
    String metricName, ThrowableComputable<MetricSample, Exception> computable) throws Exception {

    Metric metric = new Metric(metricName);
    // LayoutLib has a large static initialization that would trigger on the first render.
    // Warm up by inflating few times before measuring.
    for (int i = 0; i < NUMBER_OF_WARM_UP; i++) {
      computable.compute();
    }

    // baseline samples
    List<MetricSample> elapsedList = new ArrayList<>();
    for (int i = 0; i < NUMBER_OF_SAMPLES; i++) {
      elapsedList.add(computable.compute());
    }

    List<MetricSample> result = pruneOutliers(elapsedList);
    metric.addSamples(sBenchMark, result.toArray(new MetricSample[0]));
    metric.commit();
  }

  private static MetricSample getInflateSamples(RenderTask task) {
    long startTime = System.currentTimeMillis();
    RenderResult result = task.inflate();
    long elapsedTime = System.currentTimeMillis() - startTime;

    checkSimpleLayoutResult(result);
    return new MetricSample(Instant.now().toEpochMilli(), elapsedTime);
  }

  private static MetricSample getRenderSamples(RenderTask task) {
    checkSimpleLayoutResult(task.inflate());

    long startTime = System.currentTimeMillis();
    CompletableFuture<RenderResult> resultsFuture = task.render();
    RenderResult result = Futures.getUnchecked(resultsFuture);
    long elapsedTime = System.currentTimeMillis() - startTime;

    checkSimpleLayoutResult(result);
    return new MetricSample(Instant.now().toEpochMilli(), elapsedTime);
  }

  /**
   * Asserts that the given result matches the {@link #SIMPLE_LAYOUT} structure
   */
  private static void checkSimpleLayoutResult(@NotNull RenderResult result) {
    assertEquals(Result.Status.SUCCESS, result.getRenderResult().getStatus());

    List<ViewInfo> views = result.getRootViews().get(0).getChildren();
    assertEquals(3, views.size());
    String previousCoordinates = "";
    for (int i = 0; i < 3; i++) {
      ViewInfo view = views.get(i);
      assertEquals("android.widget.LinearLayout", view.getClassName());
      // Check the coordinates are different for each box
      String currentCoordinates = String.format("%dx%d - %dx%d", view.getTop(), view.getLeft(), view.getBottom(), view.getRight());
      assertNotEquals(previousCoordinates, currentCoordinates);
      previousCoordinates = currentCoordinates;
    }
  }

  /**
   * Try to prune outliers based on standard deviation. No more than {@link #MAX_PRUNED_SAMPLES} samples will be pruned.
   */
  private static List<MetricSample> pruneOutliers(List<MetricSample> list) {
    float average = getAverage(list);
    float std = getStandardDeviation(list, average);

    float lowerBound = average - std;
    float upperBound = average + std;

    List<MetricSample> result = new ArrayList<>();
    for (MetricSample sample : list) {
      if (sample.getSampleData() >= lowerBound && sample.getSampleData() <= upperBound) {
        result.add(sample);
      }
    }

    // Too many samples pruned (i.e. data is widely spread). Return the raw list.
    if (result.size() < (list.size() - MAX_PRUNED_SAMPLES)) {
      return list;
    }
    return result;
  }

  private static float getStandardDeviation(List<MetricSample> list, float average) {
    float sum = 0;
    for (MetricSample sample : list) {
      sum += Math.pow(sample.getSampleData() - average, 2);
    }
    return (float) Math.sqrt(sum / list.size());
  }

  private static float getAverage(List<MetricSample> list) {
    float sum = 0;
    for (MetricSample sample : list) {
      sum += sample.getSampleData();
    }
    return sum / list.size();
  }
}
