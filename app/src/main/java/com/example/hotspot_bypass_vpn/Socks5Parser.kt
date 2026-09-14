package com.example.hotspot_bypass_vpn

import java.io.EOFException
import java.io.InputStream
import java.net.InetAddress

internal object Socks5Parser {
    const val VERSION = 5
    const val METHOD_NO_AUTH = 0
    const val METHOD_NOT_ACCEPTABLE = 0xFF

    const val COMMAND_CONNECT = 1
    const val COMMAND_UDP_ASSOCIATE = 3

    const val REPLY_SUCCEEDED = 0
    const val REPLY_GENERAL_FAILURE = 1
    const val REPLY_CONNECTION_REFUSED = 5
    const val REPLY_COMMAND_NOT_SUPPORTED = 7
    const val REPLY_ADDRESS_NOT_SUPPORTED = 8

    data class Greeting(val supportsNoAuthentication: Boolean)

    data class Request(
        val command: Int,
        val addressType: Int,
        val targetHost: String,
        val targetPort: Int
    )

    data class UdpTarget(
        val addressType: Int,
        val targetHost: String,
        val targetPort: Int,
        val headerLength: Int
    )

    class ProtocolException(
        message: String,
        val replyCode: Int = REPLY_GENERAL_FAILURE
    ) : Exception(message)

    fun readGreeting(input: InputStream): Greeting {
        val version = readByte(input, "greeting version")
        if (version != VERSION) throw ProtocolException("Unsupported SOCKS version: $version")

        val methodCount = readByte(input, "authentication method count")
        if (methodCount == 0) throw ProtocolException("SOCKS greeting did not include any methods")

        val methods = readExactly(input, methodCount, "authentication methods")
        return Greeting(methods.any { (it.toInt() and 0xFF) == METHOD_NO_AUTH })
    }

    fun readRequest(input: InputStream): Request {
        val version = readByte(input, "request version")
        if (version != VERSION) throw ProtocolException("Unsupported request version: $version")

        val command = readByte(input, "request command")
        val reserved = readByte(input, "reserved byte")
        if (reserved != 0) throw ProtocolException("Reserved byte must be zero")

        val addressType = readByte(input, "address type")
        val targetHost = readAddress(input, addressType)
        val portBytes = readExactly(input, 2, "target port")
        val targetPort = ((portBytes[0].toInt() and 0xFF) shl 8) or
            (portBytes[1].toInt() and 0xFF)

        return Request(command, addressType, targetHost, targetPort)
    }

    fun parseUdpHeader(data: ByteArray, packetLength: Int): UdpTarget? {
        if (packetLength < 4 || packetLength > data.size) return null
        if (data[0].toInt() != 0 || data[1].toInt() != 0) return null
        if ((data[2].toInt() and 0xFF) != 0) return null // Fragmentation is unsupported by SOCKS5 here.

        var offset = 4
        val addressType = data[3].toInt() and 0xFF
        val address = readAddress(data, packetLength, offset, addressType) ?: return null
        offset = address.second
        if (offset + 2 > packetLength) return null

        val targetPort = ((data[offset].toInt() and 0xFF) shl 8) or
            (data[offset + 1].toInt() and 0xFF)
        offset += 2
        if (targetPort == 0) return null

        return UdpTarget(addressType, address.first, targetPort, offset)
    }

    fun buildMethodSelection(method: Int): ByteArray =
        byteArrayOf(VERSION.toByte(), method.toByte())

    fun buildReply(replyCode: Int, bindPort: Int = 0): ByteArray = byteArrayOf(
        VERSION.toByte(),
        replyCode.toByte(),
        0,
        1,
        0,
        0,
        0,
        0,
        (bindPort ushr 8).toByte(),
        (bindPort and 0xFF).toByte()
    )

    fun buildUdpHeader(address: InetAddress, port: Int): ByteArray {
        val addressBytes = address.address
        val addressType = when (addressBytes.size) {
            4 -> 1
            16 -> 4
            else -> throw IllegalArgumentException("Unsupported address length: ${addressBytes.size}")
        }
        return ByteArray(4 + addressBytes.size + 2).also { header ->
            header[3] = addressType.toByte()
            System.arraycopy(addressBytes, 0, header, 4, addressBytes.size)
            header[header.size - 2] = (port ushr 8).toByte()
            header[header.size - 1] = (port and 0xFF).toByte()
        }
    }

    private fun readAddress(input: InputStream, addressType: Int): String = when (addressType) {
        1 -> InetAddress.getByAddress(readExactly(input, 4, "IPv4 address")).hostAddress
            ?: throw ProtocolException("Invalid IPv4 address", REPLY_ADDRESS_NOT_SUPPORTED)
        3 -> {
            val length = readByte(input, "domain length")
            if (length == 0) throw ProtocolException("Domain name cannot be empty", REPLY_ADDRESS_NOT_SUPPORTED)
            String(readExactly(input, length, "domain name"), Charsets.UTF_8)
        }
        4 -> InetAddress.getByAddress(readExactly(input, 16, "IPv6 address")).hostAddress
            ?: throw ProtocolException("Invalid IPv6 address", REPLY_ADDRESS_NOT_SUPPORTED)
        else -> throw ProtocolException("Unsupported address type: $addressType", REPLY_ADDRESS_NOT_SUPPORTED)
    }

    private fun readAddress(
        data: ByteArray,
        packetLength: Int,
        startOffset: Int,
        addressType: Int
    ): Pair<String, Int>? {
        var offset = startOffset
        return when (addressType) {
            1 -> {
                if (offset + 4 > packetLength) return null
                val address = InetAddress.getByAddress(data.copyOfRange(offset, offset + 4)).hostAddress ?: return null
                offset += 4
                address to offset
            }
            3 -> {
                if (offset >= packetLength) return null
                val length = data[offset].toInt() and 0xFF
                offset++
                if (length == 0 || offset + length > packetLength) return null
                val address = String(data, offset, length, Charsets.UTF_8)
                offset += length
                address to offset
            }
            4 -> {
                if (offset + 16 > packetLength) return null
                val address = InetAddress.getByAddress(data.copyOfRange(offset, offset + 16)).hostAddress ?: return null
                offset += 16
                address to offset
            }
            else -> null
        }
    }

    private fun readByte(input: InputStream, field: String): Int {
        val value = input.read()
        if (value < 0) throw EOFException("Unexpected EOF while reading $field")
        return value
    }

    private fun readExactly(input: InputStream, length: Int, field: String): ByteArray {
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = input.read(bytes, offset, length - offset)
            if (count < 0) throw EOFException("Unexpected EOF while reading $field")
            if (count == 0) continue
            offset += count
        }
        return bytes
    }
}
