package org.myprofiler4j.java.client.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParserUtils {

  public static final String SEP = ":";
  
  public static Pattern FILE_NAME_PATTERN = Pattern.compile(".*methodProfile_.*\\.txt|.*methodProfile_.*\\.txt\\.[1-9][0-9]{0,2}$");

  public static final DateFormat TIME_FORATTER = new SimpleDateFormat("MM/dd/yyyy-HH:mm:ss");
  private static final String PREFIX = "methodProfile_";
  
  public static Locale LOCALE = null;
  
  public static TimeZone TIMEZONE = null;
  
  public static long STARTUP_TIME = 0;
  
  public static Map<String, String> readMethodInfo(String methodFilePath, Map<String, String> result, Set<String> includedSet) throws Exception{
    return readFile(methodFilePath, result, includedSet);
  }
  
  public static Map<String, String> readThreadInfo(String threadInfoFile, Map<String, String> result, Set<String> includedSet) throws Exception{
    return readFile(threadInfoFile, result, includedSet);
  }
  
  private static Map<String, String> readFile(String file, Map<String, String> result, Set<String> includedSet) throws Exception{
    if(result == null) 
      result = new HashMap<String, String>();
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new FileReader(new File(file)));
      String line = null;
      while((line = reader.readLine()) != null) {
        if(line.startsWith("#") || line.isEmpty()) {
          continue;
        }
        int index = line.indexOf(SEP);
        if(index < 0) {
          continue;
        }

        String key = line.substring(0, index);
        String value = line.substring(index + 1);
        if(includedSet != null && includedSet.size() > 0 && !includedSet.contains(key)) {
          continue;
        } else {         
          result.put(key, value);
          if(includedSet != null && result.size() == includedSet.size()) {
            break;
          }
        }
      }
    } catch (Exception e) {
      throw e;
    } finally {
      if(reader != null) {
        try {
          reader.close();
        } catch (Exception ex) {}
      }
    }

    return result;
  }
  
  public static void initTimeZone(String file) {
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new FileReader(new File(file)));
      String line = null;
      while((line = reader.readLine()) != null) {
        if(line.startsWith("#Profiler")) {
          STARTUP_TIME = Long.valueOf(line.split(SEP)[1]);
        } else if(line.startsWith("#Locale")) {
          String[] items = line.split(SEP);
          String[] localeVars = items[1].split("_");
          if(localeVars.length == 1) {
            LOCALE = new Locale(localeVars[0]);
          }else if(localeVars.length >= 2) {
            LOCALE = new Locale(localeVars[0], localeVars[1]);
          }
        } else if(line.startsWith("#Timezone")) {
          TIMEZONE = TimeZone.getTimeZone(line.split(":")[1]);
          TIME_FORATTER.setTimeZone(TIMEZONE);
          break;
        } 
      }
    } catch (Exception ex) {
      ex.printStackTrace();
    } finally {
      if(reader != null) {
        try {
          reader.close();
        } catch (Exception e) {
        }
      }
    }
  }
  
  /**
   * @param filePath
   * @return
   */
  public static boolean isMethodProfilingFile(String filePath) {
    Matcher matcher = FILE_NAME_PATTERN.matcher(filePath);
    return matcher.matches();
  }
  
  public static long getTimeInMilliseconds(String timeString) throws Exception{
    Date d = TIME_FORATTER.parse(timeString);
    return d.getTime();
  }
  
  public static String getFilePostfix(String profileFilePath) {
    int idx1 = profileFilePath.indexOf(PREFIX);
    int idx2 = profileFilePath.indexOf(".txt");
    return profileFilePath.substring(idx1 + PREFIX.length() - 1, idx2 + 4);
  }
}
