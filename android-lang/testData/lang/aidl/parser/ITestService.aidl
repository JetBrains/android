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

// This is a test case taken from the AIDL tool's codebase:
// https://cs.android.com/android/platform/superproject/+/master:system/tools/aidl/tests/android/aidl/tests/ITestService.aidl

package android.aidl.tests;

import android.aidl.tests.BackendType;
import android.aidl.tests.ByteEnum;
import android.aidl.tests.INamedCallback;
import android.aidl.tests.INewName;
import android.aidl.tests.IOldName;
import android.aidl.tests.IntEnum;
import android.aidl.tests.LongEnum;
import android.aidl.tests.RecursiveList;
import android.aidl.tests.StructuredParcelable;
import android.aidl.tests.extension.ExtendableParcelable;

/**
 * interface comment
 */
@SuppressWarnings(value={"inout-parameter", "mixed-oneway", "out-array"})
@SensitiveData
@JavaDefault
interface ITestService {
    // Test that constants are accessible

    /**
     * extra doc comment
     */
    // extra line comment
    /*
     * extra regular comment
     */
    /**
     * const comment
     */
    const int TEST_CONSTANT = 42;
    const int TEST_CONSTANT2 = -42;
    const int TEST_CONSTANT3 = +42;
    const int TEST_CONSTANT4 = +4;
    const int TEST_CONSTANT5 = -4;
    const int TEST_CONSTANT6 = -0;
    const int TEST_CONSTANT7 = +0;
    const int TEST_CONSTANT8 = 0;
    const int TEST_CONSTANT9 = 0x56;
    const int TEST_CONSTANT10 = 0xa5;
    const int TEST_CONSTANT11 = 0xFA;
    const int TEST_CONSTANT12 = 0xffffffff;

    const byte BYTE_TEST_CONSTANT = 17;
    const long LONG_TEST_CONSTANT = 1L << 40;

    const String STRING_TEST_CONSTANT = "foo";
    const String STRING_TEST_CONSTANT2 = "bar";

    const @utf8InCpp String STRING_TEST_CONSTANT_UTF8 = "baz";

    // This is to emulate a method that is added after the service is implemented.
    // So the client cannot assume that a call to this method will be successful
    // or not. However, inside the test environment, we can't build client and
    // the server with different version of this AIDL file. So, we let the server
    // actually implement this and intercept the dispatch to the method
    // inside onTransact().
    // WARNING: Must be first method.
    // This requires hard coding the transaction number. As long as this method is
    // the first in this interface, it can keep the
    // "::android::IBinder::FIRST_CALL_TRANSACTION + 0" value and allow
    // methods to be added and removed.
    int UnimplementedMethod(int arg);

    /**
     * @deprecated to make sure we have something in system/tools/aidl which does a compile check
     *     of deprecated and make sure this is reflected in goldens
     */
    void Deprecated();

    oneway void TestOneway();

    // Test that primitives work as parameters and return types.
    boolean RepeatBoolean(boolean token);
    byte RepeatByte(byte token);
    char RepeatChar(char token);
    int RepeatInt(int token);
    long RepeatLong(long token);
    float RepeatFloat(float token);
    double RepeatDouble(double token);
    String RepeatString(String token);
    ByteEnum RepeatByteEnum(ByteEnum token);
    IntEnum RepeatIntEnum(IntEnum token);
    LongEnum RepeatLongEnum(LongEnum token);

    // Test that arrays work as parameters and return types.
    boolean[] ReverseBoolean(in boolean[] input, out boolean[] repeated);
    byte[] ReverseByte(in byte[] input, out byte[] repeated);
    char[] ReverseChar(in char[] input, out char[] repeated);
    int[] ReverseInt(in int[] input, out int[] repeated);
    long[] ReverseLong(in long[] input, out long[] repeated);
    float[] ReverseFloat(in float[] input, out float[] repeated);
    double[] ReverseDouble(in double[] input, out double[] repeated);
    String[] ReverseString(in String[] input, out String[] repeated);
    ByteEnum[] ReverseByteEnum(in ByteEnum[] input, out ByteEnum[] repeated);
    IntEnum[] ReverseIntEnum(in IntEnum[] input, out IntEnum[] repeated);
    LongEnum[] ReverseLongEnum(in LongEnum[] input, out LongEnum[] repeated);

    // Test that clients can send and receive Binders.
    @PropagateAllowBlocking INamedCallback GetOtherTestService(String name);
    boolean VerifyName(INamedCallback service, String name);
    INamedCallback[] GetInterfaceArray(in String[] names);
    boolean VerifyNamesWithInterfaceArray(in INamedCallback[] services, in String[] names);
    @nullable INamedCallback[] GetNullableInterfaceArray(
            in @nullable String[] names);
    boolean VerifyNamesWithNullableInterfaceArray(
            in @nullable INamedCallback[] services, in @nullable String[] names);

    @nullable List<INamedCallback> GetInterfaceList(in @nullable String[] names);
    boolean VerifyNamesWithInterfaceList(in @nullable List<INamedCallback> services,
            in @nullable String[] names);

    // Test that List<T> types work correctly.
    List<String> ReverseStringList(in List<String> input, out List<String> repeated);

    ParcelFileDescriptor RepeatParcelFileDescriptor(in ParcelFileDescriptor read);
    ParcelFileDescriptor[] ReverseParcelFileDescriptorArray(
            in ParcelFileDescriptor[] input, out ParcelFileDescriptor[] repeated);

    // Test that service specific exceptions work correctly.
    void ThrowServiceException(int code);

    // Test nullability
    @nullable int[] RepeatNullableIntArray(in @nullable int[] input);
    @nullable ByteEnum[] RepeatNullableByteEnumArray(in @nullable ByteEnum[] input);
    @nullable IntEnum[] RepeatNullableIntEnumArray(in @nullable IntEnum[] input);
    @nullable LongEnum[] RepeatNullableLongEnumArray(in @nullable LongEnum[] input);
    @nullable String RepeatNullableString(in @nullable String input);
    @nullable List<String> RepeatNullableStringList(
            in @nullable List<String> input);

    // Small empty parcelable for nullability check
    @JavaDerive(equals=true)
    @RustDerive(Clone=true, PartialEq=true)
    parcelable Empty {}
    @nullable Empty RepeatNullableParcelable(in @nullable Empty input);
    @nullable Empty[] RepeatNullableParcelableArray(in @nullable Empty[] input);
    @nullable List<Empty> RepeatNullableParcelableList(
            in @nullable List<Empty> input);

    void TakesAnIBinder(in IBinder input);
    void TakesANullableIBinder(in @nullable IBinder input);
    void TakesAnIBinderList(in List<IBinder> input);
    void TakesANullableIBinderList(in @nullable List<IBinder> input);

    // Test utf8 decoding from utf16 wire format
    @utf8InCpp String RepeatUtf8CppString(@utf8InCpp String token);
    @nullable @utf8InCpp String RepeatNullableUtf8CppString(
            @nullable @utf8InCpp String token);

    @utf8InCpp String[] ReverseUtf8CppString(
            in @utf8InCpp String[] input, out @utf8InCpp String[] repeated);

    @SuppressWarnings(value={"out-nullable"})
    @nullable @utf8InCpp String[] ReverseNullableUtf8CppString(
            in @nullable @utf8InCpp String[] input,
            out @nullable @utf8InCpp String[] repeated);

    @SuppressWarnings(value={"out-nullable"})
    @nullable @utf8InCpp List<String> ReverseUtf8CppStringList(
            in @nullable @utf8InCpp List<String> input,
            out @nullable @utf8InCpp List<String> repeated);

    /**
     * comment before annotation
     */
    @nullable INamedCallback GetCallback(boolean return_null);

    // Since this paracelable has clearly defined default values, it would be
    // inefficient to use an IPC to fill it out in practice.
    void FillOutStructuredParcelable(inout StructuredParcelable parcel);

    void RepeatExtendableParcelable(in ExtendableParcelable ep, out ExtendableParcelable ep2);

    RecursiveList ReverseList(in RecursiveList list);

    IBinder[] ReverseIBinderArray(in IBinder[] input, out IBinder[] repeated);
    @nullable IBinder[] ReverseNullableIBinderArray(
            in @nullable IBinder[] input, out @nullable IBinder[] repeated);

    // All these constant expressions should be equal to 1
    const int A1 = (~(-1)) == 0;
    const int A2 = ~~(1 << 31) == (1 << 31);
    const int A3 = -0x7fffffff < 0;
    const int A4 = 0x80000000 < 0;
    const int A5 = 0x7fffffff == 2147483647;
    const int A6 = (1 << 31) == 0x80000000;
    const int A7 = (1 + 2) == 3;
    const int A8 = (8 - 9) == -1;
    const int A9 = (9 * 9) == 81;
    const int A10 = (29 / 3) == 9;
    const int A11 = (29 % 3) == 2;
    const int A12 = (0xC0010000 | 0xF00D) == (0xC001F00D);
    const int A13 = (10 | 6) == 14;
    const int A14 = (10 & 6) == 2;
    const int A15 = (10 ^ 6) == 12;
    const int A16 = 6 < 10;
    const int A17 = (10 < 10) == 0;
    const int A18 = (6 > 10) == 0;
    const int A19 = (10 > 10) == 0;
    const int A20 = 19 >= 10;
    const int A21 = 10 >= 10;
    const int A22 = 5 <= 10;
    const int A23 = (19 <= 10) == 0;
    const int A24 = 19 != 10;
    const int A25 = (10 != 10) == 0;
    const int A26 = (22 << 1) == 44;
    const int A27 = (11 >> 1) == 5;
    const int A28 = (1 || 0) == 1;
    const int A29 = (1 || 1) == 1;
    const int A30 = (0 || 0) == 0;
    const int A31 = (0 || 1) == 1;
    const int A32 = (1 && 0) == 0;
    const int A33 = (1 && 1) == 1;
    const int A34 = (0 && 0) == 0;
    const int A35 = (0 && 1) == 0;
    const int A36 = 4 == 4;
    const int A37 = -4 < 0;
    const int A38 = 0xffffffff == -1;
    const int A39 = 4 + 1 == 5;
    const int A40 = 2 + 3 - 4;
    const int A41 = 2 - 3 + 4 == 3;
    const int A42 = 1 == 4 == 0;
    const int A43 = 1 && 1;
    const int A44 = 1 || 1 && 0; // && higher than ||
    const int A45 = 1 < 2;
    const int A46 = !!((3 != 4 || (2 < 3 <= 3 > 4)) >= 0);
    const int A47 = !(1 == 7) && ((3 != 4 || (2 < 3 <= 3 > 4)) >= 0);
    const int A48 = (1 << 2) >= 0;
    const int A49 = (4 >> 1) == 2;
    const int A50 = (8 << -1) == 4;
    const int A51 = (1 << 30 >> 30) == 1;
    const int A52 = (1 | 16 >> 2) == 5;
    const int A53 = (0x0f ^ 0x33 & 0x99) == 0x1e; // & higher than ^
    const int A54 = (~42 & (1 << 3 | 16 >> 2) ^ 7) == 3;
    const int A55 = (2 + 3 - 4 * -7 / (10 % 3)) - 33 == 0;
    const int A56 = (2 + (-3 & 4 / 7)) == 2;
    const int A57 = (((((1 + 0)))));

    IOldName GetOldNameInterface();
    INewName GetNewNameInterface();

    // Retrieve the ICppJavaTests if the server supports it
    @nullable IBinder GetCppJavaTests();

    BackendType getBackendType();

    parcelable CompilerChecks {
        // IBinder
        IBinder binder;
        @nullable IBinder nullable_binder;
        IBinder[] binder_array;
        @nullable IBinder[] nullable_binder_array;
        List<IBinder> binder_list;
        @nullable List<IBinder> nullable_binder_list;

        // ParcelFileDescriptor
        ParcelFileDescriptor pfd;
        @nullable ParcelFileDescriptor nullable_pfd;
        ParcelFileDescriptor[] pfd_array;
        @nullable ParcelFileDescriptor[] nullable_pfd_array;
        List<ParcelFileDescriptor> pfd_list;
        @nullable List<ParcelFileDescriptor> nullable_pfd_list;

        // parcelable
        Empty parcel;
        @nullable Empty nullable_parcel;
        Empty[] parcel_array;
        @nullable Empty[] nullable_parcel_array;
        List<Empty> parcel_list;
        @nullable List<Empty> nullable_parcel_list;
    }
}
