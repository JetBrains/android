package test.pkg;

import android.support.media.ExifInterface;

import static android.support.media.ExifInterface.TAG_GPS_DEST_DISTANCE;

public class ExifUsage {
    private void setExifLatLong(String path, String lat, String lon) throws Exception {
        ExifInterface exif = new android.support.media.ExifInterface(path);
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, lat);
        exif.saveAttributes();
    }
}
