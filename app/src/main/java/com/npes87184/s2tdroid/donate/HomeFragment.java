package com.npes87184.s2tdroid.donate;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.internal.view.ContextThemeWrapper;
import android.widget.EditText;

import com.npes87184.s2tdroid.donate.model.Analysis;
import com.npes87184.s2tdroid.donate.model.KeyCollection;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.text.NumberFormat;

import cn.pedant.SweetAlert.SweetAlertDialog;
import ru.bartwell.exfilepicker.ExFilePicker;
import ru.bartwell.exfilepicker.ExFilePickerParcelObject;

/**
 * Created by npes87184 on 2015/5/17.
 */
public class HomeFragment extends PreferenceFragment implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    private final String APP_DIR = Environment.getExternalStorageDirectory().getAbsolutePath() + "/S2TDroid/";
    String[] charsetsToBeTestedCN = {"UTF-8", "GBK"};
    String[] charsetsToBeTestedTW = {"UTF-8", "BIG5"};

    private static final int EX_FILE_PICKER_RESULT = 0;

    boolean isIn = false;
    int wordNumber = 0;

    private Preference inputPreference;
    private Preference outputPreference;
    private Preference startPreference;
    private SharedPreferences prefs;
    private SweetAlertDialog pDialog;

    String booknameString = "default";

    Object syncToken = new Object();

    public static HomeFragment newInstance() {
        HomeFragment fragment = new HomeFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        prefs = getPreferenceManager().getSharedPreferences();
        prefs.registerOnSharedPreferenceChangeListener(this);

        pDialog = new SweetAlertDialog(getActivity(), SweetAlertDialog.PROGRESS_TYPE)
                .setTitleText(getString(R.string.wait));

        inputPreference = findPreference(KeyCollection.KEY_INPUT_FILE);
        inputPreference.setSummary(prefs.getString(KeyCollection.KEY_INPUT_FILE, APP_DIR));
        inputPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                isIn = true;
                Intent intent = new Intent(getActivity(), ru.bartwell.exfilepicker.ExFilePickerActivity.class);
                intent.putExtra(ExFilePicker.SET_ONLY_ONE_ITEM, true);
                intent.putExtra(ExFilePicker.ENABLE_QUIT_BUTTON, true);
                intent.putExtra(ExFilePicker.SET_CHOICE_TYPE, ExFilePicker.CHOICE_TYPE_FILES);
                intent.putExtra(ExFilePicker.SET_FILTER_LISTED, new String[] { "txt", "lrc", "trc", "srt", "ssa", "ass", "saa" });
                intent.putExtra(ExFilePicker.SET_START_DIRECTORY, prefs.getString(KeyCollection.KEY_PATH, APP_DIR));
                startActivityForResult(intent, EX_FILE_PICKER_RESULT);
                return true;
            }
        });

        outputPreference = findPreference(KeyCollection.KEY_OUTPUT_FOLDER);
        outputPreference.setSummary(prefs.getString(KeyCollection.KEY_OUTPUT_FOLDER, APP_DIR));
        outputPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                isIn = false;
                Intent intent = new Intent(getActivity(), ru.bartwell.exfilepicker.ExFilePickerActivity.class);
                intent.putExtra(ExFilePicker.SET_START_DIRECTORY, prefs.getString(KeyCollection.KEY_OUTPUT_FOLDER, APP_DIR));
                intent.putExtra(ExFilePicker.ENABLE_QUIT_BUTTON, true);
                intent.putExtra(ExFilePicker.SET_CHOICE_TYPE, ExFilePicker.CHOICE_TYPE_DIRECTORIES);
                intent.putExtra(ExFilePicker.SET_ONLY_ONE_ITEM, true);
                startActivityForResult(intent, EX_FILE_PICKER_RESULT);
                return true;
            }
        });

        startPreference = findPreference(KeyCollection.KEY_START);
        startPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(final Preference preference) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            File inFile = new File(prefs.getString(KeyCollection.KEY_INPUT_FILE, APP_DIR));
                            if(!inFile.exists()) {
                                Message msg = new Message();
                                msg.what = 2;
                                mHandler.sendMessage(msg);
                                Thread.currentThread().interrupt();
                                return;
                            }
                            // file extension, ex: .txt, .lrc
                            int startIndex = inFile.getName().lastIndexOf(46) + 1;
                            int endIndex = inFile.getName().length();
                            String file_extension = inFile.getName().substring(startIndex, endIndex);

                            // file name
                            String name = inFile.getName();
                            int pos = name.lastIndexOf(".");
                            if (pos > 0) {
                                name = name.substring(0, pos);
                            }

                            InputStream is = new FileInputStream(inFile);
                            String encodeString = "UTF-8";
                            if(prefs.getString(KeyCollection.KEY_ENCODING, "0").equals("0")) {
                                // auto detect encode
                                if(prefs.getString(KeyCollection.KEY_MODE, "s2t").equals("s2t")) {
                                    Charset charset = detectCharset(inFile, charsetsToBeTestedCN);
                                    if (charset == null) {  // maybe Unicode
                                        encodeString = "Unicode";
                                    } else if(charset.name().equals("UTF-8")) { // UTF-8
                                        encodeString = "UTF-8";
                                    } else {
                                        encodeString = "GBK";
                                    }
                                } else {
                                    Charset charset = detectCharset(inFile, charsetsToBeTestedTW);
                                    if (charset == null) {  // maybe Unicode
                                        encodeString = "Unicode";
                                    } else if(charset.name().equals("UTF-8")) { // UTF-8
                                        encodeString = "UTF-8";
                                    } else {
                                        encodeString = "BIG5";
                                    }
                                }
                            } else {
                                encodeString = prefs.getString(KeyCollection.KEY_ENCODING, "UTF-8");
                            }
                            InputStreamReader isr = new InputStreamReader(is, encodeString);
                            BufferedReader bReader = new BufferedReader(isr);
                            String line;
                            int TorS = 0; // >0 means t2s
                            if(prefs.getString(KeyCollection.KEY_MODE, "0").equals("0")) {
                                line = bReader.readLine();
                                if(Analysis.isTraditional(line)) {
                                    booknameString = Analysis.TtoS(line);
                                    ++TorS;
                                } else {
                                    booknameString = Analysis.StoT(line);
                                    --TorS;
                                }
                            } else {
                                booknameString = prefs.getString(KeyCollection.KEY_MODE, "s2t").equals("s2t")?Analysis.StoT(bReader.readLine()):Analysis.TtoS(bReader.readLine());
                            }
                            String firstLine = booknameString;
                            if(prefs.getBoolean(KeyCollection.KEY_SAME_FILENAME, false)) {
                                booknameString = name;
                                Message msg = new Message();
                                msg.what = 3;
                                mHandler.sendMessage(msg);
                            } else {
                                Message msg = new Message();
                                msg.what = 4;
                                mHandler.sendMessage(msg);
                                synchronized (syncToken) {
                                    syncToken.wait();
                                }
                                booknameString = booknameString.split(" ")[0];
                            }
                            File file = new File(prefs.getString(KeyCollection.KEY_OUTPUT_FOLDER, APP_DIR));
                            if(!file.exists() || !file.isDirectory()) {
                                file.mkdir();
                            }

                            // if file exists add -1 in the last
                            File testFile = new File(prefs.getString(KeyCollection.KEY_OUTPUT_FOLDER, APP_DIR)   + booknameString  + "." + file_extension);
                            File outFile;
                            if(testFile.exists()) {
                                outFile = new File(prefs.getString(KeyCollection.KEY_OUTPUT_FOLDER, APP_DIR)   + booknameString + "-1." + file_extension);
                            } else {
                                outFile = new File(prefs.getString(KeyCollection.KEY_OUTPUT_FOLDER, APP_DIR)   + booknameString  + "." + file_extension);
                            }

                            // doing transform
                            OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(outFile), prefs.getString(KeyCollection.KEY_OUTPUT_ENCODING, "Unicode"));
                            BufferedWriter bw = new BufferedWriter(osw);
                            bw.write(firstLine + "\r");
                            bw.newLine();
                            while((line = bReader.readLine()) != null) {
                                if(line.length()==0) {
                                    bw.write("\r");
                                    bw.newLine();
                                    continue;
                                }
                                wordNumber += line.length();
                                if(prefs.getString(KeyCollection.KEY_MODE, "0").equals("0")) {
                                    if(TorS<10 && TorS>-10) {
                                        // detect step
                                        if(Analysis.isTraditional(line)) {
                                            bw.write(Analysis.TtoS(line) + "\r");
                                            ++TorS;
                                        } else {
                                            bw.write(Analysis.StoT(line) + "\r");
                                            --TorS;
                                        }
                                    } else {
                                        if(TorS>0) {
                                            bw.write(Analysis.TtoS(line) + "\r");
                                        } else {
                                            bw.write(Analysis.StoT(line) + "\r");
                                        }
                                    }
                                } else {
                                    if(prefs.getString(KeyCollection.KEY_MODE, "s2t").equals("s2t")) {
                                        bw.write(Analysis.StoT(line) + "\r");
                                    } else {
                                        bw.write(Analysis.TtoS(line) + "\r");
                                    }
                                }
                                bw.newLine();
                            }
                            bReader.close();
                            bw.close();

                            //media rescan for correctly show in pc
                            if(testFile.exists()) {
                                MediaScannerConnection.scanFile(getActivity(), new String[]{prefs.getString(KeyCollection.KEY_OUTPUT_FOLDER, APP_DIR) + booknameString + "-1." + file_extension}, null, null);
                            } else {
                                MediaScannerConnection.scanFile(getActivity(), new String[]{prefs.getString(KeyCollection.KEY_OUTPUT_FOLDER, APP_DIR) + booknameString + "." + file_extension}, null, null);
                            }
                        } catch(Exception e){

                        }
                        Message msg = new Message();
                        msg.what = 1;
                        mHandler.sendMessage(msg);
                    }
                }).start();

                return true;
            }
        });
    }

    Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    NumberFormat nf = NumberFormat.getInstance();
                    pDialog.setTitleText(getString(R.string.word_count) + nf.format(wordNumber))
                            .setConfirmText("OK")
                            .changeAlertType(SweetAlertDialog.SUCCESS_TYPE);
                    break;
                case 2:
                    new SweetAlertDialog(getActivity(), SweetAlertDialog.ERROR_TYPE)
                            .setTitleText(getString(R.string.oops))
                            .setContentText(getString(R.string.oops_detail))
                            .show();
                    break;
                case 3:
                    pDialog.show();
                    pDialog.setCancelable(false);
                    break;
                case 4:
                    AlertDialog.Builder editDialog = new AlertDialog.Builder(new ContextThemeWrapper(getActivity(), R.style.AppCompatAlertDialogStyle));
                    editDialog.setTitle(getResources().getString(R.string.bookname));

                    final EditText editText = new EditText(getActivity());
                    editText.setText(booknameString);
                    editDialog.setView(editText);
                    editDialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        // do something when the button is clicked
                        public void onClick(DialogInterface arg0, int arg1) {
                            booknameString = editText.getText().toString();
                            pDialog.show();
                            pDialog.setCancelable(false);
                            synchronized(syncToken) {
                                syncToken.notify();
                            }
                        }
                    });
                    editDialog.show();
                    break;
            }
            super.handleMessage(msg);
        }
    };

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == EX_FILE_PICKER_RESULT) {
            if (data != null) {
                ExFilePickerParcelObject object = (ExFilePickerParcelObject) data.getParcelableExtra(ExFilePickerParcelObject.class.getCanonicalName());
                if (object.count > 0) {
                    // Here is object contains selected files names and path
                    if(isIn) {
                        inputPreference.getEditor().putString(KeyCollection.KEY_INPUT_FILE, object.path + object.names.get(0) + "/").commit();
                        prefs.edit().putString(KeyCollection.KEY_PATH, object.path).commit();
                        prefs.edit().putString(KeyCollection.KEY_FILE_NAME, object.names.get(0)).commit();
                    }
                    else {
                        outputPreference.getEditor().putString(KeyCollection.KEY_OUTPUT_FOLDER, object.path + object.names.get(0) + "/").commit();
                    }
                }
            }
        }
        getActivity().finish();
        Intent intent = new Intent(getActivity(), MainActivity.class);
        startActivity(intent);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(KeyCollection.KEY_INPUT_FILE)) {
            inputPreference.setSummary(sharedPreferences.getString(KeyCollection.KEY_INPUT_FILE, APP_DIR));
        } else if (key.equals(KeyCollection.KEY_OUTPUT_FOLDER)) {
            outputPreference.setSummary(sharedPreferences.getString(KeyCollection.KEY_OUTPUT_FOLDER, APP_DIR));
        }
    }

    public Charset detectCharset(File f, String[] charsets) {

        Charset charset = null;

        // charsets UTF8, BIG5 etc.
        for (String charsetName : charsets) {
            charset = detectCharset(f, Charset.forName(charsetName));
            if (charset != null) {
                break;
            }
        }
        return charset;
    }

    private Charset detectCharset(File f, Charset charset) {
        try {
            BufferedInputStream input = new BufferedInputStream(new FileInputStream(f));

            CharsetDecoder decoder = charset.newDecoder();
            decoder.reset();

            byte[] buffer = new byte[512];
            boolean identified = false;
            while ((input.read(buffer) != -1) && (!identified)) {
                identified = identify(buffer, decoder);
            }

            input.close();

            if (identified) {
                return charset;
            } else {
                return null;
            }

        } catch (Exception e) {
            return null;
        }
    }

    private boolean identify(byte[] bytes, CharsetDecoder decoder) {
        try {
            decoder.decode(ByteBuffer.wrap(bytes));
        } catch (CharacterCodingException e) {
            return false;
        }
        return true;
    }
}