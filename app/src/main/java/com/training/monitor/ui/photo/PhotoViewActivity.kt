// 촬영한 사진을 전체화면으로 보여주는 화면 — 로컬 Uri(촬영 직후 미리보기) 또는
// 서버에 저장된 기록의 recordId(사후 열람) 두 방식으로 열 수 있다.

package com.training.monitor.ui.photo

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.training.monitor.data.api.RetrofitClient
import com.training.monitor.databinding.ActivityPhotoViewBinding
import kotlinx.coroutines.launch

class PhotoViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoViewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnClose.setOnClickListener { finish() }

        val photoUri = intent.getStringExtra(EXTRA_PHOTO_URI)?.toUri()
        val recordId = intent.getLongExtra(EXTRA_RECORD_ID, -1L)
        when {
            photoUri != null -> binding.ivPhoto.setImageURI(photoUri)
            recordId != -1L -> loadPhotoFromServer(recordId)
        }
    }

    /** recordId로 서버의 사진 바이트를 받아와 디코딩해서 보여준다. 실패하면 안내 후 화면을 닫는다. */
    private fun loadPhotoFromServer(recordId: Long) {
        lifecycleScope.launch {
            val bitmap = try {
                val response = RetrofitClient.create(this@PhotoViewActivity).recordPhoto(recordId)
                val bytes = if (response.isSuccessful) response.body()?.bytes() else null
                bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            } catch (e: Exception) {
                null
            }

            if (bitmap != null) {
                binding.ivPhoto.setImageBitmap(bitmap)
            } else {
                Toast.makeText(this@PhotoViewActivity, "사진을 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    companion object {
        private const val EXTRA_PHOTO_URI = "photoUri"
        private const val EXTRA_RECORD_ID = "recordId"

        /** 아직 서버에 저장하지 않은 로컬 촬영 파일(Uri)을 바로 보여줄 때 사용. */
        fun newIntent(context: Context, photoUri: Uri): Intent =
            Intent(context, PhotoViewActivity::class.java)
                .putExtra(EXTRA_PHOTO_URI, photoUri.toString())

        /** 서버에 이미 저장된 기록의 사진을 recordId로 받아와 보여줄 때 사용. */
        fun newIntentForRecord(context: Context, recordId: Long): Intent =
            Intent(context, PhotoViewActivity::class.java)
                .putExtra(EXTRA_RECORD_ID, recordId)
    }
}
