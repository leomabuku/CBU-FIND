package com.campus.lostandfound.data.remote

import com.campus.lostandfound.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class CloudinaryUploadResult(
    val secureUrl: String,
    val publicId: String
)

class CloudinaryUploader {
    suspend fun uploadImage(
        bytes: ByteArray,
        userId: String,
        folder: String
    ): CloudinaryUploadResult = withContext(Dispatchers.IO) {
        upload(
            bytes = bytes,
            userId = userId,
            folder = folder,
            fileName = "cbu-find-${UUID.randomUUID()}.jpg",
            contentType = "image/jpeg",
            resourceType = "image"
        )
    }

    suspend fun uploadMedia(
        bytes: ByteArray,
        userId: String,
        folder: String,
        fileName: String,
        contentType: String
    ): CloudinaryUploadResult = withContext(Dispatchers.IO) {
        upload(
            bytes = bytes,
            userId = userId,
            folder = folder,
            fileName = fileName,
            contentType = contentType,
            resourceType = "auto"
        )
    }

    private fun upload(
        bytes: ByteArray,
        userId: String,
        folder: String,
        fileName: String,
        contentType: String,
        resourceType: String
    ): CloudinaryUploadResult {
        val cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME.trim()
        val uploadPreset = BuildConfig.CLOUDINARY_UPLOAD_PRESET.trim()

        require(cloudName.isNotBlank() && uploadPreset.isNotBlank()) {
            "Cloudinary is not configured. Add CLOUDINARY_CLOUD_NAME and CLOUDINARY_UPLOAD_PRESET to local.properties, then rebuild the app."
        }

        val boundary = "CampusFindBoundary${UUID.randomUUID()}"
        val connection = (URL("https://api.cloudinary.com/v1_1/$cloudName/$resourceType/upload").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 75_000
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        val requestBody = ByteArrayOutputStream().use { output ->
            output.writeFormField(boundary, "upload_preset", uploadPreset)
            output.writeFormField(boundary, "context", "app=cbu_find|uploaded_by=$userId|kind=$folder")
            output.writeFileField(boundary, "file", fileName, contentType, bytes)
            output.write("--$boundary--\r\n".toByteArray())
            output.toByteArray()
        }

        connection.outputStream.use { it.write(requestBody) }

        val statusCode = connection.responseCode
        val response = if (statusCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
        connection.disconnect()

        if (statusCode !in 200..299) {
            throw IllegalStateException(readableCloudinaryError(statusCode, response))
        }

        val json = JSONObject(response)
        return CloudinaryUploadResult(
            secureUrl = json.optString("secure_url"),
            publicId = json.optString("public_id")
        ).also {
            require(it.secureUrl.isNotBlank()) {
                "Cloudinary uploaded the image but did not return a secure URL."
            }
        }
    }

    private fun ByteArrayOutputStream.writeFormField(boundary: String, name: String, value: String) {
        write("--$boundary\r\n".toByteArray())
        write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
        write(value.toByteArray())
        write("\r\n".toByteArray())
    }

    private fun ByteArrayOutputStream.writeFileField(
        boundary: String,
        name: String,
        fileName: String,
        contentType: String,
        bytes: ByteArray
    ) {
        write("--$boundary\r\n".toByteArray())
        write("Content-Disposition: form-data; name=\"$name\"; filename=\"$fileName\"\r\n".toByteArray())
        write("Content-Type: $contentType\r\n\r\n".toByteArray())
        write(bytes)
        write("\r\n".toByteArray())
    }

    private fun readableCloudinaryError(statusCode: Int, response: String): String {
        val cloudinaryMessage = runCatching {
            JSONObject(response).optJSONObject("error")?.optString("message")
        }.getOrNull().orEmpty()

        val message = cloudinaryMessage.ifBlank { response.ifBlank { "No response body returned." } }

        return when {
            "upload preset" in message.lowercase() ->
                "Cloudinary rejected the upload preset. Confirm the preset exists and is unsigned. $message"
            "cloud name" in message.lowercase() || statusCode == 404 ->
                "Cloudinary could not find this cloud. Confirm CLOUDINARY_CLOUD_NAME in local.properties. $message"
            statusCode == 401 || statusCode == 403 ->
                "Cloudinary rejected the upload. Confirm the upload preset is unsigned and not restricted too tightly. $message"
            else -> "Cloudinary upload failed with HTTP $statusCode. $message"
        }
    }
}
