// 촬영한 사진을 전체화면으로 보여주는 화면

package com.training.monitor.ui.photo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.training.monitor.databinding.ActivityPhotoViewBinding

class PhotoViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoViewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val photoUri = intent.getStringExtra(EXTRA_PHOTO_URI)?.toUri()
        binding.ivPhoto.setImageURI(photoUri)

        binding.btnClose.setOnClickListener { finish() }
    }

    companion object {
        private const val EXTRA_PHOTO_URI = "photoUri"

        fun newIntent(context: Context, photoUri: Uri): Intent =
            Intent(context, PhotoViewActivity::class.java)
                .putExtra(EXTRA_PHOTO_URI, photoUri.toString())
    }
}
