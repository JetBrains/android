package p1.p2;

import android.os.Parcel;
import android.os.Parcelable;

public class <warning>MyParcelable</warning> implements Parcelable {
  public static final Creator<MyParcelable> CREATOR = new Creator<MyParcelable>() {
    @Override
    public MyParcelable createFromParcel(Parcel source) {
      return new MyParcelable();
    }

    @Override
    public MyParcelable[] newArray(int size) {
      return new MyParcelable[size];
    }
  };

  public static final Creator<MyParcelable> <warning>CREATOR1</warning> = new Creator<MyParcelable>() {
    @Override
    public MyParcelable createFromParcel(Parcel source) {
      return new MyParcelable();
    }

    @Override
    public MyParcelable[] newArray(int size) {
      return new MyParcelable[size];
    }
  };

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
  }
}