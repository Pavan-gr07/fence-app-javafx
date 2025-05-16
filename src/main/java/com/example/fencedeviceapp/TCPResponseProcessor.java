package com.example.fencedeviceapp;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

public class TCPResponseProcessor {
    private static final Logger logger = Logger.getLogger(TCPResponseProcessor.class.getName());

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
     * Process the TCP response from the device
     *
     * @param response The byte array response from the device
     * @param ip The IP address of the device
     * @return Map containing the parsed device data
     * @throws Exception If the response is invalid
     */
    public static Map<String, Integer> processResponse(byte[] response, String ip) throws Exception {
        try {
            logger.log(Level.INFO, "Processing TCP response for IP {0}. Raw response: {1}",
                    new Object[]{ip, bytesToHexString(response)});

            if (response == null) {
                throw new Exception("Invalid response format for IP " + ip + ": response is null.");
            }

            if (response.length != 16) {
                throw new Exception("Invalid response length for IP " + ip + ": expected 16, got " + response.length);
            }

            // Calculate checksum (sum of first 13 elements & 0xFF)
            int checkSum = 0;
            for (int i = 0; i < 13; i++) {
                checkSum += response[i] & 0xFF;
            }
            checkSum &= 0xFF;

            // Verify response integrity (CS signature and checksum)
            if (!((response[14] & 0xFF) == 67 && (response[15] & 0xFF) == 83 && (response[13] & 0xFF) == checkSum)) {
                logger.log(Level.SEVERE, "Invalid response from {0}. Response: {1}, Expected checksum: {2}",
                        new Object[]{ip, bytesToHexString(response), checkSum});
                throw new Exception("Invalid response from " + ip);
            }

            Map<String, Integer> deviceData = new HashMap<>();

            // Process byte values (indexes 2-8)
            String[] deviceProps = {"FVout", "FVReturn", "BV", "Check", "GadgetStatus", "FenceStatus", "AlarmStatus"};
            for (int i = 0; i < deviceProps.length; i++) {
                deviceData.put(deviceProps[i], response[i + 2] & 0xFF);
            }

            // Process boolean flags from the 12th byte
            byte twelfthByte = response[12];
            String[] devicePropsBool = {"Inp4", "Inp3", "Inp2", "ServiceMode", "LidStatus",
                    "DrinageIntrusion", "EncloserStatus", "MainsStatus"};

            for (int i = 0; i < devicePropsBool.length; i++) {
                deviceData.put(devicePropsBool[i], ((twelfthByte & (1 << i)) != 0) ? 1 : 0);
            }

            logger.log(Level.INFO, "Processed TCP response for IP {0}: {1}",
                    new Object[]{ip, deviceData});

            return deviceData;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing TCP response for IP {0}: {1}",
                    new Object[]{ip, e.getMessage()});
            throw e;
        }
    }

    /**
     * Convert a hex string to byte array
     *
     * @param hexString The hex string to convert
     * @return The resulting byte array
     */
    public static byte[] hexStringToByteArray(String hexString) {
        int len = hexString.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                    + Character.digit(hexString.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * Convert a byte array to hex string for logging
     *
     * @param bytes The byte array to convert
     * @return The hex string representation
     */
    public static String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }
}