package com.example.targetsdkupgradesample32_33.media;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.targetsdkupgradesample32_33.R;

import java.io.File;

public class MediaActivity extends AppCompatActivity {
    ImageView image ;

    private static final int MY_PERMISSIONS_ACCESS_IMAGE = 201;
    private static final int MY_PERMISSIONS_ACCESS_VIDEO = 202;
    private static final int MY_PERMISSIONS_ACCESS_AUDIO = 203;
    private static final int MY_PERMISSIONS_ACCESS_WRITE_EXTERNAL_STORAGE = 204;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media);

    }

    public void PickUpImage(View view) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[] { Manifest.permission.READ_EXTERNAL_STORAGE },
                    MY_PERMISSIONS_ACCESS_WRITE_EXTERNAL_STORAGE);


        }
        else {
            //  add new code

        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_PERMISSIONS_ACCESS_WRITE_EXTERNAL_STORAGE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // hotspotManager.turnOnHotspot();
                    Intent galleryIntent= new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    launchCameraActivity.launch(galleryIntent);
                }
            }
        }
    }

    ActivityResultLauncher<Intent> launchCameraActivity = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        Log.w("Image loaded",""+data.getData().getPath());

                        Bitmap photoBitmap;
                        if(data != null){
                            Uri selectedImageUri = data.getData();

                            final String path = getPathFromURI(selectedImageUri);

                            if (path != null) {
                                File f = new File(path);
                                selectedImageUri = Uri.fromFile(f);
                            }
                            // Set the image in ImageView
                            ImageView  im = (ImageView)  findViewById(R.id.imageView);
                            im.setImageURI(selectedImageUri);

                        }
                    }
                }
            });



    public String getPathFromURI(Uri contentUri) {
        String res = null;
        String[] proj = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(contentUri, proj, null, null, null);
        if (cursor.moveToFirst()) {
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            res = cursor.getString(column_index);
        }
        cursor.close();
        return res;
    }


   // @RequiresApi(api = Build.VERSION_CODES.S)

}


