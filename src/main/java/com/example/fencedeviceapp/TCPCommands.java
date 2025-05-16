package com.example.fencedeviceapp;

/**
 * TCP Command constants
 */
public class TCPCommands {
    // TCP Commands as byte arrays
    public static final byte[] GET_ALL_STATUS = hexStringToByteArray("474554414C4C535441545553009D4353");
    public static final byte[] ARM = hexStringToByteArray("5455524E46454E43454F4E0000474353");
    public static final byte[] DISARM = hexStringToByteArray("5455524E46454E43454F464600854353");
    public static final byte[] POSTPONE = hexStringToByteArray("504F5354504F4E450000000000784353");
    public static final byte[] ACKNOWLEDGE = hexStringToByteArray("5455524E414C41524D4F464600914353");
    public static final byte[] GADGET_ON = hexStringToByteArray("5455524E4C494748544F4E00005E4353");
    public static final byte[] GADGET_OFF = hexStringToByteArray("5455524E4C494748544F4646009C4353");
    public static final byte[] ACKNOWLEDGEDIA = hexStringToByteArray("41434B4E574C45444745444941A34353");
    public static final byte[] SERVICE_MODE_ON = hexStringToByteArray("5352564943454D4F44454F4E008E4353");
    public static final byte[] SERVICE_MODE_OFF = hexStringToByteArray("5352564943454D4F44454F4646CC4353");

    /**
     * Convert a hex string to byte array
     *
     * @param hexString The hex string to convert
     * @return The resulting byte array
     */
    private static byte[] hexStringToByteArray(String hexString) {
        int len = hexString.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                    + Character.digit(hexString.charAt(i + 1), 16));
        }
        return data;
    }
}