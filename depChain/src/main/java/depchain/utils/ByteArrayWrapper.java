package depchain.utils;

import java.util.Arrays;

public class ByteArrayWrapper {
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
