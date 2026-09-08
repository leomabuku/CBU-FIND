package com.campus.lostandfound.data.remote

import com.campus.lostandfound.BuildConfig
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ApiFailure(
    val code: String,
    override val message: String,
    val retryable: Boolean,
    val referenceId: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    fun userMessage(): String = "$message Reference: $referenceId"
}

data class UploadSignature(
    val cloudName: String,
    val apiKey: String,
    val signature: String,
    val signatureAlgorithm: String,
    val timestamp: Long,
    val folder: String,
    val context: String,
    val resourceType: String
)

class WorkerApiClient(
    private val auth: FirebaseAuth,
    private val appCheck: FirebaseAppCheck
) {
    suspend fun request(
        path: String,
        method: String = "POST",
        body: JSONObject = JSONObject()
    ): JSONObject = try {
        requestAttempt(path, method, body, forceRefresh = false)
    } catch (failure: ApiFailure) {
        if (failure.code != "AUTH_EXPIRED" && failure.code != "APP_CHECK_REQUIRED") throw failure
        try {
            requestAttempt(path, method, body, forceRefresh = true)
        } catch (refreshedFailure: ApiFailure) {
            if (refreshedFailure.code == "AUTH_EXPIRED") auth.signOut()
            throw refreshedFailure
        }
    }

    private suspend fun requestAttempt(
        path: String,
        method: String,
        body: JSONObject,
        forceRefresh: Boolean
    ): JSONObject {
        val baseUrl = BuildConfig.WORKER_API_BASE_URL.trim().trimEnd('/')
        if (baseUrl.isBlank()) {
            throw ApiFailure(
                code = "CONFIGURATION_REQUIRED",
                message = "The trusted CBU Find API is not configured. Add WORKER_API_BASE_URL to local.properties and rebuild.",
                retryable = false,
                referenceId = "ANDROID-CONFIG"
            )
        }
        val user = auth.currentUser ?: throw ApiFailure("AUTH_REQUIRED", "Sign in again to continue.", false, "ANDROID-AUTH")
        val idToken = try {
            user.getIdToken(forceRefresh).await().token.orEmpty()
        } catch (error: Exception) {
            throw ApiFailure("AUTH_EXPIRED", "Your session expired. Sign in again, then retry.", false, "ANDROID-TOKEN", error)
        }
        val appCheckToken = try {
            appCheck.getAppCheckToken(forceRefresh).await().token
        } catch (error: Exception) {
            val message = if (BuildConfig.DEBUG) {
                "This development build is not registered with Firebase App Check. Register this device's debug token, then retry."
            } else {
                "This app installation could not be verified. Install the latest official build, then retry."
            }
            throw ApiFailure("APP_CHECK_REQUIRED", message, false, "ANDROID-APPCHECK", error)
        }

        return withContext(Dispatchers.IO) {
            val connection = try {
                (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    doInput = true
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $idToken")
                    setRequestProperty("X-Firebase-AppCheck", appCheckToken)
                    if (method != "GET" && method != "DELETE") {
                        doOutput = true
                        outputStream.use { it.write(body.toString().toByteArray()) }
                    }
                }
            } catch (error: Exception) {
                throw ApiFailure("DEPENDENCY_UNAVAILABLE", "CBU Find is offline. Check your connection and retry.", true, "ANDROID-NETWORK", error)
            }
            try {
                val status = connection.responseCode
                val responseText = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                val response = runCatching { JSONObject(responseText) }.getOrElse {
                    throw ApiFailure("DEPENDENCY_UNAVAILABLE", "The CBU Find service returned an invalid response. Please retry.", true, connection.getHeaderField("x-reference-id") ?: "ANDROID-HTTP-$status", it)
                }
                if (status !in 200..299 || !response.optBoolean("ok")) {
                    val error = response.optJSONObject("error") ?: JSONObject()
                    throw ApiFailure(
                        code = error.optString("code", "INTERNAL_ERROR"),
                        message = error.optString("message", "The request could not be completed."),
                        retryable = error.optBoolean("retryable", status >= 500),
                        referenceId = error.optString("referenceId", connection.getHeaderField("x-reference-id") ?: "ANDROID-HTTP-$status")
                    )
                }
                response.optJSONObject("data") ?: JSONObject().put("value", response.opt("data"))
            } catch (failure: ApiFailure) {
                throw failure
            } catch (error: Exception) {
                throw ApiFailure("DEPENDENCY_UNAVAILABLE", "CBU Find could not reach its trusted service. Check your connection and retry.", true, "ANDROID-NETWORK", error)
            } finally {
                connection.disconnect()
            }
        }
    }

    suspend fun signUpload(folder: String, resourceType: String): UploadSignature {
        val data = request("/v1/uploads/sign", body = JSONObject().put("folder", folder).put("resourceType", resourceType))
        return UploadSignature(
            cloudName = data.getString("cloudName"), apiKey = data.getString("apiKey"), signature = data.getString("signature"),
            signatureAlgorithm = data.getString("signatureAlgorithm"), timestamp = data.getLong("timestamp"), folder = data.getString("folder"),
            context = data.getString("context"), resourceType = data.getString("resourceType")
        )
    }
}
