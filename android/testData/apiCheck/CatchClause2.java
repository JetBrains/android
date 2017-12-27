package test.pkg;

import android.media.MediaDrm;
import android.media.UnsupportedSchemeException;

import java.util.UUID;

public class Class {
    public static void test(UUID uuid) {
        try {
            if (uuid != null) {
                <error descr="Call requires API level 18 (current min is 1): new android.media.MediaDrm">new MediaDrm</error>(uuid);
            }
        } catch (<error descr="Class requires API level 21 (current min is 1): android.media.MediaDrm.MediaDrmStateException">MediaDrm.MediaDrmStateException</error> | <error descr="Class requires API level 18 (current min is 1): android.media.UnsupportedSchemeException">UnsupportedSchemeException</error> e) {
            e.printStackTrace();
        }
    }
}