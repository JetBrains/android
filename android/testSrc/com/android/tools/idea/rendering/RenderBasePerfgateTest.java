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

import static com.android.tools.idea.rendering.PerfgateRenderUtil.NUMBER_OF_SAMPLES;
import static com.android.tools.idea.rendering.PerfgateRenderUtil.NUMBER_OF_WARM_UP;
import static com.android.tools.idea.rendering.PerfgateRenderUtil.pruneOutliers;
import static com.android.tools.idea.rendering.PerfgateRenderUtil.sRenderMemoryBenchMark;
import static com.android.tools.idea.rendering.PerfgateRenderUtil.sRenderTimeBenchMark;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;

import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.perflogger.Metric;
import com.android.tools.perflogger.Metric.MetricSample;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

public class RenderBasePerfgateTest extends AndroidTestCase {

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

    ThrowableComputable<PerfgateRenderMetric, Exception> computable = () -> {
      RenderTask task = RenderTestUtil.createRenderTask(myFacet, file, configuration, logger);
      PerfgateRenderMetric metric = getInflateMetric(task);
      task.dispose().get(5, TimeUnit.SECONDS);
      return metric;
    };

    computeAndRecordMetric("inflate_time_base", "inflate_memory_base", computable);
  }

  public void testBaseRender() throws Exception {
    VirtualFile file = myFixture.addFileToProject("res/layout/layout.xml", SIMPLE_LAYOUT).getVirtualFile();
    Configuration configuration = RenderTestUtil.getConfiguration(myModule, file);
    RenderLogger logger = mock(RenderLogger.class);

    ThrowableComputable<PerfgateRenderMetric, Exception> computable = () -> {
      RenderTask task = RenderTestUtil.createRenderTask(myFacet, file, configuration, logger);
      PerfgateRenderMetric metric = getRenderMetric(task);
      task.dispose().get(5, TimeUnit.SECONDS);
      return metric;
    };

    computeAndRecordMetric("render_time_base", "render_memory_base", computable);
  }

  private static void computeAndRecordMetric(
    String renderMetricName, String memoryMetricName, ThrowableComputable<PerfgateRenderMetric, Exception> computable) throws Exception {

    System.gc();

    // LayoutLib has a large static initialization that would trigger on the first render.
    // Warm up by inflating few times before measuring.
    for (int i = 0; i < NUMBER_OF_WARM_UP; i++) {
      computable.compute();
    }

    // baseline samples
    List<MetricSample> renderTimes = new ArrayList<>();
    List<MetricSample> memoryUsages = new ArrayList();
    for (int i = 0; i < NUMBER_OF_SAMPLES; i++) {

      PerfgateRenderMetric metric = computable.compute();

      renderTimes.add(metric.getRenderTimeMetricSample());
      memoryUsages.add(metric.getMemoryMetricSample());
    }

    Metric renderMetric = new Metric(renderMetricName);
    List<MetricSample> result = pruneOutliers(renderTimes);
    renderMetric.addSamples(sRenderTimeBenchMark, result.toArray(new MetricSample[0]));
    renderMetric.commit();

    // Let's start without pruning to see how bad it is.
    Metric memMetric = new Metric(memoryMetricName);
    memMetric.addSamples(sRenderMemoryBenchMark, memoryUsages.toArray(new MetricSample[0]));
    memMetric.commit();
  }

  private static PerfgateRenderMetric getInflateMetric(RenderTask task) {
    PerfgateRenderMetric renderMetric = new PerfgateRenderMetric();

    renderMetric.beforeTest();
    RenderResult result = Futures.getUnchecked(task.inflate());
    renderMetric.afterTest();

    checkSimpleLayoutResult(result);
    return renderMetric;
  }

  private static PerfgateRenderMetric getRenderMetric(RenderTask task) {
    checkSimpleLayoutResult(Futures.getUnchecked(task.inflate()));
    PerfgateRenderMetric renderMetric = new PerfgateRenderMetric();

    renderMetric.beforeTest();
    RenderResult result = Futures.getUnchecked(task.render());
    renderMetric.afterTest();

    checkSimpleLayoutResult(result);
    return renderMetric;
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
}
