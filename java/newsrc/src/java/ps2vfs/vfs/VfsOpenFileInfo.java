package ps2vfs.vfs;


public class VfsOpenFileInfo
{
  private int fd;
  private long ip;
  private String info;
  private String vpath;
  private String opath;

  public VfsOpenFileInfo(int inFD, long inIP) {
    fd = inFD;
    ip = inIP;
  }

  public int getFD() {
    return fd;
  }

  public long getIP() {
    return ip;
  }

  public String getIPStr() {
    return "" + 
      ((ip >> 24) & 0xff) + "." +
      ((ip >> 16) & 0xff) + "." +
      ((ip >> 8) & 0xff) + "." +
      ((ip) & 0xff);
  }
  
  public void setInfo(String info) {
    this.info = info;
  }

  public String getInfo() {
    return info;
  }

  public void setVPath(String inVpath) {
    vpath = inVpath;
  }

  public String getVPath() {
    return vpath;
  }

  public void setOPath(String inOpath) {
    opath = inOpath;
  }

  public String getOPath() {
    return opath;
  }
  
}
