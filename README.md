#Delvik环境下MultiDex实现原理与源码分析

* 提取主Dex以外的所有其他Dex文件到/data/data/应用包名称/code-cache/secondary-dexs目录下
* 调用PathClassLoader的 pathList对象的makeDexElements方法
* PathClassLoader的 pathList对象的dexElements数组进行扩容并且把第一步获取的Dex文件列表保存进去
```java
  public static void install(Context context) {
        Log.i("MultiDex", "install");
        if(IS_VM_MULTIDEX_CAPABLE) {
            Log.i("MultiDex", "VM has multidex support, MultiDex support library is disabled.");
        } else if(VERSION.SDK_INT < 4) {
            throw new RuntimeException("Multi dex installation failed. SDK " + VERSION.SDK_INT + " is unsupported. Min SDK version is " + 4 + ".");
        } else {
            try {
                ApplicationInfo e = getApplicationInfo(context);
                if(e == null) {
                    return;
                }

                Set var2 = installedApk;
                synchronized(installedApk) {
                //applicationInfo.sourceDir=/data/app/com.example.test-2.apk
                    String apkPath = e.sourceDir;
                    if(installedApk.contains(apkPath)) {
                        return;
                    }

                    installedApk.add(apkPath);
                    if(VERSION.SDK_INT > 20) {
                        Log.w("MultiDex", "MultiDex is not guaranteed to work in SDK version " + VERSION.SDK_INT + ": SDK version higher than " + 20 + " should be backed by " + "runtime with built-in multidex capabilty but it\'s not the " + "case here: java.vm.version=\"" + System.getProperty("java.vm.version") + "\"");
                    }

                    ClassLoader loader;
                    try {
                        loader = context.getClassLoader();
                    } catch (RuntimeException var9) {
                        Log.w("MultiDex", "Failure while trying to obtain Context class loader. Must be running in test mode. Skip patching.", var9);
                        return;
                    }

                    if(loader == null) {
                        Log.e("MultiDex", "Context class loader is null. Must be running in test mode. Skip patching.");
                        return;
                    }

                    try {
                        clearOldDexDir(context);
                    } catch (Throwable var8) {
                        Log.w("MultiDex", "Something went wrong when trying to clear old MultiDex extraction, continuing without cleaning.", var8);
                    }
                    ///data/data/com.example.test/code-cache/secondary-dexes
                    File dexDir = new File(e.dataDir, SECONDARY_FOLDER_NAME);
                    //返回主Dex之外的Dex文件列表
                    List files = MultiDexExtractor.load(context, e, dexDir, false);
                    //文件校验
                    if(checkValidZipFiles(files)) {
                        //安装次Dex文件
                        installSecondaryDexes(loader, dexDir, files);
                    } else {
                        Log.w("MultiDex", "Files were not valid zip files.  Forcing a reload.");
                        files = MultiDexExtractor.load(context, e, dexDir, true);
                        if(!checkValidZipFiles(files)) {
                            throw new RuntimeException("Zip files were not valid.");
                        }
                    ///data/data/com.example.test/code-cache/secondary-dexes
                    //files 次Dex文件列表
                        installSecondaryDexes(loader, dexDir, files);
                    }
                }
            } catch (Exception var11) {
                Log.e("MultiDex", "Multidex installation failure", var11);
                throw new RuntimeException("Multi dex installation failed (" + var11.getMessage() + ").");
            }

            Log.i("MultiDex", "install done");
        }
    }

```
下面是load方法

```java
 static List<File> load(Context context, ApplicationInfo applicationInfo, File dexDir, boolean forceReload) throws IOException {
        Log.i("MultiDex", "MultiDexExtractor.load(" + applicationInfo.sourceDir + ", " + forceReload + ")");
        File sourceApk = new File(applicationInfo.sourceDir);
        long currentCrc = getZipCrc(sourceApk);
        List files;
        //第一次进来的时候返回false
        if(!forceReload && !isModified(context, sourceApk, currentCrc)) {
            try {
                ///data/app/com.example.test-2.apk
                ///data/data/com.example.test/code-cache/secondary-dexes
                files = loadExistingExtractions(context, sourceApk, dexDir);
            } catch (IOException var9) {
                Log.w("MultiDex", "Failed to reload existing extracted secondary dex files, falling back to fresh extraction", var9);
                files = performExtractions(sourceApk, dexDir);
                putStoredApkInfo(context, getTimeStamp(sourceApk), currentCrc, files.size() + 1);
            }
        } else {
                
            Log.i("MultiDex", "Detected that extraction must be performed.");
            //首先提取主Dex之外的所有Dex
            ///data/data/com.example.test/code-cache/secondary-dexes
            ///data/app/com.example.test-2.apk
            files = performExtractions(sourceApk, dexDir);
            //修改时间，校验码，文件大小保存到SharedPreference
            putStoredApkInfo(context, getTimeStamp(sourceApk), currentCrc, files.size() + 1);
        }

        Log.i("MultiDex", "load found " + files.size() + " secondary dex files");
        return files;
    }
```
下面是loadExistingExtractions函数

```java
private static List<File> loadExistingExtractions(Context context, File sourceApk, File dexDir) throws IOException {
        Log.i("MultiDex", "loading existing secondary dex files");
        //sourceApk是/data/app/com.example.test-2.apk
        //dexDir是/data/data/com.example.test/code-cache/secondary-dexes
        String extractedFilePrefix = sourceApk.getName() + ".classes";
        int totalDexNumber = getMultiDexPreferences(context).getInt("dex.number", 1);
        ArrayList files = new ArrayList(totalDexNumber);

        for(int secondaryNumber = 2; secondaryNumber <= totalDexNumber; ++secondaryNumber) {
            String fileName = extractedFilePrefix + secondaryNumber + ".zip";
            File extractedFile = new File(dexDir, fileName);
            if(!extractedFile.isFile()) {
                throw new IOException("Missing extracted secondary dex file \'" + extractedFile.getPath() + "\'");
            }

            files.add(extractedFile);
            if(!verifyZipFile(extractedFile)) {
                Log.i("MultiDex", "Invalid zip file: " + extractedFile);
                throw new IOException("Invalid ZIP file.");
            }
        }

        return files;
    }
```
installSecondaryDexes方法如下
```java
 private static void installSecondaryDexes(ClassLoader loader, File dexDir, List<File> files) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, InvocationTargetException, NoSuchMethodException, IOException {
        if(!files.isEmpty()) {
            if(VERSION.SDK_INT >= 19) {
                MultiDex.V19.install(loader, files, dexDir);
            } else if(VERSION.SDK_INT >= 14) {
                MultiDex.V14.install(loader, files, dexDir);
            } else {
                MultiDex.V4.install(loader, files);
            }
        }

    }

```
v19实现方式
```java
 private static final class V19 {
        private V19() {
        }
        //此函数有两个作用，一个是调PathClassLoader类的dexPathList对象的makeDexElements,二是dexPathList对象dexElements成员变量进行扩容并把次Dex文件列表保存进去
        private static void install(ClassLoader loader, List<File> additionalClassPathEntries, File optimizedDirectory) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, InvocationTargetException, NoSuchMethodException {
        //获取pathListFile变量
            Field pathListField = MultiDex.findField(loader, "pathList");
            //获取对象值
            Object dexPathList = pathListField.get(loader);
            ArrayList suppressedExceptions = new ArrayList();
            //对象dexPathList的成员变量dexElements扩容(dexElements应该是个数组)，并且把次dex文件的地址保存到里面去
            MultiDex.expandFieldArray(dexPathList, "dexElements", makeDexElements(dexPathList, new ArrayList(additionalClassPathEntries), optimizedDirectory, suppressedExceptions));
            //处理异常
            if(suppressedExceptions.size() > 0) {
                Iterator suppressedExceptionsField = suppressedExceptions.iterator();

                while(suppressedExceptionsField.hasNext()) {
                    IOException dexElementsSuppressedExceptions = (IOException)suppressedExceptionsField.next();
                    Log.w("MultiDex", "Exception in makeDexElement", dexElementsSuppressedExceptions);
                }

                Field suppressedExceptionsField1 = MultiDex.findField(loader, "dexElementsSuppressedExceptions");
                IOException[] dexElementsSuppressedExceptions1 = (IOException[])((IOException[])suppressedExceptionsField1.get(loader));
                if(dexElementsSuppressedExceptions1 == null) {
                    dexElementsSuppressedExceptions1 = (IOException[])suppressedExceptions.toArray(new IOException[suppressedExceptions.size()]);
                } else {
                    IOException[] combined = new IOException[suppressedExceptions.size() + dexElementsSuppressedExceptions1.length];
                    suppressedExceptions.toArray(combined);
                    System.arraycopy(dexElementsSuppressedExceptions1, 0, combined, suppressedExceptions.size(), dexElementsSuppressedExceptions1.length);
                    dexElementsSuppressedExceptions1 = combined;
                }

                suppressedExceptionsField1.set(loader, dexElementsSuppressedExceptions1);
            }

        }
        //调对象dexPathList的makeDexElements方法构造Dex文件列表，并返回dx文件列表
        private static Object[] makeDexElements(Object dexPathList, ArrayList<File> files, File optimizedDirectory, ArrayList<IOException> suppressedExceptions) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
            Method makeDexElements = MultiDex.findMethod(dexPathList, "makeDexElements", new Class[]{ArrayList.class, File.class, ArrayList.class});
            return (Object[])((Object[])makeDexElements.invoke(dexPathList, new Object[]{files, optimizedDirectory, suppressedExceptions}));
        }
    }

```
