// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.execution.common.processhandler

import com.android.ddmlib.IDevice

/**
 *
 */
interface DeviceAwareProcessHandler {
  /**
   * Checks if is process is associated with a running [device].
   */
  fun isAssociated(device: IDevice): Boolean
}