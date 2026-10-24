package com.sowrobrana.chobivault

import android.app.Application

/**
 * Application entry point. Keeps the vault key alias namespaced to
 * ChobiVault (see crypto/CryptoManager) — never shared with PhotoLock.
 */
class ChobiVaultApp : Application()
