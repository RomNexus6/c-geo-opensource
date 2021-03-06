package cgeo.geocaching;

import cgeo.geocaching.LogTemplateProvider.LogTemplate;
import cgeo.geocaching.activity.AbstractActivity;
import cgeo.geocaching.compatibility.Compatibility;
import cgeo.geocaching.enumerations.CacheType;
import cgeo.geocaching.enumerations.StatusCode;
import cgeo.geocaching.maps.MapProviderFactory;
import cgeo.geocaching.twitter.TwitterAuthorizationActivity;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.http.HttpResponse;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;
import java.util.SortedMap;
import java.util.concurrent.atomic.AtomicReference;

public class cgeoinit extends AbstractActivity {

    private final static int SELECT_MAPFILE_REQUEST = 1;

    private ProgressDialog loginDialog = null;
    private ProgressDialog webDialog = null;
    private Handler logInHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            try {
                if (loginDialog != null && loginDialog.isShowing()) {
                    loginDialog.dismiss();
                }

                if (msg.obj == null || (msg.obj instanceof Drawable)) {
                    helpDialog(res.getString(R.string.init_login_popup), res.getString(R.string.init_login_popup_ok),
                            (Drawable) msg.obj);
                } else {
                    helpDialog(res.getString(R.string.init_login_popup),
                            res.getString(R.string.init_login_popup_failed_reason) + " " +
                                    ((StatusCode) msg.obj).getErrorString(res) + ".");
                }
            } catch (Exception e) {
                showToast(res.getString(R.string.err_login_failed));

                Log.e(Settings.tag, "cgeoinit.logInHandler: " + e.toString());
            }

            if (loginDialog != null && loginDialog.isShowing()) {
                loginDialog.dismiss();
            }

            init();
        }
    };

    private Handler webAuthHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            try {
                if (webDialog != null && webDialog.isShowing()) {
                    webDialog.dismiss();
                }

                if (msg.what > 0) {
                    helpDialog(res.getString(R.string.init_sendToCgeo), res.getString(R.string.init_sendToCgeo_register_ok).replace("####", "" + msg.what));
                } else {
                    helpDialog(res.getString(R.string.init_sendToCgeo), res.getString(R.string.init_sendToCgeo_register_fail));
                }
            } catch (Exception e) {
                showToast(res.getString(R.string.init_sendToCgeo_register_fail));

                Log.e(Settings.tag, "cgeoinit.webHandler: " + e.toString());
            }

            if (webDialog != null && webDialog.isShowing()) {
                webDialog.dismiss();
            }

            init();
        }
    };
    protected boolean enableTemplatesMenu = false;

    public cgeoinit() {
        super("c:geo-configuration");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // init

        setTheme();
        setContentView(R.layout.init);
        setTitle(res.getString(R.string.settings));

        init();
    }

    @Override
    public void onResume() {
        super.onResume();

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        init();
    }

    @Override
    public void onPause() {
        saveValues();
        super.onPause();
    }

    @Override
    public void onStop() {
        saveValues();
        Compatibility.dataChanged(getPackageName());
        super.onStop();
    }

    @Override
    public void onDestroy() {
        saveValues();

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 0, 0, res.getString(R.string.init_clear)).setIcon(android.R.drawable.ic_menu_delete);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 0) {
            boolean status = false;

            ((EditText) findViewById(R.id.username)).setText("");
            ((EditText) findViewById(R.id.password)).setText("");
            ((EditText) findViewById(R.id.passvote)).setText("");

            status = saveValues();
            if (status) {
                showToast(res.getString(R.string.init_cleared));
            } else {
                showToast(res.getString(R.string.err_init_cleared));
            }

            finish();
        }

        return false;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo) {
        if (enableTemplatesMenu) {
            menu.setHeaderTitle(R.string.init_signature_template_button);
            for (LogTemplate template : LogTemplateProvider.getTemplates()) {
                menu.add(0, template.getItemId(), 0, template.getResourceId());
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        LogTemplate template = LogTemplateProvider.getTemplate(item.getItemId());
        if (template != null) {
            return insertSignatureTemplate(template);
        }
        return super.onContextItemSelected(item);
    }

    private boolean insertSignatureTemplate(final LogTemplate template) {
        EditText sig = (EditText) findViewById(R.id.signature);
        String insertText = "[" + template.getTemplateString() + "]";
        cgBase.insertAtPosition(sig, insertText, true);
        return true;
    }

    public void init() {

        // geocaching.com settings
        ImmutablePair<String, String> login = Settings.getLogin();
        if (login != null) {
            ((EditText) findViewById(R.id.username)).setText(login.left);
            ((EditText) findViewById(R.id.password)).setText(login.right);
        }

        Button logMeIn = (Button) findViewById(R.id.log_me_in);
        logMeIn.setOnClickListener(new logIn());

        TextView legalNote = (TextView) findViewById(R.id.legal_note);
        legalNote.setClickable(true);
        legalNote.setOnClickListener(new View.OnClickListener() {

            public void onClick(View arg0) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.geocaching.com/about/termsofuse.aspx")));
            }
        });

        // gcvote settings
        String passvoteNow = Settings.getGCvoteLogin().right;
        if (passvoteNow != null) {
            ((EditText) findViewById(R.id.passvote)).setText(passvoteNow);
        }

        // go4cache settings
        TextView go4cache = (TextView) findViewById(R.id.about_go4cache);
        go4cache.setClickable(true);
        go4cache.setOnClickListener(new View.OnClickListener() {

            public void onClick(View arg0) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://go4cache.com/")));
            }
        });

        final CheckBox publicButton = (CheckBox) findViewById(R.id.publicloc);
        publicButton.setChecked(Settings.isPublicLoc());
        publicButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setPublicLoc(publicButton.isChecked());
            }
        });

        // Twitter settings
        Button authorizeTwitter = (Button) findViewById(R.id.authorize_twitter);
        authorizeTwitter.setOnClickListener(new View.OnClickListener() {

            public void onClick(View arg0) {
                Intent authIntent = new Intent(cgeoinit.this, TwitterAuthorizationActivity.class);
                startActivity(authIntent);
            }
        });

        final CheckBox twitterButton = (CheckBox) findViewById(R.id.twitter_option);
        twitterButton.setChecked(Settings.isUseTwitter() && Settings.isTwitterLoginValid());
        twitterButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setUseTwitter(twitterButton.isChecked());
                if (Settings.isUseTwitter() && !Settings.isTwitterLoginValid()) {
                    Intent authIntent = new Intent(cgeoinit.this, TwitterAuthorizationActivity.class);
                    startActivity(authIntent);
                }

                twitterButton.setChecked(Settings.isUseTwitter());
            }
        });

        // Signature settings
        EditText sigEdit = (EditText) findViewById(R.id.signature);
        if (sigEdit.getText().length() == 0) {
            sigEdit.setText(Settings.getSignature());
        }
        Button sigBtn = (Button) findViewById(R.id.signature_help);
        sigBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                helpDialog(res.getString(R.string.init_signature_help_title), res.getString(R.string.init_signature_help_text));
            }
        });
        Button templates = (Button) findViewById(R.id.signature_template);
        registerForContextMenu(templates);
        templates.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                enableTemplatesMenu = true;
                openContextMenu(v);
                enableTemplatesMenu = false;
            }
        });
        final CheckBox autoinsertButton = (CheckBox) findViewById(R.id.sigautoinsert);
        autoinsertButton.setChecked(Settings.isAutoInsertSignature());
        autoinsertButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setAutoInsertSignature(autoinsertButton.isChecked());
            }
        });

        // Cache details
        final CheckBox autoloadButton = (CheckBox) findViewById(R.id.autoload);
        autoloadButton.setChecked(Settings.isAutoLoadDescription());
        autoloadButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setAutoLoadDesc(autoloadButton.isChecked());
            }
        });

        final CheckBox ratingWantedButton = (CheckBox) findViewById(R.id.ratingwanted);
        ratingWantedButton.setChecked(Settings.isRatingWanted());
        ratingWantedButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setRatingWanted(ratingWantedButton.isChecked());
            }
        });

        final CheckBox elevationWantedButton = (CheckBox) findViewById(R.id.elevationwanted);
        elevationWantedButton.setChecked(Settings.isElevationWanted());
        elevationWantedButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setElevationWanted(elevationWantedButton.isChecked());
            }
        });

        // Other settings
        final CheckBox skinButton = (CheckBox) findViewById(R.id.skin);
        skinButton.setChecked(Settings.isLightSkin());
        skinButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setLightSkin(skinButton.isChecked());
            }
        });

        final CheckBox addressButton = (CheckBox) findViewById(R.id.address);
        addressButton.setChecked(Settings.isShowAddress());
        addressButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setShowAddress(addressButton.isChecked());
            }
        });

        final CheckBox captchaButton = (CheckBox) findViewById(R.id.captcha);
        captchaButton.setChecked(Settings.isShowCaptcha());
        captchaButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setShowCaptcha(captchaButton.isChecked());
            }
        });

        final CheckBox dirImgButton = (CheckBox) findViewById(R.id.loaddirectionimg);
        dirImgButton.setChecked(Settings.getLoadDirImg());
        dirImgButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setLoadDirImg(!Settings.getLoadDirImg());
                dirImgButton.setChecked(Settings.getLoadDirImg());
            }
        });

        final CheckBox useEnglishButton = (CheckBox) findViewById(R.id.useenglish);
        useEnglishButton.setChecked(Settings.isUseEnglish());
        useEnglishButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setUseEnglish(useEnglishButton.isChecked());
            }
        });

        final CheckBox excludeButton = (CheckBox) findViewById(R.id.exclude);
        excludeButton.setChecked(Settings.isExcludeMyCaches());
        excludeButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setExcludeMine(excludeButton.isChecked());
            }
        });

        final CheckBox disabledButton = (CheckBox) findViewById(R.id.disabled);
        disabledButton.setChecked(Settings.isExcludeDisabledCaches());
        disabledButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setExcludeDisabledCaches(disabledButton.isChecked());
            }
        });

        TextView showWaypointsThreshold = (TextView) findViewById(R.id.showwaypointsthreshold);
        showWaypointsThreshold.setText(String.valueOf(Settings.getWayPointsThreshold()));

        final CheckBox autovisitButton = (CheckBox) findViewById(R.id.trackautovisit);
        autovisitButton.setChecked(Settings.isTrackableAutoVisit());
        autovisitButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setTrackableAutoVisit(autovisitButton.isChecked());
            }
        });

        final CheckBox offlineButton = (CheckBox) findViewById(R.id.offline);
        offlineButton.setChecked(Settings.isStoreOfflineMaps());
        offlineButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setStoreOfflineMaps(offlineButton.isChecked());
            }
        });

        final CheckBox saveLogImgButton = (CheckBox) findViewById(R.id.save_log_img);
        saveLogImgButton.setChecked(Settings.isStoreLogImages());
        saveLogImgButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setStoreLogImages(saveLogImgButton.isChecked());
            }
        });

        final CheckBox livelistButton = (CheckBox) findViewById(R.id.livelist);
        livelistButton.setChecked(Settings.isLiveList());
        livelistButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setLiveList(livelistButton.isChecked());
            }
        });

        final CheckBox unitsButton = (CheckBox) findViewById(R.id.units);
        unitsButton.setChecked(!Settings.isUseMetricUnits());
        unitsButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setUseMetricUnits(!unitsButton.isChecked());
            }
        });

        final CheckBox gnavButton = (CheckBox) findViewById(R.id.gnav);
        gnavButton.setChecked(Settings.isUseGoogleNavigation());
        gnavButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setUseGoogleNavigation(gnavButton.isChecked());
            }
        });

        final CheckBox logOffline = (CheckBox) findViewById(R.id.log_offline);
        logOffline.setChecked(Settings.getLogOffline());
        logOffline.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setLogOffline(!Settings.getLogOffline());
                logOffline.setChecked(Settings.getLogOffline());
            }
        });

        final CheckBox browserButton = (CheckBox) findViewById(R.id.browser);
        browserButton.setChecked(Settings.isBrowser());
        browserButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Settings.setAsBrowser(browserButton.isChecked());
            }
        });

        // Altitude settings
        EditText altitudeEdit = (EditText) findViewById(R.id.altitude);
        altitudeEdit.setText(String.valueOf(Settings.getAltCorrection()));

        //Send2cgeo settings
        String webDeviceName = Settings.getWebDeviceName();

        if (StringUtils.isNotBlank(webDeviceName)) {
            ((EditText) findViewById(R.id.webDeviceName)).setText(webDeviceName);
        } else {
            String s = android.os.Build.MODEL;
            ((EditText) findViewById(R.id.webDeviceName)).setText(s);
        }

        Button webAuth = (Button) findViewById(R.id.sendToCgeo_register);
        webAuth.setOnClickListener(new webAuth());

        // Map source settings
        SortedMap<Integer, String> mapSources = MapProviderFactory.getMapSources();
        Spinner mapSourceSelector = (Spinner) findViewById(R.id.mapsource);
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(this, android.R.layout.simple_spinner_item, mapSources.values().toArray(new String[] {}));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mapSourceSelector.setAdapter(adapter);
        int mapsource = Settings.getMapSource();
        mapSourceSelector.setSelection(MapProviderFactory.getSourceOrdinalFromId(mapsource));
        mapSourceSelector.setOnItemSelectedListener(new cgeoChangeMapSource());

        initMapfileEdittext(false);

        Button selectMapfile = (Button) findViewById(R.id.select_mapfile);
        selectMapfile.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Intent selectIntent = new Intent(cgeoinit.this, cgSelectMapfile.class);
                startActivityForResult(selectIntent, SELECT_MAPFILE_REQUEST);
            }
        });

        refreshBackupLabel();

    }

    private void initMapfileEdittext(boolean setFocus) {
        EditText mfmapFileEdit = (EditText) findViewById(R.id.mapfile);
        mfmapFileEdit.setText(Settings.getMapFile());
        if (setFocus) {
            mfmapFileEdit.requestFocus();
        }
    }

    /**
     * @param view
     *            unused here but needed since this method is referenced from XML layout
     */
    public void backup(View view) {
        // avoid overwriting an existing backup with an empty database (can happen directly after reinstalling the app)
        if (app.getAllStoredCachesCount(true, CacheType.ALL, null) == 0) {
            helpDialog(res.getString(R.string.init_backup), res.getString(R.string.init_backup_unnecessary));
            return;
        }

        final AtomicReference<String> fileRef = new AtomicReference<String>(null);
        final ProgressDialog dialog = ProgressDialog.show(this, res.getString(R.string.init_backup), res.getString(R.string.init_backup_running), true, false);
        Thread backupThread = new Thread() {
            final Handler handler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    dialog.dismiss();
                    final String file = fileRef.get();
                    if (file != null) {
                        helpDialog(res.getString(R.string.init_backup_backup), res.getString(R.string.init_backup_success) + "\n" + file);
                    } else {
                        helpDialog(res.getString(R.string.init_backup_backup), res.getString(R.string.init_backup_failed));
                    }
                    refreshBackupLabel();
                }
            };

            @Override
            public void run() {
                fileRef.set(app.backupDatabase());
                handler.sendMessage(handler.obtainMessage());
            }
        };
        backupThread.start();
    }

    private void refreshBackupLabel() {
        TextView lastBackup = (TextView) findViewById(R.id.backup_last);
        File lastBackupFile = cgeoapplication.isRestoreFile();
        if (lastBackupFile != null) {
            lastBackup.setText(res.getString(R.string.init_backup_last) + " " + cgBase.formatTime(lastBackupFile.lastModified()) + ", " + cgBase.formatDate(lastBackupFile.lastModified()));
        } else {
            lastBackup.setText(res.getString(R.string.init_backup_last_no));
        }
    }

    /**
     * @param view
     *            unused here but needed since this method is referenced from XML layout
     */
    public void restore(View view) {
        app.restoreDatabase(this);
    }

    public boolean saveValues() {
        String usernameNew = StringUtils.trimToEmpty(((EditText) findViewById(R.id.username)).getText().toString());
        String passwordNew = StringUtils.trimToEmpty(((EditText) findViewById(R.id.password)).getText().toString());
        String passvoteNew = StringUtils.trimToEmpty(((EditText) findViewById(R.id.passvote)).getText().toString());
        // don't trim signature, user may want to have whitespace at the beginning
        String signatureNew = ((EditText) findViewById(R.id.signature)).getText().toString();
        String altitudeNew = StringUtils.trimToNull(((EditText) findViewById(R.id.altitude)).getText().toString());
        String mfmapFileNew = StringUtils.trimToEmpty(((EditText) findViewById(R.id.mapfile)).getText().toString());

        int altitudeNewInt = 0;
        if (altitudeNew != null) {
            try {
                altitudeNewInt = Integer.parseInt(altitudeNew);
            } catch (NumberFormatException e) {
                altitudeNewInt = 0;
            }
        }

        final boolean status1 = Settings.setLogin(usernameNew, passwordNew);
        final boolean status2 = Settings.setGCvoteLogin(passvoteNew);
        final boolean status3 = Settings.setSignature(signatureNew);
        final boolean status4 = Settings.setAltCorrection(altitudeNewInt);
        final boolean status5 = Settings.setMapFile(mfmapFileNew);
        TextView field = (TextView) findViewById(R.id.showwaypointsthreshold);
        Settings.setShowWaypointsThreshold(safeParse(field, 5));

        return status1 && status2 && status3 && status4 && status5;
    }

    /**
     * Returns the Int Value in the Field
     *
     * @param field
     *            the field to retrieve the integer value from
     * @param defaultValue
     *            the default value
     * @return either the field content or the default value
     */

    static private int safeParse(final TextView field, int defaultValue) {
        try {
            return Integer.parseInt(field.getText().toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static class cgeoChangeMapSource implements OnItemSelectedListener {

        @Override
        public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2,
                long arg3) {
            Settings.setMapSource(MapProviderFactory.getSourceIdFromOrdinal(arg2));
        }

        @Override
        public void onNothingSelected(AdapterView<?> arg0) {
            arg0.setSelection(MapProviderFactory.getSourceIdFromOrdinal(Settings.getMapSource()));
        }
    }

    private class logIn implements View.OnClickListener {

        public void onClick(View arg0) {
            final String username = ((EditText) findViewById(R.id.username)).getText().toString();
            final String password = ((EditText) findViewById(R.id.password)).getText().toString();

            if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
                showToast(res.getString(R.string.err_missing_auth));
                return;
            }

            loginDialog = ProgressDialog.show(cgeoinit.this, res.getString(R.string.init_login_popup), res.getString(R.string.init_login_popup_working), true);
            loginDialog.setCancelable(false);

            Settings.setLogin(username, password);
            cgBase.clearCookies();

            (new Thread() {

                @Override
                public void run() {
                    final StatusCode loginResult = cgBase.login();
                    Object payload = loginResult;
                    if (loginResult == StatusCode.NO_ERROR) {
                        cgBase.detectGcCustomDate();
                        payload = cgBase.downloadAvatar(cgeoinit.this);
                    }
                    logInHandler.obtainMessage(0, payload).sendToTarget();
                }
            }).start();
        }
    }

    private class webAuth implements View.OnClickListener {

        public void onClick(View arg0) {
            final String deviceName = ((EditText) findViewById(R.id.webDeviceName)).getText().toString();
            final String deviceCode = Settings.getWebDeviceCode();

            if (StringUtils.isBlank(deviceName)) {
                showToast(res.getString(R.string.err_missing_device_name));
                return;
            }

            webDialog = ProgressDialog.show(cgeoinit.this, res.getString(R.string.init_sendToCgeo), res.getString(R.string.init_sendToCgeo_registering), true);
            webDialog.setCancelable(false);

            (new Thread() {

                @Override
                public void run() {
                    int pin = 0;

                    final String nam = StringUtils.defaultString(deviceName);
                    final String cod = StringUtils.defaultString(deviceCode);

                    final Parameters params = new Parameters("name", nam, "code", cod);
                    HttpResponse response = cgBase.request("http://send2.cgeo.org/auth.html", params, true);

                    if (response != null && response.getStatusLine().getStatusCode() == 200)
                    {
                        //response was OK
                        String[] strings = cgBase.getResponseData(response).split(",");
                        try {
                            pin = Integer.parseInt(strings[1].trim());
                        } catch (Exception e) {
                            Log.e(Settings.tag, "webDialog: " + e.toString());
                        }
                        String code = strings[0];
                        Settings.setWebNameCode(nam, code);
                    }

                    webAuthHandler.sendEmptyMessage(pin);
                }
            }).start();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SELECT_MAPFILE_REQUEST) {
            if (resultCode == RESULT_OK) {
                if (data.hasExtra("mapfile")) {
                    Settings.setMapFile(data.getStringExtra("mapfile"));
                }
            }
            initMapfileEdittext(true);
        }
    }
}
