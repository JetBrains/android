/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.aidl.tests;

@SuppressWarnings(value={"inout-parameter", "out-array"})
parcelable ArrayOfInterfaces {
    interface IEmptyInterface {}

    interface IMyInterface {
        @nullable IEmptyInterface[] methodWithInterfaces(IEmptyInterface iface,
                @nullable IEmptyInterface nullable_iface,
                in IEmptyInterface[] iface_array_in, out IEmptyInterface[] iface_array_out,
                inout IEmptyInterface[] iface_array_inout,
                in @nullable IEmptyInterface[] nullable_iface_array_in,
                out @nullable IEmptyInterface[] nullable_iface_array_out,
                inout @nullable IEmptyInterface[] nullable_iface_array_inout);
    }

    @JavaDerive(toString=true, equals=true)
    parcelable MyParcelable {
        IEmptyInterface iface;
        @nullable IEmptyInterface nullable_iface;
        IEmptyInterface[] iface_array;
        @nullable IEmptyInterface[] nullable_iface_array;
    }

    @JavaDerive(toString=true, equals=true)
    union MyUnion {
        IEmptyInterface iface;
        @nullable IEmptyInterface nullable_iface;
        IEmptyInterface[] iface_array;
        @nullable IEmptyInterface[] nullable_iface_array;
    }
}
