package com.example.myapplication.offline

import com.example.myapplication.CloudinaryService
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

object CloudinaryUploader {
    private const val CLOUDINARY_CLOUD_NAME = "dof4gj5pr"
    private const val CLOUDINARY_UPLOAD_PRESET = "rutas_fotos"

    suspend fun uploadFile(file: File): String? {
        val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
        val presetBody = CLOUDINARY_UPLOAD_PRESET.toRequestBody("text/plain".toMediaTypeOrNull())

        val response = CloudinaryService.api.uploadImage(
            CLOUDINARY_CLOUD_NAME,
            body,
            presetBody
        )
        return response.secure_url
    }
}
