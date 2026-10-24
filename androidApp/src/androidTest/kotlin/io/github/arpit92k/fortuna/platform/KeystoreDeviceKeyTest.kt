package io.github.arpit92k.fortuna.platform

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.arpit92k.fortuna.core.ports.DeviceKeyException
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@RunWith(AndroidJUnit4::class)
class KeystoreDeviceKeyTest {
    // Test keys of their own, so the app's real key is never touched.
    private val alias = "fortuna-test-key"
    private val otherAlias = "fortuna-test-key-other"
    private val secret = Random.nextBytes(48)

    @After
    fun removeTestKeys() {
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            deleteEntry(alias)
            deleteEntry(otherAlias)
        }
    }

    // From the plan: a secret wrapped by the device key unwraps after the app is restarted.
    // A new adapter loads the Keystore afresh, as the app does when it starts again.
    @Test
    fun aWrappedSecretUnwrapsAfterARestart() {
        val wrapped = KeystoreDeviceKey(alias).wrap(secret)
        assertContentEquals(secret, KeystoreDeviceKey(alias).unwrap(wrapped))
    }

    @Test
    fun eachWrapUsesAFreshNonce() {
        val key = KeystoreDeviceKey(alias)
        assertFalse(key.wrap(secret).contentEquals(key.wrap(secret)))
    }

    @Test
    fun aChangedByteIsRefused() {
        val key = KeystoreDeviceKey(alias)
        val wrapped = key.wrap(secret)
        for (position in listOf(0, 20, wrapped.size - 1)) {
            val changed = wrapped.copyOf().also { it[position] = (it[position].toInt() xor 1).toByte() }
            assertFailsWith<DeviceKeyException> { key.unwrap(changed) }
        }
        assertFailsWith<DeviceKeyException> { key.unwrap(wrapped.copyOf(20)) }
    }

    @Test
    fun anotherKeysSecretIsRefused() {
        val wrapped = KeystoreDeviceKey(otherAlias).wrap(secret)
        KeystoreDeviceKey(alias).wrap(secret)
        assertFailsWith<DeviceKeyException> { KeystoreDeviceKey(alias).unwrap(wrapped) }
    }

    @Test
    fun withoutAKeyNothingUnwraps() {
        val wrapped = KeystoreDeviceKey(alias).wrap(secret)
        removeTestKeys()
        assertFailsWith<DeviceKeyException> { KeystoreDeviceKey(alias).unwrap(wrapped) }
    }
}
