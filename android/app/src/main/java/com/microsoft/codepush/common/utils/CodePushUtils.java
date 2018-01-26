package com.microsoft.codepush.common.utils;

import android.support.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.microsoft.appcenter.utils.AppCenterLog;
import com.microsoft.codepush.common.CodePush;
import com.microsoft.codepush.common.exceptions.CodePushFinalizeException;
import com.microsoft.codepush.common.exceptions.CodePushMalformedDataException;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Common utils for each platform.
 */
public abstract class CodePushUtils {

    /**
     * Instance of GsonBuilder, singleton.
     */
    private static Gson mGson = new GsonBuilder().create();

    /**
     * Gracefully finalizes {@link Closeable} resources. Method iterates through resources and invokes
     * {@link Closeable#close()} on it if needed. If an exception was thrown during <code>.close()</code> call,
     * it will be logged using logErrorMessage parameter as general message.
     *
     * @param resources       resources to finalize.
     * @param logErrorMessage general logging message for errors occurred during resource finalization.
     * @return last {@link IOException} thrown during resource finalization, null if no exception was thrown.
     */
    @SuppressWarnings("WeakerAccess")
    public static IOException finalizeResources(List<Closeable> resources, @Nullable String logErrorMessage) {
        IOException lastException = null;
        for (int i = 0; i < resources.size(); i++) {
            Closeable resource = resources.get(i);
            try {
                if (resource != null) {
                    resource.close();
                }
            } catch (IOException e) {
                lastException = e;
                if (logErrorMessage != null) {
                    AppCenterLog.error(CodePush.LOG_TAG, logErrorMessage, e);
                }
            }
        }
        return lastException;
    }

    /**
     * Gets the string content from instance of {@link InputStream}.
     *
     * @param inputStream InputStream instance.
     * @return string content.
     * @throws IOException if read/write error occurred while accessing the file system.
     */
    @SuppressWarnings("WeakerAccess")
    public static String getStringFromInputStream(InputStream inputStream) throws IOException {
        BufferedReader bufferedReader = null;
        try {
            StringBuilder buffer = new StringBuilder();
            bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                buffer.append(line);
                buffer.append("\n");
            }
            return buffer.toString().trim();
        } finally {
            IOException e = finalizeResources(
                    Arrays.asList(bufferedReader, inputStream),
                    null);
            if (e != null) {
                throw new CodePushFinalizeException(e);
            }
        }
    }

    /**
     * Parses {@link JSONObject} from file.
     *
     * @param filePath path to file.
     * @return parsed {@link JSONObject} instance.
     * @throws CodePushMalformedDataException if file contains malformed data.
     */
    @SuppressWarnings("WeakerAccess")
    public static JSONObject getJsonObjectFromFile(String filePath) throws CodePushMalformedDataException {
        try {
            String content = FileUtils.readFileToString(filePath);
            return new JSONObject(content);
        } catch (JSONException | IOException e) {
            throw new CodePushMalformedDataException(
                    "Unable to parse contents of \"" + filePath + "\", the file may be corrupted.",
                    e);
        }
    }

    /**
     * Writes {@link JSONObject} to file.
     *
     * @param json     {@link JSONObject} instance.
     * @param filePath path to file.
     * @throws IOException if write error occurred while accessing the file system.
     */
    public static void writeJsonToFile(JSONObject json, String filePath) throws IOException {
        String jsonString = json.toString();
        FileUtils.writeStringToFile(jsonString, filePath);
    }

    /**
     * Converts {@link Object} instance to {@link JSONObject}.
     *
     * @param object {@link JSONObject} instance.
     * @return {@link JSONObject} instance.
     * @throws JSONException if there was a problem with the JSON API.
     */
    public static JSONObject convertObjectToJsonObject(Object object) throws JSONException {
        return new JSONObject(mGson.toJsonTree(object).toString());
    }

    /**
     * Converts {@link Object} instance to json string.
     *
     * @param object {@link JSONObject} instance.
     * @return the json string.
     */
    public static String convertObjectToJsonString(Object object) {
        return mGson.toJsonTree(object).toString();
    }

    /**
     * Converts {@link JSONObject} instance to specified class.
     *
     * @param jsonObject {@link JSONObject} instance.
     * @param classOfT   the class of T.
     * @param <T>        the type of the desired object.
     * @return instance of T.
     */
    public static <T> T convertJsonObjectToObject(JSONObject jsonObject, Class<T> classOfT) {
        return convertStringToObject(jsonObject.toString(), classOfT);
    }

    /**
     * Converts json string to specified class.
     *
     * @param stringObject json string.
     * @param classOfT     the class of T.
     * @param <T>          the type of the desired object.
     * @return instance of T.
     */
    @SuppressWarnings("WeakerAccess")
    public static <T> T convertStringToObject(String stringObject, Class<T> classOfT) {
        return mGson.fromJson(stringObject, classOfT);
    }

    /**
     * Converts object to query string using the following scheme: <br/>
     * <ul>
     * <li>object converts to {@link JSONObject};</li>
     * <li>{@link JSONObject} instance converts to {@link Map}&lt;String, Object&gt;
     * using field names as keys for Map and its values as values for Map;</li>
     * <li>iterates through {@link Map}&lt;String, Object&gt; instance and builds query string.</li>
     * </ul>
     *
     * @param object object.
     * @return query string.
     * @throws CodePushMalformedDataException if object contains malformed data.
     */
    public static String getQueryStringFromObject(Object object) throws CodePushMalformedDataException {
        JsonObject updateRequestJson = mGson.toJsonTree(object).getAsJsonObject();
        Map<String, Object> updateRequestMap = new HashMap<>();
        updateRequestMap = (Map<String, Object>) mGson.fromJson(updateRequestJson, updateRequestMap.getClass());
        StringBuilder sb = new StringBuilder();
        for (HashMap.Entry<String, Object> e : updateRequestMap.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            try {
                sb.append(URLEncoder.encode(e.getKey(), "UTF-8"))
                        .append('=')
                        .append(URLEncoder.encode(e.getValue().toString(), "UTF-8"));
            } catch (UnsupportedEncodingException exception) {
                throw new CodePushMalformedDataException("Error converting object to query string", exception);
            }
        }
        return sb.toString();
    }
}