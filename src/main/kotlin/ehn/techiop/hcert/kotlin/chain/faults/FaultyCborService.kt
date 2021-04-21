package ehn.techiop.hcert.kotlin.chain.faults

import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper
import com.upokecenter.cbor.CBORObject
import ehn.techiop.hcert.data.DigitalGreenCertificate
import ehn.techiop.hcert.kotlin.chain.impl.DefaultCborService

/**
 * Encodes the input without the required structure around it
 */
class FaultyCborService : DefaultCborService() {

    override fun encode(input: DigitalGreenCertificate): ByteArray {
        val cbor = CBORMapper().writeValueAsBytes(input)
        return CBORObject.FromObject(cbor).EncodeToBytes()
    }

}