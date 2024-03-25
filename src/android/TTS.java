package com.wordsbaking.cordova.tts;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CordovaInterface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import java.util.HashMap;
import java.util.Locale;
import java.util.*;

import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;

import android.content.Intent;
import android.content.Context;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.speech.tts.Voice;
import android.util.Log;

import timber.log.Timber;

/*
    Cordova Text-to-Speech Plugin
    https://github.com/vilic/cordova-plugin-tts

    by VILIC VANE
    https://github.com/vilic

    updated by SEBASTIAAN PASMA
    https://github.com/spasma

    MIT License
*/

public class TTS extends CordovaPlugin implements OnInitListener {

    public static final String ERR_INVALID_OPTIONS = "ERR_INVALID_OPTIONS";
    public static final String ERR_NOT_INITIALIZED = "ERR_NOT_INITIALIZED";
    public static final String ERR_ERROR_INITIALIZING = "ERR_ERROR_INITIALIZING";
    public static final String ERR_UNKNOWN = "ERR_UNKNOWN";

    TextToSpeech tts = null;
    Context context = null;
    CordovaWebView webViewContext = null;
    CallbackContext execCallbackContext = null;

    String textToRead;
    String locale;
    double speechRate = 1.0;
    double pitch = 1.0;
    boolean cancel = false;
    String identifier;

    private UtteranceProgressListener TTSListener = new UtteranceProgressListener() {
        @Override
        public void onStart(String s) {
            // do nothing
            Timber.v("UtteranceProgressListener onStart");
        }

        @Override
        public void onDone(String callbackId) {
            Timber.v("UtteranceProgressListener onDone");
            resetSpeechParams();
            if (!callbackId.equals("")) {
                CallbackContext context = new CallbackContext(callbackId, webViewContext);
                context.success();
            }
        }

        @Override
        public void onError(String callbackId, int errorCode) {
            Timber.e("UtteranceProgressListener onError error code: " + errorCode);
            resetSpeechParams();
            if (!callbackId.equals("")) {
                CallbackContext context = new CallbackContext(callbackId, webViewContext);
                context.error(ERR_UNKNOWN);
            }

            CallbackContext context = new CallbackContext(callbackId, webViewContext);
            context.error(errorCode);
        }
    };


    @Override
    public void initialize(CordovaInterface cordova, final CordovaWebView webView) {
        Timber.v("initialize");
        context = cordova.getActivity().getApplicationContext();
        webViewContext = webView;
        try {
            createTTSInstance(null);
        }
        catch (Exception e) {
            Timber.e(e.getMessage());
        }
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext)
            throws JSONException {
        execCallbackContext = callbackContext;
        if (action.equals("speak")) {
            try {
                speak(args, callbackContext);
            }
            catch(Exception e) {
                Timber.e(e.getMessage());
                callbackContext.error(e.getMessage());
            }
        } else if (action.equals("stop")) {
            try {
                stop(args, callbackContext);
            }
            catch (Exception e) {
                Timber.e(e.getMessage());
                callbackContext.error(e.getMessage());
            }
        } else if (action.equals("checkLanguage")) {
            try {
                checkLanguage(args, callbackContext);
            }
            catch (Exception e) {
                Timber.e(e.getMessage());
                callbackContext.error(e.getMessage());
            }
        } else if (action.equals("getVoices")) {
            try {
                getVoices(args, callbackContext);
            }
            catch (Exception e) {
                Timber.e(e.getMessage());
                callbackContext.error(e.getMessage());
            }
        } else if (action.equals("openInstallTts")) {
            try {
                callInstallTtsActivity(args, callbackContext);
            }
            catch (Exception e) {
                Timber.e(e.getMessage());
                callbackContext.error(e.getMessage());
            }
        } else {
            return false;
        }
        return true;
    }

    @Override
    public void onInit(int status) {
        Timber.v("tts STARTED");
        if (status != TextToSpeech.SUCCESS) {
            tts = null;
            Timber.e("Initialization status: ", status);
        } else {
            try {
                // if we have a callback context we are reading a pending script
                if (execCallbackContext != null) {
                    HashMap<String, String> ttsParams = new HashMap<String, String>();
                    ttsParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, execCallbackContext.getCallbackId());
                    Set<Voice> voices = tts.getVoices();

                    if (voices != null) {
                        TTSspeak(voices, execCallbackContext, ttsParams);
                    } else {
                        execCallbackContext.error(ERR_NOT_INITIALIZED);
                    }
                } else { // we're just warming up the tts engine with an empty string
                    HashMap<String, String> ttsParams = new HashMap<String, String>();
                    ttsParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "");
                    tts.setLanguage(new Locale("en", "US"));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        tts.speak("",TextToSpeech.QUEUE_FLUSH,null,null);
                    } else {
                        tts.speak("", TextToSpeech.QUEUE_FLUSH, null);
                    }
                    Timber.v("SUCCESS");
                }
            }
            catch (Exception e) {
                Timber.e(e.getMessage());
                if (execCallbackContext != null) {
                    execCallbackContext.error(e.getMessage());
                }
            }
        }
    }

    private void resetSpeechParams()
    {
        Timber.v("resetSpeechParams");
        textToRead = null;
        locale = null;
        identifier = null;
        speechRate = 1.0;
        pitch = 1.0;
        cancel = false;
        execCallbackContext = null;
    }

    private void createTTSInstance(CallbackContext callbackContext)
        throws JSONException, NullPointerException {
        Timber.v("createTTSInstance");
        tts = new TextToSpeech(cordova.getActivity().getApplicationContext(), this, "com.google.android.tts");
        if (tts == null) {
            Timber.e(ERR_ERROR_INITIALIZING);
            if (callbackContext != null) {
                callbackContext.error(ERR_ERROR_INITIALIZING);
            }
        } else {
            tts.setOnUtteranceProgressListener(TTSListener);
        }
    }

    private void stop(JSONArray args, CallbackContext callbackContext)
            throws JSONException, NullPointerException {
        Timber.v("stop");
        tts.stop();
    }

    private void callInstallTtsActivity(JSONArray args, CallbackContext callbackContext)
            throws JSONException, NullPointerException {
        Timber.v("callInstallTTSActivity");
        PackageManager pm = context.getPackageManager();
        Intent installIntent = new Intent();
        installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
        ResolveInfo resolveInfo = pm.resolveActivity( installIntent, PackageManager.MATCH_DEFAULT_ONLY );

        if( resolveInfo == null ) {
            Timber.v("Not able to find the activity which should be started for this intent");
        } else {
            installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(installIntent);
        }
    }


    private void checkLanguage(JSONArray args, CallbackContext callbackContext)
            throws JSONException, NullPointerException {
        Timber.v("checkLanguage");
        Set<Locale> supportedLanguages = tts.getAvailableLanguages();
        String languages = "";
        if(supportedLanguages!= null) {
            for (Locale lang : supportedLanguages) {
                languages = languages + "," + lang;
            }
        }
        if (languages != "") {
            languages = languages.substring(1);
        }

        final PluginResult result = new PluginResult(PluginResult.Status.OK, languages);
        callbackContext.sendPluginResult(result);
    }

    private void speak(JSONArray args, CallbackContext callbackContext)
            throws JSONException, NullPointerException {
        Timber.v("speak");
        JSONObject params = args.getJSONObject(0);
        if (params == null) {
            callbackContext.error(ERR_INVALID_OPTIONS);
            return;
        }

        if (params.isNull("text")) {
            Timber.e("Invalid textToRead");
            callbackContext.error(ERR_INVALID_OPTIONS);
            return;
        } else {
            textToRead = params.getString("text");
        }

        if (params.isNull("identifier")) {
            identifier = "";
            Timber.v("No voice identifier");
        } else {
            identifier = params.getString("identifier");
            Timber.v("got identifier: " + identifier);
        }

        if (params.isNull("locale")) {
            locale = Locale.getDefault().toLanguageTag();
        } else {
            locale = params.getString("locale");
        }

        if (!params.isNull("cancel")) {
            cancel = params.getBoolean("cancel");
        }
        Timber.v("cancel is set to "+cancel+ "("+(cancel?"TextToSpeech.QUEUE_FLUSH":"TextToSpeech.QUEUE_ADD")+")");

        if (params.isNull("rate")) {
            speechRate = 1.0;
            Timber.v("No rate provided, so rate is set to " + speechRate);
        } else {
            speechRate = params.getDouble("rate");
            Timber.v("rate is set to " + speechRate);
        }

        if (params.isNull("pitch")) {
            pitch = 1.0;
            Timber.v("No pitch provided, so pitch set to " + pitch);
        } else {
            pitch = params.getDouble("pitch");
            Timber.v("Pitch set to " + pitch);
        }

        if (tts == null) {
            Timber.e("speak(): Error Initializing");
            callbackContext.error(ERR_ERROR_INITIALIZING);
            return;
        }

        HashMap<String, String> ttsParams = new HashMap<String, String>();
        ttsParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, callbackContext.getCallbackId());
        Set<Voice> voices = tts.getVoices();
        Voice voice = null;

        // if voices == null tts is not initialized properly, so init again;
        // OnInit() triggered by adding our UtteranceProgressListener will handle calling TTSspeak()
        if (voices == null) {
            Timber.e("TextToSpeech.Engine returned 'null' for voices; restarting instance");
            createTTSInstance(callbackContext);
        } else {
            TTSspeak(voices, callbackContext, ttsParams);
        }
    }

    private void TTSspeak(Set<Voice> voices, CallbackContext callbackContext, HashMap<String, String> ttsParams)
        throws JSONException, NullPointerException {
        Voice voice = null;
        Timber.v("TTSspeak");
        if (!identifier.equals("")) {
            for (Voice tmpVoice : voices) {
                if (tmpVoice.getName().contains(identifier)) {
                    Timber.v("Found Voice for identifier: " + tmpVoice.getName());
                    voice = tmpVoice;
                    break;
                } else {
                    voice = null;
                }
            }
            if (voice == null) {
                Timber.v("No Voice for identifier: "+identifier+", we'll try the locale");
            }
        }
        if (voice == null) {
            String[] localeArgs = locale.split("-");
            tts.setLanguage(new Locale(localeArgs[0], localeArgs[1]));
            for (Voice tmpVoice : voices) {
                if (tmpVoice.getName().toLowerCase().contains(locale.toLowerCase())) {
                    Timber.v("Found Voice for locale: " + tmpVoice.getName());
                    voice = tmpVoice;
                    break;
                } else {
                    voice = null;
                }
            }
        }

        if (voice != null) {
            Timber.v("We've got a voice: " + voice.getName());
            tts.setVoice(voice);
        } else {
            Timber.v("No voice found.");
        }

        if (Build.VERSION.SDK_INT >= 27) {
            tts.setSpeechRate((float) speechRate * 0.7f);
        } else {
            tts.setSpeechRate((float) speechRate);
        }
        tts.setPitch((float)pitch);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(textToRead,cancel?TextToSpeech.QUEUE_FLUSH:TextToSpeech.QUEUE_ADD,null,callbackContext.getCallbackId());
        } else {
            tts.speak(textToRead,cancel?TextToSpeech.QUEUE_FLUSH:TextToSpeech.QUEUE_ADD,ttsParams);
        }
    }

    private void getVoices(JSONArray args, CallbackContext callbackContext)
            throws JSONException, NullPointerException {
        Timber.v("getVoices");
        Set<Voice> voices = tts.getVoices();
        JSONArray languages = new JSONArray();
        for (Voice tmpVoice : voices) {
            JSONObject lang = new JSONObject();
            Timber.v("Voice: " + tmpVoice.getName());
            lang.put("name", tmpVoice.getName());
            lang.put("identifier", tmpVoice.getName());
            lang.put("language", tmpVoice.getLocale());
            languages.put(lang);
        }

        final PluginResult result = new PluginResult(PluginResult.Status.OK, languages);
        callbackContext.sendPluginResult(result);
    }
}
