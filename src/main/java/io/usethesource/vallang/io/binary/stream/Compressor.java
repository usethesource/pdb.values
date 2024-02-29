/**
 * Copyright (c) 2016, Davy Landman, Centrum Wiskunde & Informatica (CWI) All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided with
 * the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package io.usethesource.vallang.io.binary.stream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import com.github.luben.zstd.util.Native;

import io.usethesource.vallang.io.binary.util.ByteBufferInputStream;
import io.usethesource.vallang.io.binary.util.DirectZstdInputStream;

/* package */ final class Compressor {

    public static boolean zstdAvailable() {
        try {
            Native.load();
            return Native.isLoaded();
        }
        catch (Throwable t) {
            return false;
        }
    }

    public static OutputStream wrapStream(OutputStream rawStream, int algorithm, int level) throws IOException {
        switch (algorithm) {
            case Header.Compression.GZIP: {
                var result = new GZIPOutputStream(rawStream) {
                    public void setLevel(int level) {
                        def.setLevel(level);
                    }
                };
                result.setLevel(level);
                return result;
            }
            case Header.Compression.XZ: {
                return new XZOutputStream(rawStream, new LZMA2Options(level));
            }
            case Header.Compression.ZSTD: {
                return new ZstdOutputStream(rawStream, level);
            }
            default:
                return rawStream;
        }
    }

    public static InputStream wrapStream(InputStream raw, int algorithm) throws IOException {
        switch (algorithm) {
            case Header.Compression.NONE:
                return raw;
            case Header.Compression.GZIP:
                return new GZIPInputStream(raw);
            case Header.Compression.XZ:
                return new XZInputStream(raw);
            case Header.Compression.ZSTD:
                if (Compressor.zstdAvailable()) {
                    if (raw instanceof ByteBufferInputStream && ((ByteBufferInputStream)raw).getByteBuffer().isDirect()) {
                        return new DirectZstdInputStream((ByteBufferInputStream) raw);
                    }
                    return new ZstdInputStream(raw);
                }
                else {
                    throw new IOException("There is no native zstd library available for the current architecture.");
                }
            default:
                throw new IOException("Unsupported compression format");
        }
    }
}
