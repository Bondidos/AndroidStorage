package com.plcoding.androidstorage

import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.plcoding.androidstorage.databinding.ActivityMainBinding
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


    }

    private fun savePhotoToInternalStorage(filename: String, bmp: Bitmap): Boolean{
        return try{
            openFileOutput(filename, MODE_PRIVATE).use { stream ->

            }
        }catch (e: IOException){
            e.stackTrace
            false
        }
    }
}