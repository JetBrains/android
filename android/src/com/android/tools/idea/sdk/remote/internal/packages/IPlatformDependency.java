/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tools.idea.sdk.remote.internal.packages;

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.sdk.remote.internal.packages.IAndroidVersionProvider;
import com.android.tools.idea.sdk.remote.internal.packages.Package;

/**
 * Interface used to decorate a {@link Package} that has a dependency
 * on a specific platform (API level and/or code name).
 * <p/>
 * A package that has this dependency can only be installed if a platform with at least the
 * requested API level is present or installed at the same time.
 * <p/>
 * Note that although this interface looks like {@link IAndroidVersionProvider}, it does
 * not convey the same semantic since {@link IAndroidVersionProvider} does <em>not</em>
 * imply any dependency being a limiting factor as far as installation is concerned.
 */
public interface IPlatformDependency {

    /** Returns the version of the platform dependency of this package. */
    AndroidVersion getAndroidVersion();

}
