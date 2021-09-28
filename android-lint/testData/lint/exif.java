package test.pkg;

import <warning descr="Avoid using `android.media.ExifInterface`; use `androidx.media.ExifInterface` instead">android.media.Exif<caret>Interface</warning>;

import static <warning descr="Avoid using `android.media.ExifInterface`; use `androidx.media.ExifInterface` instead">android.media.ExifInterface.TAG_GPS_DEST_DISTANCE</warning>;

public class ExifUsage {
    private void setExifLatLong(String path, String lat, String lon) throws Exception {
        ExifInterface exif = new <warning descr="Avoid using `android.media.ExifInterface`; use `androidx.media.ExifInterface` instead">android.media.ExifInterface</warning>(path);
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, lat);
        exif.saveAttributes();
    }
}
