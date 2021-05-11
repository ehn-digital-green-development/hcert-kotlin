package ehn.techiop.hcert.kotlin.trust

import ehn.techiop.hcert.kotlin.chain.VerificationResult
import ehn.techiop.hcert.kotlin.chain.impl.PrefilledCertificateRepository
import ehn.techiop.hcert.kotlin.chain.impl.RandomEcKeyCryptoService
import ehn.techiop.hcert.kotlin.chain.impl.RandomRsaKeyCryptoService
import ehn.techiop.hcert.kotlin.chain.impl.TrustListCertificateRepository
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.lessThanOrEqualTo
import org.junit.jupiter.api.Test
import java.security.cert.X509Certificate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class TrustListTest {

    @Test
    fun v1serverClientExchange() {
        val clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
        val cryptoService = RandomEcKeyCryptoService(clock = clock)
        val certificate = cryptoService.getCertificate()
        val trustListEncoded = TrustListV1EncodeService(cryptoService, clock = clock).encode(randomCertificates(clock))

        verifyClientOperations(certificate, clock, trustListEncoded)
    }

    @Test
    fun v2serverClientExchange() {
        val clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
        val cryptoService = RandomEcKeyCryptoService(clock = clock)
        val certificate = cryptoService.getCertificate()
        val trustListEncoded = TrustListV2EncodeService(cryptoService, clock = clock).encode(randomCertificates(clock))

        verifyClientOperations(certificate, clock, trustListEncoded)
    }

    private fun verifyClientOperations(
        certificate: X509Certificate,
        clock: Clock,
        trustListEncoded: ByteArray
    ) {
        // might never happen on the client, that the trust list is loaded in this way
        val clientTrustRoot = PrefilledCertificateRepository(certificate)
        val clientTrustList = TrustListDecodeService(clientTrustRoot, clock = clock).decode(trustListEncoded)
        // that's the way to go: Trust list used for verification of QR codes
        val clientTrustListAdapter = TrustListCertificateRepository(trustListEncoded, clientTrustRoot, clock)

        assertThat(clientTrustList.size, equalTo(2))
        for (cert in clientTrustList) {
            assertThat(cert.getValidFrom().epochSecond, lessThanOrEqualTo(clock.instant().epochSecond))
            assertThat(cert.getValidUntil().epochSecond, greaterThanOrEqualTo(clock.instant().epochSecond))
            assertThat(cert.getKid().size, equalTo(8))
            assertThat(cert.getValidContentTypes().size, equalTo(3))

            clientTrustListAdapter.loadTrustedCertificates(cert.getKid(), VerificationResult()).forEach {
                assertThat(it.buildOneKey().AsPublicKey(), equalTo(cert.buildOneKey().AsPublicKey()))
            }
        }
    }

    private fun randomCertificates(clock: Clock): Set<X509Certificate> =
        listOf(RandomEcKeyCryptoService(clock = clock), RandomRsaKeyCryptoService(clock = clock))
            .map { it.getCertificate() }
            .toSet()

}