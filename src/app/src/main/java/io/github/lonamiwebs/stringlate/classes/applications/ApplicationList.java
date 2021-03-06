package io.github.lonamiwebs.stringlate.classes.applications;

import android.content.Context;
import android.os.AsyncTask;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import io.github.lonamiwebs.stringlate.interfaces.ProgressUpdateCallback;
import io.github.lonamiwebs.stringlate.R;
import io.github.lonamiwebs.stringlate.utilities.FileDownloader;
import io.github.lonamiwebs.stringlate.utilities.FileExtractor;

public class ApplicationList implements Iterable<Application> {

    //region Members

    public final static int DEFAULT_APPS_LIMIT = 50;
    public final static String FDROID_REPO_URL = "https://f-droid.org/repo";
    private final static String FDROID_INDEX_URL = FDROID_REPO_URL+"/index.jar";

    private File mRoot;
    private Context mContext;

    private static final String BASE_DIR = "index";

    private ArrayList<Application> mApplications;

    //endregion

    //region Initialization

    public ApplicationList(Context context) {
        mApplications = new ArrayList<>();
        mContext = context;

        mRoot = new File(mContext.getCacheDir(), BASE_DIR);
    }

    //endregion

    //region Getters

    public ArrayList<Application> getApplications(boolean applyLimit, String filter) {
        // If (limit not applied) or (limit applied but there are less apps than limit)
        //    (use the current apps) otherwise (use our custom limit)
        int take = !applyLimit || mApplications.size() < DEFAULT_APPS_LIMIT ?
                mApplications.size() : DEFAULT_APPS_LIMIT;

        ArrayList<Application> result = new ArrayList<>(take);
        if (filter == null) {
            for (int i = 0; i < take; i++)
                result.add(mApplications.get(i));
        }
        else {
            filter = filter.trim().toLowerCase();
            for (int i = 0; i < mApplications.size() && take > 0; i++) {
                Application app = mApplications.get(i);
                if (app.getName().toLowerCase().contains(filter)) {
                    result.add(app);
                    take--;
                }
            }
        }

        return result;
    }

    //endregion

    public void syncRepo(final ProgressUpdateCallback callback) {
        final AsyncTask<Void, Void, Void> step3 = new AsyncTask<Void, Void, Void>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                callback.onProgressUpdate(mContext.getString(R.string.loading_index_xml),
                        mContext.getString(R.string.loading_index_xml_long));
            }

            @Override
            protected Void doInBackground(Void... voids) {
                loadIndexXml(); // Loses not-required information
                saveIndexXml(); // Thus minimizes the file
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                callback.onProgressFinished(mContext.getString(R.string.done), true);
            }
        };
        final AsyncTask<Void, Void, Void> step2 = new AsyncTask<Void, Void, Void>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                callback.onProgressUpdate(mContext.getString(R.string.extracting_index_xml),
                        mContext.getString(R.string.extracting_index_xml_long));
            }

            @Override
            protected Void doInBackground(Void... voids) {
                extractIndexXml();
                deleteIndexJar();
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                step3.execute();
            }
        };
        final AsyncTask<Void, Void, Void> step1 = new AsyncTask<Void, Void, Void>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                callback.onProgressUpdate(mContext.getString(R.string.downloading_index_jar),
                        mContext.getString(R.string.downloading_index_jar_long));
            }

            @Override
            protected Void doInBackground(Void... voids) {
                downloadIndexJar();
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                step2.execute();
            }
        };

        step1.execute();
    }

    // Step 1: Download the index.jar
    private void downloadIndexJar() {
        FileDownloader.downloadFile(FDROID_INDEX_URL, getIndexFile("jar"));
    }

    // Step 2a: Extract the index.xml from the index.jar
    private void extractIndexXml() {
        FileExtractor.unpackZip(getIndexFile("jar"), mRoot, false);
    }

    // Step 2b: Delete index.jar
    private boolean deleteIndexJar() {
        return getIndexFile("jar").delete();
    }

    // Step 3a: Load the ApplicationList from the index.xml
    public boolean loadIndexXml() {
        try {
            File file = getIndexFile("xml");
            if (file.isFile()) {
                mApplications = ApplicationListParser
                        .parseFromXml(new FileInputStream(getIndexFile("xml")));
                return true;
            } else {
                mApplications.clear();
            }
        } catch (IOException | XmlPullParserException e) {
            // Won't happen
            e.printStackTrace();
        }
        return false;
    }

    // Step 3b: Save a (minimized) version of the index.xml
    private void saveIndexXml() {
        try {
            ApplicationListParser.parseToXml(this,
                    new FileOutputStream(getIndexFile("xml")));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private File getIndexFile(String extension) {
        return new File(mRoot, "index."+extension);
    }

    @Override
    public Iterator<Application> iterator() {
        return mApplications.iterator();
    }
}
