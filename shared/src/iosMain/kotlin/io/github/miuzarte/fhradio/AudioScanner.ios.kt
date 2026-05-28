package io.github.miuzarte.fhradio

import io.github.miuzarte.fhradio.model.RadioInfo

actual class AudioScanner {
    actual fun verifyOnly(config: RadioInfo, folderPath: String): VerifyResult = VerifyResult(emptyList())
}
