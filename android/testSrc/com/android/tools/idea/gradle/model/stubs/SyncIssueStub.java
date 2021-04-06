/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.model.stubs;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.SyncIssue;
import java.util.List;
import java.util.Objects;

public final class SyncIssueStub extends BaseStub implements SyncIssue {
    @NonNull private final String myMessage;
    @Nullable private final String myData;
    private final int mySeverity;
    private final int myType;

    public SyncIssueStub() {
        this("message", "data", 1, 2);
    }

    public SyncIssueStub(@NonNull String message, @Nullable String data, int severity, int type) {
        myMessage = message;
        myData = data;
        mySeverity = severity;
        myType = type;
    }

    @Override
    @NonNull
    public String getMessage() {
        return myMessage;
    }

    @Override
    @Nullable
    public String getData() {
        return myData;
    }

    @Override
    public int getSeverity() {
        return mySeverity;
    }

    @Override
    public int getType() {
        return myType;
    }

    @Nullable
    @Override
    public List<String> getMultiLineMessage() {
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SyncIssue)) {
            return false;
        }
        SyncIssue issue = (SyncIssue) o;
        return getSeverity() == issue.getSeverity()
                && getType() == issue.getType()
                && Objects.equals(getMessage(), issue.getMessage())
                && Objects.equals(getData(), issue.getData());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getMessage(), getData(), getSeverity(), getType());
    }

    @Override
    public String toString() {
        return "SyncIssueStub{"
                + "myMessage='"
                + myMessage
                + '\''
                + ", myData='"
                + myData
                + '\''
                + ", mySeverity="
                + mySeverity
                + ", myType="
                + myType
                + "}";
    }
}
