package com.amplitude.api;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

public class DeviceInfo {

    public static final String TAG = "com.amplitude.api.DeviceInfo";

    public static final String OS_NAME = "android";

    private boolean locationListening = true;

    private Context context;

    private CachedInfo cachedInfo;

    /**
     * Internal class serves as a cache
     */
    private class CachedInfo {
        private String advertisingId;
        private String country;
        private String versionName;
        private String osName;
        private String osVersion;
        private String brand;
        private String manufacturer;
        private String model;
        private String carrier;
        private String language;
        private boolean limitAdTrackingEnabled;

        private CachedInfo() {
            advertisingId = getAdvertisingId();
            versionName = getVersionName();
            osName = getOsName();
            osVersion = getOsVersion();
            brand = getBrand();
            manufacturer = getManufacturer();
            model = getModel();
            carrier = getCarrier();
            country = getCountry();
            language = getLanguage();
        }

        /**
         * Internal methods for getting raw information
         */

        private String getVersionName() {
            PackageInfo packageInfo;
            try {
                packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                return packageInfo.versionName;
            } catch (NameNotFoundException e) {
            }
            return null;
        }

        private String getOsName() {
            return OS_NAME;
        }

        private String getOsVersion() {
            return Build.VERSION.RELEASE;
        }

        private String getBrand() {
            return Build.BRAND;
        }

        private String getManufacturer() {
            return Build.MANUFACTURER;
        }

        private String getModel() {
            return Build.MODEL;
        }

        private String getCarrier() {
            TelephonyManager manager = (TelephonyManager) context
                    .getSystemService(Context.TELEPHONY_SERVICE);
            return manager.getNetworkOperatorName();
        }

        private String getCountry() {
            // This should not be called on the main thread.

            // Prioritize reverse geocode, but until we have a result from that,
            // we try to grab the country from the network, and finally the locale
            String country = getCountryFromLocation();
            if (!TextUtils.isEmpty(country)) {
                return country;
            }

            country = getCountryFromNetwork();
            if (!TextUtils.isEmpty(country)) {
                return country;
            }
            return getCountryFromLocale();
        }

        private String getCountryFromLocation() {
            if (!isLocationListening()) {
                return null;
            }

            Location recent = getMostRecentLocation();
            if (recent != null) {
                try {
                    Geocoder geocoder = getGeocoder();
                    List<Address> addresses = geocoder.getFromLocation(recent.getLatitude(),
                            recent.getLongitude(), 1);
                    if (addresses != null) {
                        for (Address address : addresses) {
                            if (address != null) {
                                return address.getCountryCode();
                            }
                        }
                    }
                } catch (IOException e) {
                    // Failed to reverse geocode location
                } catch (NullPointerException e) {
                    // Failed to reverse geocode location
                }
            }
            return null;
        }

        private String getCountryFromNetwork() {
            try {
                TelephonyManager manager = (TelephonyManager) context
                        .getSystemService(Context.TELEPHONY_SERVICE);
                if (manager.getPhoneType() != TelephonyManager.PHONE_TYPE_CDMA) {
                    String country = manager.getNetworkCountryIso();
                    if (country != null) {
                        return country.toUpperCase(Locale.US);
                    }
                }
            } catch (Exception e) {
                // Failed to get country from network
            }
            return null;
        }

        private String getCountryFromLocale() {
            return Locale.getDefault().getCountry();
        }

        private String getLanguage() {
            return Locale.getDefault().getLanguage();
        }

        private String getAdvertisingId() {
            // This should not be called on the main thread.
            try {
                Class AdvertisingIdClient = Class
                        .forName("com.google.android.gms.ads.identifier.AdvertisingIdClient");
                Method getAdvertisingInfo = AdvertisingIdClient.getMethod("getAdvertisingIdInfo",
                        Context.class);
                Object advertisingInfo = getAdvertisingInfo.invoke(null, context);
                Method isLimitAdTrackingEnabled = advertisingInfo.getClass().getMethod(
                        "isLimitAdTrackingEnabled");
                Boolean limitAdTrackingEnabled = (Boolean) isLimitAdTrackingEnabled
                        .invoke(advertisingInfo);
                this.limitAdTrackingEnabled =
                        limitAdTrackingEnabled != null && limitAdTrackingEnabled;
                Method getId = advertisingInfo.getClass().getMethod("getId");
                advertisingId = (String) getId.invoke(advertisingInfo);
            } catch (ClassNotFoundException e) {
                Log.w(TAG, "Google Play Services SDK not found!");
            } catch (Exception e) {
                Log.e(TAG, "Encountered an error connecting to Google Play Services", e);
            }
            return advertisingId;
        }
    }

    public DeviceInfo(Context context) {
        this.context = context;
    }

    private CachedInfo getCachedInfo() {
        if (cachedInfo == null) {
            cachedInfo = new CachedInfo();
        }
        return cachedInfo;
    }

    public void prefetch() {
        getCachedInfo();
    }

    public String generateUUID() {
        return UUID.randomUUID().toString();
    }

    public String getVersionName() {
        return getCachedInfo().versionName;
    }

    public String getOsName() {
        return getCachedInfo().osName;
    }

    public String getOsVersion() {
        return getCachedInfo().osVersion;
    }

    public String getBrand() {
        return getCachedInfo().brand;
    }

    public String getManufacturer() {
        return getCachedInfo().manufacturer;
    }

    public String getModel() {
        return getCachedInfo().model;
    }

    public String getCarrier() {
        return getCachedInfo().carrier;
    }

    public String getCountry() {
        return getCachedInfo().country;
    }

    public String getLanguage() {
        return getCachedInfo().language;
    }

    public String getAdvertisingId() {
        return getCachedInfo().advertisingId;
    }

    public boolean isLimitAdTrackingEnabled() {
        return getCachedInfo().limitAdTrackingEnabled;
    }

    public Location getMostRecentLocation() {
        if (!isLocationListening()) {
            return null;
        }

        LocationManager locationManager = (LocationManager) context
                .getSystemService(Context.LOCATION_SERVICE);

        // Don't crash if the device does not have location services.
        if (locationManager == null) {
            return null;
        }

        List<String> providers = locationManager.getProviders(true);

        // It's possible that the location service is running out of process
        // and the remote getProviders call fails. Handle null provider lists.
        if (providers == null) {
            return null;
        }

        List<Location> locations = new ArrayList<Location>();
        for (String provider : providers) {
            Location location = locationManager.getLastKnownLocation(provider);
            if (location != null) {
                locations.add(location);
            }
        }

        long maximumTimestamp = -1;
        Location bestLocation = null;
        for (Location location : locations) {
            if (location.getTime() > maximumTimestamp) {
                maximumTimestamp = location.getTime();
                bestLocation = location;
            }
        }

        return bestLocation;
    }

    public boolean isLocationListening() {
        return locationListening;
    }

    public void setLocationListening(boolean locationListening) {
        this.locationListening = locationListening;
    }

    // @VisibleForTesting
    protected Geocoder getGeocoder() {
        return new Geocoder(context, Locale.ENGLISH);
    }

}
