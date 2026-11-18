package com.example

import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.ui.auth.AuthScreen
import com.example.ui.navigation.AppMainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.DailyFinanceReminderManager
import com.example.util.configureAppCheck
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure explicit sRGB RGBA_8888 window surface defaults so HWUI avoids
        // uninitialized dataspace=0 / 101010-2 wide-color probing on OEM GPUs.
        window.setFormat(PixelFormat.RGBA_8888)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
        }

        // Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)
        DailyFinanceReminderManager.ensureNotificationChannel(this)
        DailyFinanceReminderManager.rescheduleIfEnabled(this)

        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent()
                }
            }
        }
    }
}

@Composable
fun AppContent() {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    if (currentUser == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
            }
        )
    } else {
        val uid = currentUser!!.uid
        // Scoped by uid so sign out or user switch safely recreates state
        key(uid) {
            AppMainScreen(currentUserId = uid)
        }
    }
}
