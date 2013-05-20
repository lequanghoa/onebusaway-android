package com.joulespersecond.oba.region;

import com.joulespersecond.oba.elements.ObaRegion;
import com.joulespersecond.seattlebusbot.Application;
import com.joulespersecond.seattlebusbot.BuildConfig;
import com.joulespersecond.seattlebusbot.R;
import com.joulespersecond.seattlebusbot.UIHelp;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.location.Location;
import android.os.AsyncTask;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class ObaRegionsTask extends AsyncTask<Void, Integer, ArrayList<ObaRegion>> {

    private static final String TAG = "ObaRegionsTask";
    
    private Context mContext;
    private ProgressDialog mProgressDialog;
    
    private final boolean mForceReload;
    
    public ObaRegionsTask(Context context) {
        this.mContext = context;
        mForceReload = false;
    }
    
    public ObaRegionsTask(Context context, boolean force) {
        this.mContext = context;
        mForceReload = force;
    }
    
    @Override
    protected void onPreExecute() {        
        mProgressDialog = ProgressDialog.show(mContext, "",
                mContext.getString(R.string.region_detecting_server), true);
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setCancelable(false);
        mProgressDialog.show();
        
        super.onPreExecute();
    }

    @Override
    protected ArrayList<ObaRegion> doInBackground(Void... params) {
        ArrayList<ObaRegion> results;
        if (!mForceReload) {
            //
            // Check the DB
            //
            results = RegionUtils.getRegionsFromProvider(mContext);
            if (results != null) {
                if (BuildConfig.DEBUG) { Log.d(TAG, "Retrieved regions from database."); }
                return results;
            }
            if (BuildConfig.DEBUG) { Log.d(TAG, "Regions list retrieved from database was null."); }
        }

        results = RegionUtils.getRegionsFromServer(mContext);
        if (results == null) {
            if (BuildConfig.DEBUG) { Log.d(TAG, "Regions list retrieved from server was null."); }
            return null;
        }

        if (BuildConfig.DEBUG) { Log.d(TAG, "Retrieved regions list from server."); }
        
        RegionUtils.saveToProvider(mContext, results);
        return results;
    }
    
     @Override
    protected void onPostExecute(ArrayList<ObaRegion> results) {
        //TODO - Make new request from NETWORK_PROVIDER asynchronously, since LocationManager.getLastKnownLocation() 
        //is buggy, and NETWORK_PROVIDER should return with a new coarse location (WiFi or cell) quickly
        Location myLocation = UIHelp.getLocation2(mContext);
                     
        ObaRegion closestRegion = RegionUtils.getClosestRegion(results, myLocation); 
         
        if (mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }
        
        if(Application.get().getCurrentRegion() == null && closestRegion != null){
            //Set region application-wide
            Application.get().setCurrentRegion(closestRegion);
            if (BuildConfig.DEBUG) { Log.d(TAG, "Detected closest region '" + closestRegion.getName() + "'"); }            
        }else{
            haveUserChooseRegion(results);
        }    
         
        super.onPostExecute(results);
    }
     
    private void haveUserChooseRegion(final ArrayList<ObaRegion> result){
        // Create dialog for user to choose
        List<String> serverNames = new ArrayList<String>();
        for (ObaRegion region : result) {
            serverNames.add(region.getName());
        }

        final CharSequence[] items = serverNames
                .toArray(new CharSequence[serverNames.size()]);

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(mContext.getString(R.string.region_choose_region));
        builder.setItems(items, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int item) {
                for (ObaRegion region : result) {                    
                    if (region.getName().equals(items[item])) {
                        //Set the region application-wide
                        Application.get().setCurrentRegion(region);                        
                        if (BuildConfig.DEBUG) { Log.d(TAG, "User chose region '" + items[item] + "'."); }                                               
                        break;
                    }
                }
                // TODO - clear custom API url pref here?
            }
        });
        
        AlertDialog alert = builder.create();
        alert.show();
    }

}
