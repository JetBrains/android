/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.ide.common.res2;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.ILogger;
import com.google.common.collect.Lists;

import java.util.List;

/**
 * Implementation of the ILogger interface that records all the logs.
 */
public class RecordingLogger implements ILogger {

    private final List<String> mErrorMsgs = Lists.newArrayList();
    private final List<String> mWarningMsgs = Lists.newArrayList();
    private final List<String> mInfoMsgs = Lists.newArrayList();
    private final List<String> mVerboseMsgs = Lists.newArrayList();

    @Override
    public void error(@Nullable Throwable t, @Nullable String msgFormat, Object... args) {
        mErrorMsgs.add(String.format(msgFormat, args));
    }

    @Override
    public void warning(@NonNull String msgFormat, Object... args) {
        mWarningMsgs.add(String.format(msgFormat, args));
    }

    @Override
    public void info(@NonNull String msgFormat, Object... args) {
        mInfoMsgs.add(String.format(msgFormat, args));
    }

    @Override
    public void verbose(@NonNull String msgFormat, Object... args) {
        mVerboseMsgs.add(String.format(msgFormat, args));
    }

    public List<String> getErrorMsgs() {
        return mErrorMsgs;
    }

    public List<String> getWarningMsgs() {
        return mWarningMsgs;
    }

    public List<String> getInfoMsgs() {
        return mInfoMsgs;
    }

    public List<String> getVerboseMsgs() {
        return mVerboseMsgs;
    }
}
