package dk.defxws.fedoragsearch.server.utils;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.defxws.fedoragsearch.server.errors.GenericSearchException;

public final class IOUtils {

    private static final Logger LOG = LoggerFactory.getLogger(IOUtils.class);

    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;

    private static final String UTF8 = "UTF8";

    private IOUtils() {
    }

    public static int copy(final InputStream input, final OutputStream output) throws IOException {
        return copy(input, output, DEFAULT_BUFFER_SIZE);
    }

    public static int copyAndCloseInput(final InputStream input, final OutputStream output)
            throws IOException {
        try {
            return copy(input, output, DEFAULT_BUFFER_SIZE);
        } finally {
            closeStream(input);
        }
    }

    public static int copyAndCloseInput(final InputStream input, final OutputStream output,
                                        final int bufferSize) throws IOException {
        try {
            return copy(input, output, bufferSize);
        } finally {
            closeStream(input);
        }
    }

    public static int copy(final InputStream input, final OutputStream output, int bufferSize)
            throws IOException {
        int avail = input.available();
        if(avail > 262144) {
            avail = 262144;
        }
        if(avail > bufferSize) {
            bufferSize = avail;
        }
        final byte[] buffer = new byte[bufferSize];
        int n = input.read(buffer);
        int total = 0;
        while(- 1 != n) {
            output.write(buffer, 0, n);
            total += n;
            n = input.read(buffer);
        }
        return total;
    }

    public static int copy(final Reader input, final OutputStream output) throws IOException {
        return copy(input, output, DEFAULT_BUFFER_SIZE);
    }

    public static int copy(final Reader input, final OutputStream output, int bufferSize)
            throws IOException {
        final char[] buffer = new char[bufferSize];
        int n = input.read(buffer);
        int total = 0;
        while(- 1 != n) {
            output.write(new String(buffer).getBytes("UTF-8"), 0, n);
            total += n;
            n = input.read(buffer);
        }
        return total;
    }

    public static String newStringFromBytes(final byte[] bytes, final String charsetName) {
        try {
            return new String(bytes, charsetName);
        } catch(UnsupportedEncodingException e) {
            throw new RuntimeException(
                    "Impossible failure: Charset.forName(\"" + charsetName + "\") returns " + "invalid name.");
        }
    }

    public static String newStringFromBytes(final byte[] bytes) {
        return newStringFromBytes(bytes, UTF8);
    }

    public static String newStringFromBytes(final byte[] bytes, final String charsetName, final int start,
                                            final int length) {
        try {
            return new String(bytes, start, length, charsetName);
        } catch(UnsupportedEncodingException e) {
            throw new RuntimeException(
                    "Impossible failure: Charset.forName(\"" + charsetName + "\") returns invalid " + "name.");

        }
    }

    public static String newStringFromBytes(final byte[] bytes, final int start, final int length) {
        return newStringFromBytes(bytes, UTF8, start, length);
    }

    public static String newStringFromStream(final InputStream input, final String charsetName) throws IOException {
        int i = input.available();
        if(i < DEFAULT_BUFFER_SIZE) {
            i = DEFAULT_BUFFER_SIZE;
        }
        final ByteArrayOutputStream bos = new ByteArrayOutputStream(i);
        copy(input, bos);
        closeStream(input);
        return bos.toString(charsetName);
    }

    public static String newStringFromStream(final InputStream input) throws IOException {
        return newStringFromStream(input, UTF8);
    }

    public static void closeStream(final Closeable closeable) {
        if(closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch(final IOException e) {
            LOG.error("Error on closing stream.", e);
        }
    }

    // TODO: Temporal workaround!
    public static StringBuffer convertStreamToStringBuffer(final Stream stream) throws GenericSearchException {
        StringBuffer resultXml = new StringBuffer();
        try {
            stream.writeCacheTo(resultXml);
            stream.close();
        } catch(IOException e) {
            throw new GenericSearchException("Error converting Stream to string.", e);
        }
        return resultXml;
    }

}

