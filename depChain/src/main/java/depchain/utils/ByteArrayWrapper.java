package depchain.utils;

import java.util.Arrays;

import java.io.Serializable;

public class ByteArrayWrapper implements Serializable {
    private static final long serialVersionUID = 1L;
    private final byte[] data;

    public ByteArrayWrapper(byte[] data) {
        this.data = data;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ByteArrayWrapper)) {
            return false;
        }
        return Arrays.equals(data, ((ByteArrayWrapper) other).data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    public byte[] getData() {
        return data;
    }
}
