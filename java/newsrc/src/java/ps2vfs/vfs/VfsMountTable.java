package ps2vfs.vfs;

import ps2vfs.plugin.*;



public class VfsMountTable 
{
  private static boolean debug = false;
  private java.util.logging.Logger log;
  private java.util.HashMap mountTable;
  
  VfsMountTable() {
    mountTable = new java.util.HashMap();
    log = java.util.logging.Logger.getLogger("ps2vfs");
  }

  public java.util.Iterator getIterator() {
    return mountTable.entrySet().iterator();
  }

  public boolean addMountPoint(String virtualPath, String actualPath, 
			       boolean explodeContent, boolean recursive, boolean hidden) {
    java.util.List mounted = null;

    mounted = addMountPath(virtualPath);
    mounted.add(new MountPoint(actualPath, explodeContent, recursive, hidden));
    log.config("Mounting: " + virtualPath + " => " + actualPath + " " + 
	       (recursive?"r":"-") + (explodeContent?"e":"-") + (hidden?"h":"-"));
    return true;
  }

  public boolean removeMountPoint(String virtualPath, String actualPath, 
				  boolean explodeContent, boolean recursive, boolean hidden) {
    
    java.util.List mounted = (java.util.List) mountTable.get(virtualPath);
    boolean removed = false;
    if(mounted != null) {
      removed = mounted.remove(new MountPoint(actualPath, explodeContent, 
					      recursive, hidden));
    } 
    if(removed) {
      log.config("Unmounting: " + virtualPath + " => " + actualPath + " " + 
		 (recursive?"r":"-") + (explodeContent?"e":"-") + (hidden?"h":"-"));
      removeMountPath(virtualPath, null);
    }
    return removed;
  }
  
  public ps2vfs.plugin.VfsDir getVirtualDirContent(String path) {
    int curLevel;
    String virtualPath = "/";
    ps2vfs.plugin.VfsDir dirContent = null;
    java.util.List mounted = null;
    String name = "";
    
    for(curLevel = path.indexOf('/'); 
	path.length() > 0; curLevel = path.indexOf('/')) {
      if(curLevel == 0) {
	path = path.substring(1);
	continue;
      }
      String oldPath = path;
      if(curLevel > 0) {
	name = path.substring(0, curLevel);
	path = path.substring(curLevel + 1);
      } else {
	name = path;
	path = "";
      }
      
      // Add name to current path.
      String nvp = virtualPath + name + "/";
      mounted = (java.util.List) mountTable.get(nvp);
      if(mounted == null) {
	path = oldPath;
	break;
      }
      virtualPath = nvp;
    }

    mounted = (java.util.List) mountTable.get(virtualPath);
    if(mounted != null) {
      if(debug) {
	System.out.println("Found valid virtual path '" + 
			   virtualPath + "'" + " remain " +  path);
      }
      dirContent = selectMountPoint(virtualPath, path, mounted);
    }
    return dirContent;
  }

  public void print() {
    java.util.Set keys = mountTable.keySet();
    for(java.util.Iterator it = keys.iterator(); it.hasNext();) {
      System.out.println("Key: " + it.next());
    }
  }


  private ps2vfs.plugin.VfsDir selectMountPoint(String virtualPath, 
						String path, 
						java.util.List mounted) {
    VfsResolvedDir rootDirContent = null;
    if(debug) {
      System.out.println("Selecting: '" + path + "' in " + mounted + 
			 " (list of " + virtualPath + ")");
    }
    
    for(java.util.ListIterator li = mounted.listIterator(); 
	li.hasNext();) {
      MountPoint mp = (MountPoint) li.next();
      VfsResolvedDir dirContent = mp.resolveDir(path);
      if(dirContent != null) {
	if(dirContent.isRootDir()) {
	  // Append this root content to the rest.
	  if(rootDirContent == null)
	    rootDirContent = dirContent;
	  else 
	    rootDirContent.add(dirContent);
	} else {
	  // This is a sub dir of this MP, so we have found our match.
	  // just return it!
	  String vPath;
	  if(debug) {
	    System.err.println("Returning subdir dir: " + virtualPath + 
			       dirContent.getVirtualPath());
	  }
	  return new ps2vfs.plugin.VfsDir(virtualPath + dirContent.getVirtualPath(), 
					  dirContent.getDirContent());
	}
      }
    }
    if(rootDirContent != null) {
      if(debug) {
	System.err.println("Returning root dir: " + virtualPath + 
			   rootDirContent.getVirtualPath());
      }
      return new ps2vfs.plugin.VfsDir(virtualPath + rootDirContent.getVirtualPath(), 
				      rootDirContent.getDirContent());
    }
    return null;
  }

  private boolean removeMountPath(String virtualPath, String child) {
    // Clean up directories as they become empty?
    // They are added implicitly so they should probably wanish
    // impicitly as well. Empty diretories will only clutter
    // the VFS anyway.
    java.util.List mounted = null;
    boolean removed = false;

    if(virtualPath != null) {
      mounted = (java.util.List) mountTable.get(virtualPath);
      if(mounted == null) {
	// System.err.println("RMP: " + virtualPath + ", " + child + ", " + removed + ", (Not Found!!)");
	return false;
      }
      if(child != null) {
	removed = mounted.remove(new MountPoint(child));
      }
      // System.err.println("RMP: " + virtualPath + ", " + child + ", " + removed);

      if(mounted.isEmpty()) {
	// log.config("Removing empty dir: " + virtualPath);
	removed = true;
	mountTable.remove(virtualPath);
	int lastSlash = virtualPath.lastIndexOf('/');
	if(lastSlash >= 0 && 
	   lastSlash == virtualPath.length()-1) {
	  if(lastSlash == 0) 
	    return removed;
	  virtualPath = virtualPath.substring(0, lastSlash);
	  lastSlash = virtualPath.lastIndexOf('/');
	}
	if(lastSlash >= 0) {
	  removeMountPath(virtualPath.substring(0, lastSlash+1), virtualPath.substring(lastSlash+1));
	}
      }
    }
    return removed;
  }

  private java.util.List addMountPath(String virtualPath) {
    java.util.List mounted = null;

    if(virtualPath != null) {
      int curLevel = 0;
      String name = "";
      String parentPath = "/";
      
      for(curLevel = virtualPath.indexOf('/'); 
	  virtualPath.length() > 0; curLevel = virtualPath.indexOf('/')) {
	if(curLevel == 0) {
	  virtualPath = virtualPath.substring(1);
	  continue;
	}

	if(curLevel > 0) {
	  name = virtualPath.substring(0, curLevel);
	  virtualPath = virtualPath.substring(curLevel + 1);
	} else {
	  name = virtualPath;
	  virtualPath = "";
	}
	// Add name to current path.
	mounted = (java.util.List) mountTable.get(parentPath);
	if(mounted == null) {
	  mounted = new java.util.Vector();
	  // System.out.println("Adding '" + parentPath + "'");
	  mountTable.put(parentPath, mounted);
	}
	MountPoint nmp = new MountPoint(name);
	if(!mounted.contains(nmp)) {
	  // System.out.println("Adding '" + name + "' to '"+ parentPath + "' " + mounted.size());
	  mounted.add(nmp);
	} else {
	  // System.out.println("Present '" + name + "' in '" + parentPath + "' " + mounted.size());
	}
	parentPath = parentPath + name + "/";
      }
      mounted = (java.util.List) mountTable.get(parentPath);
      if(mounted == null) {
	// System.out.println("Adding '" + parentPath + "'");
	mounted = new java.util.Vector();
	mountTable.put(parentPath, mounted);
      }
    }
    return mounted;
  }


  public String[] getChildren(String path) {
    int curLevel;
    String virtualPath = "/";
    ps2vfs.plugin.VfsDir dirContent = null;
    java.util.List mounted = null;
    String name = "";
    
    for(curLevel = path.indexOf('/'); 
	path.length() > 0; curLevel = path.indexOf('/')) {
      if(curLevel == 0) {
	path = path.substring(1);
	continue;
      }
      String oldPath = path;
      if(curLevel > 0) {
	name = path.substring(0, curLevel);
	path = path.substring(curLevel + 1);
      } else {
	name = path;
	path = "";
      }
      
      // Add name to current path.
      String nvp = virtualPath + name + "/";
      mounted = (java.util.List) mountTable.get(nvp);
      if(mounted == null) {
	path = oldPath;
	break;
      }
      virtualPath = nvp;
    }

    mounted = (java.util.List) mountTable.get(virtualPath);
    String[] strArray = null;
    if(mounted != null && path.length() == 0) {
      java.util.ArrayList sortedMpList = new java.util.ArrayList(mounted);
      java.util.Collections.sort(sortedMpList, new MountPoint.DirComp());
      
      int size = sortedMpList.size();
      
      strArray = new String[size];
      int n = 0;
      for(java.util.ListIterator li = sortedMpList.listIterator(); 
	  li.hasNext();) {
	MountPoint mp = (MountPoint) li.next();
	if(mp.isVirtual()) {
	  strArray[n++] = mp.getOpenPath();
	} else {
	  strArray[n++] = mp.toString();
	}
      }
    }
    return strArray;
  }
};
