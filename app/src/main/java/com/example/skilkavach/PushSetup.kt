package com.example.skilkavach

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging

object PushSetup {
    fun initialize(context: Context) {
        check(
            BuildConfig.FIREBASE_APP_ID.isNotBlank() &&
                BuildConfig.FIREBASE_API_KEY.isNotBlank() &&
                BuildConfig.FIREBASE_PROJECT_ID.isNotBlank() &&
                BuildConfig.FIREBASE_SENDER_ID.isNotBlank()
        ) {
            "Push notifications are not configured in this build. Updates remain available in the app inbox."
        }
        if (FirebaseApp.getApps(context).isEmpty())
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                    .build(),
            )
        FirebaseMessaging.getInstance().isAutoInitEnabled = true
    }
}
