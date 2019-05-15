export namespace mp3 {
    class Mp3Encoder {
        constructor(channels?: number, samplerate?: number, kbps?: number)

        encodeBuffer(left: Int16Array, right: Int16Array): Int8Array

        encodeSamples(left: Float32Array, right: Float32Array): Int8Array

        flush(): Int8Array
    }
}
