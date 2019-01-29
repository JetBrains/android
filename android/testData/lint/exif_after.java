package test.pkg;

import androidx.exifinterface.media.ExifInterface;

import static androidx.exifinterface.media.ExifInterface.TAG_GPS_DEST_DISTANCE;

public class ExifUsage {
    private void setExifLatLong(String path, String lat, String lon) throws Exception {
        ExifInterface exif = new androidx.exifinterface.media.ExifInterface(path);
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, lat);
        exif.saveAttributes();
    }
}
