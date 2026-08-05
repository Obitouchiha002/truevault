package com.truevault.core.crypto.keystore

import javax.crypto.Cipher
import javax.crypto.SecretKey

/**
 * Access to keys that live inside the Android Keystore.
 *
 * Keys obtained here are non-exportable: the returned [SecretKey] is a handle, not key bytes. Even
 * with full read access to the app's files, an attacker cannot take these keys off the device.
 *
 * This is an interface so the vault logic can be unit tested against an in-memory implementation —
 * the real Keystore is not available on the JVM.
 */
interface HardwareKeyStore {

    /**
     * The device-bound wrapping key. No user authentication is attached, because it is only ever the
     * *outer* layer: the vault master key underneath it is still sealed with the password-derived
     * key. Its job is to make the stored blob useless on any other device.
     */
    fun getOrCreateDeviceBoundKey(): SecretKey

    /**
     * The biometric-bound key, created only when the user opts into biometric unlock.
     *
     * Every use requires a fresh biometric authentication, and enrolling a new fingerprint or face
     * permanently invalidates it — so someone who adds their own biometric to a stolen unlocked
     * device does not inherit access to the vault.
     */
    fun getOrCreateBiometricBoundKey(): SecretKey

    fun hasBiometricBoundKey(): Boolean

    /** Removes the biometric key. Called when the user turns biometric unlock off. */
    fun deleteBiometricBoundKey()

    /** A [Cipher] ready to be passed to `BiometricPrompt.CryptoObject` for sealing. */
    fun biometricEncryptCipher(): Cipher

    /** A [Cipher] ready to be passed to `BiometricPrompt.CryptoObject` for opening. */
    fun biometricDecryptCipher(nonce: ByteArray): Cipher

    /**
     * Whether the device-bound key actually sits in secure hardware.
     *
     * Reported to the user rather than assumed. A software-backed Keystore still protects against
     * ordinary file access, but it is not the same guarantee, and claiming otherwise would be a lie.
     */
    fun isHardwareBacked(): Boolean

    /** True when the biometric key was invalidated by a new biometric enrolment. */
    fun isBiometricKeyInvalidated(): Boolean
}

/** Raised when the Keystore itself misbehaves. Carries no key material and no user data. */
class KeyStoreUnavailableException(message: String) : Exception(message)

/** Raised when a biometric-bound key was invalidated by a new enrolment. */
class BiometricKeyInvalidatedException : Exception("Biometric key invalidated by new enrolment")
