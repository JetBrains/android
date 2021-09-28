package test.pkg;

import android.os.Parcel;
import android.os.Parcelable;

public class ParcelableDemo {
    private interface MoreThanParcelable extends Parcelable {
        void somethingMore();
    }

    private static abstract class AbstractParcelable implements Parcelable {
    }

    private static class <error descr="This class implements `Parcelable` but does not provide a `CREATOR` field">JustParcelable</error> implements Parcelable {
        public int describeContents() {return 0;}
        public void writeToParcel(Parcel dest, int flags) {}
    }

    private static class <error descr="This class implements `Parcelable` but does not provide a `CREATOR` field">JustParcelableSubclass</error> extends JustParcelable {
    }

    private static class <error descr="This class implements `Parcelable` but does not provide a `CREATOR` field">ParcelableThroughAbstractSuper</error> extends AbstractParcelable {
        public int describeContents() {return 0;}
        public void writeToParcel(Parcel dest, int flags) {}
    }

    private static class <error descr="This class implements `Parcelable` but does not provide a `CREATOR` field">ParcelableThroughInterface</error> implements MoreThanParcelable {
        public int describeContents() {return 0;}
        public void writeToParcel(Parcel dest, int flags) {}
        public void somethingMore() {}
    }
}