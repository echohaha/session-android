package org.thoughtcrime.securesms.loki

import android.content.Context
import okhttp3.*
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.whispersystems.libsignal.logging.Log
import org.whispersystems.signalservice.internal.util.JsonUtil
import org.whispersystems.signalservice.loki.api.LokiPushNotificationAcknowledgement
import java.io.IOException

object LokiPushNotificationManager {
    private val connection = OkHttpClient()

    private val server by lazy {
        LokiPushNotificationAcknowledgement.shared.server
    }

    private const val tokenExpirationInterval = 2 * 24 * 60 * 60 * 1000

    @JvmStatic
    fun unregister(token: String, context: Context?) {
        val parameters = mapOf( "token" to token )
        val url = "${server}/register"
        val body = RequestBody.create(MediaType.get("application/json"), JsonUtil.toJson(parameters))
        val request = Request.Builder().url(url).post(body).build()
        connection.newCall(request).enqueue(object : Callback {

            override fun onResponse(call: Call, response: Response) {
                when (response.code()) {
                    200 -> {
                        val bodyAsString = response.body()!!.string()
                        val json = JsonUtil.fromJson(bodyAsString, Map::class.java)
                        val code  = json?.get("code") as? Int
                        if (code != null && code != 0) {
                            TextSecurePreferences.setIsUsingFCM(context, false)
                        } else {
                            Log.d("Loki", "Couldn't disable FCM due to error: ${json?.get("message") as? String ?: "null"}.")
                        }
                    }
                }
            }

            override fun onFailure(call: Call, exception: IOException) {
                Log.d("Loki", "Couldn't disable FCM.")
            }
        })
    }

    @JvmStatic
    fun register(token: String, hexEncodedPublicKey: String, context: Context?, force: Boolean) {
        val oldToken = TextSecurePreferences.getFCMToken(context)
        val lastUploadDate = TextSecurePreferences.getLastFCMUploadTime(context)
        if (!force && token == oldToken && System.currentTimeMillis() - lastUploadDate < tokenExpirationInterval) {  return }
        val parameters = mapOf( "token" to token, "pubKey" to hexEncodedPublicKey )
        val url = "${server}/register"
        val body = RequestBody.create(MediaType.get("application/json"), JsonUtil.toJson(parameters))
        val request = Request.Builder().url(url).post(body).build()
        connection.newCall(request).enqueue(object : Callback {

            override fun onResponse(call: Call, response: Response) {
                when (response.code()) {
                    200 -> {
                        val bodyAsString = response.body()!!.string()
                        val json = JsonUtil.fromJson(bodyAsString, Map::class.java)
                        val code  = json?.get("code") as? Int
                        if (code != null && code != 0) {
                            TextSecurePreferences.setIsUsingFCM(context, true)
                            TextSecurePreferences.setFCMToken(context, token)
                            TextSecurePreferences.setLastFCMUploadTime(context, System.currentTimeMillis())
                        } else {
                            Log.d("Loki", "Couldn't register for FCM due to error: ${json?.get("message") as? String ?: "null"}.")
                        }
                    }
                }
            }

            override fun onFailure(call: Call, exception: IOException) {
                Log.d("Loki", "Couldn't register for FCM.")
            }
        })
    }
}
