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
package com.android.tools.profilers.memory;

import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.TestGrpcChannel;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.ClassObject;
import com.android.tools.profilers.memory.adapters.HeapObject;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class MemoryProfilerTestBase {
  protected StudioProfilers myProfilers;
  protected MemoryServiceMock myMockService;
  protected MemoryProfilerStage myStage;

  protected int myLegacyAllocationAspectCount;
  protected int myCurrentLoadedCaptureAspectCount;
  protected int myCurrentLoadingCaptureAspectCount;
  protected int myCurrentHeapAspectCount;
  protected int myCurrentClassAspectCount;
  protected int myCurrentInstanceAspectCount;

  @Rule
  public TestGrpcChannel<MemoryServiceMock> myGrpcChannel = new TestGrpcChannel<>("MEMORY_TEST_CHANNEL", new MemoryServiceMock());

  @Before
  public void setup() {
    resetCounts();
    myStage.getAspect().addDependency()
      .onChange(MemoryProfilerAspect.LEGACY_ALLOCATION, () -> ++myLegacyAllocationAspectCount)
      .onChange(MemoryProfilerAspect.CURRENT_LOADING_CAPTURE, () -> ++myCurrentLoadedCaptureAspectCount)
      .onChange(MemoryProfilerAspect.CURRENT_LOADED_CAPTURE, () -> ++myCurrentLoadingCaptureAspectCount)
      .onChange(MemoryProfilerAspect.CURRENT_HEAP, () -> ++myCurrentHeapAspectCount)
      .onChange(MemoryProfilerAspect.CURRENT_CLASS, () -> ++myCurrentClassAspectCount)
      .onChange(MemoryProfilerAspect.CURRENT_INSTANCE, () -> ++myCurrentInstanceAspectCount);
  }

  protected void assertAndResetCounts(int legacyAllocationAspect,
                                    int currentLoadingCaptureAspectCount,
                                    int currentLoadedCaptureAspect,
                                    int currentHeapAspectCount,
                                    int currentClassAspectCount,
                                    int currentInstanceAspectCount) {
    assertEquals(legacyAllocationAspect, myLegacyAllocationAspectCount);
    assertEquals(currentLoadingCaptureAspectCount, myCurrentLoadingCaptureAspectCount);
    assertEquals(currentLoadedCaptureAspect, myCurrentLoadedCaptureAspectCount);
    assertEquals(currentHeapAspectCount, myCurrentHeapAspectCount);
    assertEquals(currentClassAspectCount, myCurrentClassAspectCount);
    assertEquals(currentInstanceAspectCount, myCurrentInstanceAspectCount);
    resetCounts();
  }

  protected void resetCounts() {
    myLegacyAllocationAspectCount = 0;
    myCurrentLoadingCaptureAspectCount = 0;
    myCurrentLoadedCaptureAspectCount = 0;
    myCurrentHeapAspectCount = 0;
    myCurrentClassAspectCount = 0;
    myCurrentInstanceAspectCount = 0;
  }

  protected static final CaptureObjectLoader DUMMY_LOADER = new CaptureObjectLoader() {
    @NotNull
    @Override
    public ListenableFuture<CaptureObject> loadCapture(@NotNull CaptureObject captureObject) {
      return Futures.immediateFuture(captureObject);
    }
  };

  protected static final CaptureObject DUMMY_CAPTURE = new CaptureObject() {
    @NotNull
    @Override
    public String getLabel() {
      return "DUMMY_CAPTURE";
    }

    @NotNull
    @Override
    public List<HeapObject> getHeaps() {
      return Arrays.asList(DUMMY_HEAP_1, DUMMY_HEAP_2);
    }

    @Override
    public long getStartTimeNs() {
      return 5;
    }

    @Override
    public long getEndTimeNs() {
      return 10;
    }

    @Override
    public boolean load() {
      return true;
    }

    @Override
    public boolean isDoneLoading() {
      return true;
    }

    @Override
    public boolean isError() {
      return false;
    }

    @Override
    public void dispose() {
    }
  };

  protected static final HeapObject DUMMY_HEAP_1 = new HeapObject() {
    @NotNull
    @Override
    public String getHeapName() {
      return "DUMMY_HEAP_1";
    }

    @NotNull
    @Override
    public List<ClassObject> getClasses() {
      return Arrays.asList(DUMMY_CLASS_1, DUMMY_CLASS_2);
    }

    @NotNull
    @Override
    public List<ClassAttribute> getClassAttributes() {
      return Collections.emptyList();
    }
  };

  protected static final HeapObject DUMMY_HEAP_2 = new HeapObject() {
    @NotNull
    @Override
    public String getHeapName() {
      return "DUMMY_HEAP_2";
    }

    @NotNull
    @Override
    public List<ClassObject> getClasses() {
      return Arrays.asList(DUMMY_CLASS_2, DUMMY_CLASS_1);
    }

    @NotNull
    @Override
    public List<ClassAttribute> getClassAttributes() {
      return Collections.emptyList();
    }
  };

  protected static final ClassObject DUMMY_CLASS_1 = new ClassObject() {
    @NotNull
    @Override
    public String getName() {
      return "DUMMY_CLASS_1";
    }

    @NotNull
    @Override
    public List<InstanceAttribute> getInstanceAttributes() {
      return Collections.emptyList();
    }

    @Override
    public int getChildrenCount() {
      return 1;
    }

    @NotNull
    @Override
    public List<InstanceObject> getInstances() {
      return Collections.singletonList(DUMMY_INSTANCE);
    }
  };

  protected static final ClassObject DUMMY_CLASS_2 = new ClassObject() {
    @NotNull
    @Override
    public String getName() {
      return "DUMMY_CLASS_2";
    }

    @NotNull
    @Override
    public List<InstanceAttribute> getInstanceAttributes() {
      return Collections.emptyList();
    }

    @Override
    public int getChildrenCount() {
      return 1;
    }

    @NotNull
    @Override
    public List<InstanceObject> getInstances() {
      return Collections.singletonList(DUMMY_INSTANCE);
    }
  };

  @SuppressWarnings("Convert2Lambda")
  protected static final InstanceObject DUMMY_INSTANCE = new InstanceObject() {
    @NotNull
    @Override
    public String getName() {
      return "DUMMY_INSTANCE";
    }
  };
}
