package com.plcoding.androidstorage

import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.plcoding.androidstorage.databinding.ActivityMainBinding
import com.plcoding.androidstorage.storageUtill.sdk29AndUp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.lang.Exception
import java.util.*
import java.util.jar.Manifest

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var internalStoragePhotoAdapter: InternalStoragePhotoAdapter
    private lateinit var sharedStoragePhotoAdapter: SharedPhotoAdapter


    private var readPermissionGranted = false
    private var writePermissonGranted = false
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        internalStoragePhotoAdapter = InternalStoragePhotoAdapter {
            val isDeletionSuccessfully = deletePhotoFromInternalStorage(it.name)
            if (isDeletionSuccessfully) {
                loadPhotosFromInternalStorageIntoRecyclerView()
                Toast.makeText(this, "Photo deleted successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to delete photo", Toast.LENGTH_SHORT).show()
            }
        }

        sharedStoragePhotoAdapter = SharedPhotoAdapter {

        }

        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
            readPermissionGranted = it[android.Manifest.permission.READ_EXTERNAL_STORAGE] ?: readPermissionGranted
            writePermissonGranted = it[android.Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: writePermissonGranted


        }
        updateOrRequestPermission()

        val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) {
            val isPrivate = binding.switchPrivate.isChecked
            val isSavedSuccessfully = when {
                isPrivate -> savePhotoToInternalStorage(UUID.randomUUID().toString(), it)
                writePermissonGranted -> savePhotoToExternalStorage(UUID.randomUUID().toString(), it)
                else -> false

            }
            if(isPrivate){
                loadPhotosFromInternalStorageIntoRecyclerView()
            }
            if (isSavedSuccessfully) {
                loadPhotosFromInternalStorageIntoRecyclerView()
                Toast.makeText(this, "Photo saved successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to save photo", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnTakePhoto.setOnClickListener {
            takePhoto.launch()
        }

        setupInternalStorageRecyclerView()
        loadPhotosFromInternalStorageIntoRecyclerView()
    }

    private fun updateOrRequestPermission() {
        val isReadPermissionGranted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        val isWritePermissionGranted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        val minSDK = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        readPermissionGranted = isReadPermissionGranted
        writePermissonGranted = isWritePermissionGranted || minSDK

        val permissionsToRequest = mutableListOf<String>()

        if (!readPermissionGranted) {
            permissionsToRequest.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (!writePermissonGranted) {
            permissionsToRequest.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun savePhotoToExternalStorage(displayName: String, bmp: Bitmap): Boolean {
        val imageCollection = sdk29AndUp {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.WIDTH, bmp.width)
            put(MediaStore.Images.Media.HEIGHT, bmp.height)
        }

        return try {
            contentResolver.insert(imageCollection, contentValues)?.also { uri ->
                contentResolver.openOutputStream(uri).use {
                    if (!bmp.compress((Bitmap.CompressFormat.JPEG), 95, it)){
                        throw IOException("Couldn't save bitmap")
                    }
                }
            } ?: throw IOException("Couldn't create Mediastore entry")
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }


    private fun setupInternalStorageRecyclerView() = binding.rvPrivatePhotos.apply {
        adapter = internalStoragePhotoAdapter
        layoutManager = StaggeredGridLayoutManager(3, RecyclerView.VERTICAL)
    }

    private fun loadPhotosFromInternalStorageIntoRecyclerView() {
        lifecycleScope.launch {
            val photos = loadFromInternalStorage()
            internalStoragePhotoAdapter.submitList(photos)
        }
    }

    private fun deletePhotoFromInternalStorage(fileName: String): Boolean {
        return try {
            deleteFile(fileName)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun loadFromInternalStorage(): List<InternalStoragePhoto> {
        return withContext(Dispatchers.IO) {
            val files = filesDir.listFiles()
            files?.filter { it.canRead() && it.isFile && it.name.endsWith("jpg") }?.map {
                val bytes = it.readBytes()
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                InternalStoragePhoto(it.name, bmp)
            } ?: emptyList()

        }
    }

    private fun savePhotoToInternalStorage(filename: String, bmp: Bitmap): Boolean {
        return try {
            openFileOutput("$filename.jpg", MODE_PRIVATE).use { stream ->
                if (!bmp.compress(Bitmap.CompressFormat.JPEG, 100, stream)) {
                    throw IOException("Couldn't save bitmap")
                }
            }
            true
        } catch (e: IOException) {
            e.stackTrace
            false
        }
    }
}