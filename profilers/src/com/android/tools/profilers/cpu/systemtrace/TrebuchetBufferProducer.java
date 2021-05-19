/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.cpu.systemtrace;

import java.io.File;
import trebuchet.io.BufferProducer;

/**
 * Interface to wrap producers of trebuchet buffers.
 * This interface allows us to better handle errors by not forcing parsing to be done in the constructor instead it is done
 * in the {@link #parseFile(File)} method.
 */
public interface TrebuchetBufferProducer extends BufferProducer {
  /**
   * Called from {@link AtraceParser#parseModelIfNeeded(File)}. This method should trigger the parsing of the supplied file.
   * @return true on sucessfull parsing, false otherwise. If false is returned an entry should be logged to the standard error log.
   */
  boolean parseFile(File file);
}
