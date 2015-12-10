package com.github.dubu.runtimepermissionshelper.rxver;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rx.Observable;
import rx.functions.Func1;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;

public class RxPermissions {

    public static final String TAG = RxPermissions.class.getSimpleName();
    private static RxPermissions sSingleton;

    public static RxPermissions getInstance(Context ctx) {
        if (sSingleton == null) {
            sSingleton = new RxPermissions(ctx.getApplicationContext());
        }
        return sSingleton;
    }

    private Context mCtx;

    private Map<String, PublishSubject<Permission>> mSubjects = new HashMap<>();
    private boolean mLogging;

    RxPermissions(Context ctx) {
        mCtx = ctx;
    }

    public void setLogging(boolean logging) {
        mLogging = logging;
    }

    private void log(String message) {
        if (mLogging) {
            Log.d(TAG, message);
        }
    }

    public Observable<Permission> requestEach(final String... permissions) {
        return requestEach(null, permissions);
    }

    public Observable<Permission> requestEach(final Observable<?> trigger,
                                              final String... permissions) {
        if (permissions == null || permissions.length == 0) {
            throw new IllegalArgumentException("RxPermissions.request/requestEach requires at least one input permission");
        }
        return oneOf(trigger, pending(permissions))
                .flatMap(new Func1<Object, Observable<Permission>>() {
                    @Override
                    public Observable<Permission> call(Object o) {
                        return requestPermission(permissions);
                    }
                });
    }

    public Observable<Boolean> request(final String... permissions) {
        return request(null, permissions);
    }

    public Observable<Boolean> request(final Observable<?> trigger, final String... permissions) {
        return requestEach(trigger, permissions)
                .buffer(permissions.length)
                .flatMap(new Func1<List<Permission>, Observable<Boolean>>() {
                    @Override
                    public Observable<Boolean> call(List<Permission> permissions) {
                        if (permissions.isEmpty()) {
                            return Observable.empty();
                        }
                        for (Permission p : permissions) {
                            if (!p.granted) {
                                return Observable.just(false);
                            }
                        }
                        return Observable.just(true);
                    }
                });
    }


    private Observable<?> pending(final String... permissions) {
        for (String p : permissions) {
            Subject s = mSubjects.get(p);
            if (s == null || !s.hasCompleted()) {
                return Observable.empty();
            }
        }
        return Observable.just(null);
    }

    private Observable<Object> oneOf(Observable<?> trigger, Observable<?> pending) {
        if (trigger == null) {
            return Observable.just(null);
        }
        return Observable.merge(trigger, pending);
    }

    @TargetApi(Build.VERSION_CODES.M)
    private Observable<Permission> requestPermission(final String... permissions) {
        if (mLogging) {
            log("Requesting permissions " + TextUtils.join(", ", permissions));
        }

        List<Observable<Permission>> list = new ArrayList<>(permissions.length);
        List<String> unrequestedPermissions = new ArrayList<>();

        for (String permission : permissions) {
            if (isGranted(permission)) {
                list.add(Observable.just(new Permission(permission, true)));
                continue;
            }

            if (isRevoked(permission)) {
                list.add(Observable.just(new Permission(permission, false)));
                continue;
            }

            PublishSubject<Permission> subject = mSubjects.get(permission);
            if (subject == null || subject.hasCompleted()) {
                if (subject == null) {
                    unrequestedPermissions.add(permission);
                }
                subject = PublishSubject.create();
                mSubjects.put(permission, subject);
            }
            list.add(subject);
        }

        if (!unrequestedPermissions.isEmpty()) {
            startShadowActivity(unrequestedPermissions
                    .toArray(new String[unrequestedPermissions.size()]));
        }
        return Observable.concat(Observable.from(list));
    }

    public Observable<Boolean> shouldShowRequestPermissionRationale(final Activity activity,
                                                                    final String... permissions) {
        if (!isMarshmallow()) {
            return Observable.just(false);
        }
        return Observable.just(shouldShowRequestPermissionRationale_(activity, permissions));
    }

    @TargetApi(Build.VERSION_CODES.M)
    private boolean shouldShowRequestPermissionRationale_(final Activity activity,
                                                          final String... permissions) {
        for (String p : permissions) {
            if (!isGranted(p) && !activity.shouldShowRequestPermissionRationale(p)) {
                return false;
            }
        }
        return true;
    }

    void startShadowActivity(String[] permissions) {
        log("startShadowActivity " + TextUtils.join(", ", permissions));
        Intent intent = new Intent(mCtx, EmptyActivity.class);
        intent.putExtra("permissions", permissions);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mCtx.startActivity(intent);
    }

    public boolean isGranted(String permission) {
        return !isMarshmallow() || isGranted_(permission);
    }

    public boolean  isRevoked(String permission) {
        return isMarshmallow() && isRevoked_(permission);
    }

    boolean isMarshmallow() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    @TargetApi(Build.VERSION_CODES.M)
    private boolean isGranted_(String permission) {
        return mCtx.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    @TargetApi(Build.VERSION_CODES.M)
    private boolean isRevoked_(String permission) {
        return mCtx.getPackageManager().isPermissionRevokedByPolicy(permission, mCtx.getPackageName());
    }

    void onDestroy() {
        log("onDestroy");
        for (Subject subject : mSubjects.values()) {
            subject.onCompleted();
        }
    }

    void onRequestPermissionsResult(int requestCode,
                                    String permissions[], int[] grantResults) {
        for (int i = 0, size = permissions.length; i < size; i++) {
            log("onRequestPermissionsResult  " + permissions[i]);
            // Find the corresponding subject
            PublishSubject<Permission> subject = mSubjects.get(permissions[i]);
            if (subject == null) {
                // No subject found
                throw new IllegalStateException("RxPermissions.onRequestPermissionsResult invoked but didn't find the corresponding permission request.");
            }
            mSubjects.remove(permissions[i]);
            boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
            subject.onNext(new Permission(permissions[i], granted));
            subject.onCompleted();
        }
    }
}
