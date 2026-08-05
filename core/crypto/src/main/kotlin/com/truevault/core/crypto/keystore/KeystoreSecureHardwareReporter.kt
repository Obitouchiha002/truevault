package com.truevault.core.crypto.keystore

import com.truevault.core.capabilities.SecureHardwareReporter
import com.truevault.core.capabilities.model.SecureHardwareCapability
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the Keystore's actual backing into the capability layer.
 *
 * Lives here because only this module may touch key material, and it is reported honestly: a device
 * whose backing cannot be determined is `UNKNOWN`, never optimistically upgraded to hardware.
 */
@Singleton
class KeystoreSecureHardwareReporter @Inject constructor(
    private val hardwareKeyStore: HardwareKeyStore,
) : SecureHardwareReporter {

    override fun capability(): SecureHardwareCapability = try {
        if (hardwareKeyStore.isHardwareBacked()) {
            SecureHardwareCapability.TRUSTED_ENVIRONMENT
        } else {
            SecureHardwareCapability.SOFTWARE_ONLY
        }
    } catch (e: Exception) {
        SecureHardwareCapability.UNKNOWN
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SecureHardwareModule {

    @Binds
    @Singleton
    abstract fun bindsReporter(impl: KeystoreSecureHardwareReporter): SecureHardwareReporter
}
