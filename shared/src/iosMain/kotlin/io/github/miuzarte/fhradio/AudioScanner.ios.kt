package io.github.miuzarte.fhradio

import io.github.miuzarte.fhradio.model.RadioConfig

actual class AudioScanner {
    actual fun verifyOnly(config: RadioConfig, folderPath: String): VerifyResult = VerifyResult(emptyList())
}
