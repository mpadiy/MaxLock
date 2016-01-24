/*
 * MaxLock, an Xposed applock module for Android
 * Copyright (C) 2014-2015  Maxr1998
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.Maxr1998.xposed.maxlock.ui.settings.applist;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SearchView;
import android.widget.Toast;

import com.haibison.android.lockpattern.LockPatternActivity;
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.Maxr1998.xposed.maxlock.Common;
import de.Maxr1998.xposed.maxlock.R;
import de.Maxr1998.xposed.maxlock.ui.SettingsActivity;
import de.Maxr1998.xposed.maxlock.util.MLPreferences;
import de.Maxr1998.xposed.maxlock.util.Util;

public class AppListFragment extends Fragment {

    private static final int BACKUP_STORAGE_PERMISSION_REQUEST_CODE = 101;
    private static final int RESTORE_STORAGE_PERMISSION_REQUEST_CODE = 102;
    private static List<ApplicationInfo> APP_LIST = new ArrayList<>();
    private static SetupAppListTask TASK;
    private AppListAdapter mAdapter;
    private SharedPreferences prefs;
    private ArrayAdapter<String> restoreAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setHasOptionsMenu(true);
        prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mAdapter = new AppListAdapter(AppListFragment.this, APP_LIST);
        // Generate list
        if (APP_LIST.isEmpty() && TASK == null) {
            TASK = new SetupAppListTask(this, APP_LIST, mAdapter);
            TASK.execute();
        } else {
            mAdapter.saveListBackup();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        getActivity().setTitle(getString(R.string.pref_screen_apps));
        //noinspection ConstantConditions
        ((AppCompatActivity) getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.fragment_appslist, container, false);
        // Setup layout
        FastScrollRecyclerView recyclerView = (FastScrollRecyclerView) rootView.findViewById(R.id.app_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setAdapter(mAdapter);
        return rootView;
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.applist_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
        menu.setGroupVisible(R.id.menu_group_default, false);
        final SearchView searchView = (SearchView) MenuItemCompat.getActionView(menu.findItem(R.id.toolbar_search));
        searchView.setOnSearchClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                menu.setGroupVisible(R.id.menu_group_hide_on_search, false);
            }
        });
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                ((InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(searchView.getWindowToken(), 0);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                mAdapter.getFilter().filter(s);
                return true;
            }
        });
        searchView.setOnCloseListener(new SearchView.OnCloseListener() {
            @Override
            public boolean onClose() {
                menu.setGroupVisible(R.id.menu_group_hide_on_search, true);
                return false;
            }
        });
        filterIcon(menu.findItem(R.id.toolbar_filter_activated));
    }

    @SuppressLint("CommitPrefEdits")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                return Util.hideKeyboardFromWindow(getActivity(), getView());
            case R.id.toolbar_backup_list:
                if (prefs.getBoolean(Common.ENABLE_PRO, false)) {
                    Util.checkForStoragePermission(this, BACKUP_STORAGE_PERMISSION_REQUEST_CODE, R.string.dialog_storage_permission_backup_restore);
                } else {
                    Toast.makeText(getActivity(), R.string.toast_pro_required, Toast.LENGTH_SHORT).show();
                }
                return true;
            case R.id.toolbar_restore_list:
                if (prefs.getBoolean(Common.ENABLE_PRO, false)) {
                    Util.checkForStoragePermission(this, RESTORE_STORAGE_PERMISSION_REQUEST_CODE, R.string.dialog_storage_permission_backup_restore);
                } else {
                    Toast.makeText(getActivity(), R.string.toast_pro_required, Toast.LENGTH_SHORT).show();
                }
                return true;
            case R.id.toolbar_clear_list:
                MLPreferences.getPrefsApps(getActivity()).edit().clear().commit();
                getActivity().getSharedPreferences(Common.PREFS_KEYS_PER_APP, Context.MODE_PRIVATE).edit().clear().commit();
                ((SettingsActivity) getActivity()).restart();
                return true;
            case R.id.toolbar_filter_activated:
                String appListFilter = prefs.getString("app_list_filter", "");
                switch (appListFilter) {
                    case "@*activated*":
                        prefs.edit().putString("app_list_filter", "@*deactivated*").apply();
                        break;
                    case "@*deactivated*":
                        prefs.edit().putString("app_list_filter", "").apply();
                        break;
                    default:
                        prefs.edit().putString("app_list_filter", "@*activated*").apply();
                        break;
                }
                filterIcon(item);
                filter();
                return true;
            default:
                return false;
        }
    }

    private void backupFile(File file, File directory) {
        try {
            FileUtils.copyFileToDirectory(file, directory);
        } catch (FileNotFoundException f) {
            f.printStackTrace();
        } catch (IOException e) {
            Toast.makeText(getActivity(), R.string.toast_backup_restore_exception, Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length == 0 || grantResults[0] == PackageManager.PERMISSION_DENIED) {
            return;
        }
        final String prefsAppsName = Common.PREFS_APPS + ".xml";
        final String prefsPerAppName = Common.PREFS_KEYS_PER_APP + ".xml";
        final File prefsAppsFile = new File(Util.dataDir(getActivity()) + "shared_prefs/" + prefsAppsName);
        final File prefsPerAppFile = new File(Util.dataDir(getActivity()) + "shared_prefs/" + prefsPerAppName);
        switch (requestCode) {
            case BACKUP_STORAGE_PERMISSION_REQUEST_CODE:
                String currentBackupDirPath = Common.BACKUP_DIR + new SimpleDateFormat("yyyy-MM-dd-HH.mm.ss", Locale.getDefault())
                        .format(new Date(System.currentTimeMillis())) + File.separator;
                backupFile(prefsAppsFile, new File(currentBackupDirPath));
                backupFile(prefsPerAppFile, new File(currentBackupDirPath));
                if (new File(currentBackupDirPath).exists() && new File(currentBackupDirPath + prefsAppsName).exists())
                    Toast.makeText(getActivity(), R.string.toast_backup_success, Toast.LENGTH_SHORT).show();
                break;
            case RESTORE_STORAGE_PERMISSION_REQUEST_CODE:
                List<String> list = new ArrayList<>(Arrays.asList(new File(Common.BACKUP_DIR).list()));
                restoreAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, list);
                AlertDialog restoreDialog = new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.dialog_text_restore_list)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setAdapter(restoreAdapter, new DialogInterface.OnClickListener() {
                            @SuppressWarnings("deprecation")
                            @SuppressLint("InlinedApi")
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                File restorePackagesFile = new File(Common.BACKUP_DIR + restoreAdapter.getItem(i) + File.separator + prefsAppsName);
                                File restorePerAppFile = new File(Common.BACKUP_DIR + restoreAdapter.getItem(i) + File.separator + prefsPerAppName);
                                try {
                                    if (restorePackagesFile.exists()) {
                                        FileUtils.deleteQuietly(prefsAppsFile);
                                        FileUtils.copyFile(restorePackagesFile, prefsAppsFile);
                                    }
                                    if (restorePerAppFile.exists()) {
                                        FileUtils.deleteQuietly(prefsPerAppFile);
                                        FileUtils.copyFile(restorePerAppFile, prefsPerAppFile);
                                    }
                                } catch (IOException e) {
                                    Toast.makeText(getActivity(), R.string.toast_no_files_to_restore, Toast.LENGTH_SHORT).show();
                                }
                                getActivity().getSharedPreferences(Common.PREFS_APPS, Context.MODE_MULTI_PROCESS);
                                getActivity().getSharedPreferences(Common.PREFS_KEYS_PER_APP, Context.MODE_MULTI_PROCESS);
                                Toast.makeText(getActivity(), R.string.toast_restore_success, Toast.LENGTH_SHORT).show();
                                ((SettingsActivity) getActivity()).restart();
                            }
                        })
                        .show();
                restoreDialog.getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                    @Override
                    public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                        try {
                            FileUtils.deleteDirectory(new File(Common.BACKUP_DIR + restoreAdapter.getItem(i)));
                            restoreAdapter.remove(restoreAdapter.getItem(i));
                            restoreAdapter.notifyDataSetChanged();
                        } catch (IOException e) {
                            e.printStackTrace();
                            return false;
                        }
                        return true;
                    }
                });
                break;
        }
    }

    @SuppressWarnings("deprecation")
    private void filterIcon(MenuItem item) {
        if (prefs == null) {
            return;
        }
        String filter = prefs.getString("app_list_filter", "");
        Drawable icon = getResources().getDrawable(R.drawable.ic_apps_24dp);
        switch (filter) {
            case "@*activated*":
                icon = getResources().getDrawable(R.drawable.ic_checked_24dp);
                break;
            case "@*deactivated*":
                icon = getResources().getDrawable(R.drawable.ic_unchecked_24dp);
                break;
        }
        item.setIcon(icon);
    }

    public void filter() {
        mAdapter.getFilter().filter(prefs.getString("app_list_filter", ""));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (String.valueOf(requestCode).startsWith(String.valueOf(Util.PATTERN_CODE_APP))) {
            if (resultCode == LockPatternActivity.RESULT_OK) {
                String app = APP_LIST.get(Integer.parseInt(String.valueOf(requestCode).substring(1))).packageName;
                Util.receiveAndSetPattern(getActivity(), data.getCharArrayExtra(LockPatternActivity.EXTRA_PATTERN), app);
            }
        } else super.onActivityResult(requestCode, resultCode, data);
    }
}