package com.cms.common.util;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility for generating time-ordered UUIDs (Version 7).
 *
 * <h2>Why UUID v7 over UUID v4?</h2>
 * <p>UUID v4 is completely random. When used as a primary key in PostgreSQL,
 * random UUIDs cause index fragmentation — each new row inserts at a random
 * position in the B-tree index, causing page splits and poor cache locality.
 * Under write load, this degrades both insert performance and read performance.
 *
 * <p>UUID v7 embeds the current Unix timestamp in milliseconds in the most
 * significant 48 bits. New UUIDs are monotonically increasing within the same
 * millisecond, so they insert at the "end" of the B-tree index — same locality
 * as an auto-increment integer, but globally unique without a sequence.
 *
 * <h2>Format (RFC 9562)</h2>
 * <pre>
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                         unix_ts_ms (48 bits)                    |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |  ver (4) = 0111 |         rand_a (12 bits)                      |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |var (2) = 10 |                rand_b (62 bits)                   |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 */
public final class UuidUtils {

    private UuidUtils() {
        // Utility class — no instantiation
    }

    /**
     * Generates a new UUID v7 (time-ordered, random).
     *
     * <p>Thread-safe — uses {@link ThreadLocalRandom} for the random bits.
     *
     * @return a new UUID v7
     */
    public static UUID generateV7() {
        long timestampMs = Instant.now().toEpochMilli();

        // Most significant bits:
        // bits 0-47  : Unix timestamp in milliseconds (48 bits)
        // bits 48-51 : Version = 7 (0111)
        // bits 52-63 : Random (12 bits)
        long msb = (timestampMs << 16);                     // shift timestamp into top 48 bits
        msb &= 0xFFFFFFFFFFFF0000L;                         // ensure bottom 16 bits are clean
        msb |= 0x7000L;                                     // set version bits (0111 = 7)
        msb |= (ThreadLocalRandom.current().nextLong() & 0x0FFFL); // 12 random bits

        // Least significant bits:
        // bits 0-1  : Variant = 10 (RFC 4122 / RFC 9562)
        // bits 2-63 : Random (62 bits)
        long lsb = ThreadLocalRandom.current().nextLong();
        lsb &= 0x3FFFFFFFFFFFFFFFL;  // clear top 2 bits
        lsb |= 0x8000000000000000L;  // set variant bits (10)

        return new UUID(msb, lsb);
    }

    /**
     * Convenience method — generates a UUID v7 and returns it as a string.
     *
     * @return UUID v7 in standard hyphenated string format
     */
    public static String generateV7String() {
        return generateV7().toString();
    }
}
