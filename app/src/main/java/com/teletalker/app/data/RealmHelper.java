package com.teletalker.app.data;

import io.realm.Realm;
import io.realm.RealmConfiguration;

public class RealmHelper {

    public static Realm getRealmInstance() {
        RealmConfiguration config = new RealmConfiguration.Builder()
//                .name("myrealm.realm")
//                .encryptionKey(getKey())
                .schemaVersion(42)
                .allowWritesOnUiThread(true)
                .allowQueriesOnUiThread(true)
                .deleteRealmIfMigrationNeeded()
//                .modules(new MySchemaModule())
//                .migration(new MyMigration())
                .build();
        return Realm.getInstance(config);

    }


    public static int insertFile(MediaFile mediaFile){
//        // Log.d("FIlre","insertFile");
        Realm realm = RealmHelper.getRealmInstance();
        realm.beginTransaction();
//        RealmResults<MediaFile> result = realm.where(MediaFile.class).equalTo("uri",mediaFile.getUri()).findAll();
//        // Log.d("FIlre","insertFile filre size " + result.size());

//        if (result.size() > 0 ) {
//            realm.commitTransaction();
//            return -1;
//        }
        realm.copyToRealmOrUpdate(mediaFile);
        realm.commitTransaction();
        realm.close();
        return 1;
    }
}
