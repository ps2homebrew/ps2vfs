package ps2vfs.vfs;

// import
import java.util.Vector;
import java.util.List;
import java.util.Arrays;
import java.io.*;
import java.net.URI;
import java.net.URL;
import ps2vfs.plugin.*;


public class Ps2Vfs 
{
  
  private static boolean debug = false;
  private boolean enableGUI = true;
  private java.util.logging.FileHandler fhand = null; 
  private java.util.logging.Handler chand = null;
  private int port = 6969;

  public Ps2Vfs(String args[]) 
    throws Exception
  {
    String pluginPath = null;
    int n;
    for(n = 0; n < args.length; n++) {
      if(args[n].charAt(0) == '-') {
	if(args[n].equals("-nw")) 
	  enableGUI = false;
      } else {
	pluginPath = args[n];
	break;
      }
    }
    
    if(pluginPath == null) {
      printUsage();
      throw new Exception();
    }
    
    numOpenFiles = 0;
    openFiles = new java.util.Hashtable();

    initLogger();
    
    try {
      initMountTable();
      initPlugins(pluginPath);
    } catch(Throwable ee) {
      ee.printStackTrace(System.err);
      log.warning(ee.toString());
    }
  }

  private void printUsage() {
    System.out.println("Usage: [-nw] <PluginPath>");
    System.out.println("       -nw        Run without GUI");
    System.out.println("       PluginPath Path to plugin repository");
  }

  
  /**
   * This method will return the content of the deepest directory
   * qualified by the virtualPath and the cwd.
   *
   * @param virtualPath     The path to resolve.
   * @param cwd             The cwd to use if the virtualPath is not absolute,
   *                        if this is null the root will be the cwd.
   * @param getParentOfFile Set to true if a file should be allowed as the 
   *                        last element in the path. The parent dir content is returned.
   *
   * @return The content of the directory qualified by the input parameters.
   */

  public ps2vfs.plugin.VfsDir getDirContent(String virtualPath, ps2vfs.plugin.VfsDir cwd,
					    boolean getParentOfFile) 
    throws java.io.FileNotFoundException 
  {
    
    if(virtualPath == null)
      return null;

    String originalPath = virtualPath;
    if(cwd == null) {
      cwd = mountTable.getVirtualDirContent(virtualPath);
    }
    if(cwd == null) 
      throw new java.io.FileNotFoundException(originalPath + " virtual level");

    // Make sure we filter the CWD so any virtual or overloaded files get resoled.
    filterDirContent(cwd);
    if(debug)
      log.finer("VP: " + virtualPath + " cwd("  + cwd + ")");


    String cwdPath = cwd.getPath();
    int cwdPathLen = cwdPath.length();
    int vpLen = virtualPath.length();
    if(debug)
      log.finer("VPLen: " + vpLen + " cwdLen: "  + cwdPathLen);

    if(vpLen > cwdPathLen) {
      virtualPath = virtualPath.substring(cwdPathLen);
    } else {
      virtualPath = "";
    }
    String[] pathElements = virtualPath.split("/+");
    List dirContent = null;
    for(int n = 0; n < pathElements.length; n++) {
      if(pathElements[n] == null || pathElements[n].length() == 0)
	continue;
      
      VfsDirEntry dirEnt = cwd.getEntry(pathElements[n]);
      boolean lastElement = (n == pathElements.length-1);
      
      if(dirEnt == null)
	throw new java.io.FileNotFoundException(originalPath + " at '" + pathElements[n] + "'");
      
      if(dirEnt.isDirectory()) {
	// Select ok.
	cwd = resolveDir(cwd.getPath(), dirEnt);
	if(cwd == null) {
	  throw new java.io.FileNotFoundException(originalPath + " at '" + 
						  pathElements[n] + "'");
	}
      } else if(getParentOfFile && lastElement) {
	// file as last element is ok.
      } else {
	// Trying to select a file, invalid path.
	throw new java.io.FileNotFoundException(originalPath + " at '" + 
						pathElements[n] + "'");
      }
    }
    return cwd;
  }

  public ps2vfs.plugin.VfsDir resolveDir(String path, VfsDirEntry dirEnt) 
  {
    String openPath = dirEnt.getOpenPath();
    java.util.List dirContent = null;
    ps2vfs.plugin.VfsDir resolvedDir = null;

    if(openPath != null && dirEnt.isDirectory()) {
      VfsPlugin handler = dirEnt.getHandler();
      if(handler != null) {
	dirContent = handler.readDir(openPath);
      } else {
	// Try fallback method for opening path.
	File dir = new File(openPath);
	if(dir.isDirectory()) {
	  if(debug) {
	    log.finer("RD: Reading content of dir: " + dir.getAbsolutePath());
	  }
	  File[] files = dir.listFiles();
	  dirContent = ps2vfs.plugin.VfsDirEntry.toList(files, true);
	}
      }
    }
    
    if(dirContent != null) {
      resolvedDir = new ps2vfs.plugin.VfsDir(path + dirEnt.getVirtualName() + "/", dirContent);
      filterDirContent(resolvedDir);
    }
    return resolvedDir;
  }


  public int openFile(VfsDirEntry fileEnt, ps2vfs.server.Ps2VfsClient client) 
    throws TooManyOpenFilesException, java.io.FileNotFoundException 
  {
    int fd = -1;
    if(fileEnt != null) {
      VfsPlugin handler = fileEnt.getHandler();
      VfsOpenFile ofile = null;
      if(handler != null) {
	ofile = handler.open(fileEnt.getOpenPath());
      } else {
	// Dummy handler (use Jakarta VFS??)
	log.finer("Opening file: " + fileEnt.getVirtualName() + " => " + fileEnt.getOpenPath());
	ofile = new VfsDefaultOpenFile(fileEnt.getOpenPath());
	log.finer("Opened file: " + ofile);
      }
      
      if(ofile != null) {
	fd = getFileDescriptor(new OpenFileDescriptor(ofile, fileEnt.getVirtualPath(), client));
	log.finer("Opened file: " + ofile + " => fd: " + fd);
      } else {
	throw new java.io.FileNotFoundException(fileEnt.getVirtualName());
      }
    }
    return fd;
  }

  public void closeFile(int fd) {
    VfsOpenFile file = freeFileDescriptor(fd);
    file.close();
  }

  public synchronized VfsOpenFile getFileFromDescriptor(int fd) {
    OpenFileDescriptor ofd = (OpenFileDescriptor) openFiles.get(new Integer(fd));
    if(ofd != null) 
      return ofd.getFile();
    else
      return null;
  }
  
  public synchronized int getFileDescriptor(OpenFileDescriptor ofile) 
    throws TooManyOpenFilesException 
  {
    if(numOpenFiles >= maxOpenFiles) {
      throw new TooManyOpenFilesException();
    }
    numOpenFiles++;

    int fd = numOpenFiles;
    while(fd > 0 && openFiles.containsKey(new Integer(fd)) )
      fd--;

    while(openFiles.containsKey(new Integer(fd))) {
      fd++;
    }
    
    openFiles.put(new Integer(fd), ofile);
    log.finer("Allocating file descriptor " + fd);
    return fd;
  }
  
  public synchronized VfsOpenFile freeFileDescriptor(int fd) {
    Integer fdInt = new Integer(fd);
    OpenFileDescriptor ofd = (OpenFileDescriptor) openFiles.get(fdInt);
    openFiles.remove(fdInt);
    if(numOpenFiles > 0)
      numOpenFiles--;
    log.finer("Freeing file descriptor " + fdInt + " number of open files " + numOpenFiles);
    if(ofd != null) 
      return ofd.getFile();
    else
      return null;
  }

  public synchronized void closeAllClientFiles(ps2vfs.server.Ps2VfsClient client) {
    final int maxRemoveChunk = 10;
    int[] removeFds = new int[maxRemoveChunk];
    int removeN;
    
    do {
      removeN = 0;
      java.util.Iterator it = openFiles.keySet().iterator();
      while(it.hasNext() && removeN < maxRemoveChunk) {
	Integer fdInt = (Integer) it.next();
	OpenFileDescriptor ofd = (OpenFileDescriptor) openFiles.get(fdInt);
	if(ofd.getClient() == client) {
	  removeFds[removeN] = fdInt.intValue();
	  removeN++;
	}
      }
      for(int n = 0; n < removeN; n++) {
	freeFileDescriptor(removeFds[n]);
      }
    } while(removeN > 0);
  }

  public synchronized VfsOpenFileInfo[] getOpenFilesInfo() {
    VfsOpenFileInfo[] info = new VfsOpenFileInfo[numOpenFiles];
    java.util.Iterator it = openFiles.keySet().iterator();
    int n=0;
    while(it.hasNext()) {
      Integer fdInt = (Integer) it.next();
      OpenFileDescriptor ofd = (OpenFileDescriptor) openFiles.get(fdInt);
      info[n] = new VfsOpenFileInfo(fdInt.intValue(), 0);
      VfsOpenFile of = ofd.getFile();
      info[n].setInfo(of.getInfo());
      info[n].setVPath(ofd.getVirtualPath());
      info[n].setOPath(of.getOpenPath());
      n++;
    }
    return info;
  }


  public void filterDirContent(ps2vfs.plugin.VfsDir dir) {
    java.util.Iterator it = plugins.iterator();
    while(it.hasNext()) {
      VfsPlugin plugin = (VfsPlugin) it.next();
      plugin.filterDir(dir);
    }
    dir.sort();
    dir.uniqify();
    printDirContent(dir);
  }
  
  public void printDirContent(ps2vfs.plugin.VfsDir dir) {

    if(!debug) {
      return;
    }
    System.out.println("Content of " + dir.getPath());

    VfsDirEntry[] entries = dir.listEntries();
    for(int n = 0; n < entries.length; n++) {
      System.out.print(entries[n].getVirtualName());
      System.out.print(" | ");
      if(entries[n].isDirectory())
	System.out.print("d");
      else
	System.out.print("-");
      if(entries[n].isVirtualDir())
	System.out.print("v");
      else
	System.out.print("-");
      
      System.out.print(" | " + entries[n].getOpenPath() + " | ");
      System.out.println("" + entries[n].getHandler());
    }

    if(entries == null || entries.length == 0){
      System.out.println("Empty dir");
    }
  }

  public void setConsoleLogLevel(String clevel) {
    if(chand != null)  
      chand.setLevel(java.util.logging.Level.parse(clevel));
  } 

  public void setFileLogLevel(String flevel) {
    if(fhand != null) {
      fhand.setLevel(java.util.logging.Level.parse(flevel));
    }
  }

  public String getConsoleLogLevel() {
    if(chand != null) {
      return chand.getLevel().getName();
    }
    return "OFF";
  }

  public String getFileLogLevel() {
    if(fhand != null) {
      return fhand.getLevel().getName();
    }
    return "OFF";
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public void persistParams() {
    Parameters params = getParameters();
    params.setMntTable(mountTable);
    if(chand != null) {
      params.setServerLogLevel(chand.getLevel().getName());
    }
    if(fhand != null) {
      params.setServerFileLogLevel(fhand.getLevel().getName());
    }
    params.setPort(port);
    params.saveParameters();
  }

  public void startServer() {
    printBanner();


    while(true) {
      try {
	port = getParameters().getPort();
	ps2vfs.server.Ps2VfsServer server = new ps2vfs.server.Ps2VfsServer(port,
									   this, log);
	server.start();

	int sleepInterval = 60000;
	Object oFrame = null;
	if(enableGUI) {
	  ps2vfs.gui.MountPointsFrame frame = new ps2vfs.gui.MountPointsFrame(this);
	  frame.show();
	  sleepInterval = 1000;
	}

	while(true) {
	  try {
	    Thread.sleep(sleepInterval);
	    if(enableGUI) {
	      ps2vfs.gui.MountPointsFrame frame = (ps2vfs.gui.MountPointsFrame) oFrame;
	    }
	  } catch(InterruptedException e) {
	  }
	}
      } catch (Throwable e) {
	e.printStackTrace(System.err);
      }
    }
  }


  private void printBanner() {
    log.info("# Playstation 2 Virtual File System with plugin support.");
    log.info("# The PS2 interface and server is based on, with additional ");
    log.info("# adaptions based on actual PS2Reality behavior, the Playstation 2");
    log.info("# Virtual File System Example, see copyright details below.");
    log.info("# ");
    log.info("# (C)2004, Krilon ");
    log.info("# ");
    log.info("# Thanks to the PS2Reality team!");
    log.info("# ------------------------------------------------------------------------------");
  }

  public static void main(String args[]) {
    Ps2Vfs ps2vfs;
    try {
      ps2vfs = new Ps2Vfs(args);
    } catch(Exception e) {
      return;
    }
    
    ps2vfs.startServer();
    ps2vfs.persistParams();
  }



  private void initPlugins(String pluginPath) {
    try {
      log.info("Loading plugins..");	
      File dir = new File(pluginPath);
      plugins = new java.util.Vector();
      protoMap = new java.util.HashMap();
      
      java.io.File dirContent[] = dir.listFiles();
      if(dirContent == null) {
	return;
      }
      
      URL urls[] = {dir.toURI().toURL()};
      java.net.URLClassLoader loader = new java.net.URLClassLoader(urls);
      
      for(int n = 0; n < dirContent.length; n++) {
	if(!dirContent[n].isDirectory()) {
	  String name = dirContent[n].getName();
	  String myClassName = null;
	  int extpos = name.lastIndexOf('.');
	  if(extpos > 0) {
	    String ext = name.substring(extpos);
	    if(ext.equals(".jar")) {
	      java.util.jar.JarFile jf = new java.util.jar.JarFile(dirContent[n].getAbsolutePath());
	      java.util.jar.Manifest manf = jf.getManifest();
	      
	      URL jarUrls[] = {dirContent[n].toURI().toURL()};
	      java.net.URLClassLoader jarLoader = new java.net.URLClassLoader(jarUrls);
	      
	      
	      if(manf != null) {
		// Extract plugins from MANIFEST.
		java.util.jar.Attributes attrs = manf.getMainAttributes();
		
		myClassName = attrs.getValue(java.util.jar.Attributes.Name.MAIN_CLASS);
		java.util.Map emap = manf.getEntries();
		if(emap != null) {
		  java.util.Collection ecol = emap.values();
		  for(java.util.Iterator it = ecol.iterator(); it.hasNext();) {
		    attrs = (java.util.jar.Attributes) it.next();
		    myClassName = attrs.getValue(java.util.jar.Attributes.Name.MAIN_CLASS);
		    if(myClassName != null)
		      loadPluginClass(myClassName, jarLoader);
		  }
		}
	      } else {
		myClassName = name.substring(0, extpos);
		loadPluginClass(myClassName, jarLoader);
	      }
	    } else if (ext.equals(".class") || ext.equals(".jar")) {
	      myClassName = name.substring(0, extpos);
	      loadPluginClass(myClassName, loader);
	    }
	  }
	}
      }
    } catch(Throwable ee) {
      if(debug) {
	ee.printStackTrace(System.err);
      }
      log.warning(ee.toString());
    }
  }

  private void loadPluginClass(String pluginClassName, ClassLoader loader) {
    Class newPlugin = null;
    VfsPlugin newPluginInst = null;

    try {
      newPlugin = loader.loadClass(pluginClassName);
    } catch(Throwable e) {
      e.printStackTrace(System.err);
    }
    if(newPlugin != null) {
      try {
	newPluginInst = (VfsPlugin) newPlugin.newInstance();
	log.info("Loaded plugin '" + newPluginInst.getName() + "'");
      } catch (Throwable e) {
	e.printStackTrace(System.err);
	log.warning("Remove non-plugin class from plugin dir " + newPlugin);
      }
    }
    
    if(newPluginInst != null) {
      if(newPluginInst.init(log)) 
	plugins.add(newPluginInst);
    }
  }

  private void initMountTable() {
    mountTable = getParameters().getMntTable();
  }

  public VfsMountTable getMountTable() {
    return mountTable;
  }


  private void initLogger() {
    LogFormatter lf = new LogFormatter(debug);
    
    log = java.util.logging.Logger.getLogger("ps2vfs");
    log.setUseParentHandlers(false);
    log.setLevel(java.util.logging.Level.ALL);
    
    Parameters params = getParameters();

    chand = new java.util.logging.ConsoleHandler();
    chand.setLevel(java.util.logging.Level.parse(params.getServerLogLevel()));
    //chand.setLevel(java.util.logging.Level.CONFIG);
    chand.setFormatter(lf);
    log.addHandler(chand);
    /*
      java.util.logging.MemoryHandler mhand = new java.util.logging.MemoryHandler(chand2, 1000, 
      java.util.logging.Level.OFF);
      mhand.setLevel(java.util.logging.Level.CONFIG);
      log.addHandler(mhand);
    */
    try {
      fhand = new java.util.logging.FileHandler("ps2vfs-%u.log.%g");
      fhand.setLevel(java.util.logging.Level.parse(params.getServerFileLogLevel()));
      //fhand.setLevel(java.util.logging.Level.ALL);
      fhand.setFormatter(lf);
      log.addHandler(fhand);
    } catch(java.io.IOException e) {
      log.warning("Failed to open file log: " + e);
    }
    
    log.config("Log initialized.");
  }
  

  public synchronized static Parameters getParameters() {
    parameters_ = new Parameters();
    parameters_.loadParameters();
    return parameters_;
  }

  public java.util.Vector getPluginList() {
    return plugins;
  }

  private static Parameters parameters_;

  private java.util.logging.Logger log;
  private java.util.Vector plugins;
  private java.util.HashMap protoMap;



  private VfsMountTable mountTable;  

  // Open file descriptor bookkeeping.
  private java.util.Hashtable openFiles;
  private int numOpenFiles;
  private int maxOpenFiles = 20;
};

