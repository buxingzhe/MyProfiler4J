package org.myprofiler4j.java.utils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

/**
 * A utility class used for encryption and decryption
 * 
 * @author nhuang
 *
 */
public class SecurityUtil {
  private final static String ALGORITHM = "PBEWithMD5AndDES";
  private final static BASE64Encoder BASE64_ENCODER = new BASE64Encoder();
  private final static BASE64Decoder BASE64_DECODER = new BASE64Decoder();
  private final static byte[] SALT = {
    (byte) 0xaf, (byte) 0xb2, (byte) 0x1c, (byte) 0xd5,
    (byte) 0xaf, (byte) 0xb2, (byte) 0x1c, (byte) 0xd5,
  };

  public static void main(String[] args) throws Exception {
    if(args == null || args.length < 2) {
      System.out.println("Usage: generatePass.sh <user name> <password>");
      System.exit(-1);
    }
    String userName = args[0];
    String plainPassword = args[1];
    byte[] encryptPass = encrypt(plainPassword, userName);
    System.out.println("Encrypted password: " + base64Encode(encryptPass));
  }

  public static byte[] encrypt(String plainText, String keySpecPw) throws GeneralSecurityException, UnsupportedEncodingException {
    SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(ALGORITHM);
    SecretKey key = keyFactory.generateSecret(new PBEKeySpec(keySpecPw.toCharArray()));
    Cipher pbeCipher = Cipher.getInstance(ALGORITHM);
    pbeCipher.init(Cipher.ENCRYPT_MODE, key, new PBEParameterSpec(SALT, 20));
    return pbeCipher.doFinal(plainText.getBytes("UTF-8"));
  }

  public static byte[] decrypt(byte[] encyptedValue, String keySpecPw) throws GeneralSecurityException, IOException {
    SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(ALGORITHM);
    SecretKey key = keyFactory.generateSecret(new PBEKeySpec(keySpecPw.toCharArray()));
    Cipher pbeCipher = Cipher.getInstance(ALGORITHM);
    pbeCipher.init(Cipher.DECRYPT_MODE, key, new PBEParameterSpec(SALT, 20));
    return pbeCipher.doFinal(encyptedValue);
  }

  public static String base64Encode(byte[] bytes) {
    return BASE64_ENCODER.encode(bytes);
  }

  public static byte[] base64Decode(String encyptedValue) throws IOException {
    return BASE64_DECODER.decodeBuffer(encyptedValue);
  }

}
