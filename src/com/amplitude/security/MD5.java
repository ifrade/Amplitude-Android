package com.amplitude.security;

/* @(#)MD5.java	1.13 2006-02-11
 * This file was freely contributed to the LimeWire project  and is covered
 * by its existing GPL licence, but it may be used individually as a public
 * domain implementation of a published algorithm (see below for references).
 * It was also freely contributed to the Bitzi public domain sources.
 * @author  Philippe Verdy
 */

/* Sun may wish to change the following package name, if integrating this
 * class in the Sun JCE Security Provider for Java 1.5 (code-named Tiger).
 *
 * You can include it in your own Security Provider by inserting
 * this property in your Provider derived class:
 * put("MessageDigest.MD5", "org.rodage.pub.java.security.MD5");
 */
//package org.rodage.pub.java.security
import java.security.DigestException;
import java.security.MessageDigest;
//--+---+1--+---+--2+---+---+3--+---+--4+---+---+5--+---+--6+---+---+7--+---+-
//3456789012345678901234567890123456789012345678901234567890123456789012345678

/* Implementation note:
 * This class is highly optimized:
 * - to reduce indirect accesses to frequently used member variables with
 *   <iload_0; getfield #n> <iload_0; putfield #n> by using local variables to
 *   cache their value,
 * - to limit the number of <istore_0>..<istore_3> or <istore n> by reusing
 *   intermediate results in the same expression with <dup>, or by avoiding
 *   multiple assignments with predictable results,
 * - to benefit from the abbreviated bytecodes <iconst_m1>..<iconst_5> rather
 *   than <bipush 6>..<bipush 127>,
 * - to order local variables by decreasing usage so frequent local variables
 *   will get abbreviated bytecodes <iload_0>..<iload_3> or
 *   <istore_0>..<istore_3>,
 * - to limit the operand stack usage by swapping commutative operands so
 *   that expressions can be evaluated directly from left to right,
 * - to avoid pairing dependancies with <istore n; iload n> that impact the
 *   performance on pipelined processors, by swapping some commutative
 *   operands.
 * - to maximize the CPU cache hit by scheduling instructions if
 *   possible when the same variable value is needed multiple times,
 * because the HotSpot JIT compiler (in the Client or Server VMs) will almost
 * never optimize these cases itself due to restrictions in the Java VM
 * Specification. Other optimizations are still possible, but most of them
 * would require programming with bytecodes directly with BCEL, as they seem
 * impossible to acheive with the Java language itself (there's nearly no
 * optimization of the bytecode generated by the Java compiler tool, because
 * it adopts a too strict generation mode just to get sure to comply with the
 * Java Language specification that prohibits some optimizations.) You may get
 * higher performances by compiling this source with the IBM or Borland Java
 * Language compilers, rather than with the basic Sun "javac" implementation.
 *
 * Shamefully and unlike with traditional language compilers to CPU native
 * code, there's currently no Java bytecode assembler tool to create very
 * optimized pure 100% Java classes from a source in Java assembly language
 * (and no complete specification for such an assembly language, which is
 * only partly defined in the Java VM Specification, and partly used by the
 * "javap" disassember tool, or by the newer BCEL package). If such a tool
 * exists, let me know so that I can translate this class to this language.
 */

/**
 * <p>The MD5 message-digest algorithm takes as input a message of arbitrary
 * length and produces as output a 128-bit "fingerprint" or "message digest"
 * of the input.  It is conjectured that it is computationally infeasible to
 * produce two messages having the same message digest, or to produce any
 * message having a given prespecified target message digest.</p>
 *
 * <p>References:</p>
 * <ol>
 *   <li>Rivest R., "The <a href="http://www.ietf.org/rfc/rfc1321.txt">MD5</a>
 *     Message-Digest Algorithm", Informational RFC 1321, MIT Laboratory for
 *     Computer Science, and RSA Data Security, Inc., April 1992.</li>
 *   <li>RFC Editor, "<a
 *     href="http://www.rfc-editor.org/cgi-bin/errataSearch.pl?rfc=1321&">Erratum</a>
 *     for RFC 1321", 2002-06-14, 2001-01-19, 2000-04-12.</li>
 * </ol>
 */
public final class MD5 extends MessageDigest implements Cloneable {
    /**
     * Private contextual byte count, stored at end of the last block,
     * after the ending padded block.
     */
    private long bytes;

    /**
     * Private context for incomplete blocks and padded bytes.<br/>
     * INVARIANT: padded must be in 0..63.<br/>
     * When the padded reaches 64 bytes, a new block is computed.
     * Up to 56 last bytes are kept in the padded history.
     */
    private int padded;
    private byte[] pad;

    /**
     * Private context that contains the current digest key.
     */
    private int hA, hB, hC, hD;

    /**
     * Creates a MD5 object with default initial state.
     */
    public MD5() {
        super("MD5");
        pad = new byte[64];
        init();
    }

    /**
     * Clones this object.
     */
    public Object clone() throws CloneNotSupportedException {
        final MD5 that = (MD5)super.clone();
        that.pad = (byte[])this.pad.clone();
        return that;
    }

    /**
     * Reset then initialize the digest context.<br/>
     *
     * Overrides the protected abstract method of
     * <code>java.security.MessageDigestSpi</code>.
     * @modifies  this
     */
    public void engineReset() {
        padded = 0;
        bytes = 0L;
        int i = 60;
        final byte[] buf = pad;
        do {
           buf[i-4] = (byte)0x00;
           buf[i-3] = (byte)0x00;
           buf[i-2] = (byte)0x00;
           buf[i-1] = (byte)0x00;
           buf[i  ] = (byte)0x00;
           buf[i+1] = (byte)0x00;
           buf[i+2] = (byte)0x00;
           buf[i+3] = (byte)0x00;
        } while ((i-=8) >= 0);
        init();
    }

    /**
     * Initialize the digest context.
     * @modifies  this
     */
    protected void init() {
        /* Set to MD5/RIPEMD128 magic values. */
        hA = 0x67452301;
        hB = 0xefcdab89;
        hC = 0x98badcfe;
        hD = 0x10325476;
    }

    /**
     * Updates the digest using the specified byte.
     * Requires internal buffering, and may be slow.<br/>
     *
     * Overrides the protected abstract method of
     * <code>java.security.MessageDigestSpi</code>.
     * @param input  the byte to use for the update.
     * @modifies  this
     */
    public void engineUpdate(final byte input) {
        bytes++;
        if (padded < 63) {
            pad[padded++] = input;
            return;
        }
        pad[63] = input;
        engineUpdate(pad, padded);
        padded = 0;
    }

    /**
     * Updates the digest using the specified array of bytes,
     * starting at the specified offset.<br/>
     *
     * Input length can be any size. May require internal buffering,
     * if input blocks are not multiple of 64 bytes.<br/>
     *
     * Overrides the protected abstract method of
     * <code>java.security.MessageDigestSpi</code>.
     * @param input  the array of bytes to use for the update.
     * @param offset  the offset to start from in the array of bytes.
     * @param length  the number of bytes to use, starting at offset.
     * @modifies  this
     */
    public void engineUpdate(byte[] input, int offset, int length) {
        if (offset >= 0 && length >= 0 && offset + length <= input.length) {
            bytes += length;
            /* Terminate the previous block. */
            if (padded > 0 && padded + length >= 64) {
                final int remaining;
                System.arraycopy(input, offset, pad, padded,
                    remaining = 64 - padded);
                engineUpdate(pad, padded = 0);
                offset += remaining;
                length -= remaining;
            }
            /* Loop on large sets of complete blocks. */
            while (length >= 512) {
                engineUpdate(input, offset);
                engineUpdate(input, offset +  64);
                engineUpdate(input, offset + 128);
                engineUpdate(input, offset + 192);
                engineUpdate(input, offset + 256);
                engineUpdate(input, offset + 320);
                engineUpdate(input, offset + 384);
                engineUpdate(input, offset + 448);
                offset += 512;
                length -= 512;
            }
            /* Loop on remaining complete blocks. */
            while (length >= 64) {
                engineUpdate(input, offset);
                offset += 64;
                length -= 64;
            }
            /* remaining bytes kept for next block. */
            if (length > 0) {
                System.arraycopy(input, offset, pad, padded, length);
                padded += length;
            }
            return;
        }
        throw new ArrayIndexOutOfBoundsException(offset);
    }

    /**
     * Completes the hash computation by performing final operations
     * such as padding. Computes the final hash and returns the final
     * value as a byte[16] array. Once engineDigest has been called,
     * the engine will be automatically reset as specified in the
     * Java Security MessageDigest specification.<br/>
     *
     * For faster operations with multiple digests, allocate your own
     * array and use engineDigest(byte[], int offset, int len).<br/>
     *
     * Overrides the protected abstract method of
     * <code>java.security.MessageDigestSpi</code>.
     * @return the length of the digest stored in the output buffer.
     * @modifies  this
     */
    public byte[] engineDigest() {
        try {
            final byte hashvalue[] = new byte[16]; /* digest length in bytes */
            engineDigest(hashvalue, 0, 16); /* digest length in bytes */
            return hashvalue;
        } catch (DigestException e) {
            return null;
        }
    }

    /**
     * Returns the digest length in bytes. Can be used to allocate your own
     * output buffer when computing multiple digests.<br/>
     *
     * Overrides the protected abstract method of
     * <code>java.security.MessageDigestSpi</code>.
     * @return  the digest length in bytes.
     */
    public int engineGetDigestLength() {
        return 16; /* digest length in bytes */
    }

    /**
     * Completes the hash computation by performing final operations
     * such as padding. Once engineDigest has been called, the engine
     * will be automatically reset (see engineReset).<br/>
     *
     * Overrides the protected abstract method of
     * <code>java.security.MessageDigestSpi</code>.
     * @param hashvalue  the output buffer in which to store the digest.
     * @param offset  offset to start from in the output buffer
     * @param length  number of bytes within buf allotted for the digest.
     *                Both this default implementation and the SUN provider
     *                do not return partial digests.  The presence of this
     *                parameter is solely for consistency in our API's.
     *                If the value of this parameter is less than the
     *                actual digest length, the method will throw a
     *                DigestException.  This parameter is ignored if its
     *                value is greater than or equal to the actual digest
     *                length.
     * @return  the length of the digest stored in the output buffer.
     * @modifies  this
     */
    public int engineDigest(final byte[] hashvalue, int offset,
            final int length) throws DigestException {
        if (length >= 16) { /* digest length in bytes */
            if (hashvalue.length - offset >= 16) { /* digest length in bytes */
                /* Flush the trailing bytes, adding padded bytes into last
                 * blocks. */
                int i;
                /* Add padded null bytes but replace the last 8 padded bytes
                 * by the little-endian 64-bit digested message bit-length. */
                final byte[] buf;
                (buf=pad)[i=padded] = (byte)0x80;/* required 1st padded byte */
                /* Check if 8 bytes are available in pad to store the total
                 * message size */
                switch (i) { /* INVARIANT: i must be in [0..63] */
                case 56: buf[57] = (byte)0x00; /* no break; falls thru */
                case 57: buf[58] = (byte)0x00; /* no break; falls thru */
                case 58: buf[59] = (byte)0x00; /* no break; falls thru */
                case 59: buf[60] = (byte)0x00; /* no break; falls thru */
                case 60: buf[61] = (byte)0x00; /* no break; falls thru */
                case 61: buf[62] = (byte)0x00; /* no break; falls thru */
                case 62: buf[63] = (byte)0x00; /* no break; falls thru */
                case 63: engineUpdate(buf, 0);
                         i = -1;
                }
                /* Clear the rest of the 56 first bytes of pad[]. */
                switch (i & 7) {
                case 7:      i-=3;
                        break;
                case 6: buf[(i-=2)+3] = (byte)0x00;
                        break;
                case 5: buf[(i-=1)+2] = (byte)0x00;
                        buf[i     +3] = (byte)0x00;
                        break;
                case 4: buf[i     +1] = (byte)0x00;
                        buf[i     +2] = (byte)0x00;
                        buf[i     +3] = (byte)0x00;
                        break;
                case 3: buf[(i+=1) ] = (byte)0x00;
                        buf[i     +1] = (byte)0x00;
                        buf[i     +2] = (byte)0x00;
                        buf[i     +3] = (byte)0x00;
                        break;
                case 2: buf[(i+=2)-1] = (byte)0x00;
                        buf[i       ] = (byte)0x00;
                        buf[i     +1] = (byte)0x00;
                        buf[i     +2] = (byte)0x00;
                        buf[i     +3] = (byte)0x00;
                        break;
                case 1: buf[(i+=3)-2] = (byte)0x00;
                        buf[i     -1] = (byte)0x00;
                        buf[i       ] = (byte)0x00;
                        buf[i     +1] = (byte)0x00;
                        buf[i     +2] = (byte)0x00;
                        buf[i     +3] = (byte)0x00;
                        break;
                case 0: buf[(i+=4)-3] = (byte)0x00;
                        buf[i     -2] = (byte)0x00;
                        buf[i     -1] = (byte)0x00;
                        buf[i       ] = (byte)0x00;
                        buf[i     +1] = (byte)0x00;
                        buf[i     +2] = (byte)0x00;
                        buf[i     +3] = (byte)0x00;
                        break;
                }
                while ((i+=8) <= 52) {
                    buf[i-4] = (byte)0x00;
                    buf[i-3] = (byte)0x00;
                    buf[i-2] = (byte)0x00;
                    buf[i-1] = (byte)0x00;
                    buf[i  ] = (byte)0x00;
                    buf[i+1] = (byte)0x00;
                    buf[i+2] = (byte)0x00;
                    buf[i+3] = (byte)0x00;
                }
                /* Convert the message size from bytes to little-endian bits. */
                buf[56] = (byte)((i = (int)bytes << 3));
                buf[57] = (byte)(i >>> 8);
                buf[58] = (byte)(i >>> 16);
                buf[59] = (byte)(i >>> 24);
                buf[60] = (byte)((i = (int)(bytes >>> 29)));
                buf[61] = (byte)(i >>> 8);
                buf[62] = (byte)(i >>> 16);
                buf[63] = (byte)(i >>> 24);
                engineUpdate(buf, 0);
                /* Return the computed digest in little-endian byte order. */
                hashvalue[ offset       ] = (byte)(i = hA);
                hashvalue[ offset     +1] = (byte)(i >>> 8);
                hashvalue[ offset     +2] = (byte)(i >>> 16);
                hashvalue[ offset     +3] = (byte)(i >>> 24);
                hashvalue[ offset     +4] = (byte)(i = hB);
                hashvalue[ offset     +5] = (byte)(i >>> 8);
                hashvalue[(offset+=10)-4] = (byte)(i >>> 16);
                hashvalue[ offset     -3] = (byte)(i >>> 24);
                hashvalue[ offset     -2] = (byte)(i = hC);
                hashvalue[ offset     -1] = (byte)(i >>> 8);
                hashvalue[ offset       ] = (byte)(i >>> 16);
                hashvalue[ offset     +1] = (byte)(i >>> 24);
                hashvalue[ offset     +2] = (byte)(i = hD);
                hashvalue[ offset     +3] = (byte)(i >>> 8);
                hashvalue[ offset     +4] = (byte)(i >>> 16);
                hashvalue[ offset     +5] = (byte)(i >>> 24);
                engineReset(); /* clear the evidence */
                return 16; /* digest length in bytes */
            }
            throw new DigestException(
                "insufficient space in output buffer to store the digest");
        }
        throw new DigestException("partial digests not returned");
    }

    /**
     * Updates the digest using the specified array of bytes,
     * starting at the specified offset, but an implied length
     * of exactly 64 bytes.<br/>
     *
     * Requires no internal buffering, but assumes a fixed input size,
     * in which the required padding bytes must have been added.
     *
     * @param input  the array of bytes to use for the update.
     * @param offset  the offset to start from in the array of bytes.
     * @modifies  this
     */
    private final void engineUpdate(final byte[] input, int offset) {
        /* Intermediate sub-digest values. */
        int a, b, c, d;
        /* Store the input block into the local working set of 32-bit values,
         * in little-endian byte order. Be careful when widening bytes or
         * integers due to sign extension in Java ! Note: One weakness of MD5
         * is that this input is not scheduled between each round, so that
         * meet-in-the-middle collision attacks are possible between rounds
         * 1..2 and rounds 3..4 for each of its input word, reducing the
         * cryptographic strength by 16 bit. The actual cryptographic strength
         * of MD5 is not 64 bits as one would expected from a strong 128-bit
         * digest, but at most 48 bits, allowing brute-force collision attacks
         * on the remaining bits with modest computing resources with an
         * algorithm based on this bit-reduction property. For this reason,
         * signatures based on MD5 are not secure today, unless MD5 is used as
         * the hash function of a HMAC signature, that shields the MD5 digest
         * from meet-in-the-middle attacks with other rounds. It is expected
         * that MD5 will be cracked soon by detecting other internal collision
         * problems. More modern digest algorithms (such as SHA-1) should have
         * at least 5 rounds and use a cryptographically strong scheduling
         * function for its input. (Tiger, another 192-bit digest algorithm,
         * schedules its 8-word input using simple and strong arithmetic, but
         * with only 3 rounds so that its strength is around 88 bits. For
         * SHA-1, which is a 160-bit digest, the strength is around 72 bits.)
         * Note also that the additive constants in the first round of MD5 do
         * not provide any strength against inner collisions of the input but
         * just reduce the frequency of collisions in the chained digest
         * values, making meet-in-the-middle collision attacks just more
         * complex to compute on chained blocks; if the digest is not chained
         * with multiple input blocks, or if input blocks are known as clear
         * text, this effect on digest values strength becomes void, so MD5
         * should not be used for digesting private messages of less than 56
         * bytes (such as passwords stored in their digested form in static
         * databases), or to sign files against data corruption, but it
         * can still be used to secure password exchanges with a randomized
         * challenge exchange (for example in SSL with a strong random
         * generator on the SSL server and a HMAC-like shield; anyway the
         * security of MD5 will never exceed the 64-bits strength face to
         * simple brute-force attacks).
         */
        int i0, i1, i2, i3, i4, i5, i6, i7, i8, i9, iA, iB, iC, iD, iE, iF;
        /* The 64 additive constants are T[i] = floor(2\88\8832 * abs(sin(i))),
         * where i is in radians and varies from 1 to 64. */

        /* Assignments in round 4 and final compression. */
        hB = (b = hB) +
             (c = (d = (a = (b = (c = (d = (a =
        (b = (c = (d = (a = (b = (c = (d = (a =
        /* Assignments in round 3. */
        (b = (c = (d = (a = (b = (c = (d = (a =
        (b = (c = (d = (a = (b = (c = (d = (a =
        /* Assignments in round 2. */
        (b = (c = (d = (a = (b = (c = (d = (a =
        (b = (c = (d = (a = (b = (c = (d = (a =
        /* Assignments in round 1. */
        (b = (c = (d = (a = (b = (c = (d = (a =
        (b = (c = (d = (a = (b = (c = (d = (a =
        /* Round 1 (steps 1..16).
         * - left rotations: 7, 12, 17, 22 bits.
         * - key schedule (multiplexer) function:
         *   F(x,y,z) = (~x & z) | (x & y)  = ((y ^ z) & x) ^ z. */
         b + ((a = ((((c = hC)
                        ^ (d = hD)
                           ) & b) ^ d) + (i0=
                (input[ offset       ] & 0xff)
              | (input[ offset     +1] & 0xff)<<8
              | (input[ offset     +2] & 0xff)<<16
              |  input[ offset     +3]<<24) + 0xd76aa478 + hA
                                                            )<< 7 | a>>>25))
           + ((d = (((b ^ c) & a) ^ c) + (i1=
                (input[ offset     +4] & 0xff)
              | (input[ offset     +5] & 0xff)<<8
              | (input[(offset+=10)-4] & 0xff)<<16
              |  input[ offset     -3]<<24) + 0xe8c7b756 + d)<<12 | d>>>20))
           + ((c = (((a ^ b) & d) ^ b) + (i2=
                (input[ offset     -2] & 0xff)
              | (input[ offset     -1] & 0xff)<<8
              | (input[ offset       ] & 0xff)<<16
              |  input[ offset     +1]<<24) + 0x242070db + c)<<17 | c>>>15))
           + ((b = (((d ^ a) & c) ^ a) + (i3=
                (input[ offset     +2] & 0xff)
              | (input[ offset     +3] & 0xff)<<8
              | (input[ offset     +4] & 0xff)<<16
              |  input[ offset     +5]<<24) + 0xc1bdceee + b)<<22 | b>>>10))
           + ((a = (((c ^ d) & b) ^ d) + (i4=
                (input[(offset+=10)-4] & 0xff)
              | (input[ offset     -3] & 0xff)<<8
              | (input[ offset     -2] & 0xff)<<16
              |  input[ offset     -1]<<24) + 0xf57c0faf + a)<< 7 | a>>>25))
           + ((d = (((b ^ c) & a) ^ c) + (i5=
                (input[ offset       ] & 0xff)
              | (input[ offset     +1] & 0xff)<<8
              | (input[ offset     +2] & 0xff)<<16
              |  input[ offset     +3]<<24) + 0x4787c62a + d)<<12 | d>>>20))
           + ((c = (((a ^ b) & d) ^ b) + (i6=
                (input[ offset     +4] & 0xff)
              | (input[ offset     +5] & 0xff)<<8
              | (input[(offset+=10)-4] & 0xff)<<16
              |  input[ offset     -3]<<24) + 0xa8304613 + c)<<17 | c>>>15))
           + ((b = (((d ^ a) & c) ^ a) + (i7=
                (input[ offset     -2] & 0xff)
              | (input[ offset     -1] & 0xff)<<8
              | (input[ offset       ] & 0xff)<<16
              |  input[ offset     +1]<<24) + 0xfd469501 + b)<<22 | b>>>10))
           + ((a = (((c ^ d) & b) ^ d) + (i8=
                (input[ offset     +2] & 0xff)
              | (input[ offset     +3] & 0xff)<<8
              | (input[ offset     +4] & 0xff)<<16
              |  input[ offset     +5]<<24) + 0x698098d8 + a)<< 7 | a>>>25))
           + ((d = (((b ^ c) & a) ^ c) + (i9=
                (input[(offset+=10)-4] & 0xff)
              | (input[ offset     -3] & 0xff)<<8
              | (input[ offset     -2] & 0xff)<<16
              |  input[ offset     -1]<<24) + 0x8b44f7af + d)<<12 | d>>>20))
           + ((c = (((a ^ b) & d) ^ b) + (iA=
                (input[ offset       ] & 0xff)
              | (input[ offset     +1] & 0xff)<<8
              | (input[ offset     +2] & 0xff)<<16
              |  input[ offset     +3]<<24) + 0xffff5bb1 + c)<<17 | c>>>15))
           + ((b = (((d ^ a) & c) ^ a) + (iB=
                (input[ offset     +4] & 0xff)
              | (input[ offset     +5] & 0xff)<<8
              | (input[(offset+=10)-4] & 0xff)<<16
              |  input[ offset     -3]<<24) + 0x895cd7be + b)<<22 | b>>>10))
           + ((a = (((c ^ d) & b) ^ d) + (iC=
                (input[ offset     -2] & 0xff)
              | (input[ offset     -1] & 0xff)<<8
              | (input[ offset       ] & 0xff)<<16
              |  input[ offset     +1]<<24) + 0x6b901122 + a)<< 7 | a>>>25))
           + ((d = (((b ^ c) & a) ^ c) + (iD=
                (input[ offset     +2] & 0xff)
              | (input[ offset     +3] & 0xff)<<8
              | (input[ offset     +4] & 0xff)<<16
              |  input[ offset     +5]<<24) + 0xfd987193 + d)<<12 | d>>>20))
           + ((c = (((a ^ b) & d) ^ b) + (iE=
                (input[(offset=offset+10)-4] & 0xff)
              | (input[ offset     -3] & 0xff)<<8
              | (input[ offset     -2] & 0xff)<<16
              |  input[ offset     -1]<<24) + 0xa679438e + c)<<17 | c>>>15))
           + ((b = (((d ^ a) & c) ^ a) + (iF=
                (input[ offset       ] & 0xff)
              | (input[ offset     +1] & 0xff)<<8
              | (input[ offset     +2] & 0xff)<<16
              |  input[ offset     +3]<<24) + 0x49b40821 + b)<<22 | b>>>10))
        /* Round 2 (steps 17..32).
         * - left rotations: 5, 9, 14, 20 bits.
         * - key schedule (multiplexer) function:
         *   G(x,y,z) = F(z,x,y) = (~z & y) | (z & x) = ((y ^ x) & z) ^ y. */
           + ((a = (((c ^ b) & d) ^ c) + i1 + 0xf61e2562 + a)<< 5 | a>>>27))
           + ((d = (((b ^ a) & c) ^ b) + i6 + 0xc040b340 + d)<< 9 | d>>>23))
           + ((c = (((a ^ d) & b) ^ a) + iB + 0x265e5a51 + c)<<14 | c>>>18))
           + ((b = (((d ^ c) & a) ^ d) + i0 + 0xe9b6c7aa + b)<<20 | b>>>12))
           + ((a = (((c ^ b) & d) ^ c) + i5 + 0xd62f105d + a)<< 5 | a>>>27))
           + ((d = (((b ^ a) & c) ^ b) + iA + 0x02441453 + d)<< 9 | d>>>23))
           + ((c = (((a ^ d) & b) ^ a) + iF + 0xd8a1e681 + c)<<14 | c>>>18))
           + ((b = (((d ^ c) & a) ^ d) + i4 + 0xe7d3fbc8 + b)<<20 | b>>>12))
           + ((a = (((c ^ b) & d) ^ c) + i9 + 0x21e1cde6 + a)<< 5 | a>>>27))
           + ((d = (((b ^ a) & c) ^ b) + iE + 0xc33707d6 + d)<< 9 | d>>>23))
           + ((c = (((a ^ d) & b) ^ a) + i3 + 0xf4d50d87 + c)<<14 | c>>>18))
           + ((b = (((d ^ c) & a) ^ d) + i8 + 0x455a14ed + b)<<20 | b>>>12))
           + ((a = (((c ^ b) & d) ^ c) + iD + 0xa9e3e905 + a)<< 5 | a>>>27))
           + ((d = (((b ^ a) & c) ^ b) + i2 + 0xfcefa3f8 + d)<< 9 | d>>>23))
           + ((c = (((a ^ d) & b) ^ a) + i7 + 0x676f02d9 + c)<<14 | c>>>18))
           + ((b = (((d ^ c) & a) ^ d) + iC + 0x8d2a4c8a + b)<<20 | b>>>12))
        /* Round 3 (steps 33..48).
         * - left rotations: 4, 11, 16, 23 bits.
         * - key schedule function: H(x,y,z) = y ^ x ^ z. */
           + ((a = (c ^ b ^ d)         + i5 + 0xfffa3942 + a)<< 4 | a>>>28))
           + ((d = (b ^ a ^ c)         + i8 + 0x8771f681 + d)<<11 | d>>>21))
           + ((c = (a ^ d ^ b)         + iB + 0x6d9d6122 + c)<<16 | c>>>16))
           + ((b = (d ^ c ^ a)         + iE + 0xfde5380c + b)<<23 | b>>> 9))
           + ((a = (c ^ b ^ d)         + i1 + 0xa4beea44 + a)<< 4 | a>>>28))
           + ((d = (b ^ a ^ c)         + i4 + 0x4bdecfa9 + d)<<11 | d>>>21))
           + ((c = (a ^ d ^ b)         + i7 + 0xf6bb4b60 + c)<<16 | c>>>16))
           + ((b = (d ^ c ^ a)         + iA + 0xbebfbc70 + b)<<23 | b>>> 9))
           + ((a = (c ^ b ^ d)         + iD + 0x289b7ec6 + a)<< 4 | a>>>28))
           + ((d = (b ^ a ^ c)         + i0 + 0xeaa127fa + d)<<11 | d>>>21))
           + ((c = (a ^ d ^ b)         + i3 + 0xd4ef3085 + c)<<16 | c>>>16))
           + ((b = (d ^ c ^ a)         + i6 + 0x04881d05 + b)<<23 | b>>> 9))
           + ((a = (c ^ b ^ d)         + i9 + 0xd9d4d039 + a)<< 4 | a>>>28))
           + ((d = (b ^ a ^ c)         + iC + 0xe6db99e5 + d)<<11 | d>>>21))
           + ((c = (a ^ d ^ b)         + iF + 0x1fa27cf8 + c)<<16 | c>>>16))
           + ((b = (d ^ c ^ a)         + i2 + 0xc4ac5665 + b)<<23 | b>>> 9))
        /* Round 4 (steps 49..64) and final compression.
         * - left rotations: 6, 10, 15, 21 bits.
         * - key schedule function I(x,y,z) = (~z | x) ^ y. */
           + ((a = ((~d | b) ^ c)      + i0 + 0xf4292244 + a)<< 6 | a>>>26))
           + ((d = ((~c | a) ^ b)      + i7 + 0x432aff97 + d)<<10 | d>>>22))
           + ((c = ((~b | d) ^ a)      + iE + 0xab9423a7 + c)<<15 | c>>>17))
           + ((b = ((~a | c) ^ d)      + i5 + 0xfc93a039 + b)<<21 | b>>>11))
           + ((a = ((~d | b) ^ c)      + iC + 0x655b59c3 + a)<< 6 | a>>>26))
           + ((d = ((~c | a) ^ b)      + i3 + 0x8f0ccc92 + d)<<10 | d>>>22))
           + ((c = ((~b | d) ^ a)      + iA + 0xffeff47d + c)<<15 | c>>>17))
           + ((b = ((~a | c) ^ d)      + i1 + 0x85845dd1 + b)<<21 | b>>>11))
           + ((a = ((~d | b) ^ c)      + i8 + 0x6fa87e4f + a)<< 6 | a>>>26))
           + ((d = ((~c | a) ^ b)      + iF + 0xfe2ce6e0 + d)<<10 | d>>>22))
           + ((c = ((~b | d) ^ a)      + i6 + 0xa3014314 + c)<<15 | c>>>17))
           + ((b = ((~a | c) ^ d)      + iD + 0x4e0811a1 + b)<<21 | b>>>11))
           + ((a = ((~d | b) ^ c)      + i4 + 0xf7537e82 + a)<< 6 | a>>>26))
           + ((d = ((~c | a) ^ b)      + iB + 0xbd3af235 + d)<<10 | d>>>22))
           + ((c = ((~b | d) ^ a)      + i2 + 0x2ad7d2bb + c)<<15 | c>>>17))
           + ((b = ((~a | c) ^ d)      + i9 + 0xeb86d391 + b)<<21 | b>>>11);
        hC += c;
        hD += d;
        hA += a;
    }
}
