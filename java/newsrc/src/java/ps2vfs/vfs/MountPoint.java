package ps2vfs.vfs;

import ps2vfs.plugin.*;

public class MountPoint 
{
  private String openPath;
  private boolean recursive;
  private boolean explodeContent;
  private boolean virtual;
  private boolean hidden;
  private static boolean debug = false;

  public static class DirComp
    implements java.util.Comparator
  {
    public int compare(Object e1, Object e2) {
      MountPoint de1 = (MountPoint) e1;
      MountPoint de2 = (MountPoint) e2;
      
      if(de1.isVirtual() == de2.isVirtual()) {
	return de1.getOpenPath().compareTo(de2.getOpenPath());
      } else if(de1.isVirtual()) {
	return -1;
      } else
	return 1;
    }
  }

  MountPoint(String iPath) {
    openPath = iPath;
    virtual = true;
    explodeContent = false;
    recursive = true;
  }
  
  MountPoint(String iPath, 
	     boolean iExplodeContent,
	     boolean iRecursive,
	     boolean iHidden) {
    openPath = iPath;
    recursive = iRecursive;
    explodeContent = iExplodeContent;
    hidden = iHidden;
    virtual = false;
  }

  public String toString() {
    if(virtual) {
      return "MPv: " + openPath;
    } else {
      return "MP : " + (recursive ? "r" : "-") + (explodeContent ? "e":"-") + (hidden?"h":"-") + " : " + openPath;
    }
  }

  public boolean equals(Object o) throws ClassCastException {
    boolean eq = false;
    MountPoint mp = (MountPoint) o;
    eq = mp.openPath.equals(openPath);
    if(eq) {
      if(virtual) {
	eq = mp.virtual;
      } else {
	eq = (mp.explodeContent == explodeContent) && (mp.recursive == recursive);
      }
    }
    return eq;
  }


  public VfsResolvedDir resolveDir(String path) {
    // This flag indicates that the path is a 
    // sub-dir of this MP entry.

    boolean isSubPath = true;
    java.util.List fileVec = null;
    VfsResolvedDir resDir = null;

    if(path == null)
      path = "";

    isSubPath = path.length() > 0;

    int pathSep1 = path.indexOf('/');
    int pathSep2 = -1;
    if(pathSep1 > 0) 
      pathSep2 = path.indexOf('/', pathSep1+1);
    
    if(debug) {
      System.out.println("Resolving: " + path + " in mp " + this);
    }
    
    if(virtual) {
      isSubPath = false;
    } else if(!recursive && 
	      ((explodeContent && pathSep1 > 0)  || (!explodeContent && pathSep2 > 0))) {
      isSubPath = false;
    }

    if(debug) {
      System.out.println("Resolving: " + path + " in mp " + this + 
			 " isSubPath: " + isSubPath + " " + pathSep1 + " " + pathSep2);
    }
      
    String vPathPrefix = "";
    if(isSubPath && !explodeContent) {
      String dirName = path;
      if(pathSep1>0)
	dirName = path.substring(0, pathSep1);
      
      java.io.File oPathFile = new java.io.File(openPath);
      if(debug) {
	System.out.println("Checking subdir name " + dirName + 
			   " with mp op: " + oPathFile.getName());
      }

      if(oPathFile.getName().equals(dirName)) {
	vPathPrefix = dirName;
	if(pathSep1 > 0) {
	  path = path.substring(pathSep1);
	} else {
	  path = "";
	}
      } else {
	isSubPath = false;
      }
    } 
    
    // At this point we know that the remaining path can be used to 
    // together with the open path to resolve the directory.
    // TODO: Use Jakarta VFS to open the path.
    // mgr.resolveFile(openPath, path);

    String virtPath = "";
    if(!isSubPath || (!recursive && path.length()>0)) {
      virtPath = path;
      path = "";
    } 
    java.io.File openPathFile = new java.io.File(openPath);
    java.io.File dir = new java.io.File(openPath + path);
    if(!isSubPath && (!explodeContent || virtual)) {
      if(debug) {
	System.out.println("Adding virtual dir: '" + openPath + path + "'");
      }

      VfsDirEntry vDirEnt = new VfsDirEntry();
      vDirEnt.setVirtualName(dir.getName());
      vDirEnt.setDirectory(true);
      vDirEnt.setOpenPath(dir.getPath());
      fileVec = new java.util.Vector(1);
      fileVec.add(vDirEnt);
    } else {
      if(debug) {
	System.out.println("Checking " + openPath + ":" + path);
      }

      while(!dir.isDirectory() && path.length() > 0) {
	int lps = path.lastIndexOf('/');
	if(lps > 0) {
	  virtPath = path.substring(lps) + virtPath;
	  path = path.substring(0,lps);
	} else {
	  virtPath = path + virtPath;
	  path = "";
	}
	dir = new java.io.File(openPath + path);
      }
      
      if(dir.isDirectory()) {
	if(debug) {
	  System.out.println("Reading content of dir: " +  
			     dir.getAbsolutePath() + " vp: " + virtPath);
	}
	java.io.File[] files = dir.listFiles();
	fileVec = VfsDirEntry.toList(files, recursive);
	
      } else {
	java.util.logging.Logger.getLogger("ps2vfs").warning("Not a supported path: " + 
							     dir.getAbsolutePath() + 
							     " vp: " + virtPath);
      }
    } 
    
    if(fileVec != null) {
      // System.out.println("Returning filelist from path: " + vPathPrefix + path);  
      resDir = new VfsResolvedDir(fileVec, vPathPrefix + path, 
				  (vPathPrefix.length() + path.length()) == 0);
    }
    return resDir;
  }
    
  public String  getOpenPath() { return openPath; }
  public boolean getRecursive() { return recursive; }
  public boolean getExplodedContent() { return explodeContent; }
  public boolean isVirtual() { return virtual; }
  public boolean isHidden() { return hidden; }
};
