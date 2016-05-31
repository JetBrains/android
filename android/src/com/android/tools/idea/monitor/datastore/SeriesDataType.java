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
package com.android.tools.idea.monitor.datastore;

/**
 * Here we define the series of data that we collect. The implementation of {@link SeriesDataStore} needs to return the
 * appropriate series type for each of the following items in the enum.
 */

// TODO make this an interface so we can subclass it by given data type.
public enum SeriesDataType {
  NETWORK_SENDING, //long
  NETWORK_RECEIVING, //long
  NETWORK_CONNECTIONS, //long
  CPU_MY_PROCESS, //long
  CPU_OTHER_PROCESSES, //long
  CPU_THREADS //long
}
