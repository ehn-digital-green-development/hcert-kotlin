package ehn.techiop.hcert.kotlin.trust

import COSE.OneKey
import ehn.techiop.hcert.kotlin.chain.VerificationResult
import ehn.techiop.hcert.kotlin.chain.ext.FixedClock
import ehn.techiop.hcert.kotlin.chain.impl.PrefilledCertificateRepository
import ehn.techiop.hcert.kotlin.chain.impl.RandomEcKeyCryptoService
import ehn.techiop.hcert.kotlin.chain.impl.RandomRsaKeyCryptoService
import ehn.techiop.hcert.kotlin.chain.impl.TrustListCertificateRepository
import ehn.techiop.hcert.kotlin.chain.toHexString
import ehn.techiop.hcert.kotlin.crypto.Certificate
import ehn.techiop.hcert.kotlin.crypto.JvmCertificate
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.lessThanOrEqualTo
import org.junit.jupiter.api.Test
import java.security.cert.X509Certificate


class TrustListTest {

    @Test
    fun v2serverClientExchange() {
        val clock = FixedClock(Instant.fromEpochMilliseconds(0))
        val cryptoService = RandomEcKeyCryptoService(clock = clock)
        val certificate = cryptoService.getCertificate()
        val encodeService = TrustListV2EncodeService(cryptoService, clock = clock)
        val trustListEncoded = encodeService.encodeContent(randomCertificates(clock))
        val trustListSignature = encodeService.encodeSignature(trustListEncoded)

        verifyClientOperations(certificate, clock, trustListSignature, trustListEncoded)
    }

    private fun verifyClientOperations(
        certificate: Certificate<*>,
        clock: Clock,
        trustListSignature: ByteArray,
        trustListEncoded: ByteArray? = null
    ) {
        // might never happen on the client, that the trust list is loaded in this way
        val clientTrustRoot = PrefilledCertificateRepository(certificate)
        val decodeService = TrustListDecodeService(clientTrustRoot, clock = clock)
        val clientTrustList = decodeService.decode(trustListSignature, trustListEncoded)
        // that's the way to go: Trust list used for verification of QR codes
        val clientTrustListAdapter =
            TrustListCertificateRepository(trustListSignature, trustListEncoded, clientTrustRoot, clock)

        assertThat(clientTrustList.size, equalTo(2))
        for (cert in clientTrustList) {
            assertThat(cert.validFrom.epochSeconds, lessThanOrEqualTo(clock.now().epochSeconds))
            assertThat(cert.validUntil.epochSeconds, greaterThanOrEqualTo(clock.now().epochSeconds))
            assertThat(cert.kid.size, equalTo(8))
            assertThat(cert.validContentTypes.size, equalTo(3))

            clientTrustListAdapter.loadTrustedCertificates(cert.kid, VerificationResult()).forEach {
                assertThat(
                    (it.cosePublicKey.toCoseRepresentation() as OneKey).EncodeToBytes(),
                    equalTo((cert.cosePublicKey.toCoseRepresentation() as OneKey).EncodeToBytes())
                )
            }
        }
    }


    private fun randomCertificates(clock: Clock): Set<X509Certificate> =
        listOf(RandomEcKeyCryptoService(clock = clock), RandomRsaKeyCryptoService(clock = clock))
            .map { (it.getCertificate() as JvmCertificate).certificate }
            .toSet()

}
