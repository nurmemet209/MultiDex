package com.example.nurmemet.multidex;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import dalvik.system.PathClassLoader;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.e("test", "getFilesDir=" + getFilesDir().getAbsolutePath());
        //getFilesDir=/data/data/com.example.test/files
        try {
            ApplicationInfo applicationInfo = getApplicationInfo(this);
            if (applicationInfo != null) {
                Log.e("test", "applicationInfo.sourceDir=" + applicationInfo.sourceDir);
                //applicationInfo.sourceDir=/data/app/com.example.test-2.apk
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }




    }


    private static ApplicationInfo getApplicationInfo(Context context) throws PackageManager.NameNotFoundException {
        PackageManager pm;
        String packageName;
        try {
            pm = context.getPackageManager();
            packageName = context.getPackageName();
        } catch (RuntimeException var4) {
            Log.w("MultiDex", "Failure while trying to obtain ApplicationInfo from Context. Must be running in test mode. Skip patching.", var4);
            return null;
        }

        if (pm != null && packageName != null) {
            ApplicationInfo applicationInfo = pm.getApplicationInfo(packageName, 128);
            return applicationInfo;
        } else {
            return null;
        }
    }

}
