package test.pkg;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.SparseArray;

@SuppressWarnings("unused")
@SuppressLint("NewApi")
public class ParcelClassLoaderTest {
    private void testParcelable(Parcel in) {
        Parcelable error1   = in.<warning descr="Passing null here (to use the default class loader) will not work if you are restoring your own classes. Consider using for example `getClass().getClassLoader()` instead.">readParcelable(null)</warning>;
        Parcelable[] error2 = in.<warning descr="Passing null here (to use the default class loader) will not work if you are restoring your own classes. Consider using for example `getClass().getClassLoader()` instead.">readParcelableArray(null)</warning>;
        Bundle error3       = in.<warning descr="Passing null here (to use the default class loader) will not work if you are restoring your own classes. Consider using for example `getClass().getClassLoader()` instead.">readBundle(null)</warning>;
        Object[] error4     = in.<warning descr="Passing null here (to use the default class loader) will not work if you are restoring your own classes. Consider using for example `getClass().getClassLoader()` instead.">readArray(null)</warning>;
        SparseArray error5  = in.<warning descr="Passing null here (to use the default class loader) will not work if you are restoring your own classes. Consider using for example `getClass().getClassLoader()` instead.">readSparseArray(null)</warning>;
        Object error6       = in.<warning descr="Passing null here (to use the default class loader) will not work if you are restoring your own classes. Consider using for example `getClass().getClassLoader()` instead.">readValue(null)</warning>;
        Parcelable error7   = in.<warning descr="Passing null here (to use the default class loader) will not work if you are restoring your own classes. Consider using for example `getClass().getClassLoader()` instead.">readPersistableBundle(null)</warning>;
        Bundle error8       = in.<warning descr="Using the default class loader will not work if you are restoring your own classes. Consider using for example `readBundle(getClass().getClassLoader())` instead.">read<caret>Bundle()</warning>;
        Parcelable error9   = in.<warning descr="Using the default class loader will not work if you are restoring your own classes. Consider using for example `readPersistableBundle(getClass().getClassLoader())` instead.">readPersistableBundle()</warning>;

        Parcelable ok      = in.readParcelable(getClass().getClassLoader());
    }
}