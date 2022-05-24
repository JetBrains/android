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
        Parcelable error1   = in.readParcelable(getClass().getClassLoader());
        Parcelable[] error2 = in.readParcelableArray(null);
        Bundle error3       = in.readBundle(null);
        Object[] error4     = in.readArray(null);
        SparseArray error5  = in.readSparseArray(null);
        Object error6       = in.readValue(null);
        Parcelable error7   = in.readPersistableBundle(null);
        Bundle error8       = in.readBundle();
        Parcelable error9   = in.readPersistableBundle();

        Parcelable ok      = in.readParcelable(getClass().getClassLoader());
    }
}