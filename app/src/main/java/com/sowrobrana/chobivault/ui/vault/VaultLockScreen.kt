package com.sowrobrana.chobivault.ui.vault

import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.sowrobrana.chobivault.security.PinManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardOptions

/**
 * Vault gate: first run sets a PIN, later runs ask it.
 * The PIN is only a gate (salted PBKDF2 hash) — it never encrypts media,
 * so changing it never re-encrypts the vault. Fingerprint unlock is
 * offered when the device supports it.
 */
@Composable
fun VaultLockScreen(
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pinManager = remember { PinManager(context) }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val isSetup = !pinManager.hasPin()

    val activity = context as? FragmentActivity
    val canBiometric = remember {
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun tryBiometric() {
        val act = activity ?: return
        val prompt = BiometricPrompt(
            act,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    onUnlocked()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    Toast.makeText(context, errString, Toast.LENGTH_SHORT).show()
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("ভল্ট খুলুন")
                .setSubtitle("ফিঙ্গারপ্রিন্ট দিয়ে ভল্ট খুলুন")
                .setNegativeButtonText("PIN ব্যবহার করুন")
                .build(),
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (isSetup) "ভল্টের PIN বানান" else "ভল্ট খুলতে PIN দিন",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "ভল্টের ছবি শুধু এই ফোনেই থাকে, এনক্রিপ্ট করা অবস্থায়।",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
            label = { Text("PIN (৪–৬ সংখ্যা)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
        if (isSetup) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = confirmPin,
                onValueChange = {
                    if (it.length <= 6 && it.all { c -> c.isDigit() }) confirmPin = it
                },
                label = { Text("PIN আবার দিন") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            CoroutineScope(Dispatchers.IO).launch {
                if (isSetup) {
                    if (pin.length < 4) {
                        error = "PIN কমপক্ষে ৪ সংখ্যার হতে হবে"
                        return@launch
                    }
                    if (pin != confirmPin) {
                        error = "দুইবার একই PIN দিন"
                        return@launch
                    }
                    pinManager.setPin(pin)
                    android.os.Handler(android.os.Looper.getMainLooper())
                        .post { onUnlocked() }
                } else {
                    val ok = pinManager.verify(pin)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        if (ok) onUnlocked() else error = "ভুল PIN"
                    }
                }
            }
        }) {
            Text(if (isSetup) "PIN সেট করুন" else "ভল্ট খুলুন")
        }
        if (!isSetup && canBiometric) {
            TextButton(onClick = { tryBiometric() }) {
                Text("ফিঙ্গারপ্রিন্ট দিয়ে খুলুন")
            }
        }
    }
}
