package org.myprofiler4j.java.agent;

public class UserInfo {
  private static final String DEFAULT_PASS = "MyProfiler4J";
  private int tryLoginTimes = 0;
  private String userName;
  private String password;

  public UserInfo(String username, String pw) {
    this.userName = username;
    if (pw == null || pw.isEmpty()) {
      this.password = DEFAULT_PASS;
    } else {
      this.password = pw;
    }
  }

  public String getUserName() {
    return userName;
  }

  public String getPassword() {
    return password;
  }

  public void setUserName(String userName) {
    this.userName = userName;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public boolean isAdmin() {
    return "admin".equalsIgnoreCase(userName);
  }

  public int getTryLoginTimes() {
    return tryLoginTimes;
  }

  public void increaseTryLoginTimes() {
    tryLoginTimes++;
  }

}
