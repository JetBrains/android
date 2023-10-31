package com.example.targetsdkupgradesample32_33.ziptraversal

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.targetsdkupgradesample32_33.R
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class ZipPathTraversal : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_zip_path_traversal)

        val resoureId = R.raw.ziptest
        val resources = resources

        val inputStream = resources.openRawResource(resoureId)
        val zipInputStream = ZipInputStream(inputStream)
        var entry: ZipEntry? = null

        while (zipInputStream.nextEntry.also { entry = it } != null) {

            // Get the entry's name and size.
            val entryName = entry!!.name
            val entrySize = entry!!.size

            // Log the entry's name and size.
            println("Entry name: $entryName")
            println("Entry size: $entrySize")
        }
    }
}