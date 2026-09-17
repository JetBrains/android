/*
 * Copyright (C) 2026 The Android Open Source Project
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

/** Holds an Allocation information. */
public class AllocationInfo {
    private final String mAllocatedClass;
    private final int mAllocNumber;
    private final int mAllocationSize;
    private final short mThreadId;
    private final StackTraceElement[] mStackTrace;

    /*
     * Simple constructor.
     */
    AllocationInfo(int allocNumber, String allocatedClass, int allocationSize,
        short threadId, StackTraceElement[] stackTrace) {
        mAllocNumber = allocNumber;
        mAllocatedClass = allocatedClass;
        mAllocationSize = allocationSize;
        mThreadId = threadId;
        mStackTrace = stackTrace;
    }

    /**
     * Returns the allocation number. Allocations are numbered as they happen with the most
     * recent one having the highest number
     */
    public int getAllocNumber() {
        return mAllocNumber;
    }

    /**
     * Returns the name of the allocated class.
     */
    public String getAllocatedClass() {
        return mAllocatedClass;
    }

    /**
     * Returns the size of the allocation.
     */
    public int getSize() {
        return mAllocationSize;
    }

    /**
     * Returns the id of the thread that performed the allocation.
     */
    public short getThreadId() {
        return mThreadId;
    }

    /*
     * (non-Javadoc)
     * @see com.android.ddmlib.IStackTraceInfo#getStackTrace()
     */
    public StackTraceElement[] getStackTrace() {
        return mStackTrace;
    }

    public String getFirstTraceClassName() {
        if (mStackTrace.length > 0) {
            return mStackTrace[0].getClassName();
        }

        return null;
    }

    public String getFirstTraceMethodName() {
        if (mStackTrace.length > 0) {
            return mStackTrace[0].getMethodName();
        }

        return null;
    }
}
