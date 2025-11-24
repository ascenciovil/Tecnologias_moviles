package com.example.myapplication

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

data class CloudinaryUploadResponse(
    val secure_url: String?
)

interface CloudinaryApi {
    @Multipart
    @POST("v1_1/{cloudName}/image/upload")
    suspend fun uploadImage(
        @Path("cloudName") cloudName: String,
        @Part file: MultipartBody.Part,
        @Part("upload_preset") uploadPreset: RequestBody
    ): CloudinaryUploadResponse
}

object CloudinaryService {
    private const val BASE_URL = "https://api.cloudinary.com/"

    val api: CloudinaryApi by lazy {
        val retrofit = retrofit2.Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()

        retrofit.create(CloudinaryApi::class.java)
    }
}
