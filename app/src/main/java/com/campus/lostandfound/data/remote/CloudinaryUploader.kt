package com.campus.lostandfound.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class CloudinaryUploadResult(
    val secureUrl: String,
    val publicId: String,
    val resourceType: String,
    val format: String,
    val bytes: Long,
    val width: Int,
    val height: Int,
    val originalName: String
)

class CloudinaryUploader(private val api: WorkerApiClient) {
    suspend fun uploadImage(bytes: ByteArray, folder: String): CloudinaryUploadResult =
        upload(bytes, folder, "cbu-find-${UUID.randomUUID()}.jpg", "image/jpeg", "image")

    suspend fun uploadMedia(bytes: ByteArray, folder: String, fileName: String, contentType: String): CloudinaryUploadResult =
        upload(bytes, folder, fileName, contentType, "auto")

    private suspend fun upload(bytes: ByteArray, folder: String, fileName: String, contentType: String, resourceType: String): CloudinaryUploadResult {
        val signing = api.signUpload(folder, resourceType)
        return withContext(Dispatchers.IO) {
            val boundary = "CampusFindBoundary${UUID.randomUUID()}"
            val connection = (URL("https://api.cloudinary.com/v1_1/${signing.cloudName}/${signing.resourceType}/upload").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 20_000; readTimeout = 75_000; doInput = true; doOutput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }
            val requestBody = ByteArrayOutputStream().use { output ->
                output.field(boundary, "api_key", signing.apiKey); output.field(boundary, "timestamp", signing.timestamp.toString())
                output.field(boundary, "signature", signing.signature); output.field(boundary, "signature_algorithm", signing.signatureAlgorithm)
                output.field(boundary, "folder", signing.folder); output.field(boundary, "context", signing.context)
                output.file(boundary, "file", fileName, contentType, bytes); output.write("--$boundary--\r\n".toByteArray()); output.toByteArray()
            }
            connection.outputStream.use { it.write(requestBody) }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            if (status !in 200..299) throw ApiFailure("DEPENDENCY_UNAVAILABLE", "The media upload failed. Retry, or continue without an attachment.", true, "CLOUDINARY-$status")
            val json = JSONObject(response)
            CloudinaryUploadResult(
                secureUrl = json.getString("secure_url"), publicId = json.getString("public_id"), resourceType = json.optString("resource_type", resourceType),
                format = json.optString("format"), bytes = json.optLong("bytes", bytes.size.toLong()), width = json.optInt("width"), height = json.optInt("height"),
                originalName = json.optString("original_filename", fileName)
            )
        }
    }

    private fun ByteArrayOutputStream.field(boundary: String, name: String, value: String) {
        write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
    }
    private fun ByteArrayOutputStream.file(boundary: String, name: String, fileName: String, contentType: String, bytes: ByteArray) {
        write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"; filename=\"$fileName\"\r\nContent-Type: $contentType\r\n\r\n".toByteArray()); write(bytes); write("\r\n".toByteArray())
    }
}
