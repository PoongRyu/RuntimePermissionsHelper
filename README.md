# RuntimePermissionsHelper
Android Runtime Permissions Helper

This library allows the usage of RxJava with the new Android M permission model.

Android M was added to check Permission.
but Permission check processing is so dirty.

This Project is to be simple, Checking permissions.

This Project has Two Version.

1. RxJava 
2. Non RxJava


# Install / Download
--------

Current version: [0.1.1]

Gradle:
```groovy
repositories {
    jcenter()
}

compile 'com.github.dubulee:runtimepermissionshelper:0.1.1'
```

# How to use - RX Ver.


```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Must be done during an initialization phase like onCreate
    RxPermissions.getInstance(this)
        .request(Manifest.permission.CALL_PHONE)
        .subscribe(new Subscriber<Boolean>() {
                            @Override
                            public void onCompleted() {

                            }

                            @Override
                            public void onError(Throwable e) {

                            }

                            @Override
                            public void onNext(Boolean aBoolean) {
                                if(aBoolean) {
                                    Toast.makeText(MainActivity.this,
                                            "RX Permissions OK",
                                            Toast.LENGTH_SHORT).show();
                                }
                                else {
                                    Toast.makeText(MainActivity.this,
                                            "Permission denied, can't enable the camera ",
                                            Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
}
```
  
# How to use - Non RX Ver.

* To check permissions

```java

AndroidPermissions.check(this)
    .permissions(Manifest.permission.CALL_PHONE)
    .hasPermissions(new Checker.Action0() {
        @Override
        public void call(String[] permissions) {
            // do something..
        }
    })
    .noPermissions(new Checker.Action1() {
        @Override
        public void call(String[] permissions) {
            // do something..
        }
    })
    .check();


```

* To get Result

```java

public void onRequestPermissionsResult(int requestCode, final String[] permissions, int[] grantResults) {
    AndroidPermissions.result(MainActivity.this)
        .addPermissions(REQUEST_CODE, Manifest.permission.CALL_PHONE)
        .putActions(REQUEST_CODE, new Result.Action0() {
            @Override
            public void call() {
                // do something..
            }
        }, new Result.Action1() {
            @Override
            public void call(String[] hasPermissions, String[] noPermissions) {
                // do something..
            }
        })
        .result(requestCode, permissions, grantResults);
}

```
