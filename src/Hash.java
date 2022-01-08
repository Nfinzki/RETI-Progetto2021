/**
 * This file contains the implementation of a hashing function
 * based on SHA-256 algorithm
 */

import java.security.*;

public class Hash {

    /**
     *	Calculates the string hash SHA-256 value
     * 	@param s input string
     *  @return the bytes corresponding to the input hash value
     */
    public static byte[] sha256(String s) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(s.getBytes());
        return md.digest();
    }

    /**
     *	Converts a byte array in an HEX string
     *	@param hash byte array
     *	@return HEX string
     */
    public static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
