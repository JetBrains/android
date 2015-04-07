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

import com.android.tools.idea.sdk.remote.internal.packages.Package;
import com.android.utils.Pair;

/**
 * Interface used to decorate a {@link Package} that provides a version for layout lib.
 */
public interface ILayoutlibVersion {

    public static final int LAYOUTLIB_API_NOT_SPECIFIED = 0;
    public static final int LAYOUTLIB_REV_NOT_SPECIFIED = 0;

    /**
     * Returns the layoutlib version. Mandatory starting with repository XSD rev 4.
     * <p/>
     * The first integer is the API of layoublib, which should be > 0.
     * It will be equal to {@link #LAYOUTLIB_API_NOT_SPECIFIED} (0) if the layoutlib
     * version isn't specified.
     * <p/>
     * The second integer is the revision for that given API. It is >= 0
     * and works as a minor revision number, incremented for the same API level.
     *
     * @since sdk-repository-4.xsd
     */
    public Pair<Integer, Integer> getLayoutlibVersion();
}
