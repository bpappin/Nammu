package pl.tajchert.nammu;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.LocationManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by Michal Tajchert on 2015-06-04.
 */
public class Nammu {
    private static final String TAG = Nammu.class.getSimpleName();
    private static Context context;
    private static SharedPreferences sharedPreferences;
    private static final String KEY_PREV_PERMISSIONS = "previous_permissions";
    private static ArrayList<PermissionRequest> permissionRequests = new ArrayList<PermissionRequest>();

    public static void init(Context context) {
        sharedPreferences = context.getSharedPreferences("pl.tajchert.runtimepermissionhelper", Context.MODE_PRIVATE);
        Nammu.context = context;
    }

    /**
     * Check that all given permissions have been granted by verifying that each entry in the
     * given array is of the value {@link PackageManager#PERMISSION_GRANTED}.
     */
    public static boolean verifyPermissions(int[] grantResults) {
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if the Activity has access to all given permissions.
     * Always returns true on platforms below M.
     */
    public static boolean hasPermission(Activity activity, String permission) {
        if (!isMNC()) {
            return true;
        }

        return activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Returns true if the Activity has access to a given permission.
     * Always returns true on platforms below M.
     */
    public static boolean hasPermission(Activity activity, String[] permissions) {
        if (!isMNC()) {
            return true;
        }

        for (String permission : permissions) {
            if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private static boolean isMNC() {
        /*
         TODO: In the Android M Preview release, checking if the platform is M is done through
         the codename, not the version code. Once the API has been finalised, the following check
         should be used: */
        // return Build.VERSION.SDK_INT == Build.VERSION_CODES.MNC

        return "MNC".equals(Build.VERSION.CODENAME);
    }

    public static void askForPermission(Activity activity, String permission, PermissionCallback permissionCallback) {
        askForPermission(activity, new String[]{permission}, permissionCallback);
    }

    public static void askForPermission(Activity activity, String[] permissions, PermissionCallback permissionCallback) {
        if (permissionCallback == null) {
            return;
        }
        if (hasPermission(activity, permissions)) {
            permissionCallback.permissionGranted();
            return;
        }
        PermissionRequest permissionRequest = new PermissionRequest(new ArrayList<String>(Arrays.asList(permissions)), permissionCallback);
        permissionRequests.add(permissionRequest);

        activity.requestPermissions(permissions, permissionRequest.getRequestCode());
    }

    public static void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        PermissionRequest requestResult = new PermissionRequest(requestCode);
        if (permissionRequests.contains(requestResult)) {
            PermissionRequest permissionRequest = permissionRequests.get(permissionRequests.indexOf(requestResult));
            if (verifyPermissions(grantResults)) {
                //Permission has been granted
                permissionRequest.getPermissionCallback().permissionGranted();
            } else {
                permissionRequest.getPermissionCallback().permissionRefused();

            }
            permissionRequests.remove(requestResult);
        }
    }


    //Listening part

    /**
     * Save permission when we got granted it - for later use to detect when it will got revoke
     * @param permissions
     */
    public static void savePermission(String[] permissions) {
        if(permissions == null) {
            return;
        }
        Set<String> set = new HashSet<String>();
        for(String perm : permissions) {
            set.add(perm);
        }
        set.addAll(getPrevPermissions());
        sharedPreferences.edit().putStringSet(KEY_PREV_PERMISSIONS, set).apply();
    }

    /**
     * Save permission when we got granted it - for later use to detect when it will got revoke
     * @param permissions
     */
    public static void savePermission(ArrayList<String> permissions) {
        if(permissions == null) {
            return;
        }
        Set<String> set = new HashSet<String>();
        set.addAll(permissions);
        set.addAll(getPrevPermissions());
        sharedPreferences.edit().putStringSet(KEY_PREV_PERMISSIONS, set).apply();
    }

    /**
     * Save permission when we got granted it - for later use to detect when it will got revoke
     * @param permission
     */
    public static void savePermission(String permission) {
        if(permission == null) {
            return;
        }
        Set<String> set = new HashSet<String>();
        set.add(permission);
        set.addAll(getPrevPermissions());
        sharedPreferences.edit().putStringSet(KEY_PREV_PERMISSIONS, set).apply();
    }

    /**
     * Get list of previous Permission that we are listening to, past tense as they are quite possible outdated (saved with savePermission())
     * @return
     */
    public static ArrayList<String> getPrevPermissions() {
        ArrayList<String> prevPermissions = new ArrayList<String>();
        prevPermissions.addAll(sharedPreferences.getStringSet(KEY_PREV_PERMISSIONS, new HashSet<String>()));
        return prevPermissions;
    }

    /**
     * Check if we are subscribed to give Permission
     * @param permission
     * @return
     */
    public static boolean containsPermission(String permission) {
        if(permission == null) {
            return false;
        }
        if(getPrevPermissions().contains(permission)) {
            return true;
        }
        return false;
    }

    /**
     * Use to unsubscribe to particular Permission
     * @param permission
     */
    public static void removePermission(String permission) {
        if(containsPermission(permission)) {
            ArrayList<String> prevPermissions = getPrevPermissions();
            prevPermissions.remove(permission);
            Set<String> set = new HashSet<String>();
            set.addAll(prevPermissions);
            sharedPreferences.edit().putStringSet(KEY_PREV_PERMISSIONS, set).apply();
        }
    }

    /**
     * Used to trigger comparing process - @permissionListener will be called each time Permission was revoked
     * Use when not in Activity as it uses manual check of permissions
     * @param permissionListener
     */
    public static void permissionCompare(PermissionListener permissionListener) {
        Nammu.permissionCompare(null, permissionListener);
    }

    /**
     * Used to trigger comparing process - @permissionListener will be called each time Permission was revoked
     * @param activity
     * @param permissionListener
     */
    public static void permissionCompare(Activity activity, PermissionListener permissionListener) {
        if(activity == null && context == null) {
            throw new RuntimeException("Before comparing permissions you need to call Nammu.init(context)");

        }
        ArrayList<String> prevPermissions = getPrevPermissions();
        for(String permission : prevPermissions) {
            if(activity == null) {
                //We do background check - for example from Service
                boolean status = checkPermission(permission);
                if(!status) {
                    if (permissionListener != null) {
                        permissionListener.permissionsChanged(permission);
                    }
                    removePermission(permission);
                }
            } else {
                //We do check on Activity instance (official API)
                if (activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
                    //is ok, we still have this permission
                } else {
                    //We lost access to this permission, remove it from list of saved granted permissions and inform listener
                    if (permissionListener != null) {
                        permissionListener.permissionsChanged(permission);
                    }
                    removePermission(permission);
                }
            }
        }
    }

    public static boolean checkPermission(String permissionName) {
        if(context == null) {
            throw new RuntimeException("Before comparing permissions you need to call Nammu.init(context)");
        }
        return checkPermissionSafe(permissionName);
    }

    private static boolean checkPermissionSafe(String permissionName) {
        if(permissionName == null) {
            return false;
        }

        if("android.permission.READ_CALENDAR".equals(permissionName)
                || "android.permission.WRITE_CALENDAR".equals(permissionName)) {
            return  checkCalendar(context);
        } else if("android.permission.CAMERA".equals(permissionName)) {
            throw new RuntimeException("Camera background check is yet not implemented, if you know solution " +
                    "please do a pull request, if not please use method based on Activity instance to check permission");
            //return checkCamera(context);
        } else if("android.permission.READ_CONTACTS".equals(permissionName)
                || "android.permission.WRITE_CONTACTS".equals(permissionName)
                || "android.permission.READ_PROFILE".equals(permissionName)
                || "android.permission.WRITE_PROFILE".equals(permissionName)) {
            return checkContacts(context);
        } else if("android.permission.ACCESS_FINE_LOCATION".equals(permissionName)
                || "android.permission.ACCESS_COARSE_LOCATION".equals(permissionName)) {
            return checkLocation(context);
        } else if("android.permission.RECORD_AUDIO".equals(permissionName)) {
            return checkMicrophone();
        } else if("android.permission.READ_PHONE_STATE".equals(permissionName)
                || "android.permission.CALL_PHONE".equals(permissionName)
                || "android.permission.READ_CALL_LOG".equals(permissionName)
                || "com.android.voicemail.permission.ADD_VOICEMAIL".equals(permissionName)
                || "android.permission.USE_SIP".equals(permissionName)
                || "android.permission.PROCESS_OUTGOING_CALLS".equals(permissionName)) {
            return checkPhone(context);
        } else if("android.permission.BODY_SENSORS".equals(permissionName)
                || "android.permission.USE_FINGERPRINT".equals(permissionName)) {
            return checkSensors(context);
        } else if("android.permission.SEND_SMS".equals(permissionName)
                || "android.permission.RECEIVE_SMS".equals(permissionName)
                || "android.permission.READ_SMS".equals(permissionName)
                || "android.permission.RECEIVE_WAP_PUSH".equals(permissionName)
                || "android.permission.RECEIVE_MMS".equals(permissionName)
                || "android.permission.READ_CELL_BROADCASTS".equals(permissionName)) {
            return checkSms(context);
        }
        return false;
    }

    /**
     * Check for android.permission-group.CALENDAR
     * @param context
     * @return
     */
    public static boolean checkCalendar(Context context) {
        try {
            Cursor cursor = context.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, null, null, null, null);
            cursor.close();
        } catch (SecurityException e) {
            //No android.permission-group.CALENDAR
            return false;
        }
        return true;
    }

    /**
     * Check for android.permission-group.CAMERA
     * @return
     */
    private static boolean checkCamera(Context context) {
        try {
            Camera camera = Camera.open();
            //TODO call some method that requires Check for android.permission-group.CAMERA, and not Camera.open()
            // as it result in crash in first run of other instance of Camera
            // Maybe Camera2 API? I couldn't find any method that doesn't open camera view and requires permission at the same time
        } catch (RuntimeException e) {
            Log.e(TAG, "checkCamera error: " + e.getLocalizedMessage());
            return false;
        }

        return true;
    }

    /**
     * Check for android.permission-group.CONTACTS
     * @param context
     * @return
     */
    public static boolean checkContacts(Context context) {
        try {
            Cursor cursor = context.getContentResolver().query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);
            cursor.close();
        } catch (SecurityException e) {
            //No android.permission-group.CONTACTS
            return false;
        }
        return true;
    }

    /**
     * Check for android.permission-group.LOCATION
     * @param context
     * @return
     */
    public static boolean checkLocation(Context context) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        criteria.setPowerRequirement(Criteria.POWER_LOW);
        String bestProvider = locationManager.getBestProvider(criteria, false);
        if (bestProvider == null) {
            //No android.permission-group.LOCATION
            return false;
        }
        return true;
    }

    /**
     * Check for android.permission-group.MICROPHONE
     * @return
     */
    public static boolean checkMicrophone() {
        MediaRecorder mRecorder = new MediaRecorder();
        try {
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        } catch (RuntimeException e) {
            //No android.permission-group.MICROPHONE
            return false;
        }
        return true;
    }

    /**
     * Check for android.permission-group.PHONE
     * @param context
     * @return
     */
    public static boolean checkPhone(Context context) {
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        try {
            telephonyManager.getDeviceId();
        } catch (SecurityException e) {
            //No android.permission-group.PHONE
            return false;
        }
        return true;
    }

    /**
     * Check for android.permission-group.SENSORS
     * @param context
     * @return
     */
    public static boolean checkSensors(Context context) {
        SensorManager mSensorManager = ((SensorManager)context.getSystemService(Context.SENSOR_SERVICE));
        try {
            Sensor sensor=  mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
            if(sensor != null) {
                sensor.getVendor();
            } else {
                //We don't have sensor so we cannot test if we have access to it or not
            }
        } catch (SecurityException e) {
            //No android.permission-group.SENSORS
            return false;
        }
        return true;
    }

    /**
     * Check for android.permission-group.SMS
     * @param context
     * @return
     */
    public static boolean checkSms(Context context) {
        try {
            Cursor cursor = context.getContentResolver().query(Uri.parse("content://sms/inbox"), null, null, null, null);
            cursor.close();
        } catch (SecurityException e) {
            //No android.permission-group.SMS
            return false;
        }
        return true;
    }
}
